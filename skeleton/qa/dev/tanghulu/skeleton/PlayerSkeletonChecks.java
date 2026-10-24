package dev.tanghulu.skeleton;

import dev.tanghulu.addon.util.PlayerSkeletonGeometry;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class PlayerSkeletonChecks {
    private record Bone(Vector3f from, Vector3f to) {}
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 1e-5f, message + ": " + actual + " != " + expected);
    }

    private static PlayerEntityModel model(boolean slim) {
        return new PlayerEntityModel(PlayerEntityModel.getTexturedModelData(Dilation.NONE, slim)
            .getRoot().createPart(64, 64), slim);
    }

    private static List<Bone> bones(PlayerEntityModel model, MatrixStack matrices, boolean slim) {
        List<Bone> result = new ArrayList<>();
        PlayerSkeletonGeometry.draw(model, matrices, slim, (from, to) -> result.add(new Bone(from, to)));
        return result;
    }

    public static void main(String[] args) {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
        var model = model(false);
        var matrices = new MatrixStack();
        var standing = bones(model, matrices, false);
        check(standing.size() == 11, "Connected head, torso, shoulders, arms, hips and legs");
        near(standing.get(0).to.y, -0.5f, "Head top");
        near(standing.get(2).to.y, 0.75f, "Waist");
        near(standing.get(9).to.y, 1.5f, "Left foot");
        near(standing.get(10).to.y, 1.5f, "Right foot");
        near(standing.get(5).to.x, 0.375f, "Normal left arm center");
        near(standing.get(6).to.x, -0.375f, "Normal right arm center");
        var slim = bones(model(true), matrices, true);
        near(slim.get(5).to.x, 5.5f / 16f, "Slim left arm center");
        near(slim.get(6).to.x, -5.5f / 16f, "Slim right arm center");

        model.head.pitch = (float) Math.PI / 2;
        model.leftArm.pitch = (float) Math.PI / 2;
        model.rightLeg.pitch = -(float) Math.PI / 2;
        var posed = bones(model, matrices, false);
        near(posed.get(0).to.z, -0.5f, "Looking up rotates head line");
        near(posed.get(5).to.z, 10f / 16f, "Arm follows its animated pitch");
        near(posed.get(10).to.z, -0.75f, "Leg follows its animated pitch");
        near(posed.get(9).to.z, 0, "Other leg is independent");

        // Exercise vanilla animation, then reuse the same model for another player's pose.
        var state = new PlayerEntityRenderState();
        state.sneaking = true;
        state.isInSneakingPose = true;
        model.setAngles(state);
        var sneaking = bones(model, matrices, false);
        check(sneaking.get(0).from.distanceSquared(standing.get(0).from) > 0.001f, "Sneak moves the head joint");
        check(sneaking.get(2).to.distanceSquared(standing.get(2).to) > 0.001f, "Sneak bends the spine");
        state.sneaking = false;
        state.isInSneakingPose = false;
        model.setAngles(state);
        near(model.body.pitch, 0, "Next player does not inherit crouching");

        matrices.scale(2, -2, 2);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
        Matrix4f original = new Matrix4f(matrices.peek().getPositionMatrix());
        var transformed = bones(model(false), matrices, false);
        near(transformed.get(0).to.y, 1, "Global scale and inversion apply to head");
        near(transformed.get(9).to.y, -3, "Global scale and inversion apply to feet");
        near(Math.abs(transformed.get(5).to.z), 0.75f, "Body yaw applies to arms");
        check(original.equals(matrices.peek().getPositionMatrix()), "Drawing restores caller's matrix");
        try {
            PlayerSkeletonGeometry.draw(model, matrices, false, (from, to) -> { throw new IllegalStateException("test"); });
            throw new AssertionError("Consumer must throw");
        } catch (IllegalStateException expected) {
            check(original.equals(matrices.peek().getPositionMatrix()), "Exception also restores caller's matrix");
        }
        double worldX = 29_999_999d;
        double endpoint = worldX + standing.get(5).to.x;
        check(Math.abs(endpoint - worldX - 0.375d) < 1e-8, "Distant world coordinates preserve sub-block precision");
        System.out.println("Player skeleton checks passed: " + checks);
    }
}
