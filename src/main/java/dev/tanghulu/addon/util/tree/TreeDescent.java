package dev.tanghulu.addon.util.tree;

/** One return step: prediction, server confirmation and physical landing are separate states. */
public final class TreeDescent {
    public enum BlockStatus { OWNED, PREDICTED_AIR, REMOVED, REPLACED, UNKNOWN }
    public enum Landing { SUPPORTED, WAIT_SYNC, UNSUPPORTED, HAZARD }
    public enum Action { CENTER, MINE, WAIT_CONFIRMATION, WAIT_LANDING, COMPLETE, WAIT_SUPPORT, RECOVER, REPLACED, UNSUPPORTED }
    private TreeDescent() {}

    public static BlockStatus classify(boolean localOwned, boolean localAir, boolean serverRemoved, boolean serverReplaced) {
        if (serverReplaced) return BlockStatus.REPLACED;
        if (serverRemoved) return localAir ? BlockStatus.REMOVED : BlockStatus.UNKNOWN;
        if (localOwned) return BlockStatus.OWNED;
        return localAir ? BlockStatus.PREDICTED_AIR : BlockStatus.UNKNOWN;
    }

    public static Action decide(int blockY, double feetY, boolean grounded, boolean centered,
                                BlockStatus block, Landing landing) {
        if (block == BlockStatus.REPLACED) return Action.REPLACED;
        if (block == BlockStatus.REMOVED) {
            if (grounded && feetY <= blockY + 0.1) return Action.COMPLETE;
            return centered ? Action.WAIT_LANDING : Action.CENTER;
        }
        if (block == BlockStatus.PREDICTED_AIR || block == BlockStatus.UNKNOWN) {
            return centered ? Action.WAIT_CONFIRMATION : Action.CENTER;
        }
        if (feetY < blockY + 0.75) return Action.RECOVER;
        if (!centered) return Action.CENTER;
        if (!grounded) return Action.WAIT_LANDING;
        if (landing == Landing.WAIT_SYNC) return Action.WAIT_SUPPORT;
        if (landing != Landing.SUPPORTED) return Action.UNSUPPORTED;
        return Action.MINE;
    }

    public static double centerStep(double position, int blockCoordinate) {
        return Math.clamp(blockCoordinate + 0.5 - position, -0.12, 0.12);
    }
}
