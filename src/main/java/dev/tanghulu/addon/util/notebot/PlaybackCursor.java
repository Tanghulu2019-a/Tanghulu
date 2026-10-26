package dev.tanghulu.addon.util.notebot;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;

/** Hold the song tick until its chord has actually been dispatched. */
public final class PlaybackCursor<T> {
    private final ArrayDeque<T> notes = new ArrayDeque<>();
    private int tick = Integer.MIN_VALUE;

    public void prepare(int songTick, Collection<T> chord, boolean polyphonic) {
        if (tick == songTick) return;
        if (!notes.isEmpty()) throw new IllegalStateException("Previous chord is still pending");
        tick = songTick;
        for (T value : new LinkedHashSet<>(chord)) {
            notes.add(value);
            if (!polyphonic) break;
        }
    }

    public T peek() { return notes.peek(); }
    public void complete() { notes.remove(); }
    public boolean isEmpty() { return notes.isEmpty(); }
    public void reset() { notes.clear(); tick = Integer.MIN_VALUE; }
}
