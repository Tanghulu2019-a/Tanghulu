package dev.tanghulu.addon.util.seeds;

import com.seedfinding.mccore.version.MCVersion;

/** Keeps legacy enum identifiers and labels for existing Meteor settings. */
public enum SeedVersion {
    v26_3,
    v1_21, v1_20_6, v1_20_5, v1_20_4, v1_20_3, v1_20_2, v1_20_1, v1_20, v1_19_4, v1_19_3, v1_19_2, v1_19_1, v1_19, v1_18_2, v1_18_1, v1_18, v1_17_1, v1_17, v1_16_5, v1_16_4, v1_16_3, v1_16_2, v1_16_1, v1_16, v1_15_2, v1_15_1, v1_15, v1_14_4, v1_14_3, v1_14_2, v1_14_1, v1_14, v1_13_2, v1_13_1, v1_13, v1_12_2, v1_12_1, v1_12, v1_11_2, v1_11_1, v1_11, v1_10_2, v1_10_1, v1_10, v1_9_4, v1_9_3, v1_9_2, v1_9_1, v1_9, v1_8_9, v1_8_8, v1_8_7, v1_8_6, v1_8_5, v1_8_4, v1_8_3, v1_8_2, v1_8_1, v1_8, v1_7_10, v1_7_9, v1_7_8, v1_7_7, v1_7_6, v1_7_5, v1_7_4, v1_7_3, v1_7_2, v1_6_4, v1_6_2, v1_6_1, v1_5_2, v1_5_1, v1_4_7, v1_4_6, v1_4_5, v1_4_4, v1_4_2, v1_3_2, v1_3_1, v1_2_5, v1_2_4, v1_2_3, v1_2_2, v1_2_1, v1_1, v1_0, vb1_8_1, vb1_8, vb1_7_3, vb1_7_2, vb1_7, vb1_6_6, vb1_6_5, vb1_6_4, vb1_6_3, vb1_6_2, vb1_6_1, vb1_6, vb1_5_01, vb1_5, vb1_4_01, vb1_4, vb1_3_01, vb1_3b, vb1_2_02, vb1_2_01, vb1_2, vb1_1_02, vb1_1_01, vb1_0_2, vb1_0_01, vb1_0, va1_2_6, va1_2_5, va1_2_4_01, va1_2_3_04, va1_2_3_02, va1_2_3_01, va1_2_3, va1_2_2b, va1_2_2a, va1_2_1_01, va1_2_1, va1_2_0_02, va1_2_0_01, va1_2_0, va1_1_2_01, va1_1_2, va1_1_0, va1_0_17_04, va1_0_17_02, va1_0_16, va1_0_15, va1_0_14, va1_0_11, va1_0_5_01, va1_0_4;

    @Override public String toString() { return this == v26_3 ? "26.3" : MCVersion.valueOf(name()).name; }
}
