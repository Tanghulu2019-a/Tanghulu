package dev.tanghulu.ancientapple.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class MarkerSelectionTest {
    private ChestTarget chest(int x, int y, int z) {
        return new ChestTarget(x, y, z, 0, 0, 1, "ancient_city", 1, Map.of());
    }

    @Test void independentIconAndBeamDistancesIncludeTheirOwnBoundary() {
        var near = chest(16, 0, 0);
        var far = chest(2048, 0, 0);
        var beyond = chest(2049, 0, 0);
        var targets = List.of(far, beyond, near);
        assertEquals(List.of(near), MarkerSelection.nearest(targets, false, .5, .5, .5, 16, 128, t -> true));
        assertEquals(List.of(near, far), MarkerSelection.nearest(targets, false, .5, .5, .5, 2048, 128, t -> true));
    }

    @Test void distanceIncludesHeightAndSupportsFarNegativeCoordinates() {
        var inside = chest(-29999999, -48, -29999999);
        var above = chest(-29999999, -31, -29999999);
        assertEquals(List.of(inside), MarkerSelection.nearest(List.of(above, inside), false,
            -29999998.5, -47.5, -29999998.5, 16, 128, t -> true));
    }

    @Test void invisibleNearChestsDoNotConsumeTheMarkerLimit() {
        var targets = new ArrayList<ChestTarget>();
        for (int x = 1; x <= 300; x++) targets.add(chest(x, 0, 0));
        var selected = MarkerSelection.nearest(targets, false, .5, .5, .5, 300, 128, t -> t.x > 100);
        assertEquals(128, selected.size());
        assertEquals(101, selected.getFirst().x);
        assertEquals(228, selected.getLast().x);
    }

    @Test void equalDistanceRetainsEncounterOrder() {
        var west = chest(-1, 0, 0);
        var east = chest(1, 0, 0);
        assertEquals(List.of(east), MarkerSelection.nearest(List.of(east, west), false,
            .5, .5, .5, 16, 1, t -> true));
    }

    @Test void showingCompletedNeverRevealsOpenedBlacklistedOrUnpredictedTargets() {
        var opened = chest(1, 0, 0); opened.openedWithApples = true;
        var blacklisted = chest(2, 0, 0); blacklisted.blacklisted = true;
        var collected = chest(3, 0, 0); collected.status = ChestTarget.Status.COLLECTED;
        var unpredicted = chest(4, 0, 0); unpredicted.lootSeed = 0;
        var empty = chest(5, 0, 0); empty.status = ChestTarget.Status.EMPTY;
        var targets = List.of(opened, blacklisted, collected, unpredicted, empty);
        assertTrue(MarkerSelection.nearest(targets, false, .5, .5, .5, 16, 128, t -> true).isEmpty());
        assertEquals(List.of(empty), MarkerSelection.nearest(targets, true, .5, .5, .5, 16, 128, t -> true));
    }

    @Test void visibilityRunsOnlyForInRangeCandidatesUntilLimitIsReached() {
        var calls = new AtomicInteger();
        var near = chest(1, 0, 0);
        var farther = chest(2, 0, 0);
        var outside = chest(100, 0, 0);
        assertEquals(List.of(near), MarkerSelection.nearest(List.of(outside, farther, near), false,
            .5, .5, .5, 16, 1, t -> { calls.incrementAndGet(); return true; }));
        assertEquals(1, calls.get());
    }

    @Test void randomizedSelectionsMatchThe017Pipeline() {
        var random = new Random(1718);
        for (int round = 0; round < 200; round++) {
            var targets = new ArrayList<ChestTarget>();
            for (int i = 0; i < 400; i++) {
                var target = chest(random.nextInt(1000) - 500, random.nextInt(100) - 64, random.nextInt(1000) - 500);
                target.status = ChestTarget.Status.values()[random.nextInt(ChestTarget.Status.values().length)];
                target.apples = random.nextInt(3);
                target.actualApples = random.nextInt(3);
                target.verificationRequired = random.nextBoolean();
                target.serverObserved = random.nextBoolean();
                target.openedWithApples = random.nextInt(8) == 0;
                target.blacklisted = random.nextInt(8) == 0;
                if (random.nextInt(8) == 0) target.lootSeed = 0;
                targets.add(target);
            }
            boolean completed = random.nextBoolean();
            double x = random.nextDouble() * 100 - 50, y = -48, z = random.nextDouble() * 100 - 50;
            int distance = 16 + random.nextInt(600), limit = random.nextInt(129);
            Predicate<ChestTarget> visible = t -> (t.x & 3) != 0;
            // Original 0.1.17: sort every eligible target before applying range and visibility.
            var original = targets.stream().filter(t -> !t.openedWithApples && !t.isBlacklisted() && t.hasPrediction()
                    && (t.apples > 0 || t.verificationRequired || t.serverObserved) && (completed || t.eligible()))
                .sorted(Comparator.comparingDouble(t -> t.distanceSquared(x, y, z))).toList().stream()
                .filter(t -> t.distanceSquared(x, y, z) <= (double) distance * distance)
                .filter(visible).limit(limit).toList();
            assertEquals(original, MarkerSelection.nearest(targets, completed, x, y, z, distance, limit, visible),
                "Mismatch in round " + round);
        }
    }
}
