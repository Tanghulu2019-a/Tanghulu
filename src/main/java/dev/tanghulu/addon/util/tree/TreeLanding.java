package dev.tanghulu.addon.util.tree;

import net.minecraft.block.*;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/** Collision surfaces, including paths and slabs, can support a one-step descent. */
public final class TreeLanding {
    private TreeLanding() {}
    public static boolean hazardous(BlockState state) {
        return state.isOf(Blocks.LAVA) || state.getFluidState().isIn(FluidTags.LAVA) || state.isOf(Blocks.MAGMA_BLOCK)
            || state.isOf(Blocks.SLIME_BLOCK) || state.isOf(Blocks.HONEY_BLOCK)
            || (state.getBlock() instanceof CampfireBlock && state.get(CampfireBlock.LIT));
    }
    public static double surface(BlockState state, BlockView world, BlockPos pos) {
        if (hazardous(state)) return Double.NaN;
        double surface = Double.NaN;
        for (var box : state.getCollisionShape(world, pos).getBoundingBoxes()) {
            if (box.maxX <= 0.3 || box.minX >= 0.7 || box.maxZ <= 0.3 || box.minZ >= 0.7) continue;
            if (Double.isNaN(surface) || box.maxY + pos.getY() > surface) surface = box.maxY + pos.getY();
        }
        return surface;
    }
}
