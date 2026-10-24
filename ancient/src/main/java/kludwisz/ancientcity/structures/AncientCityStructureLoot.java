package kludwisz.ancientcity.structures;

import java.util.List;

import com.seedfinding.mccore.util.data.Pair;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mcfeature.loot.LootTable;

import kludwisz.ancientcity.loot.AncientCityLootTables;

public class AncientCityStructureLoot {
	public static final List< List<Pair<BPos,LootTable>> > STRUCTURE_LOOT_V2 = List.<List<Pair<BPos, LootTable>>>of(
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(// single golden apple
					// new Pair<>(new BPos(9,8,20), AncientCityLootTables.NULL.get())
				),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(
				new Pair<>(new BPos(4,4,15), AncientCityLootTables.TREASURE_CHEST.get()),
				new Pair<>(new BPos(12,4,15), AncientCityLootTables.TREASURE_CHEST.get())
				),
			List.of(),
			List.of(), // camp 2
			List.of(), // camp 3
			List.of(
				new Pair<>(new BPos(14,3,11), AncientCityLootTables.TREASURE_CHEST.get())
				),
			List.of(
				new Pair<>(new BPos(10,2,5), AncientCityLootTables.TREASURE_CHEST.get())
				),
			List.of(
				new Pair<>(new BPos(7,1,5), AncientCityLootTables.TREASURE_CHEST.get())
				),
			List.of(
				new Pair<>(new BPos(12,1,7), AncientCityLootTables.ICE_BOX.get())
				),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(
				new Pair<>(new BPos(12,1,19), AncientCityLootTables.TREASURE_CHEST.get()),
				new Pair<>(new BPos(21,1,18), AncientCityLootTables.TREASURE_CHEST.get()),
				new Pair<>(new BPos(25,1,10), AncientCityLootTables.TREASURE_CHEST.get())
				),
			List.of(),
			List.of(),
			List.of(),
			List.of(
				new Pair<>(new BPos(8,16,8), AncientCityLootTables.TREASURE_CHEST.get())
				),
			List.of(
				new Pair<>(new BPos(7,1,5), AncientCityLootTables.TREASURE_CHEST.get()),
				new Pair<>(new BPos(13,7,9), AncientCityLootTables.TREASURE_CHEST.get())
				),
			List.of(
				new Pair<>(new BPos(10,14,7), AncientCityLootTables.TREASURE_CHEST.get())
				),
			List.of(
				new Pair<>(new BPos(8,7,13), AncientCityLootTables.TREASURE_CHEST.get())
				),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(), // stairs5
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(), // sculk
			List.of()  // empty
	);
}
