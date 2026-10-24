package dev.tanghulu.prediction26;

import com.google.gson.*;
import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.server.*;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.notifications.NotificationManager;
import net.minecraft.server.packs.repository.*;
import net.minecraft.server.permissions.*;
import net.minecraft.server.players.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.structure.placement.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.storage.*;
import java.io.*;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** A separate, unticked 26.3 vanilla process. No socket, live save, mod or client classes. */
public final class Vanilla26 extends MinecraftServer {
    private final Path directory;
    private Vanilla26(LevelStorageSource.LevelStorageAccess session, PackRepository packs, WorldStem stem, Path directory) {
        super(Thread.currentThread(), session, packs, stem, Optional.empty(), Proxy.NO_PROXY,
            DataFixers.getDataFixer(), new Services(null,null,null,null,null), new LevelLoadListener() {
                public void start(Stage stage,int total) {} public void update(Stage stage,int done,int total) {}
                public void finish(Stage stage) {} public void updateFocus(ResourceKey<Level> level,ChunkPos pos) {}
            }, false, new NotificationManager());
        this.directory=directory;
    }
    static Vanilla26 open(Path directory,long seed) throws Exception {
        var session=LevelStorageSource.createDefault(directory.resolve("saves")).createAccess("prediction");
        var packs=ServerPacksSource.createPackRepository(session);
        try {
            var config=new WorldLoader.InitConfig(new WorldLoader.PackConfig(packs,WorldDataConfiguration.DEFAULT,true,true),
                Commands.CommandSelection.DEDICATED,PermissionSet.ALL_PERMISSIONS);
            WorldStem stem=WorldLoader.load(config,c -> {
                var dimensions=WorldPresets.createNormalWorldDimensions(c.datapackWorldRegistries());
                var baked=dimensions.bake(c.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM));
                var settings=new LevelSettings("Tanghulu 26.3 prediction",GameType.SPECTATOR,
                    new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,true),true,c.dataConfiguration());
                var data=new PrimaryLevelData(settings,baked.specialWorldProperty(),Lifecycle.stable());
                data.setInitialized(true);
                var gen=new WorldGenSettings(new WorldOptions(seed,true,false),dimensions);
                return new WorldLoader.DataLoadOutput<>(new LevelDataAndDimensions.WorldDataAndGenSettings(data,gen),baked.dimensionsRegistryAccess());
            },WorldStem::new,Util.backgroundExecutor(),Runnable::run).get();
            var server=new Vanilla26(session,packs,stem,directory);
            server.initServer(); return server;
        } catch(Throwable e) { session.close(); throw e; }
    }
    @Override protected boolean initServer() {
        setUsesAuthentication(false);
        setPlayerList(new PlayerList(this,registries(),playerDataStorage,notificationManager()) {});
        createLevels(); return true;
    }
    @Override public Path getServerDirectory() { return directory==null?Path.of("."):directory; }
    @Override public LevelBasedPermissionSet operatorUserPermissions() { return LevelBasedPermissionSet.OWNER; }
    @Override public PermissionSet getFunctionCompilationPermissions() { return PermissionSet.ALL_PERMISSIONS; }
    @Override public boolean shouldRconBroadcast() { return false; }
    @Override protected SampleLogger getTickTimeLogger() { return null; }
    @Override public boolean isTickTimeLoggingEnabled() { return false; }
    @Override public net.minecraft.SystemReport fillServerSystemReport(net.minecraft.SystemReport r) { return r; }
    @Override public boolean isDedicatedServer() { return false; }
    @Override public int getRateLimitPacketsPerSecond() { return 0; }
    @Override public int getMaxPlayers() { return 0; }
    @Override public int getCommandSpamThresholdSeconds() { return 0; }
    @Override public int getChatSpamThresholdSeconds() { return 0; }
    @Override public boolean useNativeTransport() { return false; }
    @Override public boolean isPublished() { return false; }
    @Override public boolean shouldInformAdmins() { return false; }
    @Override public boolean isSingleplayerOwner(NameAndId profile) { return false; }
    @Override protected void onServerExit() {}

    public static void main(String[] args) throws Exception {
        PrintStream protocol=new PrintStream(new FileOutputStream(FileDescriptor.out),true,StandardCharsets.UTF_8);
        System.setOut(System.err);
        SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        try(var server=open(Path.of(args[0]),Long.parseLong(args[1]));
            var reader=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
            protocol.println("READY 26.3");
            for(String line;(line=reader.readLine())!=null;) {
                if(line.equals("QUIT"))break;
                try { protocol.println(new Gson().toJson(server.request(JsonParser.parseString(line).getAsJsonObject()))); }
                catch(Throwable e) { e.printStackTrace(); protocol.println(new Gson().toJson(Map.of("error",e.toString()))); }
            }
        } finally { Util.shutdownExecutors(); }
        System.exit(0);
    }
    Object request(JsonObject request) throws Exception {
        String op=request.get("op").getAsString();
        if(op.equals("version"))return Map.of("version",getServerVersion());
        var dimension=switch(request.get("dimension").getAsString()) {
            case "overworld" -> Level.OVERWORLD; case "the_nether" -> Level.NETHER; case "the_end" -> Level.END;
            default -> throw new IllegalArgumentException("Unknown dimension");
        };
        var world=Objects.requireNonNull(getLevel(dimension));
        int cx=request.get("x").getAsInt(),cz=request.get("z").getAsInt();
        if(op.equals("ores")) {
            var chunk=world.getChunk(cx,cz,ChunkStatus.FEATURES);
            List<Object> result=new ArrayList<>();
            for(int s=0;s<chunk.getSections().length;s++) {
                var section=chunk.getSection(s);
                if(!section.maybeHas(state -> oreName(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath())!=null))continue;
                for(int y=0;y<16;y++)for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                    String name=oreName(BuiltInRegistries.BLOCK.getKey(section.getBlockState(x,y,z).getBlock()).getPath());
                    if(name!=null)result.add(List.of(name,cx*16+x,world.getMinY()+s*16+y,cz*16+z));
                }
            }
            return Map.of("ores",result);
        }
        if(!op.equals("structures"))throw new IllegalArgumentException("Unknown operation");
        int radius=request.get("radius").getAsInt();
        if(radius<1 || radius>2000)throw new IllegalArgumentException("Invalid radius");
        Set<String> selected=new HashSet<>(); request.getAsJsonArray("selected").forEach(v -> selected.add(v.getAsString()));
        boolean ships=request.has("ships") && request.get("ships").getAsBoolean();
        var state=world.getChunkSource().getGeneratorState();
        Set<Long> candidates=new TreeSet<>();
        for(var holder:state.possibleStructureSets()) {
            var set=holder.value();
            if(set.structures().stream().noneMatch(e -> selected.contains(e.structure().unwrapKey().orElseThrow().identifier().getPath())))continue;
            var placement=set.placement();
            if(placement instanceof RandomSpreadStructurePlacement spread) {
                int spacing=spread.spacing();
                for(int rx=Math.floorDiv(cx-radius,spacing);rx<=Math.floorDiv(cx+radius,spacing);rx++)
                    for(int rz=Math.floorDiv(cz-radius,spacing);rz<=Math.floorDiv(cz+radius,spacing);rz++) {
                        var pos=spread.getPotentialStructureChunk(state.getLevelSeed(),rx*spacing,rz*spacing);
                        if(inRadius(pos,cx,cz,radius) && placement.isStructureChunk(state,pos.x(),pos.z()))candidates.add(pos.pack());
                    }
            } else if(placement instanceof ConcentricRingsStructurePlacement rings) {
                for(var pos:state.getRingPositionsFor(rings))if(inRadius(pos,cx,cz,radius))candidates.add(pos.pack());
            } else {
                for(int x=cx-radius;x<=cx+radius;x++)for(int z=cz-radius;z<=cz+radius;z++)
                    if(inRadius(new ChunkPos(x,z),cx,cz,radius) && placement.isStructureChunk(state,x,z))candidates.add(ChunkPos.pack(x,z));
            }
        }
        List<Object> result=new ArrayList<>();
        var templateName=TemplateStructurePiece.class.getDeclaredField("templateName"); templateName.setAccessible(true);
        var palette=PalettedContainerFactory.create(registryAccess());
        for(long packed:candidates) {
            var pos=ChunkPos.unpack(packed);
            var chunk=new ProtoChunk(pos,UpgradeData.EMPTY,world,palette,null);
            world.getChunkSource().getGenerator().createStructures(registryAccess(),state,world.structureManager(),chunk,getStructureTemplateManager(),dimension);
            for(var entry:chunk.getAllStarts().entrySet()) {
                String id=registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(entry.getKey()).getPath();
                var start=entry.getValue();
                if(!selected.contains(id) || !start.isValid())continue;
                BlockPos location=new BlockPos(pos.getMinBlockX(),start.getBoundingBox().minY(),pos.getMinBlockZ());
                if(ships) {
                    TemplateStructurePiece ship=null;
                    for(var piece:start.getPieces())if(piece instanceof TemplateStructurePiece t && templateName.get(t).equals("ship")) { ship=t; break; }
                    if(ship==null)continue;
                    location=ship.templatePosition();
                }
                result.add(List.of(id,location.getX(),location.getY(),location.getZ()));
            }
        }
        return Map.of("structures",result);
    }
    private static boolean inRadius(ChunkPos p,int x,int z,int r) { return (long)(p.x()-x)*(p.x()-x)+(long)(p.z()-z)*(p.z()-z)<=(long)r*r; }
    private static String oreName(String name) {
        if(name.equals("ancient_debris"))return name;
        if(name.startsWith("deepslate_"))name=name.substring(10);
        return switch(name) { case "coal_ore","iron_ore","gold_ore","redstone_ore","diamond_ore","lapis_ore","copper_ore","emerald_ore","nether_quartz_ore" -> name;
            case "nether_gold_ore" -> "gold_ore"; default -> null; };
    }
}
