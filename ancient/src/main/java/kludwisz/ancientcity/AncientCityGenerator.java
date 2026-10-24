package kludwisz.ancientcity;

import java.util.*;

import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.block.BlockBox;
import com.seedfinding.mccore.util.block.BlockDirection;
import com.seedfinding.mccore.util.block.BlockMirror;
import com.seedfinding.mccore.util.block.BlockRotation;
import com.seedfinding.mccore.util.data.Pair;
import com.seedfinding.mccore.util.data.Triplet;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.LootTable;
import com.seedfinding.mcseed.rand.JRand;

import kludwisz.ancientcity.jigsawpools.AncientCityJigsawBlocks;
import kludwisz.ancientcity.jigsawpools.AncientCityPools;
import kludwisz.ancientcity.jigsawpools.JigsawBlock;
import kludwisz.ancientcity.structures.AncientCityPieceNames;
import kludwisz.ancientcity.structures.AncientCityStructureLoot;
import kludwisz.ancientcity.structures.AncientCityStructureSize;
import kludwisz.ancientcity.util.BlockBoxUtil;
import kludwisz.ancientcity.util.MutableBlockPos;
import kludwisz.ancientcity.util.RotationUtil;
import kludwisz.util.DecoratorRand;
import kludwisz.util.VoxelShape;

// ancient city generator by Kludwisz
// huge thanks to profotoce59 for the original code: 
// https://github.com/profotoce59/VillageGenerator

public class AncientCityGenerator {
    private static final int MAX_DIST = 116; // max distance from city anchor
    public static final int MAX_DEPTH = 7;

    public static final BlockRotation[] BLOCK_ROTATIONS = BlockRotation.values();

    public final Piece[] pieces = new Piece[256];
    public final VoxelShape[] voxelShapes = new VoxelShape[256];
    public int piecesLen;
    private long worldseed;
    public final Deque<Piece> placing = new ArrayDeque<>();

    private final BlockBox rootBox = BlockBox.empty();
    private final BlockJigsawInfo[] parentJigsawsArr = new BlockJigsawInfo[5];
    private final BlockJigsawInfo[] childPieceJigsawBlocksArr = new BlockJigsawInfo[5];
    private final int[] childTemplatesArr = new int[46];
    private final BlockRotation[] childRotationsArr = new BlockRotation[4];
    private final MutableBlockPos childJigsawPos = new MutableBlockPos();
    private final BlockBox childPieceMinBox = BlockBox.empty();

    public AncientCityGenerator() {
        for (int i = 0; i < this.pieces.length; i++) {
            this.pieces[i] = new Piece(i);
        }for (int i = 0; i < this.voxelShapes.length; i++) {
            this.voxelShapes[i] = new VoxelShape();
        }
        for (int i = 0; i < this.parentJigsawsArr.length; i++) {
            this.parentJigsawsArr[i] = new BlockJigsawInfo();
        }
        for (int i = 0; i < this.childPieceJigsawBlocksArr.length; i++) {
            this.childPieceJigsawBlocksArr[i] = new BlockJigsawInfo();
        }
    }
    
