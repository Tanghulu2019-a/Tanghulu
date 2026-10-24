package dev.tanghulu.ancientapple.core;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class Calibration {
    public enum Result { MATCH, EMPTY, MISMATCH, DUPLICATE }
    private final Set<String> matched = new HashSet<>();
    private boolean failed;
    public Result observe(ChestTarget target, Map<String,Integer> actual) {
        if (actual.isEmpty()) return Result.EMPTY;
        if (!actual.equals(target.expected)) { failed=true; return Result.MISMATCH; }
        if (!matched.add(target.key())) return Result.DUPLICATE;
        return Result.MATCH;
    }
    public boolean ready() { return !failed && matched.size() >= 3; }
    public boolean allowsAutomatic(boolean skipSamples) { return skipSamples || ready(); }
    public int matches() { return matched.size(); }
    public void reset() { matched.clear(); failed=false; }
    public Set<String> matchedKeys() { return Set.copyOf(matched); }
    public boolean failed() { return failed; }
    public void restore(Set<String> keys, boolean failed) { reset(); matched.addAll(keys); this.failed=failed; }
}
