package dev.tanghulu.compat.smoke;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.tanghulu.addon.modules.SeedMine;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.block.Blocks;
import java.lang.reflect.*;
import java.util.*;

/** Run the transformed handler without opening a window or connecting to a world. */
public final class BaritoneMixinRuntimeChecks {
    private static Field field(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Object unsafe = field(unsafeType, "theUnsafe").get(null);
        return type.cast(unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type));
    }

    @SuppressWarnings("unchecked")
    public static void run() throws Exception {
        Class<?> mine = Class.forName("baritone.process.MineProcess");
        Method handler = Arrays.stream(mine.getDeclaredMethods())
            .filter(m -> m.getName().contains("onPruneStream") && m.getParameterCount() == 3
                && m.getParameterTypes()[2] == Operation.class).findFirst().orElseThrow();
        handler.setAccessible(true);
        var systems = (Map<Class<?>, Object>) field(Systems.class, "systems").get(null);
        Object previous = systems.get(Modules.class);
        var modules = new Modules();
        var instances = (Map<Class<?>, Module>) field(Modules.class, "moduleInstances").get(modules);
        systems.put(Modules.class, modules);
        int[] calls = {0};
        var stone = Blocks.STONE.getDefaultState();
        try {
            for (boolean expected : List.of(false, true)) {
                Operation<Boolean> original = args -> {
                    calls[0]++;
                    if (args.length != 2 || args[0] != null || args[1] != stone)
                        throw new AssertionError("Original operation arguments changed");
                    return expected;
                };
                if (!handler.invoke(null, null, stone, original).equals(expected))
                    throw new AssertionError("Inactive SeedMine must preserve the original operation result");
            }
            if (calls[0] != 2) throw new AssertionError("Original operation was not called exactly once");
            var seedMine = allocate(SeedMine.class);
            field(Module.class, "active").set(seedMine, true);
            var enabled = allocate(BoolSetting.class);
            field(Setting.class, "value").set(enabled, true);
            field(SeedMine.class, "baritone").set(seedMine, enabled);
            instances.put(SeedMine.class, seedMine);
            Operation<Boolean> forbidden = args -> { throw new AssertionError("Active SeedMine must use predicted goals"); };
            if (!(Boolean) handler.invoke(null, null, stone, forbidden)
                || (Boolean) handler.invoke(null, null, Blocks.AIR.getDefaultState(), forbidden))
                throw new AssertionError("Active SeedMine must retain solid blocks and prune air");
            System.out.println("BARITONE_MIXIN_RUNTIME_CHECKS PASS delegateTrue=true delegateFalse=true activeSolid=true activeAir=false mikuConflict="
                + Boolean.getBoolean("tanghulu.smoke.mikuConflict"));
        } finally {
            if (previous == null) systems.remove(Modules.class); else systems.put(Modules.class, previous);
        }
    }
}
