package dev.tanghulu.stash.mixin;

import meteordevelopment.meteorclient.settings.Setting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Setting.class, remap = false)
public interface SettingTitleAccessor {
    @Mutable
    @Accessor("title")
    void tanghuluStash$setTitle(String title);
}
