package dev.tanghulu.ancientapple.qa;

import com.seedfinding.mccore.rand.ChunkRand;
import kludwisz.ancientcity.AncientCityGenerator;
import kludwisz.ancientcity.structures.AncientCityStructureLoot;

public final class LayoutDebug {
    public static void main(String[] args) {
        var g=new AncientCityGenerator(); g.generate(0,50,79,new ChunkRand());
        for(int i=0;i<g.piecesLen;i++) {
            var p=g.pieces[i];
            if(p.id==59 || !AncientCityStructureLoot.STRUCTURE_LOOT_V2.get(p.id).isEmpty())
                System.out.println(i+" "+p.id+" "+p.getName()+" "+p.pos.x+","+p.pos.y+","+p.pos.z+" box="+p.box);
        }
    }
}
