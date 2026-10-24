package dev.tanghulu.ancientapple.core;

import java.util.*;

/** Temporarily unreachable targets remain unfinished and receive bounded retries. */
public final class TargetQueue {
    private static final int MAX_ATTEMPTS=3;
    private final Collection<ChestTarget> targets;
    private final Map<String,Integer> attempts=new HashMap<>(),retryAt=new HashMap<>();
    public TargetQueue(Collection<ChestTarget> targets) { this.targets=targets; }
    public void reset() { attempts.clear(); retryAt.clear(); }
    public Optional<ChestTarget> next(double x,double y,double z,ChestTarget previous,int tick) {
        var available=targets.stream().filter(t -> attempts.getOrDefault(t.key(),0)<MAX_ATTEMPTS && retryAt.getOrDefault(t.key(),0)<=tick).toList();
        return TargetSelector.next(available,x,y,z,previous);
    }
    public void defer(ChestTarget target,int tick) {
        target.status=ChestTarget.Status.DEFERRED; target.reason="寻路暂未到达，保留为待重试";
        int count=attempts.merge(target.key(),1,Integer::sum); retryAt.put(target.key(),tick+200*count);
    }
    public long pending() { return targets.stream().filter(ChestTarget::eligible).count(); }
    public boolean exhausted() { return pending()>0 && targets.stream().filter(ChestTarget::eligible).allMatch(t -> attempts.getOrDefault(t.key(),0)>=MAX_ATTEMPTS); }
}
