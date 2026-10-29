package dev.tanghulu.ancientapple.core;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Shared by seed predictions and authoritative container observations. */
public final class TargetLoot {
    private TargetLoot() {}
    public static int swiftSneakBooks(ItemStack stack) {
        if (!stack.isOf(Items.ENCHANTED_BOOK)) return 0;
        var enchantments = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (enchantments != null) {
            for (var entry : enchantments.getEnchantmentEntries())
                if (entry.getKey().matchesKey(Enchantments.SWIFT_SNEAK) && entry.getIntValue() == 3)
                    return stack.getCount();
        }
        return 0;
    }
    public static int count(ItemStack stack) {
        return stack.isOf(Items.ENCHANTED_GOLDEN_APPLE) ? stack.getCount() : swiftSneakBooks(stack);
    }
    public static boolean selectedForQuickLoot(ItemStack stack, boolean apples, boolean bottles, boolean carrots, boolean books) {
        return !stack.isEmpty() && ((apples && stack.isOf(Items.ENCHANTED_GOLDEN_APPLE))
            || (bottles && stack.isOf(Items.EXPERIENCE_BOTTLE))
            || (carrots && stack.isOf(Items.GOLDEN_CARROT))
            || (books && swiftSneakBooks(stack)>0));
    }
}
