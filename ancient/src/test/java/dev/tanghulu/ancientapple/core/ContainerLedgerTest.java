package dev.tanghulu.ancientapple.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContainerLedgerTest {
    @Test void playerInventoryResyncCannotReplaceAnOpenChest() {
        var ledger=new ContainerLedger(); ledger.open(7);
        int[] chest=new int[63]; chest[4]=2; ledger.full(7,chest);
        ledger.full(0,new int[46]);
        assertTrue(ledger.ready(7)); assertEquals(2,ledger.chestApples());
        ledger.slot(7,4,0); ledger.slot(0,36,2);
        assertEquals(0,ledger.chestApples()); assertEquals(2,ledger.playerApples());
    }
    @Test void newPlayerSlotPacketAndContainerPacketCanArriveInEitherOrder() {
        for(boolean playerFirst:new boolean[]{false,true}) {
            var ledger=new ContainerLedger(); ledger.open(7);
            int[] chest=new int[90]; chest[7]=2; chest[54]=3; ledger.full(7,chest);
            var receipt=new TransferReceipt(3,2,2);
            if(playerFirst)ledger.playerSlot(0,2); else ledger.slot(7,7,0);
            assertFalse(receipt.confirmed(ledger.playerApples(),ledger.chestApples()));
            if(playerFirst)ledger.slot(7,7,0); else ledger.playerSlot(0,2);
            assertTrue(receipt.confirmed(ledger.playerApples(),ledger.chestApples()));
        }
    }
    @Test void reopeningConfirmsAPredictedClickThatProducedNoSlotDeltas() {
        var ledger=new ContainerLedger(); ledger.open(5);
        int[] before=new int[63]; before[0]=1; ledger.full(5,before);
        long serial=ledger.serial(); var receipt=new TransferReceipt(0,1,1);
        // The optimistic GUI changed but no authoritative packet was received.
        assertEquals(serial,ledger.serial()); assertFalse(receipt.confirmed(ledger.playerApples(),ledger.chestApples()));
        ledger.open(6); int[] after=new int[63]; after[62]=1; ledger.full(6,after);
        assertTrue(ledger.serial()>serial); assertTrue(receipt.confirmed(ledger.playerApples(),ledger.chestApples()));
        ledger.slot(5,0,12); assertEquals(0,ledger.chestApples());
    }
    @Test void reopeningDoesNotConfirmAnUnsuccessfulOrRejectedClick() {
        var ledger=new ContainerLedger(); ledger.open(5);
        int[] before=new int[63]; before[0]=2; ledger.full(5,before);
        var receipt=new TransferReceipt(0,2,2);
        ledger.open(6); ledger.full(6,before);
        assertFalse(receipt.confirmed(ledger.playerApples(),ledger.chestApples()));
    }
}
