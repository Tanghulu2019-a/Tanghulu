package dev.tanghulu.ancientapple.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TargetQueueTest {
    private ChestTarget chest(int x) { return new ChestTarget(x,-48,0,0,0,1,"ancient_city",1,Map.of()); }
    @Test void stalledChestRemainsPendingWhileOtherChestsAreSelected() {
        var a=chest(0); var b=chest(20); var queue=new TargetQueue(List.of(a,b));
        queue.defer(a,0);
        assertEquals(ChestTarget.Status.DEFERRED,a.status); assertEquals(2,queue.pending());
        assertSame(b,queue.next(0,-48,0,a,1).orElseThrow());
        b.status=ChestTarget.Status.COLLECTED;
        assertTrue(queue.next(0,-48,0,b,199).isEmpty()); assertFalse(queue.exhausted());
        assertSame(a,queue.next(0,-48,0,b,200).orElseThrow());
    }
    @Test void exhaustedRetriesAreUnfinishedAndResumeRestoresTheirBudget() {
        var a=chest(0); var queue=new TargetQueue(List.of(a));
        for(int i=0;i<3;i++)queue.defer(a,1000*i);
        assertTrue(queue.next(0,0,0,null,10000).isEmpty()); assertTrue(queue.exhausted()); assertEquals(1,queue.pending());
        queue.reset(); assertFalse(queue.exhausted()); assertSame(a,queue.next(0,0,0,null,10000).orElseThrow());
        a.status=ChestTarget.Status.COLLECTED; assertEquals(0,queue.pending()); assertFalse(queue.exhausted());
    }
    @Test void uncertainZeroAppleChestsRemainPendingWhenDeferred() {
        var a=chest(0); a.apples=0; a.verificationRequired=true;
        var queue=new TargetQueue(List.of(a)); queue.defer(a,0);
        assertEquals(1,queue.pending()); assertSame(a,queue.next(0,0,0,null,201).orElseThrow());
    }
    @Test void failedCalibrationDoesNotHideAnUncollectedChestWhenSamplesAreSkipped() {
        var a=chest(0); a.status=ChestTarget.Status.MISMATCH;
        var queue=new TargetQueue(List.of(a));
        assertEquals(1,queue.pending()); assertSame(a,queue.next(0,0,0,null,0).orElseThrow());
    }
    @Test void aStatusChangeCannotRequeueBlacklistedChests() {
        var collected=chest(0); collected.markCollected(); collected.status=ChestTarget.Status.MISMATCH;
        var pending=chest(20); var queue=new TargetQueue(List.of(collected,pending));
        assertEquals(1,queue.pending()); assertSame(pending,queue.next(0,-48,0,null,0).orElseThrow());
        assertFalse(queue.exhausted());
    }
}
