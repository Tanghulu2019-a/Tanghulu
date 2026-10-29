package dev.tanghulu.addon.util.tree;

import java.util.*;

/** A bounded snapshot: walking never moves the harvest boundary or adds new trees. */
public final class TreePlan {
    public record Pos(int x, int y, int z) {
        public Pos offset(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }
    }

    private final Pos origin;
    private final int radius, below, above;
    private final Map<Pos, TreeSpecies> logs = new HashMap<>();
    private final Map<Pos, TreeSpecies> leaves = new HashMap<>();

    public TreePlan(Pos origin, int radius, int below, int above) {
        this.origin = Objects.requireNonNull(origin);
        if (radius < 1 || below < 0 || above < 1) throw new IllegalArgumentException("Invalid tree bounds");
        this.radius = radius; this.below = below; this.above = above;
    }

    public boolean contains(Pos p) {
        long dx = (long) p.x - origin.x, dz = (long) p.z - origin.z;
        return dx * dx + dz * dz <= (long) radius * radius
            && p.y >= origin.y - below && p.y <= origin.y + above;
    }

    public void addLog(Pos p, TreeSpecies species) { if (contains(p)) logs.put(p, species); }
    public void addNaturalLeaves(Pos p, TreeSpecies species) { leaves.put(p, species); }
    public int logCount() { return logs.size(); }
    public Set<Pos> naturalLeaves() { return Set.copyOf(leaves.keySet()); }

    /** Connected logs need matching natural foliage. Stripped logs/wood are never candidates. */
    public List<List<Pos>> trees() {
        Set<Pos> visited = new HashSet<>();
        List<List<Pos>> result = new ArrayList<>();
        for (var entry : logs.entrySet()) {
            if (!visited.add(entry.getKey())) continue;
            TreeSpecies species = entry.getValue();
            ArrayDeque<Pos> queue = new ArrayDeque<>();
            List<Pos> component = new ArrayList<>();
            queue.add(entry.getKey());
            boolean foliage = false;
            while (!queue.isEmpty()) {
                Pos p = queue.removeFirst();
                component.add(p);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Pos next = p.offset(dx, dy, dz);
                        foliage |= leaves.get(next) == species;
                        if (logs.get(next) == species && visited.add(next)) queue.addLast(next);
                    }
                }
            }
            if (foliage && component.size() >= 2) {
                component.sort(Comparator.comparingLong(this::distance));
                result.add(List.copyOf(component));
            }
        }
        result.sort(Comparator.comparingLong(tree -> distance(tree.getFirst())));
        return List.copyOf(result);
    }

    private long distance(Pos p) {
        long dx = (long) p.x - origin.x, dy = (long) p.y - origin.y, dz = (long) p.z - origin.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public List<Pos> targets() {
        return trees().stream().flatMap(Collection::stream).sorted(Comparator.comparingLong(this::distance)).toList();
    }
}
