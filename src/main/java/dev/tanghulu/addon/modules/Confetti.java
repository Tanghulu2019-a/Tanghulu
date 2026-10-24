// Adapted from Numby Hack v2.6.24 (GPL-3.0); localized and integrated by Tanghulu, 2026-10-24.
package dev.tanghulu.addon.modules;

import dev.tanghulu.addon.BaseModule;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.Vec3d;

/**
 * from Tanuki
 */
public class Confetti extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("常规设置");

    private final Setting<SettingColor> colorOne = sgGeneral.add(new ColorSetting.Builder()
            .name("第一种颜色")
            .description("图腾触发时使用的第一种粒子颜色，约占四分之一。")
            .defaultValue(new SettingColor(146, 188, 98, 255))
            .build());

    private final Setting<SettingColor> colorTwo = sgGeneral.add(new ColorSetting.Builder()
            .name("第二种颜色")
            .description("图腾触发时使用的第二种粒子颜色，约占四分之三。")
            .defaultValue(new SettingColor(73, 107, 190, 255))
            .build());

    public Confetti() {
        super(BaseModule.CATEGORY, "图腾粒子自定义", "自定义不死图腾触发时的两种粒子颜色。", "Confetti");
    }

    public Vec3d getColorOne() {
        return getDoubleVectorColor(colorOne);
    }

    public Vec3d getColorTwo() {
        return getDoubleVectorColor(colorTwo);
    }

    public Vec3d getDoubleVectorColor(Setting<SettingColor> colorSetting) {
        return new Vec3d((double) colorSetting.get().r / 255, (double) colorSetting.get().g / 255,
                (double) colorSetting.get().b / 255);
    }
}
