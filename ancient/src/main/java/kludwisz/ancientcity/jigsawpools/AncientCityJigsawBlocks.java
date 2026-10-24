package kludwisz.ancientcity.jigsawpools;

import java.util.*;

import com.seedfinding.mccore.util.block.BlockBox;
import com.seedfinding.mccore.util.block.BlockDirection;
import com.seedfinding.mccore.util.block.BlockRotation;
import com.seedfinding.mccore.util.data.Pair;
import com.seedfinding.mccore.util.pos.BPos;
import kludwisz.ancientcity.AncientCityGenerator;
import kludwisz.ancientcity.structures.AncientCityStructureSize;
import kludwisz.ancientcity.util.BlockBoxUtil;
import kludwisz.ancientcity.util.MutableBlockPos;

public class AncientCityJigsawBlocks {
    public static final List<List<JigsawBlock>> JIGSAW_BLOCKS_V2 = List.of(
    		// city/entrance/entrance_connector
    		List.of(
    				new JigsawBlock(5, "empty", "entrance_middle", BlockDirection.WEST, new BPos(0,0,19)),
    				new JigsawBlock(4, "empty", "connect_wall", BlockDirection.NORTH, new BPos(19,0,0)),
    				new JigsawBlock(4, "empty", "connect_wall", BlockDirection.SOUTH, new BPos(19,0,38)),
    				new JigsawBlock(7, "entrance_start", "empty", BlockDirection.EAST, new BPos(29,0,19))
    		),
    		// city/entrance/entrance_path_1
    		List.of(
    				new JigsawBlock(4, "empty", "connect_wall", BlockDirection.NORTH, new BPos(14,0,0)),
    				new JigsawBlock(4, "empty", "connect_wall", BlockDirection.SOUTH, new BPos(14,0,38)),
    				new JigsawBlock(5, "entrance_middle", "empty", BlockDirection.EAST, new BPos(34,0,19))
    		),
    		// city/entrance/entrance_path_2
    		List.of(
    				new JigsawBlock(4, "empty", "connect_wall", BlockDirection.NORTH, new BPos(14,0,0)),
    				new JigsawBlock(4, "empty", "connect_wall", BlockDirection.SOUTH, new BPos(14,0,38)),
    				new JigsawBlock(5, "entrance_middle", "empty", BlockDirection.EAST, new BPos(34,0,19))
    		),
    		// city/entrance/entrance_path_3
    		List.of(
    				new JigsawBlock(4, "empty", "connect_wall", BlockDirection.NORTH, new BPos(14,0,0)),
    				new JigsawBlock(4, "empty", "connect_wall", BlockDirection.SOUTH, new BPos(14,0,38)),
    				new JigsawBlock(5, "entrance_middle", "empty", BlockDirection.EAST, new BPos(34,0,19))
    		),
    		// city/entrance/entrance_path_4
    		List.of(
    				new JigsawBlock(4, "empty", "connect_wall", BlockDirection.NORTH, new BPos(14,0,0)),
    				new JigsawBlock(4, "empty", "connect_wall", BlockDirection.SOUTH, new BPos(14,0,38)),
    				new JigsawBlock(5, "entrance_middle", "empty", BlockDirection.EAST, new BPos(34,0,19))
    		),
    		// city/entrance/entrance_path_5
    		List.of(
    				new JigsawBlock(4, "empty", "connect_wall", BlockDirection.NORTH, new BPos(14,0,0)),
    				new JigsawBlock(4, "empty", "connect_wall", BlockDirection.SOUTH, new BPos(14,0,38)),
    				new JigsawBlock(5, "entrance_middle", "empty", BlockDirection.EAST, new BPos(34,0,19))
    		),
    		// city_center/city_center_1
    		List.of(
    				new JigsawBlock(1, "empty", "connect_bottom", BlockDirection.WEST, new BPos(0,1,0)),
    				new JigsawBlock(1, "empty", "connect_right", BlockDirection.SOUTH, new BPos(0,1,40)),
    				new JigsawBlock(1, "empty", "connect_left", BlockDirection.NORTH, new BPos(17,1,0)),
    				new JigsawBlock(1, "empty", "connect_top", BlockDirection.EAST, new BPos(17,1,40)),
    				new JigsawBlock(7, "city_anchor", "empty", BlockDirection.DOWN, new BPos(13,24,20))
    		),
    		// city_center/city_center_2
    		List.of(
    				new JigsawBlock(1, "empty", "connect_bottom", BlockDirection.WEST, new BPos(0,1,0)),
    				new JigsawBlock(1, "empty", "connect_right", BlockDirection.SOUTH, new BPos(0,1,40)),
    				new JigsawBlock(1, "empty", "connect_left", BlockDirection.NORTH, new BPos(17,1,0)),
    				new JigsawBlock(1, "empty", "connect_top", BlockDirection.EAST, new BPos(17,1,40)),
    				new JigsawBlock(7, "city_anchor", "empty", BlockDirection.DOWN, new BPos(13,24,20))
    		),
    		// city_center/city_center_3
    		List.of(
    				new JigsawBlock(1, "empty", "connect_bottom", BlockDirection.WEST, new BPos(0,1,0)),
    				new JigsawBlock(1, "empty", "connect_right", BlockDirection.SOUTH, new BPos(0,1,40)),
    				new JigsawBlock(1, "empty", "connect_left", BlockDirection.NORTH, new BPos(17,1,0)),
    				new JigsawBlock(1, "empty", "connect_top", BlockDirection.EAST, new BPos(17,1,40)),
    				new JigsawBlock(7, "city_anchor", "empty", BlockDirection.DOWN, new BPos(13,24,20))
    		),
    		// city_center/walls/bottom_1
    		List.of(
    				new JigsawBlock(5, "empty", "entrance_start", BlockDirection.WEST, new BPos(0,0,20)),
    				new JigsawBlock(1, "empty", "connect_bottom_left_corner", BlockDirection.NORTH, new BPos(7,0,0)),
    				new JigsawBlock(1, "empty", "connect_bottom_right_corner", BlockDirection.SOUTH, new BPos(7,0,40)),
    				new JigsawBlock(7, "connect_bottom", "empty", BlockDirection.EAST, new BPos(13,0,0))
    		),
    		// city_center/walls/bottom_2
    		List.of(
    				new JigsawBlock(5, "empty", "entrance_start", BlockDirection.WEST, new BPos(0,0,20)),
    				new JigsawBlock(1, "empty", "connect_bottom_left_corner", BlockDirection.NORTH, new BPos(7,0,0)),
    				new JigsawBlock(1, "empty", "connect_bottom_right_corner", BlockDirection.SOUTH, new BPos(7,0,40)),
    				new JigsawBlock(7, "connect_bottom", "empty", BlockDirection.EAST, new BPos(13,0,0))
    		),
    		// city_center/walls/bottom_left_corner
    		List.of(
    				new JigsawBlock(7, "connect_bottom_left_corner", "empty", BlockDirection.SOUTH, new BPos(7,0,13))
    		),
    		// city_center/walls/bottom_right_corner
    		List.of(
    				new JigsawBlock(7, "connect_bottom_right_corner", "empty", BlockDirection.NORTH, new BPos(7,0,0))
    		),
    		// city_center/walls/bottom_right_corner_1
    		List.of(
    				new JigsawBlock(7, "connect_bottom_right_corner", "empty", BlockDirection.NORTH, new BPos(7,0,0))
    		),
    		// city_center/walls/bottom_right_corner_2
    		List.of(
    				new JigsawBlock(7, "connect_bottom_right_corner", "empty", BlockDirection.NORTH, new BPos(7,0,0))
    		),
    		// city_center/walls/left
    		List.of(
    				new JigsawBlock(4, "empty", "connect_wall", BlockDirection.NORTH, new BPos(9,0,0)),
    				new JigsawBlock(7, "connect_left", "empty", BlockDirection.SOUTH, new BPos(17,0,13))
    		),
    		// city_center/walls/right
    		List.of(
    				new JigsawBlock(7, "connect_right", "empty", BlockDirection.NORTH, new BPos(0,0,0)),
    				new JigsawBlock(4, "empty", "connect_wall", BlockDirection.SOUTH, new BPos(8,0,13))
    		),
    		// city_center/walls/top
    		List.of(
    				new JigsawBlock(7, "connect_top", "empty", BlockDirection.WEST, new BPos(0,0,40)),
    				new JigsawBlock(1, "empty", "connect_top_left_corner", BlockDirection.NORTH, new BPos(6,0,0)),
    				new JigsawBlock(1, "empty", "connect_top_right_corner", BlockDirection.SOUTH, new BPos(6,0,40)),
    				new JigsawBlock(4, "empty", "connect_wall", BlockDirection.EAST, new BPos(13,0,19))
    		),
    		// city_center/walls/top_left_corner
    		List.of(
    				new JigsawBlock(7, "connect_top_left_corner", "empty", BlockDirection.SOUTH, new BPos(6,0,13))
    		),
    		// city_center/walls/top_right_corner
    		List.of(
    				new JigsawBlock(7, "connect_top_right_corner", "empty", BlockDirection.NORTH, new BPos(6,0,0))
    		),
    		// structures/barracks
    		List.of(
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.WEST, new BPos(0,0,8)),
    				new JigsawBlock(2, "bottom", "bottom", BlockDirection.UP, new BPos(10,3,10)),
    				new JigsawBlock(2, "bottom", "bottom", BlockDirection.UP, new BPos(11,3,2))
    		),
    		// structures/camp_1
    		List.of(
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.WEST, new BPos(0,0,7))
    		),
    		// structures/camp_2
    		List.of(
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.WEST, new BPos(0,0,7))
    		),
    		// structures/camp_3
    		List.of(
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.WEST, new BPos(0,0,7))
    		),
    		// structures/chamber_1
    		List.of(
    				new JigsawBlock(2, "bottom", "bottom", BlockDirection.UP, new BPos(12,0,6)),
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.EAST, new BPos(18,0,7)),
    				new JigsawBlock(2, "bottom", "bottom", BlockDirection.UP, new BPos(5,3,1))
    		),
    		// structures/chamber_2
    		List.of(
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.EAST, new BPos(11,0,6)),
    				new JigsawBlock(2, "bottom", "bottom", BlockDirection.UP, new BPos(8,1,5))
    		),
    		// structures/chamber_3
    		List.of(
    				new JigsawBlock(2, "bottom", "bottom", BlockDirection.UP, new BPos(4,0,7)),
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.EAST, new BPos(9,0,5))
    		),
    		// structures/ice_box_1
    		List.of(
    				new JigsawBlock(2, "bottom", "bottom", BlockDirection.UP, new BPos(12,0,6)),
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.EAST, new BPos(18,0,7)),
    				new JigsawBlock(2, "bottom", "bottom", BlockDirection.UP, new BPos(5,3,1))
    		),
    		// structures/large_pillar_1
    		List.of(
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.EAST, new BPos(5,0,2))
    		),
    		// structures/large_ruin_1
    		List.of(
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.WEST, new BPos(0,0,7))
    		),
    		// structures/medium_pillar_1
    		List.of(
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.EAST, new BPos(6,0,3)),
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.EAST, new BPos(6,0,4))
    		),
    		// structures/medium_ruin_1
    		List.of(
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.WEST, new BPos(0,0,6))
    		),
    		// structures/medium_ruin_2
    		List.of(
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.WEST, new BPos(0,0,5))
    		),
    		// structures/sauna_1
    		List.of(
    				new JigsawBlock(2, "bottom", "bottom", BlockDirection.UP, new BPos(5,0,26)),
    				new JigsawBlock(2, "bottom", "bottom", BlockDirection.UP, new BPos(8,0,12)),
    				new JigsawBlock(2, "bottom", "bottom", BlockDirection.UP, new BPos(18,0,33)),
    				new JigsawBlock(2, "bottom", "bottom", BlockDirection.UP, new BPos(21,0,9)),
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.EAST, new BPos(28,0,21))
    		),
    		// structures/small_ruin_1
    		List.of(
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.WEST, new BPos(0,0,3))
    		),
    		// structures/small_ruin_2
    		List.of(
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.WEST, new BPos(0,0,2))
    		),
    		// structures/small_statue
    		List.of(
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.WEST, new BPos(0,0,3)),
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.WEST, new BPos(0,0,5))
    		),
    		// structures/tall_ruin_1
    		List.of(
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.SOUTH, new BPos(8,0,16))
    		),
    		// structures/tall_ruin_2
    		List.of(
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.NORTH, new BPos(9,0,0))
    		),
    		// structures/tall_ruin_3
    		List.of(
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.SOUTH, new BPos(8,0,16))
    		),
    		// structures/tall_ruin_4
    		List.of(
    				new JigsawBlock(7, "connect_structure", "empty", BlockDirection.NORTH, new BPos(9,0,0))
    		),
    		// walls/intact_corner_wall_1
    		List.of(
    				new JigsawBlock(6, "connect_wall", "connect_wall", BlockDirection.WEST, new BPos(0,0,10)),
    				new JigsawBlock(6, "connect_wall", "connect_wall", BlockDirection.NORTH, new BPos(10,0,0)),
    				new JigsawBlock(6, "connect_wall", "connect_wall", BlockDirection.SOUTH, new BPos(10,0,20)),
    				new JigsawBlock(6, "connect_wall", "connect_wall", BlockDirection.EAST, new BPos(20,0,10))
    		),
    		// walls/intact_horizontal_wall_1
    		List.of(
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.WEST, new BPos(0,0,10)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.SOUTH, new BPos(8,0,20)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.NORTH, new BPos(9,0,0)),
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.EAST, new BPos(17,0,10))
    		),
    		// walls/intact_horizontal_wall_2
    		List.of(
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.WEST, new BPos(0,0,10)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.SOUTH, new BPos(8,0,20)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.NORTH, new BPos(9,0,0)),
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.EAST, new BPos(17,0,10))
    		),
    		// walls/intact_horizontal_wall_bridge
    		List.of(
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.WEST, new BPos(0,0,10)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.NORTH, new BPos(9,0,0)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.SOUTH, new BPos(9,0,20))
    		),
    		// walls/intact_horizontal_wall_passage_1
    		List.of(
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.WEST, new BPos(0,0,10)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.NORTH, new BPos(9,0,0)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.SOUTH, new BPos(9,0,20)),
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.EAST, new BPos(17,0,10))
    		),
    		// walls/intact_horizontal_wall_stairs_1
    		List.of(
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.WEST, new BPos(0,0,10)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.NORTH, new BPos(9,0,0)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.SOUTH, new BPos(9,0,20)),
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.EAST, new BPos(17,0,10))
    		),
    		// walls/intact_horizontal_wall_stairs_2
    		List.of(
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.WEST, new BPos(0,0,10)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.NORTH, new BPos(9,0,0)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.SOUTH, new BPos(9,0,20)),
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.EAST, new BPos(17,0,10))
    		),
    		// walls/intact_horizontal_wall_stairs_3
    		List.of(
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.WEST, new BPos(0,0,10)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.SOUTH, new BPos(6,0,20)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.NORTH, new BPos(9,0,0)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.SOUTH, new BPos(9,0,20)),
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.EAST, new BPos(17,0,10))
    		),
    		// walls/intact_horizontal_wall_stairs_4
    		List.of(
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.WEST, new BPos(0,0,10)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.NORTH, new BPos(9,0,0)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.SOUTH, new BPos(9,0,20)),
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.EAST, new BPos(17,0,10))
    		),
    		//stairs5
    		List.of(),
    		// walls/intact_intersection_wall_1
    		List.of(
    				new JigsawBlock(6, "connect_wall", "connect_wall", BlockDirection.WEST, new BPos(0,0,10)),
    				new JigsawBlock(6, "connect_wall", "connect_wall", BlockDirection.SOUTH, new BPos(10,0,20)),
    				new JigsawBlock(6, "connect_wall", "connect_wall", BlockDirection.EAST, new BPos(20,0,10))
    		),
    		// walls/intact_lshape_wall_1
    		List.of(
    				new JigsawBlock(6, "connect_wall", "connect_wall", BlockDirection.SOUTH, new BPos(10,0,20)),
    				new JigsawBlock(6, "connect_wall", "connect_wall", BlockDirection.EAST, new BPos(20,0,10))
    		),
    		// walls/ruined_corner_wall_1
    		List.of(
    				new JigsawBlock(6, "connect_wall", "connect_wall", BlockDirection.WEST, new BPos(0,0,10)),
    				new JigsawBlock(6, "connect_wall", "connect_wall", BlockDirection.NORTH, new BPos(10,0,0)),
    				new JigsawBlock(6, "connect_wall", "connect_wall", BlockDirection.SOUTH, new BPos(10,0,20)),
    				new JigsawBlock(6, "connect_wall", "connect_wall", BlockDirection.EAST, new BPos(20,0,10))
    		),
    		// walls/ruined_corner_wall_2
    		List.of(
    				new JigsawBlock(6, "connect_wall", "connect_wall", BlockDirection.WEST, new BPos(0,0,10)),
    				new JigsawBlock(6, "connect_wall", "connect_wall", BlockDirection.NORTH, new BPos(10,0,0)),
    				new JigsawBlock(6, "connect_wall", "connect_wall", BlockDirection.SOUTH, new BPos(10,0,20)),
    				new JigsawBlock(6, "connect_wall", "connect_wall", BlockDirection.EAST, new BPos(20,0,10))
    		),
    		// walls/ruined_horizontal_wall_stairs_1
    		List.of(
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.WEST, new BPos(0,0,10)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.NORTH, new BPos(9,0,0)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.SOUTH, new BPos(9,0,20)),
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.EAST, new BPos(17,0,10))
    		),
    		// walls/ruined_horizontal_wall_stairs_2
    		List.of(
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.WEST, new BPos(0,0,10)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.NORTH, new BPos(9,0,0)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.SOUTH, new BPos(9,0,20)),
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.EAST, new BPos(17,0,10))
    		),
    		// walls/ruined_horizontal_wall_stairs_3
    		List.of(
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.WEST, new BPos(0,0,10)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.SOUTH, new BPos(6,0,20)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.NORTH, new BPos(9,0,0)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.SOUTH, new BPos(9,0,20)),
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.EAST, new BPos(17,0,10))
    		),
    		// walls/ruined_horizontal_wall_stairs_4
    		List.of(
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.WEST, new BPos(0,0,10)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.NORTH, new BPos(9,0,0)),
    				new JigsawBlock(3, "empty", "connect_structure", BlockDirection.SOUTH, new BPos(9,0,20)),
    				new JigsawBlock(4, "connect_wall", "connect_wall", BlockDirection.EAST, new BPos(17,0,10))
    		),
    		// sculk_patch
    		List.of(
    				new JigsawBlock(7, "bottom", "bottom", BlockDirection.DOWN, new BPos(0,0,0))
    		),
    		// empty
    		List.of()
    );

	public static JigsawBlock[][] JIGSAW_BLOCKS;
	static {
		JIGSAW_BLOCKS = new JigsawBlock[JIGSAW_BLOCKS_V2.size()][];
		for (int i = 0; i < JIGSAW_BLOCKS.length; i++) {
			JIGSAW_BLOCKS[i] = JIGSAW_BLOCKS_V2.get(i).toArray(JigsawBlock[]::new);
		}
	}

