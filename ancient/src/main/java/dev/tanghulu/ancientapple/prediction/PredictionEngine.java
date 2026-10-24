package dev.tanghulu.ancientapple.prediction;

import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.version.MCVersion;
import dev.tanghulu.ancientapple.core.ChestTarget;
import kludwisz.ancientcity.AncientCity;
import kludwisz.ancientcity.AncientCityGenerator;
import net.minecraft.item.Items;
import net.minecraft.registry.*;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.noise.NoiseConfig;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.nio.file.Path;

/** Single worker owns all mutable RNGs and noise caches. No client world access off thread. */
public final class PredictionEngine implements AutoCloseable {
    public record Progress(int completed,int total,int cities,int chests) {}
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r -> { Thread t=new Thread(r,"ancient-apple-prediction"); t.setDaemon(true); return t; });
    private final AtomicLong generation=new AtomicLong();
    private Future<?> job;
    public volatile Progress progress=new Progress(0,0,0,0);
    public volatile boolean running;
    public volatile int terrainChests;
    // Interrupting a zipfs read closes the shared Minecraft JAR channel for the
    // entire process. Cancel cooperatively at region/chest boundaries instead.
    public synchronized void cancel() { generation.incrementAndGet(); if(job!=null) job.cancel(false); running=false; }
    public synchronized void search(long seed,int centerX,int centerZ,int radius,RegistryWrapper.WrapperLookup lootRegistries,Path terrainDirectory,
                                    Consumer<List<ChestTarget>> onDone,Consumer<Throwable> onError) {
        search(seed,centerX,centerZ,radius,lootRegistries,LootVersion.MC_1_21_11,terrainDirectory,onDone,onError);
    }
    public synchronized void search(long seed,int centerX,int centerZ,int radius,RegistryWrapper.WrapperLookup lootRegistries,LootVersion lootVersion,Path terrainDirectory,
                                    Consumer<List<ChestTarget>> onDone,Consumer<Throwable> onError) {
        cancel(); long epoch=generation.get(); running=true;
        job=executor.submit(() -> {
            TerrainBridge terrain=null; terrainChests=0;
            Set<Long> liveTerrainChunks=new HashSet<>();
            try {
                var biomes=new VanillaBiomes(seed);
                AncientLoot loot=new AncientLoot(lootRegistries,lootVersion);
                AncientCity structure=new AncientCity(MCVersion.v1_21);
                AncientCityGenerator layout=new AncientCityGenerator(); ChunkRand random=new ChunkRand();
                SearchArea area=new SearchArea(centerX,centerZ,radius);
                List<int[]> regions=area.regions();
                List<ChestTarget> result=new ArrayList<>(); int completed=0,cities=0;
                progress=new Progress(0,regions.size(),0,0);
                for(int[] region:regions) {
                    if(Thread.currentThread().isInterrupted() || generation.get()!=epoch) return;
                    var candidate=structure.getInRegion(seed,region[0],region[1],random);
                    int x=candidate.getX(),z=candidate.getZ();
                    if(area.mayContainCity(x*16,z*16)) {
                        layout.generate(seed,x,z,random);
                        if(area.intersects(layout) && biomes.valid(layout)) {
                            cities++;
                            var overlapping=OverlapDetector.chunks(layout,seed,x,z,biomes);
                            for(var c:LayoutPredictor.chests(layout,seed)) {
                                if(Thread.currentThread().isInterrupted() || generation.get()!=epoch)return;
                                if(!area.contains(c.x(),c.z()))continue;
                                long chestSeed=c.seed();
                                boolean sensitive=c.terrainRequired() || overlapping.contains(LayoutPredictor.chunk(c.x(),c.z()));
                                if(sensitive) {
                                    long chunk=LayoutPredictor.chunk(c.x(),c.z());
                                    if(terrain!=null && !liveTerrainChunks.contains(chunk) && liveTerrainChunks.size()>=24) { terrain.close(); terrain=null; liveTerrainChunks.clear(); }
                                    if(terrain==null)terrain=TerrainBridge.open(terrainDirectory,seed);
                                    liveTerrainChunks.add(chunk);
                                    Long corrected=terrain.lootSeedAt(c.x(),c.y(),c.z()); terrainChests++;
                                    if(corrected!=null)chestSeed=corrected;
                                }
                                var stacks=chestSeed==0?List.<net.minecraft.item.ItemStack>of():loot.generate(chestSeed);
                                int apples=stacks.stream().filter(s -> s.isOf(Items.ENCHANTED_GOLDEN_APPLE)).mapToInt(net.minecraft.item.ItemStack::getCount).sum();
                                var target=new ChestTarget(c.x(),c.y(),c.z(),x,z,chestSeed,"minecraft:chests/ancient_city",apples,LootSignature.of(stacks,loot.registries()));
                                target.verificationRequired=sensitive || chestSeed==0; result.add(target);
                            }
                        }
                    }
                    progress=new Progress(++completed,regions.size(),cities,result.size());
                }
                if(generation.get()==epoch) { running=false; onDone.accept(List.copyOf(result)); }
            } catch(Throwable e) { if(generation.get()==epoch && !(e instanceof CancellationException)) { running=false; onError.accept(e); } }
            finally { if(terrain!=null)try { terrain.close(); } catch(Exception e) { if(generation.get()==epoch)onError.accept(e); } }
        });
    }
    @Override public void close() { cancel(); executor.shutdown(); }
}
