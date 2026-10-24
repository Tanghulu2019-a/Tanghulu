package dev.tanghulu.addon.mixin;

import dev.tanghulu.addon.mixinface.MySettings;
import meteordevelopment.meteorclient.gui.DefaultSettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import meteordevelopment.meteorclient.settings.SettingGroup;
import dev.tanghulu.stash.StashLocalization;

@Mixin(DefaultSettingsWidgetFactory.class)
public abstract class DefaultSettingsWidgetFactoryMixin extends SettingsWidgetFactory {
    public DefaultSettingsWidgetFactoryMixin(GuiTheme theme) {
        super(theme);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void onInit(GuiTheme theme, CallbackInfo ci) {
        new MySettings(factories, this.theme).addSettings();
        StashLocalization.installEnumFactory(factories, this.theme);
    }

    @Redirect(method = "group", at = @At(value = "FIELD",
        target = "Lmeteordevelopment/meteorclient/settings/SettingGroup;name:Ljava/lang/String;"), remap = false)
    private String tanghuluStash$groupTitle(SettingGroup group) {
        return StashLocalization.groupTitle(group);
    }
}