    public void generate(long worldseed, int chunkX, int chunkZ, ChunkRand rand) {
        this.piecesLen = 0;
        this.worldseed = worldseed;

        // choose random starting template and rotation
        rand.setCarverSeed(this.worldseed, chunkX, chunkZ, MCVersion.v1_19); // version doesnt matter
        BlockRotation startPieceRotation = rand.getRandom(BLOCK_ROTATIONS);
        int startPieceId = START_TEMPLATES[rand.nextInt(START_TEMPLATES.length)];
        BPos startPieceSize = AncientCityStructureSize.STRUCTURE_SIZE_V2.get(startPieceId);
        
        // STARTING JIGSAW:
        // For ancient cities, there is only 1 starting jigsaw at the same position in all structure starts, so we can use
        // this shortened version of getRandomNamedJigsaw() that uses precalculated values
        Main.skipShuffle(rand, 5);

        // moving startPos by the relative position of ancient city anchor to get correct bounding boxes
        Piece startPiece = this.pieces[this.piecesLen];
        MutableBlockPos startPiecePos = startPiece.pos;
        startPiecePos.set(-13, -24, -20).rotate(startPieceRotation).offset(chunkX << 4, -27, chunkZ << 4);
        BlockBox startPieceBox = startPiece.box;
        BlockBoxUtil.setSizeRotatePos(startPieceBox, startPieceSize, startPieceRotation, startPiecePos);
        int centerX = (startPieceBox.minX + startPieceBox.maxX) / 2;
        int centerZ = (startPieceBox.minZ + startPieceBox.maxZ) / 2;
        int centerY = startPieceBox.minY + 1;
        int y = startPiecePos.y;

        // create the first piece
        startPiece.id = startPieceId;
        BlockBoxUtil.set(startPiece.box, startPieceBox);
        startPiece.rotation = startPieceRotation;
        startPiece.depth = 0;
        startPiece.move(0, y - centerY, 0);

        // create structure max bounding box
        this.placing.clear();
        this.piecesLen++;
        VoxelShape rootFreeSpace = this.voxelShapes[255].init(BlockBoxUtil.set(this.rootBox, centerX - MAX_DIST, y - MAX_DIST, centerZ - MAX_DIST, centerX + MAX_DIST, y + MAX_DIST, centerZ + MAX_DIST));
        rootFreeSpace.cutout.add(startPieceBox);
        startPiece.freeSpace = rootFreeSpace;
        
        // place pieces
        this.placing.addLast(startPiece);
        while (!this.placing.isEmpty()) {
            this.tryPlacing(this.placing.removeFirst(), rand);
        }
    }

