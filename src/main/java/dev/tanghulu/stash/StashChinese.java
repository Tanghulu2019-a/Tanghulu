package dev.tanghulu.stash;

import java.util.Map;

/** Display text only. Module, setting, group and enum serialization IDs stay unchanged. */
public final class StashChinese {
    private StashChinese() {}

    public static final Map<String, String> MODULE_TITLES = Map.ofEntries(
        Map.entry("auto-log-plus", "Baritone助手"),
        Map.entry("better-stash-finder", "增强仓库搜索"),
        Map.entry("ElytraFlyPlusPlus", "鞘翅飞行增强"),
        Map.entry("Pitch40Util", "40度飞行辅助"),
        Map.entry("VanityESP", "装饰透视")
    );

    private static final Map<String, String> SETTING_TITLES = Map.ofEntries(
        Map.entry("AutoLogPlus/log-on-portal", "滞留传送门时退出"),
        Map.entry("AutoLogPlus/portal-ticks", "传送门停留时长"),
        Map.entry("AutoLogPlus/server-not-responding", "服务器无响应时退出"),
        Map.entry("AutoLogPlus/seconds-not-responding", "无响应超时时间"),
        Map.entry("AutoLogPlus/reconnect-after-not-responding", "超时退出后重连"),
        Map.entry("AutoLogPlus/reconnect-seconds", "重连等待时间"),
        Map.entry("AutoLogPlus/illegal-disconnect", "异常方式断线"),
        Map.entry("AutoLogPlus/travel-destination", "寻路目的地"),
        Map.entry("AutoLogPlus/travel-use-elytra", "启用鞘翅"),
        Map.entry("AutoLogPlus/travel-arrival-radius", "鞘翅到达判定距离"),
        Map.entry("AutoLogPlus/travel-clear-on-arrival", "到达后自动删除目的地坐标"),
        Map.entry("AutoLogPlus/travel-key", "一键起飞快捷键"),
        Map.entry("BetterStashFinder/storage-blocks", "搜索的储物方块"),
        Map.entry("BetterStashFinder/minimum-storage-count", "最少储物方块数量"),
        Map.entry("BetterStashFinder/shulker-instant-hit", "单个潜影盒即算仓库"),
        Map.entry("BetterStashFinder/crafter-instant-hit", "单个合成器即算仓库"),
        Map.entry("BetterStashFinder/disable-on-teleport-or-death", "传送或死亡后关闭"),
        Map.entry("BetterStashFinder/ignore-trial-chambers", "忽略试炼密室"),
        Map.entry("BetterStashFinder/minimum-distance", "距离原点的最小距离"),
        Map.entry("BetterStashFinder/only-old-chunks", "仅搜索旧区块"),
        Map.entry("BetterStashFinder/save-to-waypoints", "保存为地图标记"),
        Map.entry("BetterStashFinder/notifications", "游戏内通知"),
        Map.entry("BetterStashFinder/notification-mode", "通知方式"),
        Map.entry("BetterStashFinder/send-webhook", "发送 Webhook 通知"),
        Map.entry("BetterStashFinder/webhook-link", "Webhook 地址"),
        Map.entry("BetterStashFinder/advanced-logging", "详细通知内容"),
        Map.entry("BetterStashFinder/ping-for-stash-finder", "发现仓库时提醒我"),
        Map.entry("BetterStashFinder/discord-ID", "Discord 用户 ID"),
        Map.entry("ElytraFlyPlusPlus/bounce", "弹跳飞行"),
        Map.entry("ElytraFlyPlusPlus/motion-y-boost", "垂直动量加速"),
        Map.entry("ElytraFlyPlusPlus/only-while-colliding", "仅贴墙时加速"),
        Map.entry("ElytraFlyPlusPlus/tunnel-bounce", "隧道弹跳"),
        Map.entry("ElytraFlyPlusPlus/speed", "目标速度"),
        Map.entry("ElytraFlyPlusPlus/lock-pitch", "锁定俯仰角"),
        Map.entry("ElytraFlyPlusPlus/pitch", "俯仰角"),
        Map.entry("ElytraFlyPlusPlus/lock-yaw", "锁定水平朝向"),
        Map.entry("ElytraFlyPlusPlus/use-custom-yaw", "自定义水平朝向"),
        Map.entry("ElytraFlyPlusPlus/yaw", "水平朝向"),
        Map.entry("ElytraFlyPlusPlus/highway-obstacle-passer", "道路自动绕障"),
        Map.entry("ElytraFlyPlusPlus/use-custom-start-position", "自定义道路起点"),
        Map.entry("ElytraFlyPlusPlus/start-position", "道路起点坐标"),
        Map.entry("ElytraFlyPlusPlus/away-from-start-position", "远离起点飞行"),
        Map.entry("ElytraFlyPlusPlus/distance", "绕障目标距离"),
        Map.entry("ElytraFlyPlusPlus/y-level", "道路弹跳高度"),
        Map.entry("ElytraFlyPlusPlus/avoid-portal-traps", "躲避传送门陷阱"),
        Map.entry("ElytraFlyPlusPlus/portal-avoid-distance", "陷阱绕行触发距离"),
        Map.entry("ElytraFlyPlusPlus/portal-scan-width", "陷阱扫描宽度"),
        Map.entry("ElytraFlyPlusPlus/chestplate-fakefly", "胸甲飞行"),
        Map.entry("ElytraFlyPlusPlus/toggle-elytra", "自动切换鞘翅和胸甲"),
        Map.entry("Pitch40Util/auto-adjust-bounds", "自动调整高度范围"),
        Map.entry("Pitch40Util/bound-gap", "高度上下限间距"),
        Map.entry("Pitch40Util/auto-firework", "自动使用烟花"),
        Map.entry("Pitch40Util/velocity-threshold", "垂直速度阈值"),
        Map.entry("Pitch40Util/cooldown-ticks", "烟花冷却时间"),
        Map.entry("VanityESP/item-frames", "高亮地图展示框"),
        Map.entry("VanityESP/banners", "高亮旗帜"),
        Map.entry("VanityESP/map-color", "地图画填充颜色"),
        Map.entry("VanityESP/map-outline-color", "地图画轮廓颜色"),
        Map.entry("VanityESP/banner-fill", "旗帜填充颜色"),
        Map.entry("VanityESP/banner-outline", "旗帜轮廓颜色")
    );

    public static String settingTitle(String module, String setting) {
        return SETTING_TITLES.get(module + "/" + setting);
    }

    public static String groupTitle(String name) {
        return switch (name) {
            case "General" -> "常规设置";
            case "Colors" -> "颜色设置";
            case "Obstacle Passer" -> "自动绕障";
            default -> name;
        };
    }

    public static String enumTitle(String setting, String value) {
        return switch (value) {
            case "Chat" -> "聊天消息";
            case "Toast" -> "弹窗通知";
            case "Highlight" -> "游戏内高亮";
            case "LogWebhook" -> "Webhook 通知";
            case "Lines" -> "仅轮廓";
            case "Sides" -> "仅填充";
            case "Both" -> switch (setting) {
                case "notification-mode" -> "聊天和弹窗";
                case "log-mode" -> "高亮和 Webhook";
                default -> "填充和轮廓";
            };
            default -> value;
        };
    }
}
