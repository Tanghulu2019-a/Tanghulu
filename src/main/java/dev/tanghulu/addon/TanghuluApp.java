package dev.tanghulu.addon;

import dev.tanghulu.addon.commands.CommandTanghulu;
import dev.tanghulu.addon.hud.HudTanghulu;
import dev.tanghulu.addon.modules.*;
import dev.tanghulu.addon.util.timer.TickTimerManager;
import lombok.extern.slf4j.Slf4j;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.renderer.Fonts;
import meteordevelopment.meteorclient.renderer.text.FontFace;
import meteordevelopment.meteorclient.renderer.text.FontFamily;
import meteordevelopment.meteorclient.renderer.text.FontInfo;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

@Slf4j
public class TanghuluApp {

    public static void onRegisterCategories() {
        Modules.registerCategory(BaseModule.CATEGORY);
    }

    public static void init() {
        log.info("Initializing Meteor Addon Tanghulu");

        TickTimerManager.INSTANCE.getTickTime();
        registerModules();
        ChatUtils.warning("Tanghulu插件群：1013297171");
        Commands.add(new CommandTanghulu());

        Hud.get().register(HudTanghulu.INFO);

        configureFont();
    }

    private static void registerModules() {
        Modules modules = Modules.get();
        modules.add(new ClearXaeroWaypoints());
        modules.add(new Beyblade());
        modules.add(new Confetti());
        modules.add(new SignTextEsp());
        modules.add(new SafeFire());
        modules.add(new NewChunks());
        modules.add(new PlayerAlert());
        modules.add(new PlayerSkeleton());
        modules.add(new ThreeCNewbieCheck());
        modules.add(new AutoTrashModule());
        modules.add(new AutoMiner());
        modules.add(new TreeAura());
        modules.add(new SeedMine());
        modules.add(new OnekeyFireWork());
        modules.add(new StructureFinder());
        modules.add(new MushroomFinder());
        modules.add(new ElytraFinder());
        modules.add(new AutoWalk());
        modules.add(new AutoLog());
        modules.add(new ElytraUnbreak());
        modules.add(new RoadBuilder());
        modules.add(new FarmHelper());
        modules.add(new AutoXP());
        modules.add(new NoJumpDelay());
        modules.add(new Scaffold());
        modules.add(new AutoSlab());
        modules.add(new KillAuraTanghulu());
        modules.add(new ElytraArmorSwitch());
    }

    private static void configureFont() {
        try {
            Config config = Config.get();
            config.customFont.set(true);
            // 查找并设置Dengxian字体
            FontFamily dengxianFamily = Fonts.getFamily("Dengxian");

            if (dengxianFamily != null) {
                FontFace dengxianFont = dengxianFamily.get(FontInfo.Type.Regular);
                if (dengxianFont != null) {
                    config.font.set(dengxianFont);
                }
            }
        } catch (Exception ignored) {
            // Keep the existing font if font configuration is unavailable.
        }
    }
}