    public void tryPlacing(Piece parentPiece, ChunkRand rand) {
        int parentPieceId = parentPiece.id;
        int parentPieceDepth = parentPiece.depth;
        MutableBlockPos parentPiecePos = parentPiece.pos;
        VoxelShape parentPieceInnerFreeSpace = null;
        BlockBox parentPieceBox = parentPiece.box;

        if (parentPieceDepth == MAX_DEPTH) {
            Main.skipShuffle(rand, AncientCityJigsawBlocks.JIGSAW_BLOCKS_V2.get(parentPieceId).size());
            return;
        }

        BlockJigsawInfo[] parentJigsaws = this.parentJigsawsArr;
        int parentJigsawsLen = getShuffledJigsawBlocks(rand, parentJigsaws, parentPieceId, parentPiece.rotation, parentPiecePos);
        nextParentJigsaw:
        for (int parentJigsawIndex = 0; parentJigsawIndex < parentJigsawsLen; parentJigsawIndex++) {
            BlockJigsawInfo parentJigsaw = parentJigsaws[parentJigsawIndex];

            Piece childPiece = this.pieces[this.piecesLen];
            MutableBlockPos childPiecePos = childPiece.pos;
            BlockBox childPieceBox = childPiece.box;

            BlockDirection parentJigsawFront = parentJigsaw.front;
            MutableBlockPos parentJigsawPos = parentJigsaw.pos;
            this.childJigsawPos.set(
                    parentJigsawPos.x + parentJigsawFront.getVector().getX(),
                    parentJigsawPos.y + parentJigsawFront.getVector().getY(),
                    parentJigsawPos.z + parentJigsawFront.getVector().getZ()
            );

            VoxelShape freeSpace;

            if (BlockBoxUtil.contains(parentPieceBox, this.childJigsawPos)) {
                if (parentPieceInnerFreeSpace == null) {
                    parentPieceInnerFreeSpace = this.voxelShapes[parentPiece.index].init(parentPieceBox);
                }
                freeSpace = parentPieceInnerFreeSpace;
            } else {
                freeSpace = parentPiece.freeSpace;
            }

            boolean skip = false;
            BlockBox childPieceMinBox = AncientCityJigsawBlocks.PIECE_TARGET_MIN_BOXES[parentPieceId].get(parentJigsaw.nbt.targetName);
            if (childPieceMinBox != null) {
                BlockBoxUtil.setRotateMove(this.childPieceMinBox, childPieceMinBox, parentPiece.rotation, childJigsawPos);
                if (!isInsideFreeSpace(freeSpace, this.childPieceMinBox)) {
                    skip = true;
                }
            }

            int[] childTemplates = this.childTemplatesArr;
            int childTemplatesLen = getShuffledTemplatesFromPool(rand, parentJigsaw.nbt.poolType, childTemplates);
            for (int childTemplateIndex = 0; childTemplateIndex < childTemplatesLen; childTemplateIndex++) {
                int childPieceId = childTemplates[childTemplateIndex];
                if (childPieceId == 60) // empty piece
                    break;

                Set<BlockDirection> directions = AncientCityJigsawBlocks.PIECE_CONNECTION_DIRECTIONS[childPieceId].get(parentJigsaw.nbt.targetName);
                if (directions == null || skip) {
                    Main.skipShuffle(rand, 4);
                    for (int i = 0; i < 4; i++) {
                        Main.skipShuffle(rand, AncientCityJigsawBlocks.JIGSAW_BLOCKS_V2.get(childPieceId).size());
                    }
                    continue;
                }

                BlockRotation[] childRotations = this.childRotationsArr;
                getShuffledBlockRotations(rand, childRotations);
                for (BlockRotation childPieceRotation : childRotations) {

                    BlockRotation childRotationInverse = switch (childPieceRotation) {
                        case CLOCKWISE_90 -> BlockRotation.COUNTERCLOCKWISE_90;
                        case COUNTERCLOCKWISE_90 -> BlockRotation.CLOCKWISE_90;
                        default -> childPieceRotation;
                    };
                    if (!directions.contains(childRotationInverse.rotate(parentJigsawFront))) {
                        Main.skipShuffle(rand, AncientCityJigsawBlocks.JIGSAW_BLOCKS_V2.get(childPieceId).size());
                        continue;
                    }

                    BPos childPieceSize = AncientCityStructureSize.STRUCTURE_SIZE_V2.get(childPieceId);

                    BlockJigsawInfo[] arr2 = this.childPieceJigsawBlocksArr;
                    int len2 = getShuffledJigsawBlocks(rand, arr2, childPieceId, childPieceRotation, MutableBlockPos.ORIGIN);
                    for (int ji2 = 0; ji2 < len2; ji2++) {
                        BlockJigsawInfo childJigsaw = arr2[ji2];
                        //Main.A += 1;

                        if (!parentJigsaw.canAttach(childJigsaw, parentJigsawFront)) continue;

                        //Main.B += 1;

                        MutableBlockPos childJigsawOffset = childJigsaw.pos;
                        childPiecePos.set(
                                this.childJigsawPos.x - childJigsawOffset.x,
                                this.childJigsawPos.y - childJigsawOffset.y,
                                this.childJigsawPos.z - childJigsawOffset.z
                        );

                        if (childPieceSize.getX() < 1 || childPieceSize.getY() < 1 || childPieceSize.getZ() < 1) {
                            BlockBoxUtil.set(childPieceBox, childPiecePos);
                        } else {
                            BlockBoxUtil.setSizeRotatePos(childPieceBox, childPieceSize, childPieceRotation, childPiecePos);
                        }

                        if (!isInsideFreeSpace(freeSpace, childPieceBox)) continue;

                        //Main.C += 1;

                        freeSpace.cutout.add(childPieceBox);

                        int childPieceDepth = parentPieceDepth + 1;
                        childPiece.id = childPieceId;
                        childPiece.rotation = childPieceRotation;
                        childPiece.depth = childPieceDepth;
                        childPiece.freeSpace = freeSpace;

                        this.piecesLen += 1;

                        if (childPieceDepth <= MAX_DEPTH) {
                            this.placing.addLast(childPiece);
                            //System.out.println(childPieceDepth + " -> " + rand.getSeed());
                        }

                        continue nextParentJigsaw;
                    }
                }
            }
        }
    }

