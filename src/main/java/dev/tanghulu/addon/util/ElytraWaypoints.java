package dev.tanghulu.addon.util;

import dev.tanghulu.addon.util.mushroom.MushroomWaypoints;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.world.MinimapWorld;
import java.io.IOException;
import java.util.List;

/** Optional Xaero bridge. Called on the client thread only after checking the mod. */
public final class ElytraWaypoints {
    private ElytraWaypoints() {}
    public static void saveAll(Object context, List<BlockPos> points) throws IOException {
        var session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null || session.getWorldManager().getAutoWorld() != context)
            throw new IOException("Xaero 世界会话已变化，请重新搜索");
        var world = MushroomWaypoints.dimensionWorld(session, (MinimapWorld) context, World.END);
        var set = world.getCurrentWaypointSet();
        for (var pos : points) {
            boolean exists = false;
            for (var waypoint : set.getWaypoints()) {
                if (waypoint.getX() == pos.getX() && waypoint.getZ() == pos.getZ()
                    && "鞘翅（预测）".equals(waypoint.getName())) { exists = true; break; }
            }
            if (exists) continue;
            var waypoint = new Waypoint(pos.getX(), pos.getY(), pos.getZ(), "鞘翅（预测）", "翅", 5);
            waypoint.setTemporary(false);
            waypoint.setYIncluded(false);
            set.add(waypoint);
        }
        session.getWorldManagerIO().saveWorld(world);
        session.getWaypointSession().setSetChangedTime(System.currentTimeMillis());
    }
}
