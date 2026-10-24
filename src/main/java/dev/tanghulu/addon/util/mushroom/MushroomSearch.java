package dev.tanghulu.addon.util.mushroom;

import com.google.gson.*;
import dev.tanghulu.addon.util.seeds.Prediction26;
import meteordevelopment.meteorclient.MeteorClient;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;

/** One cancellable search; downloads are verified and the worker never opens a player's save. */
public final class MushroomSearch implements AutoCloseable {
    public record Target(int x, int y, int z) {
        public int netherX() { return Math.floorDiv(x,8); }
        public int netherZ() { return Math.floorDiv(z,8); }
    }
    private volatile boolean cancelled;
    private volatile Process process;
    private volatile Thread owner;
    private static final ReentrantLock CACHE_LOCK = new ReentrantLock();
    private static final Set<Process> LIVE=ConcurrentHashMap.newKeySet();
    static { Runtime.getRuntime().addShutdownHook(new Thread(() -> LIVE.forEach(Process::destroyForcibly),"tanghulu-mushroom-shutdown")); }
    private void check() throws InterruptedException {
        if(cancelled || Thread.currentThread().isInterrupted())throw new InterruptedException("搜索已取消");
    }
    public List<Target> runAll(long seed, MushroomVersion version, int x, int z, int radius, int step, Consumer<String> status) throws Exception {
        owner=Thread.currentThread();
        check();
        Path java=version.requiresJava25()?Prediction26.findJava():Path.of(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").startsWith("Windows")?"java.exe":"java");
        Path cache=MeteorClient.FOLDER.toPath().resolve("tanghulu-mushroom").resolve(version.toString()).toAbsolutePath();
        status.accept("正在准备 "+version+" 原版生物群系运行库（首次使用需下载）");
        CACHE_LOCK.lockInterruptibly();
        try { check(); prepare(cache,version); }
        finally { CACHE_LOCK.unlock(); }
        check();
        Path log=Files.createTempFile(cache,"search-",".log");
        Process child=new ProcessBuilder(java.toString(),"-Xmx1G","-XX:ActiveProcessorCount=2","-Dfile.encoding=UTF-8",
            "-cp",cache.resolve("worker.jar")+File.pathSeparator+cache.resolve("runtime")+File.separator+"*",
            "dev.tanghulu.mushroom.BiomeWorker",cache.resolve("mappings.txt").toString(),Long.toString(seed),
            Integer.toString(x),Integer.toString(z),Integer.toString(radius),Integer.toString(step),"all")
            .directory(cache.toFile()).redirectError(log.toFile()).start();
        process=child;
        LIVE.add(child);
        if(cancelled)child.destroyForcibly();
        Thread watchdog=Thread.ofVirtual().start(() -> {
            try { Thread.sleep(Duration.ofMinutes(10)); child.destroyForcibly(); }
            catch(InterruptedException ignored) { Thread.currentThread().interrupt(); }
        });
        try(var input=new BufferedReader(new InputStreamReader(child.getInputStream(),StandardCharsets.UTF_8))) {
            List<Target> targets=new ArrayList<>();
            for(String line;(line=input.readLine())!=null;) {
                check();
                if(line.equals("READY"))status.accept("正在搜索蘑菇岛，半径 "+radius+" 格");
                else if(line.startsWith("PERCENT "))status.accept("扫描进度 "+line.substring(8)+"%");
                else if(line.startsWith("DONE ")) {
                    if(Integer.parseInt(line.substring(5))!=targets.size())throw new IOException("搜索结果不完整");
                    return List.copyOf(targets);
                } else if(line.startsWith("TARGET ")) {
                    String[] p=line.split(" ");
                    targets.add(new Target(Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3])));
                } else if(line.startsWith("ERROR "))throw new IOException("原版预测失败："+line.substring(6)+"；日志："+log);
            }
            check(); throw new IOException("预测进程退出或超过 10 分钟；日志："+log);
        } finally {
            watchdog.interrupt(); child.destroyForcibly(); LIVE.remove(child);
            process=null; owner=null;
        }
    }
    private void prepare(Path cache,MushroomVersion version) throws Exception {
        Files.createDirectories(cache);
        JsonObject release;
        try(var in=MushroomSearch.class.getResourceAsStream("/tanghulu/mushroom/releases.json")) {
            if(in==null)throw new IOException("缺少蘑菇岛版本清单");
            release=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject(version.toString());
        }
        if(release==null)throw new IOException("未验证的版本："+version);
        download(release.getAsJsonObject("server"),cache.resolve("server.jar"));
        if(release.has("server_mappings"))download(release.getAsJsonObject("server_mappings"),cache.resolve("mappings.txt"));
        Path runtime=cache.resolve("runtime"); Files.createDirectories(runtime);
        try(var zip=new ZipFile(cache.resolve("server.jar").toFile())) {
            for(var entries=zip.entries();entries.hasMoreElements();) {
                check(); var entry=entries.nextElement(); String name=entry.getName();
                if((name.startsWith("META-INF/versions/") || name.startsWith("META-INF/libraries/")) && name.endsWith(".jar")) {
                    Path target=runtime.resolve(Path.of(name).getFileName().toString());
                    // Reuse intact files; CRC also detects corruption that preserves file size.
                    if(Files.isRegularFile(target) && Files.size(target)==entry.getSize() && crc32(target)==entry.getCrc())continue;
                    try(var in=zip.getInputStream(entry)) { Files.copy(in,target,StandardCopyOption.REPLACE_EXISTING); }
                }
            }
        }
        try(var in=MushroomSearch.class.getResourceAsStream("/tanghulu/mushroom/worker.jar")) {
            if(in==null)throw new IOException("缺少蘑菇岛预测组件");
            Files.copy(in,cache.resolve("worker.jar"),StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private void download(JsonObject artifact,Path destination) throws Exception {
        check(); String expected=artifact.get("sha1").getAsString();
        if(Files.isRegularFile(destination) && sha1(destination).equals(expected))return;
        Path part=destination.resolveSibling(destination.getFileName()+".part");
        try {
            var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NORMAL).build();
            var response=client.send(HttpRequest.newBuilder(URI.create(artifact.get("url").getAsString()))
                .timeout(Duration.ofMinutes(5)).GET().build(),HttpResponse.BodyHandlers.ofFile(part));
            check();
            if(response.statusCode()!=200 || !sha1(part).equals(expected))throw new IOException("Mojang 运行库下载或 SHA-1 校验失败");
            Files.move(part,destination,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(part); }
    }
    private static String sha1(Path path) throws Exception {
        var digest=MessageDigest.getInstance("SHA-1");
        try(var in=Files.newInputStream(path)) { byte[] bytes=new byte[65536]; for(int n;(n=in.read(bytes))!=-1;)digest.update(bytes,0,n); }
        return HexFormat.of().formatHex(digest.digest());
    }
    private long crc32(Path path) throws IOException,InterruptedException {
        var crc=new CRC32();
        try(var in=Files.newInputStream(path)) {
            byte[] bytes=new byte[65536];
            for(int n;(n=in.read(bytes))!=-1;) { check(); crc.update(bytes,0,n); }
        }
        return crc.getValue();
    }
    @Override public void close() {
        cancelled=true; Process child=process;
        if(child!=null)child.destroyForcibly();
        Thread running=owner;
        if(running!=null && Thread.currentThread()!=running)running.interrupt();
    }
}
