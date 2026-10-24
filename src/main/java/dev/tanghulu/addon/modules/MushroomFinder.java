package dev.tanghulu.addon.modules;

import dev.tanghulu.addon.BaseModule;
import dev.tanghulu.addon.util.mushroom.*;
import dev.tanghulu.addon.util.seeds.Seeds;
import dev.tanghulu.addon.util.ChatUtils;
import dev.tanghulu.ancientapple.gui.NoteSetting;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** The client stays on 1.21.11; generation is evaluated in the selected release's isolated JVM. */
public final class MushroomFinder extends BaseModule {
    private final SettingGroup general=settings.getDefaultGroup();
    private final Setting<String> seedInput=general.add(new StringSetting.Builder().name("世界种子")
        .description("输入完整数字种子；留空时读取单人世界或已保存的服务器种子。定位需要正确种子。")
        .defaultValue("-7346913998703726680").build());
    private final Setting<MushroomVersion> version=general.add(new EnumSetting.Builder<MushroomVersion>().name("世界生成版本")
        .description("选择目标区块实际生成时的版本。首次使用该版本需下载官方运行库；26.x 需 Java 25。")
        .defaultValue(MushroomVersion.v26_1).build());
    private final Setting<Integer> radius=general.add(new IntSetting.Builder().name("搜索半径")
        .description("主世界格数；从下界搜索时会先将玩家坐标乘以 8。")
        .defaultValue(16000).min(64).max(100000).sliderRange(1000,32000).build());
    public enum SearchDetail {
        快速(64), 均衡(32), 精细(8);
        final int spacing;
        SearchDetail(int spacing) { this.spacing=spacing; }
    }
    private final Setting<SearchDetail> detail=general.add(new EnumSetting.Builder<SearchDetail>().name("搜索精细度")
        .description("快速：每隔 64 格检查；均衡：32 格；精细：8 格，更容易发现小岛，但搜索更慢。三种模式都会校验找到的位置。")
        .defaultValue(SearchDetail.均衡).build());
    private final Setting<Boolean> restart=general.add(new BoolSetting.Builder().name("重新搜索")
        .description("按当前种子、版本和位置重新搜索；关闭模块可取消。")
        .defaultValue(false).onChanged(this::restartChanged).build());
    private final Setting<Boolean> stop=general.add(new BoolSetting.Builder().name("停止搜索")
        .description("立即停止当前搜索，模块保持开启；已保存的航标保留。需要继续时点击重新搜索。")
        .defaultValue(false).onChanged(this::stopChanged).build());
    private volatile long generation;
    private volatile MushroomSearch job;
    private Thread worker;
    private ClientWorld searchWorld;
    private String status="待搜索";

