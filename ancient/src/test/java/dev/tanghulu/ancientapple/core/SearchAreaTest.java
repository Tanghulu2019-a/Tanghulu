package dev.tanghulu.ancientapple.core;

import com.seedfinding.mccore.rand.ChunkRand;
import dev.tanghulu.ancientapple.prediction.LayoutPredictor;
import dev.tanghulu.ancientapple.prediction.SearchArea;
import kludwisz.ancientcity.AncientCityGenerator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SearchAreaTest {
    @Test void includesAppleChestWhenCityStartIsOutsideSearchRadius() {
        // A real seed-0 city; chest (841,-48,1248) contains two enchanted apples.
        SearchArea area=new SearchArea(1190,1247,384);
        assertFalse(area.contains(50*16,79*16));
        assertTrue(area.mayContainCity(50*16,79*16));
        assertTrue(area.regions().stream().anyMatch(r -> r[0]==2 && r[1]==3));
        var layout=new AncientCityGenerator(); layout.generate(0,50,79,new ChunkRand());
        assertTrue(area.intersects(layout));
        assertTrue(LayoutPredictor.chests(layout,0).stream().filter(c -> area.contains(c.x(),c.z())).anyMatch(c -> c.x()==841 && c.z()==1248));
        assertFalse(area.contains(709,1214));
    }
    @Test void includesAdjacentNegativeRegionAndCircleBoundaryWithoutExpandingTargetRadius() {
        SearchArea area=new SearchArea(0,0,384);
        assertTrue(area.regions().stream().anyMatch(r -> r[0]==-2 && r[1]==0));
        assertTrue(area.mayContainCity(-400,0));
        assertTrue(area.contains(-384,0)); assertTrue(area.contains(384,0));
        assertFalse(area.contains(-385,0)); assertFalse(area.contains(384,1));
        assertFalse(area.mayContainCity(1000,0));
    }
    @Test void expandedRadiusIsAccurateNearPositiveAndNegativeWorldBorders() {
        for(int center:new int[]{29900000,-29900000}) {
            var area=new SearchArea(center,center,131072);
            assertTrue(area.contains(center+131072,center));
            assertFalse(area.contains(center+131073,center));
            assertFalse(area.contains(center+100000,center+100000));
            assertTrue(area.mayContainCity(center-131200,center));
        }
    }
}
