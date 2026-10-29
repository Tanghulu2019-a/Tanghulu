package dev.tanghulu.addon.mixin;

import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.pathing.movement.CalculationContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.tanghulu.addon.mixinface.MagicMix;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "baritone.process.MineProcess")
public class MineProcessMixin {

    @Shadow(remap = false)
    private List<BlockPos> a; // knownOreLocations

    @Inject(method = "a(Ljava/util/List;Lbaritone/pathing/movement/CalculationContext;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void onRescan(List<BlockPos> already, CalculationContext context, CallbackInfo ci) {
        if (MagicMix.oreSimBaritone()) {
            a = new java.util.ArrayList<>(MagicMix.oreGoals);
            ci.cancel();
        }
    }

    // Chain with other addons (notably Meteor Miku) that redirect this same call.
    // Competing required @Redirects prevent MineProcess and BaritoneAPI from loading.
    @WrapOperation(method = "a(Lbaritone/pathing/movement/CalculationContext;Lbaritone/api/utils/BlockOptionalMetaLookup;Ljava/util/List;Lnet/minecraft/util/math/BlockPos;)Z",
        at = @At(value = "INVOKE", target = "Lbaritone/api/utils/BlockOptionalMetaLookup;has(Lnet/minecraft/block/BlockState;)Z"))
    private static boolean onPruneStream(BlockOptionalMetaLookup instance, BlockState blockState, Operation<Boolean> original) {
        if (!MagicMix.oreSimBaritone()) {
            return original.call(instance, blockState);
        }
        return !blockState.isAir();
    }

}
