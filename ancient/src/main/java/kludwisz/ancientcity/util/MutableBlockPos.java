package kludwisz.ancientcity.util;

import com.seedfinding.mccore.util.block.BlockRotation;
import com.seedfinding.mccore.util.pos.BPos;

public class MutableBlockPos {
    public static final MutableBlockPos ORIGIN = new MutableBlockPos();

    public int x;
    public int y;
    public int z;

    public MutableBlockPos() {

    }

    public BPos toImmutable() {
        return new BPos(this.x, this.y, this.z);
    }

    public MutableBlockPos set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public MutableBlockPos set(BPos pos) {
        return this.set(pos.getX(), pos.getY(), pos.getZ());
    }

    public MutableBlockPos rotate(BlockRotation rotation) {
        final int prevX = this.x;
        final int prevZ = this.z;

        switch (rotation) {
            case NONE -> {

            }
            case CLOCKWISE_90 -> {
                this.x = -prevZ;
                this.z = prevX;
            }
            case CLOCKWISE_180 -> {
                this.x = -prevX;
                this.z = -prevZ;
            }
            case COUNTERCLOCKWISE_90 -> {
                this.x = prevZ;
                this.z = -prevX;
            }
        }
        return this;
    }

    public MutableBlockPos offset(int x, int y, int z) {
        this.x += x;
        this.y += y;
        this.z += z;
        return this;
    }

    public void setRotateOffset(BPos point, BlockRotation rotation, MutableBlockPos offset) {
        switch (rotation) {
            case NONE -> {
                this.x = offset.x + point.getX();
                this.y = offset.y + point.getY();
                this.z = offset.z + point.getZ();
            }
            case CLOCKWISE_90 -> {
                this.x = offset.x - point.getZ();
                this.y = offset.y + point.getY();
                this.z = offset.z + point.getX();
            }
            case CLOCKWISE_180 -> {
                this.x = offset.x - point.getX();
                this.y = offset.y + point.getY();
                this.z = offset.z - point.getZ();
            }
            case COUNTERCLOCKWISE_90 -> {
                this.x = offset.x + point.getZ();
                this.y = offset.y + point.getY();
                this.z = offset.z - point.getX();
            }
        }
    }
}
