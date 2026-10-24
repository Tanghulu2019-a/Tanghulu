package dev.tanghulu.ancientapple.qa;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.version.MCVersion;
import dev.tanghulu.ancientapple.prediction.AncientLoot;
import dev.tanghulu.ancientapple.prediction.LootSignature;
import dev.tanghulu.ancientapple.prediction.LayoutPredictor;
import dev.tanghulu.ancientapple.prediction.TerrainBridge;
import dev.tanghulu.ancientapple.prediction.VanillaBiomes;
import dev.tanghulu.ancientapple.prediction.OverlapDetector;
import dev.tanghulu.ancientapple.prediction.PredictionEngine;
import dev.tanghulu.ancientapple.prediction.SearchArea;
import dev.tanghulu.ancientapple.storage.WorldStore;
import kludwisz.ancientcity.AncientCity;
import kludwisz.ancientcity.AncientCityGenerator;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.datafixer.Schemas;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.registry.*;
import net.minecraft.resource.*;
import net.minecraft.server.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.dedicated.*;
import net.minecraft.command.permission.PermissionPredicate;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.rule.GameRules;
import java.net.Proxy;
import java.nio.file.*;
import java.util.*;

import dev.tanghulu.ancientapple.prediction.LootVersion;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
public final class LootProfilesOracle {
    public static void main(String[] args) throws Exception {
        Path project=Path.of(args[0]).toAbsolutePath(); long seed=0;
        SharedConstants.createGameVersion(); Bootstrap.initialize();
        Path directory=project.resolve("build/profile-oracle"); Files.createDirectories(directory);
        Path properties=directory.resolve("server.properties");
        Files.writeString(properties,"level-seed="+seed+"\nonline-mode=false\nlevel-name=oracle\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nmax-tick-time=-1\nsync-chunk-writes=false\n");
        var settings=new ServerPropertiesLoader(properties);
        var storage=LevelStorage.create(directory.resolve("saves"));
        var session=storage.createSession(System.getProperty("aah.qa.instance","oracle"));
        var packs=VanillaDataPackProvider.createManager(session);
        var config=new SaveLoading.ServerConfig(new SaveLoading.DataPacks(packs,DataConfiguration.SAFE_MODE,true,true),CommandManager.RegistrationEnvironment.DEDICATED,PermissionPredicate.ALL);
        SaveLoader loader=SaveLoading.load(config,c -> {
            var dims=settings.getPropertiesHandler().createDimensionsRegistryHolder(c.worldGenRegistryManager())
                .toConfig(c.dimensionsRegistryManager().getOrThrow(RegistryKeys.DIMENSION));
            var info=new LevelInfo("oracle",GameMode.SPECTATOR,false,Difficulty.PEACEFUL,true,new GameRules(c.dataConfiguration().enabledFeatures()),c.dataConfiguration());
            SaveProperties props=new LevelProperties(info,settings.getPropertiesHandler().generatorOptions,dims.specialWorldProperty(),dims.getLifecycle());
            props.getMainWorldProperties().setInitialized(true);
            return new SaveLoading.LoadContext<>(props,dims.toDynamicRegistryManager());
        },SaveLoader::new,Util.getMainWorkerExecutor(),Runnable::run).get();
        var api=ApiServices.create(new YggdrasilAuthenticationService(Proxy.NO_PROXY),directory.toFile());
        OracleServer server=new OracleServer(session,packs,loader,settings,api);
        try {
            server.setupServer();
            var world=server.getOverworld(); var registries=world.getRegistryManager();
            var params=new LootWorldContext.Builder(world).add(LootContextParameters.ORIGIN,Vec3d.ZERO).build(LootContextTypes.CHEST);
            var ops=RegistryOps.of(JsonOps.INSTANCE,registries);
            List<Map<String,Object>> report=new ArrayList<>();
            var noEnchantments=RegistryWrapper.WrapperLookup.of(registries.stream().filter(r -> !r.getKey().equals(RegistryKeys.ENCHANTMENT)));
            for(LootVersion version:LootVersion.values()) {
                var profile=JsonParser.parseString(Files.readString(project.resolve("src/main/resources/loot-profiles/"+version+".json"))).getAsJsonObject();
                var tableJson=profile.getAsJsonObject("table").deepCopy();
                // Direct HolderSet preserves the official version's candidate order.
                for(var p:tableJson.getAsJsonArray("pools"))for(var e:p.getAsJsonObject().getAsJsonArray("entries")) {
                    var entry=e.getAsJsonObject();
                    if(entry.has("functions"))for(var f:entry.getAsJsonArray("functions")) {
                        var fn=f.getAsJsonObject();
                        if(fn.has("options") && fn.get("options").isJsonPrimitive() && fn.get("options").getAsString().equals("#minecraft:on_random_loot"))fn.add("options",profile.getAsJsonArray("enchantments"));
                    }
                }
                var vanilla=LootTable.CODEC.parse(ops,tableJson).getOrThrow();
                var addon=new AncientLoot(registries,version); var random=new java.util.Random(261);
                var missingRegistry=new AncientLoot(noEnchantments,version);
                int mismatches=0,appleChests=0;
                List<Map<String,Object>> nativeFixtures=new ArrayList<>();
                for(int i=0;i<1024;i++) {
                    long lootSeed=i==0?-18307909876657279L:random.nextLong(); if(lootSeed==0)lootSeed=1;
                    var inventory=new SimpleInventory(27); vanilla.supplyInventory(inventory,params,lootSeed);
                    List<ItemStack> actual=new ArrayList<>(); for(int slot=0;slot<27;slot++)actual.add(inventory.getStack(slot));
                    var predicted=addon.generate(lootSeed);
                    if(version==LootVersion.MC_26_2 || version==LootVersion.MC_26_3) {
                        Map<String,Integer> counts=new TreeMap<>();
                        predicted.forEach(stack -> counts.merge(Registries.ITEM.getId(stack.getItem()).toString(),stack.getCount(),Integer::sum));
                        nativeFixtures.add(Map.of("lootSeed",lootSeed,"counts",counts));
                    }
                    var expectedSignature=LootSignature.of(actual,registries);
                    if(!expectedSignature.equals(LootSignature.of(predicted,addon.registries())))mismatches++;
                    if(!expectedSignature.equals(LootSignature.of(missingRegistry.generate(lootSeed),missingRegistry.registries())))
                        throw new AssertionError("Server enchantment registry leak: "+version+" seed="+lootSeed);
                    if(actual.stream().anyMatch(s -> s.isOf(Items.ENCHANTED_GOLDEN_APPLE)))appleChests++;
                }
                String profileHash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(project.resolve("src/main/resources/loot-profiles/"+version+".json"))));
                report.add(Map.of("version",version.toString(),"checked",1024,"mismatches",mismatches,"appleChests",appleChests,"profileSha256",profileHash,"missingRegistryChecked",1024));
                System.out.println("PROFILE_RESULT "+report.getLast());
                if(mismatches>0)throw new AssertionError(version+" mismatches: "+mismatches);
                if(!nativeFixtures.isEmpty())Files.writeString(project.resolve("build/reports/native-fixtures-"+version+".json"),new GsonBuilder().create().toJson(nativeFixtures));
            }
            Files.createDirectories(project.resolve("build/reports"));
            Files.writeString(project.resolve("build/reports/loot-profile-oracle.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed",true,"runtime","1.21.11","method","Vanilla LootTable.CODEC and supplyInventory, official per-version table and ordered enchantments, complete item/component comparison; no network listener", "versions",report)));
        } finally { server.close(); session.close(); }
        System.exit(0);
    }
    private static final class OracleServer extends MinecraftServer {
        OracleServer(LevelStorage.Session session,ResourcePackManager packs,SaveLoader loader,ServerPropertiesLoader settings,ApiServices api) {
            super(Thread.currentThread(),session,packs,loader,Proxy.NO_PROXY,Schemas.getFixer(),api,new net.minecraft.world.chunk.ChunkLoadProgress() {
                public void init(Stage stage,int total) {} public void progress(Stage stage,int done,int total) {}
                public void finish(Stage stage) {} public void initSpawnPos(RegistryKey<World> dimension,ChunkPos pos) {}
            });
        }
        @Override public boolean setupServer() {
            setOnlineMode(false); setPlayerManager(new PlayerManager(this,getCombinedDynamicRegistries(),saveHandler,getManagementListener()) {});
            createWorlds(); return true;
        }
        @Override public net.minecraft.command.permission.LeveledPermissionPredicate getOpPermissionLevel() { return net.minecraft.command.permission.LeveledPermissionPredicate.OWNERS; }
        @Override public PermissionPredicate getFunctionPermissions() { return PermissionPredicate.ALL; }
        @Override public boolean shouldBroadcastRconToOps() { return false; }
        @Override public boolean shouldBroadcastConsoleToOps() { return false; }
        @Override protected net.minecraft.util.profiler.log.DebugSampleLog getDebugSampleLog() { return null; }
        @Override public boolean shouldPushTickTimeLog() { return false; }
        @Override public SystemDetails addExtraSystemDetails(SystemDetails details) { return details; }
        @Override public boolean isDedicated() { return false; }
        @Override public int getRateLimit() { return 0; }
        @Override public int getMaxPlayerCount() { return 0; }
        @Override public boolean isUsingNativeTransport() { return false; }
        @Override public boolean isRemote() { return false; }
        @Override public boolean isHost(PlayerConfigEntry profile) { return false; }
        @Override public Path getRunDirectory() { return Path.of(".").toAbsolutePath(); }
        @Override public void exit() { }
    }
}
