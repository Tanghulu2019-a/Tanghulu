package dev.tanghulu.addon.util.seeds;

import baritone.api.IBaritone;

/** Loaded after checking the optional Meteor Baritone dependency. */
public final class BaritoneMiningDriver implements AutoMineController.Driver {
    private final IBaritone baritone;

    public BaritoneMiningDriver(IBaritone baritone) { this.baritone = java.util.Objects.requireNonNull(baritone); }

    @Override public void start(String[] ores) {
        baritone.getPathingBehavior().cancelEverything();
        baritone.getMineProcess().mineByName(ores);
    }

    @Override public boolean active() { return baritone.getMineProcess().isActive(); }

    @Override public void stop() {
        boolean mining = baritone.getMineProcess().isActive();
        baritone.getMineProcess().cancel();
        if (mining) baritone.getPathingBehavior().cancelEverything();
    }
}