    public MushroomFinder() {
        super("蘑菇岛定位","按世界种子精准校验蘑菇岛位置，保存主世界及下界对应的 Xaero 航标。");
        general.add(new NoteSetting("精细度说明","通常选均衡；精细更容易发现小岛，但搜索更慢。"));
    }
    private void notice(Formatting color,String message) {
        // Keep both lifecycle messages in chat, independent of Meteor's replaceable module feedback.
        ChatUtils.sendMsg(Text.literal("[蘑菇岛定位] "+message).formatted(color));
    }
    private void restartChanged(boolean value) {
        if(value)mc.execute(() -> { restart.set(false); if(isActive())start(); });
    }
    private void stopChanged(boolean value) {
        if(value)mc.execute(() -> {
            stop.set(false);
            boolean running=worker!=null;
            cancel(); status="已停止";
            notice(Formatting.YELLOW,running?"搜索已停止，已保存的航标保留。":"当前没有正在进行的搜索。");
        });
    }
    @Override public void onActivate() { start(); }
    @Override public void onDeactivate() {
        if(worker!=null)notice(Formatting.YELLOW,"搜索已取消。");
        cancel(); status="已取消";
    }
    @Override public String getInfoString() { return status; }
    @EventHandler private void tick(TickEvent.Pre event) {
        if(worker!=null && mc.world!=searchWorld) {
            cancel(); status="世界已变化，搜索取消";
            if(mc.player!=null)notice(Formatting.YELLOW,status);
        }
    }
    private void cancel() {
        generation++;
        var active=job; if(active!=null)active.close();
        if(worker!=null)worker.interrupt();
        worker=null; job=null;
    }
    private void start() {
        cancel();
        if(mc.player==null || mc.world==null) { status="请先进入世界"; return; }
        if(!World.OVERWORLD.equals(mc.world.getRegistryKey()) && !World.NETHER.equals(mc.world.getRegistryKey())) {
            error("请在主世界或下界搜索蘑菇岛"); status="维度不支持"; return;
        }
        if(!FabricLoader.getInstance().isModLoaded("xaerominimap")) {
            error("蘑菇岛定位需要安装 Xaero 小地图"); status="未安装 Xaero"; return;
        }
        final Object context;
        try { context=MushroomWaypoints.context(); }
        catch(LinkageError|RuntimeException e) {
            error("Xaero 接口不兼容，请检查小地图版本；详细原因已写入日志。");
            org.slf4j.LoggerFactory.getLogger(MushroomFinder.class).warn("Mushroom Xaero session failed",e);
            status="Xaero 不兼容"; return;
        }
        if(context==null) { warning("Xaero 世界会话尚未就绪，请稍后点重新搜索"); status="等待 Xaero"; return; }
        final long seed;
        try {
            if(!seedInput.get().isBlank())seed=Long.parseLong(seedInput.get().strip());
            else {
                var saved=Seeds.get().getSeed();
                if(saved==null || saved.seed==null) { error("请填写世界种子；多人服务器不会自动发送世界种子"); status="缺少种子"; return; }
                seed=saved.seed;
            }
        } catch(NumberFormatException e) { error("请输入有效的 64 位数字种子"); status="种子无效"; return; }
        int scale=World.NETHER.equals(mc.world.getRegistryKey())?8:1;
        long centerX=(long)mc.player.getBlockX()*scale,centerZ=(long)mc.player.getBlockZ()*scale;
        if(Math.abs(centerX)>29999984 || Math.abs(centerZ)>29999984) {
            error("换算后的主世界坐标超出世界边界"); status="坐标越界"; return;
        }
        final int x=(int)centerX,z=(int)centerZ,r=radius.get(),spacing=detail.get().spacing;
        final MushroomVersion selected=version.get();
        final long token=generation;
        searchWorld=mc.world;
        status="准备搜索";
        notice(Formatting.AQUA,"开始搜索范围内的蘑菇岛！生成版本 "+selected+"，半径 "+r+" 格，精细度："+detail.get()+"。首次使用需准备运行库；点击停止搜索可取消。");
        final var search=new MushroomSearch();
        job=search;
        worker=Thread.ofVirtual().name("tanghulu-mushroom-search").unstarted(() -> {
            try(search) {
                if(token!=generation || Thread.currentThread().isInterrupted())return;
                var targets=search.runAll(seed,selected,x,z,r,spacing,message -> mc.execute(() -> {
                    if(valid(token))status=message;
                }));
                mc.execute(() -> {
                    if(!valid(token))return;
                    worker=null; job=null;
                    if(targets.isEmpty()) {
                        status="范围内未发现";
                        notice(Formatting.YELLOW,"搜索完毕！当前范围内未发现蘑菇岛，可扩大搜索半径或将搜索精细度设为精细。"); return;
                    }
                    try {
                        MushroomWaypoints.saveAll(context,targets,selected);
                        status="已标注 "+targets.size()+" 处蘑菇岛";
                        notice(Formatting.GREEN,"搜索完毕！共找到 "+targets.size()+" 处蘑菇岛，已分别保存主世界和下界对应航标。");
                    } catch(Exception|LinkageError e) {
                        status="航标保存失败";
                        notice(Formatting.RED,"搜索完毕！共找到 "+targets.size()+" 处蘑菇岛，但航标保存失败，请检查 Xaero 地图会话。详细原因已写入日志。");
                        org.slf4j.LoggerFactory.getLogger(MushroomFinder.class).warn("Mushroom waypoint save failed",e);
                    }
                });
            } catch(Exception e) {
                mc.execute(() -> {
                    if(!valid(token))return;
                    worker=null; job=null; status="搜索失败";
                    notice(Formatting.RED,"搜索结束，定位失败。请检查 Java 25、网络和搜索范围；详细原因已写入日志。");
                    org.slf4j.LoggerFactory.getLogger(MushroomFinder.class).warn("Mushroom search failed",e);
                });
            }
        });
        worker.start();
    }
    private boolean valid(long token) { return token==generation && isActive() && mc.world==searchWorld; }
}
