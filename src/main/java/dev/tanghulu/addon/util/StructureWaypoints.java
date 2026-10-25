package dev.tanghulu.addon.util;

import dev.tanghulu.addon.util.mushroom.MushroomWaypoints;
import net.minecraft.world.World;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.world.MinimapWorld;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

/** Optional map API stays isolated from module registration. Client thread only. */
public final class StructureWaypoints {
    private StructureWaypoints() {}
    public record Point(int x, int y, int z, String name, String dimension) {}
    public static Object capture() {
        var session = BuiltInHudModules.MINIMAP.getCurrentSession();
        return session == null ? null : session.getWorldManager().getAutoWorld();
    }
    public static void saveAll(Object context, List<Point> points) throws IOException {
        var session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null || session.getWorldManager().getAutoWorld() != context)
            throw new IOException("Xaero 世界会话已变化，请重新搜索");
        for (String dimension : List.of("overworld", "the_nether", "the_end")) {
            var batch = points.stream().filter(p -> p.dimension().equals(dimension)).toList();
            if (batch.isEmpty()) continue;
            var key = switch (dimension) {
                case "the_nether" -> World.NETHER;
                case "the_end" -> World.END;
                default -> World.OVERWORLD;
            };
            var world = MushroomWaypoints.dimensionWorld(session, (MinimapWorld) context, key);
            var set = world.getCurrentWaypointSet();
            var existing = new HashSet<String>();
            for (var waypoint : set.getWaypoints())
                existing.add(waypoint.getName() + "|" + waypoint.getX() + "|" + waypoint.getZ());
            for (var point : batch) {
                String name = point.name() + "（结构预测）";
                if (!existing.add(name + "|" + point.x() + "|" + point.z())) continue;
                var waypoint = new Waypoint(point.x(), point.y(), point.z(), name, "构", 9);
                waypoint.setTemporary(false);
                waypoint.setYIncluded(false);
                set.add(waypoint);
            }
            session.getWorldManagerIO().saveWorld(world);
        }
        session.getWaypointSession().setSetChangedTime(System.currentTimeMillis());
    }
}
