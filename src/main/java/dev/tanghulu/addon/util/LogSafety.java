package dev.tanghulu.addon.util;

/** Shared boundary rules for the configurable AutoLog safety checks. */
public final class LogSafety {
    private LogSafety() {}
    public static boolean lowDurability(int max, int damage, int percent) {
        return max > 0 && (long) (max - damage) * 100 <= (long) max * percent;
    }
    public static boolean outsideY(double y, double first, double second) {
        return y < Math.min(first, second) || y > Math.max(first, second);
    }
    public static boolean coordinates(double x, double z, double targetX, double targetZ, double radius, boolean outside) {
        double distance = Math.hypot(x - targetX, z - targetZ);
        return outside ? distance > radius : distance <= radius;
    }
    public static boolean itemCount(int count, int threshold, boolean atLeast) {
        return atLeast ? count >= threshold : count <= threshold;
    }
}
