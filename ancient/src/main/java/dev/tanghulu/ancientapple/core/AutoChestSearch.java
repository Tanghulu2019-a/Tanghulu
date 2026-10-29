package dev.tanghulu.ancientapple.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serializes open requests and remembers completed containers for this run. */
public final class AutoChestSearch<K> {
    public record Candidate<K>(K key, double distanceSquared) {}
    private final Map<K, Long> skipped = new LinkedHashMap<>();
    private K pending;
    private long ticks, deadline, nextAttempt;

    public void reset() { skipped.clear(); pending = null; ticks = deadline = nextAttempt = 0; }
    public void tick() {
        ticks++;
        if (pending != null && ticks >= deadline) { pending = null; nextAttempt = ticks + 4; }
    }
    public boolean canSearch() { return pending == null && ticks >= nextAttempt; }
    public K select(List<Candidate<K>> candidates, double range) {
        if (!canSearch() || !Double.isFinite(range) || range <= 0) return null;
        K result = null;
        double nearest = range * range;
        for (var candidate : candidates) {
            double distance = candidate.distanceSquared();
            if (!Double.isFinite(distance) || distance < 0 || distance > nearest
                || skipped.getOrDefault(candidate.key(), 0L) > ticks) continue;
            if (result == null || distance < nearest) { result = candidate.key(); nearest = distance; }
        }
        return result;
    }
    public void attempted(K key, Collection<K> halves, int timeoutTicks) {
        if (pending != null) throw new IllegalStateException("An open request is already pending");
        pending = key;
        deadline = ticks + Math.max(40, timeoutTicks);
        // Rejected/locked chests must not monopolize the next scan.
        remember(halves, deadline + 200);
    }
    public void opened(Collection<K> halves) {
        pending = null;
        remember(halves, ticks + 200);
        nextAttempt = ticks + 4;
    }
    public void completed(Collection<K> halves) {
        pending = null;
        remember(halves, Long.MAX_VALUE);
        nextAttempt = ticks + 4;
    }
    private void remember(Collection<K> halves, long until) {
        for (K key : halves) {
            skipped.merge(key, until, Math::max);
            while (skipped.size() > 4096) skipped.remove(skipped.keySet().iterator().next());
        }
    }
}
