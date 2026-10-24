package dev.tanghulu.ancientapple.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.tanghulu.ancientapple.core.GlidePilot.Vec;

class ChestReachTest {
    @Test void thirdBlockHighChestCanBeReachedFromTheGround() {
        Vec feet=new Vec(0,0,0),eye=new Vec(0,1.62,0),face=new Vec(3,3.5,0);
        assertTrue(feet.distance(face)>4,"old feet-based arrival test kept pathing");
        assertTrue(ChestReach.reachable(eye,face,4.5));
    }
    @Test void rejectsOutOfRangeAndHonorsCustomReach() {
        Vec eye=new Vec(0,1.62,0),face=new Vec(5,3.5,0);
        assertFalse(ChestReach.reachable(eye,face,4.5));
        assertTrue(ChestReach.reachable(eye,face,6));
    }
    @Test void samplesLowerFacesAsWellAsCenterForPartlyObstructedChests() {
        var points=ChestReach.aimPoints(12,3,-5);
        assertTrue(points.stream().anyMatch(p -> p.y()<3.2));
        assertTrue(points.stream().allMatch(p -> p.x()>12 && p.x()<13 && p.y()>3 && p.y()<3.875 && p.z()>-5 && p.z()<-4));
    }
}
