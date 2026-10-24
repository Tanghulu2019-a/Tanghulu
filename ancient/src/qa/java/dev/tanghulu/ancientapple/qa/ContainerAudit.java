package dev.tanghulu.ancientapple.qa;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import dev.tanghulu.ancientapple.core.ContainerLedger;
import dev.tanghulu.ancientapple.core.TransferReceipt;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import java.nio.file.*;
import java.util.*;

/** Real vanilla QUICK_MOVE and sync suppression, with an in-process packet sink. */
public final class ContainerAudit {
    private static final ComponentChangesHash.ComponentHasher HASH=Object::hashCode;
    public static void run(ServerWorld world,Path project) throws Exception {
        List<Map<String,Object>> cases=new ArrayList<>();
        for(int rows:new int[]{3,6})for(boolean full:new boolean[]{false,true}) {
            var player=new ServerPlayerEntity(world.getServer(),world,new GameProfile(UUID.randomUUID(),"AAH_QA"),SyncedClientOptions.createDefault());
            var box=new SimpleInventory(rows*9); box.setStack(rows*9-1,new ItemStack(Items.ENCHANTED_GOLDEN_APPLE,2)); box.setStack(0,new ItemStack(Items.DIAMOND,3));
            if(full)for(int i=0;i<36;i++)player.getInventory().setStack(i,new ItemStack(Items.COBBLESTONE,64));
            var ledger=new ContainerLedger(); ledger.open(5);
            var menu=rows==3?GenericContainerScreenHandler.createGeneric9x3(5,player.getInventory(),box):GenericContainerScreenHandler.createGeneric9x6(5,player.getInventory(),box);
            var sink=new Sink(ledger); menu.updateSyncHandler(sink); sink.slots=0;
            var receipt=new TransferReceipt(ledger.playerApples(),ledger.chestApples(),2);
            menu.disableSyncing(); menu.onSlotClick(rows*9-1,0,SlotActionType.QUICK_MOVE,player);
            // Same remote hashes that an accurately predicted ClickSlotC2SPacket supplies.
            for(int i=0;i<menu.slots.size();i++)menu.setReceivedHash(i,ItemStackHash.fromItemStack(menu.getSlot(i).getStack(),HASH));
            menu.setReceivedCursorHash(ItemStackHash.fromItemStack(menu.getCursorStack(),HASH));
            menu.enableSyncing(); menu.sendContentUpdates();
            if(sink.slots!=0)throw new AssertionError("Expected vanilla to suppress matching click deltas");
            boolean premature=receipt.confirmed(ledger.playerApples(),ledger.chestApples());
            ledger.open(6);
            var reopened=rows==3?GenericContainerScreenHandler.createGeneric9x3(6,player.getInventory(),box):GenericContainerScreenHandler.createGeneric9x6(6,player.getInventory(),box);
            reopened.updateSyncHandler(new Sink(ledger));
            boolean confirmed=receipt.confirmed(ledger.playerApples(),ledger.chestApples());
            if(premature || confirmed==full || box.getStack(0).getCount()!=3 || !box.getStack(0).isOf(Items.DIAMOND))throw new AssertionError("Incorrect transfer confirmation / non-apple item changed");
            cases.add(Map.of("rows",rows,"fullInventory",full,"clickDeltas",sink.slots,"prematureConfirmation",premature,"confirmedAfterReopen",confirmed));
        }
        var report=Map.of("passed",true,"cases",cases,"method","Actual GenericContainerScreenHandler QUICK_MOVE, TrackedSlot.Impl and sendContentUpdates; authoritative snapshot on reopen.");
        Files.writeString(project.resolve("build/reports/container-sync.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("CONTAINER_SYNC_AUDIT "+report);
    }
    private static final class Sink implements ScreenHandlerSyncHandler {
        private final ContainerLedger ledger;
        private int slots;
        private Sink(ContainerLedger ledger) { this.ledger=ledger; }
        private int count(ItemStack s) { return s.isOf(Items.ENCHANTED_GOLDEN_APPLE)?s.getCount():0; }
        public void updateState(ScreenHandler handler,List<ItemStack> stacks,ItemStack cursor,int[] properties) { ledger.full(handler.syncId,stacks.stream().mapToInt(this::count).toArray()); }
        public void updateSlot(ScreenHandler handler,int slot,ItemStack stack) { slots++; ledger.slot(handler.syncId,slot,count(stack)); }
        public void updateCursorStack(ScreenHandler handler,ItemStack stack) {}
        public void updateProperty(ScreenHandler handler,int property,int value) {}
        public TrackedSlot createTrackedSlot() { return new TrackedSlot.Impl(HASH); }
    }
}
