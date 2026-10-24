package kludwisz.ancientcity.util;

import com.seedfinding.mccore.util.block.BlockBox;
import com.seedfinding.mccore.util.block.BlockRotation;
import com.seedfinding.mccore.util.pos.BPos;

public class BlockBoxUtil {
    public static BlockBox set(BlockBox box, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        box.minX = minX;
        box.minY = minY;
        box.minZ = minZ;
        box.maxX = maxX;
        box.maxY = maxY;
        box.maxZ = maxZ;
        return box;
    }

    public static BlockBox set(BlockBox box, MutableBlockPos pos) {
        return set(box, pos.x, pos.y, pos.z, pos.x, pos.y, pos.z);
    }

    public static BlockBox set(BlockBox box, BlockBox other) {
        return set(box, other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ);
    }

    public static void setOffset(BlockBox box, BlockBox other, MutableBlockPos offset) {
        box.minX = other.minX + offset.x;
        box.minY = other.minY + offset.y;
        box.minZ = other.minZ + offset.z;
        box.maxX = other.maxX + offset.x;
        box.maxY = other.maxY + offset.y;
        box.maxZ = other.maxZ + offset.z;
    }

    public static void setSizeRotatePos(BlockBox box, BPos size, BlockRotation rotation, MutableBlockPos pos) {
        int sx = size.getX() - 1;
        int sy = size.getY() - 1;
        int sz = size.getZ() - 1;
        if (rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90) {
            int tmp = sx;
            sx = sz;
            sz = tmp;
        }
        box.minY = pos.y;
        box.maxY = pos.y + sy;
        switch (rotation) {
            case NONE -> {
                box.minX = pos.x;
                box.minZ = pos.z;
                box.maxX = pos.x + sx;
                box.maxZ = pos.z + sz;
            }
            case CLOCKWISE_90 -> {
                box.minX = pos.x - sx;
                box.minZ = pos.z;
                box.maxX = pos.x;
                box.maxZ = pos.z + sz;
            }
            case CLOCKWISE_180 -> {
                box.minX = pos.x - sx;
                box.minZ = pos.z - sz;
                box.maxX = pos.x;
                box.maxZ = pos.z;
            }
            case COUNTERCLOCKWISE_90 -> {
                box.minX = pos.x;
                box.minZ = pos.z - sz;
                box.maxX = pos.x + sx;
                box.maxZ = pos.z;
            }
        }
    }

    public static void setRotateMove(BlockBox box, BlockBox other, BlockRotation rotation, MutableBlockPos offset) {
        box.minY = offset.y + other.minY;
        box.maxY = offset.y + other.maxY;
        switch (rotation) {
            case NONE -> {
                box.minX = offset.x + other.minX;
                box.minZ = offset.z + other.minZ;
                box.maxX = offset.x + other.maxX;
                box.maxZ = offset.z + other.maxZ;
            }
            case CLOCKWISE_90 -> {
                box.minX = offset.x - other.maxZ;
                box.minZ = offset.z + other.minX;
                box.maxX = offset.x - other.minZ;
                box.maxZ = offset.z + other.maxX;
            }
            case CLOCKWISE_180 -> {
                box.minX = offset.x - other.maxX;
                box.minZ = offset.z - other.maxZ;
                box.maxX = offset.x - other.minX;
                box.maxZ = offset.z - other.minZ;
            }
            case COUNTERCLOCKWISE_90 -> {
                box.minX = offset.x + other.minZ;
                box.minZ = offset.z - other.maxX;
                box.maxX = offset.x + other.maxZ;
                box.maxZ = offset.z - other.minX;
            }
        }
    }

    public static boolean contains(BlockBox box, MutableBlockPos pos) {
        return pos.x >= box.minX && pos.x <= box.maxX && pos.z >= box.minZ && pos.z <= box.maxZ && pos.y >= box.minY && pos.y <= box.maxY;
    }

    public static void intersect(BlockBox box, BlockBox other) {
        box.minX = Math.max(box.minX, other.minX);
        box.minY = Math.max(box.minY, other.minY);
        box.minZ = Math.max(box.minZ, other.minZ);
        box.maxX = Math.min(box.maxX, other.maxX);
        box.maxY = Math.min(box.maxY, other.maxY);
        box.maxZ = Math.min(box.maxZ, other.maxZ);
    }

    public static boolean isEmpty(BlockBox box) {
        return box.minX > box.maxX || box.minY > box.maxY || box.minZ > box.maxZ;
    }
}
