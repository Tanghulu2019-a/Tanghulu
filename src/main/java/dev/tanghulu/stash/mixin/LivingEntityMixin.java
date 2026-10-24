package dev.tanghulu.stash.mixin;

import dev.tanghulu.stash.FlightHooks;
import net.minecraft.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin
{
    @Shadow
    private int jumpingCooldown;

    @Inject(at = @At("HEAD"), method = "tickMovement()V")
    private void tickMovement(CallbackInfo ci)
    {
        if (FlightHooks.isEnabled() && mc != null && (Object) this == mc.player)
        {
            this.jumpingCooldown = 0;
        }
    }

    @Inject(at = @At("HEAD"), method = "isGliding", cancellable = true)
    private void isGliding(CallbackInfoReturnable<Boolean> cir)
    {
        if (FlightHooks.isEnabled() && mc != null && (Object) this == mc.player)
        {
            cir.setReturnValue(true);
        }
    }
}
