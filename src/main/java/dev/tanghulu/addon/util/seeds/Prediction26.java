package dev.tanghulu.addon.util.seeds;

import com.google.gson.*;
import meteordevelopment.meteorclient.MeteorClient;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

/** Owns an isolated vanilla process; no Minecraft 26.3 classes enter the mod class loader. */
public final class Prediction26 implements AutoCloseable {
    private static final String SERVER_SHA1="33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c";
    private static final Gson JSON=new Gson();
    private static final Set<Prediction26> LIVE=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static { Runtime.getRuntime().addShutdownHook(new Thread(() -> LIVE.forEach(Prediction26::cancel),"tanghulu-prediction-shutdown")); }
    private final Path temporary;
    private final Process process;
    private final BufferedReader input;
    private final BufferedWriter output;
    private volatile boolean closed;

    private Prediction26(Path temporary,Process process) {
        this.temporary=temporary; this.process=process;
        input=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8));
        output=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8));
        LIVE.add(this);
    }
    public static Prediction26 open(long seed) throws Exception {
        Path java=findJava();
        Path cache=MeteorClient.FOLDER.toPath().resolve("tanghulu-prediction/26.3").toAbsolutePath();
        prepare(cache);
        Path temporary=Files.createTempDirectory(cache,"world-");
        Process child=new ProcessBuilder(java.toString(),"-Xmx2G","-XX:ActiveProcessorCount=2",
            "-Dfile.encoding=UTF-8","-cp",cache.resolve("helper.jar")+File.pathSeparator+cache.resolve("runtime")+File.separator+"*",
            "dev.tanghulu.prediction26.Vanilla26",temporary.toString(),Long.toString(seed))
            .directory(temporary.toFile()).redirectError(temporary.resolve("backend.log").toFile()).start();
        var result=new Prediction26(temporary,child);
        // A bounded watchdog also releases readLine when startup is stuck.
        Thread.ofVirtual().start(() -> {
            try { Thread.sleep(Duration.ofMinutes(3)); if(!result.ready)child.destroyForcibly(); }
            catch(InterruptedException ignored) { Thread.currentThread().interrupt(); }
        });
        try {
            for(String line;(line=result.input.readLine())!=null;)if(line.equals("READY 26.3")) {
                result.ready=true; return result;
            }
            throw new IOException("26.3 预测进程启动失败；日志："+result.retainLog());
        } catch(Exception e) { result.close(); throw e; }
    }
    private volatile boolean ready;
    public synchronized JsonObject request(Map<String,Object> request) throws Exception {
        if(closed)throw new IOException("预测已取消");
        output.write(JSON.toJson(request)); output.newLine(); output.flush();
        final boolean[] done={false};
        Thread watchdog=Thread.ofVirtual().start(() -> {
            try { Thread.sleep(Duration.ofMinutes(5)); synchronized(done) { if(!done[0])process.destroyForcibly(); } }
            catch(InterruptedException ignored) { Thread.currentThread().interrupt(); }
        });
        try {
            for(String line;(line=input.readLine())!=null;) {
                if(!line.startsWith("{"))continue;
                var result=JsonParser.parseString(line).getAsJsonObject();
                if(result.has("error"))throw new IOException(result.get("error").getAsString()+"；日志："+retainLog());
                return result;
            }
            throw new IOException("26.3 预测进程已退出；日志："+retainLog());
        } finally { synchronized(done) { done[0]=true; } watchdog.interrupt(); }
    }
    private static synchronized void prepare(Path cache) throws Exception {
        Files.createDirectories(cache);
        Path server=cache.resolve("server.jar");
        if(!Files.exists(server) || !sha1(server).equals(SERVER_SHA1)) {
            var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NORMAL).build();
            Path part=cache.resolve("server.jar.part");
            try {
                var response=client.send(HttpRequest.newBuilder(URI.create("https://piston-data.mojang.com/v1/objects/"+SERVER_SHA1+"/server.jar"))
                    .timeout(Duration.ofMinutes(5)).GET().build(),HttpResponse.BodyHandlers.ofFile(part));
                if(response.statusCode()!=200 || !sha1(part).equals(SERVER_SHA1))throw new IOException("官方 26.3 运行库下载或校验失败");
                Files.move(part,server,StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(part); }
        }
        Path runtime=cache.resolve("runtime"); Files.createDirectories(runtime);
        try(var zip=new ZipFile(server.toFile())) {
            for(var entries=zip.entries();entries.hasMoreElements();) {
                var entry=entries.nextElement(); String name=entry.getName();
                if((name.startsWith("META-INF/versions/") || name.startsWith("META-INF/libraries/")) && name.endsWith(".jar")) {
                    Path target=runtime.resolve(Path.of(name).getFileName().toString());
                    if(!Files.exists(target) || Files.size(target)!=entry.getSize())
                        try(var stream=zip.getInputStream(entry)) { Files.copy(stream,target,StandardCopyOption.REPLACE_EXISTING); }
                }
            }
        }
        try(var stream=Prediction26.class.getResourceAsStream("/tanghulu/prediction/prediction26.jar")) {
            if(stream==null)throw new IOException("缺少 26.3 预测组件");
            byte[] bytes=stream.readAllBytes(); Path target=cache.resolve("helper.jar");
            if(!Files.exists(target) || !Arrays.equals(bytes,Files.readAllBytes(target))) Files.write(target,bytes);
        }
    }
    private Path retainLog() throws IOException {
        Path target=temporary.getParent().resolve("last-error.log");
        Files.copy(temporary.resolve("backend.log"),target,StandardCopyOption.REPLACE_EXISTING); return target;
    }
    private static String sha1(Path file) throws Exception {
        var digest=MessageDigest.getInstance("SHA-1");
        try(var stream=Files.newInputStream(file)) { byte[] b=new byte[65536]; for(int n;(n=stream.read(b))!=-1;)digest.update(b,0,n); }
        return HexFormat.of().formatHex(digest.digest());
    }
    public static Path findJava() throws Exception {
        String executable=System.getProperty("os.name").startsWith("Windows")?"java.exe":"java";
        Set<Path> candidates=new LinkedHashSet<>();
        String override=System.getenv("TANGHULU_JAVA25");
        if(override!=null && !override.isBlank())candidates.add(Path.of(override));
        candidates.add(Path.of(System.getProperty("java.home"),"bin",executable));
        String home=System.getenv("JAVA_HOME"); if(home!=null)candidates.add(Path.of(home,"bin",executable));
        String path=System.getenv("PATH"); if(path!=null)for(String part:path.split(File.pathSeparator))if(!part.isBlank())candidates.add(Path.of(part,executable));
        List<Path> launchers=new ArrayList<>();
        launchers.add(Path.of(System.getProperty("user.home"),".minecraft/runtime"));
        String appdata=System.getenv("APPDATA"); if(appdata!=null)launchers.add(Path.of(appdata,".minecraft/runtime"));
        for(Path root:launchers)if(Files.isDirectory(root))try(var files=Files.find(root,7,(p,a) -> a.isRegularFile() && p.getFileName().toString().equals(executable))) { files.forEach(candidates::add); }
        for(Path candidate:candidates)if(Files.isRegularFile(candidate)) {
            Process check=new ProcessBuilder(candidate.toString(),"-version").redirectErrorStream(true).start();
            if(!check.waitFor(5,TimeUnit.SECONDS)) { check.destroyForcibly(); continue; }
            String version=new String(check.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            var matcher=java.util.regex.Pattern.compile("version \\\"(\\d+)").matcher(version);
            if(check.exitValue()==0 && matcher.find() && Integer.parseInt(matcher.group(1))>=25)return candidate.toAbsolutePath();
        }
        throw new IOException("26.3 原版预测需要 Java 25：安装后将 TANGHULU_JAVA25 指向 java 可执行文件，再重启游戏");
    }
    /** Deliberately not synchronized: cancellation must be able to unblock a pending request. */
    public void cancel() { process.destroyForcibly(); }
    @Override public void close() {
        if(closed)return; closed=true; process.destroyForcibly();
        LIVE.remove(this);
        try {
            if(process.waitFor(10,TimeUnit.SECONDS))try(var files=Files.walk(temporary)) {
                for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
            }
        } catch(Exception ignored) { if(ignored instanceof InterruptedException)Thread.currentThread().interrupt(); }
    }
}
