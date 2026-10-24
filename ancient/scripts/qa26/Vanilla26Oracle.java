import com.google.gson.*;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.serialization.JsonOps;
import net.minecraft.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.*;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.packs.repository.*;
import net.minecraft.server.permissions.*;
import net.minecraft.server.players.*;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import java.net.Proxy;
import java.nio.file.*;
import java.util.*;

/** Developer-only oracle compiled against Mojang's unmodified official 26.1 server.
 * Creates an isolated workspace world, with no network listener and no players. */
public final class Vanilla26Oracle {
    public static void main(String[] args) throws Exception {
        Path project=Path.of(args[0]).toAbsolutePath();
        Path directory=project.resolve("build/oracle-26.1"); Files.createDirectories(directory);
        SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        Path properties=directory.resolve("server.properties");
        Files.writeString(properties,"level-seed=-7346913998703726680\nonline-mode=false\nlevel-name=oracle\nview-distance=2\nsimulation-distance=2\nsync-chunk-writes=false\n");
        var settings=new DedicatedServerSettings(properties);
        var storage=LevelStorageSource.createDefault(directory.resolve("saves"));
        var access=storage.createAccess("oracle");
        var packs=ServerPacksSource.createPackRepository(access);
        var config=new WorldLoader.InitConfig(new WorldLoader.PackConfig(packs,WorldDataConfiguration.DEFAULT,true,true),Commands.CommandSelection.DEDICATED,PermissionSet.ALL_PERMISSIONS);
        WorldStem stem=WorldLoader.load(config,c -> {
            var dimensions=settings.getProperties().createDimensions(c.datapackWorldgen());
            var baked=dimensions.bake(c.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM));
            var levelSettings=new LevelSettings("oracle",GameType.SPECTATOR,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,c.dataConfiguration());
            var data=new PrimaryLevelData(levelSettings,baked.specialWorldProperty(),baked.lifecycle()); data.setInitialized(true);
            var worldGen=new WorldGenSettings(settings.getProperties().worldOptions,dimensions);
            return new WorldLoader.DataLoadOutput<>(new LevelDataAndDimensions.WorldDataAndGenSettings(data,worldGen),baked.dimensionsRegistryAccess());
        },WorldStem::new,net.minecraft.util.Util.backgroundExecutor(),Runnable::run).get();
        var services=Services.create(new YggdrasilAuthenticationService(Proxy.NO_PROXY),directory.toFile());
        var server=new OracleServer(access,packs,stem,services);
        try {
            server.initServer();
            var world=server.overworld(); var pos=new BlockPos(49286,-49,-175458);
            var chunk=world.getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FEATURES);
            var nbt=chunk.getBlockEntityNbtForSaving(pos,world.registryAccess());
            if(nbt==null)throw new AssertionError("Official 26.1 generation has no chest at the user sample");
            long actualSeed=nbt.getLongOr("LootTableSeed",0);
            String actualTable=nbt.getStringOr("LootTable","");
            if(actualSeed!=-18307909876657279L || !actualTable.equals("minecraft:chests/ancient_city"))throw new AssertionError("Official 26.1 sample seed differs: "+nbt);
            var table=server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,Identifier.withDefaultNamespace("chests/ancient_city")));
            var params=new LootParams.Builder(world).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(pos)).withLuck(0).create(LootContextParamSets.CHEST);
            var fixtures=JsonParser.parseString(Files.readString(project.resolve("build/reports/loot26-fixtures.json"))).getAsJsonArray();
            var ops=RegistryOps.create(JsonOps.INSTANCE,world.registryAccess());
            int checked=0,appleChests=0; List<String> errors=new ArrayList<>();
            for(var fixture:fixtures) {
                var expected=fixture.getAsJsonObject(); long seed=expected.get("lootSeed").getAsLong();
                var inventory=new SimpleContainer(27); table.fill(inventory,params,seed);
                int apples=0; Map<String,Integer> signature=new TreeMap<>();
                for(int i=0;i<27;i++) {
                    var stack=inventory.getItem(i); if(stack.isEmpty())continue;
                    if(stack.is(Items.ENCHANTED_GOLDEN_APPLE))apples+=stack.getCount();
                    var encoded=ItemStack.CODEC.encodeStart(ops,stack.copyWithCount(1)).getOrThrow();
                    signature.merge(canonical(encoded).toString(),stack.getCount(),Integer::sum);
                }
                Map<String,Integer> predicted=new TreeMap<>(); expected.getAsJsonObject("items").entrySet().forEach(e -> predicted.put(e.getKey(),e.getValue().getAsInt()));
                if(apples!=expected.get("apples").getAsInt() || !signature.equals(predicted)) {
                    errors.add("seed="+seed+" apples="+apples+"/"+expected.get("apples")+" itemsMatch="+signature.equals(predicted));
                    if(errors.size()<=2)System.out.println("LOOT26_MISMATCH "+errors.getLast()+" actual="+signature+" expected="+predicted);
                }
                if(apples>0)appleChests++; checked++;
            }
            var sample=new SimpleContainer(27); table.fill(sample,params,actualSeed);
            int sampleApples=0; for(int i=0;i<27;i++)if(sample.getItem(i).is(Items.ENCHANTED_GOLDEN_APPLE))sampleApples+=sample.getItem(i).getCount();
            var report=new LinkedHashMap<String,Object>();
            report.put("passed",errors.isEmpty() && checked==1024 && sampleApples==0); report.put("minecraft","26.1");
            report.put("method","Official Mojang 26.1 FEATURES chunk at user coordinate; LootTable.fill, complete item and component comparison against the addon, 1024 loot seeds; no network listener.");
            report.put("sampleLootSeed",actualSeed); report.put("sampleApples",sampleApples); report.put("checked",checked); report.put("appleChests",appleChests); report.put("errors",errors);
            Files.writeString(project.resolve("build/reports/loot26-oracle.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
            System.out.println("LOOT26_RESULT "+report);
            if(!errors.isEmpty() || sampleApples!=0)throw new AssertionError("26.1 oracle failed: "+errors.size());
        } finally { server.close(); access.close(); }
        System.exit(0);
    }
    private static JsonElement canonical(JsonElement e) {
        if(e.isJsonObject()) { var out=new JsonObject(); new TreeSet<>(e.getAsJsonObject().keySet()).forEach(k -> out.add(k,canonical(e.getAsJsonObject().get(k)))); return out; }
        if(e.isJsonArray()) { var out=new JsonArray(); e.getAsJsonArray().forEach(v -> out.add(canonical(v))); return out; }
        return e;
    }
    private static final class OracleServer extends MinecraftServer {
        OracleServer(LevelStorageSource.LevelStorageAccess access,PackRepository packs,WorldStem stem,Services services) {
            super(Thread.currentThread(),access,packs,stem,Optional.empty(),Proxy.NO_PROXY,DataFixers.getDataFixer(),services,new LevelLoadListener() {
                public void start(Stage stage,int total) {} public void update(Stage stage,int done,int total) {} public void finish(Stage stage) {}
                public void updateFocus(ResourceKey<Level> dimension,ChunkPos pos) {}
            },false);
        }
        @Override protected boolean initServer() {
            setUsesAuthentication(false); setPlayerList(new PlayerList(this,registries(),playerDataStorage,notificationManager()) {}); createLevels(); return true;
        }
        @Override public LevelBasedPermissionSet operatorUserPermissions() { return LevelBasedPermissionSet.OWNER; }
        @Override public PermissionSet getFunctionCompilationPermissions() { return PermissionSet.ALL_PERMISSIONS; }
        @Override public boolean shouldRconBroadcast() { return false; }
        @Override public boolean shouldInformAdmins() { return false; }
        @Override protected SampleLogger getTickTimeLogger() { return null; }
        @Override public boolean isTickTimeLoggingEnabled() { return false; }
        @Override public SystemReport fillServerSystemReport(SystemReport report) { return report; }
        @Override public boolean isDedicatedServer() { return false; }
        @Override public int getRateLimitPacketsPerSecond() { return 0; }
        @Override public int getMaxPlayers() { return 0; }
        @Override public boolean useNativeTransport() { return false; }
        @Override public boolean isPublished() { return false; }
        @Override public boolean isSingleplayerOwner(NameAndId profile) { return false; }
        @Override protected void onServerExit() {}
    }
}
