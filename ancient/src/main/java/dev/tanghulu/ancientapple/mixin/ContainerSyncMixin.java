package dev.tanghulu.ancientapple.mixin;

import dev.tanghulu.ancientapple.HunterModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe packets only AFTER vanilla applies them on the client thread. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ContainerSyncMixin {
    @Unique private static HunterModule aah$containerHunter() {
        var modules=Modules.get();
        var hunter=modules==null?null:modules.get(HunterModule.class);
        return hunter!=null && hunter.isActive()?hunter:null;
    }
    @Inject(method="onOpenScreen",at=@At("TAIL"))
    private void opened(OpenScreenS2CPacket packet,CallbackInfo ci) {
        var h=aah$containerHunter(); if(h!=null)h.serverContainerOpened(packet.getSyncId());
    }
    @Inject(method="onInventory",at=@At("TAIL"))
    private void inventory(InventoryS2CPacket packet,CallbackInfo ci) {
        var h=aah$containerHunter(); if(h!=null)h.serverInventory(packet.syncId(),packet.contents());
    }
    @Inject(method="onScreenHandlerSlotUpdate",at=@At("TAIL"))
    private void slot(ScreenHandlerSlotUpdateS2CPacket packet,CallbackInfo ci) {
        var h=aah$containerHunter(); if(h!=null)h.serverSlot(packet.getSyncId(),packet.getSlot(),packet.getStack());
    }
    @Inject(method="onSetPlayerInventory",at=@At("TAIL"))
    private void playerSlot(SetPlayerInventoryS2CPacket packet,CallbackInfo ci) {
        var h=aah$containerHunter(); if(h!=null)h.serverPlayerSlot(packet.slot(),packet.contents());
    }
}
