package dev.tanghulu.ancientapple.core;

/** A click is not a receipt. Both inventory and container must acknowledge it. */
public record TransferReceipt(int beforePlayer, int beforeChest, int moved) {
    public boolean confirmed(int playerNow, int chestNow) {
        return moved>0 && playerNow>=beforePlayer+moved && chestNow<=beforeChest-moved;
    }
}
