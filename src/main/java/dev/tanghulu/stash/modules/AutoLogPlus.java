package dev.tanghulu.stash.modules;

import dev.tanghulu.stash.Addon;
import dev.tanghulu.addon.util.LogSafety;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import dev.tanghulu.stash.navigation.BaritoneTravel;
import dev.tanghulu.stash.navigation.OneClickTravel;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;

public class AutoLogPlus extends Module
{
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("耐久与位置检测");
    private final Setting<Boolean> durabilityCheck = sgSafety.add(new BoolSetting.Builder()
        .name("耐久检测").description("已穿戴装备、主手或副手任一可损坏物品剩余耐久达到阈值时断开，不受附近玩家限制。")
        .defaultValue(false).build());
    private final Setting<Integer> durabilityPercent = sgSafety.add(new IntSetting.Builder()
        .name("最低剩余耐久百分比").description("剩余耐久小于或等于此百分比触发；忽略空槽和无耐久物品。")
        .defaultValue(10).range(0,100).sliderRange(0,100).visible(durabilityCheck::get).build());
    private final Setting<Boolean> armorOnly = sgSafety.add(new BoolSetting.Builder()
        .name("仅检查护甲耐久").description("仅检测穿戴装备；关闭时也检查主手和副手。")
        .defaultValue(false).visible(durabilityCheck::get).build());
    private final Setting<Boolean> ignoreElytra = sgSafety.add(new BoolSetting.Builder()
        .name("忽略鞘翅耐久").description("耐久检测不计入鞘翅。")
        .defaultValue(false).visible(durabilityCheck::get).build());
    private final Setting<Boolean> yCheck = sgSafety.add(new BoolSetting.Builder()
        .name("Y轴检测").description("Y 坐标超出设定安全区间时断开，不受附近玩家限制；上下限填反时自动交换。")
        .defaultValue(false).build());
    private final Setting<Double> minY = sgSafety.add(new DoubleSetting.Builder()
        .name("安全Y下限").description("低于此高度时断开。")
        .defaultValue(-64).sliderRange(-64,320).visible(yCheck::get).build());
    private final Setting<Double> maxY = sgSafety.add(new DoubleSetting.Builder()
        .name("安全Y上限").description("高于此高度时断开。")
        .defaultValue(320).sliderRange(-64,320).visible(yCheck::get).build());
    private final Setting<Boolean> coordinateCheck = sgSafety.add(new BoolSetting.Builder()
        .name("坐标检测").description("在当前所在维度按 X/Z 距离触发断开，不受附近玩家限制。")
        .defaultValue(false).build());
    private final Setting<Double> targetX = sgSafety.add(new DoubleSetting.Builder()
        .name("目标X").description("检测中心的 X 坐标。")
        .defaultValue(0).sliderRange(-10000,10000).visible(coordinateCheck::get).build());
    private final Setting<Double> targetZ = sgSafety.add(new DoubleSetting.Builder()
        .name("目标Z").description("检测中心的 Z 坐标。")
        .defaultValue(0).sliderRange(-10000,10000).visible(coordinateCheck::get).build());
    private final Setting<Double> coordinateRadius = sgSafety.add(new DoubleSetting.Builder()
        .name("坐标半径").description("距中心的水平距离，单位为格。")
        .defaultValue(5).min(0).sliderRange(0,1000).visible(coordinateCheck::get).build());
    private final Setting<Boolean> outsideCoordinates = sgSafety.add(new BoolSetting.Builder()
        .name("离开范围时断开").description("开启：超出半径触发；关闭：进入半径（包含边界）触发。")
        .defaultValue(false).visible(coordinateCheck::get).build());

    private final SettingGroup sgTravel = settings.createGroup("一键起飞");

    private final Setting<String> destination = sgTravel.add(new StringSetting.Builder()
        .name("travel-destination")
        .description("输入 X Z 或 X Y Z 整数坐标，支持空格或逗号分隔；留空表示没有目的地。")
        .defaultValue("").build());

    private final Setting<Boolean> useElytra = sgTravel.add(new BoolSetting.Builder()
        .name("travel-use-elytra")
        .description("启用 Baritone 鞘翅寻路及自动起飞，仅支持下界；关闭时使用普通寻路。")
        .defaultValue(false).build());

    private final Setting<Double> arrivalRadius = sgTravel.add(new DoubleSetting.Builder()
        .name("travel-arrival-radius")
        .description("鞘翅落地后距离目的地的水平距离不超过此值才算到达；普通寻路使用精确目标。")
        .defaultValue(16).min(1).max(128).sliderRange(1, 64)
        .visible(useElytra::get).build());

    private final Setting<Boolean> clearOnArrival = sgTravel.add(new BoolSetting.Builder()
        .name("travel-clear-on-arrival")
        .description("成功到达后清空目的地输入和 Baritone 目标；中断或失败时保留。")
        .defaultValue(false).build());