    private boolean isInsideFreeSpace(VoxelShape freeSpace, BlockBox box) {
        if (box.minX < freeSpace.outer.minX || box.minY < freeSpace.outer.minY || box.minZ < freeSpace.outer.minZ || box.maxX > freeSpace.outer.maxX || box.maxY > freeSpace.outer.maxY || box.maxZ > freeSpace.outer.maxZ) {
            return false;
        }

        for (BlockBox cutoutBox : freeSpace.cutout){
            if (intersects(box, cutoutBox)){
                return false;
            }
        }
        return true;
    }

    public static boolean intersects(BlockBox box1, BlockBox box) {
        return box1.maxX >= box.minX && box1.minX <= box.maxX && box1.maxZ >= box.minZ && box1.minZ <= box.maxZ && box1.maxY >= box.minY && box1.minY <= box.maxY;
    }

    static public class Piece {
    	public int id;
        public MutableBlockPos pos;
        public BlockBox box;
        public BlockRotation rotation;
        public VoxelShape freeSpace;
        public int depth;
        int index;

        public String getName(){
            return AncientCityPieceNames.PIECENAMES.get(this.id);
        }

        public Piece(int index) {
            this.pos = new MutableBlockPos();
            this.box = new BlockBox(0, 0, 0, 0, 0, 0);
            this.index = index;
        }

        public void move(int x, int y, int z) {
            this.pos.offset(x, y, z);
            this.box.move(x, y, z);
        }
    }

    public static class BlockJigsawInfo {
        public JigsawBlock nbt;
        public MutableBlockPos pos = new MutableBlockPos();
//        public BPos pos;
        public BlockDirection front;
        public BlockJigsawInfo() {

        }
        
        public static BlockDirection getOpposite(BlockDirection b){
            switch (b) {
                case NORTH:
                    return BlockDirection.SOUTH;
                case SOUTH:
                    return BlockDirection.NORTH;
                case WEST:
                    return BlockDirection.EAST;
                case EAST:
                    return BlockDirection.WEST;
                case DOWN:
                    return BlockDirection.UP;
                case UP:
                    return BlockDirection.DOWN;
                default:
                    throw new IllegalStateException("Unable to get facing of " );
            }
        }
        
        public boolean canAttach(BlockJigsawInfo blockJigsawInfo2, BlockDirection direction) {
            return (direction.ordinal() ^ 1) == blockJigsawInfo2.front.ordinal() && this.nbt.targetName.equals(blockJigsawInfo2.nbt.name);
        }
    }

    public static int getShuffledJigsawBlocks(JRand rand, BlockJigsawInfo[] arr, int id, BlockRotation rotation, MutableBlockPos offset) {//taking 20% need to opti
        JigsawBlock[] blocks = AncientCityJigsawBlocks.JIGSAW_BLOCKS[id];
        int len = blocks.length;
        for (int i = 0; i < len; i++) {
            JigsawBlock jigsawBlock = blocks[i];
            BlockJigsawInfo blockJigsawInfo = arr[i];
            blockJigsawInfo.nbt = jigsawBlock;
            blockJigsawInfo.pos.setRotateOffset(jigsawBlock.relativePos, rotation, offset);
//            blockJigsawInfo.front = rotation.rotate(jigsawBlock.direction1);
            blockJigsawInfo.front = RotationUtil.rotate(rotation, jigsawBlock.direction1);
        }
        Main.shuffle(rand, arr, len);
        return len;
    }
    
    private static int getShuffledTemplatesFromPool(JRand rand, int poolId, int[] arr) {
        int[] pool = AncientCityPools.CITY_POOLS[poolId];
        int len = pool.length;
        System.arraycopy(pool, 0, arr, 0, len);
    	Main.shuffle(rand, arr, len);
    	return len;
    }

    public static void getShuffledBlockRotations(JRand rand, BlockRotation[] arr) {
        arr[0] = BlockRotation.NONE;
        arr[1] = BlockRotation.CLOCKWISE_90;
        arr[2] = BlockRotation.CLOCKWISE_180;
        arr[3] = BlockRotation.COUNTERCLOCKWISE_90;
        Main.shuffle(rand, arr);
    }

