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

/** Runs actual vanilla chunk generation in isolated build/oracle worlds, without a network listener.
 * It is a developer-only verifier, never packaged in the client addon. */
public final class VanillaOracle {
    private static final long[] SEEDS={0,1,-1,42,123456,61359933871276778L,5000697870809672599L,-8694405306540134269L,5629499605952483L,Long.MIN_VALUE};
    private static final List<Map<String,Object>> RECORDS=new ArrayList<>();
    private static final List<String> ERRORS=new ArrayList<>();
    private static Path project;
    private static Map<String,Object> templateAudit=Map.of(),boundarySearch=Map.of();
    public static void main(String[] args) throws Exception {
        project=Path.of(args[0]);
        SharedConstants.createGameVersion(); Bootstrap.initialize();
        if(Boolean.getBoolean("aah.qa.navigationOnly")) { verifySeed(0); System.exit(0); }
        if(Boolean.getBoolean("aah.qa.lootOnly")) { verifySeed(0); System.exit(0); }
        int seedLimit=Integer.getInteger("aah.qa.seeds",10);
        try {
            templateAudit=TemplateAudit.run(ERRORS);
            for(int i=0;i<Math.min(seedLimit,SEEDS.length);i++) verifySeed(SEEDS[i]);
            boolean passed=ERRORS.isEmpty() && RECORDS.size()>=20 && seedLimit>=10;
            writeReport(passed,Math.min(seedLimit,SEEDS.length));
            if(!ERRORS.isEmpty())throw new AssertionError(String.join("\n",ERRORS.subList(0,Math.min(20,ERRORS.size()))));
            System.out.println("ORACLE_RESULT passed="+passed+" cities="+RECORDS.size());
        } catch(Throwable e) {
            ERRORS.add(e.toString()); writeReport(false,(int)RECORDS.stream().map(r -> r.get("seed")).distinct().count());
            e.printStackTrace(); System.exit(1);
        }
        System.exit(0);
    }
    private static void verifySeed(long seed) throws Exception {
        Path directory=Path.of("worlds",Long.toString(seed)); Files.createDirectories(directory);
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
        try(var correction=TerrainBridge.open(directory.resolve("prediction-terrain"),seed)) {
            server.setupServer();
            var world=server.getOverworld();
            if(seed==0) {
                NavigationAudit.run(world,project);
                if(Boolean.getBoolean("aah.qa.navigationOnly"))ContainerAudit.run(world,project);
                else ServerSampleAudit.run(world.getRegistryManager(),project);
            }
            if(Boolean.getBoolean("aah.qa.lootOnly"))return;
            if(Boolean.getBoolean("aah.qa.prefetch")) { world.getChunk(46,76,ChunkStatus.FEATURES); world.getChunk(55,79,ChunkStatus.FEATURES); }
            var structure=world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE).getOrThrow(RegistryKey.of(RegistryKeys.STRUCTURE,Identifier.ofVanilla("ancient_city"))).value();
            AncientCity finder=new AncientCity(MCVersion.v1_21); ChunkRand random=new ChunkRand();
            VanillaBiomes biomes=new VanillaBiomes(seed);
            int found=0;
            int cityLimit=Integer.getInteger("aah.qa.citiesPerSeed",4);
            for(int ring=0;ring<=16 && found<cityLimit;ring++) for(int rx=-ring;rx<=ring && found<cityLimit;rx++) for(int rz=-ring;rz<=ring && found<cityLimit;rz++) {
                if(Math.max(Math.abs(rx),Math.abs(rz))!=ring)continue;
                var candidate=finder.getInRegion(seed,rx,rz,random);
                var chunk=world.getChunk(candidate.getX(),candidate.getZ(),ChunkStatus.STRUCTURE_STARTS);
                StructureStart start=chunk.getStructureStart(structure);
                var predictedLayout=new AncientCityGenerator(); predictedLayout.generate(seed,candidate.getX(),candidate.getZ(),new ChunkRand());
                if(biomes.valid(predictedLayout)!=(start!=null && start.hasChildren()))ERRORS.add("Biome mismatch seed="+seed+" chunk="+candidate.getX()+","+candidate.getZ());
                if(start==null || !start.hasChildren())continue;
                System.out.println("ORACLE_CITY seed="+seed+" chunk="+candidate.getX()+","+candidate.getZ());
                verifyCity(server,seed,start,candidate.getX(),candidate.getZ(),correction); found++;
            }
            if(found<cityLimit)ERRORS.add("Seed "+seed+" did not find "+cityLimit+" cities");
            if(seed==0 && !Boolean.getBoolean("aah.qa.navigationOnly")) {
                for(int[] center:new int[][]{{50000,50000},{-100000,100000},{150000,-150000}}) {
                    int rx=Math.floorDiv(center[0],384),rz=Math.floorDiv(center[1],384); boolean farFound=false;
                    for(int ring=0;ring<8 && !farFound;ring++)for(int dx=-ring;dx<=ring && !farFound;dx++)for(int dz=-ring;dz<=ring && !farFound;dz++) {
                        if(Math.max(Math.abs(dx),Math.abs(dz))!=ring)continue;
                        var candidate=finder.getInRegion(seed,rx+dx,rz+dz,random);
                        var layout=new AncientCityGenerator(); layout.generate(seed,candidate.getX(),candidate.getZ(),new ChunkRand());
                        if(!biomes.valid(layout))continue;
                        var start=world.getChunk(candidate.getX(),candidate.getZ(),ChunkStatus.STRUCTURE_STARTS).getStructureStart(structure);
                        if(start==null || !start.hasChildren())throw new AssertionError("Far coordinate biome mismatch");
                        System.out.println("ORACLE_FAR center="+center[0]+","+center[1]);
                        verifyCity(server,seed,start,candidate.getX(),candidate.getZ(),correction); farFound=true;
                    }
                    if(!farFound)ERRORS.add("No far ancient city near "+Arrays.toString(center));
                }
            }
            if(seed==0 && !Boolean.getBoolean("aah.qa.navigationOnly"))verifyBoundarySearch(world.getRegistryManager(),directory.resolve("boundary-prediction"));
        } finally { server.close(); }
    }
    private static void verifyBoundarySearch(RegistryWrapper.WrapperLookup registries,Path terrain) throws Exception {
        SearchArea area=new SearchArea(1190,1247,384);
        try(PredictionEngine engine=new PredictionEngine()) {
            var result=new java.util.concurrent.CompletableFuture<List<dev.tanghulu.ancientapple.core.ChestTarget>>();
            engine.search(0,area.centerX(),area.centerZ(),area.radius(),registries,terrain,result::complete,result::completeExceptionally);
            var targets=result.get(180,java.util.concurrent.TimeUnit.SECONDS);
            boolean found=targets.stream().anyMatch(t -> t.x==841 && t.y==-48 && t.z==1248 && t.apples==2);
            boolean bounded=targets.stream().allMatch(t -> area.contains(t.x,t.z));
            if(!found || !bounded)ERRORS.add("Live prediction worker missed boundary apple chest or returned out-of-radius chest");
            boundarySearch=Map.of("foundOutsideStartChest",found,"allTargetsInsideRadius",bounded,"targets",targets.size());
            System.out.println("ORACLE_BOUNDARY_SEARCH "+boundarySearch);
        }
    }
    private static void verifyCity(OracleServer server,long seed,StructureStart start,int cx,int cz,TerrainBridge correction) throws Exception {
        var world=server.getOverworld();
        var lookup=world.getRegistryManager();
        AncientCityGenerator layout=new AncientCityGenerator(); layout.generate(seed,cx,cz,new ChunkRand());
        Map<BlockPos,Long> predicted=new HashMap<>();
        Set<BlockPos> uncertain=new HashSet<>();
        int corrected=0;
        var overlapping=OverlapDetector.chunks(layout,seed,cx,cz,new VanillaBiomes(seed));
        for(var c:LayoutPredictor.chests(layout,seed)) {
            Long value=c.seed(); if(c.terrainRequired() || overlapping.contains(LayoutPredictor.chunk(c.x(),c.z()))) { uncertain.add(new BlockPos(c.x(),c.y(),c.z())); value=correction.lootSeedAt(c.x(),c.y(),c.z()); corrected++; }
            if(value!=null)predicted.put(new BlockPos(c.x(),c.y(),c.z()),value);
        }
        System.out.println("ORACLE_TERRAIN corrected="+corrected);
        Map<BlockPos,Long> actual=new HashMap<>(); var bounds=start.getBoundingBox();
        int row=0;
        for(int x=bounds.getMinX()>>4;x<=bounds.getMaxX()>>4;x++) {
            for(int z=bounds.getMinZ()>>4;z<=bounds.getMaxZ()>>4;z++) {
                var chunk=world.getChunk(x,z,ChunkStatus.FEATURES);
                for(var pos:chunk.getBlockEntityPositions()) {
                    if(!bounds.contains(pos))continue;
                    var nbt=chunk.getPackedBlockEntityNbt(pos,lookup); if(nbt==null)continue;
                    if(nbt.getString("LootTable","").equals("minecraft:chests/ancient_city")) actual.put(pos.toImmutable(),nbt.getLong("LootTableSeed",0));
                }
            }
            System.out.println("ORACLE_CHUNKS row="+(++row)+" actualChests="+actual.size());
        }
        if(!actual.equals(predicted)) {
            for(var e:predicted.entrySet()) if(!Objects.equals(actual.get(e.getKey()),e.getValue()) && !uncertain.contains(e.getKey()))ERRORS.add("Unflagged seed variance seed="+seed+" pos="+e.getKey()+" seed predicted="+e.getValue()+" actual="+actual.get(e.getKey()));
            for(var e:actual.entrySet()) if(!predicted.containsKey(e.getKey()) && !uncertain.contains(e.getKey()))ERRORS.add("Missing prediction seed="+seed+" pos="+e.getKey());
        }
        AncientLoot engine=new AncientLoot(lookup);
        var table=server.getReloadableRegistries().getLootTable(RegistryKey.of(RegistryKeys.LOOT_TABLE,Identifier.ofVanilla("chests/ancient_city")));
        int apples=0; List<Map<String,Object>> chests=new ArrayList<>();
        for(var entry:actual.entrySet()) {
            var params=new LootWorldContext.Builder(world).add(LootContextParameters.ORIGIN,Vec3d.ofCenter(entry.getKey())).luck(0).build(LootContextTypes.CHEST);
            SimpleInventory container=new SimpleInventory(27); table.supplyInventory(container,params,entry.getValue());
            List<ItemStack> real=new ArrayList<>(); for(int i=0;i<27;i++)real.add(container.getStack(i));
            var expected=engine.generate(entry.getValue());
            int realApples=real.stream().filter(s -> s.isOf(Items.ENCHANTED_GOLDEN_APPLE)).mapToInt(ItemStack::getCount).sum();
            int predictedApples=expected.stream().filter(s -> s.isOf(Items.ENCHANTED_GOLDEN_APPLE)).mapToInt(ItemStack::getCount).sum();
            if(!LootSignature.of(real,lookup).equals(LootSignature.of(expected,engine.registries())))ERRORS.add("Loot mismatch seed="+seed+" pos="+entry.getKey()+" apples="+predictedApples+"/"+realApples);
            apples+=realApples;
            chests.add(Map.of("x",entry.getKey().getX(),"y",entry.getKey().getY(),"z",entry.getKey().getZ(),"lootSeed",entry.getValue(),"apples",realApples,"verificationRequired",uncertain.contains(entry.getKey())));
        }
        RECORDS.add(Map.of("seed",seed,"chunkX",cx,"chunkZ",cz,"chests",chests,"apples",apples));
        System.out.println("ORACLE_CITY_DONE chests="+actual.size()+" apples="+apples+" errors="+ERRORS.size());
    }
    private static void writeReport(boolean passed,int seeds) throws Exception {
        var report=new LinkedHashMap<String,Object>(); report.put("passed",passed); report.put("minecraft","1.21.11"); report.put("algorithm",WorldStore.ALGORITHM);
        report.put("certaintyPolicy","verify-order-sensitive");
        var digest=java.security.MessageDigest.getInstance("SHA-256");
        try(var paths=Files.walk(project.resolve("src/main/java"))) {
            for(Path file:paths.filter(Files::isRegularFile).filter(p -> p.toString().contains("prediction") || p.toString().contains("kludwisz")).sorted(java.util.Comparator.comparing(p -> project.relativize(p).toString())).toList()) {
                digest.update(project.relativize(file).toString().replace('\\','/').getBytes(java.nio.charset.StandardCharsets.UTF_8)); digest.update(Files.readAllBytes(file));
            }
        }
        report.put("sourceHash",HexFormat.of().formatHex(digest.digest()));
        report.put("templateAudit",templateAudit); report.put("boundarySearch",boundarySearch);
        report.put("seeds",seeds); report.put("cities",RECORDS.size()); report.put("records",RECORDS); report.put("errors",ERRORS);
        report.put("method","Vanilla 1.21.11 server classes, real FEATURES chunks and LootTable.supplyInventory; isolated workspace saves, no network listener.");
        Path file=project.resolve("build/reports/prediction-oracle.json"); Files.createDirectories(file.getParent()); Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(report));
        if(passed)Files.writeString(project.resolve("src/main/resources/prediction-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
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
