package dev.tanghulu.addon.util.threec;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** One rate-limited HTTP lane; a failed player never blocks other queued players indefinitely. */
public final class ThreeCQueryQueue implements AutoCloseable {
    @FunctionalInterface public interface Query {
        ThreeCPlayerService.PlayerTime query(String name) throws IOException, InterruptedException;
    }
    public interface Listener {
        void success(ThreeCPlayerService.PlayerTime data);
        void retry(String name, long attempt, String error);
    }

    private static final class Job {
        final String name;
        boolean manual, cancelled;
        long attempts;
        ScheduledFuture<?> future;
        Job(String name, boolean manual) { this.name = name; this.manual = manual; }
    }

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
        Thread.ofVirtual().name("tanghulu-3c-query").factory());
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final Query query;
    private final Listener listener;
    private final Executor delivery;
    private final long spacingNanos, retryMillis;
    // Only accessed by the single HTTP lane, including after cancellation.
    private long lastRequestNanos;

    public ThreeCQueryQueue(Query query, Listener listener, Executor delivery, long spacingMillis, long retryMillis) {
        if (spacingMillis < 0 || retryMillis < 0) throw new IllegalArgumentException("Negative interval");
        this.query = query;
        this.listener = listener;
        this.delivery = delivery;
        this.spacingNanos = TimeUnit.MILLISECONDS.toNanos(spacingMillis);
        this.retryMillis = retryMillis;
    }

    public static String key(String name) { return name.toLowerCase(Locale.ROOT); }

    public synchronized boolean enqueue(String input, boolean manual) {
        String name = ThreeCPlayerService.validateName(input);
        Job previous = jobs.get(key(name));
        if (previous != null) { previous.manual |= manual; return false; }
        Job job = new Job(name, manual);
        jobs.put(key(name), job);
        schedule(job, 0);
        return true;
    }

    public synchronized int pendingCount() { return jobs.size(); }

    public synchronized void cancelAll() { cancelMatching(null); }
    public synchronized void cancelAutomatic() { cancelMatching(false); }
    public synchronized void cancelManual() { cancelMatching(true); }

    private void cancelMatching(Boolean manual) {
        jobs.values().removeIf(job -> {
            if (manual != null && job.manual != manual) return false;
            job.cancelled = true;
            if (job.future != null) job.future.cancel(true);
            return true;
        });
    }

    private synchronized boolean current(Job job) {
        return !job.cancelled && jobs.get(key(job.name)) == job;
    }

    private synchronized void schedule(Job job, long delayMillis) {
        if (current(job)) job.future = executor.schedule(() -> run(job), delayMillis, TimeUnit.MILLISECONDS);
    }

    private void run(Job job) {
        try {
            long remaining = spacingNanos - (System.nanoTime() - lastRequestNanos);
            if (lastRequestNanos != 0 && remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining);
            if (!current(job)) return;
            lastRequestNanos = System.nanoTime();
            job.attempts++;
            var data = query.query(job.name);
            delivery.execute(() -> {
                synchronized (this) {
                    if (!current(job)) return;
                    jobs.remove(key(job.name));
                    listener.success(data);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException e) {
            String error = e.getMessage() == null ? "查询失败" : e.getMessage();
            long attempt = job.attempts;
            delivery.execute(() -> {
                synchronized (this) {
                    if (current(job)) listener.retry(job.name, attempt, error);
                }
            });
            schedule(job, retryMillis);
        }
    }

    @Override public synchronized void close() {
        cancelAll();
        executor.shutdownNow();
    }
}
