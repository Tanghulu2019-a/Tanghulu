package dev.tanghulu.compat.smoke;

import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.process.IMineProcess;
import dev.tanghulu.addon.util.seeds.AutoMineController;
import dev.tanghulu.addon.util.seeds.BaritoneMiningDriver;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Exercise the adapter against the actual loaded Baritone interfaces. */
public final class MiningRuntimeChecks {
    public static void run() {
        var calls = new ArrayList<String>();
        boolean[] active = {false};
        ClassLoader loader = MiningRuntimeChecks.class.getClassLoader();
        var mine = (IMineProcess) Proxy.newProxyInstance(loader, new Class<?>[]{IMineProcess.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "mineByName" -> { calls.add("mine:" + String.join(",", (String[]) args[0])); active[0] = true; yield null; }
                case "cancel" -> { calls.add("cancelMine"); active[0] = false; yield null; }
                case "isActive" -> active[0];
                default -> throw new AssertionError("Unexpected mine API: " + method);
            };
        });
        var path = (IPathingBehavior) Proxy.newProxyInstance(loader, new Class<?>[]{IPathingBehavior.class}, (proxy, method, args) -> {
            if (!method.getName().equals("cancelEverything")) throw new AssertionError("Unexpected path API: " + method);
            calls.add("cancelPath"); return true;
        });
        var baritone = (IBaritone) Proxy.newProxyInstance(loader, new Class<?>[]{IBaritone.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getMineProcess" -> mine;
            case "getPathingBehavior" -> path;
            default -> throw new AssertionError("Unexpected Baritone API: " + method);
        });
        var control = new AutoMineController();
        control.start(new BaritoneMiningDriver(baritone), new String[]{"diamond_ore", "deepslate_diamond_ore"});
        control.update(true);
        control.stop();
        control.update(true);
        if (!calls.equals(List.of("cancelPath", "mine:diamond_ore,deepslate_diamond_ore", "cancelMine", "cancelPath"))
            || control.running() || active[0]) throw new AssertionError("Mining adapter lifecycle: " + calls);
        System.out.println("MINING_RUNTIME_CHECKS PASS start=true stopMine=true stopPath=true noRestart=true");
    }
}
