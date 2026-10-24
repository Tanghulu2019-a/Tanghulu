package dev.tanghulu.ancientapple.mixin;

import dev.tanghulu.ancientapple.integration.XaeroAppleRenderer;
import dev.tanghulu.ancientapple.integration.XaeroBridge;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="xaero.map.mods.gui.WaypointRenderer",remap=false)
public abstract class XaeroFullMapAppleMixin {
    @Inject(method="renderElement(Lxaero/map/mods/gui/Waypoint;ZDFDDLxaero/map/element/render/ElementRenderInfo;Lxaero/map/element/MapElementGraphics;Lnet/minecraft/class_4597$class_4598;Lxaero/map/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;)Z",
        at=@At("HEAD"),cancellable=true,remap=false,require=0)
    private void aah$fullMapApple(@Coerce Object waypoint,boolean hovered,double optionalScale,float scale,double x,double y,
        @Coerce Object info,@Coerce Object graphics,VertexConsumerProvider.Immediate buffers,@Coerce Object provider,CallbackInfoReturnable<Boolean> ci) {
        if(XaeroAppleRenderer.drawFullMap(this,graphics,waypoint,hovered,scale,x,y))ci.setReturnValue(false);
    }
    @Inject(method="renderElementShadow(Lxaero/map/mods/gui/Waypoint;ZFDDLxaero/map/element/render/ElementRenderInfo;Lxaero/map/element/MapElementGraphics;Lnet/minecraft/class_4597$class_4598;Lxaero/map/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;)V",
        at=@At("HEAD"),cancellable=true,remap=false,require=0)
    private void aah$noOldPinShadow(@Coerce Object waypoint,boolean hovered,float scale,double x,double y,
        @Coerce Object info,@Coerce Object graphics,VertexConsumerProvider.Immediate buffers,@Coerce Object provider,CallbackInfo ci) {
        if(XaeroBridge.isOwnedWaypoint(waypoint))ci.cancel();
    }
}
