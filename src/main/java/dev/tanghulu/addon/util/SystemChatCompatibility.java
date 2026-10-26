package dev.tanghulu.addon.util;

import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.RegistryOps;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

/** Retry system chat with invalid item tooltips removed, preserving the message itself. */
public final class SystemChatCompatibility {
    private SystemChatCompatibility() {}

    public static PacketCodec<RegistryByteBuf, Text> wrap(PacketCodec<RegistryByteBuf, Text> vanilla) {
        return new PacketCodec<>() {
            @Override
            public Text decode(RegistryByteBuf buf) {
                int start = buf.readerIndex();
                try {
                    return vanilla.decode(buf);
                } catch (DecoderException original) {
                    int failedIndex = buf.readerIndex();
                    try {
                        buf.readerIndex(start);
                        NbtElement nbt = PacketByteBuf.readNbt(buf, NbtSizeTracker.ofUnlimitedBytes());
                        RegistryOps<NbtElement> ops = RegistryOps.of(NbtOps.INSTANCE, buf.getRegistryManager());
                        if (removeInvalidItemHovers(nbt, ops)) {
                            var recovered = TextCodecs.CODEC.parse(ops, nbt);
                            if (recovered.isSuccess()) return recovered.getOrThrow();
                        }
                    } catch (RuntimeException retryFailure) {
                        original.addSuppressed(retryFailure);
                    }
                    buf.readerIndex(failedIndex);
                    throw original;
                }
            }

            @Override
            public void encode(RegistryByteBuf buf, Text text) {
                vanilla.encode(buf, text);
            }
        };
    }

    private static boolean removeInvalidItemHovers(NbtElement element, RegistryOps<NbtElement> ops) {
        boolean changed = false;
        if (element instanceof NbtCompound compound) {
            for (String hoverKey : new String[] {"hover_event", "hoverEvent"}) {
                if (compound.get(hoverKey) instanceof NbtCompound hover
                    && hover.getString("action", "").equals("show_item")
                    && HoverEvent.CODEC.parse(ops, hover).error().isPresent()) {
                    compound.remove(hoverKey);
                    changed = true;
                }
            }
            // Only visit text children; item components and arbitrary custom NBT are not text.
            for (String key : new String[] {"extra", "with", "separator"}) {
                NbtElement child = compound.get(key);
                if (child != null) changed |= removeInvalidItemHovers(child, ops);
            }
        } else if (element instanceof NbtList list) {
            for (NbtElement child : list) changed |= removeInvalidItemHovers(child, ops);
        }
        return changed;
    }
}
