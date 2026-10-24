package dev.tanghulu.ancientapple.core;

import java.util.ArrayList;
import java.util.List;
import static dev.tanghulu.ancientapple.core.GlidePilot.Vec;

/** Aim inside the chest outline; the world raycast supplies the actual hit face. */
public final class ChestReach {
    private ChestReach() {}
    public static boolean reachable(Vec eye,Vec hit,double range) {
        return range>.15 && eye.distance(hit)<=range-.15;
    }
    public static List<Vec> aimPoints(int x,int y,int z) {
        List<Vec> points=new ArrayList<>();
        points.add(new Vec(x+.5,y+.5,z+.5));
        for(double height:new double[]{.15,.5,.8})
            for(double[] offset:new double[][]{{.08,.5},{.92,.5},{.5,.08},{.5,.92}})
                points.add(new Vec(x+offset[0],y+height,z+offset[1]));
        return points;
    }
}
