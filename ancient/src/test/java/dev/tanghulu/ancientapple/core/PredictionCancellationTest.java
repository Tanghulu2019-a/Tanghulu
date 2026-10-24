package dev.tanghulu.ancientapple.core;

import dev.tanghulu.ancientapple.prediction.PredictionEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class PredictionCancellationTest {
    @TempDir Path directory;
    @Test void cancelKeepsSharedJarReadableForRunningTaskAndNextSearch() throws Exception { probe(false); }
    @Test void closeNeverInterruptsAnInFlightJarRead() throws Exception { probe(true); }
    private void probe(boolean close) throws Exception {
        Path jar=directory.resolve("game.zip");
        try(var zip=new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("rules.json")); zip.write("{\"ok\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
        }
        var engine=new PredictionEngine();
        var executorField=PredictionEngine.class.getDeclaredField("executor"); executorField.setAccessible(true);
        var jobField=PredictionEngine.class.getDeclaredField("job"); jobField.setAccessible(true);
        var executor=(ExecutorService)executorField.get(engine);
        var entered=new CountDownLatch(1); var release=new Semaphore(0); var result=new CompletableFuture<String>();
        try(var filesystem=FileSystems.newFileSystem(jar)) {
            Future<?> running=executor.submit(() -> {
                entered.countDown(); release.acquireUninterruptibly();
                try { result.complete(Files.readString(filesystem.getPath("/rules.json"))); }
                catch(Throwable e) { result.completeExceptionally(e); }
            });
            jobField.set(engine,running);
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            if(close)engine.close(); else engine.cancel();
            release.release();
            assertEquals("{\"ok\":true}",result.get(5,TimeUnit.SECONDS));
            assertEquals("{\"ok\":true}",Files.readString(filesystem.getPath("/rules.json")));
            if(!close)assertEquals("{\"ok\":true}",executor.submit(() -> Files.readString(filesystem.getPath("/rules.json"))).get(5,TimeUnit.SECONDS));
        } finally { release.release(); engine.close(); }
    }
}
