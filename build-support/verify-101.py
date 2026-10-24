"""Exercise AutoLog boundaries and the real optional Elytra waypoint writer without a game."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent.parent
work = ROOT/'build/release101-checks'
sources = {}
def stub(package, name, body):
    sources[package.replace('.', '/')+'/'+name+'.java'] = 'package '+package+';\n'+body

stub('net.minecraft.util.math', 'BlockPos', '''
public record BlockPos(int x,int y,int z) {
    public int getX(){return x;} public int getY(){return y;} public int getZ(){return z;}
}''')
stub('net.minecraft.world', 'World', 'public class World { public static final World END = new World(); }')
stub('xaero.common.minimap.waypoints', 'Waypoint', '''
public class Waypoint {
    private final int x,z; private final String name;
    public boolean temporary=true,yIncluded=true;
    public Waypoint(int x,int y,int z,String name,String symbol,int color){this.x=x;this.z=z;this.name=name;}
    public int getX(){return x;} public int getZ(){return z;} public String getName(){return name;}
    public void setTemporary(boolean v){temporary=v;} public void setYIncluded(boolean v){yIncluded=v;}
}''')
stub('xaero.hud.minimap.world','MinimapWorld','''
public class MinimapWorld {
    public final Set set = new Set();
    public Set getCurrentWaypointSet(){return set;}
    public static class Set {
        public final java.util.List<xaero.common.minimap.waypoints.Waypoint> points = new java.util.ArrayList<>();
        public Iterable<xaero.common.minimap.waypoints.Waypoint> getWaypoints(){return points;}
        public void add(xaero.common.minimap.waypoints.Waypoint p){points.add(p);}
    }
}''')
stub('xaero.hud.minimap','BuiltInHudModules','''
public class BuiltInHudModules {
    public static final Module MINIMAP=new Module();
    public static class Module { public Session session; public Session getCurrentSession(){return session;} }
    public static class Session {
        public final Manager manager=new Manager(); public final IO io=new IO(); public final Waypoints wp=new Waypoints();
        public Manager getWorldManager(){return manager;} public IO getWorldManagerIO(){return io;}
        public Waypoints getWaypointSession(){return wp;}
    }
    public static class Manager {
        public xaero.hud.minimap.world.MinimapWorld origin;
        public xaero.hud.minimap.world.MinimapWorld getAutoWorld(){return origin;}
    }
    public static class IO {
        public xaero.hud.minimap.world.MinimapWorld saved;
        public void saveWorld(xaero.hud.minimap.world.MinimapWorld w){saved=w;}
    }
    public static class Waypoints { public long changed; public void setSetChangedTime(long t){changed=t;} }
}''')
stub('dev.tanghulu.addon.util.mushroom','MushroomWaypoints','''
public class MushroomWaypoints {
    public static final xaero.hud.minimap.world.MinimapWorld END = new xaero.hud.minimap.world.MinimapWorld();
    public static xaero.hud.minimap.world.MinimapWorld dimensionWorld(
        xaero.hud.minimap.BuiltInHudModules.Session s,xaero.hud.minimap.world.MinimapWorld origin,net.minecraft.world.World dimension) {
        if(dimension!=net.minecraft.world.World.END)throw new AssertionError("wrong dimension");
        return END;
    }
}''')
stub('dev.tanghulu.qa','Release101Checks','''
import dev.tanghulu.addon.util.LogSafety;
import dev.tanghulu.addon.util.ElytraWaypoints;
import dev.tanghulu.addon.util.mushroom.MushroomWaypoints;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.common.minimap.waypoints.Waypoint;
import net.minecraft.util.math.BlockPos;
import java.util.List;
public class Release101Checks {
    static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception {
        check(LogSafety.lowDurability(100,90,10),"durability inclusive");
        check(!LogSafety.lowDurability(100,89,10),"durability above threshold");
        check(!LogSafety.lowDurability(0,0,100),"indestructible");
        check(!LogSafety.lowDurability(432,388,10),"fractional durability must not round down");
        check(LogSafety.lowDurability(432,389,10),"fractional below");
        check(LogSafety.lowDurability(100,100,0),"zero durability");
        check(LogSafety.lowDurability(Integer.MAX_VALUE,0,100),"no integer overflow");
        check(LogSafety.outsideY(-65,-64,320),"below Y");
        check(LogSafety.outsideY(321,-64,320),"above Y");
        check(!LogSafety.outsideY(-64,-64,320),"Y lower inclusive");
        check(!LogSafety.outsideY(320,-64,320),"Y upper inclusive");
        check(LogSafety.outsideY(321,320,-64),"swapped Y limits");
        check(LogSafety.coordinates(3,4,0,0,5,false),"radius inclusive");
        check(!LogSafety.coordinates(3,4,0,0,5,true),"outside strict");
        check(LogSafety.coordinates(-100,-100,-100,-100,0,false),"negative target zero radius");
        check(LogSafety.coordinates(-106,-100,-100,-100,5,true),"leave negative target");
        check(LogSafety.itemCount(0,0,false),"empty inventory zero lower limit");
        check(LogSafety.itemCount(16,16,false),"count low inclusive");
        check(!LogSafety.itemCount(17,16,false),"count above lower limit");
        check(LogSafety.itemCount(16,16,true),"count upper inclusive");
        check(!LogSafety.itemCount(15,16,true),"count below upper limit");
        var s=new BuiltInHudModules.Session(); BuiltInHudModules.MINIMAP.session=s;
        s.manager.origin=new MinimapWorld(); var end=MushroomWaypoints.END;
        end.set.add(new Waypoint(10,80,20,"user waypoint","U",1));
        var points=new java.util.ArrayList<BlockPos>();
        for(int i=0;i<25;i++)points.add(new BlockPos(i*100,0,-i*100));
        points.add(new BlockPos(10,80,20));
        ElytraWaypoints.saveAll(s.manager.origin,points);
        check(end.set.points.size()==27,"all results beyond chat limit and unrelated point preserved");
        check(s.manager.origin.set.points.isEmpty(),"no overworld waypoints");
        check(s.io.saved==end,"persist End dimension");
        check(s.wp.changed>0,"refresh marker cache");
        check(end.set.points.stream().skip(1).noneMatch(w->w.temporary||w.yIncluded),"permanent horizontal prediction markers");
        ElytraWaypoints.saveAll(s.manager.origin,points);
        check(end.set.points.size()==27,"repeated scan duplicates");
        Object previous=s.manager.origin; s.manager.origin=new MinimapWorld();
        try { ElytraWaypoints.saveAll(previous,points); throw new AssertionError("stale session accepted"); }
        catch(java.io.IOException expected){checks++;}
        BuiltInHudModules.MINIMAP.session=null;
        try { ElytraWaypoints.saveAll(previous,points); throw new AssertionError("missing session accepted"); }
        catch(java.io.IOException expected){checks++;}
        System.out.println("Release 1.0.1 checks passed: "+checks);
    }
}''')
paths=[]
for name, content in sources.items():
    path=work/'src'/name
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(content,encoding='utf-8')
    paths.append(str(path))
for name in ['LogSafety','ElytraWaypoints']:
    paths.append(str(ROOT/f'src/main/java/dev/tanghulu/addon/util/{name}.java'))
classes=work/'classes';classes.mkdir(parents=True,exist_ok=True)
subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(classes),*paths],check=True)
subprocess.run(['java','-cp',str(classes),'dev.tanghulu.qa.Release101Checks'],check=True)
