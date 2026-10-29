package dev.tanghulu.compat.smoke;

import baritone.api.*;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.pathing.goals.*;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.utils.IInputOverrideHandler;
import dev.tanghulu.addon.util.tree.BaritoneTreeDriver;
import net.minecraft.util.math.BlockPos;
import java.lang.reflect.Proxy;

public final class TreeNavigationRuntimeChecks {
    public static void run() throws Exception {
        ClassLoader loader = TreeNavigationRuntimeChecks.class.getClassLoader();
        Goal[] current = {null}; boolean[] active = {false}; int[] cancellations = {0}, forced = {0}, clears = {0};
        var custom = (ICustomGoalProcess) Proxy.newProxyInstance(loader, new Class<?>[]{ICustomGoalProcess.class}, (p, m, a) -> switch (m.getName()) {
            case "isActive" -> active[0];
            case "mostRecentGoal" -> current[0];
            case "setGoalAndPath" -> { current[0] = (Goal) a[0]; active[0] = true; yield null; }
            case "onLostControl" -> { active[0] = false; yield null; }
            default -> throw new AssertionError(m);
        });
        var path = (IPathingBehavior) Proxy.newProxyInstance(loader, new Class<?>[]{IPathingBehavior.class}, (p, m, a) -> switch (m.getName()) {
            case "isPathing" -> false;
            case "cancelEverything" -> { cancellations[0]++; yield false; }
            case "forceCancel" -> { forced[0]++; yield null; }
            default -> throw new AssertionError(m);
        });
        var inputs = (IInputOverrideHandler) Proxy.newProxyInstance(loader, new Class<?>[]{IInputOverrideHandler.class}, (p, m, a) -> {
            if (m.getName().equals("clearAllKeys")) { clears[0]++; return null; }
            throw new AssertionError(m);
        });
        var baritone = (IBaritone) Proxy.newProxyInstance(loader, new Class<?>[]{IBaritone.class}, (p, m, a) -> {
            if (m.getName().equals("getCustomGoalProcess")) return custom;
            if (m.getName().equals("getPathingBehavior")) return path;
            if (m.getName().equals("getInputOverrideHandler")) return inputs;
            return Proxy.newProxyInstance(loader, new Class<?>[]{m.getReturnType()}, (process, method, args) -> {
                if (method.getName().equals("isActive")) return false;
                throw new AssertionError(method);
            });
        });
        var settingsConstructor = Settings.class.getDeclaredConstructor(); settingsConstructor.setAccessible(true);
        var settings = settingsConstructor.newInstance(); settings.allowPlace.value = true; settings.allowBreak.value = true;
        var driver = new BaritoneTreeDriver(baritone, settings);
        driver.begin();
        if (settings.allowPlace.value || settings.allowBreak.value) throw new AssertionError("Navigation may place untracked blocks");
        driver.walkTo(BlockPos.ORIGIN, true);
        if (!driver.active() || driver.interrupted()) throw new AssertionError("Goal ownership missing");
        driver.stop();
        if (!settings.allowPlace.value || !settings.allowBreak.value || active[0] || cancellations[0] != 1
            || forced[0] != 1 || clears[0] != 1) {
            throw new AssertionError("Owned navigation not cancelled or settings not restored");
        }
        driver = new BaritoneTreeDriver(baritone, settings); driver.begin(); driver.walkTo(BlockPos.ORIGIN, true);
        driver.idle(); // Release the failed tree's movement before switching to the next tree.
        driver.walkTo(new BlockPos(6, 64, 6), true);
        if (!driver.active() || driver.interrupted() || !current[0].isInGoal(6, 64, 6)
            || settings.allowPlace.value || settings.allowBreak.value)
            throw new AssertionError("Skipping a failed tree cannot take ownership of the next ground target");
        driver.stop();
        int priorCancellations = cancellations[0], priorClears = clears[0];
        driver = new BaritoneTreeDriver(baritone, settings); driver.begin(); driver.walkTo(BlockPos.ORIGIN, true);
        current[0] = new GoalBlock(9, 64, 9); active[0] = true;
        if (!driver.interrupted()) throw new AssertionError("External takeover not detected");
        driver.stop();
        if (!active[0] || cancellations[0] != priorCancellations || clears[0] != priorClears) throw new AssertionError("External navigation was cancelled");
        System.out.println("TREE_NAVIGATION_RUNTIME_CHECKS PASS settingsRestored=true forcedStop=true jumpReleased=true takeover=true skipHandoff=true");
    }
}
