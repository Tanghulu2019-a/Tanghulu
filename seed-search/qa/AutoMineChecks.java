package dev.tanghulu.seedsearch;

import dev.tanghulu.addon.util.seeds.AutoMineController;

public final class AutoMineChecks {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static final class Driver implements AutoMineController.Driver {
        int starts, stops;
        boolean active, failStart;
        public void start(String[] ores) {
            starts++;
            active = true;
            if (failStart) throw new IllegalStateException("test startup failure");
        }
        public boolean active() { return active; }
        public void stop() { stops++; active = false; }
    }
    public static void main(String[] args) {
        var control = new AutoMineController();
        var first = new Driver();
        check(!control.running() && !control.stop(), "Stopped by default; repeated stop is safe");
        control.start(first, new String[]{"diamond_ore"});
        check(control.running() && first.starts == 1 && first.active, "Start invokes actual driver immediately");
        check(!control.update(true) && first.starts == 1, "Ticks do not restart an active job");
        check(control.stop() && first.stops == 1 && !first.active, "Stop cancels the driver, not only a GUI flag");
        for (int i = 0; i < 100; i++) control.update(true);
        check(!control.running() && first.starts == 1, "Stopped job cannot restart on a later tick");
        check(!control.stop() && first.stops == 1, "Repeated stop does not cancel unrelated tasks");
        control.start(first, new String[]{"iron_ore"});
        var next = new Driver();
        control.start(next, new String[]{"gold_ore"});
        check(first.stops == 2 && next.starts == 1, "Restart releases the previous job");
        check(control.update(false) && !control.running() && next.stops == 1, "Target exhaustion stops paths");
        control.start(next, new String[]{"gold_ore"}); next.active = false;
        check(control.update(true) && !control.running(), "Externally ended job stays stopped");
        var failed = new Driver(); failed.failStart = true;
        try { control.start(failed, new String[]{"coal_ore"}); throw new AssertionError("Missing startup failure"); }
        catch (IllegalStateException expected) { checks++; }
        check(!control.running() && failed.stops == 1 && !failed.active, "Failed startup releases the driver");
        System.out.println("Auto mining lifecycle checks passed: " + checks);
    }
}
