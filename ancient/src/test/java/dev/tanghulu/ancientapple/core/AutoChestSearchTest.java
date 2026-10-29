package dev.tanghulu.ancientapple.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AutoChestSearchTest {
    private AutoChestSearch.Candidate<String> candidate(String key, double squaredDistance) {
        return new AutoChestSearch.Candidate<>(key, squaredDistance);
    }
    private void ticks(AutoChestSearch<?> search, int count) { for (int i = 0; i < count; i++) search.tick(); }

    @Test void choosesNearestWithinRangeRegardlessOfIterationOrder() {
        var search = new AutoChestSearch<String>();
        var candidates = List.of(candidate("far", 16), candidate("nearest", 4), candidate("middle", 9));
        assertEquals("nearest", search.select(candidates, 4));
        assertNull(search.select(candidates, 1.9));
        assertEquals("nearest", search.select(candidates, 2));
    }
    @Test void invalidCoordinatesAndRangesCannotProduceAnAttempt() {
        var search = new AutoChestSearch<String>();
        assertNull(search.select(List.of(candidate("bad", Double.NaN), candidate("negative", -1),
            candidate("infinite", Double.POSITIVE_INFINITY)), 4.5));
        assertNull(search.select(List.of(candidate("near", 1)), 0));
        assertNull(search.select(List.of(candidate("near", 1)), Double.NaN));
    }
    @Test void onlyOneRequestCanBePendingAndLateServersGetFullTimeout() {
        var search = new AutoChestSearch<String>();
        search.attempted("a", List.of("a"), 100);
        ticks(search, 99);
        assertFalse(search.canSearch());
        assertNull(search.select(List.of(candidate("b", 1)), 4));
        assertThrows(IllegalStateException.class, () -> search.attempted("b", List.of("b"), 40));
        ticks(search, 5);
        assertEquals("b", search.select(List.of(candidate("b", 1)), 4));
    }
    @Test void refusedNearestChestDoesNotStarveOtherChests() {
        var search = new AutoChestSearch<String>();
        var candidates = List.of(candidate("locked", 1), candidate("other", 4));
        search.attempted("locked", List.of("locked"), 40);
        ticks(search, 44);
        assertEquals("other", search.select(candidates, 4.5));
        ticks(search, 196);
        assertEquals("locked", search.select(candidates, 4.5));
    }
    @Test void successfulDoubleChestSkipsBothHalvesForTheRun() {
        var search = new AutoChestSearch<String>();
        search.attempted("left", List.of("left", "right"), 40);
        search.opened(List.of("left", "right"));
        search.completed(List.of("left", "right"));
        ticks(search, 5000);
        assertEquals("next", search.select(List.of(candidate("left", 1), candidate("right", 2), candidate("next", 9)), 4));
    }
    @Test void cancelledDoubleChestHasCooldownButIsNotPermanentlyCompleted() {
        var search = new AutoChestSearch<String>();
        search.attempted("left", List.of("left", "right"), 40);
        search.opened(List.of("left", "right"));
        ticks(search, 4);
        assertNull(search.select(List.of(candidate("right", 1)), 4));
        ticks(search, 236);
        assertEquals("right", search.select(List.of(candidate("right", 1)), 4));
    }
    @Test void changingRunClearsVisitedPositionsAndPendingRequests() {
        var search = new AutoChestSearch<String>();
        search.completed(List.of("a"));
        search.attempted("b", List.of("b"), 40);
        search.reset();
        assertTrue(search.canSearch());
        assertEquals("a", search.select(List.of(candidate("a", 1)), 4.5));
        assertEquals("b", search.select(List.of(candidate("b", 1)), 4.5));
    }
    @Test void emptyOrAlreadyLootedChestDoesNotLoopAndNextChestWaitsFourTicks() {
        var search = new AutoChestSearch<String>();
        search.completed(List.of("empty"));
        var candidates = List.of(candidate("empty", 1), candidate("next", 4));
        ticks(search, 3);
        assertNull(search.select(candidates, 4.5));
        search.tick();
        assertEquals("next", search.select(candidates, 4.5));
    }
}
