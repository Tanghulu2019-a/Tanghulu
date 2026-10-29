package dev.tanghulu.ancientapple.core;

/** One shift-click per tick, followed by a quiet period before closing. */
public final class QuickLootSession {
    public interface Container {
        int size();
        boolean selected(int slot);
        boolean move(int slot);
        void close();
    }
    public enum Result { MOVED, WAITING, BLOCKED, CLOSED }
    private int quietTicks;
    private boolean closed;

    public Result tick(Container container, int settleTicks) {
        if (closed) return Result.CLOSED;
        boolean remaining = false;
        for (int slot = 0; slot < container.size(); slot++) {
            if (!container.selected(slot)) continue;
            remaining = true;
            if (container.move(slot)) {
                quietTicks = 0;
                return Result.MOVED;
            }
        }
        if (remaining) {
            quietTicks = 0;
            return Result.BLOCKED;
        }
        if (++quietTicks < Math.max(2, settleTicks)) return Result.WAITING;
        closed = true;
        container.close();
        return Result.CLOSED;
    }
}
