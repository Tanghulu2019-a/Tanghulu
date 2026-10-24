package dev.tanghulu.addon.util.mushroom;

/** Explicit releases only: never silently substitute another world's generation algorithm. */
public enum MushroomVersion {
    v1_21_1, v1_21_2, v1_21_3, v1_21_4, v1_21_5, v1_21_6,
    v1_21_7, v1_21_8, v1_21_9, v1_21_10, v1_21_11,
    v26_1, v26_1_1, v26_1_2, v26_2, v26_3;

    @Override public String toString() { return name().substring(1).replace('_', '.'); }
    public boolean requiresJava25() { return name().startsWith("v26_"); }
}
