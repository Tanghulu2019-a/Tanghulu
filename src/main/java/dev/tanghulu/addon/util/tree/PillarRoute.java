package dev.tanghulu.addon.util.tree;

import java.util.*;

/** Only server-confirmed placements enter the recovery route. Natural ground is never recorded. */
public final class PillarRoute<T> {
    public record Step<T>(TreePlan.Pos pos, T block) {}
    private final ArrayDeque<Step<T>> steps = new ArrayDeque<>();
    public void confirm(TreePlan.Pos pos, T block) {
        Objects.requireNonNull(block);
        Step<T> top = steps.peekLast();
        if (top != null && !pos.equals(top.pos.offset(0, 1, 0))) {
            throw new IllegalArgumentException("Pillar route must be a contiguous vertical column");
        }
        steps.addLast(new Step<>(pos, block));
    }
    public Step<T> top() { return steps.peekLast(); }
    public int size() { return steps.size(); }
    public boolean isEmpty() { return steps.isEmpty(); }
    public void landedAfterRemoval() { if (!steps.isEmpty()) steps.removeLast(); }
    public void clear() { steps.clear(); }
    public int prune(java.util.function.Predicate<Step<T>> noLongerOwned) {
        int before = steps.size();
        steps.removeIf(noLongerOwned);
        return before - steps.size();
    }
    public List<Step<T>> snapshot() { return List.copyOf(steps); }
}
