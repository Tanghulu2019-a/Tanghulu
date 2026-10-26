package dev.tanghulu.notebot;

import dev.tanghulu.addon.util.notebot.TuneConfirmation;
import dev.tanghulu.addon.util.notebot.TuneRetryPolicy;

/** A dropped interaction must become retryable; a late pitch change must not be counted twice. */
public final class TuneRetryChecks {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var pending = new TuneConfirmation();
        var retries = new TuneRetryPolicy<String>();
        pending.begin(6, 3);
        pending.tick();
        try {
            pending.retry();
            throw new AssertionError("An unexpired interaction cannot be retransmitted");
        } catch (IllegalStateException expected) { checks++; }
        check(!pending.observe(6), "A rejected click with unchanged pitch remains unconfirmed");
        check(!pending.tick() && pending.tick(), "A dropped click reaches its timeout");
        check(retries.schedule("block A", 6) == 1, "The first timeout schedules a retry");
        pending.retry();
        check(!pending.waiting(), "A timeout releases the request instead of waiting forever");
        pending.begin(6, 3);
        check(pending.observe(7), "The retried right click can be acknowledged normally");
        retries.progress("block A");
        check(retries.attempts("block A") == 0, "Observed pitch progress resets consecutive failures");
        check(TuneConfirmation.remaining(7, 10) == 3, "Continue from actual pitch rather than the original planned hit count");

        // A late original click and a retry both take effect: actual server pitch is authoritative.
        pending.begin(7, 2);
        pending.tick(); pending.tick();
        retries.schedule("block A", 6);
        pending.retry();
        pending.begin(7, 2);
        check(pending.observe(9), "A two-step late update is accepted as authoritative");
        check(TuneConfirmation.remaining(9, 10) == 1, "Late plus retry must not blindly subtract one more hit");
        check(TuneConfirmation.remaining(11, 10) == 24, "A late overshoot is retuned using modulo 25");

        // A late update arrives during the backoff, before the scheduled retry is sent.
        retries.progress("block A");
        check(retries.schedule("block A", 6) == 1, "Backoff starts afresh after progress");
        retries.progress("block A");
        check(TuneConfirmation.remaining(10, 10) == 0 && retries.attempts("block A") == 0,
            "A target reached during backoff needs no replacement right click");
        check(TuneRetryPolicy.backoffTicks(1, 4) == 20, "First retry has a one-second cooldown at 20 TPS");
        check(TuneRetryPolicy.backoffTicks(2, 4) == 40, "Repeated failures slow down retries");
        check(TuneRetryPolicy.backoffTicks(6, 4) == 100, "Backoff is capped at five seconds");
        check(TuneRetryPolicy.backoffTicks(1, 200) == 200, "Respect a longer configured interval");

        retries.reset();
        for (int i = 1; i <= 6; i++) check(retries.schedule("block A", 6) == i, "Retry limit permits the configured attempts");
        check(retries.schedule("block A", 6) == -1, "Permanent rejection stops after the retry limit");
        check(retries.schedule("block B", 6) == 1, "Unrelated note blocks have independent failure budgets");
        retries.progress("block B");
        check(retries.schedule("block A", 6) == -1, "Progress on another block must not reset a stuck block");
        retries.progress("block A");
        check(retries.schedule("block A", 6) == 1, "Actual pitch progress permits later failures to recover");
        check(retries.total() == 8, "Total retry diagnostics survive per-pitch recovery");
        retries.reset(); pending.reset();
        check(retries.total() == 0 && retries.attempts("block A") == 0 && !pending.waiting(), "Stop and new song clear recovery state");
        try {
            pending.retry();
            throw new AssertionError("An absent request cannot be retried");
        } catch (IllegalStateException expected) { checks++; }
        System.out.println("Notebot timeout recovery checks passed: " + checks);
    }
}
