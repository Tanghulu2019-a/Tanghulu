package dev.tanghulu.ancientapple.core;

import java.util.*;
import static dev.tanghulu.ancientapple.core.GlidePilot.*;

/** Plans actual launch points and destination-height descents, using loaded collision data. */
public final class FlightPlanner {
    public enum Launch { AIR, LEDGE, JUMP }
    public record Plan(Launch launch,Vec release,float yaw,float pitch,Vec landing,Vec cruise,double landingRadius,boolean powered) {}
    private record Entry(Launch launch,Vec release,float yaw,Vec velocity) {}
    private record Cell(int x,int y,int z) {}
    public interface World {
        boolean standingClear(Vec from,Vec to);
        boolean glideClear(Vec from,Vec to);
        boolean supported(Vec feet);
        Optional<Vec> landingAt(int x,int y,int z);
        boolean canLand(Vec from,Vec to,Vec velocity,Vec landing,double radius);
    }
    private FlightPlanner() {}
    public static Optional<Plan> find(Vec from,Vec velocity,float yaw,float pitch,boolean grounded,boolean gliding,
                                     Vec destination,List<Vec> hints,boolean mayBoost,double gravity,World world) {
        return find(from,velocity,yaw,pitch,grounded,gliding,destination,hints,mayBoost,32,gravity,world);
    }
    public static Optional<Plan> find(Vec from,Vec velocity,float yaw,float pitch,boolean grounded,boolean gliding,
                                     Vec destination,List<Vec> hints,boolean mayBoost,int rocketDuration,double gravity,World world) {
        boolean descending=destination.y()<from.y()-2;
        double rise=grounded && !gliding?jumpRise(from,world):0;
        boolean deferLedges=grounded && !gliding && !descending && rise>=.72;
        List<Entry> ledges=grounded && !gliding && !deferLedges?ledges(from,destination,world):List.of();
        if(grounded && !gliding && rise<.72 && ledges.isEmpty())return Optional.empty();
        Set<Cell> checked=new HashSet<>();
        List<Vec> direct=new ArrayList<>();
        if(destination.subtract(from).horizontal()<=104)sample(destination,1,1,4,checked,direct,world);
        rank(direct,from,destination);
        int[] simulations={0};
        var quick=tryLandings(direct,from,velocity,yaw,pitch,grounded,gliding,descending,rise,ledges,mayBoost,rocketDuration,gravity,world,simulations,12);
        if(quick.isPresent())return quick;
        if(deferLedges) {
            ledges=ledges(from,destination,world);
            var edge=tryLandings(direct,from,velocity,yaw,pitch,grounded,gliding,descending,0,ledges,mayBoost,rocketDuration,gravity,world,simulations,24);
            if(edge.isPresent())return edge;
        }
        // Only expand to staged/side landings after nearby direct landings fail.
        return tryLandings(candidates(from,destination,hints,checked,world),from,velocity,yaw,pitch,grounded,gliding,descending,rise,ledges,mayBoost,rocketDuration,gravity,world,simulations,64);
    }
    private static Optional<Plan> tryLandings(List<Vec> landings,Vec from,Vec velocity,float yaw,float pitch,boolean grounded,boolean gliding,
                                             boolean descending,double rise,List<Entry> ledges,boolean mayBoost,int rocketDuration,double gravity,
                                             World world,int[] simulations,int limit) {
        for(Vec landing:landings) {
            List<Entry> entries=new ArrayList<>();
            if(gliding || !grounded)entries.add(new Entry(Launch.AIR,from,yaw,velocity));
            else {
                // Ground controls stop before jumping. Never model a ceiling-clipped
                // 0.12-block hop as a launch: deployment/rocket latency hits the floor.
                Entry jump=rise>=.72?new Entry(Launch.JUMP,from.add(new Vec(0,rise,0)),yaw(from,landing),new Vec(0,-.08,0)):null;
                if(descending)entries.addAll(ledges);
                if(jump!=null)entries.add(jump);
                if(!descending)entries.addAll(ledges);
            }
            for(Entry entry:entries) {
                float launchPitch=entry.launch==Launch.AIR?pitch:descending?0:-25;
                if(!gliding && !deploymentClear(entry.release,entry.velocity,entry.yaw,launchPitch,gravity,world))continue;
                Vec cruise=cruise(entry.release,landing);
                // Lower the cruise waypoint under a ceiling instead of rejecting a usable glide corridor.
                Vec end=landing.add(new Vec(0,.15,0));
                if(!world.glideClear(entry.release,cruise) || !world.glideClear(cruise,end)) {
                    boolean fit=false;
                    for(double y=cruise.y()-.25;y>=Math.max(entry.release.y(),landing.y()+.25);y-=.25) {
                        Vec lower=new Vec(cruise.x(),y,cruise.z());
                        if(world.glideClear(entry.release,lower) && world.glideClear(lower,end)) { cruise=lower; fit=true; break; }
                    }
                    if(!fit)continue;
                }
                double radius=1.25;
                GlidePilot.Space space=new GlidePilot.Space() {
                    public boolean clear(Vec a,Vec b) { return world.glideClear(a,b); }
                    public boolean canLand(Vec a,Vec b,Vec v) { return world.canLand(a,b,v,landing,radius); }
                };
                // Try unpowered descents first. Firework reserve never forbids gliding.
                var control=GlidePilot.choose(entry.release,entry.velocity,entry.yaw,launchPitch,
                    cruise,landing,false,0,!descending && mayBoost,rocketDuration,gravity,space);
                if(!control.safe() && descending && mayBoost)control=GlidePilot.choose(entry.release,entry.velocity,entry.yaw,
                    launchPitch,cruise,landing,false,0,true,rocketDuration,gravity,space);
                if(control.safe()) {
                    var plan=new Plan(entry.launch,entry.release,entry.yaw,launchPitch,landing,cruise,radius,control.rocket() || (!descending && mayBoost));
                    // Short powered flights can pass a 24-tick horizon yet orbit instead of landing.
                    if(landing.subtract(from).horizontal()>=12 || descending || completesShortFlight(plan,entry.velocity,rocketDuration,gravity,space))return Optional.of(plan);
                }
                if(++simulations[0]>=limit)return Optional.empty();
            }
        }
        return Optional.empty();
    }
    private static boolean completesShortFlight(Plan plan,Vec velocity,int duration,double gravity,GlidePilot.Space space) {
        Vec p=plan.release; float yaw=plan.yaw,pitch=plan.pitch; int boost=0; boolean approach=false;
        for(int i=0;i<2;i++) {
            velocity=GlidePilot.step(velocity,yaw,pitch,false,gravity);
            Vec next=p.add(velocity); if(!space.clear(p,next))return false; p=next;
        }
        for(int i=0;i<160;i++) {
            approach|=GlidePilot.approaching(p,velocity,plan.cruise,plan.landing);
            var c=GlidePilot.choose(p,velocity,yaw,pitch,plan.cruise,plan.landing,approach,boost,plan.powered && !approach,duration,gravity,space);
            if(!c.safe())return false;
            if(c.rocket())boost=duration;
            yaw=c.yaw(); pitch=c.pitch(); velocity=GlidePilot.step(velocity,yaw,pitch,boost>0,gravity);
            Vec next=p.add(velocity); if(space.canLand(p,next,velocity))return true;
            if(!space.clear(p,next))return false;
            p=next; if(boost>0)boost--;
        }
        return false;
    }
    public static boolean deploymentClear(Vec position,Vec velocity,float yaw,float pitch,double gravity,World world) {
        // Include two unpowered ticks before a rocket can actually help. This
        // check is repeated against real position/velocity just before deployment.
        for(int i=0;i<2;i++) {
            velocity=GlidePilot.step(velocity,yaw,pitch,false,gravity);
            Vec next=position.add(velocity);
            if(!world.glideClear(position,next) || !world.glideClear(next,next.add(new Vec(0,-.25,0))))return false;
            position=next;
        }
        return true;
    }
    private static double jumpRise(Vec from,World world) {
        double rise=0;
        // Require a full jump, including upright headroom.
        for(double height=.12;height<=1.21;height+=.12) {
            if(!world.standingClear(from,from.add(new Vec(0,height,0))))break;
            rise=height;
        }
        return rise;
    }
    public static Vec cruise(Vec from,Vec landing) {
        if(landing.y()<from.y()-2)return from.add(landing.subtract(from).multiply(.6));
        Vec delta=landing.subtract(from); double h=delta.horizontal(),lead=Math.min(5,h*.15);
        return new Vec(landing.x()-(h>0?delta.x()/h*lead:0),Math.max(from.y()+1.5,landing.y()+2),landing.z()-(h>0?delta.z()/h*lead:0));
    }
    private static List<Entry> ledges(Vec from,Vec goal,World world) {
        List<Entry> result=new ArrayList<>(); float heading=yaw(from,goal);
        for(int angle:new int[]{0,-45,45,-90,90,-135,135,180}) {
            float rotation=heading+angle; Vec direction=GlidePilot.look(rotation,0);
            for(double distance=.5;distance<=6;distance+=.25) {
                Vec point=from.add(direction.multiply(distance));
                if(!world.standingClear(from,point))break;
                if(!world.supported(point) && world.glideClear(point,point.add(new Vec(0,-.6,0)))) {
                    result.add(new Entry(Launch.LEDGE,point,rotation,direction.multiply(.2).add(new Vec(0,-.08,0)))); break;
                }
            }
        }
        return result;
    }
    private static List<Vec> candidates(Vec from,Vec destination,List<Vec> hints,Set<Cell> checked,World world) {
        List<Vec> result=new ArrayList<>();
        Vec delta=destination.subtract(from); double horizontal=delta.horizontal();
        // Chest-level floors are essential for pits, ravines and tall ancient-city rooms.
        if(horizontal<=104)sample(destination,3,1,4,checked,result,world);
        for(Vec hint:hints)if(hint.distance(from)<112)sample(hint,2,2,3,checked,result,world);
        // Staged landings on either side of a wall provide routes that are not
        // confined to the straight line to the chest. Baritone hints can lead
        // temporarily away from the destination to get around larger structures.
        if(horizontal>8)for(int angle:new int[]{-35,35,-70,70})for(int span:new int[]{12,24,40}) {
            Vec direction=look(yaw(from,destination)+angle,0);
            Vec anchor=from.add(direction.multiply(Math.min(horizontal,span)));
            sample(anchor,2,2,4,checked,result,world);
        }
        if(horizontal>.1)for(int span:new int[]{96,64,40,24,12,6}) {
            double length=Math.min(horizontal,span),fraction=length/horizontal;
            Vec anchor=new Vec(from.x()+delta.x()/horizontal*length,from.y()+delta.y()*fraction,from.z()+delta.z()/horizontal*length);
            sample(anchor,2,2,4,checked,result,world);
            if(destination.y()<from.y()-2)sample(new Vec(anchor.x(),destination.y(),anchor.z()),2,2,4,checked,result,world);
            sample(new Vec(anchor.x(),from.y(),anchor.z()),2,2,8,checked,result,world);
        }
        rank(result,from,destination);
        return result;
    }
    private static void rank(List<Vec> result,Vec from,Vec destination) {
        result.removeIf(p -> p.distance(from)<3 || p.distance(from)>400 ||
            (from.distance(destination)<12 && p.distance(destination)>from.distance(destination)*.5) ||
            (p.y()>=from.y()-2 && p.subtract(from).horizontal()<3));
        result.sort(Comparator.comparingDouble(p -> p.distance(destination)+p.distance(from)*.08+Math.max(0,p.y()-from.y())*.5));
    }
    private static void sample(Vec center,int radius,int step,int vertical,Set<Cell> checked,List<Vec> result,World world) {
        for(int dx=-radius;dx<=radius;dx+=step)for(int dz=-radius;dz<=radius;dz+=step)for(int dy=-vertical;dy<=vertical;dy++) {
            Cell c=new Cell((int)Math.floor(center.x())+dx,(int)Math.floor(center.y())+dy,(int)Math.floor(center.z())+dz);
            if(checked.add(c))world.landingAt(c.x,c.y,c.z).ifPresent(result::add);
        }
    }
    private static float yaw(Vec from,Vec to) { return (float)Math.toDegrees(Math.atan2(to.z()-from.z(),to.x()-from.x()))-90; }
}
