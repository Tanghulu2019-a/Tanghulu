package dev.tanghulu.addon.util.tree;

import net.minecraft.block.*;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import java.util.Collection;

public final class TreeMaterials {
    private TreeMaterials() {}
    public static boolean allowed(ItemStack stack, Collection<Block> blacklist, BlockView world, BlockPos pos) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) return false;
        Block block = item.getBlock();
        var state = block.getDefaultState();
        return !blacklist.contains(block) && !(block instanceof FallingBlock)
            && !(block instanceof LeavesBlock) && !(block instanceof BlockEntityProvider)
            && block != Blocks.SLIME_BLOCK && block != Blocks.HONEY_BLOCK && block != Blocks.MAGMA_BLOCK
            && !(block instanceof IceBlock)
            && state.getFluidState().isEmpty() && state.getHardness(world, pos) >= 0
            && Block.isShapeFullCube(state.getCollisionShape(world, pos));
    }

    public static double toolScore(ItemStack stack, BlockState state, int efficiency) {
        if (stack.isDamageable() && stack.getMaxDamage() - stack.getDamage() <= 1) return -1;
        double speed = stack.isEmpty() ? 1 : stack.getMiningSpeedMultiplier(state);
        if (speed > 1 && efficiency > 0) speed += efficiency * efficiency + 1;
        return speed / (state.isToolRequired() && !stack.isSuitableFor(state) ? 100.0 : 30.0);
    }
}
