package dev.tanghulu.stash;

import dev.tanghulu.stash.modules.BetterStashFinder.Chunk;
import net.minecraft.util.math.ChunkPos;
import java.io.StringWriter;

public final class StashRecordsChecks {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
        for (int x : new int[] {-1000, -1, 0, 1000}) {
            Chunk source = new Chunk(new ChunkPos(x, -x));
            source.chests = 1; source.barrels = 2; source.shulkers = 3;
            source.enderChests = 4; source.furnaces = 5;
            source.dispensersDroppers = 6; source.hoppers = 7; source.crafters = 8;
            StringWriter writer = new StringWriter();
            source.write(writer);
            Chunk restored = Chunk.fromCsv(writer.toString());
            check(source.equals(restored), "CSV moved chunk at " + x);
            check(source.x == restored.x && source.z == restored.z, "CSV moved block center");
            check(source.countsEqual(restored) && restored.getTotal() == 36, "CSV lost storage counts");
            restored.crafters++;
            check(!source.countsEqual(restored), "Changed counts treated as unchanged");
        }
        Chunk legacy = Chunk.fromCsv("-8,8,1,2,3,4,5,6,7");
        check(legacy.chunkPos.x == -1 && legacy.chunkPos.z == 0, "Legacy negative coordinates");
        check(legacy.barrels == 2 && legacy.hoppers == 7 && legacy.crafters == 0, "Legacy CSV columns");
        try { Chunk.fromCsv("1,2,3"); throw new AssertionError("Malformed CSV accepted"); }
        catch (IllegalArgumentException expected) {}
        System.out.println("Stash records: CSV round trips, negative coordinates, all counts, legacy rows, malformed rows PASS");
    }
}
