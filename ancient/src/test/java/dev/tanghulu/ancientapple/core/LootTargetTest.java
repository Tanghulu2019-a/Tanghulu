package dev.tanghulu.ancientapple.core;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LootTargetTest {
    private ChestTarget book(int level) {
        String signature="{\"id\":\"minecraft:enchanted_book\",\"components\":{\"minecraft:stored_enchantments\":{\"minecraft:swift_sneak\":"+level+"}}}";
        return new ChestTarget(1,-48,2,0,0,42,"ancient_city",0,Map.of(signature,1));
    }
    @Test void onlyLevelThreeBecomesATarget() {
        assertFalse(book(1).eligible());
        assertFalse(book(2).eligible());
        var target=book(3);
        assertTrue(target.eligible());
        assertTrue(MarkerSelection.shouldMark(target,false));
        assertEquals("迅捷潜行 III ×1",target.lootLabel());
    }
    @Test void bookMarkersRespectOpeningAndCollectionHistory() {
        var target=book(3);
        target.openedWithApples=true;
        assertFalse(MarkerSelection.shouldMark(target,true));
        target.openedWithApples=false;
        target.markCollected();
        assertFalse(target.eligible());
        assertFalse(MarkerSelection.shouldMark(target,true));
    }
    @Test void cachedSignaturesRecoverBooksAndMixedContents() {
        var target=book(3);
        target.swiftSneakBooks=0;
        ChestSurvey.prepare(target,false);
        assertEquals(1,target.swiftSneakBooks);
        target.apples=2;
        assertTrue(target.lootLabel().contains("附魔金苹果 ×2"));
        assertTrue(target.lootLabel().contains("迅捷潜行 III ×1"));
    }
}
