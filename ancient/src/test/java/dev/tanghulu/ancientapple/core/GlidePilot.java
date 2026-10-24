package dev.tanghulu.ancientapple.core;

/** Predict vanilla glide/rocket motion before committing a look direction.
 * No movement packets or artificial velocity are produced by this model. */
public final class GlidePilot {
    public record Vec(double x,double y,double z) {
        public Vec add(Vec v) { return new Vec(x+v.x,y+v.y,z+v.z); }
        public Vec subtract(Vec v) { return new Vec(x-v.x,y-v.y,z-v.z); }
        public Vec multiply(double k) { return new Vec(x*k,y*k,z*k); }
        public double horizontal() { return Math.hypot(x,z); }
        public double length() { return Math.sqrt(x*x+y*y+z*z); }
        public double distance(Vec v) { return subtract(v).length(); }
    }
    public interface Space {
        boolean clear(Vec from,Vec to);
        boolean canLand(Vec from,Vec to,Vec velocity);
    }
    public record Control(float yaw,float pitch,boolean rocket,boolean safe,int safeTicks,double score) {}
    private record Rollout(Control control,boolean landed) {}
    private GlidePilot() {}
    public static Vec look(double yaw,double pitch) {
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch),c=Math.cos(p);
        return new Vec(-Math.sin(y)*c,-Math.sin(p),Math.cos(y)*c);
    }
    /** Matches LivingEntity.calcGlidingVelocity, then the attached rocket's vanilla boost. */
    public static Vec step(Vec input,double yaw,double pitch,boolean rocket,double gravity) {
        Vec look=look(yaw,pitch); double h=look.horizontal(),speed=input.horizontal();
        double radians=Math.toRadians(pitch),lift=Math.pow(Math.cos(radians),2);
        Vec v=input.add(new Vec(0,gravity*(-1+lift*.75),0));
        if(v.y<0 && h>0) {
            double a=-v.y*.1*lift;
            v=v.add(new Vec(look.x*a/h,a,look.z*a/h));
        }
        if(pitch<0 && h>0) {
            double a=speed*-Math.sin(radians)*.04;
            v=v.add(new Vec(-look.x*a/h,a*3.2,-look.z*a/h));
        }
        if(h>0)v=v.add(new Vec((look.x/h*speed-v.x)*.1,0,(look.z/h*speed-v.z)*.1));
        v=new Vec(v.x*.9900000095367432,v.y*.9800000190734863,v.z*.9900000095367432);
        return rocket?v.add(look.multiply(.1).add(look.multiply(1.5).subtract(v).multiply(.5))):v;
    }
    public static Control choose(Vec position,Vec velocity,float yaw,float pitch,Vec cruise,Vec landing,
                                 boolean landingPhase,int boostTicks,boolean mayFire,int duration,double gravity,Space space) {
        Rollout best=null,fallback=null;
        for(int boost=0;boost<=(mayFire && boostTicks==0?1:0);boost++) {
            int horizon=Math.max(24,Math.min(56,(boost==1?duration:boostTicks)+8));
            for(int bias:new int[]{0,-25,25,-60,60})for(int tilt:new int[]{0,-8,8,-30,30,-60}) {
                if(boost==1 && (bias!=0 || Math.abs(tilt)>8))continue;
                Rollout r=simulate(position,velocity,yaw,pitch,cruise,landing,landingPhase,
                    Math.max(boostTicks,boost==1?duration:0),boost==1,bias,tilt,horizon,gravity,space);
                if(r.control.safe && (best==null || r.control.score<best.control.score))best=r;
                if(boost==0 && (fallback==null || r.control.safeTicks>fallback.control.safeTicks ||
                    (r.control.safeTicks==fallback.control.safeTicks && r.control.score<fallback.control.score)))fallback=r;
            }
        }
        return best!=null?best.control:fallback.control;
    }
    private static Rollout simulate(Vec p,Vec v,float yaw,float pitch,Vec cruise,Vec landing,boolean landingPhase,
                                    int boost,boolean fire,int bias,int tilt,int horizon,double gravity,Space space) {
        float firstYaw=yaw,firstPitch=pitch; int safeTicks=0; boolean landed=false,safe=true,approaching=landingPhase;
        for(int i=0;i<horizon;i++) {
            if(approaching(p,v,cruise,landing))approaching=true;
            Vec goal=approaching?landing:cruise;
            Vec delta=goal.subtract(p);
            // Aim slightly into lateral drift. The simulated velocity still turns gradually.
            double drift=approaching?6:2;
            float desiredYaw=(float)Math.toDegrees(Math.atan2(delta.z-v.z*drift,delta.x-v.x*drift))-90+bias;
            float desiredPitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.max(.1,delta.horizontal())))+tilt;
            yaw+=clamp(wrap(desiredYaw-yaw),-12,12);
            pitch+=clamp(clamp(desiredPitch,-60,85)-pitch,-6,6);
            if(i==0) { firstYaw=yaw; firstPitch=pitch; }
            Vec nextVelocity=step(v,yaw,pitch,i<boost,gravity),next=p.add(nextVelocity);
            if(space.canLand(p,next,nextVelocity)) { p=next; v=nextVelocity; landed=true; safeTicks=i+1; break; }
            if(!space.clear(p,next)) { safe=false; break; }
            p=next; v=nextVelocity; safeTicks=i+1;
        }
        // All rollouts must score the same final destination. Scoring an old
        // mid-air waypoint rewards orbiting underneath it during deep descents.
        double score=(landed?p.distance(landing)*2+v.horizontal()*3:p.distance(landing)+Math.abs(p.y-landing.y)*2)
            +Math.abs(bias)*.025+Math.abs(tilt)*.01+(fire?1:0);
        return new Rollout(new Control(firstYaw,firstPitch,fire,safe,safeTicks,score),landed);
    }
    public static boolean approaching(Vec position,Vec velocity,Vec cruise,Vec landing) {
        Vec onward=landing.subtract(cruise),passed=position.subtract(cruise);
        return position.distance(landing)<Math.max(7,velocity.length()*7) || position.distance(cruise)<Math.max(3,velocity.length()*5)
            || passed.x()*onward.x()+passed.y()*onward.y()+passed.z()*onward.z()>=0;
    }
    public static float wrap(float degrees) { float f=degrees%360; return f>=180?f-360:f< -180?f+360:f; }
    private static float clamp(float f,float lo,float hi) { return Math.max(lo,Math.min(hi,f)); }
}
