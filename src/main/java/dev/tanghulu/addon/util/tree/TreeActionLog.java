package dev.tanghulu.addon.util.tree;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Separate action channels keep tool selection and mining from repeatedly announcing each other. */
public final class TreeActionLog {
    private final Map<String, Object> last = new HashMap<>();
    public void reset() { last.clear(); }
    public boolean changed(String channel, Object key) {
        return !Objects.equals(last.put(channel, key), key);
    }
}
