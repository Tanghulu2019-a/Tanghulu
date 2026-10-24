package dev.tanghulu.ancientapple.mixin;

import dev.tanghulu.ancientapple.HunterModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records player intent around vanilla client prediction, without changing the interaction. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ManualInteractionMixin {
    @Unique private static HunterModule aah$interactionHunter() {
        var modules=Modules.get(); var module=modules==null?null:modules.get(HunterModule.class);
        return module!=null && module.isActive()?module:null;
    }
    @Inject(method="interactBlock",at=@At("HEAD"))
    private void chestInteraction(ClientPlayerEntity player,Hand hand,BlockHitResult hit,CallbackInfoReturnable<ActionResult> ci) {
        var h=aah$interactionHunter(); if(h!=null)h.manualChestInteraction(hit.getBlockPos());
    }
    @Inject(method="clickSlot",at=@At("HEAD"))
    private void beforeClick(int sync,int slot,int button,SlotActionType action,PlayerEntity player,CallbackInfo ci) {
        var h=aah$interactionHunter(); if(h!=null)h.manualClickBefore(sync,slot,button,action);
    }
    @Inject(method="clickSlot",at=@At("RETURN"))
    private void afterClick(int sync,int slot,int button,SlotActionType action,PlayerEntity player,CallbackInfo ci) {
        var h=aah$interactionHunter(); if(h!=null)h.manualClickAfter(sync,slot,button,action);
    }
}
