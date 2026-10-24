package dev.tanghulu.threec;

import dev.tanghulu.addon.util.threec.ThreeCPlayerService.PlayerTime;
import dev.tanghulu.addon.util.threec.ThreeCQueryQueue;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises real scheduling, interruption and stale callback delivery without the public API. */
public final class ThreeCQueueChecks {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void await(CountDownLatch latch) throws Exception {
        check(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for query lane");
    }
    private static ThreeCQueryQueue.Listener listener(CountDownLatch success, AtomicInteger retries) {
        return new ThreeCQueryQueue.Listener() {
            @Override public void success(PlayerTime data) { success.countDown(); }
            @Override public void retry(String name, long attempt, String error) { retries.incrementAndGet(); }
        };
    }
    public static void run() throws Exception {
        var calls = new AtomicInteger();
        var retries = new AtomicInteger();
        var done = new CountDownLatch(1);
        try (var queue = new ThreeCQueryQueue(name -> {
            if (calls.incrementAndGet() <= 8) throw new IOException("Temporary API error");
            return new PlayerTime(name, 86400, null);
        }, listener(done, retries), Runnable::run, 0, 1)) {
            queue.enqueue("Tester", true);
            await(done);
            check(calls.get() == 9 && retries.get() == 8, "Retry beyond an arbitrary attempt limit until success");
            check(queue.pendingCount() == 0, "Success removes request");
        }

        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        done = new CountDownLatch(1);
        calls.set(0);
        try (var queue = new ThreeCQueryQueue(name -> {
            calls.incrementAndGet(); started.countDown(); release.await();
            return new PlayerTime(name, 0, null);
        }, listener(done, retries), Runnable::run, 0, 1)) {
            check(queue.enqueue("Tester", false), "Initial automatic request");
            await(started);
            check(!queue.enqueue("TESTER", true), "Case-insensitive deduplication and promotion to manual");
            queue.cancelAutomatic();
            check(queue.pendingCount() == 1, "Disabling automatic queries preserves promoted manual request");
            release.countDown();
            await(done);
            check(calls.get() == 1, "Only one HTTP call per queued name");
        }

        var firstFail = new CountDownLatch(1);
        done = new CountDownLatch(2);
        List<String> order = new CopyOnWriteArrayList<>();
        try (var queue = new ThreeCQueryQueue(name -> {
            order.add(name);
            if (name.equals("Broken") && order.size() == 1) { firstFail.countDown(); throw new IOException("retry"); }
            return new PlayerTime(name, 1, null);
        }, listener(done, retries), Runnable::run, 0, 100)) {
            queue.enqueue("Broken", false);
            await(firstFail);
            queue.enqueue("Healthy", false);
            await(done);
            check(order.equals(List.of("Broken", "Healthy", "Broken")), "Failed player yields to other players before retry");
        }

        var inFlight = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        done = new CountDownLatch(1);
        calls.set(0);
        try (var queue = new ThreeCQueryQueue(name -> {
            calls.incrementAndGet(); inFlight.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException e) { interrupted.countDown(); throw e; }
            return new PlayerTime(name, 0, null);
        }, listener(done, retries), Runnable::run, 0, 10)) {
            queue.enqueue("Tester", false);
            await(inFlight);
            queue.cancelAll();
            await(interrupted);
            check(queue.pendingCount() == 0 && calls.get() == 1, "Cancellation interrupts HTTP and clears retry queue");
            check(done.getCount() == 1, "Cancelled request delivers no success");
        }

        var callbacks = new LinkedBlockingQueue<Runnable>();
        done = new CountDownLatch(1);
        retries.set(0);
        try (var queue = new ThreeCQueryQueue(name -> new PlayerTime(name, 0, null),
            listener(done, retries), callbacks::add, 0, 10)) {
            queue.enqueue("Tester", true);
            Runnable stale = callbacks.poll(5, TimeUnit.SECONDS);
            check(stale != null, "Success queued for client thread");
            queue.cancelAll();
            stale.run();
            check(done.getCount() == 1, "Queued old success ignored after cancel or world switch");
        }
        callbacks.clear();
        try (var queue = new ThreeCQueryQueue(name -> { throw new IOException("missing duration"); },
            listener(new CountDownLatch(1), retries), callbacks::add, 0, 100)) {
            queue.enqueue("Tester", false);
            Runnable stale = callbacks.poll(5, TimeUnit.SECONDS);
            check(stale != null, "Failure queued for client thread");
            queue.cancelAutomatic();
            stale.run();
            check(retries.get() == 0 && queue.pendingCount() == 0, "No stale retry notification after automatic cancel");
        }

        List<Long> timestamps = new CopyOnWriteArrayList<>();
        done = new CountDownLatch(3);
        try (var queue = new ThreeCQueryQueue(name -> {
            timestamps.add(System.nanoTime()); return new PlayerTime(name, 0, null);
        }, listener(done, retries), Runnable::run, 30, 1)) {
            for (String name : List.of("Alice", "Bobby", "Carol")) queue.enqueue(name, false);
            await(done);
            check(timestamps.get(1) - timestamps.get(0) >= TimeUnit.MILLISECONDS.toNanos(25)
                && timestamps.get(2) - timestamps.get(1) >= TimeUnit.MILLISECONDS.toNanos(25), "Rate limit shared across all players");
        }
        System.out.println("3C queue checks passed: " + checks);
    }
}
