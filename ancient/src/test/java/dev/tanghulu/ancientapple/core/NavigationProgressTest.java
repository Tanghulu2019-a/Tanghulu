package dev.tanghulu.ancientapple.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NavigationProgressTest {
    @Test void jumpingAtAHighChestDoesNotResetTheStallBudget() {
        var progress=new NavigationProgress(30,90);
        progress.observe(new GlidePilot.Vec(0,0,0),-1,true);
        boolean replanned=false,deferred=false;
        for(int tick=1;tick<=100;tick++) {
            int phase=tick%12;
            double height=phase==0?0:Math.sin(phase*Math.PI/12)*1.2;
            var action=progress.observe(new GlidePilot.Vec(0,height,0),-1,phase==0);
            replanned|=action==NavigationProgress.Action.REPLAN;
            deferred|=action==NavigationProgress.Action.DEFER;
        }
        assertTrue(replanned); assertTrue(deferred);
    }
    @Test void ClimbingOntoANewFloorStillCountsAsProgress() {
        var progress=new NavigationProgress(10,30);
        for(int tick=0;tick<100;tick++) {
            assertEquals(NavigationProgress.Action.NONE,progress.observe(new GlidePilot.Vec(0,tick/6,0),-1,true));
        }
    }
    @Test void movingAwayDuringADetourIsStillProgress() {
        var progress=new NavigationProgress(10,30);
        for(int i=0;i<200;i++)assertEquals(NavigationProgress.Action.NONE,progress.observe(new GlidePilot.Vec(-i*.05,0,0),-1));
    }
    @Test void changingMiningStagesKeepAStationaryPlayerWorking() {
        var progress=new NavigationProgress(10,30);
        for(int i=0;i<200;i++)assertEquals(NavigationProgress.Action.NONE,progress.observe(new GlidePilot.Vec(0,0,0),i%10));
    }
    @Test void realStallsReplanThenDeferAndResetGivesAFreshBudget() {
        var progress=new NavigationProgress(10,30); var position=new GlidePilot.Vec(0,0,0);
        progress.observe(position,-1);
        for(int i=1;i<=30;i++)assertEquals(i==10?NavigationProgress.Action.REPLAN:i==30?NavigationProgress.Action.DEFER:NavigationProgress.Action.NONE,progress.observe(position,-1));
        progress.reset(); assertEquals(NavigationProgress.Action.NONE,progress.observe(position,-1));
    }
}