    public static final int[] START_TEMPLATES = AncientCityPools.CITY_POOLS[0];

    
    // -------------------------
    // SEEDFINDING UTILS
    // -------------------------
    
    public List<Pair<BPos, LootTable>> getChests() {
    	ArrayList<Pair<BPos, LootTable>> result = new ArrayList<>();
    	
    	for (int pieceIndex = 0; pieceIndex < this.piecesLen; pieceIndex++) {
            Piece p = this.pieces[pieceIndex];

            List<Pair<BPos, LootTable>> chests = AncientCityStructureLoot.STRUCTURE_LOOT_V2.get(p.id);
    		if (chests==null || chests.isEmpty())
    			continue;
    		
    		for (Pair<BPos, LootTable> chest : chests) {
    			BPos rotatedOffset = chest.getFirst().transform(BlockMirror.NONE, p.rotation, BPos.ORIGIN);
    			BPos realChestPos = p.pos.toImmutable().add(rotatedOffset);
    			result.add(new Pair<>(realChestPos, chest.getSecond()));
    		}
    	}
    	
    	return result;
    }
    
    
    public List<Triplet<BPos, LootTable, Long>> getChestsWithLootSeeds() {
    	ArrayList<Triplet<BPos, LootTable, Long>> result = new ArrayList<>();
    	HashMap<CPos, DecoratorRand> chunkRandoms = new HashMap<>();

        for (int pieceIndex = 0; pieceIndex < this.piecesLen; pieceIndex++) {
            Piece p = this.pieces[pieceIndex];
    		
    		List<Pair<BPos, LootTable>> chests = AncientCityStructureLoot.STRUCTURE_LOOT_V2.get(p.id);
    		if (chests==null || chests.isEmpty())
    			continue;	 

    		for (Pair<BPos, LootTable> chest : chests) {
    			BPos rotatedOffset = chest.getFirst().transform(BlockMirror.NONE, p.rotation, BPos.ORIGIN);
    			BPos realChestPos = p.pos.toImmutable().add(rotatedOffset);
    			CPos chestChunkPos = realChestPos.toChunkPos();
    			
    			if (!chunkRandoms.containsKey(chestChunkPos)) {
    				DecoratorRand rand = new DecoratorRand();
    				long popseed = rand.getPopulationSeed(this.worldseed, chestChunkPos.getX()<<4, chestChunkPos.getZ()<<4);
    				rand.setDecoratorSeed(popseed, 0, 7);
    				chunkRandoms.put(chestChunkPos, rand);
    			} 

    			long lootseed = chunkRandoms.get(chestChunkPos).nextLong();
				result.add(new Triplet<>(realChestPos, chest.getSecond(), lootseed));
    		}
    	}
    	
    	return result;
    }
    
    public List<Triplet<CPos, LootTable, Integer>> getChestChunksWithRandCalls() {
    	ArrayList<Triplet<CPos, LootTable, Integer>> result = new ArrayList<>();
    	HashMap<CPos, Integer> callMap = new HashMap<>();

        for (int pieceIndex = 0; pieceIndex < this.piecesLen; pieceIndex++) {
            Piece p = this.pieces[pieceIndex];

    		List<Pair<BPos, LootTable>> chests = AncientCityStructureLoot.STRUCTURE_LOOT_V2.get(p.id);
    		if (chests==null || chests.isEmpty())
    			continue;	 

    		for (Pair<BPos, LootTable> chest : chests) {
    			BPos rotatedOffset = chest.getFirst().transform(BlockMirror.NONE, p.rotation, BPos.ORIGIN);
    			BPos realChestPos = p.pos.toImmutable().add(rotatedOffset);
    			CPos chestChunkPos = realChestPos.toChunkPos();
    			
    			if (!callMap.containsKey(chestChunkPos)) {
    				callMap.put(chestChunkPos, 0);
    			} 

    			int calls = callMap.get(chestChunkPos);
    			callMap.put(chestChunkPos, calls+1);
				result.add(new Triplet<>(chestChunkPos, chest.getSecond(), calls));
    		}
    	}
    	return result;
    }
}
