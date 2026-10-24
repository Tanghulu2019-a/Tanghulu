package dev.tanghulu.ancientapple.qa;

import com.google.gson.*;
import kludwisz.ancientcity.jigsawpools.AncientCityPools;
import kludwisz.ancientcity.structures.AncientCityPieceNames;
import kludwisz.ancientcity.structures.AncientCityStructureLoot;
import kludwisz.ancientcity.structures.AncientCityStructureSize;
import net.minecraft.nbt.*;
import java.io.*;
import java.net.JarURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Audits every vanilla ancient-city template, not just templates seen in seed fixtures. */
public final class TemplateAudit {
    private static final String PREFIX="data/minecraft/structure/ancient_city/";
    private static final String[] POOLS={"city_center","city_center/walls","sculk","structures","walls","city/entrance","walls/no_corners"};
    private TemplateAudit() {}
    public static Map<String,Object> run(List<String> errors) throws Exception {
        var url=TemplateAudit.class.getClassLoader().getResource(PREFIX+"city_center/city_center_1.nbt");
        if(url==null || !(url.openConnection() instanceof JarURLConnection connection))throw new IOException("Vanilla structure resources not found");
        connection.setUseCaches(false);
        int templates=0,treasureChests=0,iceBoxes=0,fixedChests=0;
        Set<String> names=new TreeSet<>();
        try(var jar=connection.getJarFile()) {
            for(var entry:Collections.list(jar.entries())) {
                String path=entry.getName();
                if(!path.startsWith(PREFIX) || !path.endsWith(".nbt"))continue;
                String name=path.substring(PREFIX.length(),path.length()-4); names.add(name); templates++;
                int id=AncientCityPieceNames.PIECENAMES.indexOf(name);
                if(id<0) { errors.add("Unknown vanilla ancient-city template: "+name); continue; }
                Set<String> expected=new TreeSet<>(),actual=new TreeSet<>();
                for(var descriptor:AncientCityStructureLoot.STRUCTURE_LOOT_V2.get(id)) {
                    var p=descriptor.getFirst(); expected.add(p.getX()+","+p.getY()+","+p.getZ());
                }
                NbtCompound nbt;
                try(var in=jar.getInputStream(entry)) { nbt=NbtIo.readCompressed(in,NbtSizeTracker.ofUnlimitedBytes()); }
                var size=nbt.getListOrEmpty("size"); var expectedSize=AncientCityStructureSize.STRUCTURE_SIZE_V2.get(id);
                if(size.getInt(0,0)!=expectedSize.getX() || size.getInt(1,0)!=expectedSize.getY() || size.getInt(2,0)!=expectedSize.getZ())errors.add("Template dimensions differ: "+name);
                for(var block:nbt.getListOrEmpty("blocks").streamCompounds().toList()) {
                    var data=block.getCompoundOrEmpty("nbt"); String table=data.getString("LootTable","");
                    var pos=block.getListOrEmpty("pos"); String coordinate=pos.getInt(0,0)+","+pos.getInt(1,0)+","+pos.getInt(2,0);
                    if(table.equals("minecraft:chests/ancient_city") || table.equals("minecraft:chests/ancient_city_ice_box")) {
                        actual.add(coordinate);
                        if(table.endsWith("ice_box"))iceBoxes++; else treasureChests++;
                        if(table.endsWith("ice_box")!=(id==27))errors.add("Unexpected loot table in "+name+" at "+coordinate);
                    } else if(!table.isEmpty())errors.add("Unhandled city loot table "+table+" in "+name);
                    if(data.getString("id","").equals("minecraft:chest") && table.isEmpty()) {
                        fixedChests++;
                        if(id!=7 || !coordinate.equals("9,8,20"))errors.add("Unhandled fixed chest in "+name+" at "+coordinate);
                        if(data.getListOrEmpty("Items").streamCompounds().anyMatch(item -> item.getString("id","").equals("minecraft:enchanted_golden_apple")))errors.add("Unpredicted fixed enchanted apple in "+name);
                    }
                }
                if(!expected.equals(actual))errors.add("Template chest coordinates differ: "+name+" expected="+expected+" actual="+actual);
            }
            for(int i=0;i<POOLS.length;i++) {
                var entry=jar.getJarEntry("data/minecraft/worldgen/template_pool/ancient_city/"+POOLS[i]+".json");
                Map<String,Integer> expected=new TreeMap<>(),actual=new TreeMap<>();
                for(var p:AncientCityPools.CITY_POOLS_V2.get(i)) {
                    // Upstream represents the camp_1/camp_2/camp_3 overlay by camp_2.
                    // They have identical dimensions/connectors and no loot containers.
                    String name=p.getFirst()==22?"structures/camp_1":AncientCityPieceNames.PIECENAMES.get(p.getFirst());
                    expected.merge(name,p.getSecond(),Integer::sum);
                }
                try(var in=new InputStreamReader(jar.getInputStream(entry),StandardCharsets.UTF_8)) {
                    for(var e:JsonParser.parseReader(in).getAsJsonObject().getAsJsonArray("elements")) {
                        var eobj=e.getAsJsonObject();
                        actual.merge(key(eobj.getAsJsonObject("element"),names,errors),eobj.get("weight").getAsInt(),Integer::sum);
                    }
                }
                if(!expected.equals(actual))errors.add("City pool members/weights differ: "+POOLS[i]+" expected="+expected+" actual="+actual);
            }
        }
        var result=Map.<String,Object>of("templates",templates,"pools",POOLS.length,"treasureChestPositions",treasureChests,"iceBoxes",iceBoxes,"fixedChests",fixedChests);
        System.out.println("ORACLE_TEMPLATE_AUDIT "+result);
        return result;
    }
    private static String key(JsonObject element,Set<String> names,List<String> errors) {
        String type=element.get("element_type").getAsString();
        if(type.equals("minecraft:empty_pool_element"))return "empty";
        if(type.equals("minecraft:feature_pool_element"))return "sculk_patch";
        if(type.equals("minecraft:list_pool_element")) {
            var elements=element.getAsJsonArray("elements");
            String first=key(elements.get(0).getAsJsonObject(),names,errors);
            for(int i=1;i<elements.size();i++)key(elements.get(i).getAsJsonObject(),names,errors);
            return first;
        }
        String location=element.get("location").getAsString();
        String name=location.replace("minecraft:ancient_city/","");
        // Vanilla itself references this absent template; its loaded template is empty.
        if(!names.contains(name) && !name.equals("walls/intact_horizontal_wall_stairs_5"))errors.add("Missing template referenced by pool: "+location);
        return name;
    }
}
