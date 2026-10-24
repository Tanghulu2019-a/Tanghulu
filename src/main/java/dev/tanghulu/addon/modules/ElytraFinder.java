package dev.tanghulu.addon.modules;

import dev.tanghulu.addon.BaseModule;
import dev.tanghulu.addon.util.seeds.Seed;
import dev.tanghulu.addon.util.seeds.SeedVersion;
import dev.tanghulu.addon.util.seeds.Prediction26;
import dev.tanghulu.addon.util.seeds.Seeds;
import com.seedfinding.mcbiome.layer.land.MushroomLayer;
import com.seedfinding.mcbiome.source.BiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.state.Dimension;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.structure.EndCity;
import com.seedfinding.mcfeature.structure.generator.structure.EndCityGenerator;
import com.seedfinding.mcterrain.TerrainGenerator;
import lombok.extern.slf4j.Slf4j;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static meteordevelopment.meteorclient.utils.world.Dimension.End;

@Slf4j
public class ElytraFinder extends BaseModule {

    // 设置组
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSeed = settings.createGroup("种子设置");

    // 通用设置
    private final Setting<Integer> searchRadius = sgGeneral.add(new IntSetting.Builder()
        .name("搜索半径")
        .description("以玩家为中心的搜索半径（区块）。")
        .defaultValue(50)
        .min(10)
        .max(2000)
        .sliderMin(10)
        .sliderMax(2000)
        .build()
    );

//    private final Setting<Integer> maxResults = sgGeneral.add(new IntSetting.Builder()
//        .name("最大结果数")
//        .description("显示的最大鞘翅位置数量")
//        .defaultValue(50)
//        .min(1)
//        .max(200)
//        .sliderMin(1)
//        .sliderMax(200)
//        .build()
//    );

    private final Setting<Boolean> includeDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("结果包含距离")
        .description("在聊天结果中包含距离信息")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoSearch = sgGeneral.add(new BoolSetting.Builder()
        .name("自动搜索")
        .description("启用模块时自动开始搜索")
        .defaultValue(true)
        .build()
    );

    // 种子设置
    private final Setting<String> seedInput = sgSeed.add(new StringSetting.Builder()
        .name("种子")
        .description("输入服务器种子(默认3C3U种子)")
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
        .description("应用手动输入的种子设置")
        .defaultValue(false)
        .onChanged(this::onApplySeedChanged)
        .build()
    );

    private final Setting<Boolean> startSearch = sgGeneral.add(new BoolSetting.Builder()
        .name("开始搜索")
        .description("开始搜索鞘翅位置")
        .defaultValue(false)
        .onChanged(this::onStartSearchChanged)
        .build()
    );

    // 内部变量
    private volatile boolean isSearching = false;
    private volatile Prediction26 prediction26;
    private volatile long searchGeneration;
    private CompletableFuture<Void> searchTask = null;

    public ElytraFinder() {
        super(CATEGORY_TANGHULU_BUILD, "末地鞘翅定位", "自动搜索带末地船的末地城结构（包含鞘翅）");
    }

    @Override
    public void onActivate() {
        super.onActivate();
        startElytraSearch();
        info("末地鞘翅定位模块已启用");
    }

    @Override
    public void onDeactivate() {
        searchGeneration++;
        if (prediction26 != null) prediction26.cancel();
        if (searchTask != null && !searchTask.isDone()) {
            searchTask.cancel(true);
            isSearching = false;
            info("搜索已取消");
        }
        info("末地鞘翅定位模块已禁用");
    }

    @EventHandler
    private void onGameLeft(meteordevelopment.meteorclient.events.game.GameLeftEvent event) {
        searchGeneration++;
        if (prediction26 != null) prediction26.cancel();
        if (searchTask != null) searchTask.cancel(false);
        isSearching = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // 检查搜索任务状态
        if (searchTask != null && searchTask.isDone()) {
            isSearching = false;
            searchTask = null;
            startSearch.set(false);
        }
    }

    private void onApplySeedChanged(boolean value) {
        if (value) {
            String seed = seedInput.get().trim();
            if (!seed.isEmpty()) {
                try {
                    Seeds.get().setSeed(seed, mcVersion.get());
                    info("已设置种子: " + seed + " 版本: " + mcVersion.get().toString());
                } catch (Exception e) {
                    error("设置种子失败: " + e.getMessage());
                }
            } else {
                error("请先输入种子");
            }
            applySeed.set(false);
        }
    }

