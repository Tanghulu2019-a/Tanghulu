package dev.tanghulu.ancientapple.prediction;

import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.util.ApiServices;
import net.minecraft.util.Util;
import java.net.Proxy;
import java.nio.file.Path;

/** This class and all Minecraft classes are loaded in TerrainBridge's isolated loader. */
public final class ScratchBootstrap {
    public static ScratchTerrain open(Path directory,long seed) throws Exception {
        SharedConstants.createGameVersion(); Bootstrap.initialize();
        var api=ApiServices.create(new YggdrasilAuthenticationService(Proxy.NO_PROXY),directory.toFile());
        return ScratchTerrain.open(directory,seed,api);
    }
    public static void finish(ScratchTerrain context) {
        try { context.close(); } finally { Util.shutdownExecutors(); }
    }
}
