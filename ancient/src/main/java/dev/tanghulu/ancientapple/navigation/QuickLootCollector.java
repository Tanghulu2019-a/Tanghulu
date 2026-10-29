package dev.tanghulu.ancientapple.navigation;

import dev.tanghulu.ancientapple.core.QuickLootSession;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Handles a real manually or automatically opened chest after its first inventory packet. */
public final class QuickLootCollector {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Predicate<ItemStack> selected;
    private final Consumer<String> warning;
    private final Consumer<BlockPos> completed;
    private BlockPos pendingChest;
    private BlockPos openedChest;
    private int pendingAge;
    private GenericContainerScreenHandler handler;
    private QuickLootSession session;
    private boolean ready, warned;

    public QuickLootCollector(Predicate<ItemStack> selected, Consumer<String> warning, Consumer<BlockPos> completed) {
        this.selected = selected;
        this.warning = warning;
        this.completed = completed;
    }

    public void reset() {
        pendingChest = null;
        openedChest = null;
        handler = null;
        session = null;
        pendingAge = 0;
        ready = warned = false;
    }

    public void onInteractBlock(BlockPos pos) {
        reset();
        if (mc.world != null && mc.world.getBlockState(pos).getBlock() instanceof ChestBlock)
            pendingChest = pos.toImmutable();
    }

    public void serverContainerOpened(int sync) {
        BlockPos chest = pendingChest;
        reset();
        if (chest == null || mc.world == null || mc.player == null
            || !(mc.world.getBlockState(chest).getBlock() instanceof ChestBlock)
            || !(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler current)
            || current.syncId != sync || (current.getRows() != 3 && current.getRows() != 6)) return;
        handler = current;
        openedChest = chest;
        session = new QuickLootSession();
    }
    public boolean isCollecting() { return handler != null; }
    public BlockPos openedChest() { return openedChest; }

    public void serverInventory(int sync, List<ItemStack> stacks) {
        if (handler != null && handler.syncId == sync && stacks.size() == handler.slots.size()) ready = true;
    }

    public void tick() {
        if (pendingChest != null && ++pendingAge > 100) pendingChest = null;
        if (handler == null) return;
        if (mc.player == null || mc.world == null || mc.player.currentScreenHandler != handler
            || !(mc.currentScreen instanceof GenericContainerScreen)) { reset(); return; }
        if (!ready || mc.interactionManager == null || !handler.getCursorStack().isEmpty()) return;
        // Allow server corrections to arrive before closing, including on high-latency servers.
        var entry = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        int settleTicks = entry == null ? 4 : Math.max(4, (int) Math.ceil(entry.getLatency() / 50.0) + 3);
        var result = session.tick(new QuickLootSession.Container() {
            public int size() { return handler.getRows() * 9; }
            public boolean selected(int slot) { return selected.test(handler.getSlot(slot).getStack()); }
            public boolean move(int slot) {
                var source = handler.getSlot(slot);
                ItemStack stack = source.getStack();
                if (!source.canTakeItems(mc.player) || !hasRoom(stack)) return false;
                int before = stack.getCount();
                mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
                return source.getStack().getCount() < before;
            }
            public void close() { mc.player.closeHandledScreen(); }
        }, settleTicks);
        if (result == QuickLootSession.Result.BLOCKED && !warned) {
            warned = true;
            warning.accept("快速拿取：选中物品尚未拿完，背包已满或物品无法移动；请整理背包，箱子将保持打开。");
        }
        if (result == QuickLootSession.Result.MOVED) warned = false;
        if (result == QuickLootSession.Result.CLOSED) {
            BlockPos chest = openedChest;
            reset();
            completed.accept(chest);
        }
    }

    private boolean hasRoom(ItemStack stack) {
        for (int slot = handler.getRows() * 9; slot < handler.slots.size(); slot++) {
            var destination = handler.getSlot(slot);
            if (!destination.canInsert(stack)) continue;
            ItemStack existing = destination.getStack();
            int limit = destination.getMaxItemCount(stack);
            if (existing.isEmpty() && limit > 0) return true;
            if (ItemStack.areItemsAndComponentsEqual(stack, existing) && existing.getCount() < limit) return true;
        }
        return false;
    }
}
