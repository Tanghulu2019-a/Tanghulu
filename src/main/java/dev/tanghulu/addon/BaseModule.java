package dev.tanghulu.addon;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import dev.tanghulu.addon.mixin.IClientWorld;
import dev.tanghulu.addon.modules.MEnum;
import meteordevelopment.meteorclient.pathing.NopPathManager;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static dev.tanghulu.addon.util.ChatUtils.sendMsg;

public abstract class BaseModule extends Module implements MEnum {

    public static final Category CATEGORY = dev.tanghulu.ancientapple.AncientAppleAddon.CATEGORY;
    // Keep existing module references compatible while sharing one category instance.
    public static final Category CATEGORY_TANGHULU_BUILD = CATEGORY;
    public static final Category CATEGORY_TANGHULU_COMBAT = CATEGORY;

    public MinecraftClient mc = MinecraftClient.getInstance();

    IBaritone baritone;

    public BaseModule(Category category, String name, String description, String... aliases) {
        super(category, name, description, aliases);
    }

    public BaseModule(Category category, String name, String desc) {
        super(category, name, desc);
        initBaritone();
    }

    public BaseModule(String name, String desc) {
        super(CATEGORY, name, desc);
    }

    @Override
    public void onActivate() {
        super.onActivate();
        if (mc == null) {
            mc = MinecraftClient.getInstance();
        }
        initBaritone();
    }

    private void initBaritone() {
        try {
            if (!(PathManagers.get() instanceof NopPathManager)) {
                baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            }
        } catch (Exception e) {
            error("请安装Baritone!");
        }
    }

    public void sendToggledMsg() {
        Text onMsg = Text.empty().setStyle(Style.EMPTY.withFormatting(Formatting.GREEN)).append("ON");
        Text offMsg = Text.empty().setStyle(Style.EMPTY.withFormatting(Formatting.RED)).append("OFF");
        MutableText toggledMsg = Text.empty();
        toggledMsg.append(Text.empty().setStyle(Style.EMPTY.withFormatting(Formatting.WHITE)).append(title));
        toggledMsg.append(" ");
        toggledMsg.append(Text.empty().setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)).append("toggled "));
        toggledMsg.append((isActive() ? onMsg : offMsg));

        sendMsg(toggledMsg, hashCode());
    }

    public static void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null || mc.world == null) return;
        try (PendingUpdateManager pendingUpdateManager = ((IClientWorld) mc.world).getPendingManager().incrementSequence()) {
            int i = pendingUpdateManager.getSequence();
            mc.getNetworkHandler().sendPacket(packetCreator.predict(i));
        }
    }
}
