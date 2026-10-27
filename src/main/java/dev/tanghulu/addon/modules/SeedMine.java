package dev.tanghulu.addon.modules;

import baritone.api.BaritoneAPI;
import dev.tanghulu.addon.BaseModule;
import dev.tanghulu.addon.mixinface.MagicMix;
import dev.tanghulu.addon.util.Ore;
import dev.tanghulu.addon.util.seeds.Seed;
import dev.tanghulu.addon.util.seeds.SeedVersion;
import dev.tanghulu.addon.util.seeds.Prediction26;
import dev.tanghulu.addon.util.seeds.Seeds;
import dev.tanghulu.addon.util.seeds.OreSearchProgress;
import dev.tanghulu.addon.util.seeds.AutoMineController;
import dev.tanghulu.addon.util.seeds.BaritoneMiningDriver;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.pathing.NopPathManager;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SeedMine extends BaseModule {

    private final Map<Long, Map<Ore, Set<Vec3d>>> chunkRenderers = new ConcurrentHashMap<>();
    private Seed worldSeed = null;
    private Map<RegistryKey<Biome>, List<Ore>> oreConfig;
    public List<BlockPos> oreGoals = new ArrayList<>();
    private final AutoMineController autoMine = new AutoMineController();
    private String miningStatus = "自动挖掘未开始";
    private WLabel miningLabel;
    private volatile long predictionGeneration;
    private volatile Prediction26 prediction26;
    private final Set<Long> pending26 = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ExecutorService worker26 = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "tanghulu-ore-26.3"); thread.setDaemon(true); return thread;
    });
    private long backendSeed;
    private int backendChunks;
    private volatile boolean failed26;
    private ClientWorld predictionWorld;
    private final OreSearchProgress search = new OreSearchProgress();
    private final ArrayDeque<Chunk> searchQueue = new ArrayDeque<>();
    private long searchToken;
    private int statusTicks;
    private String status = "等待开始搜索";
    private WLabel statusLabel;

    private final SettingGroup sgSeed = settings.createGroup("种子设置");
    private final Setting<String> seedInput = sgSeed.add(new StringSetting.Builder()
        .name("种子")
        .description("输入世界种子。（默认值为3C3U种子）")
        .defaultValue("-7346913998703726680")
        .build()
    );

    private final Setting<SeedVersion> mcVersion = sgSeed.add(new EnumSetting.Builder<SeedVersion>()
        .name("MC版本")
        .description("选择区块生成版本；26.3 使用原版预测，需要 Java 25，首次下载官方运行库。")
        .defaultValue(SeedVersion.v26_3)
        .build()
    );

    private final Setting<Boolean> applySeed = sgSeed.add(new BoolSetting.Builder()
        .name("应用种子")
        .description("点击应用上面设置的种子和版本")
        .defaultValue(false)
        .onChanged(this::onApplySeedChanged)
        .build()
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();


    private final Setting<Integer> horizontalRadius = sgGeneral.add(new IntSetting.Builder()
        .name("区块范围")
        .description("显示区块的距离上限。")
        .defaultValue(3)
        .min(1)
        .sliderMax(10)
        .build()
    );


    private final Setting<Boolean> baritone = sgGeneral.add(new BoolSetting.Builder()
        .name("同步设置baritone" + (BaritoneUtils.IS_AVAILABLE ? "（OK）" : "（没找到）"))
        .description("将baritone矿物位置设置为种子算出的实际位置。")
        .defaultValue(true)
        .build()
    );

    public SeedMine() {
        super(CATEGORY_TANGHULU_BUILD, "种子矿透", "种子透视增强版。输入种子，算出矿物实际位置。注意必须使用基于彗星版的男中音。基于meteor_rejects");
        SettingGroup sgOres = settings.createGroup("矿物");
        Ore.oreSettings.forEach(sgOres::add);
    }

    public boolean baritone() {
        return isActive() && (baritone.get() || autoMine.running()) && isBaritonePresent();
    }

    public static boolean isBaritonePresent() {
        try {
            Class.forName("baritone.api.BaritoneAPI", false, SeedMine.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable t) {
            // 某些情况下 BaritoneAPI 类存在但初始化失败（例如循环依赖），也要视为不可用
            return false;
        }
    }


    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || oreConfig == null) {
            return;
        }
        if (Seeds.get().getSeed() != null) {
            int chunkX = mc.player.getChunkPos().x;
            int chunkZ = mc.player.getChunkPos().z;

            int rangeVal = horizontalRadius.get();
            for (int x = chunkX - rangeVal; x <= chunkX + rangeVal; x++) {
                for (int z = chunkZ - rangeVal; z <= chunkZ + rangeVal; z++) {
                    renderChunk(x, z, event);
                }
            }
        }

    }

    private void renderChunk(int x, int z, Render3DEvent event) {
        long chunkKey = ChunkPos.toLong(x, z);

        if (chunkRenderers.containsKey(chunkKey)) {
            Map<Ore, Set<Vec3d>> chunk = chunkRenderers.get(chunkKey);

            for (Map.Entry<Ore, Set<Vec3d>> oreRenders : chunk.entrySet()) {
                if (oreRenders.getKey().active.get()) {
                    for (Vec3d pos : oreRenders.getValue()) {
                        event.renderer.boxLines(pos.x, pos.y, pos.z, pos.x + 1, pos.y + 1, pos.z + 1, oreRenders.getKey().color, 0);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (event.newState.isOpaque()) return;

        long chunkKey = ChunkPos.toLong(event.pos);
        if (chunkRenderers.containsKey(chunkKey)) {
            Vec3d pos = Vec3d.of(event.pos);
            for (var ore : chunkRenderers.get(chunkKey).values()) {
                ore.remove(pos);
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (worldSeed != null && mc.world != predictionWorld) {
            stopAutoMine(false);
            cancelSearch(false);
            chunkRenderers.clear(); oreConfig = null; worldSeed = null;
            MagicMix.oreGoals = List.of();
            setStatus("世界或维度已变化，请点击开始搜索");
            notice(Formatting.YELLOW, status);
            return;
        }
        if (oreConfig == null) return;
        Seed current = Seeds.get().getSeed();
        if (current == null || current.seed.longValue() != worldSeed.seed.longValue()
            || !current.targetVersion.equals(worldSeed.targetVersion)) {
            stopAutoMine(false);
            cancelSearch(false);
            chunkRenderers.clear(); oreConfig = null; worldSeed = null;
            MagicMix.oreGoals = List.of();
            setStatus("种子已变化，请点击开始搜索");
            notice(Formatting.YELLOW, status);
            return;
        }
        if (search.running() && !worldSeed.is26_3()) {
            long started = System.nanoTime();
            for (int i = 0; i < 2 && !searchQueue.isEmpty() && search.running(); i++) {
                Chunk chunk = searchQueue.removeFirst();
                try { doMathOnChunk(chunk); }
                catch (Exception e) { failSearch(searchToken, "矿物搜索失败：" + e.getMessage()); }
                if (System.nanoTime() - started >= 4_000_000L) break;
            }
        }
        if (search.running() && ++statusTicks >= 20) {
            statusTicks = 0;
            refreshProgress();
        }

        updateMiningGoals();
        if (autoMine.running()) {
            try {
                if (autoMine.update(!MagicMix.oreGoals.isEmpty())) {
                    setMiningStatus(MagicMix.oreGoals.isEmpty() ? "已发现的矿物已挖完" : "自动挖掘已结束");
                    notice(Formatting.YELLOW, miningStatus + "，需要继续时点击开始自动挖。");
                }
            } catch (RuntimeException e) {
                stopAutoMine(false);
                setMiningStatus("自动挖掘失败");
                notice(Formatting.RED, "自动挖掘失败：" + e.getMessage());
            }
        }
    }

    private void updateMiningGoals() {
        List<BlockPos> targets = new ArrayList<>();
        for (var entry : chunkRenderers.values()) {
            entry.entrySet().stream().filter(e -> e.getKey().active.get())
                .flatMap(e -> e.getValue().stream()).map(BlockPos::ofFloored)
                .forEach(targets::add);
        }
        targets.sort(Comparator.comparingInt(p -> Math.abs(p.getX() - mc.player.getBlockX())
            + Math.abs(p.getY() - mc.player.getBlockY()) + Math.abs(p.getZ() - mc.player.getBlockZ())));
        MagicMix.oreGoals = targets.stream().distinct().toList();
    }

    private void setMiningStatus(String value) {
        miningStatus = value;
        if (miningLabel != null) miningLabel.set(value);
    }

    private void startAutoMine() {
        if (mc.player == null || mc.world == null) { setMiningStatus("请先进入世界"); return; }
        if (autoMine.running()) { notice(Formatting.YELLOW, "自动挖掘已在运行"); return; }
        if (!isActive() || oreConfig == null || worldSeed == null || predictionWorld != mc.world) {
            setMiningStatus("请先开始搜索，完成后再开始自动挖"); notice(Formatting.YELLOW, miningStatus); return;
        }
        if (search.running()) {
            setMiningStatus("请等待本次搜索完成后再开始自动挖"); notice(Formatting.YELLOW, miningStatus); return;
        }
        if (!isBaritonePresent()) { setMiningStatus("未找到适配 Meteor 的 Baritone"); notice(Formatting.RED, miningStatus); return; }
        updateMiningGoals();
        if (MagicMix.oreGoals.isEmpty()) {
            setMiningStatus("没有符合矿物设置的挖掘目标，请重新搜索"); notice(Formatting.YELLOW, miningStatus); return;
        }
        List<String> ores = new ArrayList<>();
        Set<String> deepOres = Set.of("coal_ore", "iron_ore", "gold_ore", "redstone_ore", "diamond_ore", "lapis_ore", "copper_ore", "emerald_ore");
        for (Setting<Boolean> setting : Ore.oreSettings) if (setting.get()) {
            String name = setting.description;
            ores.add(name);
            if (deepOres.contains(name)) ores.add("deepslate_" + name);
            if (name.equals("gold_ore")) ores.add("nether_gold_ore");
        }
        if (ores.isEmpty()) { setMiningStatus("请先勾选要挖掘的矿物"); notice(Formatting.YELLOW, miningStatus); return; }
        try {
            autoMine.start(new BaritoneMiningDriver(BaritoneAPI.getProvider().getPrimaryBaritone()), ores.toArray(String[]::new));
            setMiningStatus("自动挖掘中（" + MagicMix.oreGoals.size() + " 个预测目标）");
            notice(Formatting.GREEN, "开始自动挖：已将预测矿物位置交给 Baritone。");
        } catch (RuntimeException e) {
            setMiningStatus("自动挖掘启动失败");
            notice(Formatting.RED, miningStatus + "：" + e.getMessage());
        }
    }

    private void stopAutoMine(boolean announce) {
        boolean running = autoMine.running();
        try { autoMine.stop(); }
        catch (RuntimeException e) { notice(Formatting.RED, "停止 Baritone 挖掘时出错：" + e.getMessage()); }
        setMiningStatus("自动挖掘已停止");
        if (announce) notice(Formatting.YELLOW, running ? "已停止自动挖和对应寻路，搜索结果保留。" : "当前没有自动挖掘任务。");
    }

    @Override public WWidget getWidget(GuiTheme theme) {
        var list = theme.verticalList();
        var buttons = list.add(theme.horizontalList()).expandX().widget();
        buttons.add(theme.button("开始搜索")).expandX().widget().action = this::startSearch;
        buttons.add(theme.button("停止搜索")).expandX().widget().action = () -> cancelSearch(true);
        var miningButtons = list.add(theme.horizontalList()).expandX().widget();
        miningButtons.add(theme.button("开始自动挖")).expandX().widget().action = this::startAutoMine;
        miningButtons.add(theme.button("停止自动挖")).expandX().widget().action = () -> stopAutoMine(true);
        statusLabel = list.add(theme.label(status)).widget();
        miningLabel = list.add(theme.label(miningStatus)).widget();
        list.add(theme.label("按当前种子、版本和区块范围搜索；按钮会自动开启本模块。"));
        list.add(theme.label("搜索点击时已加载的区块；移动到新区块后可再次点击开始搜索。"));
        list.add(theme.label("搜索完成后点击开始自动挖；停止自动挖不会清除搜索结果。"));
        return list;
    }

    @Override public String getInfoString() { return autoMine.running() ? status + " | 自动挖掘中" : status; }

    private void setStatus(String value) {
        status = value;
        if (statusLabel != null) statusLabel.set(value);
    }

    private void notice(Formatting color, String message) {
        if (mc.player != null && mc.world != null) {
            dev.tanghulu.addon.util.ChatUtils.sendMsg(Text.literal("[种子矿透] " + message).formatted(color));
        }
    }

    private void refreshProgress() {
        setStatus(search.status());
        if (mc.player != null && mc.inGameHud != null) mc.inGameHud.setOverlayMessage(Text.literal(status), false);
    }

    private void recordChunk(long token, long key, Map<Ore, Set<Vec3d>> ores) {
        int matches = ores.entrySet().stream().filter(e -> e.getKey().active.get())
            .mapToInt(e -> e.getValue().size()).sum();
        if (!search.complete(token, key, matches)) return;
        refreshProgress();
        if (!search.running()) {
            stopPrediction26();
            notice(Formatting.GREEN, status + "。移动到新区块后可再次搜索。");
        }
    }

    private void failSearch(long token, String reason) {
        if (!search.fail(token)) return;
        stopPrediction26();
        searchQueue.clear();
        refreshProgress();
        notice(Formatting.RED, reason + "。本次搜索已结束，可点击开始搜索重试。");
    }

    private void cancelSearch(boolean announce) {
        boolean running = search.running();
        search.cancel();
        searchQueue.clear();
        stopPrediction26();
        if (running) {
            refreshProgress();
            if (announce) notice(Formatting.YELLOW, status);
        } else if (announce) notice(Formatting.YELLOW, "当前没有正在进行的搜索。");
    }

    private void onApplySeedChanged(boolean value) {
        if (!value) return;
        try {
            if (mc.player == null || mc.world == null) { setStatus("请先进入世界"); return; }
            if (seedInput.get().isBlank()) { notice(Formatting.RED, "请先输入种子"); return; }
            stopAutoMine(false);
            cancelSearch(false);
            Seeds.get().setSeed(seedInput.get().strip(), mcVersion.get());
            chunkRenderers.clear(); oreConfig = null; worldSeed = null;
            MagicMix.oreGoals = List.of();
            setStatus("种子已应用，请点击开始搜索");
            notice(Formatting.AQUA, status);
        } finally { applySeed.set(false); }
    }

    @Override public void onActivate() {
        super.onActivate();
        setStatus("等待开始搜索");
    }

    @Override public void onDeactivate() {
        boolean running = search.running();
        stopAutoMine(false);
        cancelSearch(false);
        chunkRenderers.clear(); oreConfig = null; worldSeed = null;
        MagicMix.oreGoals = List.of();
        setStatus("模块已关闭，请点击开始搜索");
        if (running) notice(Formatting.YELLOW, "模块已关闭，本次搜索已取消。");
    }

    @EventHandler private void onGameLeft(meteordevelopment.meteorclient.events.game.GameLeftEvent event) {
        stopAutoMine(false);
        cancelSearch(false);
        chunkRenderers.clear(); oreConfig = null; worldSeed = null;
        MagicMix.oreGoals = List.of();
        setStatus("已离开世界，搜索停止");
    }

    @EventHandler private void onGameJoined(meteordevelopment.meteorclient.events.game.GameJoinedEvent event) {
        setStatus("等待开始搜索");
    }

    private void startSearch() {
        if (mc.player == null || mc.world == null) { setStatus("请先进入世界"); return; }
        if (PathManagers.get() instanceof NopPathManager) {
            setStatus("请先安装适配 Meteor 的 Baritone"); notice(Formatting.RED, status); return;
        }
        if (Ore.oreSettings.stream().noneMatch(Setting::get)) {
            setStatus("请先勾选要搜索的矿物"); notice(Formatting.RED, status); return;
        }
        if (!seedInput.get().isBlank()) Seeds.get().setSeed(seedInput.get().strip(), mcVersion.get());
        Seed selected = Seeds.get().getSeed();
        if (selected == null) { setStatus("请先填写世界种子"); notice(Formatting.RED, status); return; }
        boolean restarting = search.running();
        stopAutoMine(false);
        cancelSearch(false);
        if (!isActive()) toggle();
        worldSeed = selected;
        predictionWorld = mc.world;
        failed26 = false;
        statusTicks = 0;
        chunkRenderers.clear(); oreConfig = null; MagicMix.oreGoals = List.of();
        var center = mc.player.getChunkPos();
        int radius = horizontalRadius.get();
        Map<Long, Chunk> loaded = new LinkedHashMap<>();
        for (Chunk chunk : Utils.chunks(false)) {
            var pos = chunk.getPos();
            if (Math.abs(pos.x - center.x) <= radius && Math.abs(pos.z - center.z) <= radius) loaded.put(pos.toLong(), chunk);
        }
        var chunks = new ArrayList<>(loaded.values());
        chunks.sort(Comparator.comparingInt(c -> Math.abs(c.getPos().x - center.x) + Math.abs(c.getPos().z - center.z)));
        searchToken = search.begin(loaded.keySet());
        refreshProgress();
        notice(Formatting.AQUA, (restarting ? "重新开始搜索" : "开始搜索") + "：生成版本 " + worldSeed.targetVersion
            + "，共 " + search.total() + " 个已加载区块。" + (worldSeed.is26_3() ? "正在准备预测，首次使用可能需要下载运行库。" : ""));
        if (!search.running()) { notice(Formatting.YELLOW, "搜索结束：当前范围内没有已加载区块，请稍后重试。"); return; }
        try {
            oreConfig = Ore.getRegistry(PlayerUtils.getDimension());
            if (worldSeed.is26_3()) for (Chunk chunk : chunks) predictChunk26(chunk.getPos());
            else searchQueue.addAll(chunks);
        } catch (Exception e) { failSearch(searchToken, "无法开始矿物搜索：" + e.getMessage()); }
    }

    private void doMathOnChunk(Chunk chunk) {

        if (oreConfig == null || worldSeed == null) return;

        var chunkPos = chunk.getPos();
        long chunkKey = chunkPos.toLong();

        ClientWorld world = mc.world;

        if (chunkRenderers.containsKey(chunkKey) || world == null) {
            return;
        }

        Set<RegistryKey<Biome>> biomes = new HashSet<>();
        ChunkPos.stream(chunkPos, 1).forEach(chunkPosx -> {
            Chunk chunkxx = world.getChunk(chunkPosx.x, chunkPosx.z, ChunkStatus.BIOMES, false);
            if (chunkxx == null) return;

            for (ChunkSection chunkSection : chunkxx.getSectionArray()) {
                chunkSection.getBiomeContainer().forEachValue(entry -> biomes.add(entry.getKey().get()));
            }
        });
        Set<Ore> oreSet = biomes.stream().flatMap(b -> getDefaultOres(b).stream()).collect(Collectors.toSet());

        int chunkX = chunkPos.x << 4;
        int chunkZ = chunkPos.z << 4;
        ChunkRandom random = new ChunkRandom(ChunkRandom.RandomProvider.XOROSHIRO.create(0));

        long populationSeed = random.setPopulationSeed(worldSeed.seed, chunkX, chunkZ);
        HashMap<Ore, Set<Vec3d>> h = new HashMap<>();

        for (Ore ore : oreSet) {

            HashSet<Vec3d> ores = new HashSet<>();

            random.setDecoratorSeed(populationSeed, ore.index, ore.step);

            int repeat = ore.count.get(random);

            for (int i = 0; i < repeat; i++) {

                if (ore.rarity != 1F && random.nextFloat() >= 1 / ore.rarity) {
                    continue;
                }

                int x = random.nextInt(16) + chunkX;
                int z = random.nextInt(16) + chunkZ;
                int y = ore.heightProvider.get(random, ore.heightContext);
                BlockPos origin = new BlockPos(x, y, z);

                RegistryKey<Biome> biome = chunk.getBiomeForNoiseGen(x, y, z).getKey().get();

                if (!getDefaultOres(biome).contains(ore)) {
                    continue;
                }

                if (ore.scattered) {
                    ores.addAll(generateHidden(world, random, origin, ore.size));
                } else {
                    ores.addAll(generateNormal(world, random, origin, ore.size, ore.discardOnAirChance));
                }
            }
            if (!ores.isEmpty()) {
                h.put(ore, ores);
            }
        }
        chunkRenderers.put(chunkKey, h);
        recordChunk(searchToken, chunkKey, h);
    }

    private void stopPrediction26() {
        predictionGeneration++;
        pending26.clear();
        Prediction26 backend = prediction26;
        if (backend != null) backend.cancel();
        worker26.execute(() -> {
            if (backend != null) backend.close();
            if (prediction26 == backend) prediction26 = null;
        });
    }

    private void predictChunk26(ChunkPos pos) {
        if (mc.player == null || mc.world == null || failed26 || !search.running()) return;
        long key = pos.toLong();
        if (!pending26.add(key)) return;
        long generation = predictionGeneration;
        long token = searchToken;
        long seed = worldSeed.seed;
        var worldKey = mc.world.getRegistryKey();
        ClientWorld clientWorld = mc.world;
        String dimension = worldKey.getValue().getPath();
        Map<String, Ore> types = new HashMap<>();
        oreConfig.values().forEach(ores -> ores.forEach(ore -> types.putIfAbsent(ore.active.description, ore)));
        worker26.execute(() -> {
            if (generation != predictionGeneration || failed26) return;
            try {
                if (prediction26 == null || backendSeed != seed || backendChunks >= 32) {
                    if (prediction26 != null) prediction26.close();
                    prediction26 = Prediction26.open(seed); backendSeed = seed; backendChunks = 0;
                }
                if (generation != predictionGeneration) { prediction26.close(); prediction26 = null; return; }
                var response = prediction26.request(Map.of("op", "ores", "dimension", dimension, "x", pos.x, "z", pos.z));
                backendChunks++;
                Map<Ore, Set<Vec3d>> positions = new HashMap<>();
                for (var element : response.getAsJsonArray("ores")) {
                    var row = element.getAsJsonArray();
                    Ore ore = types.get(row.get(0).getAsString());
                    if (ore != null) positions.computeIfAbsent(ore, ignored -> new HashSet<>()).add(
                        new Vec3d(row.get(1).getAsInt(), row.get(2).getAsInt(), row.get(3).getAsInt()));
                }
                mc.execute(() -> {
                    if (generation != predictionGeneration || !isActive() || mc.world != clientWorld
                        || mc.world.getRegistryKey() != worldKey) return;
                    positions.values().forEach(ores -> ores.removeIf(p -> !mc.world.getBlockState(BlockPos.ofFloored(p)).isOpaque()));
                    chunkRenderers.put(key, positions); pending26.remove(key);
                    recordChunk(token, key, positions);
                });
            } catch (Exception e) {
                if (prediction26 != null) { prediction26.close(); prediction26 = null; }
                if (generation == predictionGeneration) failed26 = true;
                mc.execute(() -> {
                    if (generation == predictionGeneration) failSearch(token, "26.3 矿物预测失败：" + e.getMessage());
                });
            }
        });
    }

    private List<Ore> getDefaultOres(RegistryKey<Biome> biomeRegistryKey) {
        if (oreConfig.containsKey(biomeRegistryKey)) {
            return oreConfig.get(biomeRegistryKey);
        } else {
            return this.oreConfig.values().stream().findAny().get();
        }
    }

    // ====================================
    // Mojang code
    // ====================================

    private ArrayList<Vec3d> generateNormal(ClientWorld world, ChunkRandom random, BlockPos blockPos, int veinSize, float discardOnAir) {
        float f = random.nextFloat() * 3.1415927F;
        float g = (float) veinSize / 8.0F;
        int i = MathHelper.ceil(((float) veinSize / 16.0F * 2.0F + 1.0F) / 2.0F);
        double d = (double) blockPos.getX() + Math.sin(f) * (double) g;
        double e = (double) blockPos.getX() - Math.sin(f) * (double) g;
        double h = (double) blockPos.getZ() + Math.cos(f) * (double) g;
        double j = (double) blockPos.getZ() - Math.cos(f) * (double) g;
        double l = (blockPos.getY() + random.nextInt(3) - 2);
        double m = (blockPos.getY() + random.nextInt(3) - 2);
        int n = blockPos.getX() - MathHelper.ceil(g) - i;
        int o = blockPos.getY() - 2 - i;
        int p = blockPos.getZ() - MathHelper.ceil(g) - i;
        int q = 2 * (MathHelper.ceil(g) + i);
        int r = 2 * (2 + i);

        for (int s = n; s <= n + q; ++s) {
            for (int t = p; t <= p + q; ++t) {
                if (o <= world.getTopY(Heightmap.Type.MOTION_BLOCKING, s, t)) {
                    return this.generateVeinPart(world, random, veinSize, d, e, h, j, l, m, n, o, p, q, r, discardOnAir);
                }
            }
        }

        return new ArrayList<>();
    }

    private ArrayList<Vec3d> generateVeinPart(ClientWorld world, ChunkRandom random, int veinSize, double startX, double endX, double startZ, double endZ, double startY, double endY, int x, int y, int z, int size, int i, float discardOnAir) {

        BitSet bitSet = new BitSet(size * i * size);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        double[] ds = new double[veinSize * 4];

        ArrayList<Vec3d> poses = new ArrayList<>();

        int n;
        double p;
        double q;
        double r;
        double s;
        for (n = 0; n < veinSize; ++n) {
            float f = (float) n / (float) veinSize;
            p = MathHelper.lerp(f, startX, endX);
            q = MathHelper.lerp(f, startY, endY);
            r = MathHelper.lerp(f, startZ, endZ);
            s = random.nextDouble() * (double) veinSize / 16.0D;
            double m = ((double) (MathHelper.sin(3.1415927F * f) + 1.0F) * s + 1.0D) / 2.0D;
            ds[n * 4] = p;
            ds[n * 4 + 1] = q;
            ds[n * 4 + 2] = r;
            ds[n * 4 + 3] = m;
        }

        for (n = 0; n < veinSize - 1; ++n) {
            if (!(ds[n * 4 + 3] <= 0.0D)) {
                for (int o = n + 1; o < veinSize; ++o) {
                    if (!(ds[o * 4 + 3] <= 0.0D)) {
                        p = ds[n * 4] - ds[o * 4];
                        q = ds[n * 4 + 1] - ds[o * 4 + 1];
                        r = ds[n * 4 + 2] - ds[o * 4 + 2];
                        s = ds[n * 4 + 3] - ds[o * 4 + 3];
                        if (s * s > p * p + q * q + r * r) {
                            if (s > 0.0D) {
                                ds[o * 4 + 3] = -1.0D;
                            } else {
                                ds[n * 4 + 3] = -1.0D;
                            }
                        }
                    }
                }
            }
        }

        for (n = 0; n < veinSize; ++n) {
            double u = ds[n * 4 + 3];
            if (!(u < 0.0D)) {
                double v = ds[n * 4];
                double w = ds[n * 4 + 1];
                double aa = ds[n * 4 + 2];
                int ab = Math.max(MathHelper.floor(v - u), x);
                int ac = Math.max(MathHelper.floor(w - u), y);
                int ad = Math.max(MathHelper.floor(aa - u), z);
                int ae = Math.max(MathHelper.floor(v + u), ab);
                int af = Math.max(MathHelper.floor(w + u), ac);
                int ag = Math.max(MathHelper.floor(aa + u), ad);

                for (int ah = ab; ah <= ae; ++ah) {
                    double ai = ((double) ah + 0.5D - v) / u;
                    if (ai * ai < 1.0D) {
                        for (int aj = ac; aj <= af; ++aj) {
                            double ak = ((double) aj + 0.5D - w) / u;
                            if (ai * ai + ak * ak < 1.0D) {
                                for (int al = ad; al <= ag; ++al) {
                                    double am = ((double) al + 0.5D - aa) / u;
                                    if (ai * ai + ak * ak + am * am < 1.0D) {
                                        int an = ah - x + (aj - y) * size + (al - z) * size * i;
                                        if (!bitSet.get(an)) {
                                            bitSet.set(an);
                                            mutable.set(ah, aj, al);
                                            if (aj >= -64 && aj < 320 && (world.getBlockState(mutable).isOpaque())) {
                                                if (shouldPlace(world, mutable, discardOnAir, random)) {
                                                    poses.add(new Vec3d(ah, aj, al));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return poses;
    }

    private boolean shouldPlace(ClientWorld world, BlockPos orePos, float discardOnAir, ChunkRandom random) {
        if (discardOnAir == 0F || (discardOnAir != 1F && random.nextFloat() >= discardOnAir)) {
            return true;
        }

        for (Direction direction : Direction.values()) {
            if (!world.getBlockState(orePos.add(direction.getVector())).isOpaque() && discardOnAir != 1F) {
                return false;
            }
        }
        return true;
    }

    private ArrayList<Vec3d> generateHidden(ClientWorld world, ChunkRandom random, BlockPos blockPos, int size) {

        ArrayList<Vec3d> poses = new ArrayList<>();

        int i = random.nextInt(size + 1);

        for (int j = 0; j < i; ++j) {
            size = Math.min(j, 7);
            int x = this.randomCoord(random, size) + blockPos.getX();
            int y = this.randomCoord(random, size) + blockPos.getY();
            int z = this.randomCoord(random, size) + blockPos.getZ();
            if (world.getBlockState(new BlockPos(x, y, z)).isOpaque()) {
                if (shouldPlace(world, new BlockPos(x, y, z), 1F, random)) {
                    poses.add(new Vec3d(x, y, z));
                }
            }
        }

        return poses;
    }

    private int randomCoord(ChunkRandom random, int size) {
        return Math.round((random.nextFloat() - random.nextFloat()) * (float) size);
    }
}
