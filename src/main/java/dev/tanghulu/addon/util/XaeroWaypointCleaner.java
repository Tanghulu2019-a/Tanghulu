package dev.tanghulu.addon.util;

import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loaded only after checking for Xaero Minimap. No World Map or XaeroPlus dependency. */
public final class XaeroWaypointCleaner {
    private XaeroWaypointCleaner() {}

    public record Result(int count, boolean stashRecordsCleared) {}

    public static Result clearCurrentServer() throws IOException {
        var session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) throw new IOException("Xaero 世界会话尚未就绪");
        var origin = session.getWorldManager().getAutoWorld();
        if (origin == null) throw new IOException("Xaero 尚未识别当前世界");
        // The automatic world determines the active server even if the waypoint GUI
        // is browsing an unrelated server. Its root includes every dimension/subworld.
        Map<MinimapWorld, Map<WaypointSet, List<Waypoint>>> snapshots = new LinkedHashMap<>();
        int count = 0;
        for (var world : origin.getContainer().getRoot().getAllWorldsIterable()) {
            Map<WaypointSet, List<Waypoint>> sets = new LinkedHashMap<>();
            for (var set : world.getIterableWaypointSets()) {
                if (set.isEmpty()) continue;
                List<Waypoint> points = new ArrayList<>();
                set.addTo(points);
                sets.put(set, points);
                count += points.size();
            }
            if (!sets.isEmpty()) snapshots.put(world, sets);
        }
        var stashRecords = StashSearchRecords.snapshotCurrentWorld();

        // Snapshots exist only for this invocation's error recovery, never on disk.
        try {
            stashRecords.clearFiles();
            for (var entry : snapshots.entrySet()) {
                for (var set : entry.getValue().keySet()) set.clear();
                session.getWorldManagerIO().saveWorld(entry.getKey());
            }
        } catch (IOException | RuntimeException failure) {
            stashRecords.restoreFiles(failure);
            // Restore all live sets first, then attempt every disk rollback independently.
            for (var sets : snapshots.values()) {
                for (var entry : sets.entrySet()) {
                    entry.getKey().clear();
                    entry.getKey().addAll(entry.getValue());
                }
            }
            for (var world : snapshots.keySet()) {
                try { session.getWorldManagerIO().saveWorld(world); }
                catch (IOException | RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        } finally {
            session.getWaypointSession().setSetChangedTime(System.currentTimeMillis());
        }
        stashRecords.clearCache();
        return new Result(count, stashRecords.hasSavedFiles());
    }
}
