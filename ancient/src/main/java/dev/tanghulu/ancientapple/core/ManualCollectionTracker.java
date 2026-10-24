package dev.tanghulu.ancientapple.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Passive receipts for player-made clicks. Local prediction alone is never proof of collection. */
public final class ManualCollectionTracker {
    private static final int MAX_PENDING=128;
    private final ContainerLedger ledger=new ContainerLedger();
    private final Map<String,Pending> pending=new LinkedHashMap<>();
    private String activeKey;
    private int syncId=-1,cursor,chestSlots;
    private long serial,possessionSerial;

    public void reset() {
        ledger.reset(); pending.clear(); activeKey=null; syncId=-1; cursor=chestSlots=0;
        serial=possessionSerial=0;
    }
    public void opened(String key,int sync) {
        activeKey=key; syncId=sync; chestSlots=0; ledger.open(sync); serial++;
    }
    public void full(int sync,int[] counts) {
        ledger.full(sync,counts); serial++;
        if((sync==0 && counts.length>=45) || (sync==syncId && ledger.ready(sync)))possessionSerial=serial;
        if(sync==syncId && ledger.ready(sync)) { chestSlots=counts.length-36; rememberChest(); }
    }
    public void slot(int sync,int slot,int count) {
        ledger.slot(sync,slot,count); serial++;
        if(sync==0 && slot>=9 && slot<45)possessionSerial=serial;
        else if(sync==syncId && ledger.ready(sync)) {
            if(slot>=0 && slot<chestSlots)rememberChest();
            else if(slot>=chestSlots && slot<chestSlots+36)possessionSerial=serial;
        }
    }
    public void playerSlot(int slot,int count) {
        if(slot<0 || slot>=36)return;
        ledger.playerSlot(slot,count); possessionSerial=++serial;
    }
    public void cursor(int count) { cursor=count; possessionSerial=++serial; }
    public boolean ready(int sync) { return activeKey!=null && sync==syncId && ledger.ready(sync); }
    /** Opening is separate from collection: only authoritative container slots may dismiss a marker. */
    public String openedWithApples() {
        return ready(syncId) && ledger.chestApples()>0?activeKey:null;
    }

    /** Called around vanilla's click method; these counts only identify an intended withdrawal. */
    public void predictedClick(int beforeChest,int beforePossessed,int afterChest,int afterPossessed) {
        if(activeKey==null || !ledger.ready(syncId))return;
        if(afterChest>beforeChest) { pending.remove(activeKey); return; }
        int moved=beforeChest-afterChest;
        if(moved<=0 || afterPossessed-beforePossessed<moved || ledger.chestApples()<=0)return;
        if(!pending.containsKey(activeKey)) {
            if(pending.size()>=MAX_PENDING)pending.remove(pending.keySet().iterator().next());
            pending.put(activeKey,new Pending(ledger.chestApples(),ledger.playerApples()+cursor,serial));
        }
    }
    public List<String> confirmed(int clientPossessed) {
        List<String> result=new ArrayList<>();
        // Cursor and inventory updates can arrive separately. A transient double count is not a receipt.
        int possessed=ledger.playerApples()+cursor;
        if(possessed!=clientPossessed)return result;
        pending.entrySet().removeIf(entry -> {
            Pending p=entry.getValue();
            if(p.chestSerial>p.started && possessionSerial>p.started && p.remaining==0
                && possessed>=p.beforePossessed+p.beforeChest) {
                result.add(entry.getKey()); return true;
            }
            return false;
        });
        return result;
    }
    public int pending() { return pending.size(); }
    private void rememberChest() {
        Pending p=pending.get(activeKey);
        if(p!=null) { p.remaining=ledger.chestApples(); p.chestSerial=serial; }
    }
    private static final class Pending {
        final int beforeChest,beforePossessed;
        final long started;
        int remaining;
        long chestSerial;
        Pending(int beforeChest,int beforePossessed,long started) {
            this.beforeChest=beforeChest; this.beforePossessed=beforePossessed; this.started=started;
            remaining=beforeChest; chestSerial=started;
        }
    }
}
