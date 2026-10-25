package dev.tanghulu.ancientapple.core;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Shared marker rules for world rendering and optional map integration. */
public final class MarkerSelection {
    private MarkerSelection() {}

    public static boolean shouldMark(ChestTarget target, boolean showCompleted) {
        return !target.openedWithApples && !target.isBlacklisted() && target.hasPrediction()
            && (target.apples > 0 || target.swiftSneakBooks > 0 || target.verificationRequired || target.serverObserved)
            && (showCompleted || target.eligible());
    }

    public static List<ChestTarget> nearest(Collection<ChestTarget> targets, boolean showCompleted,
                                           double x, double y, double z, int distance, int limit,
                                           Predicate<ChestTarget> visible) {
        double maximumSquared = (double) distance * distance;
        return targets.stream()
            .filter(target -> shouldMark(target, showCompleted))
            .filter(target -> target.distanceSquared(x, y, z) <= maximumSquared)
            .sorted(Comparator.comparingDouble(target -> target.distanceSquared(x, y, z)))
            .filter(visible)
            .limit(limit)
            .toList();
    }
}
