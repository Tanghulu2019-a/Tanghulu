package dev.tanghulu.addon.util.tree;

/** Normal completion requires return and collection; an explicitly skipped tree can be abandoned. */
public final class TreeCycle {
    public enum Phase { CUT, RETURN, COLLECT, DONE }
    public enum Collection { WAIT, COMPLETE, TIMED_OUT }
    private Phase phase = Phase.CUT;
    private int index, elapsed;
    private final int trees, settleTicks;
    public TreeCycle(int trees, int settleTicks) {
        if (trees < 1 || settleTicks < 1) throw new IllegalArgumentException();
        this.trees = trees; this.settleTicks = settleTicks;
    }
    public Phase phase() { return phase; }
    public int index() { return index; }
    public void returnToGround() { if (phase == Phase.CUT) phase = Phase.RETURN; }
    public TreeCycle resumeCut(boolean routeEmpty) {
        if (phase != Phase.RETURN || !routeEmpty) throw new IllegalStateException("Return must complete before relocation");
        phase = Phase.CUT;
        return this;
    }
    public void returned(boolean routeEmpty) {
        if (phase != Phase.RETURN || !routeEmpty) return;
        phase = Phase.COLLECT; elapsed = 0;
    }
    public Collection observeDrops(int drops, boolean capacity, int ticks) {
        if (drops < 0 || ticks < 1) throw new IllegalArgumentException();
        if (phase != Phase.COLLECT) return Collection.WAIT;
        elapsed += ticks;
        if (elapsed < settleTicks) return Collection.WAIT;
        if (drops > 0 || !capacity) return Collection.TIMED_OUT;
        advance();
        return Collection.COMPLETE;
    }
    public void skip() {
        if (phase == Phase.DONE) return;
        advance();
    }
    private void advance() {
        index++;
        phase = index == trees ? Phase.DONE : Phase.CUT;
        elapsed = 0;
    }
}
