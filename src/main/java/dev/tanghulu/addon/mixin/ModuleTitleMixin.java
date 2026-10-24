package dev.tanghulu.addon.mixin;

import meteordevelopment.meteorclient.utils.Utils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Utils.class, remap = false)
public abstract class ModuleTitleMixin {
    // Apply the built-in Stash module translations.
    @Inject(method = "nameToTitle", at = @At("HEAD"), cancellable = true)
    private static void tanghulu$localizeModuleTitle(String name, CallbackInfoReturnable<String> cir) {
        String chinese = dev.tanghulu.stash.StashChinese.MODULE_TITLES.get(name);
        if (chinese != null) cir.setReturnValue(chinese);
    }
}
