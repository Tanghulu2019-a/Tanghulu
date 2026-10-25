package dev.tanghulu.ancientapple.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LootSelectionTest {
    private ChestTarget chest(int apples,int books) {
        var target=new ChestTarget(0,-48,0,0,0,1,"ancient_city",apples,Map.of());
        target.swiftSneakBooks=books;
        return target;
    }
    @Test void threeChoicesFilterSingleAndMixedLoot() {
        for(int apples=0;apples<=1;apples++)for(int books=0;books<=1;books++) {
            var target=chest(apples,books);
            assertEquals(apples>0,LootSelection.Apples.matches(target));
            assertEquals(books>0,LootSelection.SwiftSneak.matches(target));
            assertTrue(LootSelection.All.matches(target));
        }
    }
    @Test void mixedChestUsesSelectedLabelAndIcon() {
        var target=chest(2,1);
        assertEquals("附魔金苹果 ×2",LootSelection.Apples.label(target));
        assertEquals("迅捷潜行 III ×1",LootSelection.SwiftSneak.label(target));
        assertEquals(target.lootLabel(),LootSelection.All.label(target));
        assertTrue(LootSelection.SwiftSneak.bookIcon(target));
        assertFalse(LootSelection.Apples.bookIcon(target));
        assertFalse(LootSelection.All.bookIcon(target));
    }
    @Test void filteringBeforeMarkerLimitKeepsFartherMatchingChest() {
        var apple=chest(1,0);
        var book=chest(0,1); book.x=10;
        assertEquals(List.of(book),MarkerSelection.nearest(List.of(apple,book),false,
            .5,-47.5,.5,100,1,LootSelection.SwiftSneak::matches));
        book.openedWithApples=true;
        assertTrue(MarkerSelection.nearest(List.of(book),true,.5,-47.5,.5,100,1,LootSelection.SwiftSneak::matches).isEmpty());
    }
    @Test void switchingDoesNotChangePredictionOrCollectionHistory() {
        var target=chest(1,1); target.markCollected();
        for(var selection:LootSelection.values()) {
            selection.matches(target); selection.label(target); selection.bookIcon(target);
            assertTrue(target.isBlacklisted());
            assertFalse(MarkerSelection.shouldMark(target,true));
        }
        assertEquals(1,target.apples);
        assertEquals(1,target.swiftSneakBooks);
    }
}
