package dev.tanghulu.ancientapple;

import dev.tanghulu.ancientapple.core.*;
import dev.tanghulu.ancientapple.integration.XaeroBridge;
import dev.tanghulu.ancientapple.navigation.ManualCollectionObserver;
import dev.tanghulu.ancientapple.prediction.PredictionEngine;
import dev.tanghulu.ancientapple.prediction.SearchArea;
import dev.tanghulu.ancientapple.prediction.LootVersion;
import dev.tanghulu.ancientapple.render.WorldMarkerRenderer;
import dev.tanghulu.ancientapple.storage.WorldStore;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import dev.tanghulu.ancientapple.gui.BlacklistScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

public final class HunterModule extends Module {
    private final SettingGroup general=settings.getDefaultGroup(), display=settings.createGroup("标记");
    public final Setting<String> seed=general.add(new StringSetting.Builder().name("世界种子").description("输入完整有符号 64 位世界种子。多人游戏请选择服务器实际使用的战利品版本。").defaultValue("-7346913998703726680").onChanged(s -> contextChanged()).build());
    private final Setting<String> seedNote=general.add(new dev.tanghulu.ancientapple.gui.NoteSetting("种子备注","多人游戏请选择服务器版本。"));
    private final Setting<LootSelection> lootSelection=general.add(new EnumSetting.Builder<LootSelection>().name("定位物品").description("选择附魔金苹果、迅捷潜行3或全部；切换后立即筛选现有结果，无需重新搜索。").defaultValue(LootSelection.All).onChanged(v -> selectionChanged()).build());
    public final Setting<LootVersion> lootVersion=general.add(new EnumSetting.Builder<LootVersion>().name("服务器战利品版本").description("选择服务器实际运行的正式版本，支持 1.21.1 至 26.3；与客户端版本独立。").defaultValue(LootVersion.MC_26_1).onChanged(v -> contextChanged()).build());
    private final Setting<Integer> radius=general.add(new IntSetting.Builder().name("搜索半径").description("本轮固定搜索范围，最多 131072 格。大范围定位耗时和内存占用随面积增长，可随时取消。").defaultValue(10000).min(384).sliderRange(384,32768).max(131072).build());
    public final Setting<Boolean> blacklistEnabled=general.add(new BoolSetting.Builder().name("启用黑名单").description("隐藏已搜取箱子；关闭后允许重新定位，保留历史记录。").defaultValue(true).onChanged(v -> blacklistChanged()).visible(() -> false).build());
    private final Setting<Boolean> xaeroEnabled=display.add(new BoolSetting.Builder().name("Xaero航点").defaultValue(true).build());
    private final Setting<Boolean> throughWalls=display.add(new BoolSetting.Builder().name("穿墙图标").defaultValue(true).build());
    private final Setting<Boolean> beams=display.add(new BoolSetting.Builder().name("航标光束").description("在游戏世界中显示航标的金色竖直光束；与地图标记独立开关。").defaultValue(true).build());
    private final Setting<Integer> beamDistance=display.add(new IntSetting.Builder().name("光束最大距离").description("航标光束的渲染距离，单位：格；独立于图标最大距离。可直接输入，最多 131072 格。").defaultValue(2048).min(16).sliderRange(16,8192).max(131072).visible(beams::get).build());
    private final Setting<Boolean> completed=display.add(new BoolSetting.Builder().name("显示已处理箱子").description("显示空箱等记录；启用黑名单时隐藏已搜取箱子。").defaultValue(false).build());
    private final Setting<Integer> renderDistance=display.add(new IntSetting.Builder().name("图标最大距离").defaultValue(2048).min(16).sliderRange(16,8192).build());
    private final Map<String,ChestTarget> targets=new LinkedHashMap<>();
    private final Calibration calibration=new Calibration();
    private final PredictionEngine prediction=new PredictionEngine();
    private final XaeroBridge xaero=new XaeroBridge();
    private final ManualCollectionObserver manual=new ManualCollectionObserver(targets,this::collected,this::opened);
    private final WorldMarkerRenderer markers=new WorldMarkerRenderer();
    private WorldStore store;
    private Object loadedWorld;
    private String context="";
    private long epoch;
    private int ticks;
    private String lastFailure="";
    private String lastFailureTrace="";
    private SearchArea surveyArea;
    public HunterModule() { super(AncientAppleAddon.CATEGORY,"古城战利品定位","古城附魔金苹果与迅捷潜行 III 附魔书预测与定位，手动搜取记录黑名单。"); runInMainMenu=true; }
    @Override public void onDeactivate() { contextChanged(); }
    private void contextChanged() {
        // Setting callbacks may fire while the module is being constructed/deserialized.
        if(prediction==null)return;
        invalidateSearch();
        manual.reset();
        save();
        store=null;
        loadedWorld=null;
        context="";
        surveyArea=null;
        targets.clear();
        calibration.reset();
        xaero.release();
    }
    private void blacklistChanged() {
        if(manual==null || store==null)return;
        manual.reset(); save();
        store.setBlacklistEnabled(blacklistEnabled.get()); store.refreshBlacklistView(targets.values());
        saveAndSync();
    }
    public List<String> blacklistPositions() {
        if(store==null)return List.of();
        try { return store.blacklistPositions(); }
        catch(Exception e) { warning("读取黑名单失败：%s",e.getMessage()); return List.of(); }
    }
    public List<String> openedPositions() {
        if(store==null)return List.of();
        try { return store.openedPositions(); }
        catch(Exception e) { warning("读取开箱记录失败：%s",e.getMessage()); return List.of(); }
    }
    public String managementContext() { return ensureContext()?context:""; }
    public String managementSummary() {
        if(mc.world==null)return "";
        String server=mc.getCurrentServerEntry()==null?"单人世界":mc.getCurrentServerEntry().address;
        return server+" · 种子 "+seed.get().trim()+" · 战利品 "+lootVersion.get();
    }
    public double horizontalDistance(String position) {
        if(mc.player==null)return 0;
        String[] parts=position.split(",");
        return Math.hypot(Integer.parseInt(parts[0])-mc.player.getX(),Integer.parseInt(parts[2])-mc.player.getZ());
    }
    public boolean restoreRecords(Collection<String> positions,boolean openedOnly,String expectedContext) {
        if(!ensureContext() || !context.equals(expectedContext)) { warning("世界或种子已变化，请刷新管理窗口后重试。"); return false; }
        manual.reset(); invalidateSearch();
        try {
            store.restoreRecords(positions,targets.values(),openedOnly);
            saveAndSync();
            return true;
        } catch(Exception e) { warning("恢复记录失败：%s",e.getMessage()); return false; }
    }
    public void listBlacklist(int page) {
        if(!ensureContext()) { message("请进入世界并设置种子。"); return; }
        var list=blacklistPositions(); int pages=Math.max(1,(list.size()+19)/20);
        if(page<1 || page>pages) { message("黑名单页码范围：1～"+pages); return; }
        message("黑名单（"+(blacklistEnabled.get()?"启用":"关闭")+"）："+list.size()+" 箱，第 "+page+" / "+pages+" 页");
        list.stream().skip((page-1)*20L).limit(20).forEach(this::message);
    }
    public void clearBlacklist() {
        if(!ensureContext()) { message("请进入世界并设置种子。"); return; }
        manual.reset(); invalidateSearch();
        try {
            int count=store.clearBlacklist(targets.values());
            saveAndSync();
            message("已清除当前世界和种子的 "+count+" 条黑名单记录及所有战利品版本的开箱隐藏记录；可重新定位箱子。");
        } catch(Exception e) { warning("清除黑名单失败：%s",e.getMessage()); }
    }
    @EventHandler private void tick(TickEvent.Pre event) {
        if(mc.world==null || mc.player==null) { if(loadedWorld!=null)contextChanged(); return; }
        if(!ensureContext())return;
        manual.tick();
        if(++ticks%40==0) {
            if(xaeroEnabled.get())syncWaypoints(); else xaero.release();
        }
    }
    public void serverContainerOpened(int sync) { manual.serverContainerOpened(sync); }
    public void serverInventory(int sync,List<ItemStack> stacks) { manual.serverInventory(sync,stacks); }
    public void serverSlot(int sync,int slot,ItemStack stack) { manual.serverSlot(sync,slot,stack); }
    public void serverPlayerSlot(int slot,ItemStack stack) { manual.serverPlayerSlot(slot,stack); }
    public void manualChestInteraction(BlockPos pos) { if(ensureContext())manual.onInteractBlock(pos); }
    public void manualClickBefore(int sync,int slot,int button,SlotActionType action) { manual.onClickSlotBefore(sync,slot,button,action); }
    public void manualClickAfter(int sync,int slot,int button,SlotActionType action) { manual.onClickSlotAfter(sync,slot,button,action); }
    public void manualCursorStack(ItemStack stack) { manual.serverCursor(stack); }
    public boolean blacklistContextReady() { return ensureContext(); }
    public void openBlacklist() {
        var screen=new BlacklistScreen(GuiThemes.get(),this);
        screen.parent=null;
        // Chat closes after dispatching commands; use Meteor's deferred screen queue.
        meteordevelopment.meteorclient.utils.Utils.screenToOpen=screen;
    }
    private void openBlacklist(GuiTheme theme) { mc.setScreen(new BlacklistScreen(theme,this)); }
    private boolean ensureContext() {
        if(mc.world==null || mc.player==null)return false;
        long worldSeed;
        try { worldSeed=Long.parseLong(seed.get().trim()); } catch(NumberFormatException e) { return false; }
        String server=mc.getCurrentServerEntry()!=null?mc.getCurrentServerEntry().address:"singleplayer:"+(mc.getServer()==null?"默认":mc.getServer().getSaveProperties().getLevelName());
        // Preserve default 0.1.10 cache keys without an editable world identifier.
        String identity=server+"|"+mc.world.getRegistryKey().getValue()+"|默认|"+xaero.worldId();
        String key=identity+"|"+worldSeed+"|loot="+lootVersion.get();
        if(mc.world!=loadedWorld || !key.equals(context)) {
            contextChanged(); context=key; loadedWorld=mc.world;
            store=new WorldStore(FabricLoader.getInstance().getConfigDir().resolve("ancient-apple-hunter/worlds"),identity,worldSeed,lootVersion.get().toString());
            store.setBlacklistEnabled(blacklistEnabled.get());
            try { var s=store.read(); s.targets.forEach(t -> { ChestSurvey.prepare(t,false); targets.put(t.key(),t); }); calibration.restore(s.matches,s.failed); }
            catch(Exception e) { warning("无法读取缓存，已从空记录开始：%s",e.getMessage()); }
        }
        return true;
    }
    public void scan() {
        if(!isActive())enable();
        if(!ensureContext()) { message("请进入世界并填写完整数值种子。"); return; }
        if(mc.world.getRegistryKey()!=World.OVERWORLD) { message("只定位主世界古城。"); return; }
        manual.reset(); long request=++epoch; String requestedContext=context;
        SearchArea requestedArea=new SearchArea(mc.player.getBlockX(),mc.player.getBlockZ(),radius.get());
        prediction.search(Long.parseLong(seed.get().trim()),mc.player.getBlockX(),mc.player.getBlockZ(),radius.get(),mc.world.getRegistryManager(),lootVersion.get(),
            FabricLoader.getInstance().getConfigDir().resolve("ancient-apple-hunter/terrain/"+WorldStore.ALGORITHM+"/"+seed.get().trim()),
            result -> mc.execute(() -> acceptPrediction(request,requestedContext,requestedArea,result)),
            error -> mc.execute(() -> { if(request==epoch)recordFailure(error); }));
        message("开始定位古城战利品箱（"+lootSelection.get()+"），半径 "+radius.get()+" 格，服务器战利品模型 "+lootVersion.get()+"。");
    }
    private void acceptPrediction(long request,String requestedContext,SearchArea area,List<ChestTarget> result) {
        if(request!=epoch || !requestedContext.equals(context) || !isActive())return;
        for(ChestTarget target:result) {
            var merged=ChestSurvey.merge(target,targets.get(target.key()),false);
            if(!blacklistEnabled.get() && merged.isBlacklisted())merged.resetCollection();
            store.applyBlacklist(merged);
            targets.put(target.key(),merged);
        }
        surveyArea=area;
        saveAndSync();
        long pending=result.stream().map(t -> targets.get(t.key())).filter(lootSelection.get()::matches).filter(ChestTarget::eligible).count();
        long hidden=result.stream().map(t -> targets.get(t.key())).filter(ChestTarget::isBlacklisted).count();
        message("定位完成："+pending+" 只待查看箱子；已隐藏 "+hidden+" 只黑名单箱子。请自行前往。");
    }
    private void invalidateSearch() { epoch++; prediction.cancel(); }
    public void cancelSearch() { invalidateSearch(); message("已取消定位；正在完成的原版补算会安全收尾。"); }
    private void recordFailure(Throwable failure) {
        Throwable root=failure; Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        while(root.getCause()!=null && seen.add(root.getCause()))root=root.getCause();
        lastFailure=root.toString();
        var details=new java.io.StringWriter(); failure.printStackTrace(new java.io.PrintWriter(details));
        lastFailureTrace=details.toString();
        try {
            var directory=FabricLoader.getInstance().getConfigDir().resolve("ancient-apple-hunter/diagnostics");
            java.nio.file.Files.createDirectories(directory);
            var file=directory.resolve("failure-"+System.currentTimeMillis()+".txt");
            java.nio.file.Files.writeString(file,lastFailureTrace);
            error("预测失败：%s；完整异常已保存：%s",lastFailure,file.toAbsolutePath());
        } catch(Exception e) { error("预测失败：%s；请用 .aah diagnose 导出详情。",lastFailure); }
        if(root instanceof java.nio.channels.ClosedChannelException || root instanceof java.nio.file.ClosedFileSystemException)
            warning("游戏资源通道已关闭，请完整退出游戏后重新启动再定位。");
    }
    private void opened(List<ChestTarget> chests) {
        boolean changed=false;
        for(var chest:chests) {
            var record=targets.get(chest.key());
            if(record!=null && !record.openedWithApples) { record.openedWithApples=true; changed=true; }
        }
        if(changed)saveAndSync();
    }
    private void collected(List<ChestTarget> chests) {
        List<ChestTarget> records=new ArrayList<>();
        for(var chest:chests) {
            chest.markCollected();
            var record=targets.get(chest.key());
            if(record==null) { record=chest; targets.put(chest.key(),record); }
            record.markCollected(); records.add(record);
        }
        try { if(store!=null)store.blacklist(records); }
        catch(Exception e) { warning("黑名单保存失败，将重试：%s",e.getMessage()); }
        saveAndSync();
    }
    public void diagnose() {
        if(!ensureContext()) { message("请先进入世界并设置种子。"); return; }
        try {
            var report=new LinkedHashMap<String,Object>();
            report.put("addonVersion",FabricLoader.getInstance().getModContainer("ancient-apple-hunter").orElseThrow().getMetadata().getVersion().getFriendlyString());
            report.put("blacklistEnabled",blacklistEnabled.get()); report.put("blacklist",blacklistPositions());
            report.put("clientVersion",net.minecraft.SharedConstants.getGameVersion().name());
            report.put("server",mc.getCurrentServerEntry()==null?"singleplayer":mc.getCurrentServerEntry().address);
            report.put("serverBrand",mc.player.networkHandler.getBrand()); report.put("seed",seed.get().trim());
            report.put("lootVersion",lootVersion.get().toString());
            report.put("lastFailure",lastFailure); report.put("lastFailureTrace",lastFailureTrace);
            report.put("mode","locator"); report.put("area",surveyArea); report.put("targets",new ArrayList<>(targets.values()));
            var dir=FabricLoader.getInstance().getConfigDir().resolve("ancient-apple-hunter/diagnostics");
            java.nio.file.Files.createDirectories(dir);
            var file=dir.resolve("survey-"+System.currentTimeMillis()+".json");
            java.nio.file.Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
            message("诊断已保存："+file.toAbsolutePath());
        } catch(Exception e) { warning("诊断导出失败：%s",e.getMessage()); }
    }
    private void save() { if(store!=null)try { store.write(targets.values(),calibration); } catch(Exception e) { warning("缓存保存失败：%s",e.getMessage()); } }
    private void selectionChanged() { if(xaero!=null && isActive())syncWaypoints(); }
    private void syncWaypoints() { if(xaeroEnabled.get())xaero.sync(targets.values(),completed.get(),lootSelection.get()); }
    private void saveAndSync() { save(); syncWaypoints(); }
    private void message(String text) { info("%s",text); }
    @Override public String getInfoString() { return prediction.running?"定位 "+prediction.progress.completed()+"/"+prediction.progress.total():"仅定位"; }
    @Override public WWidget getWidget(GuiTheme theme) {
        WTable table=theme.table();
        button(table,theme,"定位箱子",this::scan); button(table,theme,"取消定位",this::cancelSearch); table.row();
        button(table,theme,"黑名单管理",() -> openBlacklist(theme)); table.row();
        table.add(theme.label("仅显示定位航标，请自行移动、开箱和拿取。"));
        return table;
    }
    private void button(WTable table,GuiTheme theme,String text,Runnable action) { WButton b=table.add(theme.button(text)).widget(); b.action=action; }
    @EventHandler private void render(Render2DEvent event) {
        if(mc.world==null || mc.player==null || mc.world.getRegistryKey()!=World.OVERWORLD)return;
        markers.renderIcons(event,targets.values(),completed.get(),throughWalls.get(),renderDistance.get(),lootSelection.get());
        var context=event.drawContext;
        if(prediction.running) { var p=prediction.progress; context.drawTextWithShadow(mc.textRenderer,"古城箱预测 "+p.completed()+"/"+p.total()+" · 已补算 "+prediction.terrainChests+" 箱",8,8,0xffffd060); }
    }
    @EventHandler private void renderBeams(Render3DEvent event) {
        if(beams.get())markers.renderBeams(event,targets.values(),completed.get(),throughWalls.get(),beamDistance.get(),lootSelection.get());
    }
}