//	public static HashMap<Pair<Integer, String>, Set<BlockDirection>> PIECE_CONNECTION_DIRECTIONS = new HashMap<>();
//	static {
////		Set<String> ignoredNames = new HashSet<>();
////		ignoredNames.add("empty");
////		ignoredNames.add("bottom");
////		ignoredNames.add("city_anchor");
//
//		for (int i = 0; i < JIGSAW_BLOCKS_V2.size(); i++) {
//			for (JigsawBlock block : JIGSAW_BLOCKS_V2.get(i)) {
////				if (ignoredNames.contains(block.name)) continue;
//
//				PIECE_CONNECTION_DIRECTIONS.computeIfAbsent(new Pair<>(i, block.name), (p) -> new HashSet<>()).add(AncientCityGenerator.BlockJigsawInfo.getOpposite(block.direction1));
//			}
//		}
//	}

	public static HashMap<String, Set<BlockDirection>>[] PIECE_CONNECTION_DIRECTIONS = new HashMap[JIGSAW_BLOCKS_V2.size()];
	static {
		for (int i = 0; i < JIGSAW_BLOCKS_V2.size(); i++) {
			PIECE_CONNECTION_DIRECTIONS[i] = new HashMap<>();
			for (JigsawBlock block : JIGSAW_BLOCKS_V2.get(i)) {
				PIECE_CONNECTION_DIRECTIONS[i].computeIfAbsent(block.name, (p) -> new HashSet<>()).add(AncientCityGenerator.BlockJigsawInfo.getOpposite(block.direction1));
			}
			for (Map.Entry<String, Set<BlockDirection>> entry : PIECE_CONNECTION_DIRECTIONS[i].entrySet()) {
				entry.setValue(EnumSet.copyOf(entry.getValue()));
			}
		}
	}

	public static HashMap<String, BlockBox>[] PIECE_TARGET_MIN_BOXES = new HashMap[JIGSAW_BLOCKS_V2.size()];
	static {
		for (int i = 0; i < JIGSAW_BLOCKS_V2.size(); i++) {
			HashMap<String, BlockBox> targetMinBoxes = new HashMap<>();

			for (JigsawBlock parentJigsaw : JIGSAW_BLOCKS_V2.get(i)) {
				BlockBox minBox = targetMinBoxes.get(parentJigsaw.targetName);

				for (Pair<Integer, Integer> pair : AncientCityPools.CITY_POOLS_V2.get(parentJigsaw.poolType)) {
					int childPieceId = pair.getFirst();
					if (childPieceId == 60) continue;

					BPos childPieceSize = AncientCityStructureSize.STRUCTURE_SIZE_V2.get(childPieceId);
					if (childPieceSize.getX() < 1 || childPieceSize.getY() < 1 || childPieceSize.getZ() < 1) continue;

					for (BlockRotation childPieceRotation : BlockRotation.values()) {
						for (JigsawBlock childJigsaw : JIGSAW_BLOCKS_V2.get(childPieceId)) {
							if (!childJigsaw.name.equals(parentJigsaw.targetName)) continue;
							if (childPieceRotation.rotate(childJigsaw.direction1) != parentJigsaw.direction1.getOpposite()) continue;

							BlockBox childPieceBox = new BlockBox(0, 0, 0, 0, 0, 0);
							BlockBoxUtil.setSizeRotatePos(childPieceBox, childPieceSize, childPieceRotation, new MutableBlockPos().set(BPos.ORIGIN.subtract(childPieceRotation.rotate(childJigsaw.relativePos, BPos.ORIGIN))));

							if (minBox == null) {
								minBox = childPieceBox;
							} else {
								BlockBoxUtil.intersect(minBox, childPieceBox);
							}
						}
					}
				}

				if (minBox != null && !BlockBoxUtil.isEmpty(minBox)) {
					targetMinBoxes.put(parentJigsaw.targetName, minBox);
				}
			}

			PIECE_TARGET_MIN_BOXES[i] = targetMinBoxes;
		}
	}
}
