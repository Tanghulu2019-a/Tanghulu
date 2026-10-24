package dev.tanghulu.ancientapple.core;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

public final class TargetSelector {
    private TargetSelector() {}
    public static Optional<ChestTarget> next(Collection<ChestTarget> targets, double x, double y, double z, ChestTarget previous) {
        Comparator<ChestTarget> byDistance=Comparator.comparingDouble(t -> t.distanceSquared(x,y,z));
        if (previous != null) {
            Optional<ChestTarget> sameCity=targets.stream().filter(ChestTarget::eligible)
                .filter(t -> t.cityX==previous.cityX && t.cityZ==previous.cityZ).min(byDistance);
            if (sameCity.isPresent()) return sameCity;
        }
        return targets.stream().filter(ChestTarget::eligible).min(byDistance);
    }
}
