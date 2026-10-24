package dev.tanghulu.ancientapple.core;

/** Detours and mining are progress even if the straight-line chest distance grows. */
public final class NavigationProgress {
    public enum Action { NONE, REPLAN, DEFER }
    private final int replanAfter,deferAfter;
    private GlidePilot.Vec anchor;
    private int idleTicks,lastBreakingStage=-1;
    private boolean replanned;
    private double settledY;
    public NavigationProgress(int replanAfter,int deferAfter) { this.replanAfter=replanAfter; this.deferAfter=deferAfter; }
    public void reset() { anchor=null; idleTicks=0; lastBreakingStage=-1; replanned=false; }
    public Action observe(GlidePilot.Vec position,int breakingStage) { return observe(position,breakingStage,true); }
    public Action observe(GlidePilot.Vec position,int breakingStage,boolean grounded) {
        // A jump rises and falls over the same block. Only a new grounded height
        // or horizontal displacement is useful progress; airborne bobbing is not.
        boolean moved=anchor==null || Math.hypot(position.x()-anchor.x(),position.z()-anchor.z())>=.35
            || (grounded && Math.abs(position.y()-settledY)>=.35);
        boolean mining=breakingStage>0 && breakingStage!=lastBreakingStage;
        lastBreakingStage=breakingStage;
        if(moved || mining) { if(anchor==null || grounded)settledY=position.y(); anchor=position; idleTicks=0; replanned=false; return Action.NONE; }
        idleTicks++;
        if(idleTicks>=deferAfter)return Action.DEFER;
        if(idleTicks>=replanAfter && !replanned) { replanned=true; return Action.REPLAN; }
        return Action.NONE;
    }
}
