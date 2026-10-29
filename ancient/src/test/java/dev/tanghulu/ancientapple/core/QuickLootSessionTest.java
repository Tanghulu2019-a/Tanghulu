package dev.tanghulu.ancientapple.core;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static dev.tanghulu.ancientapple.core.QuickLootSession.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class QuickLootSessionTest {
    private static class Chest implements QuickLootSession.Container {
        final int[] counts;
        final boolean[] selected;
        int capacity = Integer.MAX_VALUE, clicks, closes;
        Chest(int size) { counts = new int[size]; selected = new boolean[size]; }
        public int size() { return counts.length; }
        public boolean selected(int slot) { return selected[slot] && counts[slot] > 0; }
        public boolean move(int slot) {
            int moved = Math.min(capacity, counts[slot]);
            if (moved == 0) return false;
            capacity -= moved; counts[slot] -= moved; clicks++;
            return true;
        }
        public void close() { closes++; }
    }
    @Test void takesOnlySelectedStacksAndClosesAfterSettling() {
        var chest = new Chest(27);
        chest.counts[0] = 2; chest.selected[0] = true;
        chest.counts[1] = 64;
        chest.counts[26] = 12; chest.selected[26] = true;
        var session = new QuickLootSession();
        assertEquals(MOVED, session.tick(chest, 4));
        assertEquals(1, chest.clicks);
        assertEquals(MOVED, session.tick(chest, 4));
        for (int i = 0; i < 3; i++) assertEquals(WAITING, session.tick(chest, 4));
        assertEquals(CLOSED, session.tick(chest, 4));
        assertEquals(64, chest.counts[1]);
        assertEquals(1, chest.closes);
        session.tick(chest, 4);
        assertEquals(1, chest.closes);
    }
    @Test void doubleChestIncludesLastChestSlot() {
        var chest = new Chest(54);
        chest.counts[53] = 1; chest.selected[53] = true;
        assertEquals(MOVED, new QuickLootSession().tick(chest, 2));
        assertEquals(0, chest.counts[53]);
    }
    @Test void fullInventoryDoesNotCloseAndResumesWhenSpaceIsFreed() {
        var chest = new Chest(27);
        chest.counts[0] = 8; chest.selected[0] = true; chest.capacity = 0;
        var session = new QuickLootSession();
        for (int i = 0; i < 20; i++) assertEquals(BLOCKED, session.tick(chest, 4));
        assertEquals(0, chest.closes);
        chest.capacity = 8;
        assertEquals(MOVED, session.tick(chest, 4));
    }
    @Test void partiallyTransferredStackKeepsChestOpen() {
        var chest = new Chest(27);
        chest.counts[0] = 64; chest.selected[0] = true; chest.capacity = 3;
        var session = new QuickLootSession();
        assertEquals(MOVED, session.tick(chest, 2));
        assertEquals(61, chest.counts[0]);
        assertEquals(BLOCKED, session.tick(chest, 2));
        assertEquals(0, chest.closes);
    }
    @Test void blockedStackDoesNotPreventTakingOtherSelectedStacks() {
        var chest = new Chest(27) {
            @Override public boolean move(int slot) { return slot == 0 ? false : super.move(slot); }
        };
        chest.counts[0] = chest.counts[1] = 1;
        chest.selected[0] = chest.selected[1] = true;
        assertEquals(MOVED, new QuickLootSession().tick(chest, 2));
        assertEquals(1, chest.counts[0]);
        assertEquals(0, chest.counts[1]);
    }
    @Test void serverCorrectionRestartsQuietPeriod() {
        var chest = new Chest(27);
        chest.counts[0] = 1; chest.selected[0] = true;
        var session = new QuickLootSession();
        assertEquals(MOVED, session.tick(chest, 4));
        assertEquals(WAITING, session.tick(chest, 4));
        chest.counts[0] = 1; // A correction restores the locally predicted withdrawal.
        assertEquals(MOVED, session.tick(chest, 4));
        for (int i = 0; i < 3; i++) assertEquals(WAITING, session.tick(chest, 4));
        assertEquals(CLOSED, session.tick(chest, 4));
    }
    @Test void emptyChestAndChestWithoutTargetsCloseWithoutClicks() {
        for (boolean empty : new boolean[]{true, false}) {
            var chest = new Chest(27);
            if (!empty) Arrays.fill(chest.counts, 64);
            var session = new QuickLootSession();
            assertEquals(WAITING, session.tick(chest, 2));
            assertEquals(CLOSED, session.tick(chest, 2));
            assertEquals(0, chest.clicks);
        }
    }
}
