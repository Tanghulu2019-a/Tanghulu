// Adapted from Numby Hack v2.6.24 (GPL-3.0); localized and integrated by Tanghulu, 2026-10-24.
package dev.tanghulu.addon.modules;

import dev.tanghulu.addon.BaseModule;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.Quiver;
import meteordevelopment.meteorclient.systems.modules.player.EXPThrower;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BowItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.Items;

/**
 * FloRida from venomhack
 */
public class Beyblade extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("常规设置");

    private final Setting<Mode> spinMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("旋转模式")
            .description("选择服务端旋转方式，其他玩家可以看到旋转效果。")
            .defaultValue(Mode.Beyblade)
            .build());

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
            .name("连续旋转速度")
            .description("每游戏刻的旋转角度。")
            .defaultValue(20)
            .sliderMin(0.0)
            .sliderMax(50.0)
            .visible(() -> spinMode.get() == Mode.FloRida)
            .build());

    private final Setting<AntiDesyncTrigger> antiDesync = sgGeneral.add(new EnumSetting.Builder<AntiDesyncTrigger>()
            .name("暂停旋转条件")
            .description("持有经验瓶、末影珍珠、弓或启用相关战斗模块时暂停旋转；可选择是否包含鞘翅。")
            .defaultValue(AntiDesyncTrigger.All)
            .visible(() -> spinMode.get() == Mode.Beyblade)
            .build());

    private final Setting<Boolean> yaw = sgGeneral.add(new BoolSetting.Builder()
            .name("水平旋转")
            .description("启用对应方向的旋转。")
            .defaultValue(true)
            .visible(() -> spinMode.get() == Mode.Beyblade)
            .build());

    private final Setting<Integer> ySpeed = sgGeneral.add(new IntSetting.Builder()
            .name("水平旋转速度")
            .description("每游戏刻的旋转角度。")
            .defaultValue(5)
            .range(1, 100)
            .visible(() -> spinMode.get() == Mode.Beyblade && yaw.get())
            .build());

    private final Setting<Boolean> pitch = sgGeneral.add(new BoolSetting.Builder()
            .name("上下摆头")
            .description("启用对应方向的旋转。")
            .defaultValue(false)
            .visible(() -> spinMode.get() == Mode.Beyblade)
            .build());

    private final Setting<Integer> pSpeed = sgGeneral.add(new IntSetting.Builder()
            .name("上下摆头间隔")
            .description("每次切换抬头与低头之间的游戏刻数，20 刻约为一秒。")
            .defaultValue(5)
            .range(1, 100)
            .visible(() -> spinMode.get() == Mode.Beyblade && pitch.get())
            .build());

    public Beyblade() {
        super(BaseModule.CATEGORY, "陀螺旋转", "让其他玩家看到你水平旋转或上下摆头，自己的视角保持不变。", "Bayblade", "Beyblade");
    }

    private double count = 0;
    private short yCount = 0;
    private short pCount = 0;

    @Override
    public void onActivate() {
        count = 0;
        yCount = 0;
        pCount = 0;
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        if (spinMode.get() == Mode.Beyblade) {
            beyblade();
        } else {
            floRida();
        }
    }

    private void beyblade() {
        switch (antiDesync.get()) {
            case All -> {
                if (Modules.get().isActive(EXPThrower.class) ||
                        Modules.get().isActive(meteordevelopment.meteorclient.systems.modules.combat.BedAura.class) ||
                        mc.player.getMainHandStack().getItem() instanceof ExperienceBottleItem ||
                        mc.player.getOffHandStack().getItem() instanceof ExperienceBottleItem ||
                        mc.player.getMainHandStack().getItem() instanceof EnderPearlItem ||
                        mc.player.getOffHandStack().getItem() instanceof EnderPearlItem ||
                        mc.player.getMainHandStack().getItem() instanceof ExperienceBottleItem ||
                        mc.player.getOffHandStack().getItem() instanceof ExperienceBottleItem ||
                        mc.player.getMainHandStack().getItem() instanceof BowItem ||
                        mc.player.getOffHandStack().getItem() instanceof BowItem ||
                        mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA)
                    return;
            }
            case ExceptElytra -> {
                if (Modules.get().isActive(EXPThrower.class) ||
                        Modules.get().isActive(meteordevelopment.meteorclient.systems.modules.combat.BedAura.class) ||
                        mc.player.getMainHandStack().getItem() instanceof ExperienceBottleItem ||
                        mc.player.getOffHandStack().getItem() instanceof ExperienceBottleItem ||
                        mc.player.getMainHandStack().getItem() instanceof EnderPearlItem ||
                        mc.player.getOffHandStack().getItem() instanceof EnderPearlItem ||
                        mc.player.getMainHandStack().getItem() instanceof ExperienceBottleItem ||
                        mc.player.getOffHandStack().getItem() instanceof ExperienceBottleItem ||
                        mc.player.getMainHandStack().getItem() instanceof BowItem ||
                        mc.player.getOffHandStack().getItem() instanceof BowItem)
                    return;
            }
            case None -> {
            }
        }

        yCount += ySpeed.get();
        if (yCount > 180)
            yCount = -180;

        if (pitch.get()) {
            count++;

            if (count <= pSpeed.get())
                pCount = 90;
            if (count > pSpeed.get())
                pCount = -90;
            if (count >= pSpeed.get() + pSpeed.get())
                count = 0;
        }

        Rotations.rotate(yaw.get() ? yCount : mc.player.getYaw(), pitch.get() ? pCount : mc.player.getPitch());
    }

    private void floRida() {
        Modules modules = Modules.get();
        if (!modules.isActive(EXPThrower.class) && !modules.isActive(Quiver.class)
                && !modules.isActive(EXPThrower.class)) {
            count += speed.get();
            if (count > 180) {
                count -= 360;
            }

            Rotations.rotate(count, 0.0);
        }
    }

    public enum Mode {
        Beyblade("陀螺旋转"),
        FloRida("连续旋转");
        private final String label;
        Mode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum AntiDesyncTrigger {
        All("全部条件"), ExceptElytra("鞘翅除外"), None("从不暂停");
        private final String label;
        AntiDesyncTrigger(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
}
