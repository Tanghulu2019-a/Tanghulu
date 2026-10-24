package dev.tanghulu.ancientapple.qa;

import com.google.gson.GsonBuilder;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.version.MCVersion;
import dev.tanghulu.ancientapple.prediction.*;
import kludwisz.ancientcity.*;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryWrapper;
import java.nio.file.*;
import java.util.*;

/** Reproduces the user's exact chest through each supported vanilla loot profile. */
public final class ServerSampleAudit {
    public static void run(RegistryWrapper.WrapperLookup registries,Path project) throws Exception {
        long seed=-7346913998703726680L;
        int x=49286,y=-49,z=-175458;
        var report=new LinkedHashMap<String,Object>();
        report.put("seed",seed); report.put("x",x); report.put("y",y); report.put("z",z);
        var area=new SearchArea(x,z,384); var finder=new AncientCity(MCVersion.v1_21);
        var oldLoot=new AncientLoot(registries,LootVersion.MC_1_21_11);
        var newLoot=new AncientLoot(registries,LootVersion.MC_26_1);
        boolean found=false;
        for(var region:area.regions()) {
            var candidate=finder.getInRegion(seed,region[0],region[1],new ChunkRand());
            var layout=new AncientCityGenerator(); layout.generate(seed,candidate.getX(),candidate.getZ(),new ChunkRand());
            for(var c:LayoutPredictor.chests(layout,seed)) {
                if(c.x()!=x || c.y()!=y || c.z()!=z)continue;
                found=true; report.put("cityChunkX",candidate.getX()); report.put("cityChunkZ",candidate.getZ());
                report.put("lightweightLootSeed",c.seed()); report.put("terrainSensitive",c.terrainRequired());
                var overlapping=OverlapDetector.chunks(layout,seed,candidate.getX(),candidate.getZ(),new VanillaBiomes(seed));
                boolean sensitive=c.terrainRequired() || overlapping.contains(LayoutPredictor.chunk(x,z));
                report.put("verificationRequired",sensitive);
                long corrected=c.seed();
                if(sensitive)try(var terrain=TerrainBridge.open(project.resolve("build/server-sample-terrain"),seed)) {
                    var realSeed=terrain.lootSeedAt(x,y,z); if(realSeed!=null)corrected=realSeed;
                }
                List<Map<String,Object>> variants=new ArrayList<>();
                for(long lootSeed:new LinkedHashSet<>(List.of(c.seed(),corrected))) {
                    int oldCount=oldLoot.generate(lootSeed).stream().filter(s -> s.isOf(Items.ENCHANTED_GOLDEN_APPLE)).mapToInt(s -> s.getCount()).sum();
                    int newCount=newLoot.generate(lootSeed).stream().filter(s -> s.isOf(Items.ENCHANTED_GOLDEN_APPLE)).mapToInt(s -> s.getCount()).sum();
                    variants.add(Map.of("lootSeed",lootSeed,"apples1.21.11",oldCount,"apples26.1",newCount));
                }
                report.put("variants",variants);
            }
        }
        report.put("chestPositionMatched",found);
        if(!found)throw new AssertionError("User sample is not in predicted layout");
        Files.createDirectories(project.resolve("build/reports"));
        Files.writeString(project.resolve("build/reports/server-sample.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("SERVER_SAMPLE "+report);
        // This file is consumed by an independently compiled, official 26.1 server process.
        List<Map<String,Object>> fixtures=new ArrayList<>();
        var rng=new Random(261);
        for(int i=0;i<1024;i++) {
            long lootSeed=i==0?-18307909876657279L:rng.nextLong(); if(lootSeed==0)lootSeed=1;
            var stacks=newLoot.generate(lootSeed);
            int apples=stacks.stream().filter(s -> s.isOf(Items.ENCHANTED_GOLDEN_APPLE)).mapToInt(s -> s.getCount()).sum();
            fixtures.add(Map.of("lootSeed",lootSeed,"apples",apples,"items",LootSignature.of(stacks,newLoot.registries())));
        }
        Files.writeString(project.resolve("build/reports/loot26-fixtures.json"),new GsonBuilder().setPrettyPrinting().create().toJson(fixtures));
    }
}
