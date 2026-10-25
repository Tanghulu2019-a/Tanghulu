package dev.tanghulu.ancientapple.core;

/** Filters existing predictions without changing loot generation or collection history. */
public enum LootSelection {
    Apples("附魔金苹果"), SwiftSneak("迅捷潜行3"), All("全部");

    private final String title;
    LootSelection(String title) { this.title=title; }
    @Override public String toString() { return title; }

    public boolean matches(ChestTarget target) {
        return switch(this) {
            case Apples -> target.apples>0;
            case SwiftSneak -> target.swiftSneakBooks>0;
            case All -> true;
        };
    }
    public String label(ChestTarget target) {
        return switch(this) {
            case Apples -> "附魔金苹果 ×"+target.apples;
            case SwiftSneak -> "迅捷潜行 III ×"+target.swiftSneakBooks;
            case All -> target.lootLabel();
        };
    }
    public boolean bookIcon(ChestTarget target) {
        return this==SwiftSneak || (this==All && target.swiftSneakBooks>0 && target.apples==0);
    }
}
