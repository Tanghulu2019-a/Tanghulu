package dev.tanghulu.ancientapple.core;

import dev.tanghulu.ancientapple.storage.WorldStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChestSurveyTest {
    @TempDir Path directory;
    private ChestTarget chest(int x,int apples) { return new ChestTarget(x,-49,-175458,3080,-10965,14,"ancient_city",apples,Map.of("apple",apples)); }

    @Test void removedServerSurveyCannotReviveZeroEstimateChests() {
        var t=chest(49286,0); assertFalse(t.eligible());
        t.surveyRequired=true;
        ChestSurvey.prepare(t,true); assertFalse(t.surveyRequired); assertFalse(t.eligible()); assertFalse(t.pendingVerification());
        ChestSurvey.observe(List.of(t),2,Map.of("apple",2));
        assertEquals(0,t.apples); assertEquals(2,t.actualApples); assertTrue(t.eligible()); assertFalse(t.pendingVerification());
        assertTrue(t.predictionMismatch); assertTrue(t.contentsLabel().contains("服务器确认"));
    }
    @Test void oldSurveyDiscoveriesAreNeverMarkedOrScheduled() {
        var discovered=chest(1,2); discovered.lootSeed=0; discovered.surveyRequired=true;
        discovered.verificationRequired=true; discovered.serverObserved=true; discovered.actualApples=2;
        ChestSurvey.prepare(discovered,false);
        assertFalse(discovered.eligible()); assertFalse(discovered.pendingVerification());
        assertFalse(dev.tanghulu.ancientapple.integration.XaeroBridge.shouldMark(discovered,true));
    }
    @Test void nonemptyChestWithoutApplesIsRecordedAsMismatchWithoutChangingPrediction() {
        var t=chest(49286,2); ChestSurvey.observe(List.of(t),0,Map.of("coal",4));
        assertEquals(2,t.apples); assertEquals(0,t.actualApples); assertFalse(t.eligible()); assertTrue(t.predictionMismatch);
        assertFalse(t.contentsLabel().contains("服务器确认附魔金苹果"));
    }
    @Test void lootedAndOrderSensitiveChestsDoNotProveModelMismatch() {
        var empty=chest(1,2); ChestSurvey.observe(List.of(empty),0,Map.of()); assertFalse(empty.predictionMismatch);
        var sensitive=chest(2,2); sensitive.verificationRequired=true;
        ChestSurvey.observe(List.of(sensitive),0,Map.of("coal",4)); assertFalse(sensitive.predictionMismatch);
    }
    @Test void connectedDoubleChestComparesCombinedPrediction() {
        var a=chest(1,1); var b=chest(2,2);
        ChestSurvey.observe(List.of(a,b),3,Map.of("apple",3));
        assertFalse(a.predictionMismatch); assertFalse(b.predictionMismatch); assertEquals(3,a.actualApples);
    }
    @Test void rescanKeepsServerEvidenceEvenWhenLocalEstimateChanges() {
        var old=chest(1,2); ChestSurvey.observe(List.of(old),0,Map.of("coal",2)); old.status=ChestTarget.Status.EMPTY;
        var updated=ChestSurvey.merge(chest(1,4),old,true);
        assertTrue(updated.serverObserved); assertTrue(updated.predictionMismatch); assertEquals(4,updated.apples);
        assertEquals(ChestTarget.Status.EMPTY,updated.status); assertFalse(updated.eligible());
    }
    @Test void repeatedScansCannotReviveCollectedCoordinatesEvenAfterStatusChanges() {
        var old=chest(1,2); old.markCollected(); old.status=ChestTarget.Status.DEFERRED;
        var updated=ChestSurvey.merge(chest(1,4),old,false);
        assertTrue(updated.blacklisted); assertEquals(ChestTarget.Status.COLLECTED,updated.status);
        assertFalse(updated.eligible());
        assertFalse(dev.tanghulu.ancientapple.integration.XaeroBridge.shouldMark(updated,false));
        assertFalse(dev.tanghulu.ancientapple.integration.XaeroBridge.shouldMark(updated,true));
    }
    @Test void discoveriesAreLimitedToDeepUndergroundNearKnownCities() {
        var t=chest(49286,0);
        assertTrue(ChestSurvey.cityFor(t.cityX*16,-49,t.cityZ*16,List.of(t)).isPresent());
        assertTrue(ChestSurvey.cityFor(t.cityX*16,64,t.cityZ*16,List.of(t)).isEmpty());
        assertTrue(ChestSurvey.cityFor(t.cityX*16+500,-49,t.cityZ*16,List.of(t)).isEmpty());
    }
    @Test void differentLootModelsSeparatePredictionsAndMigrateOnlyActualCompletedVisits() throws Exception {
        var collected=chest(1,2); collected.status=ChestTarget.Status.COLLECTED;
        var predicted=chest(2,4); var calibration=new Calibration(); calibration.observe(predicted,predicted.expected);
        var old=new WorldStore(directory,"3c3u|overworld",1,"1.21.11"); old.write(List.of(collected,predicted),calibration);
        var newer=new WorldStore(directory,"3c3u|overworld",1,"26.1");
        var migrated=newer.read(); assertEquals(1,migrated.targets.size()); assertTrue(migrated.matches.isEmpty());
        assertEquals(ChestTarget.Status.COLLECTED,migrated.targets.getFirst().status);
        newer.write(List.of(chest(3,0)),new Calibration());
        assertEquals(2,old.read().targets.size()); assertEquals(3,newer.read().targets.getFirst().x);
    }
}
