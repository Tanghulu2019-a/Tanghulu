package dev.tanghulu.ancientapple.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ManualCollectionTrackerTest {
    private static int[] snapshot(int rows,int chest,int owned) {
        int[] counts=new int[rows*9+36]; counts[0]=chest; counts[counts.length-1]=owned; return counts;
    }
    private ManualCollectionTracker opened(int rows,int chest,int owned) {
        var tracker=new ManualCollectionTracker(); tracker.opened("chest-a",7);
        tracker.full(7,snapshot(rows,chest,owned)); tracker.cursor(0); return tracker;
    }
    @Test void clientPredictionAloneAndUnrelatedPacketsCannotConfirmCollection() {
        var t=opened(3,2,3); t.predictedClick(2,3,0,5);
        assertTrue(t.confirmed(5).isEmpty());
        t.playerSlot(0,0); assertTrue(t.confirmed(5).isEmpty());
    }
    @Test void authoritativeChestAndInventoryPacketsCanArriveInEitherOrder() {
        for(boolean playerFirst:new boolean[]{false,true}) {
            var t=opened(3,2,3); t.predictedClick(2,3,0,5);
            if(playerFirst)t.slot(7,62,5); else t.slot(7,0,0);
            assertTrue(t.confirmed(5).isEmpty());
            if(playerFirst)t.slot(7,0,0); else t.slot(7,62,5);
            assertEquals(List.of("chest-a"),t.confirmed(5));
            assertTrue(t.confirmed(5).isEmpty());
        }
    }
    @Test void normalReopenConfirmsAQuickMoveWithoutDeltasForADoubleChest() {
        var t=opened(6,2,3); t.predictedClick(2,3,0,5);
        t.opened("chest-a",8); t.full(8,snapshot(6,0,5)); t.cursor(0);
        assertEquals(List.of("chest-a"),t.confirmed(5));
    }
    @Test void openingAnotherChestCannotConfirmTheFirstChest() {
        var t=opened(3,2,0); t.predictedClick(2,0,0,2);
        t.opened("chest-b",8); t.full(8,snapshot(3,0,2)); t.cursor(0);
        assertTrue(t.confirmed(2).isEmpty()); assertEquals(1,t.pending());
    }
    @Test void partialWithdrawalKeepsChestAvailableUntilAllApplesWereTaken() {
        var t=opened(3,2,0); t.predictedClick(2,0,1,1);
        t.full(7,snapshot(3,1,1)); assertTrue(t.confirmed(1).isEmpty());
        t.predictedClick(1,1,0,2); t.full(7,snapshot(3,0,2));
        assertEquals(List.of("chest-a"),t.confirmed(2));
    }
    @Test void rejectedClicksAndOtherPlayersRemovingContentsDoNotCreateReceipts() {
        var t=opened(3,2,0); t.predictedClick(2,0,0,2);
        t.full(7,snapshot(3,2,0)); assertTrue(t.confirmed(0).isEmpty());
        t.full(7,snapshot(3,0,0)); assertTrue(t.confirmed(0).isEmpty());
        var observed=opened(3,2,0); observed.full(7,snapshot(3,0,2));
        assertTrue(observed.confirmed(2).isEmpty());
    }
    @Test void pickupCursorAndInventoryUpdatesCannotTemporarilyDoubleCountApples() {
        var t=opened(3,2,0); t.predictedClick(2,0,0,2);
        t.slot(7,0,0); t.cursor(2);
        // Player already placed the cursor contents into inventory locally.
        t.slot(7,62,2); assertTrue(t.confirmed(2).isEmpty());
        t.cursor(0); assertEquals(List.of("chest-a"),t.confirmed(2));
    }
    @Test void cursorPickupCanBeConfirmedWithoutMovingItemsIntoTheBackpack() {
        var t=opened(3,2,0); t.predictedClick(2,0,0,2);
        t.slot(7,0,0); t.cursor(2);
        assertEquals(List.of("chest-a"),t.confirmed(2));
    }
    @Test void returningApplesToTheChestCancelsPendingCollection() {
        var t=opened(3,2,0); t.predictedClick(2,0,0,2);
        t.predictedClick(0,2,2,0); assertEquals(0,t.pending());
        t.full(7,snapshot(3,0,2)); assertTrue(t.confirmed(2).isEmpty());
    }
    @Test void changingWorldClearsPendingReceipts() {
        var t=opened(3,2,0); t.predictedClick(2,0,0,2); t.reset();
        t.opened("chest-a",8); t.full(8,snapshot(3,0,2)); t.cursor(0);
        assertTrue(t.confirmed(2).isEmpty()); assertEquals(0,t.pending());
    }
}
