package dev.tanghulu.ancientapple.core;

/** Observe real movement before advancing from jump to release to the second jump. */
public final class TakeoffSequence {
    public enum Action { JUMP, RELEASE, DEPLOY, WALK, FAILED, GLIDING }
    private int ticks,groundTicks,attempts=1,deployCooldown;
    private boolean airborne;
    public Action tick(boolean grounded,boolean gliding,double verticalSpeed,boolean ledge) {
        if(gliding)return Action.GLIDING;
        if(++ticks>80)return Action.FAILED;
        if(grounded) {
            if(airborne) {
                if(++attempts>3)return Action.FAILED;
                airborne=false; groundTicks=0; deployCooldown=0;
                return Action.RELEASE;
            }
            if(ledge)return Action.WALK;
            // Reissue the first jump if another input owner or cooldown swallowed it.
            return groundTicks++%4<2?Action.JUMP:Action.RELEASE;
        }
        if(!airborne) { airborne=true; return Action.RELEASE; }
        if(deployCooldown>0) { deployCooldown--; return Action.RELEASE; }
        if(verticalSpeed>0)return Action.RELEASE;
        deployCooldown=2;
        return Action.DEPLOY;
    }
}
