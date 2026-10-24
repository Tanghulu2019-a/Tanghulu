"""Run the real cleaner against a fake Xaero session; no game data is touched."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parent.parent
work = root / 'build/waypoint-cleaner-check'
sources = {}

def stub(package, name, body):
    sources[package.replace('.', '/') + '/' + name + '.java'] = f'package {package};\n{body}'

stub('net.minecraft.client', 'MinecraftClient', '''
public class MinecraftClient {
    public static final MinecraftClient INSTANCE = new MinecraftClient();
    public java.io.File runDirectory;
    public static MinecraftClient getInstance() { return INSTANCE; }
}''')
stub('meteordevelopment.meteorclient', 'MeteorClient', 'public class MeteorClient { public static java.io.File FOLDER; }')
stub('meteordevelopment.meteorclient.utils', 'Utils', 'public class Utils { public static String getFileWorldName() { return "current-server"; } }')
stub('meteordevelopment.meteorclient.systems.modules', 'Modules', '''
public class Modules {
    private static final Modules INSTANCE = new Modules();
    public final java.util.List<Object> modules = new java.util.ArrayList<>();
    public static Modules get() { return INSTANCE; }
    public java.util.List<Object> getAll() { return modules; }
}''')
stub('xaero.common.minimap.waypoints', 'Waypoint', 'public record Waypoint(String name) {}')
stub('xaero.hud.minimap.waypoint.set', 'WaypointSet', '''
public class WaypointSet {
    public final java.util.List<xaero.common.minimap.waypoints.Waypoint> points = new java.util.ArrayList<>();
    public boolean isEmpty() { return points.isEmpty(); }
    public void addTo(java.util.List<xaero.common.minimap.waypoints.Waypoint> dest) { dest.addAll(points); }
    public void clear() { points.clear(); }
    public void addAll(java.util.Collection<xaero.common.minimap.waypoints.Waypoint> p) { points.addAll(p); }
}''')
stub('xaero.hud.minimap.world', 'MinimapWorld', '''
public class MinimapWorld {
    public final java.util.List<xaero.hud.minimap.waypoint.set.WaypointSet> sets = new java.util.ArrayList<>();
    public Container container;
    public java.nio.file.Path file;
    public Iterable<xaero.hud.minimap.waypoint.set.WaypointSet> getIterableWaypointSets() { return sets; }
    public Container getContainer() { return container; }
    public static class Container {
        public final java.util.List<MinimapWorld> worlds = new java.util.ArrayList<>();
        public Container getRoot() { return this; }
        public Iterable<MinimapWorld> getAllWorldsIterable() { return worlds; }
    }
}''')
stub('xaero.hud.minimap', 'BuiltInHudModules', '''
public class BuiltInHudModules {
    public static final Module MINIMAP = new Module();
    public static class Module { public Session session; public Session getCurrentSession() { return session; } }
    public static class Session {
        public final Manager manager = new Manager(); public final IO io = new IO(); public final Waypoints wp = new Waypoints();
        public Manager getWorldManager() { return manager; } public IO getWorldManagerIO() { return io; }
        public Waypoints getWaypointSession() { return wp; }
    }
    public static class Manager { public xaero.hud.minimap.world.MinimapWorld origin;
        public xaero.hud.minimap.world.MinimapWorld getAutoWorld() { return origin; } }
    public static class Waypoints { public long changed; public void setSetChangedTime(long t) { changed=t; } }
    public static class IO {
        public boolean failClear;
        public java.nio.file.Path getWorldFile(xaero.hud.minimap.world.MinimapWorld w) { return w.file; }
        public void saveWorld(xaero.hud.minimap.world.MinimapWorld w) throws java.io.IOException {
            boolean empty = w.sets.stream().allMatch(s -> s.isEmpty());
            if (failClear && empty) { failClear=false; throw new java.io.IOException("save failure"); }
            StringBuilder data = new StringBuilder();
            for (var s:w.sets) for (var p:s.points) data.append(p.name()).append('\\n');
            java.nio.file.Files.writeString(w.file,data);
        }
    }
}''')
sources['CleanerAudit.java'] = '''
import java.nio.file.*;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.common.minimap.waypoints.Waypoint;
import dev.tanghulu.addon.util.XaeroWaypointCleaner;
public class CleanerAudit {
    static void check(boolean b,String why) { if(!b)throw new AssertionError(why); }
    static MinimapWorld world(Path dir,String name,MinimapWorld.Container c) throws Exception {
        var w=new MinimapWorld(); w.container=c; c.worlds.add(w); w.file=dir.resolve(name+".txt");
        for(int i=0;i<2;i++) { var s=new WaypointSet();s.points.add(new Waypoint(name+i));w.sets.add(s); }
        return w;
    }
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory(Path.of(args[0]),"scenario-");
        net.minecraft.client.MinecraftClient.INSTANCE.runDirectory=dir.toFile();
        var session=new BuiltInHudModules.Session(); BuiltInHudModules.MINIMAP.session=session;
        var root=new MinimapWorld.Container();
        var overworld=world(dir,"overworld",root); var nether=world(dir,"nether",root);
        var unrelated=world(dir,"other-server",new MinimapWorld.Container());
        session.manager.origin=overworld;
        session.io.saveWorld(unrelated);
        session.io.failClear=true;
        try { XaeroWaypointCleaner.clearCurrentServer(); throw new AssertionError("expected save failure"); }
        catch(java.io.IOException expected) {}
        check(overworld.sets.get(0).points.size()==1 && nether.sets.get(1).points.size()==1,"rollback lost points");
        check(Files.readString(overworld.file).contains("overworld0"),"disk rollback failed");
        var result=XaeroWaypointCleaner.clearCurrentServer();
        check(result.count()==4,"did not clear every dimension and set");
        check(overworld.sets.stream().allMatch(s->s.isEmpty()) && nether.sets.stream().allMatch(s->s.isEmpty()),"points remain");
        check(overworld.sets.size()==2,"removed groups");
        check(Files.readString(overworld.file).isEmpty() && Files.readString(nether.file).isEmpty(),"not persisted");
        try(var files=Files.walk(dir)) {
            check(files.filter(Files::isRegularFile).count()==3,"unexpected backup or extra files");
        }
        check(!Files.exists(dir.resolve("tanghulu/waypoint-backups")),"created backup directory");
        check(unrelated.sets.get(0).points.size()==1,"modified another server");
        check(Files.readString(unrelated.file).contains("other-server0"),"modified another server on disk");
        check(session.wp.changed>0,"no UI refresh");
        check(XaeroWaypointCleaner.clearCurrentServer().count()==0,"repeat clear not empty");
        BuiltInHudModules.MINIMAP.session=null;
        try { XaeroWaypointCleaner.clearCurrentServer();throw new AssertionError("expected missing session failure"); }
        catch(java.io.IOException expected) {}
        System.out.println("Waypoint cleaner: no backup files, save rollback, all dimensions/groups, persistence, isolation, repeat and missing session PASS");
    }
}'''

for name, body in sources.items():
    path = work / 'src' / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(body, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
classes = work / 'classes'
classes.mkdir(parents=True, exist_ok=True)
subprocess.run([str(java / 'javac'), '--release', '21', '-encoding', 'UTF-8', '-d', str(classes),
                *map(str, (work / 'src').rglob('*.java')),
                str(root / 'src/main/java/dev/tanghulu/addon/util/XaeroWaypointCleaner.java')], check=True)
subprocess.run([str(java / 'java'), '-cp', str(classes), 'CleanerAudit', str(work)], check=True)
