package dev.tanghulu.compat;

import dev.tanghulu.ancientapple.prediction.TerrainBridge;
import java.nio.file.*;

/** Exercise the actual isolated vanilla server constructor and chunk generation ABI. */
public final class TerrainCompatibilityChecks {
    public static void main(String[] args) throws Exception {
        Path cache = Path.of(args[0]);
        Files.createDirectories(cache);
        try (var terrain = TerrainBridge.open(cache, 0L)) {
            // Seed 0, ancient city at chunk 50/79, from the existing vanilla oracle.
            Long seed = terrain.lootSeedAt(828, -49, 1251);
            if (seed == null || seed == 0) throw new AssertionError("Ancient city chest did not generate");
            if (!seed.equals(terrain.lootSeedAt(828, -49, 1251))) throw new AssertionError("Reading changed chest seed");
            if (terrain.lootSeedAt(828, 300, 1251) != null) throw new AssertionError("Empty air reported as a chest");
            System.out.println("VANILLA_TERRAIN_COMPAT PASS chest=" + seed + " deterministic=true emptyAir=true");
        }
    }
}
