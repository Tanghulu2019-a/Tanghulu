package dev.tanghulu.ancientapple.navigation;

import dev.tanghulu.ancientapple.core.ChestTarget;
import dev.tanghulu.ancientapple.core.ManualCollectionTracker;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Watches normal player interactions and server packets; never moves, clicks, opens, or closes anything. */
public final class ManualCollectionObserver {
    private final MinecraftClient mc=MinecraftClient.getInstance();
    private final Map<String,ChestTarget> targets;
    private final Consumer<List<ChestTarget>> collected;
    private final Consumer<List<ChestTarget>> opened;
    private final ManualCollectionTracker tracker=new ManualCollectionTracker();
    private final Map<String,List<ChestTarget>> containers=new LinkedHashMap<>();
    private List<ChestTarget> interaction=List.of();
    private int ticks,interactionTick,genericSync=-1;
    private Click before;
    private boolean openingNotified;

    public ManualCollectionObserver(Map<String,ChestTarget> targets,Consumer<List<ChestTarget>> collected,Consumer<List<ChestTarget>> opened) {
        this.targets=targets; this.collected=collected; this.opened=opened;
    }
    public void reset() {
        tracker.reset(); containers.clear(); interaction=List.of(); before=null;
        ticks=interactionTick=0; genericSync=-1; openingNotified=false;
    }
    public void tick() {
        if(mc.player==null || mc.world==null) { reset(); return; }
        ticks++;
        if(ticks-interactionTick>100)interaction=List.of();
        for(String key:tracker.confirmed(possessed())) {
            List<ChestTarget> chests=containers.remove(key);
            if(chests!=null)collected.accept(chests);
        }
    }
    public void onInteractBlock(BlockPos pos) {
        interaction=List.of(); before=null;
        if(mc.world==null || mc.player==null)return;
        var state=mc.world.getBlockState(pos);
        if(!(state.getBlock() instanceof ChestBlock))return;
        List<ChestTarget> chests=new ArrayList<>(); chests.add(target(pos));
        if(state.get(ChestBlock.CHEST_TYPE)!=ChestType.SINGLE) {
            BlockPos partner=pos.offset(ChestBlock.getFacing(state));
            var other=mc.world.getBlockState(partner);
            if(other.isOf(state.getBlock()) && other.get(ChestBlock.CHEST_TYPE)==state.get(ChestBlock.CHEST_TYPE).getOpposite()
                && other.get(ChestBlock.FACING)==state.get(ChestBlock.FACING))chests.add(target(partner));
            else return;
        }
        interaction=List.copyOf(chests); interactionTick=ticks;
    }
    public void onClickSlotBefore(int sync,int slot,int button,SlotActionType action) {
        before=null;
        if(mc.player==null || !(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)
            || handler.syncId!=sync || !tracker.ready(sync))return;
        before=new Click(sync,chestApples(handler),possessed());
    }
    public void onClickSlotAfter(int sync,int slot,int button,SlotActionType action) {
        Click click=before; before=null;
        if(click==null || click.sync()!=sync || mc.player==null
            || !(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) || handler.syncId!=sync)return;
        tracker.predictedClick(click.chest(),click.possessed(),chestApples(handler),possessed());
    }
    public void serverContainerOpened(int sync) {
        before=null; genericSync=-1; openingNotified=false; String key=null;
        if(mc.player!=null && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler && handler.syncId==sync) {
            genericSync=sync;
            if(!interaction.isEmpty() && ticks-interactionTick<=100 && handler.getRows()==interaction.size()*3) {
                key=interaction.stream().map(ChestTarget::key).sorted().reduce((a,b) -> a+"|"+b).orElseThrow();
                if(containers.size()>=128 && !containers.containsKey(key))containers.remove(containers.keySet().iterator().next());
                containers.put(key,interaction);
            }
        }
        tracker.opened(key,sync); interaction=List.of();
    }
    public void serverInventory(int sync,List<ItemStack> stacks) {
        if(sync==0 || currentGeneric(sync)) {
            if(sync!=0 && stacks.size()!=mc.player.currentScreenHandler.slots.size())return;
            tracker.full(sync,stacks.stream().mapToInt(this::apples).toArray());
            notifyOpened();
        }
    }
    public void serverSlot(int sync,int slot,ItemStack stack) {
        if(sync==0 || currentGeneric(sync)) {
            tracker.slot(sync,slot,apples(stack)); notifyOpened();
        }
    }
    private void notifyOpened() {
        if(openingNotified || !currentGeneric(genericSync))return;
        String key=tracker.openedWithApples();
        List<ChestTarget> chests=key==null?null:containers.get(key);
        if(chests!=null) { openingNotified=true; opened.accept(chests); }
    }
    public void serverPlayerSlot(int slot,ItemStack stack) { tracker.playerSlot(slot,apples(stack)); }
    public void serverCursor(ItemStack stack) { tracker.cursor(apples(stack)); }
    private boolean currentGeneric(int sync) {
        return mc.player!=null && genericSync==sync && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler h && h.syncId==sync;
    }
    private ChestTarget target(BlockPos pos) {
        ChestTarget target=targets.get(pos.getX()+","+pos.getY()+","+pos.getZ());
        if(target!=null)return target;
        // Unpredicted manual interactions remain transient until collection is confirmed.
        target=new ChestTarget();
        target.x=pos.getX(); target.y=pos.getY(); target.z=pos.getZ();
        return target;
    }
    private int apples(ItemStack stack) { return dev.tanghulu.ancientapple.core.TargetLoot.count(stack); }
    private int chestApples(GenericContainerScreenHandler handler) {
        int count=0; for(int i=0;i<handler.getRows()*9;i++)count+=apples(handler.getSlot(i).getStack()); return count;
    }
    private int possessed() {
        int count=apples(mc.player.currentScreenHandler.getCursorStack());
        for(int i=0;i<36;i++)count+=apples(mc.player.getInventory().getStack(i));
        return count;
    }
    private record Click(int sync,int chest,int possessed) {}
}
