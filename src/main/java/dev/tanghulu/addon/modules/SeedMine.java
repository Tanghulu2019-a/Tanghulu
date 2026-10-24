package dev.tanghulu.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.utils.BetterBlockPos;
import dev.tanghulu.addon.BaseModule;
import dev.tanghulu.addon.mixinface.MagicMix;
import dev.tanghulu.addon.util.Ore;
import dev.tanghulu.addon.util.seeds.Seed;
import dev.tanghulu.addon.util.seeds.SeedVersion;
import dev.tanghulu.addon.util.seeds.Prediction26;
import dev.tanghulu.addon.util.seeds.Seeds;
import com.seedfinding.mccore.version.MCVersion;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
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
    private int tickCounter = 0;
    private volatile long predictionGeneration;
    private volatile Prediction26 prediction26;
    private final Set<Long> pending26 = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ExecutorService worker26 = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "tanghulu-ore-26.3"); thread.setDaemon(true); return thread;
    });
    private long backendSeed;
    private int backendChunks;
    private volatile boolean failed26;
    private RegistryKey<net.minecraft.world.World> predictionDimension;
    private ClientWorld predictionWorld;
    private volatile int centerX26, centerZ26, radius26;
    private int scanTicks26;

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
        .defaultValue(5)
        .min(1)
        .sliderMax(10)
        .build()
    );


    private final Setting<Boolean> baritone = sgGeneral.add(new BoolSetting.Builder()
        .name("同步设置baritone" + (BaritoneUtils.IS_AVAILABLE ? "（OK）" : "（没找到）"))
        .description("将baritone矿物位置设置为种子算出的实际位置。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> directMine = sgGeneral.add(new BoolSetting.Builder()
        .name("直接开挖")
        .description("不建议使用，除非实在不会命令")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> low = sgGeneral.add(new BoolSetting.Builder()
        .name("仅挖残骸密集区")
        .description("远古残骸在 Y=8~24 层最集中，挖掘效率最高，我们就挖这些。")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> diamond = sgGeneral.add(new BoolSetting.Builder()
        .name("仅挖钻石密集区")
        .description("钻石残骸在 Y=-58~-10 层最集中，挖掘效率最高，我们就挖这些。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> updateInterval = sgGeneral.add(new IntSetting.Builder()
        .name("更新间隔")
        .description("设置目标的间隔时间（秒）")
        .defaultValue(1)
        .min(1)
        .max(10)
        .sliderMax(10)
        .build()
    );


    public SeedMine() {
        super(CATEGORY_TANGHULU_BUILD, "种子矿透", "种子透视增强版。输入种子，算出矿物实际位置。注意必须使用基于彗星版的男中音。基于meteor_rejects");
        SettingGroup sgOres = settings.createGroup("矿物");
        Ore.oreSettings.forEach(sgOres::add);
    }

    public boolean baritone() {
        return isActive() && baritone.get() && isBaritonePresent();
    }

    public static boolean isBaritonePresent() {
        try {
            Class.forName("baritone.api.BaritoneAPI", false, ClassLoader.getSystemClassLoader());
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
            for (int range = 0; range <= rangeVal; range++) {
                for (int x = -range + chunkX; x <= range + chunkX; x++) {
                    renderChunk(x, chunkZ + range - rangeVal, event);
                }
                for (int x = (-range) + 1 + chunkX; x < range + chunkX; x++) {
                    renderChunk(x, chunkZ - range + rangeVal + 1, event);
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
        if (mc.player == null || mc.world == null || oreConfig == null) return;
        Seed currentSeed = Seeds.get().getSeed();
        if (currentSeed != null && worldSeed != null && (currentSeed.is26_3() || worldSeed.is26_3())) {
            if (currentSeed.seed.longValue() != worldSeed.seed.longValue() || !currentSeed.targetVersion.equals(worldSeed.targetVersion)
                || mc.world != predictionWorld || mc.world.getRegistryKey() != predictionDimension) { reload(); return; }
        }
        if (worldSeed != null && worldSeed.is26_3()) {
            centerX26 = mc.player.getChunkPos().x; centerZ26 = mc.player.getChunkPos().z; radius26 = horizontalRadius.get();
            if (!failed26 && ++scanTicks26 >= 20) { scanTicks26 = 0; loadVisibleChunks(); }
            int radius = horizontalRadius.get() + 1;
            var center = mc.player.getChunkPos();
            chunkRenderers.keySet().removeIf(key -> Math.abs(ChunkPos.getPackedX(key) - center.x) > radius
                || Math.abs(ChunkPos.getPackedZ(key) - center.z) > radius);
        }

        // 直接开挖功能
        if (directMine.get() && BaritoneUtils.IS_AVAILABLE) {
            tickCounter++;
            int intervalTicks = updateInterval.get() * 20; // 转换为tick数

            if (tickCounter >= intervalTicks) {
                tickCounter = 0;
                setNearestMiningTarget();
            }
        }

        List<BlockPos> tempGoals = new ArrayList<>();
        int rangeVal = 4;
        var chunkPos = mc.player.getChunkPos();

        for (int range = 0; range <= rangeVal; ++range) {
            for (int x = -range + chunkPos.x; x <= range + chunkPos.x; ++x) {
                tempGoals.addAll(addToBaritone(x, chunkPos.z + range - rangeVal));
            }
            for (int x = -range + 1 + chunkPos.x; x < range + chunkPos.x; ++x) {
                tempGoals.addAll(addToBaritone(x, chunkPos.z - range + rangeVal + 1));
            }
        }

        if (low.get()) {
            tempGoals.removeIf(p -> p.getY() < 7 || p.getY() > 30);
        }

        if (diamond.get()) {
            tempGoals.removeIf(p -> p.getY() < -58 || p.getY() > -10);
        }

        // 曼哈顿距离排序
        tempGoals.sort(Comparator.comparingInt(p ->
            Math.abs(p.getX() - mc.player.getBlockX()) + Math.abs(p.getZ() - mc.player.getBlockZ())
        ));

        MagicMix.oreGoals.addAll(tempGoals);
    }

    private ArrayList<BlockPos> addToBaritone(int chunkX, int chunkZ) {
        ArrayList<BlockPos> baritoneGoals = new ArrayList<>();
        long chunkKey = ChunkPos.toLong(chunkX, chunkZ);
        if (this.chunkRenderers.containsKey(chunkKey)) {
            this.chunkRenderers.get(chunkKey).entrySet().stream()
                .filter(entry -> entry.getKey().active.get())
                .flatMap(entry -> entry.getValue().stream())
                .map(BlockPos::ofFloored)
                .forEach(baritoneGoals::add);
        }
        return baritoneGoals;
    }

    private void setNearestMiningTarget() {
        if (mc.player == null || !BaritoneUtils.IS_AVAILABLE) return;

        // 收集所有可用的矿石目标
        List<BlockPos> allTargets = new ArrayList<>();
        int rangeVal = 8; // 扩大搜索范围
        var chunkPos = mc.player.getChunkPos();

        for (int range = 0; range <= rangeVal; ++range) {
            for (int x = -range + chunkPos.x; x <= range + chunkPos.x; ++x) {
                allTargets.addAll(addToBaritone(x, chunkPos.z + range - rangeVal));
            }
            for (int x = -range + 1 + chunkPos.x; x < range + chunkPos.x; ++x) {
                allTargets.addAll(addToBaritone(x, chunkPos.z - range + rangeVal + 1));
            }
        }

        if (allTargets.isEmpty()) return;

        // 按曼哈顿距离排序
        allTargets.sort(Comparator.comparingInt(p ->
            Math.abs(p.getX() - mc.player.getBlockX()) +
                Math.abs(p.getY() - mc.player.getBlockY()) +
                Math.abs(p.getZ() - mc.player.getBlockZ())
        ));

        // 获取最近的目标
        BlockPos nearestTarget = allTargets.get(0);

        BetterBlockPos betterBlockPos = BetterBlockPos.from(nearestTarget);

        List<String> ores = Ore.oreSettings.stream()
            .filter(setting -> setting.get())
            .map(setting -> setting.description)
            .map(setting -> setting.toLowerCase())
            .toList();

        // 设置Baritone挖掘目标
        try {
            if (!BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive()) {
                info("设置挖掘目标: " + nearestTarget.getX() + ", " + nearestTarget.getY() + ", " + nearestTarget.getZ());

                BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mineByName(ores.toArray(new String[]{}));
            }
        } catch (Exception e) {
            info("设置挖掘目标失败" + e);
        }

//        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(betterBlockPos);

    }


    private void onApplySeedChanged(boolean value) {
        if (value) {
            String seed = seedInput.get();
            if (!seed.isEmpty()) {
                Seeds.get().setSeed(seed, mcVersion.get());
                info("已设置种子: " + seed + " 版本: " + mcVersion.get().toString());
                reload();
            } else {
                error("请先输入种子");
            }
            applySeed.set(false);
        }
    }

    @Override
    public void onActivate() {
        super.onActivate();
        if (Seeds.get().getSeed() == null) {
            error("未找到种子。请在种子设置中输入种子并点击应用");
            this.toggle();
            return;
        }

        if (PathManagers.get() instanceof NopPathManager) {
            info("需要 Baritone");
            toggle();
            return;
        }

        info("注意种子是否正确。当前种子: " + Seeds.get().getSeed().seed + " 版本: " + mcVersion.get().toString());

        reload();
    }

    @Override
    public void onDeactivate() {
        stopPrediction26();
        this.chunkRenderers.clear();
        this.oreConfig = null;
    }

    @EventHandler
    private void onGameLeft(meteordevelopment.meteorclient.events.game.GameLeftEvent event) {
        if (worldSeed != null && worldSeed.is26_3()) {
            stopPrediction26(); chunkRenderers.clear(); oreConfig = null; worldSeed = null;
            MagicMix.oreGoals.clear();
        }
    }

    @EventHandler
    private void onGameJoined(meteordevelopment.meteorclient.events.game.GameJoinedEvent event) {
        if (Seeds.get().getSeed() != null && Seeds.get().getSeed().is26_3()) reload();
    }


//    @EventHandler
//    private void onPlayerRespawn(PlayerRespawnEvent event) {
//        reload();
//    }

    private void loadVisibleChunks() {
        if (mc.player == null) {
            return;
        }

        for (Chunk chunk : Utils.chunks(false)) {
            doMathOnChunk(chunk);
        }
    }

    private void reload() {
        stopPrediction26();
        failed26 = false;
        Seed seed = Seeds.get().getSeed();
        if (seed == null) return;
        worldSeed = seed;
        predictionWorld = mc.world;
        predictionDimension = mc.world == null ? null : mc.world.getRegistryKey();
        if (mc.player != null) { centerX26 = mc.player.getChunkPos().x; centerZ26 = mc.player.getChunkPos().z; radius26 = horizontalRadius.get(); }
        oreConfig = Ore.getRegistry(PlayerUtils.getDimension());
        MagicMix.oreGoals.clear();
        chunkRenderers.clear();
        if (mc.world != null && worldSeed != null) {
            loadVisibleChunks();
        }
    }

    @EventHandler
    public void onChunkData(ChunkDataEvent event) {
        doMathOnChunk(event.chunk());
    }

    private void doMathOnChunk(Chunk chunk) {

        if (oreConfig == null || worldSeed == null) return;
        if (worldSeed.is26_3()) { predictChunk26(chunk.getPos()); return; }

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
        if (mc.player == null || mc.world == null || failed26) return;
        int radius = horizontalRadius.get();
        var center = mc.player.getChunkPos();
        if (Math.abs(pos.x - center.x) > radius || Math.abs(pos.z - center.z) > radius) return;
        long key = pos.toLong();
        if (chunkRenderers.containsKey(key) || !pending26.add(key)) return;
        long generation = predictionGeneration;
        long seed = worldSeed.seed;
        var worldKey = mc.world.getRegistryKey();
        ClientWorld clientWorld = mc.world;
        String dimension = worldKey.getValue().getPath();
        Map<String, Ore> types = new HashMap<>();
        oreConfig.values().forEach(ores -> ores.forEach(ore -> types.putIfAbsent(ore.active.description, ore)));
        worker26.execute(() -> {
            if (generation != predictionGeneration || failed26) return;
            if (Math.abs(pos.x - centerX26) > radius26 || Math.abs(pos.z - centerZ26) > radius26) { pending26.remove(key); return; }
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
                    if (generation != predictionGeneration || !isActive() || mc.world != clientWorld || mc.world.getRegistryKey() != worldKey) return;
                    positions.values().forEach(ores -> ores.removeIf(p -> !mc.world.getBlockState(BlockPos.ofFloored(p)).isOpaque()));
                    chunkRenderers.put(key, positions); pending26.remove(key);
                });
            } catch (Exception e) {
                if (prediction26 != null) { prediction26.close(); prediction26 = null; }
                if (generation == predictionGeneration) failed26 = true;
                mc.execute(() -> {
                    if (generation == predictionGeneration) {
                        failed26 = true; pending26.clear();
                        error("26.3 矿物预测失败：" + e.getMessage());
                    }
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
