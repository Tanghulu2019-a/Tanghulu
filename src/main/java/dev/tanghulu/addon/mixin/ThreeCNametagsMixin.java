package dev.tanghulu.addon.mixin;

import dev.tanghulu.addon.modules.ThreeCNewbieCheck;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Nametags;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extend both layout and rendering so the badge cannot overlap health, ping or distance. */
@Mixin(value = Nametags.class, remap = false)
public abstract class ThreeCNametagsMixin {
    @Unique private String tanghulu$badge = "";
    @Unique private final Color tanghulu$newbieColor = new Color(85, 255, 85);
    @Unique private final Color tanghulu$expertColor = new Color(255, 85, 85);
    @Unique private Color tanghulu$badgeColor;

    @Inject(method = "renderNametagPlayer", at = @At("HEAD"))
    private void tanghulu$selectBadge(Render2DEvent event, PlayerEntity player, boolean shadow, CallbackInfo ci) {
        tanghulu$badge = "";
        ThreeCNewbieCheck module = Modules.get().get(ThreeCNewbieCheck.class);
        if (module == null) return;
        var data = module.nametagResult(player);
        if (data == null) return;
        boolean newbie = module.isNewbie(data);
        tanghulu$badge = newbie ? " 萌新" : " 糕手";
        tanghulu$badgeColor = newbie ? tanghulu$newbieColor : tanghulu$expertColor;
    }

    @Redirect(method = "renderNametagPlayer", at = @At(value = "INVOKE", target =
        "Lmeteordevelopment/meteorclient/renderer/text/TextRenderer;getWidth(Ljava/lang/String;Z)D", ordinal = 1))
    private double tanghulu$badgeWidth(TextRenderer renderer, String name, boolean shadow) {
        return renderer.getWidth(name, shadow) + (tanghulu$badge.isEmpty() ? 0 : renderer.getWidth(tanghulu$badge, shadow));
    }

    @Redirect(method = "renderNametagPlayer", at = @At(value = "INVOKE", target =
        "Lmeteordevelopment/meteorclient/renderer/text/TextRenderer;render(Ljava/lang/String;DDLmeteordevelopment/meteorclient/utils/render/color/Color;Z)D", ordinal = 1))
    private double tanghulu$renderBadge(TextRenderer renderer, String name, double x, double y, Color color, boolean shadow) {
        double next = renderer.render(name, x, y, color, shadow);
        return tanghulu$badge.isEmpty() ? next : renderer.render(tanghulu$badge, next, y, tanghulu$badgeColor, shadow);
    }
}
