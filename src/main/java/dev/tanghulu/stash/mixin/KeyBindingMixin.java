package dev.tanghulu.stash.mixin;

import dev.tanghulu.stash.FlightHooks;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {

    @Inject(at = @At("RETURN"), method = "isPressed", cancellable = true)
    public void isPressed(CallbackInfoReturnable<Boolean> cir)
    {
        if (FlightHooks.isEnabled() && mc != null && mc.options != null && (Object) this == mc.options.forwardKey)
        {
            cir.setReturnValue(true);
        }
    }
}
