package dev.tanghulu.ancientapple.core;

import java.util.Map;
import java.util.TreeMap;

/** All identifiers are chest block positions, never dropped item positions. */
public final class ChestTarget {
    public enum Status { PREDICTED, CONFIRMED, COLLECTED, EMPTY, MISSING, MISMATCH, UNREACHABLE, DEFERRED }
    public int x, y, z, cityX, cityZ, apples;
    public int swiftSneakBooks;
    public long lootSeed;
    public String lootTable;
    public boolean verificationRequired;
    /** Kept for reading old caches; the all-chest server survey is no longer supported. */
    public boolean surveyRequired, blockObserved, serverObserved, predictionMismatch;
    /** Collection history survives estimate/status changes and is persisted separately by WorldStore. */
    public boolean blacklisted;
    /** Hide the marker once this connected container was opened and the server showed apples. */
    public boolean openedWithApples;
    public int actualApples;
    public Map<String, Integer> expected = new TreeMap<>();
    public Status status = Status.PREDICTED;
    public String reason = "";

    public ChestTarget() {}
    public ChestTarget(int x, int y, int z, int cityX, int cityZ, long lootSeed,
                       String lootTable, int apples, Map<String, Integer> expected) {
        this.x=x; this.y=y; this.z=z; this.cityX=cityX; this.cityZ=cityZ;
        this.lootSeed=lootSeed; this.lootTable=lootTable; this.apples=apples;
        this.expected=new TreeMap<>(expected);
        refreshLoot();
    }
    /** Decode the existing canonical loot signature; no extra RNG calls or loot generation. */
    public void refreshLoot() {
        swiftSneakBooks=0;
        for(var entry:expected.entrySet()) {
            if(!entry.getKey().startsWith("{") || !entry.getKey().contains("\"minecraft:enchanted_book\""))continue;
            var json=com.google.gson.JsonParser.parseString(entry.getKey());
            if(!json.isJsonObject())continue;
            var stack=json.getAsJsonObject();
            if(!stack.has("id") || !"minecraft:enchanted_book".equals(stack.get("id").getAsString()) || !stack.has("components"))continue;
            var component=stack.getAsJsonObject("components").get("minecraft:stored_enchantments");
            if(component==null || !component.isJsonObject())continue;
            var levels=component.getAsJsonObject();
            if(levels.has("levels"))levels=levels.getAsJsonObject("levels");
            if(levels.has("minecraft:swift_sneak") && levels.get("minecraft:swift_sneak").getAsInt()==3)swiftSneakBooks+=entry.getValue();
        }
    }
    public String key() { return x + "," + y + "," + z; }
    public boolean isBlacklisted() { return blacklisted || status == Status.COLLECTED; }
    /** Call only after the server confirmed apple transfer and that the connected container is empty of apples. */
    public void markCollected() { blacklisted=true; status=Status.COLLECTED; }
    public void resetCollection() {
        blacklisted=false; openedWithApples=false; status=Status.PREDICTED; serverObserved=false; blockObserved=false;
        actualApples=0; predictionMismatch=false; reason="";
    }
    /** Old survey-only discoveries have no prediction and must never become search targets. */
    public boolean hasPrediction() { return lootSeed!=0; }
    public boolean eligible() { return !isBlacklisted() && hasPrediction() && (serverObserved ? actualApples>0 : apples>0 || swiftSneakBooks>0 || verificationRequired)
        && (status == Status.PREDICTED || status == Status.CONFIRMED || status == Status.DEFERRED || status == Status.MISMATCH); }
    public boolean pendingVerification() { return hasPrediction() && !serverObserved && verificationRequired; }
    public String contentsLabel() {
        if(serverObserved)return actualApples>0?"服务器确认目标战利品 ×"+actualApples:"已检查，无目标战利品";
        return (pendingVerification()?"待开箱核验 · ":"原版预测 · ")+lootLabel();
    }
    public String lootLabel() {
        if(apples>0 && swiftSneakBooks>0)return "附魔金苹果 ×"+apples+" / 迅捷潜行 III ×"+swiftSneakBooks;
        if(swiftSneakBooks>0)return "迅捷潜行 III ×"+swiftSneakBooks;
        return apples>0?"附魔金苹果 ×"+apples:"古城战利品（待核验）";
    }
    public double distanceSquared(double px, double py, double pz) {
        double dx=x+.5-px, dy=y+.5-py, dz=z+.5-pz;
        return dx*dx+dy*dy+dz*dz;
    }
}
