package kludwisz.ancientcity;

import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.LootContext;
import com.seedfinding.mcseed.rand.JRand;

public class Main {
//	public static void main(String[] args) {
//		testTime();
//	}
	
	static final AncientCity CITY = new AncientCity(MCVersion.v1_19_2);
	static final ChunkRand rand = new ChunkRand();
	static final LootContext ctx = new LootContext(0L, MCVersion.v1_19_2);

	public static int A;
	public static int B;
	public static int C;

	public static final int[] MAX_NEXT_INT_SEEDS = new int[6];
	static {
		for (int i = 1; i < MAX_NEXT_INT_SEEDS.length; i++) {
			int maxSeed = 1 << 31;
			maxSeed = maxSeed - Integer.remainderUnsigned(maxSeed, i) - 1;
			MAX_NEXT_INT_SEEDS[i] = maxSeed;
		}
	}
	public static final int MAX_NEXT_INT_SEEDS_5 = MAX_NEXT_INT_SEEDS[5];
	public static final int MAX_NEXT_INT_SEEDS_3 = MAX_NEXT_INT_SEEDS[3];

	public static void skipShuffle(JRand rand, int size) {
//		if (size > C) {
//			C = size;
//			System.out.println("C: " + C);
//		}
//		for (int i = size; i > 1; i--) {
////			rand.nextInt(i);
//			while (rand.next(31) > MAX_NEXT_INT_SEEDS[i]);
//		}
		switch (size) {
			case 5:
				while (rand.next(31) > MAX_NEXT_INT_SEEDS_5);
				rand.nextSeed();
				while (rand.next(31) > MAX_NEXT_INT_SEEDS_3);
				rand.nextSeed();
				break;
			case 4:
				rand.nextSeed();
				while (rand.next(31) > MAX_NEXT_INT_SEEDS_3);
				rand.nextSeed();
				break;
			case 3:
				while (rand.next(31) > MAX_NEXT_INT_SEEDS_3);
				rand.nextSeed();
				break;
			case 2:
				rand.nextSeed();
				break;
		}
//		rand.advance(size - 1);
	}

	public static void shuffle(JRand rand, int[] arr) {
		shuffle(rand, arr, arr.length);
	}

	public static void shuffle(JRand rand, int[] arr, int len) {
//		if (arr.length > B) {
//			B = arr.length;
//			System.out.println("B: " + B);
//		}
		for (int i = len; i > 1; i--) {
			int j = rand.nextInt(i);
			int tmp = arr[i - 1];
			arr[i - 1] = arr[j];
			arr[j] = tmp;
		}
	}

	public static void shuffle(JRand rand, Object[] arr) {
		shuffle(rand, arr, arr.length);
	}

	public static void shuffle(JRand rand, Object[] arr, int len) {
//		if (arr.length > B) {
//			B = arr.length;
//			System.out.println("B: " + B);
//		}
		switch (len) {
			case 5: {
				int j = rand.nextInt(5);
				Object tmp = arr[4];
				arr[4] = arr[j];
				arr[j] = tmp;
			}
			case 4: {
				int j = rand.nextInt(4);
				Object tmp = arr[3];
				arr[3] = arr[j];
				arr[j] = tmp;
			}
			case 3: {
				int j = rand.nextInt(3);
				Object tmp = arr[2];
				arr[2] = arr[j];
				arr[j] = tmp;
			}
			case 2: {
				int j = rand.nextInt(2);
				Object tmp = arr[1];
				arr[1] = arr[j];
				arr[j] = tmp;
			}
		}
	}

	public static void testTime() {
		int total = 1000;

		double maxSps = 0;
		for (int i = 0; i < 100; i++) {
			long point1 = System.nanoTime();
			long s = 0;
			int maxPieces = 0;
			AncientCityGenerator gen = new AncientCityGenerator();
			for (int seed = 0; seed < total; seed++) {
				CPos chunk = CITY.getInRegion(seed, 0, 0, rand);
				gen.generate(seed, chunk.getX(), chunk.getZ(), rand);
				s ^= rand.getSeed();
				if (gen.piecesLen > maxPieces) maxPieces = gen.piecesLen;
//				List<Triplet<BPos, LootTable, Long>> chests = gen.getChestsWithLootSeeds();
			}
			long point2 = System.nanoTime();

			if (i == 0) {
//				System.out.println("s: " + s + " (should be 133124020872309)");
				System.out.println(A + " " + B + " " + C);
				System.out.println(1.0 * A / B + " " + 1.0 * B / C);
				System.out.println(1.0 * A / C);
				System.out.println("Max pieces: " + maxPieces);
			}

			double sps = total / ((point2 - point1) / 1000000000.0);
			maxSps = Math.max(maxSps, sps);
			System.out.println(sps + " sps");
		}

		System.out.println("Max: " + maxSps + " sps");
	}
}
