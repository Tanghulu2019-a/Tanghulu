package dev.tanghulu.addon.util.notebot;

/** One right click in flight. Only a server block-state change acknowledges it. */
public final class TuneConfirmation {
    private int before, elapsed, timeout;
    private boolean waiting;

    public void begin(int note, int timeoutTicks) {
        if (waiting) throw new IllegalStateException("Already waiting for a note update");
        before = note;
        elapsed = 0;
        timeout = timeoutTicks;
        waiting = true;
    }

    public boolean observe(int serverNote) {
        if (!waiting || serverNote == before) return false;
        waiting = false;
        return true;
    }

    public boolean tick() { return waiting && ++elapsed >= timeout; }
    public boolean waiting() { return waiting; }
    public int elapsedTicks() { return elapsed; }
    /** Release an expired request for a delayed retry; callers must reread the real pitch first. */
    public void retry() {
        if (!waiting || elapsed < timeout) throw new IllegalStateException("Request has not timed out");
        waiting = false;
        elapsed = 0;
    }
    public void reset() { waiting = false; elapsed = 0; }
    public static int remaining(int from, int target) { return Math.floorMod(target - from, 25); }
}
