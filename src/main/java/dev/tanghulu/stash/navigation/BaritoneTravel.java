package dev.tanghulu.stash.navigation;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.world.World;

/** Loaded only after the optional Baritone dependency has been checked. */
public final class BaritoneTravel implements OneClickTravel.Driver {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final IBaritone baritone;
    private final OneClickTravel.Destination destination;
    private final Goal goal;
    private final boolean elytra;
    private final double arrivalRadius;
    private Boolean previousAutoJump;
    private boolean started;
    private boolean wasGliding;

    public BaritoneTravel(OneClickTravel.Destination destination, boolean elytra, double arrivalRadius) {
        this.destination = destination;
        this.elytra = elytra;
        this.arrivalRadius = arrivalRadius;
        baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone == null) throw new IllegalArgumentException("无法获取 Baritone 实例。");
        if (destination.y() != null && (destination.y() < mc.world.getBottomY()
            || destination.y() >= mc.world.getTopYInclusive() + 1)) {
            throw new IllegalArgumentException("目的地 Y 坐标超出当前世界的高度范围。");
        }
        goal = destination.y() == null ? new GoalXZ(destination.x(), destination.z())
            : new GoalBlock(destination.x(), destination.y(), destination.z());
        if (!elytra) return;
        if (!mc.world.getRegistryKey().equals(World.NETHER)) {
            throw new IllegalArgumentException("当前 Baritone 的鞘翅寻路仅支持下界；可关闭“启用鞘翅”使用普通寻路。");
        }
        if (destination.flightY() <= 0 || destination.flightY() >= 128) {
            throw new IllegalArgumentException("鞘翅目的地的 Y 必须在 1～127 之间；只输入 X Z 时使用 Y=64。");
        }
        if (!baritone.getElytraProcess().isLoaded()) {
            throw new IllegalArgumentException("Baritone 鞘翅寻路的本地库未加载，无法起飞。");
        }
        var chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isOf(Items.ELYTRA) || chest.getMaxDamage() - chest.getDamage()
            <= BaritoneAPI.getSettings().elytraMinimumDurability.value) {
            throw new IllegalArgumentException("请先穿上耐久充足的鞘翅。");
        }
        int fireworks = 0;
        for (int slot = 0; slot < 36; slot++) {
            var stack = mc.player.getInventory().getStack(slot);
            if (stack.isOf(Items.FIREWORK_ROCKET)) fireworks += stack.getCount();
        }
        if (fireworks <= BaritoneAPI.getSettings().elytraMinFireworksBeforeLanding.value) {
            throw new IllegalArgumentException("烟花数量不足，请补充烟花后重试。");
        }
    }

    @Override public void start() {
        // Stop any earlier elytra process before setting a new custom goal. Baritone's
        // setGoal forwards to an active elytra process, including when given null.
        baritone.getPathingBehavior().cancelEverything();
        baritone.getCustomGoalProcess().setGoal(goal);
        started = true;
        if (elytra) {
            previousAutoJump = BaritoneAPI.getSettings().elytraAutoJump.value;
            BaritoneAPI.getSettings().elytraAutoJump.value = true;
            baritone.getElytraProcess().pathTo(goal);
        } else {
            baritone.getCustomGoalProcess().path();
        }
    }

    @Override public boolean ownsGoal() {
        return started && baritone.getCustomGoalProcess().mostRecentGoal() == goal;
    }

    @Override public boolean arrived() {
        if (mc.player == null) return false;
        if (!elytra) return goal.isInGoal(mc.player.getBlockPos());
        wasGliding |= mc.player.isGliding();
        // Allow Baritone to finish its landing, rather than cancelling flight overhead.
        double dx = mc.player.getX() - destination.x() - 0.5;
        double dz = mc.player.getZ() - destination.z() - 0.5;
        return wasGliding && mc.player.isOnGround() && !mc.player.isGliding()
            && !baritone.getElytraProcess().isActive() && dx * dx + dz * dz <= arrivalRadius * arrivalRadius;
    }

    @Override public boolean active() {
        return elytra ? baritone.getElytraProcess().isActive() : baritone.getCustomGoalProcess().isActive();
    }

    @Override public void stop(boolean clearGoal) {
        baritone.getPathingBehavior().cancelEverything();
        if (clearGoal) {
            // Let Baritone consume its GOAL_SET command on the next tick so that
            // PathingBehavior also drops the rendered goal, not just the input.
            baritone.getCustomGoalProcess().setGoal(null);
        }
    }

    @Override public void restoreSettings() {
        if (previousAutoJump == null) return;
        // Respect an explicit change made by the user during the journey.
        if (BaritoneAPI.getSettings().elytraAutoJump.value) {
            BaritoneAPI.getSettings().elytraAutoJump.value = previousAutoJump;
        }
        previousAutoJump = null;
    }
}
