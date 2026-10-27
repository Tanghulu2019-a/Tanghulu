package dev.tanghulu.addon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.tanghulu.addon.modules.InformationTextFilter;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.systems.modules.render.Nametags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Nametags.class, remap = false)
public abstract class FilteredMeteorNametagsMixin {
    @Inject(method = "renderNametagPlayer", at = @At("HEAD"), cancellable = true)
    private void tanghulu$filterPlayer(Render2DEvent event, PlayerEntity player, boolean shadow, CallbackInfo ci) {
        if (InformationTextFilter.hides(InformationTextFilter.Source.EntityName, player.getDisplayName())) ci.cancel();
    }

    @Inject(method = "renderGenericLivingNametag", at = @At("HEAD"), cancellable = true)
    private void tanghulu$filterLiving(LivingEntity entity, boolean shadow, CallbackInfo ci) {
        if (InformationTextFilter.hides(InformationTextFilter.Source.EntityName, entity.getDisplayName())) ci.cancel();
    }

    @Inject(method = "renderGenericNametag", at = @At("HEAD"), cancellable = true)
    private void tanghulu$filterEntity(Entity entity, boolean shadow, CallbackInfo ci) {
        if (InformationTextFilter.hides(InformationTextFilter.Source.EntityName, entity.getDisplayName())) ci.cancel();
    }

    @ModifyExpressionValue(method = "renderNametagPlayer", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/text/Text;getString()Ljava/lang/String;", remap = true))
    private String tanghulu$maskPlayer(String name, Render2DEvent event, PlayerEntity player, boolean shadow) {
        return tanghulu$displayName(name, player);
    }

    @ModifyExpressionValue(method = "renderGenericLivingNametag", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/text/Text;getString()Ljava/lang/String;", remap = true))
    private String tanghulu$maskLiving(String name, LivingEntity entity, boolean shadow) {
        return tanghulu$displayName(name, entity);
    }

    @ModifyExpressionValue(method = "renderGenericNametag", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/text/Text;getString()Ljava/lang/String;", remap = true))
    private String tanghulu$maskEntity(String name, Entity entity, boolean shadow) {
        return tanghulu$displayName(name, entity);
    }

    @Unique
    private static String tanghulu$displayName(String renderedName, Entity entity) {
        var original = entity.getDisplayName();
        var display = InformationTextFilter.filter(InformationTextFilter.Source.EntityName, original);
        return display != original ? display.getString()
            : InformationTextFilter.filter(InformationTextFilter.Source.EntityName, renderedName);
    }
}
