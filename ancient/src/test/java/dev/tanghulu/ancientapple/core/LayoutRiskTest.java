package dev.tanghulu.ancientapple.core;

import com.seedfinding.mccore.rand.ChunkRand;
import dev.tanghulu.ancientapple.prediction.LayoutPredictor;
import kludwisz.ancientcity.AncientCityGenerator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LayoutRiskTest {
    @Test void flagsChestSeedsAfterSculkButKeepsEarlierChestInSameChunkDeterministic() {
        var g=new AncientCityGenerator(); g.generate(0,50,79,new ChunkRand());
        var chests=LayoutPredictor.chests(g,0);
        for(int x:new int[]{737,745,892})assertTrue(chests.stream().filter(c -> c.x()==x).findFirst().orElseThrow().terrainRequired());
        var before=chests.stream().filter(c -> c.x()==882 && c.z()==1276).findFirst().orElseThrow();
        assertFalse(before.terrainRequired()); assertEquals(-4043133393263082784L,before.seed());
    }
}
