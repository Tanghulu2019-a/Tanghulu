package dev.tanghulu.addon.util;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

/** Model-space bone endpoints, transformed only locally to preserve world-coordinate precision. */
public final class PlayerSkeletonGeometry {
    private PlayerSkeletonGeometry() {}

    @FunctionalInterface
    public interface LineConsumer {
        void line(Vector3f from, Vector3f to);
    }

    public static void draw(PlayerEntityModel model, MatrixStack matrices, boolean slim, LineConsumer lines) {
        matrices.push();
        try {
            model.getRootPart().applyTransform(matrices);
            Vector3f neck = point(matrices, model.head, 0, 0);
            Vector3f headTop = point(matrices, model.head, 0, -8);
            Vector3f chest = point(matrices, model.body, 0, 0);
            Vector3f waist = point(matrices, model.body, 0, 12);
            // Arm cuboids are offset from their shoulder pivots by half/one model pixel.
            float armCenter = slim ? 0.5f : 1f;
            Vector3f leftShoulder = point(matrices, model.leftArm, armCenter, 0);
            Vector3f rightShoulder = point(matrices, model.rightArm, -armCenter, 0);
            Vector3f leftHip = point(matrices, model.leftLeg, 0, 0);
            Vector3f rightHip = point(matrices, model.rightLeg, 0, 0);

            lines.line(neck, headTop);
            lines.line(neck, chest);
            lines.line(chest, waist);
            lines.line(chest, leftShoulder);
            lines.line(chest, rightShoulder);
            lines.line(leftShoulder, point(matrices, model.leftArm, armCenter, 10));
            lines.line(rightShoulder, point(matrices, model.rightArm, -armCenter, 10));
            lines.line(waist, leftHip);
            lines.line(waist, rightHip);
            lines.line(leftHip, point(matrices, model.leftLeg, 0, 12));
            lines.line(rightHip, point(matrices, model.rightLeg, 0, 12));
        } finally {
            matrices.pop();
        }
    }

    private static Vector3f point(MatrixStack matrices, ModelPart part, float x, float y) {
        matrices.push();
        try {
            part.applyTransform(matrices);
            return matrices.peek().getPositionMatrix().transformPosition(new Vector3f(x / 16f, y / 16f, 0));
        } finally {
            matrices.pop();
        }
    }
}
