package dev.tanghulu.prediction26;

import com.google.gson.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.util.Util;
import java.nio.file.*;
import java.util.*;

/** Compares the prediction protocol to independently requested vanilla chunk starts/blocks. */
public final class Vanilla26Audit {
    static final Gson JSON=new Gson();
    static final List<String> IDS=List.of("village_plains","village_desert","village_savanna","village_snowy","village_taiga",
        "shipwreck","shipwreck_beached","buried_treasure","ocean_ruin_cold","ocean_ruin_warm","pillager_outpost","fortress",
        "bastion_remnant","end_city","desert_pyramid","igloo","jungle_pyramid","mansion","mineshaft","mineshaft_mesa",
        "monument","nether_fossil","ruined_portal","ruined_portal_desert","ruined_portal_jungle","ruined_portal_mountain",
        "ruined_portal_ocean","ruined_portal_swamp","stronghold","swamp_hut");
    static int checks,starts,oreBlocks,ships;
    static JsonObject query(Vanilla26 server,Map<String,Object> request) throws Exception {
        return JSON.toJsonTree(server.request(JSON.toJsonTree(request).getAsJsonObject())).getAsJsonObject();
    }
    static void check(boolean value,String message) { checks++; if(!value)throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        Path root=Path.of(args[0]); Files.createDirectories(root);
        try {
            for(long seed:new long[]{0,-7346913998703726680L})try(var server=Vanilla26.open(root.resolve("seed-"+seed),seed)) {
                for(String dim:List.of("overworld","the_nether","the_end")) {
                    var world=server.getLevel(switch(dim) { case "the_end" -> Level.END; case "the_nether" -> Level.NETHER; default -> Level.OVERWORLD; });
                    int center=dim.equals("the_end")?100:-100;
                    var rows=query(server,Map.of("op","structures","dimension",dim,"x",center,"z",center,"radius",64,"selected",IDS)).getAsJsonArray("structures");
                    Set<String> predicted=new HashSet<>();
                    for(var value:rows) {
                        var row=value.getAsJsonArray(); String id=row.get(0).getAsString(); int x=row.get(1).getAsInt()>>4,z=row.get(3).getAsInt()>>4;
                        check(predicted.add(id+":"+x+":"+z),"duplicate start");
                        check((long)(x-center)*(x-center)+(long)(z-center)*(z-center)<=64*64,"outside radius");
                        if(starts++%5==0) {
                            var chunk=world.getChunk(x,z,ChunkStatus.STRUCTURE_STARTS);
                            check(chunk.getAllStarts().entrySet().stream().anyMatch(e -> e.getValue().isValid()
                                && server.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(e.getKey()).getPath().equals(id)),"false start "+id);
                        }
                    }
                    // Exhaustive small negative-coordinate area checks candidate enumeration for missing results.
                    int smallCenter=-4;
                    var small=query(server,Map.of("op","structures","dimension",dim,"x",smallCenter,"z",smallCenter,"radius",3,"selected",IDS)).getAsJsonArray("structures");
                    Set<String> actualSmall=new HashSet<>(),predictedSmall=new HashSet<>();
                    for(var value:small) { var row=value.getAsJsonArray(); predictedSmall.add(row.get(0).getAsString()+":"+(row.get(1).getAsInt()>>4)+":"+(row.get(3).getAsInt()>>4)); }
                    for(int x=-7;x<=-1;x++)for(int z=-7;z<=-1;z++)if((x+4)*(x+4)+(z+4)*(z+4)<=9) {
                        var chunk=world.getChunk(x,z,ChunkStatus.STRUCTURE_STARTS);
                        for(var e:chunk.getAllStarts().entrySet())if(e.getValue().isValid()) {
                            String id=server.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(e.getKey()).getPath();
                            if(IDS.contains(id))actualSmall.add(id+":"+x+":"+z);
                        }
                    }
                    check(predictedSmall.equals(actualSmall),"missing/extra negative-coordinate structure "+dim);
                    for(int[] chunkPos:new int[][]{{0,0},{-2,-3}}) {
                        var ores=query(server,Map.of("op","ores","dimension",dim,"x",chunkPos[0],"z",chunkPos[1])).getAsJsonArray("ores");
                        Set<String> expected=new HashSet<>(),observed=new HashSet<>();
                        for(var value:ores) { var row=value.getAsJsonArray(); observed.add(row.get(0).getAsString()+":"+row.get(1)+":"+row.get(2)+":"+row.get(3)); }
                        var chunk=world.getChunk(chunkPos[0],chunkPos[1],ChunkStatus.FEATURES);
                        Set<String> oreTypes=Set.of("coal_ore","iron_ore","gold_ore","redstone_ore","diamond_ore","lapis_ore","copper_ore","emerald_ore","nether_quartz_ore","ancient_debris");
                        for(int x=chunkPos[0]*16;x<chunkPos[0]*16+16;x++)for(int z=chunkPos[1]*16;z<chunkPos[1]*16+16;z++)for(int y=world.getMinY();y<world.getMaxY();y++) {
                            String id=BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(new BlockPos(x,y,z)).getBlock()).getPath().replace("deepslate_","");
                            if(id.equals("nether_gold_ore"))id="gold_ore";
                            if(oreTypes.contains(id))expected.add(id+":"+x+":"+y+":"+z);
                        }
                        check(observed.equals(expected),"ore mismatch "+seed+" "+dim+Arrays.toString(chunkPos)); oreBlocks+=expected.size();
                    }
                }
                var shipsResult=query(server,Map.of("op","structures","dimension","the_end","x",100,"z",100,"radius",100,"selected",List.of("end_city"),"ships",true)).getAsJsonArray("structures");
                for(var value:shipsResult) {
                    var row=value.getAsJsonArray(); var target=new BlockPos(row.get(1).getAsInt(),row.get(2).getAsInt(),row.get(3).getAsInt());
                    boolean found=false;
                    // Ship origin may be outside the start chunk; inspect start chunks in the requested region.
                    var cityRows=query(server,Map.of("op","structures","dimension","the_end","x",100,"z",100,"radius",100,"selected",List.of("end_city"))).getAsJsonArray("structures");
                    var field=TemplateStructurePiece.class.getDeclaredField("templateName"); field.setAccessible(true);
                    for(var city:cityRows) { var c=city.getAsJsonArray(); var chunk=server.getLevel(Level.END).getChunk(c.get(1).getAsInt()>>4,c.get(3).getAsInt()>>4,ChunkStatus.STRUCTURE_STARTS);
                        for(var start:chunk.getAllStarts().values())if(start.isValid())for(var p:start.getPieces())
                            if(p instanceof TemplateStructurePiece t && field.get(t).equals("ship") && t.templatePosition().equals(target))found=true;
                    }
                    check(found,"ship not present in actual vanilla start"); ships++;
                }
            }
            check(starts>10 && ships>0 && oreBlocks>100,"insufficient samples");
            Files.writeString(root.resolve("report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "passed",true,"version","26.3","checks",checks,"structureResults",starts,"ships",ships,"oreBlocks",oreBlocks,"inGameTested",false)));
            System.out.println("VANILLA26_AUDIT checks="+checks+" starts="+starts+" ships="+ships+" ores="+oreBlocks);
        } finally { Util.shutdownExecutors(); }
        System.exit(0);
    }
}
