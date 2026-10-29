package dev.tanghulu.addon.util.tree;

public enum TreeSpecies {
    OAK("橡树", "oak"), SPRUCE("云杉", "spruce"), BIRCH("白桦", "birch"),
    JUNGLE("丛林木", "jungle"), ACACIA("金合欢", "acacia"),
    DARK_OAK("深色橡树", "dark_oak"), MANGROVE("红树", "mangrove"),
    CHERRY("樱花树", "cherry"), PALE_OAK("苍白橡树", "pale_oak");

    public final String title;
    public final String id;

    TreeSpecies(String title, String id) { this.title = title; this.id = id; }
    public String logId() { return id + "_log"; }
    public String leavesId() { return id + "_leaves"; }
}
