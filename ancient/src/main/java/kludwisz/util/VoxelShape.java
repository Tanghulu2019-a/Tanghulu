package kludwisz.util;

import com.seedfinding.mccore.util.block.BlockBox;

import java.util.ArrayList;
import java.util.List;

public class VoxelShape {
    public BlockBox outer;
    public List<BlockBox> cutout = new ArrayList<>();

    public VoxelShape() {

    }

    public VoxelShape init(BlockBox box) {
        this.outer = box;
        this.cutout.clear();
        return this;
    }
}
