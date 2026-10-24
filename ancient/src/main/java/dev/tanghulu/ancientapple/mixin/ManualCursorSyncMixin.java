package dev.tanghulu.ancientapple.mixin;

import dev.tanghulu.ancientapple.HunterModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.SetCursorItemS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cursor ownership is part of a manual PICKUP receipt, including full snapshots on a normal reopen. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ManualCursorSyncMixin {
    @Unique private static HunterModule aah$cursorHunter() {
        var modules=Modules.get(); var module=modules==null?null:modules.get(HunterModule.class);
        return module!=null && module.isActive()?module:null;
    }
    @Inject(method="onSetCursorItem",at=@At("TAIL"))
    private void cursor(SetCursorItemS2CPacket packet,CallbackInfo ci) {
        var h=aah$cursorHunter(); if(h!=null)h.manualCursorStack(packet.contents());
    }
    @Inject(method="onInventory",at=@At("TAIL"))
    private void inventoryCursor(InventoryS2CPacket packet,CallbackInfo ci) {
        var player=MinecraftClient.getInstance().player;
        if(player==null || player.currentScreenHandler.syncId!=packet.syncId())return;
        var h=aah$cursorHunter(); if(h!=null)h.manualCursorStack(packet.cursorStack());
    }
}
