package dev.tanghulu.addon.util.tree;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import net.minecraft.util.math.BlockPos;

/** Navigation only. The tree worker owns all mining and tracked scaffold placements. */
public final class BaritoneTreeDriver {
    private final IBaritone baritone;
    private final Settings settings;
    private Goal goal;
    private Boolean previousPlace, previousBreak;

    public BaritoneTreeDriver() {
        this(BaritoneAPI.getProvider().getPrimaryBaritone(), BaritoneAPI.getSettings());
    }
    public BaritoneTreeDriver(IBaritone baritone, Settings settings) {
        this.baritone = baritone;
        this.settings = java.util.Objects.requireNonNull(settings);
        if (baritone == null) throw new IllegalStateException("无法获取 Baritone 实例。");
        if (otherProcess() || baritone.getCustomGoalProcess().isActive() || baritone.getPathingBehavior().isPathing()) {
            throw new IllegalStateException("Baritone 正在执行其他任务，请先结束该任务。");
        }
    }

    private boolean otherProcess() {
        return baritone.getMineProcess().isActive() || baritone.getBuilderProcess().isActive()
            || baritone.getExploreProcess().isActive() || baritone.getFollowProcess().isActive()
            || baritone.getGetToBlockProcess().isActive() || baritone.getFarmProcess().isActive()
            || baritone.getElytraProcess().isActive();
    }

    public void begin() {
        if (otherProcess() || baritone.getCustomGoalProcess().isActive() || baritone.getPathingBehavior().isPathing()) {
            throw new IllegalStateException("扫描期间 Baritone 开始了其他任务，请先结束该任务。");
        }
        previousPlace = settings.allowPlace.value;
        previousBreak = settings.allowBreak.value;
        settings.allowPlace.value = false;
        settings.allowBreak.value = false;
    }

    public void walkTo(BlockPos pos, boolean exact) {
        if (interrupted()) throw new IllegalStateException("Baritone 被其他任务接管。");
        cancelNavigation();
        goal = exact ? new GoalBlock(pos) : new GoalNear(pos, 1);
        baritone.getCustomGoalProcess().setGoalAndPath(goal);
    }

    public boolean interrupted() {
        return otherProcess() || (goal != null && baritone.getCustomGoalProcess().mostRecentGoal() != goal);
    }
    public boolean active() { return goal != null && baritone.getCustomGoalProcess().isActive(); }

    public void cancelNavigation() {
        if (goal != null && !interrupted()) {
            baritone.getCustomGoalProcess().onLostControl();
            if (!baritone.getPathingBehavior().cancelEverything()) baritone.getPathingBehavior().forceCancel();
            baritone.getInputOverrideHandler().clearAllKeys();
        }
        goal = null;
    }

    public void idle() {
        cancelNavigation();
        if (previousPlace != null && !otherProcess() && !baritone.getCustomGoalProcess().isActive()) {
            baritone.getInputOverrideHandler().clearAllKeys();
        }
    }

    public void stop() {
        try { cancelNavigation(); }
        finally {
            if (previousPlace != null && !settings.allowPlace.value) settings.allowPlace.value = previousPlace;
            if (previousBreak != null && !settings.allowBreak.value) settings.allowBreak.value = previousBreak;
            previousPlace = previousBreak = null;
        }
    }
}
