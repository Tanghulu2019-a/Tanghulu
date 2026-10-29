package dev.tanghulu.ancientapple.navigation;

import dev.tanghulu.ancientapple.core.AutoChestSearch;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Searches loaded nearby blocks without moving the player or requiring line of sight. */
public final class AutoChestLooter {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final AutoChestSearch<BlockPos> search = new AutoChestSearch<>();
    private final Predicate<ItemStack> selected;
    private Object world;

    public AutoChestLooter(Predicate<ItemStack> selected) { this.selected = selected; }
    public void reset() { search.reset(); world = null; }
    public void opened(BlockPos pos) { if (pos != null) search.opened(halves(pos)); }
    public void completed(BlockPos pos) { if (pos != null) search.completed(halves(pos)); }

    public void tick(double configuredRange, boolean throughWalls, boolean collecting) {
        if (mc.world == null || mc.player == null) { reset(); return; }
        if (world != mc.world) { reset(); world = mc.world; }
        search.tick();
        if (!search.canSearch() || collecting || mc.interactionManager == null || mc.currentScreen != null
            || mc.player.currentScreenHandler != mc.player.playerScreenHandler || mc.player.isSpectator()
            || mc.player.isSneaking() || mc.player.isUsingItem() || mc.interactionManager.isBreakingBlock()
            || !hasRoom()) return;
        double range = Math.min(configuredRange, mc.player.getBlockInteractionRange());
        Vec3d eye = mc.player.getEyePos();
        BlockPos origin = BlockPos.ofFloored(eye);
        int radius = (int) Math.ceil(range);
        List<AutoChestSearch.Candidate<BlockPos>> candidates = new ArrayList<>();
        for (BlockPos mutable : BlockPos.iterate(origin.add(-radius, -radius, -radius), origin.add(radius, radius, radius))) {
            if (!mc.world.isChunkLoaded(mutable.getX() >> 4, mutable.getZ() >> 4)
                || !(mc.world.getBlockState(mutable).getBlock() instanceof ChestBlock)) continue;
            BlockPos pos = mutable.toImmutable();
            double distance = eye.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (distance > range * range || (!throughWalls && visibleHit(pos) == null)) continue;
            candidates.add(new AutoChestSearch.Candidate<>(pos, distance));
        }
        BlockPos next = search.select(candidates, range);
        if (next == null) return;
        var entry = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        int timeout = entry == null ? 40 : Math.max(40, (int) Math.ceil(entry.getLatency() / 50.0) + 20);
        search.attempted(next, halves(next), timeout);
        BlockHitResult hit = visibleHit(next);
        if (hit == null) {
            Vec3d center = Vec3d.ofCenter(next);
            Vec3d delta = eye.subtract(center);
            Direction side = Direction.getFacing(delta.x, delta.y, delta.z);
            Vec3d point = center.add(side.getOffsetX() * .5, side.getOffsetY() * .5, side.getOffsetZ() * .5);
            hit = new BlockHitResult(point, side, next, false);
        }
        // The existing interaction mixin arms the collector and collection observer.
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
    }

    private BlockHitResult visibleHit(BlockPos pos) {
        var hit = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), Vec3d.ofCenter(pos),
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) ? hit : null;
    }
    private boolean hasRoom() {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty() || (selected.test(stack) && stack.getCount() < stack.getMaxCount())) return true;
        }
        return false;
    }
    private List<BlockPos> halves(BlockPos pos) {
        if (mc.world == null) return List.of(pos);
        var state = mc.world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            BlockPos partner = pos.offset(ChestBlock.getFacing(state));
            var other = mc.world.getBlockState(partner);
            if (other.isOf(state.getBlock()) && other.get(ChestBlock.CHEST_TYPE) == state.get(ChestBlock.CHEST_TYPE).getOpposite()
                && other.get(ChestBlock.FACING) == state.get(ChestBlock.FACING)) return List.of(pos, partner);
        }
        return List.of(pos);
    }
}
