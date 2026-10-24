package dev.tanghulu.ancientapple;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;

public final class AncientAppleAddon extends MeteorAddon {
    public static final Category CATEGORY=new Category("Tanghulu",Items.ENCHANTED_GOLDEN_APPLE.getDefaultStack());
    @Override public void onInitialize() {
        dev.tanghulu.ancientapple.gui.NoteSetting.registerRenderer();
        HunterModule module=new HunterModule(); Modules.get().add(module); Commands.add(new HunterCommand(module));
    }
    @Override public void onRegisterCategories() { Modules.registerCategory(CATEGORY); }
    @Override public String getPackage() { return "dev.tanghulu.ancientapple"; }
}
