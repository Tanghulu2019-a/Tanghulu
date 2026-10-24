package dev.tanghulu.ancientapple.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.tanghulu.ancientapple.core.TakeoffSequence.Action.*;

class TakeoffSequenceTest {
    @Test void missingFirstJumpIsRetriedUntilActuallyAirborne() {
        var sequence=new TakeoffSequence();
        assertEquals(JUMP,sequence.tick(true,false,0,false));
        assertEquals(JUMP,sequence.tick(true,false,0,false));
        assertEquals(RELEASE,sequence.tick(true,false,0,false));
        assertEquals(RELEASE,sequence.tick(true,false,0,false));
        assertEquals(JUMP,sequence.tick(true,false,0,false));
        assertEquals(RELEASE,sequence.tick(false,false,.3,false));
        assertEquals(RELEASE,sequence.tick(false,false,.1,false));
        assertEquals(DEPLOY,sequence.tick(false,false,-.01,false));
    }
    @Test void alwaysReleasesBeforeSecondJumpAndRetriesIgnoredDeployment() {
        var sequence=new TakeoffSequence();
        assertEquals(JUMP,sequence.tick(true,false,0,false));
        assertEquals(RELEASE,sequence.tick(false,false,-.01,false));
        assertEquals(DEPLOY,sequence.tick(false,false,-.02,false));
        assertEquals(RELEASE,sequence.tick(false,false,-.03,false));
        assertEquals(RELEASE,sequence.tick(false,false,-.04,false));
        assertEquals(DEPLOY,sequence.tick(false,false,-.05,false));
        assertEquals(GLIDING,sequence.tick(false,true,-.05,false));
    }
    @Test void serverGroundCorrectionRetriesAtMostThreeActualJumps() {
        var sequence=new TakeoffSequence();
        for(int attempt=1;attempt<=3;attempt++) {
            assertEquals(JUMP,sequence.tick(true,false,0,false));
            assertEquals(RELEASE,sequence.tick(false,false,.2,false));
            assertEquals(attempt==3?FAILED:RELEASE,sequence.tick(true,false,0,false));
        }
    }
    @Test void edgeAndAirLaunchesDoNotRequireAGroundJump() {
        var sequence=new TakeoffSequence();
        assertEquals(WALK,sequence.tick(true,false,0,true));
        assertEquals(RELEASE,sequence.tick(false,false,-.05,true));
        assertEquals(DEPLOY,sequence.tick(false,false,-.1,true));
        sequence=new TakeoffSequence();
        assertEquals(RELEASE,sequence.tick(false,false,-.1,false));
        assertEquals(DEPLOY,sequence.tick(false,false,-.2,false));
    }
    @Test void groundedInputThatNeverMovesEventuallyTimesOut() {
        var sequence=new TakeoffSequence();
        for(int i=0;i<80;i++)assertNotEquals(FAILED,sequence.tick(true,false,0,false));
        assertEquals(FAILED,sequence.tick(true,false,0,false));
    }
}
