package dev.tanghulu.addon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.tanghulu.addon.util.ModuleTitles;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Module.class, remap = false)
public abstract class ModuleInitialMixin {
    @ModifyExpressionValue(
        method = "<init>(Lmeteordevelopment/meteorclient/systems/modules/Category;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/Utils;nameToTitle(Ljava/lang/String;)Ljava/lang/String;")
    )
    private String tanghulu$prefixModuleTitle(String title) {
        return ModuleTitles.withEnglishInitial(title, getClass());
    }
}
