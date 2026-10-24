package dev.tanghulu.ancientapple.core;

import java.util.Arrays;

/** Authoritative apple counts, independent of the client's optimistic click state.
 * Player-inventory packets must never replace the current container snapshot. */
public final class ContainerLedger {
    private int syncId=-1,chestSlots;
    private int[] contents=new int[0];
    private final int[] player=new int[36];
    private long serial;
    public void open(int syncId) { this.syncId=syncId; chestSlots=0; contents=new int[0]; serial++; }
    public void reset() { open(-1); Arrays.fill(player,0); }
    public void full(int sync,int[] counts) {
        if(sync==0) {
            if(counts.length>=45)for(int i=9;i<45;i++)playerSlot(i<36?i:i-36,counts[i]);
        } else if(sync==syncId && counts.length>=45 && (counts.length-36)%9==0) {
            chestSlots=counts.length-36; contents=counts.clone();
            for(int i=0;i<36;i++)player[i]=contents[containerSlot(i)];
            serial++;
        }
    }
    public void slot(int sync,int slot,int count) {
        if(sync==0 && slot>=9 && slot<45)playerSlot(slot<36?slot:slot-36,count);
        else if(sync==syncId && slot>=0 && slot<contents.length) {
            contents[slot]=count;
            if(slot>=chestSlots) { int offset=slot-chestSlots; player[offset<27?offset+9:offset-27]=count; }
            serial++;
        }
    }
    public void playerSlot(int slot,int count) {
        if(slot<0 || slot>=36)return;
        player[slot]=count;
        if(contents.length>0)contents[containerSlot(slot)]=count;
        serial++;
    }
    private int containerSlot(int inventorySlot) { return chestSlots+(inventorySlot<9?27+inventorySlot:inventorySlot-9); }
    public boolean ready(int sync) { return sync==syncId && chestSlots>0 && contents.length==chestSlots+36; }
    public long serial() { return serial; }
    public int chestApples() { return Arrays.stream(contents,0,chestSlots).sum(); }
    public int playerApples() { return Arrays.stream(player).sum(); }
}
