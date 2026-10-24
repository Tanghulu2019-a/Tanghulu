// Adapted from Numby Hack v2.6.24 (GPL-3.0); localized and integrated by Tanghulu, 2026-10-24.
package dev.tanghulu.addon.modules;

import org.joml.Vector3d;

import dev.tanghulu.addon.BaseModule;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockEntityIterator;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.text.Text;

/**
 * made by cqb13
 */
public class SignTextEsp extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("常规设置");

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
            .name("文字颜色")
            .description("告示牌文字的显示颜色。")
            .defaultValue(new SettingColor(255, 255, 255))
            .build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
            .name("文字缩放")
            .description("告示牌文字的显示大小。")
            .defaultValue(1.25)
            .min(1)
            .sliderMax(4)
            .build());

    private final Setting<Boolean> hideWhenNear = sgGeneral.add(new BoolSetting.Builder()
            .name("近距离隐藏")
            .description("靠近告示牌时隐藏悬浮文字。")
            .defaultValue(true)
            .build());

    private final Setting<Integer> nearDistance = sgGeneral.add(new IntSetting.Builder()
            .name("近距离阈值")
            .description("达到指定距离时隐藏文字，单位为方块。")
            .defaultValue(10)
            .min(5)
            .sliderMax(100)
            .visible(hideWhenNear::get)
            .build());

    private final Setting<Boolean> hideWhenFar = sgGeneral.add(new BoolSetting.Builder()
            .name("远距离隐藏")
            .description("超过指定距离后隐藏悬浮文字。")
            .defaultValue(false)
            .build());

    private final Setting<Integer> farDistance = sgGeneral.add(new IntSetting.Builder()
            .name("远距离阈值")
            .description("达到指定距离时隐藏文字，单位为方块。")
            .defaultValue(1000)
            .min(5)
            .sliderMax(10000)
            .visible(hideWhenFar::get)
            .build());

    public SignTextEsp() {
        super(BaseModule.CATEGORY, "告示牌文字漂浮", "远距离显示已加载告示牌正面的文字。", "Sign Text Esp", "sign-text-esp");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        BlockEntityIterator it = new BlockEntityIterator();

        while (it.hasNext()) {
            BlockEntity entity = it.next();

            if (PlayerUtils.isWithin(entity.getPos(), (double) nearDistance.get()) && hideWhenNear.get()) {
                continue;
            }

            if (!PlayerUtils.isWithin(entity.getPos(), (double) farDistance.get()) && hideWhenFar.get()) {
                continue;
            }

            if (!(entity instanceof SignBlockEntity signEntity)) {
                continue;
            }

            SignText text = signEntity.getText(true);
            Text[] msgs = text.getMessages(true);

            renderNametagLines(
                    new Vector3d(entity.getPos().getX() + 0.5, entity.getPos().getY() + 0.5,
                            entity.getPos().getZ() + 0.5),
                    textScale.get(),
                    TextRenderer.get(),
                    textColor.get(),
                    true,
                    msgs[0].getString(),
                    msgs[1].getString(),
                    msgs[2].getString(),
                    msgs[3].getString());

        }
    }

    public void renderNametagLines(
            Vector3d pos,
            double scale,
            TextRenderer renderer,
            SettingColor color,
            boolean shadow,
            String line1,
            String line2,
            String line3,
            String line4) {
        if (!NametagUtils.to2D(pos, scale)) {
            return;
        }

        NametagUtils.begin(pos);
        renderer.begin(1, false, true);

        String[] lines = { line1, line2, line3, line4 };

        double lineHeight = renderer.getHeight();
        double totalHeight = lineHeight * lines.length;

        double y = -totalHeight / 2.0;

        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                y += lineHeight;
                continue;
            }

            double width = renderer.getWidth(line);
            renderer.render(line, -width / 2.0, y, color, shadow);
            y += lineHeight;
        }

        renderer.end();
        NametagUtils.end();
    }
}
