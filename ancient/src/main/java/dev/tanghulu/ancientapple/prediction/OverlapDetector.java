package dev.tanghulu.ancientapple.prediction;

import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.version.MCVersion;
import kludwisz.ancientcity.AncientCity;
import kludwisz.ancientcity.AncientCityGenerator;
import java.util.*;

/** Adjacent starts can share a decoration RNG in a chunk. Treat every shared
 * chest chunk as order-sensitive rather than assuming a single-city RNG stream. */
public final class OverlapDetector {
    private OverlapDetector() {}
    public static Set<Long> chunks(AncientCityGenerator city,long seed,int startX,int startZ,VanillaBiomes biomes) {
        Set<Long> chestChunks=new HashSet<>();
        for(var chest:LayoutPredictor.chests(city,seed))chestChunks.add(LayoutPredictor.chunk(chest.x(),chest.z()));
        Set<Long> result=new HashSet<>();
        int rx=Math.floorDiv(startX,24),rz=Math.floorDiv(startZ,24);
        AncientCity finder=new AncientCity(MCVersion.v1_21); ChunkRand random=new ChunkRand();
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            if(dx==0 && dz==0)continue;
            var candidate=finder.getInRegion(seed,rx+dx,rz+dz,random);
            if(Math.abs(candidate.getX()-startX)>16 || Math.abs(candidate.getZ()-startZ)>16)continue;
            var neighbor=new AncientCityGenerator(); neighbor.generate(seed,candidate.getX(),candidate.getZ(),random);
            if(!biomes.valid(neighbor))continue;
            for(int i=0;i<neighbor.piecesLen;i++) {
                var box=neighbor.pieces[i].box;
                for(int x=box.minX>>4;x<=box.maxX>>4;x++)for(int z=box.minZ>>4;z<=box.maxZ>>4;z++) {
                    long key=((long)x<<32)|(z&0xffffffffL); if(chestChunks.contains(key))result.add(key);
                }
            }
        }
        return result;
    }
}
