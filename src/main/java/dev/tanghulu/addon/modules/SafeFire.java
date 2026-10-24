// Adapted from Numby Hack v2.6.24 (GPL-3.0); localized and integrated by Tanghulu, 2026-10-24.
package dev.tanghulu.addon.modules;

import dev.tanghulu.addon.BaseModule;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.FireBlock;
import net.minecraft.block.SoulFireBlock;
import net.minecraft.util.shape.VoxelShapes;

/**
 * modified by cqb13
 * Ported from:
 * Tanuki, orignaly by walaryne
 */
public class SafeFire extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("常规设置");

    private final Setting<Boolean> fire = sgGeneral.add(new BoolSetting.Builder()
            .name("普通火焰")
            .description("阻止走入普通火焰。")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> soulFire = sgGeneral.add(new BoolSetting.Builder()
            .name("灵魂火焰")
            .description("阻止走入灵魂火焰。")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> campfire = sgGeneral.add(new BoolSetting.Builder()
            .name("普通营火")
            .description("将普通营火视为完整方块，阻止走入。")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> soulCampfire = sgGeneral.add(new BoolSetting.Builder()
            .name("灵魂营火")
            .description("将灵魂营火视为完整方块，阻止走入。")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> lava = sgGeneral.add(new BoolSetting.Builder()
            .name("熔岩")
            .description("阻止走入熔岩方块。")
            .defaultValue(false)
            .build());

    public SafeFire() {
        super(BaseModule.CATEGORY, "防火行走", "通过碰撞阻止走入火焰，可选阻挡营火与熔岩；不提供火焰免疫。", "Safe Fire", "safe-fire");
    }

    @EventHandler
    public void onCollisionShape(CollisionShapeEvent event) {
        if (event.state.getBlock() instanceof FireBlock && fire.get()) {
            event.shape = VoxelShapes.fullCube();
        }

        if (event.state.getBlock() instanceof SoulFireBlock && soulFire.get()) {
            event.shape = VoxelShapes.fullCube();
        }

        if (event.state.getBlock() == Blocks.CAMPFIRE && campfire.get()) {
            event.shape = VoxelShapes.fullCube();
        }

        if (event.state.getBlock() == Blocks.SOUL_CAMPFIRE && soulCampfire.get()) {
            event.shape = VoxelShapes.fullCube();
        }

        if (event.state.getBlock() == Blocks.LAVA && lava.get()) {
            event.shape = VoxelShapes.fullCube();
        }
    }
}
