package dev.tanghulu.addon.mixin;

import dev.tanghulu.addon.modules.InformationTextFilter;
import net.minecraft.block.entity.SignText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(SignText.class)
public abstract class FilteredSignTextMixin {
    @Shadow public abstract Text[] getMessages(boolean filtered);

    @Inject(method = "getOrderedMessages", at = @At("HEAD"), cancellable = true)
    private void tanghulu$hideSign(boolean filtered, Function<Text, OrderedText> orderer,
                                  CallbackInfoReturnable<OrderedText[]> cir) {
        Text[] original = getMessages(filtered);
        Text[] display = InformationTextFilter.filterSign(original);
        if (display == null) {
            // Do not replace the sign's raw text or its cached orderedMessages.
            cir.setReturnValue(new OrderedText[] { OrderedText.EMPTY, OrderedText.EMPTY, OrderedText.EMPTY, OrderedText.EMPTY });
        } else if (display != original) {
            OrderedText[] ordered = new OrderedText[display.length];
            for (int i = 0; i < display.length; i++) ordered[i] = orderer.apply(display[i]);
            cir.setReturnValue(ordered);
        }
    }
}
