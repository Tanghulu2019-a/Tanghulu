package dev.tanghulu.stash.navigation;

/** A single, explicitly started journey. Independent of the Minecraft client for lifecycle checks. */
public final class OneClickTravel {
    public record Destination(int x, Integer y, int z) {
        public static Destination parse(String text) {
            String[] parts = text.trim().split("[\\s,，]+");
            if (parts.length != 2 && parts.length != 3) {
                throw new IllegalArgumentException("请输入 X Z 或 X Y Z 整数坐标，例如：1000 64 -2000。");
            }
            try {
                int x = Integer.parseInt(parts[0]);
                int z = Integer.parseInt(parts[parts.length - 1]);
                Integer y = parts.length == 3 ? Integer.valueOf(parts[1]) : null;
                if (Math.abs((long) x) > 29999984 || Math.abs((long) z) > 29999984) {
                    throw new IllegalArgumentException("X、Z 坐标必须在 ±29999984 范围内。");
                }
                return new Destination(x, y, z);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("目的地必须是 X Z 或 X Y Z 整数坐标。");
            }
        }

        public int flightY() { return y == null ? 64 : y; }
    }

    public interface Driver {
        void start();
        boolean ownsGoal();
        boolean arrived();
        boolean active();
        void stop(boolean clearGoal);
        void restoreSettings();
    }

    public enum Result { RUNNING, ARRIVED, INTERRUPTED }

    private final Driver driver;
    private final String coordinates;
    private final boolean clearOnArrival;
    private Result result = Result.RUNNING;

    public OneClickTravel(Driver driver, String coordinates, boolean clearOnArrival) {
        this.driver = driver;
        this.coordinates = coordinates;
        this.clearOnArrival = clearOnArrival;
        try {
            driver.start();
        } catch (RuntimeException | LinkageError e) {
            cancel();
            throw e;
        }
    }

    public Result tick() {
        if (result != Result.RUNNING) return result;
        if (!driver.ownsGoal()) {
            result = Result.INTERRUPTED;
            driver.restoreSettings();
        } else if (driver.arrived()) {
            // A cleanup error must not turn into coordinate deletion on a later tick.
            result = Result.INTERRUPTED;
            stopAndRestore(clearOnArrival);
            result = Result.ARRIVED;
        } else if (!driver.active()) {
            result = Result.INTERRUPTED;
            stopAndRestore(false);
        }
        return result;
    }

    public void cancel() {
        if (result != Result.RUNNING) return;
        result = Result.INTERRUPTED;
        try {
            if (driver.ownsGoal()) driver.stop(false);
        } finally {
            driver.restoreSettings();
        }
    }

    private void stopAndRestore(boolean clearGoal) {
        try {
            driver.stop(clearGoal);
        } finally {
            driver.restoreSettings();
        }
    }

    /** Do not erase coordinates edited while an earlier journey was running. */
    public boolean shouldClear(String currentCoordinates) {
        return result == Result.ARRIVED && clearOnArrival && coordinates.equals(currentCoordinates);
    }
}
