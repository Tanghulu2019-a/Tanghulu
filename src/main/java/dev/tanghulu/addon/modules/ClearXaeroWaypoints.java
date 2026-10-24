package dev.tanghulu.addon.modules;

import dev.tanghulu.addon.BaseModule;
import dev.tanghulu.addon.util.XaeroWaypointCleaner;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/** A one-shot action; never remains enabled or runs again on a later login. */
public final class ClearXaeroWaypoints extends BaseModule {
    public ClearXaeroWaypoints() {
        super("一键清除航点", "直接清除当前服务器/存档所有维度、所有分组的 Xaero 小地图航点，不保留备份，完成后自动关闭。");
    }

    @Override
    public void onActivate() {
        try {
            if (mc.player == null || mc.world == null) {
                warning("请先进入要清除航点的服务器或存档。");
                return;
            }
            if (!FabricLoader.getInstance().isModLoaded("xaerominimap")) {
                error("请先安装 Xaero 小地图。");
                return;
            }
            // Keep optional Xaero types out of this module's fields and signatures.
            var result = XaeroWaypointCleaner.clearCurrentServer();
            if (result.count() == 0) info("当前服务器/存档没有可清除的航点。");
            else {
                info("已清除 %d 个航点（包括各维度、分组及死亡点）。", result.count());
            }
        } catch (Exception | LinkageError e) {
            LoggerFactory.getLogger(ClearXaeroWaypoints.class).warn("Xaero waypoint clearing failed", e);
            error("清除未完成：%s。已尝试恢复原航点，详细原因请查看日志。", e.getMessage());
        } finally {
            if (isActive()) toggle();
        }
    }
}
