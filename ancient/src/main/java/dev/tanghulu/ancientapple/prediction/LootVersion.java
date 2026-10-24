package dev.tanghulu.ancientapple.prediction;

/** Backend loot rules are independent of the protocol version used by the client. */
public enum LootVersion {
    MC_1_21_1("1.21.1"), MC_1_21_2("1.21.2"), MC_1_21_3("1.21.3"),
    MC_1_21_4("1.21.4"), MC_1_21_5("1.21.5"), MC_1_21_6("1.21.6"),
    MC_1_21_7("1.21.7"), MC_1_21_8("1.21.8"), MC_1_21_9("1.21.9"),
    MC_1_21_10("1.21.10"), MC_1_21_11("1.21.11"),
    MC_26_1("26.1"), MC_26_1_1("26.1.1"), MC_26_1_2("26.1.2"),
    MC_26_2("26.2"), MC_26_3("26.3");
    private final String label;
    LootVersion(String label) { this.label=label; }
    @Override public String toString() { return label; }
}
