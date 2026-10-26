package dev.tanghulu.notebot;

import dev.tanghulu.addon.util.notebot.TuneConfirmation;

/** Regression cases for rejected, delayed, wrapped and externally changed tuning. */
public final class TuneConfirmationChecks {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        TuneConfirmation tracker = new TuneConfirmation();
        check(!tracker.waiting() && !tracker.tick(), "Idle state cannot time out");
        tracker.begin(12, 4);
        check(!tracker.observe(12), "An unchanged server state must not acknowledge a rejected right click");
        check(tracker.waiting(), "Sending a click does not mean the server applied it");
        try {
            tracker.begin(13, 4);
            throw new AssertionError("Concurrent tuning must be rejected");
        } catch (IllegalStateException expected) { checks++; }
        check(!tracker.tick() && !tracker.tick() && !tracker.tick(), "Wait for the full timeout");
        check(tracker.tick(), "Missing server updates eventually stop tuning");
        check(tracker.waiting() && tracker.elapsedTicks() == 4, "Timeout must retain the pending click when the caller chooses to keep waiting");
        check(tracker.tick() && tracker.tick(), "Continue waiting without generating a replacement click");
        check(tracker.observe(13), "A delayed acknowledgement can recover after the warning threshold");
        check(!tracker.waiting(), "Late acknowledgement resumes tuning without another right click");
        tracker.reset();
        tracker.begin(24, 60);
        check(tracker.observe(0), "Pitch wraps from 24 to 0");
        check(!tracker.waiting() && !tracker.tick(), "An acknowledged click stops the timeout");
        check(!tracker.observe(1), "An unsolicited update cannot acknowledge an absent click");
        tracker.begin(5, 60);
        check(tracker.observe(9), "An external pitch change is authoritative; recompute remaining clicks");
        check(TuneConfirmation.remaining(9, 10) == 1, "Recompute from the actual server pitch");
        check(TuneConfirmation.remaining(24, 1) == 2, "Modulo-25 tuning distance");
        for (int from = 0; from < 25; from++) for (int target = 0; target < 25; target++) {
            int clicks = TuneConfirmation.remaining(from, target);
            check(clicks >= 0 && clicks <= 24 && (from + clicks) % 25 == target, "All pitch pairs reach the target without an extra cycle");
        }
        tracker.begin(0, 2);
        tracker.reset();
        check(!tracker.waiting() && !tracker.observe(1), "Stop/load/disconnect invalidates an old acknowledgement");
        System.out.println("Notebot tuning checks passed: " + checks);
    }
}
