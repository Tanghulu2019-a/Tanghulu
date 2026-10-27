package dev.tanghulu.addon.mixin;

import dev.tanghulu.addon.modules.InformationTextFilter;
import net.minecraft.client.render.entity.DisplayEntityRenderer;
import net.minecraft.client.render.entity.state.TextDisplayEntityRenderState;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisplayEntityRenderer.TextDisplayEntityRenderer.class)
public abstract class FilteredTextDisplayMixin {
    @Shadow private DisplayEntity.TextDisplayEntity.TextLines getLines(Text text, int width) {
        throw new AssertionError();
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity;Lnet/minecraft/client/render/entity/state/TextDisplayEntityRenderState;F)V",
        at = @At("TAIL"))
    private void tanghulu$hideDisplay(DisplayEntity.TextDisplayEntity entity, TextDisplayEntityRenderState state,
                                     float tickProgress, CallbackInfo ci) {
        if (state.data == null) return;
        var data = state.data;
        Text display = InformationTextFilter.filter(InformationTextFilter.Source.TextDisplay, data.text());
        if (display == data.text()) return;
        // Only this frame's state is masked; tracked data and split-line cache stay intact.
        if (display.getString().isEmpty()) {
            state.data = null;
        } else {
            state.data = new DisplayEntity.TextDisplayEntity.Data(display, data.lineWidth(),
                data.textOpacity(), data.backgroundColor(), data.flags());
            state.textLines = getLines(display, data.lineWidth());
        }
    }
}
