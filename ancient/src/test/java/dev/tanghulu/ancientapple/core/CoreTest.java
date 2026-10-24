package dev.tanghulu.ancientapple.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    private ChestTarget target(int x,int z,int city) {
        return new ChestTarget(x,-50,z,city,0,12L,"ancient_city",2,Map.of("apple",2));
    }
    @Test void requiresThreeDifferentNonemptyMatchingChests() {
        Calibration c=new Calibration(); ChestTarget a=target(1,1,0);
        assertEquals(Calibration.Result.EMPTY,c.observe(a,Map.of()));
        assertFalse(c.ready());
        assertEquals(Calibration.Result.MATCH,c.observe(a,Map.of("apple",2)));
        assertEquals(Calibration.Result.DUPLICATE,c.observe(a,Map.of("apple",2)));
        c.observe(target(2,1,0),Map.of("apple",2)); assertFalse(c.ready());
        c.observe(target(3,1,0),Map.of("apple",2)); assertTrue(c.ready());
        c.observe(target(4,1,0),Map.of("apple",1)); assertFalse(c.ready());
    }
    @Test void collectedChestIsNeverScheduledAgainAndCurrentCityHasPriority() {
        ChestTarget previous=target(10,0,1), same=target(100,0,1), near=target(1,0,2);
        previous.status=ChestTarget.Status.COLLECTED;
        assertSame(same,TargetSelector.next(List.of(previous,same,near),0,-50,0,previous).orElseThrow());
        same.status=ChestTarget.Status.EMPTY;
        assertSame(near,TargetSelector.next(List.of(previous,same,near),0,-50,0,previous).orElseThrow());
    }
    @Test void optionalSamplesDoNotBlockAutomaticEvenAfterPreviousMismatch() {
        Calibration c=new Calibration();
        assertTrue(c.allowsAutomatic(true)); assertFalse(c.allowsAutomatic(false));
        c.observe(target(1,1,0),Map.of("apple",1));
        assertTrue(c.failed()); assertTrue(c.allowsAutomatic(true)); assertFalse(c.allowsAutomatic(false));
    }
    @Test void transferRequiresBothServerObservations() {
        TransferReceipt receipt=new TransferReceipt(3,4,2);
        assertFalse(receipt.confirmed(3,2)); assertFalse(receipt.confirmed(5,4));
        assertTrue(receipt.confirmed(5,2));
    }
    @Test void flightAllowsUsefulShortHopsButRequiresClearanceAndReserve() {
        assertTrue(FlightPolicy.preferFlight(true,true,30,10,false));
        assertFalse(FlightPolicy.preferFlight(false,true,30,10,false));
        assertFalse(FlightPolicy.preferFlight(true,false,30,10,true));
        assertFalse(FlightPolicy.resourcesAllow(8,8,.5,false));
        assertTrue(FlightPolicy.resourcesAllow(8,8,.5,true));
        assertFalse(FlightPolicy.resourcesAllow(20,8,.09,true));
    }
    @Test void zeroAppleEstimateNeverDiscardsOrderSensitiveChest() {
        ChestTarget t=target(1,2,3); t.apples=0; assertFalse(t.eligible());
        t.verificationRequired=true; assertTrue(t.eligible());
        t.status=ChestTarget.Status.EMPTY; assertFalse(t.eligible());
    }
}
