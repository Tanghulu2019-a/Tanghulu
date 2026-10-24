package dev.tanghulu.ancientapple.gui;

import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.nbt.NbtCompound;

/** A permanent, read-only note in the normal settings order. */
public final class NoteSetting extends Setting<String> {
    public NoteSetting(String name,String text) { super(name,text,text,null,null,null); }
    public static void registerRenderer() {
        SettingsWidgetFactory.registerCustomFactory(NoteSetting.class,theme -> (table,setting) ->
            table.add(theme.label(setting.description)).expandX());
    }
    @Override protected String parseImpl(String value) { return defaultValue; }
    @Override protected boolean isValueValid(String value) { return defaultValue.equals(value); }
    @Override protected NbtCompound save(NbtCompound tag) { return tag; }
    @Override protected String load(NbtCompound tag) { return defaultValue; }
}
