package dev.tanghulu.addon.hud;

import dev.tanghulu.addon.ok.TanghuluAddon;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class HudTanghulu extends HudElement {
    /**
     * The {@code name} parameter should be in kebab-case.
     */
    public static final HudElementInfo<HudTanghulu> INFO = new HudElementInfo<>(TanghuluAddon.HUD_GROUP, "tanghulu", "HUD element tanghulu.", HudTanghulu::new);

    public HudTanghulu() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        setSize(renderer.textWidth("Tanghulu element", true), renderer.textHeight(true));

        // Render background
        renderer.quad(x, y, getWidth(), getHeight(), Color.LIGHT_GRAY);

        // Render text
        renderer.text("Tanghulu element", x, y, Color.WHITE, true);
    }
}
