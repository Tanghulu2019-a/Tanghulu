package dev.tanghulu.addon.util.mushroom;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.world.MinimapWorld;
import java.io.IOException;
import java.util.List;

/** Loaded only when Xaero Minimap is installed. World Map/XaeroPlus are not required. */
public final class MushroomWaypoints {
    private MushroomWaypoints() {}
    public static Object context() {
        var session=BuiltInHudModules.MINIMAP.getCurrentSession();
        return session==null?null:session.getWorldManager().getAutoWorld();
    }
    public static void savePair(Object context,MushroomSearch.Target target,MushroomVersion version) throws IOException {
        saveAll(context,List.of(target),version);
    }
    public static void saveAll(Object context,List<MushroomSearch.Target> targets,MushroomVersion version) throws IOException {
        MinimapSession session=BuiltInHudModules.MINIMAP.getCurrentSession();
        if(session==null || session.getWorldManager().getAutoWorld()!=context)
            throw new IOException("Xaero 世界会话已变化，请重新搜索");
        MinimapWorld origin=(MinimapWorld)context;
        MinimapWorld overworld=dimensionWorld(session,origin,World.OVERWORLD);
        MinimapWorld nether=dimensionWorld(session,origin,World.NETHER);
        int index=0;
        for(var target:targets) {
            String label="蘑菇岛 #"+(++index)+" ["+version+"]";
            String old="蘑菇岛 ["+version+"]";
            String legacy=old+" "+target.x()+","+target.z();
            renameLegacy(overworld,target.x(),target.z(),legacy,label);
            renameLegacy(nether,target.netherX(),target.netherZ(),legacy+" 下界对应点",label+" 下界对应点");
            renameLegacy(overworld,target.x(),target.z(),old,label);
            renameLegacy(nether,target.netherX(),target.netherZ(),old+" 下界对应点",label+" 下界对应点");
            add(overworld,target.x(),target.z(),label);
            add(nether,target.netherX(),target.netherZ(),label+" 下界对应点");
        }
        session.getWorldManagerIO().saveWorld(overworld);
        session.getWorldManagerIO().saveWorld(nether);
        session.getWaypointSession().setSetChangedTime(System.currentTimeMillis());
    }
    private static void renameLegacy(MinimapWorld world,int x,int z,String oldName,String newName) {
        for(var waypoint:world.getCurrentWaypointSet().getWaypoints())
            if(waypoint.getX()==x && waypoint.getZ()==z && oldName.equals(waypoint.getName()))waypoint.setName(newName);
    }
    private static MinimapWorld dimensionWorld(MinimapSession session,MinimapWorld origin,RegistryKey<World> dimension) throws IOException {
        if(dimension.equals(origin.getDimId()))return origin;
        var manager=session.getWorldManager();
        var path=origin.getContainer().getRoot().getPath().resolve(session.getDimensionHelper().getDimensionDirectoryName(dimension));
        var container=manager.getWorldContainerNullable(path);
        if(container!=null) {
            var connected=container.getFirstWorldConnectedTo(origin);
            if(connected!=null)return connected;
            var worlds=container.getWorldsCopy();
            // Match the same subworld identifier; never guess an unrelated server subworld.
            for(var world:worlds)if(world.getNode().equals(origin.getNode()))return world;
            if(!worlds.isEmpty())throw new IOException("目标维度的 Xaero 子世界尚未关联，请先在 Xaero 中关联主世界与下界地图");
        }
        MinimapWorld created=manager.addWorld(path.resolve(origin.getNode()));
        created.setDimId(dimension);
        return created;
    }
    private static void add(MinimapWorld world,int x,int z,String name) {
        var set=world.getCurrentWaypointSet();
        for(var waypoint:set.getWaypoints())
            if(waypoint.getX()==x && waypoint.getZ()==z) {
                if(name.equals(waypoint.getName()))return;
                // Renumber our own matching-version marker rather than duplicating it on a rescan.
                if("蘑".equals(waypoint.getSymbol()) && name.replaceFirst(" #\\d+", "").equals(waypoint.getName().replaceFirst(" #\\d+", ""))) {
                    waypoint.setName(name); return;
                }
            }
        var waypoint=new Waypoint(x,64,z,name,"蘑",13);
        waypoint.setTemporary(false);
        // The marker is a horizontal destination, not a predicted safe standing/portal height.
        waypoint.setYIncluded(false);
        set.add(waypoint);
    }
}
