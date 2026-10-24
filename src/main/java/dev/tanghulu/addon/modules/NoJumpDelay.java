package dev.tanghulu.addon.modules;

import dev.tanghulu.addon.BaseModule;
import dev.tanghulu.addon.mixin.LivingEntityAccessor;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;

public class NoJumpDelay extends BaseModule {
    public NoJumpDelay() {
        super("自动顶头跳", "可2格高顶头跳，无延迟，非常湿滑。");
    }

    @EventHandler
    private void onUpdate(TickEvent.Post e) {
        LivingEntityAccessor accessor = (LivingEntityAccessor) this.mc.player;
        accessor.setJumpCooldown(0);
    }
}