    private void onStartSearchChanged(boolean value) {
        if (value && !isSearching) {
            startElytraSearch();
        }
    }

    private void startElytraSearch() {
        if (isSearching) {
            warning("搜索正在进行中...");
            return;
        }

        if (mc.player == null) {
            error("玩家不存在");
            return;
        }

        // 获取种子
        Seed worldSeed = Seeds.get().getSeed();
        if (worldSeed == null) {
            error("无法获取世界种子，请手动设置种子");
            return;
        }

        // 验证种子数据
        if (worldSeed.seed == null || worldSeed.version == null) {
            error("种子数据无效，请重新设置种子");
            return;
        }

        // 检查是否在末地
        if (PlayerUtils.getDimension() != End) {
            warning("建议在末地使用此功能以获得最准确的结果");
        }

        isSearching = true;
        long generation = ++searchGeneration;
        int radius = searchRadius.get();
        BlockPos playerPos = mc.player.getBlockPos();

        info("开始搜索鞘翅位置... 半径: " + searchRadius.get() + " 区块");

        // 异步搜索
        searchTask = CompletableFuture.runAsync(() -> {
            try {
                if (worldSeed.is26_3()) searchElytra26(worldSeed, playerPos, radius, generation);
                else searchElytraLocations(worldSeed, playerPos);
            } catch (Exception e) {
                if (worldSeed.is26_3()) {
                    mc.execute(() -> { if (generation == searchGeneration && isActive()) error("26.3 鞘翅预测失败：" + e.getMessage()); });
                    return;
                }
                error("搜索过程中发生错误: " + e.getMessage());
                log.error("搜索过程中发生错误: ", e);  // 在开发环境中启用
                isSearching = false;
            }
        });
    }

    private void searchElytra26(Seed seed, BlockPos playerPos, int radius, long generation) throws Exception {
        var backend = Prediction26.open(seed.seed);
        try (backend) {
            prediction26 = backend;
            if (generation != searchGeneration) return;
            var response = backend.request(java.util.Map.of("op", "structures", "dimension", "the_end",
                "x", playerPos.getX() >> 4, "z", playerPos.getZ() >> 4, "radius", radius,
                "selected", List.of("end_city"), "ships", true));
            List<ElytraLocation> locations = new ArrayList<>();
            for (var element : response.getAsJsonArray("structures")) {
                var row = element.getAsJsonArray();
                BPos pos = new BPos(row.get(1).getAsInt(), row.get(2).getAsInt(), row.get(3).getAsInt());
                locations.add(new ElytraLocation(pos, Math.hypot(pos.getX() - playerPos.getX(), pos.getZ() - playerPos.getZ())));
            }
            mc.execute(() -> { if (generation == searchGeneration && isActive()) displayResults(locations, playerPos); });
        } finally { if (prediction26 == backend) prediction26 = null; }
    }

