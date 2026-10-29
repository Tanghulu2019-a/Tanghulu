package dev.tanghulu.addon.util.tree;

/** Interaction reach alone never means the player has reached the work position. */
public final class TreeApproach {
    private TreePlan.Pos stand;
    private int settledTicks, lastTick = -1;

    public void reset() { stand = null; settledTicks = 0; lastTick = -1; }

    public boolean update(TreePlan.Pos target, double x, double y, double z, boolean grounded, int tick) {
        if (!at(target, x, y, z, grounded)) { reset(); return false; }
        if (!target.equals(stand) || tick != lastTick + 1) {
            // Repeated checks within one tick cannot count as extra settling time.
            if (target.equals(stand) && tick == lastTick) return ready(target, x, y, z, grounded, tick);
            settledTicks = 0;
        }
        stand = target; lastTick = tick; settledTicks++;
        return ready(target, x, y, z, grounded, tick);
    }

    public boolean ready(TreePlan.Pos target, double x, double y, double z, boolean grounded, int tick) {
        return target != null && target.equals(stand) && lastTick == tick && settledTicks >= 3
            && at(target, x, y, z, grounded);
    }

    private static boolean at(TreePlan.Pos target, double x, double y, double z, boolean grounded) {
        if (target == null || !grounded) return false;
        double dx = x - target.x() - 0.5, dz = z - target.z() - 0.5;
        return dx * dx + dz * dz < 0.008 && Math.abs(y - target.y()) < 0.15;
    }
}
