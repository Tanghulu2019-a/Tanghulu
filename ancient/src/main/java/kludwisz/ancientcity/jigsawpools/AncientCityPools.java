package kludwisz.ancientcity.jigsawpools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.seedfinding.mccore.util.data.Pair;

public class AncientCityPools {
	
    // fallback not necessary cause always empty
    public static final List< List<Pair<Integer, Integer>> > CITY_POOLS_V2 = List.of(
    		/*PoolType.pools\city_center.json*/ List.of(
    				new Pair<>(6, 1),
    				new Pair<>(7, 1),
    				new Pair<>(8, 1)
    		),

    		/*PoolType.pools\city_center\walls.json*/ List.of(
    				new Pair<>(9, 1),
    				new Pair<>(10, 1),
    				new Pair<>(11, 1),
    				new Pair<>(13, 1),
    				new Pair<>(14, 1),
    				new Pair<>(15, 1),
    				new Pair<>(16, 1),
    				new Pair<>(17, 1),
    				new Pair<>(19, 1),
    				new Pair<>(18, 1)
    		),
    		
    		/*PoolType.pools\sculk.json*/ List.of(
    				new Pair<>(59, 6),
    				new Pair<>(60, 1)
    		),

    		/*PoolType.pools\structures.json*/ List.of(
    				new Pair<>(60, 7),
    				new Pair<>(20, 4),
    				new Pair<>(24, 4),
    				new Pair<>(25, 4),
    				new Pair<>(26, 4),
    				new Pair<>(33, 4),
    				new Pair<>(36, 4),
    				new Pair<>(29, 1),
    				new Pair<>(37, 1),
    				new Pair<>(38, 1),
    				new Pair<>(39, 2),
    				new Pair<>(40, 2),
    				new Pair<>(22,1/*camp1*/),
    				new Pair<>(31, 1),
    				new Pair<>(32, 1),
    				new Pair<>(34, 1),
    				new Pair<>(35, 1),
    				new Pair<>(28, 1),
    				new Pair<>(30, 1),
    				new Pair<>(27,1/*icebox*/)
    		),

    		/*PoolType.pools\walls.json*/ List.of(
    				new Pair<>(41, 1),
    				new Pair<>(51, 1),
    				new Pair<>(52, 1),
    				new Pair<>(42, 1),
    				new Pair<>(43, 1),
    				new Pair<>(46, 1),
    				new Pair<>(47, 1),
    				new Pair<>(48, 1),
    				new Pair<>(49, 4),
    				new Pair<>(45, 3),
    				new Pair<>(53, 1),
    				new Pair<>(54, 1),
    				new Pair<>(55, 2),
    				new Pair<>(56, 2),
    				new Pair<>(57, 3),
    				new Pair<>(58, 3)
    		),

    		/*PoolType.pools\city\entrance.json*/ List.of(
    				new Pair<>(0, 1),
    				new Pair<>(1, 1),
    				new Pair<>(2, 1),
    				new Pair<>(3, 1),
    				new Pair<>(4, 1),
    				new Pair<>(5, 1)
    		),

    		/*PoolType.pools\walls\no_corners.json*/ List.of(
    				new Pair<>(42, 1),
    				new Pair<>(43, 1),
    				new Pair<>(46, 1),
    				new Pair<>(47, 1),
    				new Pair<>(48, 1),
    				new Pair<>(49, 1),
    				new Pair<>(50, 1),
    				new Pair<>(44, 1)
    		),
    		
    		// pooltype.empty
    		List.of(
    				new Pair<>(60, 0)
    		)
    );

	public static int[][] CITY_POOLS;
	static {
		CITY_POOLS = new int[CITY_POOLS_V2.size()][];
		ArrayList<Integer> list = new ArrayList<>();
		for (int i = 0; i < CITY_POOLS_V2.size(); i++) {
			List<Pair<Integer, Integer>> pool = CITY_POOLS_V2.get(i);
			for (Pair<Integer, Integer> entry : pool) {
				for (int k = 0; k < entry.getSecond(); k++) {
					list.add(entry.getFirst());
				}
			}
			CITY_POOLS[i] = list.stream().mapToInt(e -> e).toArray();
			list.clear();
		}
	}
}
