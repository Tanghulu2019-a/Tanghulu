package dev.tanghulu.ancientapple.core;

import dev.tanghulu.ancientapple.integration.XaeroBridge;
import dev.tanghulu.ancientapple.storage.WorldStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OpenedMarkerTest {
    @TempDir Path directory;
    private ChestTarget chest(int x) { return new ChestTarget(x,-49,0,0,0,1,"ancient_city",2,Map.of("apple",2)); }
    private int[] snapshot(int rows,int chest,int player) {
        int[] counts=new int[rows*9+36]; counts[0]=chest; counts[counts.length-1]=player; return counts;
    }

    @Test void openingRequiresServerContentsAndDoesNotRequireTakingApples() {
        var tracker=new ManualCollectionTracker(); tracker.opened("first",7);
        assertNull(tracker.openedWithApples());
        tracker.predictedClick(2,0,0,2);
        assertNull(tracker.openedWithApples());
        tracker.slot(7,0,2); // No initial full snapshot: do not infer container contents.
        assertNull(tracker.openedWithApples());
        tracker.full(7,snapshot(3,2,0));
        assertEquals("first",tracker.openedWithApples());
        assertTrue(tracker.confirmed(0).isEmpty()); // Opening must not fabricate collection history.
    }

    @Test void playerInventoryAndWrongContainerPacketsCannotDismissMarkers() {
        var tracker=new ManualCollectionTracker(); tracker.opened("first",7);
        tracker.full(8,snapshot(3,2,0)); assertNull(tracker.openedWithApples());
        tracker.full(0,new int[46]); tracker.playerSlot(0,2); tracker.cursor(2);
        assertNull(tracker.openedWithApples());
        tracker.full(7,snapshot(3,0,12)); assertNull(tracker.openedWithApples());
        tracker.slot(7,62,14); assertNull(tracker.openedWithApples());
    }

    @Test void fullSnapshotAndLaterServerSlotBothDetectApplesIncludingSecondHalf() {
        var tracker=new ManualCollectionTracker(); tracker.opened("first|second",7);
        int[] contents=snapshot(6,0,0); contents[53]=2;
        tracker.full(7,contents); assertEquals("first|second",tracker.openedWithApples());
        tracker.opened("third",8); tracker.full(8,snapshot(3,0,0));
        assertNull(tracker.openedWithApples());
        tracker.slot(8,26,1); assertEquals("third",tracker.openedWithApples());
    }

    @Test void unrelatedScreensAndWorldChangesDropThePreviousOpening() {
        var tracker=new ManualCollectionTracker(); tracker.opened("first",7);
        tracker.full(7,snapshot(3,2,0));
        tracker.opened(null,8); tracker.full(8,snapshot(3,2,0));
        assertNull(tracker.openedWithApples());
        tracker.reset(); assertNull(tracker.openedWithApples());
    }

    @Test void dismissedMarkersStayHiddenWithCompletedDisplayAndWithoutBlacklisting() {
        var first=chest(1); var second=chest(2); var adjacent=chest(3);
        first.openedWithApples=true; second.openedWithApples=true;
        for(boolean completed:new boolean[]{true,false}) {
            assertFalse(XaeroBridge.shouldMark(first,completed));
            assertFalse(XaeroBridge.shouldMark(second,completed));
            assertTrue(XaeroBridge.shouldMark(adjacent,completed));
        }
        assertFalse(first.isBlacklisted()); assertEquals(ChestTarget.Status.PREDICTED,first.status);
    }

    @Test void restartAndRescanRetainDismissalAndKeepCollectionHistorySeparate() throws Exception {
        var target=chest(1); target.openedWithApples=true;
        var store=new WorldStore(directory,"server|overworld",5,"26.1");
        store.write(List.of(target),new Calibration());
        var loaded=new WorldStore(directory,"server|overworld",5,"26.1").read().targets.getFirst();
        var updated=chest(1); updated.apples=4; updated.expected=Map.of("apple",4);
        var merged=ChestSurvey.merge(updated,loaded,false);
        assertTrue(merged.openedWithApples); assertFalse(XaeroBridge.shouldMark(merged,true));
        assertTrue(store.blacklistPositions().isEmpty());
        assertTrue(new WorldStore(directory,"server|overworld",6,"26.1").read().targets.isEmpty());
    }

    @Test void clearingRecordsRestoresMarkersInBothLootCaches() throws Exception {
        var target=chest(1); target.openedWithApples=true;
        var first=new WorldStore(directory,"server|overworld",5,"1.21.11");
        var second=new WorldStore(directory,"server|overworld",5,"26.1");
        first.write(List.of(target),new Calibration()); second.write(List.of(target),new Calibration());
        var loaded=first.read().targets; first.clearBlacklist(loaded);
        assertTrue(XaeroBridge.shouldMark(loaded.getFirst(),false));
        for(var store:List.of(first,second)) {
            assertTrue(XaeroBridge.shouldMark(store.read().targets.getFirst(),false));
            assertTrue(store.blacklistPositions().isEmpty());
        }
    }
}
