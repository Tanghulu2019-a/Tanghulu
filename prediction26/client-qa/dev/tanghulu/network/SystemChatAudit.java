package dev.tanghulu.network;

import com.mojang.serialization.Lifecycle;
import dev.tanghulu.addon.util.SystemChatCompatibility;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntryInfo;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import java.nio.file.*;
import java.util.*;

public final class SystemChatAudit {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        // Reproduce a translated older server registry: known enchantments, no 1.21.11 lunge.
        var enchantments = new SimpleRegistry<Enchantment>(RegistryKeys.ENCHANTMENT, Lifecycle.stable());
        BuiltinRegistries.createWrapperLookup().getOrThrow(RegistryKeys.ENCHANTMENT).streamEntries()
            .filter(e -> !e.registryKey().getValue().getPath().equals("lunge"))
            .forEach(e -> enchantments.add(e.registryKey(), e.value(), RegistryEntryInfo.DEFAULT));
        enchantments.freeze();
        List<Registry<?>> registries = new ArrayList<>();
        DynamicRegistryManager.of(Registries.REGISTRIES).streamAllRegistries().forEach(e -> registries.add(e.value()));
        registries.add(enchantments);
        var manager = new DynamicRegistryManager.ImmutableImpl(registries);
        var vanilla = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC;
        var compatible = SystemChatCompatibility.wrap(vanilla);
        Path root = Path.of(args[0]);
        boolean legacy = System.getProperty("tanghulu.qa.minecraft", "1.21.11").equals("1.21.4");
        var fixture = parse(Files.readString(root.resolve("build-support/fixtures/" + (legacy ? "system-chat-1.21.4.snbt" : "system-chat-spear.snbt"))));
        var knownEnchantments = parse(fixture.toString().replace("\"minecraft:lunge\":3,", ""));
        check(read(vanilla, manager, knownEnchantments, false) != null, "Removing unavailable lunge must fix original fixture");

        try {
            read(vanilla, manager, fixture, false);
            throw new AssertionError("Log fixture must fail with the original codec");
        } catch (DecoderException expected) { check(true, "Original failure reproduced"); }
        Text recovered = read(compatible, manager, fixture, true);
        check(recovered.getString().contains("Victim") && recovered.getString().contains("Netherite Spear"), "Death message lost");
        check(recovered.getStyle().getColor().getRgb() == 0xDA8E8E, "Color lost");
        var ops = RegistryOps.of(NbtOps.INSTANCE, manager);
        String encoded = TextCodecs.CODEC.encodeStart(ops, recovered).getOrThrow().toString();
        check(!encoded.contains("show_item"), "Broken item hover survived");
        check(encoded.contains("show_entity") && encoded.contains("suggest_command"), "Valid entity hover or click lost");

        var valid = parse(legacy ? "{text:'Valid',hoverEvent:{action:'show_item',contents:{id:'minecraft:stone'}}}"
            : "{text:'Valid',hover_event:{action:'show_item',id:'minecraft:stone'}}");
        check(read(compatible, manager, valid, false).equals(read(vanilla, manager, valid, false)), "Valid item hover changed");
        var mixed = new NbtList();
        mixed.add(fixture.copy());
        mixed.add(valid.copy());
        String mixedEncoded = TextCodecs.CODEC.encodeStart(ops, read(compatible, manager, mixed, false)).getOrThrow().toString();
        check(mixedEncoded.contains("minecraft:stone"), "Valid item hover lost during fallback");
        var plain = parse("{text:'正常消息',bold:1b}");
        check(read(compatible, manager, plain, true).equals(read(vanilla, manager, plain, true)), "Normal message changed");
        var invalid = parse("{text:'broken',color:'not_a_color'}");
        try {
            read(compatible, manager, invalid, false);
            throw new AssertionError("Unrelated errors must propagate");
        } catch (DecoderException expected) { check(true, "Unrelated errors propagate"); }

        RegistryByteBuf output = new RegistryByteBuf(Unpooled.buffer(), manager);
        RegistryByteBuf baseline = new RegistryByteBuf(Unpooled.buffer(), manager);
        try {
            compatible.encode(output, recovered);
            vanilla.encode(baseline, recovered);
            check(output.equals(baseline), "Encoding changed");
        } finally { output.release(); baseline.release(); }
        Files.createDirectories(root.resolve("build/reports"));
        Files.writeString(root.resolve("build/reports/system-chat-audit.json"), "{\"passed\":true,\"checks\":" + checks + ",\"inGameTested\":false}");
        System.out.println("SYSTEM_CHAT_AUDIT passed checks=" + checks);
    }

    // QA runs against named jars; the parser entrypoint was renamed after 1.21.4.
    private static NbtCompound parse(String input) throws Exception {
        try { return (NbtCompound) StringNbtReader.class.getMethod("readCompound", String.class).invoke(null, input); }
        catch (NoSuchMethodException legacy) { return (NbtCompound) StringNbtReader.class.getMethod("parse", String.class).invoke(null, input); }
    }

    private static Text read(PacketCodec<RegistryByteBuf, Text> codec, DynamicRegistryManager manager, NbtElement nbt, boolean overlay) {
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), manager);
        try {
            // Raw network NBT plus the following overlay byte catches reader-index mistakes.
            buf.writeByte(42);
            buf.writeNbt(nbt);
            buf.writeBoolean(overlay);
            buf.readerIndex(1);
            Text text = codec.decode(buf);
            check(buf.readBoolean() == overlay && buf.readableBytes() == 0, "Wrong packet boundary");
            return text;
        } finally { buf.release(); }
    }
}
