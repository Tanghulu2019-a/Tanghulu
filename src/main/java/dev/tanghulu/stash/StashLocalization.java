package dev.tanghulu.stash;

import dev.tanghulu.stash.mixin.SettingTitleAccessor;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Changes the UI of registered Stash objects without modifying persisted names. */
public final class StashLocalization {
    private static final Set<Setting<?>> SETTINGS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<SettingGroup, String> GROUPS = new IdentityHashMap<>();

    private StashLocalization() {}

    public static <T extends Module> T localize(T module) {
        if (!module.getClass().getName().startsWith("dev.tanghulu.stash.modules.")) return module;
        for (SettingGroup group : module.settings) {
            GROUPS.put(group, StashChinese.groupTitle(group.name));
            for (Setting<?> setting : group) {
                String title = StashChinese.settingTitle(module.getClass().getSimpleName(), setting.name);
                if (title != null) ((SettingTitleAccessor) setting).tanghuluStash$setTitle(title);
                SETTINGS.add(setting);
            }
        }
        return module;
    }

    public static String groupTitle(SettingGroup group) {
        return GROUPS.getOrDefault(group, group.name);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void installEnumFactory(Map<Class<?>, SettingsWidgetFactory.Factory> factories, GuiTheme theme) {
        SettingsWidgetFactory.Factory original = factories.get(EnumSetting.class);
        factories.put(EnumSetting.class, (table, setting) -> {
            if (!SETTINGS.contains(setting)) {
                original.create(table, setting);
                return;
            }
            Enum<?> current = (Enum<?>) setting.get();
            Enum<?>[] values = current.getDeclaringClass().getEnumConstants();
            String[] labels = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                labels[i] = StashChinese.enumTitle(setting.name, values[i].name());
            }
            WDropdown<String> dropdown = table.add(theme.dropdown(labels, labels[current.ordinal()]))
                .expandCellX().widget();
            dropdown.action = () -> {
                for (int i = 0; i < labels.length; i++) {
                    if (labels[i].equals(dropdown.get())) {
                        ((EnumSetting) setting).set(values[i]);
                        return;
                    }
                }
            };
        });
    }
}
