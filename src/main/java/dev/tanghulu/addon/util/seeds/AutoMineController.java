package dev.tanghulu.addon.util.seeds;

/** Explicit mining lifecycle; stopping never schedules a later restart. */
public final class AutoMineController {
    public interface Driver {
        void start(String[] ores);
        boolean active();
        void stop();
    }
    private Driver driver;

    public void start(Driver next, String[] ores) {
        stop();
        driver = next;
        try { next.start(ores.clone()); }
        catch (RuntimeException failure) {
            driver = null;
            try { next.stop(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public boolean stop() {
        Driver previous = driver;
        driver = null;
        if (previous == null) return false;
        previous.stop();
        return true;
    }

    public boolean running() { return driver != null; }

    public boolean update(boolean hasGoals) {
        if (driver == null) return false;
        if (hasGoals && driver.active()) return false;
        stop();
        return true;
    }
}
