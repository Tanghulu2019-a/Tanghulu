package dev.tanghulu.addon.util.notebot;

import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;

public final class NotebotInteraction {
    private NotebotInteraction() {}

    public static BlockHitResult findHit(BlockView world, Vec3d eye, BlockPos pos, double reach, ShapeContext shape) {
        if (!world.getBlockState(pos).isOf(Blocks.NOTE_BLOCK)) return null;
        BlockHitResult hit = world.raycast(new RaycastContext(eye, Vec3d.ofCenter(pos),
            RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, shape));
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos) || hit.isInsideBlock()
            || eye.squaredDistanceTo(hit.getPos()) > reach * reach) return null;
        return hit;
    }
}
