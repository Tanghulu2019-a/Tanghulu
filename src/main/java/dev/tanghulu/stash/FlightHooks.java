package dev.tanghulu.stash;

import java.util.function.BooleanSupplier;

/** Keeps entity hooks inert until the optional flight module is registered. */
public final class FlightHooks {
    private static BooleanSupplier enabled = () -> false;

    private FlightHooks() {}

    static void bind(BooleanSupplier flightEnabled) {
        enabled = flightEnabled;
    }

    public static boolean isEnabled() {
        return enabled.getAsBoolean();
    }
}
