package kludwisz.ancientcity.loot;

import java.util.function.Supplier;

import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.LootPool;
import com.seedfinding.mcfeature.loot.LootTable;
import com.seedfinding.mcfeature.loot.effect.Effects;
import com.seedfinding.mcfeature.loot.entry.EmptyEntry;
import com.seedfinding.mcfeature.loot.entry.ItemEntry;
import com.seedfinding.mcfeature.loot.function.*;
import com.seedfinding.mcfeature.loot.roll.ConstantRoll;
import com.seedfinding.mcfeature.loot.roll.UniformRoll;

public class AncientCityLootTables {
	// loot function emulating MC-229209
	private static final LootFunction UNSTACKABLE_BUG_EMULATION = (baseStack, context) -> {
		if (!context.getVersion().isNewerOrEqualTo(MCVersion.v1_20_5)) {
			baseStack.setCount(1);
		}
		return baseStack;
	};

	public static final Supplier<LootTable> TREASURE_CHEST = () -> new LootTable(
			new LootPool(new UniformRoll(5.0F, 10.0F),
					new ItemEntry(Items.ENCHANTED_GOLDEN_APPLE, 1).apply(version -> SetCountFunction.uniform(1.0F, 2.0F)),
					new ItemEntry(Items.MUSIC_DISC_OTHERSIDE, 1),

					new ItemEntry(Items.COMPASS, 2),
					new ItemEntry(Items.SCULK_CATALYST, 2).apply(version -> SetCountFunction.uniform(1.0F, 2.0F)),
					new ItemEntry(Items.NAME_TAG, 2),
					new ItemEntry(Items.DIAMOND_HOE, 2).apply(
							version -> new ApplyDamageFunction(),
							version -> new EnchantWithLevelsFunction(Items.DIAMOND_HOE, 30, 50, true).apply(MCVersion.v1_19)
					),
					new ItemEntry(Items.LEAD, 2),
					new ItemEntry(Items.DIAMOND_HORSE_ARMOR, 2),
					new ItemEntry(Items.SADDLE, 2),
					new ItemEntry(Items.MUSIC_DISC_13, 2),
					new ItemEntry(Items.MUSIC_DISC_CAT, 2),
					new ItemEntry(Items.DIAMOND_LEGGINGS, 2).apply(
							version -> new EnchantWithLevelsFunction(Items.DIAMOND_LEGGINGS, 30, 50, true).apply(MCVersion.v1_19)
					),

					new ItemEntry(Items.SWIFT_SNEAK_BOOK, 3).apply(version -> new EnchantSwiftSneakFunction()),
					new ItemEntry(Items.SCULK, 3).apply(version -> SetCountFunction.uniform(4.0F, 10.0F)),
					new ItemEntry(Items.SCULK_SENSOR, 3).apply(version -> SetCountFunction.uniform(1.0F, 3.0F)),
					new ItemEntry(Items.CANDLE, 3).apply(version -> SetCountFunction.uniform(1.0F, 4.0F)),
					new ItemEntry(Items.AMETHYST_SHARD, 3).apply(version -> SetCountFunction.uniform(1.0F, 15.0F)),
					new ItemEntry(Items.EXPERIENCE_BOTTLE, 3).apply(version -> SetCountFunction.uniform(1.0F, 3.0F)),
					new ItemEntry(Items.GLOW_BERRIES, 3).apply(version -> SetCountFunction.uniform(1.0F, 15.0F)),
					new ItemEntry(Items.IRON_LEGGINGS, 3).apply(
							version -> new EnchantWithLevelsFunction(Items.IRON_LEGGINGS, 20, 39, true).apply(version)
					),

					new ItemEntry(Items.ECHO_SHARD, 4).apply(version -> SetCountFunction.uniform(1.0F, 3.0F)),
					new ItemEntry(Items.DISC_FRAGMENT_5, 4).apply(version -> SetCountFunction.uniform(1.0F, 3.0F)),

					new ItemEntry(Items.STRONG_REGENERATION_POTION, 5).apply(
							version -> SetCountFunction.uniform(1.0F, 3.0F),
							version -> UNSTACKABLE_BUG_EMULATION
					),
					new ItemEntry(Items.ENCHANTED_BOOK, 5).apply(version -> new EnchantRandomlyFunction(Items.ENCHANTED_BOOK).apply(version)),
					new ItemEntry(Items.BOOK, 5).apply(version -> SetCountFunction.uniform(3.0F, 10.0F)),
					new ItemEntry(Items.BONE, 5).apply(version -> SetCountFunction.uniform(1.0F, 15.0F)),
					new ItemEntry(Items.SOUL_TORCH, 5).apply(version -> SetCountFunction.uniform(1.0F, 15.0F)),

					new ItemEntry(Items.COAL, 7).apply(version -> SetCountFunction.uniform(6.0F, 15.0F))
			),
			
			new LootPool(new ConstantRoll(1),
					new EmptyEntry(75),
					new ItemEntry(Items.ARMOR_TRIM_WARD, 4),
					new ItemEntry(Items.ARMOR_TRIM_SILENCE, 1)
			)
	);

	public static final Supplier<LootTable> ICE_BOX = () -> new LootTable(
			new LootPool(new UniformRoll(4.0F, 10.0F),
					new ItemEntry(Items.SUSPICIOUS_STEW, 1).apply(
							version -> EffectFunction.builder()
								.apply(Effects.NIGHT_VISION, 7.0F, 10.0F)
								.apply(Effects.BLINDNESS, 5.0F, 7.0F),
							version -> UNSTACKABLE_BUG_EMULATION
					),
					new ItemEntry(Items.GOLDEN_CARROT, 1).apply(version -> SetCountFunction.uniform(1.0F, 10.0F)),
					new ItemEntry(Items.BAKED_POTATO, 1).apply(version -> SetCountFunction.uniform(1.0F, 10.0F)),
					new ItemEntry(Items.PACKED_ICE, 2).apply(version -> SetCountFunction.uniform(2.0F, 6.0F)),
					new ItemEntry(Items.SNOWBALL, 4).apply(version -> SetCountFunction.uniform(2.0F, 6.0F))
			)
	);
}
