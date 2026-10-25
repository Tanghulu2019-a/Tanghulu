package dev.tanghulu.addon.modules;

import dev.tanghulu.addon.BaseModule;
import dev.tanghulu.addon.util.XaeroWaypointCleaner;
import dev.tanghulu.ancientapple.HunterModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.waypoints.Waypoint;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;

/** Clear wins over pending Tanghulu writers. No waypoint-origin filter is applied. */
public final class ClearXaeroWaypoints extends BaseModule {
    public ClearXaeroWaypoints() {
        super("一键清除航点", "优先停止本插件航点生成，再清除当前服务器/存档全部维度、分组和来源的 Xaero 与 Meteor 航点；完成后自动关闭。其他模组独立存储的航点需对应适配。");
    }

    @Override
    public void onActivate() {
        try {
            if (mc.player == null || mc.world == null) {
                warning("请先进入要清除航点的服务器或存档。");
                return;
            }
            // Invalidate asynchronous searches before deleting. A queued result may not restore points.
            for (var type : java.util.List.of(StructureFinder.class, ElytraFinder.class, MushroomFinder.class, HunterModule.class)) {
                var module = Modules.get().get(type);
                if (module != null && module.isActive()) module.toggle();
            }
            for (var module : Modules.get().getAll()) {
                if (module instanceof dev.tanghulu.addon.util.StashSearchRecords.Cache && module.isActive()) module.toggle();
            }
            int meteorCount = 0;
            try {
                var meteor = Waypoints.get();
                var points = new ArrayList<Waypoint>();
                meteor.forEach(points::add);
                meteor.removeAll(points);
                meteor.save();
                meteorCount = points.size();
                info("已清除 %d 个 Meteor 航点。", meteorCount);
            } catch (RuntimeException e) {
                error("Meteor 航点清除未完成：%s。", e.getMessage());
                LoggerFactory.getLogger(ClearXaeroWaypoints.class).warn("Meteor waypoint clearing failed", e);
            }
            if (FabricLoader.getInstance().isModLoaded("xaerominimap")) {
                try {
                    var result = XaeroWaypointCleaner.clearCurrentServer();
                    info("已清除 %d 个 Xaero 航点（全部维度、分组、死亡点及第三方来源），仓库搜索记录及缓存已清空。", result.count());
                } catch (Exception | LinkageError e) {
                    LoggerFactory.getLogger(ClearXaeroWaypoints.class).warn("Xaero waypoint clearing failed", e);
                    error("Xaero 清除未完成：%s。已尝试恢复 Xaero 原航点；Meteor 清除结果不受影响。", e.getMessage());
                }
            }
            info("本插件的航点生成模块已停止，需要再次定位时请手动开启。外部模组若持续生成航点，请关闭其生成功能。");
        } finally {
            if (isActive()) toggle();
        }
    }
}
