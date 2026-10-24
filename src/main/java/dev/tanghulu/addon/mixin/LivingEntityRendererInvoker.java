package dev.tanghulu.addon.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuse vanilla pose transforms, including the player renderer's overrides. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker {
    @Invoker("setupTransforms")
    void tanghulu$setupTransforms(LivingEntityRenderState state, MatrixStack matrices, float bodyYaw, float baseScale);

    @Invoker("scale")
    void tanghulu$scale(LivingEntityRenderState state, MatrixStack matrices);
}
