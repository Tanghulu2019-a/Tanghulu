package dev.tanghulu.ancientapple.core;

import dev.tanghulu.ancientapple.storage.WorldStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WorldStoreTest {
    @TempDir Path directory;
    @Test void oldUnreachableHistoryIsRetriedWithoutRepeatingCollectedChests() throws Exception {
        var pending=new ChestTarget(1,-48,0,0,0,1,"ancient_city",1,Map.of()); pending.status=ChestTarget.Status.UNREACHABLE;
        var done=new ChestTarget(2,-48,0,0,0,2,"ancient_city",1,Map.of()); done.status=ChestTarget.Status.COLLECTED;
        var store=new WorldStore(directory,"server|overworld",1); store.write(List.of(pending,done),new Calibration());
        var targets=store.read().targets;
        assertEquals(ChestTarget.Status.DEFERRED,targets.get(0).status); assertTrue(targets.get(0).eligible());
        assertEquals(ChestTarget.Status.COLLECTED,targets.get(1).status); assertFalse(targets.get(1).eligible());
    }
    @Test void persistsHistoryAndSeparatesWorldsAndSeeds() throws Exception {
        ChestTarget t=new ChestTarget(-20,-48,18,-2,0,-7L,"ancient_city",2,Map.of("apple",2));
        t.status=ChestTarget.Status.COLLECTED;
        Calibration c=new Calibration(); c.observe(t,Map.of("apple",1));
        WorldStore a=new WorldStore(directory,"server|world-a|overworld",5);
        a.write(List.of(t),c);
        var read=a.read(); assertEquals(ChestTarget.Status.COLLECTED,read.targets.getFirst().status); assertTrue(read.failed);
        assertTrue(new WorldStore(directory,"server|world-b|overworld",5).read().targets.isEmpty());
        assertTrue(new WorldStore(directory,"server|world-a|overworld",6).read().targets.isEmpty());
    }
    @Test void collectedCoordinatesSurviveEmptyPredictionCachesAndLootModelChanges() throws Exception {
        var store=new WorldStore(directory,"server|overworld",1,"26.1");
        var collected=chest(1); store.blacklist(List.of(collected));
        collected.status=ChestTarget.Status.PREDICTED;
        store.write(List.of(),new Calibration());
        var otherModel=new WorldStore(directory,"server|overworld",1,"1.21.11");
        assertTrue(otherModel.read().targets.isEmpty());
        var reestimated=chest(1); assertTrue(otherModel.applyBlacklist(reestimated));
        assertTrue(reestimated.blacklisted); assertFalse(reestimated.eligible());
        assertFalse(otherModel.applyBlacklist(chest(2)));
        for(var isolated:List.of(new WorldStore(directory,"other-server|overworld",1),new WorldStore(directory,"server|nether",1),new WorldStore(directory,"server|overworld",2))) {
            isolated.read(); assertFalse(isolated.applyBlacklist(chest(1)));
        }
    }
    @Test void oldCollectedStatusMigratesFromEitherLootModelWithoutInspectingOtherWorlds() throws Exception {
        // Create the exact pre-blacklist snapshot format, including one incomplete and one empty chest.
        String identity="server|overworld|1|"+WorldStore.ALGORITHM+"|loot=26.1";
        String name=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)))+".json";
        Files.writeString(directory.resolve(name),"{\"algorithm\":\""+WorldStore.ALGORITHM+"\",\"targets\":[{\"x\":1,\"y\":-48,\"z\":0,\"status\":\"COLLECTED\"},{\"x\":2,\"y\":-48,\"z\":0,\"status\":\"CONFIRMED\"},{\"x\":3,\"y\":-48,\"z\":0,\"status\":\"EMPTY\"}],\"matches\":[],\"failed\":false}");
        var store=new WorldStore(directory,"server|overworld",1,"1.21.11"); store.read();
        assertTrue(store.applyBlacklist(chest(1))); assertFalse(store.applyBlacklist(chest(2))); assertFalse(store.applyBlacklist(chest(3)));
        Files.delete(directory.resolve(name));
        var restored=new WorldStore(directory,"server|overworld",1,"26.1"); restored.read();
        assertTrue(restored.applyBlacklist(chest(1)));
    }
    @Test void confirmedDoubleChestBlacklistsOnlyTheExplicitConnectedHalves() throws Exception {
        var store=new WorldStore(directory,"server|overworld",1);
        var first=chest(1); var connected=chest(2); var nearby=chest(3);
        store.blacklist(List.of(first,connected));
        assertTrue(first.isBlacklisted()); assertTrue(connected.isBlacklisted()); assertFalse(store.applyBlacklist(nearby));
        var restored=new WorldStore(directory,"server|overworld",1); restored.read();
        assertTrue(restored.applyBlacklist(chest(1))); assertTrue(restored.applyBlacklist(chest(2))); assertFalse(restored.applyBlacklist(chest(3)));
    }
    @Test void containerObservationAloneNeverBlacklistsAChest() throws Exception {
        var target=chest(1); ChestSurvey.observe(List.of(target),1,Map.of("apple",1));
        var store=new WorldStore(directory,"server|overworld",1); store.write(List.of(target),new Calibration());
        assertFalse(store.read().targets.getFirst().isBlacklisted());
        assertFalse(store.applyBlacklist(chest(1)));
    }
    @Test void switchingBetweenLoadedLootModelsNeverOverwritesNewerCollectionHistory() throws Exception {
        var oldModel=new WorldStore(directory,"server|overworld",1,"1.21.11");
        var newModel=new WorldStore(directory,"server|overworld",1,"26.1");
        oldModel.blacklist(List.of(chest(1))); newModel.blacklist(List.of(chest(2)));
        oldModel.write(List.of(),new Calibration());
        var restored=new WorldStore(directory,"server|overworld",1); restored.read();
        assertTrue(restored.applyBlacklist(chest(1))); assertTrue(restored.applyBlacklist(chest(2)));
    }
    private ChestTarget chest(int x) { return new ChestTarget(x,-48,0,0,0,1,"ancient_city",1,Map.of()); }
    @Test void toggleReenablesPredictedTargetsWithoutErasingStoredHistory() throws Exception {
        var store=new WorldStore(directory,"server|overworld",1);
        var target=chest(1); target.serverObserved=true; target.actualApples=0;
        store.blacklist(List.of(target)); store.write(List.of(target),new Calibration());
        store.setBlacklistEnabled(false); store.refreshBlacklistView(List.of(target));
        assertTrue(target.eligible()); assertFalse(target.serverObserved);
        assertEquals(List.of(target.key()),store.blacklistPositions());
        store.write(List.of(target),new Calibration());
        var disabled=new WorldStore(directory,"server|overworld",1); disabled.setBlacklistEnabled(false);
        assertTrue(disabled.read().targets.getFirst().eligible());
        disabled.setBlacklistEnabled(true); disabled.refreshBlacklistView(List.of(target));
        assertTrue(target.isBlacklisted()); assertFalse(target.eligible());
    }
    @Test void clearSurvivesRestartRescanAndSwitchingBothLootModels() throws Exception {
        var first=new WorldStore(directory,"server|overworld",1,"1.21.11");
        var second=new WorldStore(directory,"server|overworld",1,"26.1");
        first.write(List.of(collectedChest(1)),new Calibration());
        second.write(List.of(collectedChest(2)),new Calibration());
        var loaded=first.read().targets;
        assertEquals(2,first.clearBlacklist(loaded));
        assertTrue(loaded.getFirst().eligible());
        first.write(loaded,new Calibration());
        for(String model:List.of("1.21.11","26.1")) {
            var restored=new WorldStore(directory,"server|overworld",1,model);
            assertTrue(restored.read().targets.stream().allMatch(ChestTarget::eligible));
            assertTrue(restored.blacklistPositions().isEmpty());
            assertFalse(restored.applyBlacklist(chest(1))); assertFalse(restored.applyBlacklist(chest(2)));
        }
        first.blacklist(List.of(chest(3)));
        var restored=new WorldStore(directory,"server|overworld",1); restored.read();
        assertEquals(List.of(chest(3).key()),restored.blacklistPositions());
    }
    @Test void clearingCurrentWorldPreservesOtherWorldsAndSeeds() throws Exception {
        var current=new WorldStore(directory,"server|overworld",1);
        var other=new WorldStore(directory,"other|overworld",1);
        var seed=new WorldStore(directory,"server|overworld",2);
        for(var store:List.of(current,other,seed))store.blacklist(List.of(chest(1)));
        current.clearBlacklist(List.of());
        assertTrue(current.blacklistPositions().isEmpty());
        assertEquals(List.of(chest(1).key()),other.blacklistPositions());
        assertEquals(List.of(chest(1).key()),seed.blacklistPositions());
    }
    private ChestTarget collectedChest(int x) { var target=chest(x); target.markCollected(); return target; }
    @Test void restoreSelectedCoordinatesSurvivesRestartAndEveryProfileSwitch() throws Exception {
        for(var version:dev.tanghulu.ancientapple.prediction.LootVersion.values()) {
            var store=new WorldStore(directory,"server|overworld",1,version.toString());
            var restored=collectedChest(1); restored.openedWithApples=true;
            store.write(List.of(restored,collectedChest(2)),new Calibration());
        }
        var current=new WorldStore(directory,"server|overworld",1,"26.3");
        var targets=current.read().targets;
        assertEquals(1,current.restoreRecords(List.of(chest(1).key()),targets,false));
        current.write(targets,new Calibration());
        for(var version:dev.tanghulu.ancientapple.prediction.LootVersion.values()) {
            var reopened=new WorldStore(directory,"server|overworld",1,version.toString());
            var records=reopened.read().targets;
            assertTrue(records.getFirst().eligible()); assertFalse(records.getFirst().openedWithApples);
            assertTrue(records.getLast().isBlacklisted());
            assertEquals(List.of(chest(2).key()),reopened.blacklistPositions());
        }
    }
    @Test void restoreOpenedRecordsDoesNotEraseUnrelatedCollectionHistory() throws Exception {
        for(String version:List.of("1.21.1","26.2","26.3")) {
            var target=chest(1); target.openedWithApples=true;
            new WorldStore(directory,"server|overworld",1,version).write(List.of(target,collectedChest(2)),new Calibration());
        }
        var current=new WorldStore(directory,"server|overworld",1,"26.3");
        assertEquals(List.of(chest(1).key()),current.openedPositions());
        assertEquals(1,current.restoreRecords(List.of(chest(1).key()),current.read().targets,true));
        assertTrue(current.openedPositions().isEmpty());
        assertEquals(List.of(chest(2).key()),current.blacklistPositions());
        for(String version:List.of("1.21.1","26.2","26.3"))
            assertFalse(new WorldStore(directory,"server|overworld",1,version).read().targets.getFirst().openedWithApples);
    }
    @Test void clearIncludesNewerProfilesAndPreservesOtherSeeds() throws Exception {
        var otherSeed=new WorldStore(directory,"server|overworld",2,"26.3"); otherSeed.blacklist(List.of(chest(7)));
        for(var version:dev.tanghulu.ancientapple.prediction.LootVersion.values()) {
            var target=chest(1); target.openedWithApples=true;
            new WorldStore(directory,"server|overworld",1,version.toString()).write(List.of(target,collectedChest(2)),new Calibration());
        }
        new WorldStore(directory,"server|overworld",1,"1.21.1").clearBlacklist(List.of());
        for(var version:dev.tanghulu.ancientapple.prediction.LootVersion.values()) {
            var current=new WorldStore(directory,"server|overworld",1,version.toString());
            assertTrue(current.blacklistPositions().isEmpty()); assertTrue(current.openedPositions().isEmpty());
            assertTrue(current.read().targets.stream().allMatch(t -> !t.isBlacklisted() && !t.openedWithApples));
        }
        assertEquals(List.of(chest(7).key()),otherSeed.blacklistPositions());
    }
    @Test void invalidRestoreCannotModifyStoredHistory() throws Exception {
        var store=new WorldStore(directory,"server|overworld",1); store.blacklist(List.of(chest(1)));
        assertThrows(java.io.IOException.class,() -> store.restoreRecords(List.of("invalid"),List.of(),false));
        assertEquals(List.of(chest(1).key()),store.blacklistPositions());
    }
}
