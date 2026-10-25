package dev.tanghulu.ancientapple.core;

import java.util.*;

/** Keeps vanilla estimates separate from observations made through a server container. */
public final class ChestSurvey {
    private ChestSurvey() {}

    public static void prepare(ChestTarget target,boolean enabled) {
        // Ignore the old setting even when migrating a cache or called by an older integration.
        target.surveyRequired=false;
        target.refreshLoot();
        if(target.isBlacklisted())target.markCollected();
    }

    public static ChestTarget merge(ChestTarget prediction,ChestTarget old,boolean survey) {
        prepare(prediction,survey);
        if(old==null)return prediction;
        prediction.openedWithApples=old.openedWithApples;
        if(old.isBlacklisted()) {
            prediction.markCollected(); prediction.reason=old.reason;
        }
        // Actual visits remain authoritative even when local estimates change on a later scan.
        if(old.serverObserved || old.isBlacklisted() || old.status==ChestTarget.Status.EMPTY || old.expected.equals(prediction.expected)) {
            prediction.status=old.status; prediction.reason=old.reason;
            prediction.blockObserved=old.blockObserved; prediction.serverObserved=old.serverObserved;
            prediction.actualApples=old.actualApples; prediction.predictionMismatch=old.predictionMismatch;
        }
        if(old.isBlacklisted())prediction.markCollected();
        // Older releases also used CONFIRMED for a matching calibration without any apples.
        if(prediction.status==ChestTarget.Status.CONFIRMED && !prediction.serverObserved)prediction.status=ChestTarget.Status.PREDICTED;
        return prediction;
    }

    /** Only the actual connected halves of a double chest may share an observation. */
    public static void observe(List<ChestTarget> halves,int apples,Map<String,Integer> actual) {
        observe(halves,apples,actual,true);
    }
    public static void observe(List<ChestTarget> halves,int apples,Map<String,Integer> actual,boolean complete) {
        Map<String,Integer> expected=new TreeMap<>();
        halves.forEach(t -> t.expected.forEach((k,v) -> expected.merge(k,v,Integer::sum)));
        boolean comparable=complete && halves.stream().allMatch(t -> !t.verificationRequired && t.lootSeed!=0);
        // Empty containers may already have been looted; they do not prove an RNG mismatch.
        boolean mismatch=comparable && !actual.isEmpty() && !expected.equals(actual);
        for(ChestTarget t:halves) {
            t.blockObserved=true; t.serverObserved=true; t.actualApples=apples;
            t.predictionMismatch=mismatch;
        }
    }

    /** Restrict discoveries to the underground area around an already predicted ancient city. */
    public static Optional<ChestTarget> cityFor(int x,int y,int z,Collection<ChestTarget> predictions) {
        if(y< -64 || y> -20)return Optional.empty();
        return predictions.stream().filter(t -> {
            double dx=(double)x-t.cityX*16.0,dz=(double)z-t.cityZ*16.0;
            return dx*dx+dz*dz<=160.0*160.0;
        }).min(Comparator.comparingDouble(t -> Math.pow(x-t.cityX*16.0,2)+Math.pow(z-t.cityZ*16.0,2)));
    }
}
