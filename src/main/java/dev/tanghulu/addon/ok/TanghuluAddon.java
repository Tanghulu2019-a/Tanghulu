package dev.tanghulu.addon.ok;

import dev.tanghulu.addon.TanghuluApp;
import dev.tanghulu.ancientapple.AncientAppleAddon;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.HudGroup;

/** Single Meteor entrypoint for all Tanghulu features. */
public final class TanghuluAddon extends MeteorAddon {
    public static final HudGroup HUD_GROUP = new HudGroup("Tanghulu");

    @Override public void onInitialize() {
        // Cover direct ChatUtils calls as well as inherited module/command feedback.
        meteordevelopment.meteorclient.utils.player.ChatUtils.registerCustomPrefix(
            getPackage(), dev.tanghulu.addon.util.ChatUtils::getPrefix);
        new AncientAppleAddon().onInitialize();
        TanghuluApp.init();
    }

    @Override public void onRegisterCategories() {
        TanghuluApp.onRegisterCategories();
    }

    @Override public String getPackage() { return "dev.tanghulu"; }
}
