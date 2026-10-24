package dev.tanghulu.ancientapple.qa;

import com.google.gson.GsonBuilder;
import dev.tanghulu.ancientapple.core.GlidePilot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import java.nio.file.*;
import java.util.*;

/** Developer-only comparison against actual vanilla glide and attached-rocket code. */
public final class NavigationAudit {
    public static void run(ServerWorld world,Path project) throws Exception {
        var glide=LivingEntity.class.getDeclaredMethod("calcGlidingVelocity",Vec3d.class); glide.setAccessible(true);
        var flag=Entity.class.getDeclaredMethod("setFlag",int.class,boolean.class); flag.setAccessible(true);
        var stand=new ArmorStandEntity(EntityType.ARMOR_STAND,world); flag.invoke(stand,7,true);
        stand.setPosition(0,200,0);
        double maxError=0; int cases=0;
        for(float yaw:new float[]{0,45,90,180,335})for(float pitch:new float[]{-60,-55,-30,-10,0,15,35,55,70,85})
            for(Vec3d v:List.of(Vec3d.ZERO,new Vec3d(.8,-.1,.4),new Vec3d(1.7,.2,-.2))) {
                stand.setYaw(yaw); stand.setPitch(pitch);
                Vec3d actual=(Vec3d)glide.invoke(stand,v);
                var model=GlidePilot.step(new GlidePilot.Vec(v.x,v.y,v.z),yaw,pitch,false,.08);
                maxError=Math.max(maxError,actual.distanceTo(new Vec3d(model.x(),model.y(),model.z()))); cases++;
                stand.setVelocity(actual);
                var rocket=new FireworkRocketEntity(world,Items.FIREWORK_ROCKET.getDefaultStack(),stand); rocket.tick();
                model=GlidePilot.step(new GlidePilot.Vec(v.x,v.y,v.z),yaw,pitch,true,.08);
                maxError=Math.max(maxError,stand.getVelocity().distanceTo(new Vec3d(model.x(),model.y(),model.z()))); cases++;
            }
        boolean passed=maxError<.0002;
        var report=Map.of("passed",passed,"cases",cases,"maxVelocityError",maxError,"method","Actual LivingEntity.calcGlidingVelocity and FireworkRocketEntity.tick; no visual client flight test.");
        Path output=project.resolve("build/reports/navigation-model.json"); Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("NAVIGATION_MODEL "+report);
        if(!passed)throw new AssertionError("Glide/rocket prediction differs from vanilla by "+maxError);
    }
}
