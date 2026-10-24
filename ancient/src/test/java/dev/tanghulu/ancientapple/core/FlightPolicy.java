package dev.tanghulu.ancientapple.core;

public final class FlightPolicy {
    private FlightPolicy() {}
    public static boolean resourcesAllow(int rockets, int reserve, double durability, boolean emergency) {
        return durability>.10 && rockets>(emergency ? 0 : reserve);
    }
    /** Short hops are allowed; the caller supplies collision-checked travel costs. */
    public static boolean preferFlight(boolean clear, boolean landing, double groundCost, double flightCost, boolean emergency) {
        return clear && landing && (emergency || flightCost<groundCost);
    }
}
