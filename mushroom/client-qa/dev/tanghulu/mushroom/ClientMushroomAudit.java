package dev.tanghulu.mushroom;

import dev.tanghulu.addon.util.mushroom.*;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import net.minecraft.registry.RegistryKey;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Uses the real client wrapper, pinned official jars, and real Xaero waypoint objects without a game window. */
public final class ClientMushroomAudit {
    static int checks;
    static void check(boolean value,String message) { checks++; if(!value)throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);
        Path qa=args.length>1?Path.of(args[1]):root.resolve("mushroom-qa");
        for(var v:List.of(MushroomVersion.v1_21_1,MushroomVersion.v26_3)) {
            Path cache=Path.of(System.getProperty("prediction26.testCache"),"tanghulu-mushroom",v.toString());
            Files.createDirectories(cache);
            Files.copy(qa.resolve(v+"-server.jar"),cache.resolve("server.jar"),StandardCopyOption.REPLACE_EXISTING);
            Path mappings=qa.resolve(v+"-mappings.txt");
            if(Files.exists(mappings))Files.copy(mappings,cache.resolve("mappings.txt"),StandardCopyOption.REPLACE_EXISTING);
            try(var search=new MushroomSearch()) {
                var targets=search.runAll(0,v,0,0,2048,32,s -> {});
                check(!targets.isEmpty(),"real worker returned no island for "+v);
                check(targets.stream().allMatch(t -> Math.hypot(t.x(),t.z())<=2048),"outside radius");
            }
            var prepare=MushroomSearch.class.getDeclaredMethod("prepare",Path.class,MushroomVersion.class);
            prepare.setAccessible(true);
            Path cached;
            try(var files=Files.list(cache.resolve("runtime"))) { cached=files.filter(Files::isRegularFile).findFirst().orElseThrow(); }
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(cached));
            Files.setLastModifiedTime(cached,FileTime.fromMillis(1000000));
            var timestamp=Files.getLastModifiedTime(cached);
            try(var search=new MushroomSearch()) { prepare.invoke(search,cache,v); }
            check(Files.getLastModifiedTime(cached).equals(timestamp),"intact runtime was unnecessarily rewritten");
            // Same-size damage must be repaired, so a size-only cache check cannot pass.
            try(var file=new RandomAccessFile(cached.toFile(),"rw")) {
                int original=file.read(); file.seek(0); file.write(original^255);
            }
            try(var search=new MushroomSearch()) { prepare.invoke(search,cache,v); }
            check(Arrays.equals(digest,MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(cached))),"corrupt runtime was reused");
        }
        check(new MushroomSearch.Target(-641,64,-961).netherX()==-81,"negative X portal division");
        check(new MushroomSearch.Target(-641,64,-961).netherZ()==-121,"negative Z portal division");
        check(new MushroomSearch.Target(647,64,967).netherX()==80,"positive X portal division");
        check(new MushroomSearch.Target(-1,64,0).netherX()==-1,"near-zero negative portal division");
        var lockField=MushroomSearch.class.getDeclaredField("CACHE_LOCK"); lockField.setAccessible(true);
        var lock=(java.util.concurrent.locks.ReentrantLock)lockField.get(null);
        var queued=new CountDownLatch(1);
        lock.lock();
        try(var search=new MushroomSearch(); var executor=Executors.newSingleThreadExecutor()) {
            var future=executor.submit(() -> {
                try {
                    search.runAll(0,MushroomVersion.v1_21_1,0,0,64,8,s -> queued.countDown());
                    return false;
                } catch(InterruptedException expected) { return true; }
            });
            check(queued.await(10,TimeUnit.SECONDS),"search did not reach cache preparation");
            search.close();
            check(future.get(5,TimeUnit.SECONDS),"queued cache preparation ignored cancellation");
        } finally { lock.unlock(); }
        var ready=new CountDownLatch(1);
        try(var search=new MushroomSearch(); var executor=Executors.newSingleThreadExecutor()) {
            var future=executor.submit(() -> {
                try { search.runAll(0,MushroomVersion.v1_21_1,0,0,16000,8,s -> {
                    if(s.startsWith("正在搜索")) {
                        ready.countDown();
                        // Keep the real worker alive until the test thread cancels it.
                        try { Thread.sleep(10000); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                }); throw new AssertionError("cancelled search completed"); }
                catch(Exception expected) { return true; }
            });
            check(ready.await(30,TimeUnit.SECONDS),"worker startup timeout");
            search.close(); check(future.get(5,TimeUnit.SECONDS),"cancellation did not unblock worker");
        }
        var constructor=MinimapWorld.class.getDeclaredConstructor(MinimapWorldContainer.class,String.class,RegistryKey.class);
        constructor.setAccessible(true);
        var world=constructor.newInstance(null,"test",null);
        world.addWaypointSet("test"); world.setCurrentWaypointSetId("test");
        var set=world.getCurrentWaypointSet();
        var user=new Waypoint(-640,70,-960,"用户航标","U",2); set.add(user);
        var add=MushroomWaypoints.class.getDeclaredMethod("add",MinimapWorld.class,int.class,int.class,String.class); add.setAccessible(true);
        add.invoke(null,world,-640,-960,"蘑菇岛测试"); add.invoke(null,world,-640,-960,"蘑菇岛测试");
        check(set.size()==2,"duplicate or overwritten user waypoint");
        check(set.get(0)==user,"user waypoint changed");
        var marker=set.get(1);
        check(marker.getX()==-640 && marker.getZ()==-960 && marker.getSymbol().equals("蘑"),"wrong Xaero marker");
        var temporary=Waypoint.class.getDeclaredField("temporary"); temporary.setAccessible(true);
        var yIncluded=Waypoint.class.getDeclaredField("yIncluded"); yIncluded.setAccessible(true);
        check(!temporary.getBoolean(marker) && !yIncluded.getBoolean(marker),"marker permanence/height flags");
        var legacy=new Waypoint(-672,64,-896,"蘑菇岛 [26.3] -672,-896","蘑",13); set.add(legacy);
        var rename=MushroomWaypoints.class.getDeclaredMethod("renameLegacy",MinimapWorld.class,int.class,int.class,String.class,String.class);
        rename.setAccessible(true);
        rename.invoke(null,world,-672,-896,"蘑菇岛 [26.3] -672,-896","蘑菇岛 [26.3]");
        add.invoke(null,world,-672,-896,"蘑菇岛 [26.3]");
        check(legacy.getName().equals("蘑菇岛 [26.3]") && set.size()==3,"legacy coordinate label migration duplicated waypoint");
        check(user.getName().equals("用户航标"),"migration changed user marker");
        add.invoke(null,world,-672,-896,"蘑菇岛 #8 [26.3]");
        add.invoke(null,world,-672,-896,"蘑菇岛 #2 [26.3]");
        check(set.size()==3 && legacy.getName().equals("蘑菇岛 #2 [26.3]"),"renumbered island duplicated marker");
        Files.writeString(root.resolve("validation/mushroom-client.json"),"{\"passed\":true,\"checks\":"+checks+",\"inGameTested\":false}\n");
        System.out.println("MUSHROOM_CLIENT_AUDIT checks="+checks);
    }
}
