package dev.tanghulu.ancientapple.prediction;

import com.seedfinding.mccore.util.block.BlockMirror;
import com.seedfinding.mccore.util.pos.BPos;
import kludwisz.ancientcity.AncientCityGenerator;
import kludwisz.ancientcity.structures.AncientCityStructureLoot;
import kludwisz.util.DecoratorRand;
import java.util.*;

public final class LayoutPredictor {
    public record Chest(int x,int y,int z,long seed,boolean terrainRequired) {}
    private LayoutPredictor() {}
    public static List<Chest> chests(AncientCityGenerator layout,long seed) {
        Set<Long> tainted=new HashSet<>(); Map<Long,DecoratorRand> randoms=new HashMap<>(); List<Chest> result=new ArrayList<>();
        for(int i=0;i<layout.piecesLen;i++) {
            var piece=layout.pieces[i];
            if(piece.id==59) { tainted.add(chunk(piece.pos.x,piece.pos.z)); continue; }
            // Vanilla seeds every LootableInventory, including the fixed ordinary-apple chest.
            if(piece.id==7) {
                BPos p=piece.pos.toImmutable().add(new BPos(9,8,20).transform(BlockMirror.NONE,piece.rotation,BPos.ORIGIN));
                random(randoms,seed,p.getX(),p.getZ()).nextLong();
            }
            for(var descriptor:AncientCityStructureLoot.STRUCTURE_LOOT_V2.get(piece.id)) {
                BPos p=piece.pos.toImmutable().add(descriptor.getFirst().transform(BlockMirror.NONE,piece.rotation,BPos.ORIGIN));
                long lootSeed=random(randoms,seed,p.getX(),p.getZ()).nextLong();
                if(piece.id!=27)result.add(new Chest(p.getX(),p.getY(),p.getZ(),lootSeed,tainted.contains(chunk(p.getX(),p.getZ()))));
            }
        }
        return result;
    }
    private static DecoratorRand random(Map<Long,DecoratorRand> map,long seed,int x,int z) {
        return map.computeIfAbsent(chunk(x,z),key -> { DecoratorRand r=new DecoratorRand(); long population=r.getPopulationSeed(seed,Math.floorDiv(x,16)*16,Math.floorDiv(z,16)*16); r.setDecoratorSeed(population,0,7); return r; });
    }
    public static long chunk(int x,int z) { return ((long)Math.floorDiv(x,16)<<32)|(Math.floorDiv(z,16)&0xffffffffL); }
}
