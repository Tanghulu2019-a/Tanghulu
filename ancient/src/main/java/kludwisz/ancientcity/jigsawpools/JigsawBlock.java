package kludwisz.ancientcity.jigsawpools;

import com.seedfinding.mccore.util.block.BlockDirection;
import com.seedfinding.mccore.util.pos.BPos;

public class JigsawBlock {
    public int poolType;
    public String name;
    public String targetName;
    public BlockDirection direction1;
    public BPos relativePos;

    public JigsawBlock(int poolType, String name, String targetName, BlockDirection direction1, BPos relativePos) {
        this.poolType = poolType;
        this.name = name;
        this.targetName = targetName;
        this.direction1 = direction1;
        this.relativePos = relativePos;
    }
}