    private final Setting<Keybind> takeoffKey = sgTravel.add(new KeybindSetting.Builder()
        .name("travel-key")
        .description("模块开启且没有打开菜单时，按此快捷键开始前往目的地。")
        .defaultValue(Keybind.none())
        .action(() -> { if (mc.currentScreen == null) startTravel(); }).build());

    private OneClickTravel travel;
    private net.minecraft.client.world.ClientWorld travelWorld;
    private net.minecraft.client.network.ClientPlayNetworkHandler travelConnection;

    private final Setting<Boolean> logPortal = sgGeneral.add(new BoolSetting.Builder()
        .name("log-on-portal")
        .description("在传送门内停留过久时自动退出。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> portalTicks = sgGeneral.add(new IntSetting.Builder()
        .name("portal-ticks")
        .description("触发退出前允许停留的游戏刻数；正常穿过传送门约需 80 刻。")
        .defaultValue(30)
        .min(1)
        .sliderMax(70)
        .visible(logPortal::get)
        .build()
    );

    private final Setting<Boolean> serverNotResponding = sgGeneral.add(new BoolSetting.Builder()
        .name("server-not-responding")
        .description("服务器长时间没有响应时自动退出。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> serverNotRespondingSecs = sgGeneral.add(new DoubleSetting.Builder()
        .name("seconds-not-responding")
        .description("服务器无响应达到此秒数后退出。")
        .defaultValue(10)
        .min(1)
        .sliderMax(60)
        .visible(serverNotResponding::get)
        .build()
    );

    private final Setting<Boolean> reconnectAfterNotResponding = sgGeneral.add(new BoolSetting.Builder()
        .name("reconnect-after-not-responding")
        .description("因服务器无响应而退出后自动重连。")
        .defaultValue(false)
        .visible(serverNotResponding::get)
        .build()
    );

    private final Setting<Double> secondsToReconnect = sgGeneral.add(new DoubleSetting.Builder()
        .name("reconnect-seconds")
        .description("退出后等待多少秒重连；会临时覆盖 Meteor 的自动重连间隔。")
        .defaultValue(60)
        .min(10)
        .sliderMax(60 * 5)
        .visible(() -> reconnectAfterNotResponding.get() && serverNotResponding.get())
        .build()
    );

    private final Setting<Boolean> illegalDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("illegal-disconnect")
        .description("使用原模块的异常断线方式退出服务器。")
        .defaultValue(false)
        .build()
    );

    public AutoLogPlus()
    {
        super(Addon.CATEGORY, "auto-log-plus", "一键设置 Baritone 目的地，可选鞘翅和到达后清除坐标，并提供传送门滞留及服务器无响应保护，以及耐久、Y轴与坐标保护。");
        runInMainMenu = true;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        var list = theme.verticalList();
        var row = list.add(theme.horizontalList()).expandX().widget();
        row.add(theme.button("一键起飞")).widget().action = this::startTravel;
        row.add(theme.button("停止寻路")).widget().action = () -> {
            stopTravel();
            info("已停止本助手的寻路，目的地坐标已保留。");
        };
        list.add(theme.label("目的地格式：X Z 或 X Y Z；鞘翅模式仅限下界。"));
        list.add(theme.label("按钮会开启本模块；快捷键需先开启模块。"));
        return list;
    }

    private void startTravel() {
        if (mc.player == null || mc.world == null) {
            error("请先进入世界再使用一键起飞。");
            return;
        }
        if (!BaritoneUtils.IS_AVAILABLE) {
            error("一键起飞需要安装与当前版本匹配的 Meteor Baritone。");
            return;
        }
        try {
            String coordinates = destination.get();
            var target = OneClickTravel.Destination.parse(coordinates);
            // Validate the new request before interrupting any existing journey.
            var driver = new BaritoneTravel(target, useElytra.get(), arrivalRadius.get());
            stopTravel();
            enable();
            travelWorld = mc.world;
            travelConnection = mc.getNetworkHandler();
            travel = new OneClickTravel(driver, coordinates, clearOnArrival.get());
            info("已开始%s：%s。", useElytra.get() ? "鞘翅寻路" : "普通寻路", coordinates);
        } catch (RuntimeException | LinkageError e) {
            error("无法开始寻路：%s", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private void tickTravel() {
        if (travel == null) return;
        if (mc.world != travelWorld || mc.getNetworkHandler() != travelConnection
            || mc.player == null || !mc.player.isAlive()) {
            stopTravel();
            return;
        }
        var result = travel.tick();
        if (result == OneClickTravel.Result.RUNNING) return;
        if (result == OneClickTravel.Result.ARRIVED) {
            boolean clear = travel.shouldClear(destination.get());
            if (clear) destination.set("");
            info("已到达目的地。%s", clear ? "目的地坐标已自动删除。" : "目的地坐标已保留。");
        } else {
            warning("寻路已中断或目标已被替换，目的地坐标已保留。");
        }
        travel = null;
        travelWorld = null;
        travelConnection = null;
    }

    private void stopTravel() {
        if (travel != null) travel.cancel();
        travel = null;
        travelWorld = null;
        travelConnection = null;
    }

    @Override
    public void onDeactivate() {
        stopTravel();
        restoreReconnect();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) { stopTravel(); }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        currPortalTicks = 0;
        restoreReconnect();
    }

    @Override
    public void onActivate() {
        currPortalTicks = 0;
        restoreReconnect();
    }

    @SuppressWarnings("unchecked")
    private void restoreReconnect() {
        if (waitingForReconnection)
        {
            waitingForReconnection = false;
            AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
            Setting<Double> delay = ((Setting<Double>)autoReconnect.settings.get("delay"));
            delay.set(oldDelay);
            if (!autoReconnectEnabled && autoReconnect.isActive())
            {
                autoReconnect.toggle();
            }
        }

    }

    private int currPortalTicks = 0;
    private double oldDelay;
    private boolean autoReconnectEnabled;
    private boolean waitingForReconnection = false;

    @EventHandler
    private void onTick(TickEvent.Post event)
    {
        tickTravel();
        // Personal protection runs independently of travel and optional Baritone.
        if (mc.player != null && mc.world != null && mc.player.isAlive()) {
            String reason = checkPersonalSafety();
            if (reason != null) {
                logOut(reason, true);
                return;
            }
        }
        // If in the 2b2t queue
        if (mc.player == null || mc.world == null || mc.player.getAbilities().allowFlying) return;

        if (serverNotResponding.get() && !waitingForReconnection)
        {
            if (TickRate.INSTANCE.getTimeSinceLastTick() > serverNotRespondingSecs.get())
            {
                if (reconnectAfterNotResponding.get())
                {
                    AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
                    autoReconnectEnabled = autoReconnect.isActive();
                    Setting<Double> delay = ((Setting<Double>)autoReconnect.settings.get("delay"));
                    oldDelay = delay.get();
                    delay.set(secondsToReconnect.get());
                    if (!autoReconnectEnabled)
                    {
                        autoReconnect.toggle();
                    }
                    waitingForReconnection = true;
                }
                logOut("服务器已无响应 " + serverNotRespondingSecs.get() + " 秒。", !reconnectAfterNotResponding.get());
                return;
            }
        }

        if (logPortal.get() && mc.player.portalManager != null)
        {
            if (mc.player.portalManager.isInPortal())
            {
                currPortalTicks++;
                if (currPortalTicks > portalTicks.get())
                {
                    logOut("已在传送门内停留 " + currPortalTicks + " 游戏刻。", true);
                    return;
                }
            } else {
                currPortalTicks = 0;
            }
        } else {
            currPortalTicks = 0;
        }

    }

    private String checkPersonalSafety() {
        if (durabilityCheck.get()) {
            for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
                if (armorOnly.get() && (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)) continue;
                ItemStack stack = mc.player.getEquippedStack(slot);
                if (ignoreElytra.get() && stack.isOf(Items.ELYTRA)) continue;
                if (!stack.isEmpty() && stack.isDamageable()
                    && LogSafety.lowDurability(stack.getMaxDamage(), stack.getDamage(), durabilityPercent.get())) {
                    return "装备耐久不足：" + stack.getName().getString() + "，剩余 "
                        + (stack.getMaxDamage() - stack.getDamage()) + "/" + stack.getMaxDamage() + "。";
                }
            }
        }
        if (yCheck.get() && LogSafety.outsideY(mc.player.getY(), minY.get(), maxY.get()))
            return "Y轴超出安全范围，当前 Y=" + mc.player.getY() + "。";
        if (coordinateCheck.get() && LogSafety.coordinates(mc.player.getX(), mc.player.getZ(),
            targetX.get(), targetZ.get(), coordinateRadius.get(), outsideCoordinates.get()))
            return "坐标检测触发，目标 X=" + targetX.get() + " Z=" + targetZ.get() + "，半径 " + coordinateRadius.get() + "。";
        return null;
    }

    private void logOut(String reason, boolean turnOffReconnect)
    {
        if (mc.player == null) return;
        stopTravel();
        if (turnOffReconnect)
        {
            if (Modules.get().get(AutoReconnect.class).isActive())
            {
                Modules.get().get(AutoReconnect.class).toggle();
            }
        }

        if (illegalDisconnect.get())
        {
            mc.player.networkHandler.sendChatMessage(String.valueOf((char)0));
        }
        else
        {
            mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[Baritone助手] " + reason)));
        }
    }
}
