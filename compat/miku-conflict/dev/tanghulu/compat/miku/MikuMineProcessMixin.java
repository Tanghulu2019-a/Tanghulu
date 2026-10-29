package dev.tanghulu.compat.miku;

import baritone.api.utils.BlockOptionalMetaLookup;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Test-only reproduction of Meteor Miku's required redirect at the reported call site. */
@Pseudo
@Mixin(targets = "baritone.process.MineProcess")
public class MikuMineProcessMixin {
    @Redirect(method = "a(Lbaritone/pathing/movement/CalculationContext;Lbaritone/api/utils/BlockOptionalMetaLookup;Ljava/util/List;Lnet/minecraft/util/math/BlockPos;)Z",
        at = @At(value = "INVOKE", target = "Lbaritone/api/utils/BlockOptionalMetaLookup;has(Lnet/minecraft/block/BlockState;)Z"))
    private static boolean tanghuluMiku$prune(BlockOptionalMetaLookup instance, BlockState state) {
        return instance.has(state);
    }
}
