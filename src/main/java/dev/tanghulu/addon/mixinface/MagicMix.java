package dev.tanghulu.addon.mixinface;

import dev.tanghulu.addon.modules.SeedMine;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class MagicMix {


    public static List<BlockPos> oreGoals = new ArrayList<>();



    public static boolean oreSimBaritone() {

        SeedMine oreSim = Modules.get().get(SeedMine.class);
        if (oreSim == null || !oreSim.baritone())
            return false;

        return true;
    }
}
