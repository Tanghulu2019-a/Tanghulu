package dev.tanghulu.ancientapple.core;

import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.tanghulu.ancientapple.core.GlidePilot.*;

class FlightPlannerTest {
    private static final List<Map<String,Object>> results=new ArrayList<>();
    private static int directLandingQueries;
    private record Cell(int x,int y,int z) {}
    /** Block-volume fixtures use the same body margins and swept checks as the client. */
    private static final class Terrain implements FlightPlanner.World {
        final Set<Cell> blocks=new HashSet<>(); boolean loaded=true; int landingQueries;
        Terrain platform(int top) {
            for(int x=-8;x<=0;x++)for(int z=-4;z<=4;z++)blocks.add(new Cell(x,top-1,z)); return this;
        }
        boolean solid(int x,int y,int z) { return y<0 || blocks.contains(new Cell(x,y,z)); }
        boolean body(Vec p,double w,double h) {
            if(!loaded || Math.abs(p.x())>128 || Math.abs(p.z())>128)return false;
            for(int x=(int)Math.floor(p.x()-w);x<=Math.floor(p.x()+w);x++)
                for(int y=(int)Math.floor(p.y());y<Math.ceil(p.y()+h);y++)
                    for(int z=(int)Math.floor(p.z()-w);z<=Math.floor(p.z()+w);z++)if(solid(x,y,z))return false;
            return true;
        }
        boolean sweep(Vec a,Vec b,double w,double h) {
            int n=Math.max(1,(int)Math.ceil(a.distance(b)*3));
            for(int i=0;i<=n;i++)if(!body(a.add(b.subtract(a).multiply(i/(double)n)),w,h))return false;
            return true;
        }
        public boolean standingClear(Vec a,Vec b) { return sweep(a,b,.32,1.80); }
        public boolean glideClear(Vec a,Vec b) { return sweep(a,b,.34,.65); }
        public boolean supported(Vec p) {
            for(int x=(int)Math.floor(p.x()-.31);x<=Math.floor(p.x()+.31);x++)
                for(int z=(int)Math.floor(p.z()-.31);z<=Math.floor(p.z()+.31);z++)if(solid(x,(int)Math.floor(p.y()-.4),z))return true;
            return false;
        }
        public Optional<Vec> landingAt(int x,int y,int z) {
            landingQueries++;
            Vec p=new Vec(x+.5,y,z+.5);
            return loaded && solid(x,y-1,z) && body(p,.32,1.80)?Optional.of(p):Optional.empty();
        }
        public boolean canLand(Vec a,Vec b,Vec v,Vec landing,double radius) {
            if(v.y()>0 || b.y()>landing.y()+.08 || b.y()<landing.y()-.7 || Math.hypot(b.x()-landing.x(),b.z()-landing.z())>radius)return false;
            Vec contact=new Vec(b.x(),landing.y()+.02,b.z());
            if(landingAt((int)Math.floor(b.x()),(int)landing.y(),(int)Math.floor(b.z())).isEmpty() || !glideClear(a,contact))return false;
            double speed=v.horizontal(); return speed<=.1 || standingClear(contact,contact.add(new Vec(v.x()/speed*Math.min(4,speed*3),0,v.z()/speed*Math.min(4,speed*3))));
        }
    }
    private FlightPlanner.Plan route(String name,Terrain world,Vec from,Vec velocity,boolean ground,boolean boost,Vec goal) {
        var plan=FlightPlanner.find(from,velocity,-90,0,ground,false,goal,List.of(),boost,.08,world);
        assertTrue(plan.isPresent(),name+" did not produce a route"); return plan.orElseThrow();
    }
    private void fly(String name,Terrain world,FlightPlanner.Plan plan,Vec initialVelocity) {
        Vec p=plan.release(),v=initialVelocity; float yaw=plan.yaw(),pitch=plan.pitch(); boolean approach=false; int boost=0;
        // Deployment and the first firework are not instantaneous in the client.
        for(int i=0;i<2;i++) {
            Vec nextVelocity=step(v,yaw,pitch,false,.08),next=p.add(nextVelocity);
            assertTrue(world.glideClear(p,next),name+" collided during deployment");
            p=next; v=nextVelocity;
        }
        for(int tick=0;tick<400;tick++) {
            if(GlidePilot.approaching(p,v,plan.cruise(),plan.landing()))approach=true;
            final Vec landing=plan.landing();
            var c=choose(p,v,yaw,pitch,plan.cruise(),landing,approach,boost,plan.powered() && !approach,32,.08,new Space() {
                public boolean clear(Vec a,Vec b) { return world.glideClear(a,b); }
                public boolean canLand(Vec a,Vec b,Vec v) { return world.canLand(a,b,v,landing,plan.landingRadius()); }
            });
            assertTrue(c.safe(),name+" unsafe at tick "+tick+" position="+p+" velocity="+v+" control="+c);
            if(c.rocket())boost=32;
            yaw=c.yaw(); pitch=c.pitch(); Vec nextVelocity=step(v,yaw,pitch,boost>0,.08),next=p.add(nextVelocity);
            if(world.canLand(p,next,nextVelocity,landing,plan.landingRadius())) {
                double horizontalError=Math.hypot(next.x()-landing.x(),next.z()-landing.z());
                assertTrue(horizontalError<=1.25,name+" missed the landing target");
                results.add(Map.of("name",name,"launch",plan.launch().name(),"ticks",tick+1,"powered",plan.powered(),"landingError",next.distance(landing),"horizontalError",horizontalError)); return;
            }
            assertTrue(world.glideClear(p,next),name+" collided at tick "+tick);
            p=next; v=nextVelocity; if(boost>0)boost--;
        }
        fail(name+" never landed: "+p+" target="+plan.landing());
    }
    @Test void descendsFortyBlocksFromAnEdgeWithoutFireworks() {
        var world=new Terrain().platform(40); var from=new Vec(.5,40,.5);
        var plan=route("ledge-40",world,from,new Vec(0,0,0),true,false,new Vec(20.5,0,.5));
        assertEquals(FlightPlanner.Launch.LEDGE,plan.launch()); assertFalse(plan.powered()); assertEquals(0,plan.landing().y());
        fly("ledge-40",world,plan,look(plan.yaw(),0).multiply(.2).add(new Vec(0,-.08,0)));
    }
    @Test void descendsOneHundredTwentyBlocksInsteadOfDiggingAroundAPit() {
        var world=new Terrain().platform(120); var from=new Vec(.5,120,.5);
        var plan=route("ledge-120",world,from,new Vec(0,0,0),true,true,new Vec(40.5,0,.5));
        assertEquals(FlightPlanner.Launch.LEDGE,plan.launch()); assertFalse(plan.powered());
        fly("ledge-120",world,plan,look(plan.yaw(),0).multiply(.2).add(new Vec(0,-.08,0)));
    }
    @Test void canOpenElytraWhileAlreadyFallingWithoutFireworks() {
        var world=new Terrain(); var velocity=new Vec(.2,-.7,0);
        var plan=route("falling-40",world,new Vec(.5,40,.5),velocity,false,false,new Vec(20.5,0,.5));
        assertEquals(FlightPlanner.Launch.AIR,plan.launch()); fly("falling-40",world,plan,velocity);
    }
    @Test void acceptsVerticalDescentRatherThanRequiringHorizontalDistance() {
        var world=new Terrain(); var velocity=new Vec(0,-.8,0);
        var plan=route("vertical-40",world,new Vec(.5,40,.5),velocity,false,false,new Vec(.5,0,.5));
        fly("vertical-40",world,plan,velocity);
    }
    @Test void jumpsAndUsesFireworksOnALevelLoadedRoute() {
        var world=new Terrain();
        var plan=route("level-60",world,new Vec(.5,0,.5),new Vec(0,0,0),true,true,new Vec(60.5,0,.5));
        assertEquals(FlightPlanner.Launch.JUMP,plan.launch()); assertTrue(plan.powered());
        fly("level-60",world,plan,new Vec(0,-.08,0));
    }
    @Test void levelTransfersLandAccuratelyAcrossShortAndLongDistances() {
        for(int distance:new int[]{16,32,90}) {
            var world=new Terrain(); String name="level-"+distance;
            var plan=route(name,world,new Vec(.5,0,.5),new Vec(0,0,0),true,true,new Vec(distance+.5,0,.5));
            assertTrue(plan.landingRadius()<=1.25,"long routes must not relax touchdown accuracy");
            fly(name,world,plan,new Vec(0,-.08,0));
        }
    }
    @Test void directRoutesAvoidExpandingThousandsOfSideCandidates() {
        var world=new Terrain();
        route("direct-planning",world,new Vec(.5,0,.5),new Vec(0,0,0),true,true,new Vec(60.5,0,.5));
        directLandingQueries=world.landingQueries;
        assertTrue(world.landingQueries<=81,"direct route queried "+world.landingQueries+" landing blocks");
    }
    @Test void shortLevelTransferIsCheckedInsteadOfRejectedByDistance() {
        var world=new Terrain();
        var plan=route("short-level-10",world,new Vec(.5,0,.5),new Vec(0,0,0),true,true,new Vec(10.5,0,.5));
        assertTrue(plan.landing().distance(new Vec(10.5,0,.5))<=5,"short route must make useful progress: "+plan);
        fly("short-level-10",world,plan,new Vec(0,-.08,0));
    }
    @Test void lowersCruiseUnderAThreeBlockCeiling() {
        var world=new Terrain();
        for(int x=-4;x<=40;x++)for(int z=-5;z<=5;z++)world.blocks.add(new Cell(x,3,z));
        var plan=route("three-block-ceiling",world,new Vec(.5,0,.5),new Vec(0,0,0),true,true,new Vec(24.5,0,.5));
        assertTrue(plan.cruise().y()+.65<3);
        fly("three-block-ceiling",world,plan,new Vec(0,-.08,0));
    }
    @Test void usesALedgeBeforeJumpingUnderALowCeiling() {
        var world=new Terrain().platform(40);
        for(int x=-8;x<=2;x++)for(int z=-4;z<=4;z++)world.blocks.add(new Cell(x,42,z));
        var plan=route("low-ceiling-ledge",world,new Vec(.5,40,.5),new Vec(0,0,0),true,false,new Vec(20.5,0,.5));
        assertEquals(FlightPlanner.Launch.LEDGE,plan.launch());
        fly("low-ceiling-ledge",world,plan,look(plan.yaw(),0).multiply(.2).add(new Vec(0,-.08,0)));
    }
    @Test void rejectsGroundSkimmingJumpUnderATwoBlockCeiling() {
        var world=new Terrain();
        for(int x=-4;x<=90;x++)for(int z=-4;z<=4;z++)world.blocks.add(new Cell(x,2,z));
        assertTrue(FlightPlanner.find(new Vec(.5,0,.5),new Vec(0,0,0),-90,0,true,false,
            new Vec(60.5,0,.5),List.of(),true,.08,world).isEmpty());
        results.add(Map.of("name","two-block-tunnel","rejected",true));
    }
    @Test void refusesDeploymentWhenActualFallVelocityWouldHitGround() {
        var world=new Terrain();
        assertFalse(FlightPlanner.deploymentClear(new Vec(.5,.4,.5),new Vec(0,-.6,0),-90,-25,.08,world));
        assertTrue(FlightPlanner.deploymentClear(new Vec(.5,1.2,.5),new Vec(0,-.08,0),-90,-25,.08,world));
    }
    @Test void plansASideLandingAroundAWall() {
        var world=new Terrain();
        for(int x=10;x<=12;x++)for(int y=0;y<=12;y++)for(int z=-5;z<=5;z++)world.blocks.add(new Cell(x,y,z));
        var plan=route("wall-side-stage",world,new Vec(.5,0,.5),new Vec(0,0,0),true,true,new Vec(60.5,0,.5));
        assertTrue(Math.abs(plan.landing().z()-.5)>5,"must choose a side landing, not charge at the wall");
        fly("wall-side-stage",world,plan,new Vec(0,-.08,0));
    }
    @Test void blockedOrUnloadedSpaceDoesNotPermitLaunch() {
        var world=new Terrain().platform(40);
        for(int x=-9;x<=7;x++)for(int z=-7;z<=7;z++)for(int y=40;y<=42;y++)world.blocks.add(new Cell(x,y,z));
        assertTrue(FlightPlanner.find(new Vec(.5,40,.5),new Vec(0,0,0),-90,0,true,false,new Vec(20.5,0,.5),List.of(),true,.08,world).isEmpty());
        world=new Terrain(); world.loaded=false;
        assertTrue(FlightPlanner.find(new Vec(.5,40,.5),new Vec(0,-.8,0),-90,0,false,false,new Vec(20.5,0,.5),List.of(),true,.08,world).isEmpty());
        results.add(Map.of("name","blocked-and-unloaded","rejected",true));
    }
    @AfterAll static void report() throws Exception {
        Path file=Path.of("build/reports/flight-routes.json"); Files.createDirectories(file.getParent());
        Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed",results.size()==14,"cases",results,"directRouteLandingQueries",directLandingQueries,"scope","Core planner and receding-horizon controls including two unpowered deployment ticks with loaded block-volume fixtures; not a live client flight.")));
    }
}
