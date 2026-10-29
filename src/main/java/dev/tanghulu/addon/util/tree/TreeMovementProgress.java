package dev.tanghulu.addon.util.tree;

/** Vertical bobbing alone is not navigation progress. */
public final class TreeMovementProgress {
    private double checkpointX, checkpointZ;
    private int stalled;
    private boolean initialized;
    public void reset() { initialized = false; stalled = 0; }
    public boolean stuck(double x, double z, int elapsedTicks) {
        if (!initialized) { checkpointX = x; checkpointZ = z; initialized = true; return false; }
        double dx = x - checkpointX, dz = z - checkpointZ;
        if (dx * dx + dz * dz >= 0.0625) {
            checkpointX = x; checkpointZ = z; stalled = 0;
        } else stalled += elapsedTicks;
        return stalled >= 60;
    }

    public static boolean alignedForPillar(double feetY, int baseY, int supports) {
        return Math.abs(feetY - (baseY + supports)) < 0.15;
    }
    public static boolean placementWindow(double feetY, int targetY) {
        // A ceiling collision or jump apex can set vertical velocity to zero.
        return feetY > targetY + 1.001;
    }
}
