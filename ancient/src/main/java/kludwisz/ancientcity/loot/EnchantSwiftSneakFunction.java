package kludwisz.ancientcity.loot;

import com.seedfinding.mccore.util.data.Pair;
import com.seedfinding.mcfeature.loot.LootContext;
import com.seedfinding.mcfeature.loot.function.LootFunction;
import com.seedfinding.mcfeature.loot.item.Item;
import com.seedfinding.mcfeature.loot.item.ItemStack;

public class EnchantSwiftSneakFunction implements LootFunction {
	
	public EnchantSwiftSneakFunction() {}

	@Override
	public ItemStack process(ItemStack baseStack, LootContext context) {
		Item newItem = baseStack.getItem();
		context.nextInt(1); // choose a "random" enchantment from "the list"

		int level = context.nextInt(3) + 1;
		newItem.addEnchantment(new Pair<>("swift_sneak", level));
		return new ItemStack(newItem, baseStack.getCount());
	}
}

