package dev.tanghulu.addon.mixin;

import dev.tanghulu.addon.modules.InformationTextFilter;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(InGameHud.class)
public abstract class FilteredHudTextMixin {
    @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true)
    private Text tanghulu$filterActionBar(Text text) {
        return InformationTextFilter.filter(InformationTextFilter.Source.ActionBar, text);
    }

    @ModifyVariable(method = { "setTitle", "setSubtitle" }, at = @At("HEAD"), argsOnly = true)
    private Text tanghulu$filterTitle(Text text) {
        return InformationTextFilter.filter(InformationTextFilter.Source.Title, text);
    }
}
