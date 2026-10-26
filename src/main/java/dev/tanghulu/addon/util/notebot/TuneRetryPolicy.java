package dev.tanghulu.addon.util.notebot;

import java.util.HashMap;
import java.util.Map;

/** Limits consecutive failures for the same block/pitch, rather than total clicks in a song. */
public final class TuneRetryPolicy<K> {
    private final Map<K, Integer> failures = new HashMap<>();
    private int total;

    public int schedule(K block, int limit) {
        int attempts = failures.getOrDefault(block, 0);
        if (attempts >= limit) return -1;
        failures.put(block, ++attempts);
        total++;
        return attempts;
    }

    public void progress(K block) { failures.remove(block); }
    public int total() { return total; }
    public int attempts(K block) { return failures.getOrDefault(block, 0); }
    public void reset() { failures.clear(); total = 0; }
    public static int backoffTicks(int attempt, int minimum) {
        return Math.max(minimum, Math.min(100, 20 * Math.min(attempt, 5)));
    }
}
