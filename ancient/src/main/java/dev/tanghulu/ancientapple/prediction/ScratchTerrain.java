package dev.tanghulu.ancientapple.prediction;

import com.mojang.serialization.Lifecycle;
import net.minecraft.command.permission.*;
import net.minecraft.datafixer.Schemas;
import net.minecraft.registry.*;
import net.minecraft.resource.*;
import net.minecraft.server.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.util.ApiServices;
import net.minecraft.util.SystemDetails;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.profiler.log.DebugSampleLog;
import net.minecraft.world.*;
import net.minecraft.world.chunk.ChunkLoadProgress;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.rule.GameRules;
import java.net.Proxy;
import java.nio.file.*;
import java.util.*;

/** Isolated, unticked vanilla generation context. No runServer(), socket binding or client-world replacement.
 * Only terrain-dependent chest chunks are requested, plus vanilla's required neighbor stages. */
public final class ScratchTerrain extends MinecraftServer {
    private final Path directory;
    private ScratchTerrain(Thread thread,LevelStorage.Session session,ResourcePackManager packs,SaveLoader loader,ApiServices api,Path directory) {
        super(thread,session,packs,loader,Proxy.NO_PROXY,Schemas.getFixer(),api,new ChunkLoadProgress() {
            public void init(Stage stage,int total) {} public void progress(Stage stage,int done,int total) {}
            public void finish(Stage stage) {} public void initSpawnPos(RegistryKey<World> dimension,net.minecraft.util.math.ChunkPos pos) {}
        });
        this.directory=directory;
    }
    public static ScratchTerrain open(Path directory,long seed,ApiServices api) throws Exception {
        Files.createDirectories(directory);
        var session=LevelStorage.create(directory.resolve("saves")).createSession("terrain");
        ResourcePackManager packs=VanillaDataPackProvider.createManager(session);
        try {
            var config=new SaveLoading.ServerConfig(new SaveLoading.DataPacks(packs,DataConfiguration.SAFE_MODE,true,true),CommandManager.RegistrationEnvironment.DEDICATED,PermissionPredicate.ALL);
            SaveLoader loader=SaveLoading.load(config,c -> {
                var holder=new net.minecraft.world.dimension.DimensionOptionsRegistryHolder(Map.of(
                    net.minecraft.world.dimension.DimensionOptions.OVERWORLD,WorldPresets.getDefaultOverworldOptions(c.worldGenRegistryManager())));
                var dimensions=holder.toConfig(c.dimensionsRegistryManager().getOrThrow(RegistryKeys.DIMENSION));
                var info=new LevelInfo("Ancient Apple prediction",GameMode.SPECTATOR,false,Difficulty.PEACEFUL,true,new GameRules(c.dataConfiguration().enabledFeatures()),c.dataConfiguration());
                SaveProperties properties=new LevelProperties(info,new GeneratorOptions(seed,true,false),dimensions.specialWorldProperty(),Lifecycle.stable());
                properties.getMainWorldProperties().setInitialized(true);
                return new SaveLoading.LoadContext<>(properties,dimensions.toDynamicRegistryManager());
            },SaveLoader::new,Util.getMainWorkerExecutor(),Runnable::run).get();
            ScratchTerrain context=new ScratchTerrain(Thread.currentThread(),session,packs,loader,api,directory);
            context.setupServer(); return context;
        } catch(Throwable e) { session.tryClose(); throw e; }
    }
    public Long lootSeedAt(int x,int y,int z) {
        var world=getOverworld();
        var chunk=world.getChunk(Math.floorDiv(x,16),Math.floorDiv(z,16),ChunkStatus.FEATURES);
        var nbt=chunk.getPackedBlockEntityNbt(new BlockPos(x,y,z),world.getRegistryManager());
        if(nbt==null || !nbt.getString("LootTable","").equals("minecraft:chests/ancient_city"))return null;
        return nbt.getLong("LootTableSeed",0);
    }
    @Override protected boolean setupServer() {
        setOnlineMode(false); setPlayerManager(new PlayerManager(this,getCombinedDynamicRegistries(),saveHandler,getManagementListener()) {});
        createWorlds(); return true;
    }
    @Override public Path getRunDirectory() { return directory==null?Path.of("."):directory; }
    @Override public LeveledPermissionPredicate getOpPermissionLevel() { return LeveledPermissionPredicate.OWNERS; }
    @Override public PermissionPredicate getFunctionPermissions() { return PermissionPredicate.ALL; }
    @Override public boolean shouldBroadcastRconToOps() { return false; }
    @Override public boolean shouldBroadcastConsoleToOps() { return false; }
    @Override protected DebugSampleLog getDebugSampleLog() { return null; }
    @Override public boolean shouldPushTickTimeLog() { return false; }
    @Override public SystemDetails addExtraSystemDetails(SystemDetails details) { return details; }
    @Override public boolean isDedicated() { return false; }
    @Override public int getRateLimit() { return 0; }
    @Override public int getMaxPlayerCount() { return 0; }
    @Override public boolean isUsingNativeTransport() { return false; }
    @Override public boolean isRemote() { return false; }
    @Override public boolean isHost(PlayerConfigEntry profile) { return false; }
    @Override public void exit() {}
    @Override public void close() { try { super.close(); } finally { session.tryClose(); } }
}