    private void searchElytraLocations(Seed worldSeed, BlockPos playerPos) {
        List<ElytraLocation> elytraLocations = new ArrayList<>();

        try {
            // 验证种子数据
            if (worldSeed == null || worldSeed.seed == null || worldSeed.version == null) {
                error("种子数据无效");
                return;
            }

            long seed = worldSeed.seed;
            MCVersion version = worldSeed.version;

            info("使用种子: " + seed + ", 版本: " + version.name);

            BiomeSource biomeSource = BiomeSource.of(Dimension.END, version, seed);
            TerrainGenerator generator = TerrainGenerator.of(Dimension.END, biomeSource);
            EndCity endCity = new EndCity(version);
            EndCityGenerator endCityGenerator = new EndCityGenerator(version);
            ChunkRand rand = new ChunkRand();
            int radius = searchRadius.get();

            // 安全检查搜索半径
            if (radius > 500) {
                warning("搜索半径过大 (" + radius + ")，可能导致性能问题或错误。 ");
            }

            int playerChunkX = playerPos.getX() >> 4;
            int playerChunkZ = playerPos.getZ() >> 4;

            // 获取末地城间距，避免除零错误
            int spacing = endCity.getSpacing();
            if (spacing <= 0) {
                error("末地城间距无效: " + spacing);
                return;
            }

            // 安全计算区域范围，避免整数溢出
            int minChunkX = Math.max(playerChunkX - radius, Integer.MIN_VALUE / 2);
            int maxChunkX = Math.min(playerChunkX + radius, Integer.MAX_VALUE / 2);
            int minChunkZ = Math.max(playerChunkZ - radius, Integer.MIN_VALUE / 2);
            int maxChunkZ = Math.min(playerChunkZ + radius, Integer.MAX_VALUE / 2);

            int minRegionX = minChunkX / spacing;
            int maxRegionX = maxChunkX / spacing;
            int minRegionZ = minChunkZ / spacing;
            int maxRegionZ = maxChunkZ / spacing;

            // 验证区域范围
            if (maxRegionX - minRegionX > 1000 || maxRegionZ - minRegionZ > 1000) {
                warning("搜索范围过大，可能导致性能问题。建议减小搜索半径。");
            }

            int searchCount = 0;
            int totalRegions = (maxRegionX - minRegionX + 1) * (maxRegionZ - minRegionZ + 1);

            String searchInfo = "搜索参数: 半径=" + radius +
                ", 间距=" + spacing +
                ", 区域范围=(" + minRegionX + "," + minRegionZ + ")到(" +
                maxRegionX + "," + maxRegionZ + "), 总区域=" + totalRegions;
            info(searchInfo);
            // 搜索指定半径内的区块
            for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {

                    if (!isSearching) return; // 检查是否被取消

                    try {
                        CPos pos = endCity.getInRegion(seed, regionX, regionZ, rand);

                        // 检查是否在搜索范围内
                        double chunkDistance = Math.sqrt(Math.pow(pos.getX() - playerChunkX, 2) +
                            Math.pow(pos.getZ() - playerChunkZ, 2));
                        if (chunkDistance > radius) continue;

                        if (endCity.canSpawn(pos, biomeSource)) {
                            if (endCity.canGenerate(pos, generator)) {
                                endCityGenerator.generate(generator, pos, rand);

                                // 检查是否有末地船（包含鞘翅）
                                if (endCityGenerator.hasShip()) {
                                    BPos blockPos = new BPos(pos.getX() * 16, 0, pos.getZ() * 16);
                                    double distance = Math.sqrt(Math.pow(blockPos.getX() - playerPos.getX(), 2) +
                                        Math.pow(blockPos.getZ() - playerPos.getZ(), 2));

                                    elytraLocations.add(new ElytraLocation(blockPos, distance));

//                                    if (elytraLocations.size() >= maxResults.get()) {
//                                        break;
//                                    }
                                }

                                endCityGenerator.reset();
                            }
                        }
                    } catch (Exception e) {
                        warning(String.format("处理区域 (%d,%d) 时出错: %s", regionX, regionZ, e.getMessage()));
                        continue;
                    }

                    searchCount++;

                }

//                if (elytraLocations.size() >= maxResults.get()) {
//                    break;
//                }
            }

        } catch (Exception e) {
            error("搜索过程中发生错误, 建议调小搜索半径: " + e.getMessage());

//            info(ExceptionToStringUtils.getStackTraceAsString(e));

            return;
        }

        // 显示结果
        displayResults(elytraLocations, playerPos);
        isSearching = false;
    }

    private void displayResults(List<ElytraLocation> locations, BlockPos playerPos) {
        if (locations.isEmpty()) {
            warning("在指定范围内未找到带鞘翅的末地城");
            return;
        }

        // 按距离排序
        locations.sort((a, b) -> Double.compare(a.distance, b.distance));

        int shown = 0;
        for (int index = 0; index < Math.min(locations.size(), 20); index++) {
            var location = locations.get(index);
            String distance = includeDistance.get() ? String.format("，距离 %.0f 米", location.distance) : "";
            info("%s：X=%d Z=%d%s", "鞘翅", location.position.getX(), location.position.getZ(), distance);
            shown++;
        }
        info("搜索完成，共 %d 个结果；已显示最近 %d 个。", locations.size(), shown);
    }

    // 内部类存储鞘翅位置信息
    private static class ElytraLocation {
        final BPos position;
        final double distance;

        ElytraLocation(BPos position, double distance) {
            this.position = position;
            this.distance = distance;
        }
    }
}
