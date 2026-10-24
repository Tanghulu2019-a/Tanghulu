package dev.tanghulu.stash;

import dev.tanghulu.stash.hud.EntityList;
import dev.tanghulu.stash.hud.Weather;
import dev.tanghulu.stash.modules.*;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.loader.api.FabricLoader;

/** Registered by the existing Tanghulu entrypoint; optional APIs stay isolated. */
public final class Addon {
    public static final Category CATEGORY = dev.tanghulu.addon.BaseModule.CATEGORY;
    public static final HudGroup HUD_GROUP = dev.tanghulu.addon.ok.TanghuluAddon.HUD_GROUP;

    private Addon() {}

    public static void initialize() {
        var modules = Modules.get();
        modules.add(StashLocalization.localize(new AutoLogPlus()));
        modules.add(StashLocalization.localize(new VanityESP()));
        modules.add(StashLocalization.localize(new Pitch40Util()));
        var loader = FabricLoader.getInstance();
        if (loader.isModLoaded("xaeroworldmap") && loader.isModLoaded("xaerominimap")
            && loader.isModLoaded("xaeroplus")) {
            modules.add(StashLocalization.localize(new BetterStashFinder()));
        }
        if (BaritoneUtils.IS_AVAILABLE) {
            var flight = new ElytraFlyPlusPlus();
            modules.add(StashLocalization.localize(flight));
            FlightHooks.bind(flight::enabled);
        }
        Hud.get().register(Weather.INFO);
        Hud.get().register(EntityList.INFO);
    }
}
