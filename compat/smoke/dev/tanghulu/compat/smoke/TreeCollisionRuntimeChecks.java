package dev.tanghulu.compat.smoke;

import dev.tanghulu.addon.util.tree.TreeDescent;
import dev.tanghulu.addon.util.tree.TreeLanding;
import dev.tanghulu.addon.util.tree.TreeApproach;
import dev.tanghulu.addon.util.tree.TreePlan;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.EmptyBlockView;
import java.lang.reflect.*;
import java.util.*;

/** Reproduce edge-catching and delayed server acknowledgements with vanilla's actual collision resolver. */
public final class TreeCollisionRuntimeChecks {
    private static Method collide;
    private static int checks;
    private record Body(double x, double y, double z, double vy, boolean ground) {}
    private static void check(boolean ok, String reason) { checks++; if (!ok) throw new AssertionError(reason); }
    private static VoxelShape cube(int x, int y, int z) { return VoxelShapes.cuboid(x, y, z, x + 1, y + 1, z + 1); }
    private static Body step(Body body, List<VoxelShape> shapes, boolean steer) throws Exception {
        Vec3d request = new Vec3d(steer ? TreeDescent.centerStep(body.x, 0) : 0, body.vy,
            steer ? TreeDescent.centerStep(body.z, 0) : 0);
        Box box = new Box(body.x - 0.3, body.y, body.z - 0.3, body.x + 0.3, body.y + 1.8, body.z + 0.3);
        Vec3d actual = (Vec3d) collide.invoke(null, request, box, shapes);
        boolean ground = request.y < 0 && Math.abs(actual.y - request.y) > 0.000001;
        return new Body(body.x + actual.x, body.y + actual.y, body.z + actual.z,
            ground ? -0.08 : (body.vy - 0.08) * 0.98, ground);
    }
    public static void run() throws Exception {
        collide = Arrays.stream(Entity.class.getDeclaredMethods()).filter(m -> Modifier.isStatic(m.getModifiers())
            && Arrays.equals(m.getParameterTypes(), new Class<?>[]{Vec3d.class, Box.class, List.class}))
            .findFirst().orElseThrow();
        collide.setAccessible(true);
        var floor = EmptyBlockView.INSTANCE; var p = new BlockPos(0, 63, 0);
        check(TreeLanding.surface(Blocks.DIRT_PATH.getDefaultState(), floor, p) == 63.9375, "path is valid collision landing");
        check(TreeLanding.surface(Blocks.STONE_SLAB.getDefaultState(), floor, p) == 63.5, "bottom slab is valid collision landing");
        check(TreeLanding.surface(Blocks.STONE.getDefaultState(), floor, p) == 64, "full support landing");
        check(Double.isNaN(TreeLanding.surface(Blocks.AIR.getDefaultState(), floor, p)), "air is not a support");
        check(TreeLanding.hazardous(Blocks.LAVA.getDefaultState()), "lava remains a hazard");

        // A log at x=1 is already within reach, while the selected work position is x=0.
        var approach = new TreeApproach();
        var stand = new TreePlan.Pos(0, 64, 0);
        var approachTerrain = new ArrayList<VoxelShape>();
        for (int x = -3; x <= 1; x++) approachTerrain.add(cube(x, 63, 0));
        approachTerrain.add(cube(1, 64, 0));
        Body approaching = new Body(-2.5, 64, 0.5, -0.08, true);
        check(!approach.update(stand, approaching.x, approaching.y, approaching.z, approaching.ground, 0),
            "reachable trunk does not authorize breaking before walking to work position");
        int firstMineTick = -1;
        boolean minedBeforeArrival = false;
        for (int tick = 1; tick <= 40; tick++) {
            approaching = step(approaching, approachTerrain, true);
            if (approach.update(stand, approaching.x, approaching.y, approaching.z, approaching.ground, tick)) {
                if (firstMineTick < 0) firstMineTick = tick;
                minedBeforeArrival |= Math.abs(approaching.x - 0.5) >= 0.09 || !approaching.ground;
            }
        }
        check(firstMineTick >= 26 && !minedBeforeArrival, "first mining approval follows actual movement and settling");
        check(Math.abs(approaching.x - 0.5) < 0.001 && approaching.ground,
            "work position beside trunk is reachable with vanilla collision resolution");
        check(!approach.ready(stand, approaching.x - 0.3, approaching.y, approaching.z, approaching.ground, 40),
            "movement after rotation request prevents delayed remote breaking");

        // The scaffold's top block was mined, but the player's feet still overlap an adjacent ledge.
        List<VoxelShape> ledge = List.of(cube(0, 65, 0), cube(1, 66, 0));
        Body stuck = new Body(0.82, 67, 0.5, -0.08, true), corrected = stuck;
        for (int tick = 0; tick < 30; tick++) { stuck = step(stuck, ledge, false); corrected = step(corrected, ledge, true); }
        check(stuck.y == 67 && stuck.ground, "fixture reproduces feet caught on neighboring block");
        check(Math.abs(corrected.x - 0.5) < 0.001 && corrected.y == 66 && corrected.ground,
            "movement centering releases feet and lands one level lower without teleporting");

        var shapes = new ArrayList<VoxelShape>();
        for (int y = 63; y <= 66; y++) shapes.add(cube(0, y, 0));
        Body body = new Body(0.5, 67, 0.5, -0.08, true);
        int completed = 0; boolean premature = false, unsafe = false;
        for (int supportY = 66; supportY >= 64; supportY--) {
            check(TreeDescent.decide(supportY, body.y, body.ground, true, TreeDescent.BlockStatus.OWNED,
                TreeDescent.Landing.SUPPORTED) == TreeDescent.Action.MINE, "begin next step only from landed position");
            shapes.remove(shapes.size() - 1); // Local prediction precedes acknowledgement by ten ticks.
            boolean done = false;
            for (int tick = 0; tick < 40; tick++) {
                var status = tick < 10 ? TreeDescent.BlockStatus.PREDICTED_AIR : TreeDescent.BlockStatus.REMOVED;
                var action = TreeDescent.decide(supportY, body.y, body.ground, true, status, TreeDescent.Landing.SUPPORTED);
                unsafe |= action == TreeDescent.Action.UNSUPPORTED || action == TreeDescent.Action.REPLACED;
                if (action == TreeDescent.Action.COMPLETE) {
                    premature |= tick < 10 || !body.ground; completed++; done = true; break;
                }
                body = step(body, shapes, true);
            }
            if (!done) throw new AssertionError("Descent did not complete layer " + supportY);
        }
        check(completed == 3 && body.y == 64 && body.ground, "three-layer return reaches natural ground");
        check(!premature && !unsafe, "delayed updates neither skip landing nor trigger false unsafe stop");
        System.out.println("TREE_COLLISION_RUNTIME_CHECKS PASS checks=" + checks + " edgeCatchReproduced=true threeLayerReturn=true");
    }
}
