package dev.tanghulu.addon.modules;

import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static dev.tanghulu.addon.util.ChatUtils.sendMsg;

@SuppressWarnings("unused")
public class TanghuluModule extends Module {
    private boolean active;
    private final int priority;
    protected MinecraftClient mc = MinecraftClient.getInstance();


    public TanghuluModule(Category Category, String name, String desc) {
        super(Category, name, desc);
        this.priority = 100;
        mc = MinecraftClient.getInstance();

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

}
