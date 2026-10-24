package dev.tanghulu.ancientapple.core;

import com.seedfinding.mccore.rand.ChunkRand;
import kludwisz.ancientcity.AncientCityGenerator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Historical upstream fixtures; these are not a 1.21.11 accuracy certification. */
class LayoutRegressionTest {
    @Test void retainsNegativeCoordinatesAndChestSeedCallOrder() {
        AncientCityGenerator g=new AncientCityGenerator();
        g.generate(5000697870809672599L,1,-41,new ChunkRand());
        var chests=g.getChestsWithLootSeeds();
        assertTrue(chests.stream().anyMatch(c -> c.getFirst().getX()==7 && c.getFirst().getY()==-50
            && c.getFirst().getZ()==-577 && c.getThird()==-7184813891189711778L));
        assertTrue(chests.stream().anyMatch(c -> c.getFirst().getX()==-55 && c.getFirst().getY()==-47
            && c.getFirst().getZ()==-640 && c.getThird()==-5373701976689819811L));
    }
}
