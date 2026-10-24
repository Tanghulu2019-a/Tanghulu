package dev.tanghulu.ancientapple.mixin;

import dev.tanghulu.ancientapple.integration.XaeroBridge;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The module already renders the apple/name/beam; suppress only its duplicate Xaero icon. */
@Pseudo
@Mixin(targets="xaero.hud.minimap.waypoint.render.world.WaypointWorldRenderReader",remap=false)
public abstract class XaeroWorldAppleMixin {
    @Inject(method="isHidden(Lxaero/common/minimap/waypoints/Waypoint;Lxaero/hud/minimap/waypoint/render/world/WaypointWorldRenderContext;)Z",
        at=@At("HEAD"),cancellable=true,remap=false,require=0)
    private void aah$noDuplicate(@Coerce Object waypoint,@Coerce Object context,CallbackInfoReturnable<Boolean> ci) {
        if(XaeroBridge.isOwnedWaypoint(waypoint))ci.setReturnValue(true);
    }
}
