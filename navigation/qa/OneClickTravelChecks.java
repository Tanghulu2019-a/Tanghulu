package dev.tanghulu.stash.navigation;

public final class OneClickTravelChecks {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void reject(String text) {
        try { OneClickTravel.Destination.parse(text); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Accepted invalid coordinates: " + text);
    }

    private static final class FakeDriver implements OneClickTravel.Driver {
        boolean owned = true, arrived, active = true, failStart, failStop;
        int starts, stops, restores;
        boolean cleared;
        @Override public void start() {
            starts++;
            if (failStart) throw new IllegalStateException("Unable to start");
        }
        @Override public boolean ownsGoal() { return owned; }
        @Override public boolean arrived() { return arrived; }
        @Override public boolean active() { return active; }
        @Override public void stop(boolean clear) {
            stops++;
            cleared = clear;
            if (failStop) throw new IllegalStateException("Unable to stop");
        }
        @Override public void restoreSettings() { restores++; }
    }

    public static void main(String[] args) {
        var xyz = OneClickTravel.Destination.parse(" 1000 64 -2000 ");
        check(xyz.x() == 1000 && xyz.y() == 64 && xyz.z() == -2000, "Parse XYZ");
        var xz = OneClickTravel.Destination.parse("-100,200");
        check(xz.x() == -100 && xz.y() == null && xz.z() == 200 && xz.flightY() == 64, "Parse XZ");
        check(OneClickTravel.Destination.parse("0，-64，0").y() == -64, "Chinese commas and negative Y");
        check(OneClickTravel.Destination.parse("0 0").x() == 0, "Origin is a valid destination");
        check(OneClickTravel.Destination.parse("29999984 -29999984").z() == -29999984, "Coordinate boundary");
        for (String text : new String[]{"", "  ", "1", "1 2 3 4", "1.5 2", "~ ~", "abc 1",
            "29999985 0", "0 -29999985", "-2147483648 0", "2147483648 0", "1 9999999999999 2"}) reject(text);

        var driver = new FakeDriver();
        var journey = new OneClickTravel(driver, "100 64 200", true);
        check(driver.starts == 1 && journey.tick() == OneClickTravel.Result.RUNNING, "Starts exactly once");
        check(!journey.shouldClear("100 64 200"), "No clearing during navigation");
        driver.arrived = true;
        driver.active = false;
        check(journey.tick() == OneClickTravel.Result.ARRIVED, "Arrival recognized after process stops");
        check(driver.stops == 1 && driver.cleared && driver.restores == 1, "Arrival clears Baritone goal and restores settings");
        check(journey.shouldClear("100 64 200"), "Clear saved coordinates on arrival");
        check(!journey.shouldClear("900 64 800"), "Preserve coordinates edited during journey");
        journey.tick();
        journey.cancel();
        check(driver.stops == 1 && driver.restores == 1, "Completed journey is idempotent");

        driver = new FakeDriver();
        journey = new OneClickTravel(driver, "0 0", false);
        driver.arrived = true;
        journey.tick();
        check(!driver.cleared && !journey.shouldClear("0 0"), "Keep goal and coordinates when clearing is disabled");

        driver = new FakeDriver();
        journey = new OneClickTravel(driver, "1 2", true);
        driver.active = false;
        check(journey.tick() == OneClickTravel.Result.INTERRUPTED, "Failed or cancelled path is interrupted");
        check(!driver.cleared && !journey.shouldClear("1 2") && driver.restores == 1, "Failure retains coordinates");

        driver = new FakeDriver();
        journey = new OneClickTravel(driver, "1 2", true);
        driver.owned = false;
        driver.arrived = true;
        check(journey.tick() == OneClickTravel.Result.INTERRUPTED, "Replaced goal is not arrival");
        check(driver.stops == 0 && driver.restores == 1 && !journey.shouldClear("1 2"), "Do not stop replacement goal");

        driver = new FakeDriver();
        journey = new OneClickTravel(driver, "1 2", true);
        journey.cancel();
        journey.cancel();
        check(driver.stops == 1 && !driver.cleared && driver.restores == 1, "Manual stop or disconnect preserves goal and restores settings once");
        check(!journey.shouldClear("1 2"), "Manual stop never clears coordinates");

        driver = new FakeDriver();
        journey = new OneClickTravel(driver, "1 2", true);
        driver.owned = false;
        journey.cancel();
        check(driver.stops == 0 && driver.restores == 1, "Disconnect does not stop a replacement goal");

        driver = new FakeDriver();
        driver.failStart = true;
        try { new OneClickTravel(driver, "1 2", true); throw new AssertionError("Expected startup error"); }
        catch (IllegalStateException expected) {
            check(driver.stops == 1 && driver.restores == 1 && !driver.cleared, "Restore after partial startup failure");
        }

        driver = new FakeDriver();
        journey = new OneClickTravel(driver, "1 2", true);
        driver.failStop = true;
        try { journey.cancel(); throw new AssertionError("Expected cancellation error"); }
        catch (IllegalStateException expected) { check(driver.restores == 1, "Restore settings even if cancellation throws"); }
        System.out.println("Baritone assistant checks passed: " + checks);
    }
}
