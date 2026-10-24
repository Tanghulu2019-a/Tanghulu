package dev.tanghulu.ancientapple.mixin;

import dev.tanghulu.ancientapple.integration.XaeroAppleRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** String targets keep Xaero entirely optional; descriptors match its public 26.6 API. */
@Pseudo
@Mixin(targets="xaero.hud.minimap.waypoint.render.WaypointMapRenderer",remap=false)
public abstract class XaeroMapAppleMixin {
    @Inject(method="drawIcon(Lxaero/hud/minimap/element/render/MinimapElementGraphics;Lxaero/common/minimap/render/MinimapRendererHelper;Lxaero/common/minimap/waypoints/Waypoint;IIILxaero/lib/client/graphics/XaeroBufferProvider;Lnet/minecraft/class_4588;Lnet/minecraft/class_4588;)V",
        at=@At("HEAD"),cancellable=true,remap=false,require=0)
    private void aah$mapApple(@Coerce Object graphics,@Coerce Object helper,@Coerce Object waypoint,int x,int y,int opacity,
        @Coerce Object provider,VertexConsumer background,VertexConsumer texture,CallbackInfo ci) {
        if(XaeroAppleRenderer.drawMap(graphics,waypoint,x,y,opacity))ci.cancel();
    }
    @Inject(method="drawIconGUI(Lnet/minecraft/class_332;Lxaero/common/minimap/waypoints/Waypoint;III)V",
        at=@At("HEAD"),cancellable=true,remap=false,require=0)
    private void aah$guiApple(DrawContext context,@Coerce Object waypoint,int x,int y,int opacity,CallbackInfo ci) {
        if(XaeroAppleRenderer.drawGui(context,waypoint,x,y,opacity))ci.cancel();
    }
}
