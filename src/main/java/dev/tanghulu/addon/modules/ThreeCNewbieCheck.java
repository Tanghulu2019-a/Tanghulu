package dev.tanghulu.addon.modules;

import dev.tanghulu.addon.BaseModule;
import dev.tanghulu.addon.util.ChatUtils;
import dev.tanghulu.addon.util.threec.NearbyPlayerSelector;
import dev.tanghulu.addon.util.threec.ThreeCPlayerService;
import dev.tanghulu.addon.util.threec.ThreeCQueryQueue;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ThreeCNewbieCheck extends BaseModule {
    private final Setting<String> playerName = settings.getDefaultGroup().add(new StringSetting.Builder()
        .name("玩家名字").defaultValue("").visible(() -> false).build());
    private final Setting<Integer> threshold = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("萌新时长阈值（小时）").description("总时长低于该值为绿色萌新，等于或超过为红色糕手。")
        .defaultValue(24).min(1).sliderRange(1, 240).onChanged(value -> refresh()).build());
    private final Setting<Boolean> nametag = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("联动名字标签").description("开启本模块和 Meteor Nametags 后，在名字旁显示已查询的判断。")
        .defaultValue(true).build());
    private final Setting<Boolean> autoQuery = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("范围内自动查询").description("开启模块后自动查询范围内所有已加载玩家；成功结果在当前世界内复用。")
        .defaultValue(false).onChanged(this::autoQueryChanged).build());
    private final Setting<Double> autoRange = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("自动查询范围（米）").description("三维直线距离，包含边界；仅限当前维度已加载的玩家。")
        .defaultValue(200).min(1).sliderRange(1, 200).visible(autoQuery::get).build());

    private final Map<String, ThreeCPlayerService.PlayerTime> results = new HashMap<>();
    private final ThreeCQueryQueue queue;
    private WTextBox nameBox;
    private WLabel statusLabel, queueLabel, totalLabel, monthLabel, verdictLabel;
    private String status = "输入名字查询，或开启范围内自动查询";
    private boolean autoPaused;
    private int scanTicks;
    private ClientWorld queryWorld;
    private ClientPlayNetworkHandler queryConnection;

    public ThreeCNewbieCheck() {
        super("3C萌新判断", "查询 3C 游玩时长，联动 Meteor 名字标签，支持失败重试和范围自动查询。");
        queue = new ThreeCQueryQueue(ThreeCPlayerService::query, new ThreeCQueryQueue.Listener() {
            @Override public void success(ThreeCPlayerService.PlayerTime data) {
                if (!sameContext()) { resetContext(); return; }
                results.put(ThreeCQueryQueue.key(data.name()), data);
                status = "查询完成：" + data.name();
                refresh();
                notice(data.name() + " 总在线：" + ThreeCPlayerService.formatTime(data.totalSeconds())
                    + "；" + verdict(data) + "；数据来源：" + ThreeCPlayerService.SOURCE,
                    data.isNewbie(threshold.get()) ? Formatting.GREEN : Formatting.RED);
            }
            @Override public void retry(String name, long attempt, String error) {
                if (!sameContext()) { resetContext(); return; }
                status = name + " 第 " + attempt + " 次查询失败：" + error + "；5 秒后重试，可点击取消";
                refresh();
            }
        }, mc::execute, 2000, 5000);
        // Manual button queries also need disconnect cleanup while the module is disabled.
        autoSubscribe = false;
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override public WWidget getWidget(GuiTheme theme) {
        var list = theme.verticalList();
        var row = list.add(theme.horizontalList()).expandX().widget();
        row.add(theme.label("玩家名字"));
        nameBox = row.add(theme.textBox(playerName.get(), "输入玩家名字")).minWidth(180).expandX().widget();
        nameBox.action = () -> {
            queue.cancelManual();
            playerName.set(nameBox.get());
            status = "玩家名字已修改，点击查询";
            refresh();
        };
        row.add(theme.button("获取最近玩家（200米）")).widget().action = () -> { if (selectNearest()) query(); };
        var buttons = list.add(theme.horizontalList()).expandX().widget();
        buttons.add(theme.button("查询游玩时长")).widget().action = this::query;
        buttons.add(theme.button("取消全部查询/重试")).widget().action = () -> {
            queue.cancelAll();
            autoPaused = true;
            status = "全部查询已取消，自动查询已暂停";
            refresh();
        };
        buttons.add(theme.button("恢复自动查询")).widget().action = () -> {
            autoPaused = false;
            scanTicks = 0;
            status = isActive() && autoQuery.get() ? "已恢复范围自动查询" : "请开启模块及范围内自动查询";
            refresh();
        };
        statusLabel = list.add(theme.label(status)).widget();
        queueLabel = list.add(theme.label("")).widget();
        totalLabel = list.add(theme.label("")).widget();
        monthLabel = list.add(theme.label("")).widget();
        verdictLabel = list.add(theme.label("")).widget();
        list.add(theme.label("失败每 5 秒重试直到成功或取消；请求间隔至少 2 秒。"));
        list.add(theme.label("标签需同时开启本模块与 Meteor Nametags；查询失败不显示判断。"));
        list.add(theme.label("数据来源：3c3u.info（非官方统计，可能存在延迟）"));
        refresh();
        return list;
    }

    @Override public void onActivate() {
        ensureContext();
        autoPaused = false;
        scanTicks = 0;
        if (autoQuery.get()) { status = "范围自动查询已开启"; refresh(); return; }
        if (playerName.get().isBlank() && !selectNearest()) return;
        query();
    }

    private void autoQueryChanged(boolean enabled) {
        if (queue != null && !enabled) queue.cancelAutomatic();
        autoPaused = false;
        scanTicks = 0;
        refresh();
    }

    @Override public void onDeactivate() {
        queue.cancelAll();
        autoPaused = true;
        status = "模块已关闭，查询已取消";
        refresh();
    }

    @Override public String getInfoString() {
        return queue.pendingCount() > 0 ? "待查询 " + queue.pendingCount() : "已查询 " + results.size();
    }

    @EventHandler private void onGameLeft(GameLeftEvent event) { resetContext(); }

    @EventHandler private void onTick(TickEvent.Pre event) {
        ensureContext();
        if (!isActive() || !autoQuery.get() || autoPaused || mc.world == null || mc.player == null) return;
        if (scanTicks-- > 0) return;
        scanTicks = 19;
        for (var player : NearbyPlayerSelector.within(mc.player.getUuid(), mc.player.getX(), mc.player.getY(),
            mc.player.getZ(), autoRange.get(), candidates())) {
            if (results.containsKey(ThreeCQueryQueue.key(player.name()))) continue;
            try { queue.enqueue(player.name(), false); }
            catch (IllegalArgumentException ignored) { /* Unsupported account names cannot form a valid API request. */ }
        }
        refresh();
    }

    private List<NearbyPlayerSelector.Candidate> candidates() {
        return mc.world.getPlayers().stream().map(player -> new NearbyPlayerSelector.Candidate(
            player.getUuid(), player.getName().getString(), player.getX(), player.getY(), player.getZ(), player.isSpectator())).toList();
    }

    private boolean selectNearest() {
        ensureContext();
        if (mc.world == null || mc.player == null) { fail("请先进入世界再获取附近玩家"); return false; }
        var nearest = NearbyPlayerSelector.nearest(mc.player.getUuid(), mc.player.getX(), mc.player.getY(), mc.player.getZ(), candidates());
        if (nearest == null) { fail("200 米内未发现已加载的其他玩家"); return false; }
        queue.cancelManual();
        playerName.set(nearest.name());
        if (nameBox != null) nameBox.set(nearest.name());
        status = "已选择 " + nearest.name();
        refresh();
        return true;
    }

    private void query() {
        ensureContext();
        if (mc.world == null || mc.getNetworkHandler() == null) { fail("请先进入服务器再查询"); return; }
        try {
            String name = ThreeCPlayerService.validateName(playerName.get());
            boolean added = queue.enqueue(name, true);
            status = added ? "已加入查询队列：" + name : name + " 已在查询或重试中";
            refresh();
        } catch (IllegalArgumentException e) { fail(e.getMessage()); }
    }

    private boolean sameContext() { return mc.world == queryWorld && mc.getNetworkHandler() == queryConnection; }
    private void ensureContext() { if (!sameContext()) resetContext(); }
    private void resetContext() {
        queue.cancelAll();
        results.clear();
        queryWorld = mc.world;
        queryConnection = mc.getNetworkHandler();
        scanTicks = 0;
        status = "世界或连接已变化，旧查询及标签已清除";
        refresh();
    }

    /** Called only by Meteor's player nametag renderer, using the real account name. */
    public ThreeCPlayerService.PlayerTime nametagResult(PlayerEntity player) {
        if (!isActive() || !nametag.get() || !sameContext()) return null;
        return results.get(ThreeCQueryQueue.key(player.getName().getString()));
    }
    public boolean isNewbie(ThreeCPlayerService.PlayerTime data) { return data.isNewbie(threshold.get()); }

    private void fail(String message) { status = message; refresh(); notice(message, Formatting.YELLOW); }

    private String verdict(ThreeCPlayerService.PlayerTime data) {
        if (data == null) return "判断：暂无数据";
        return "判断：" + (data.isNewbie(threshold.get()) ? "萌新（总时长少于 " : "糕手（总时长不少于 ")
            + threshold.get() + " 小时，自定义标准）";
    }
    private void refresh() {
        if (statusLabel == null) return;
        var data = results.get(ThreeCQueryQueue.key(playerName.get().strip()));
        statusLabel.set(status);
        queueLabel.set("待查询/重试：" + queue.pendingCount() + "；已缓存：" + results.size()
            + (autoPaused ? "；自动查询已暂停" : ""));
        totalLabel.set(data == null ? "总在线时长：—" : "总在线时长：" + ThreeCPlayerService.formatTime(data.totalSeconds()));
        monthLabel.set(data == null || data.monthSeconds() == null ? "近一个月在线：—"
            : "近一个月在线：" + ThreeCPlayerService.formatTime(data.monthSeconds()));
        verdictLabel.set(verdict(data));
    }
    private void notice(String message, Formatting color) {
        if (mc.player != null) ChatUtils.sendMsg(Text.literal("[3C萌新判断] " + message).formatted(color));
    }
}
