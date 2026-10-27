package dev.tanghulu.addon.mixin;

import dev.tanghulu.addon.modules.InformationTextFilter;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class FilteredEntityLabelMixin {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void tanghulu$hideLabel(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
        var filtered = InformationTextFilter.filter(InformationTextFilter.Source.EntityName, state.displayName);
        if (filtered != state.displayName) state.displayName = filtered.getString().isEmpty() ? null : filtered;
    }
}
