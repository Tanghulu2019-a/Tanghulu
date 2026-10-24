package kludwisz.ancientcity.util;

import com.seedfinding.mccore.util.block.BlockDirection;
import com.seedfinding.mccore.util.block.BlockRotation;

public class RotationUtil {
    public static final int[] DIRECTION_INDICES = new int[]{ -1, -1, 0, 2, 3, 1 };

    public static BlockDirection rotate(BlockRotation rotation, BlockDirection direction) {
        if (direction.ordinal() < 2) return direction;

        return BlockDirection.getHorizontal()[(rotation.ordinal() + DIRECTION_INDICES[direction.ordinal()]) & 0b11];
    }
}
