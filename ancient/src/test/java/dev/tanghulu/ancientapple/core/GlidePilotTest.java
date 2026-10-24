package dev.tanghulu.ancientapple.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.tanghulu.ancientapple.core.GlidePilot.*;

class GlidePilotTest {
    private Space tunnel(double wall) {
        return new Space() {
            public boolean clear(Vec a,Vec b) {
                return b.y()>=0 && b.y()<2.2 && Math.abs(b.z())<2.5 && b.x()<wall;
            }
            public boolean canLand(Vec a,Vec b,Vec v) { return false; }
        };
    }
    @Test void straightLoadedCorridorOffersAUsableFlightControl() {
        var c=choose(new Vec(0,1.1,0),new Vec(.8,-.02,0),-90,0,new Vec(80,1.2,0),new Vec(86,0,0),false,0,true,32,.08,tunnel(200));
        assertTrue(c.safe(),c.toString()); assertTrue(c.safeTicks()>=24);
    }
    @Test void neverAddsRocketBoostIntoAClosedCorridor() {
        var c=choose(new Vec(0,1.1,0),new Vec(1.4,0,0),-90,0,new Vec(60,1.2,0),new Vec(66,0,0),false,0,true,32,.08,tunnel(10));
        assertFalse(c.safe()); assertFalse(c.rocket());
    }
    @Test void steeringRespectsYawAndPitchRateInsteadOfInstantlyTurningVelocity() {
        Space open=new Space() { public boolean clear(Vec a,Vec b) { return true; } public boolean canLand(Vec a,Vec b,Vec v) { return false; } };
        var c=choose(new Vec(0,20,0),new Vec(0,0,1.6),0,0,new Vec(40,20,0),new Vec(45,0,0),false,12,false,32,.08,open);
        assertTrue(Math.abs(wrap(c.yaw()))<=12); assertTrue(Math.abs(c.pitch())<=6);
        var velocity=step(new Vec(0,0,1.6),c.yaw(),c.pitch(),false,.08);
        assertTrue(velocity.z()>1.4); // Existing momentum cannot follow an instantaneous 90-degree line.
    }
    @Test void permitsShortDescendingHopWithoutRequiringARocket() {
        Space floor=new Space() {
            public boolean clear(Vec a,Vec b) { return b.y()>=0; }
            public boolean canLand(Vec a,Vec b,Vec v) { return b.y()<=.08 && Math.hypot(b.x(),b.z()-3)<2; }
        };
        var c=choose(new Vec(0,1.2,0),new Vec(0,-.25,.35),0,25,new Vec(0,1,2.5),new Vec(0,0,3),true,0,false,32,.08,floor);
        assertTrue(c.safe(),c.toString()); assertFalse(c.rocket());
    }
}
