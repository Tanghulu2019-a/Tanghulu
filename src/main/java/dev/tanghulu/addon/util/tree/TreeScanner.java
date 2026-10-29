package dev.tanghulu.addon.util.tree;

import net.minecraft.block.Block;
import net.minecraft.block.LeavesBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import java.util.List;
import java.util.Map;

/** Incremental world sampling; the controller only consumes progress and completed trees. */
public final class TreeScanner {
    private final ClientWorld world;
    private final Map<Block, TreeSpecies> logs, leaves;
    private final TreePlan plan;
    private final int lowX, highX, lowY, highY, lowZ, highZ;
    private final long total;
    private final BlockPos.Mutable cursor = new BlockPos.Mutable();
    private int x, y, z;
    private long sampled;

    public TreeScanner(ClientWorld world, BlockPos origin, int radius, int height,
                       Map<Block, TreeSpecies> logs, Map<Block, TreeSpecies> leaves) {
        this.world = world; this.logs = logs; this.leaves = leaves;
        plan = new TreePlan(pos(origin), radius, 8, height);
        lowX = origin.getX() - radius - 6; highX = origin.getX() + radius + 6;
        lowZ = origin.getZ() - radius - 6; highZ = origin.getZ() + radius + 6;
        lowY = Math.max(world.getBottomY(), origin.getY() - 8);
        highY = Math.min(world.getTopYInclusive(), origin.getY() + height + 6);
        total = (long) (highX - lowX + 1) * (highZ - lowZ + 1) * (highY - lowY + 1);
        x = lowX; y = lowY; z = lowZ;
    }

    public boolean advance() {
        long deadline = System.nanoTime() + 2_000_000L;
        for (int count = 0; count < 4096 && x <= highX; count++) {
            cursor.set(x, y, z);
            if (world.isChunkLoaded(x >> 4, z >> 4)) {
                var state = world.getBlockState(cursor);
                TreeSpecies log = logs.get(state.getBlock()), leaf = leaves.get(state.getBlock());
                if (log != null) plan.addLog(pos(cursor), log);
                else if (leaf != null && !state.get(LeavesBlock.PERSISTENT)) plan.addNaturalLeaves(pos(cursor), leaf);
            } else {
                // Count the rest of an unloaded column without querying every missing block.
                sampled += highY - y; y = highY;
            }
            if (plan.logCount() > 65536) throw new IllegalStateException("原木超过 65536 个，请缩小半径");
            sampled++;
            if (++y > highY) { y = lowY; if (++z > highZ) { z = lowZ; x++; } }
            if ((count & 63) == 0 && System.nanoTime() >= deadline) break;
        }
        return x > highX;
    }

    public int percent() { return (int) (sampled * 100 / total); }
    public List<List<BlockPos>> trees() {
        return plan.trees().stream().map(tree -> tree.stream()
            .map(p -> new BlockPos(p.x(), p.y(), p.z())).toList()).toList();
    }
    private static TreePlan.Pos pos(BlockPos p) { return new TreePlan.Pos(p.getX(), p.getY(), p.getZ()); }
}
