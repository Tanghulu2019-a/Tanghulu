package dev.tanghulu.addon.util.threec;

import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure distance selection, shared with the boundary regression checks. */
public final class NearbyPlayerSelector {
    public static final double RANGE = 200;
    private NearbyPlayerSelector() {}

    public record Candidate(UUID id, String name, double x, double y, double z, boolean spectator) {}

    public static List<Candidate> within(UUID self, double x, double y, double z, double range, Iterable<Candidate> players) {
        if (!Double.isFinite(range) || range < 0) throw new IllegalArgumentException("Invalid range");
        List<Candidate> result = new ArrayList<>();
        for (Candidate player : players) {
            double distance = distanceSquared(player, x, y, z);
            if (!player.id().equals(self) && !player.spectator()
                && Double.isFinite(distance) && distance <= range * range) result.add(player);
        }
        result.sort(Comparator.comparingDouble((Candidate p) -> distanceSquared(p, x, y, z)).thenComparing(Candidate::name));
        return result;
    }

    private static double distanceSquared(Candidate player, double x, double y, double z) {
        double dx = player.x() - x, dy = player.y() - y, dz = player.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public static Candidate nearest(UUID self, double x, double y, double z, Iterable<Candidate> players) {
        Candidate nearest = null;
        double best = RANGE * RANGE;
        for (Candidate player : players) {
            if (player.id().equals(self) || player.spectator()) continue;
            double dx = player.x() - x, dy = player.y() - y, dz = player.z() - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (!Double.isFinite(distance) || distance > best) continue;
            if (nearest == null || distance < best
                || (distance == best && player.name().compareTo(nearest.name()) < 0)) {
                nearest = player;
                best = distance;
            }
        }
        return nearest;
    }
}
