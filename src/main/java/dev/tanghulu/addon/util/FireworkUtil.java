package dev.tanghulu.addon.util;

import dev.tanghulu.addon.BaseModule;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;

public final class FireworkUtil {
    private FireworkUtil() {}
    private static final net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
    public static void fire() {

        try {
            if (!mc.player.isOnGround()) {
                if (mc.player.getInventory().getStack(38).getItem().equals(Items.ELYTRA)) {
                    Item item = mc.player.getMainHandStack().getItem();
                    if (!TanghuluUtil.isArmor(item)) {
                        if (item == Items.FIREWORK_ROCKET) {
                            firework();
                        } else {
                            int fireworkSlot;
                            if ((fireworkSlot = InvUtils.findInHotbar(Items.FIREWORK_ROCKET).slot()) != -1) {
                                int old = Via.getSelectedSlot();
                                BagUtil.switchToSlot(fireworkSlot);
                                firework();
                                BagUtil.switchToSlot(old);

                                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                            } else if ((fireworkSlot = InvUtils.find(Items.FIREWORK_ROCKET).slot()) != -1) {
                                BagUtil.inventorySwap(fireworkSlot, Via.getSelectedSlot());
                                firework();
                                BagUtil.inventorySwap(fireworkSlot, Via.getSelectedSlot());
                                BagUtil.sync();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {

        }
    }

    private static void firework() {
        BaseModule.sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
    }
}
