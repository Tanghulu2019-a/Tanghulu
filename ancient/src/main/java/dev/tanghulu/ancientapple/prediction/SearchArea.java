package dev.tanghulu.ancientapple.prediction;

import kludwisz.ancientcity.AncientCityGenerator;
import java.util.*;

/** Search chest coordinates, including cities whose start lies outside the circle. */
public record SearchArea(int centerX,int centerZ,int radius) {
    private static final int REGION_SIZE=384;
    // Layout pieces lie within 116 blocks of the root center. 256 also covers the
    // anchor-to-center offset and the diagonal, with a conservative margin.
    private static final int CITY_REACH=256;
    public SearchArea { if(radius<0)throw new IllegalArgumentException("Negative radius"); }
    public boolean contains(int x,int z) {
        double dx=(double)x-centerX,dz=(double)z-centerZ;
        return dx*dx+dz*dz<=(double)radius*radius;
    }
    public boolean mayContainCity(int anchorX,int anchorZ) {
        double dx=(double)anchorX-centerX,dz=(double)anchorZ-centerZ,reach=(double)radius+CITY_REACH;
        return dx*dx+dz*dz<=reach*reach;
    }
    public boolean intersects(AncientCityGenerator layout) {
        for(int i=0;i<layout.piecesLen;i++) {
            var box=layout.pieces[i].box;
            if(contains(Math.max(box.minX,Math.min(centerX,box.maxX)),Math.max(box.minZ,Math.min(centerZ,box.maxZ))))return true;
        }
        return false;
    }
    public List<int[]> regions() {
        int minX=Math.floorDiv(centerX-radius-CITY_REACH,REGION_SIZE),maxX=Math.floorDiv(centerX+radius+CITY_REACH,REGION_SIZE);
        int minZ=Math.floorDiv(centerZ-radius-CITY_REACH,REGION_SIZE),maxZ=Math.floorDiv(centerZ+radius+CITY_REACH,REGION_SIZE);
        List<int[]> result=new ArrayList<>();
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)result.add(new int[]{x,z});
        result.sort(Comparator.comparingDouble(p -> Math.pow(p[0]*384.0+192-centerX,2)+Math.pow(p[1]*384.0+192-centerZ,2)));
        return result;
    }
}
