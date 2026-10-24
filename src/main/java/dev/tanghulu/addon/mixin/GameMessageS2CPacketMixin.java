package dev.tanghulu.addon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.tanghulu.addon.util.SystemChatCompatibility;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameMessageS2CPacket.class)
public abstract class GameMessageS2CPacketMixin {
    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "FIELD",
        target = "Lnet/minecraft/text/TextCodecs;UNLIMITED_REGISTRY_PACKET_CODEC:Lnet/minecraft/network/codec/PacketCodec;"))
    private static PacketCodec<RegistryByteBuf, Text> tanghulu$compatibleSystemChat(PacketCodec<RegistryByteBuf, Text> original) {
        return SystemChatCompatibility.wrap(original);
    }
}
