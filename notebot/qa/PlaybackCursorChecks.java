package dev.tanghulu.notebot;

import dev.tanghulu.addon.util.notebot.PlaybackCursor;
import java.util.List;

public final class PlaybackCursorChecks {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var cursor = new PlaybackCursor<String>();
        cursor.prepare(0, List.of("A", "B", "C"), true);
        check(cursor.peek().equals("A"), "First chord note is first");
        cursor.prepare(0, List.of("A", "B", "C"), true);
        check(cursor.peek().equals("A"), "A delayed rotation must not consume its note");
        cursor.complete();
        cursor.prepare(0, List.of("A", "B", "C"), true);
        check(cursor.peek().equals("B"), "Repeating a held song tick must not reload the chord");
        try {
            cursor.prepare(1, List.of("D"), true);
            throw new AssertionError("Must not advance before the chord is actually sent");
        } catch (IllegalStateException expected) { checks++; }
        cursor.complete();
        check(!cursor.isEmpty() && cursor.peek().equals("C"), "Preserve the third chord note across game ticks");
        cursor.complete();
        check(cursor.isEmpty(), "Advance after the final chord note completes");
        cursor.prepare(1, List.of("A"), true);
        check(cursor.peek().equals("A"), "Repeated notes in later song ticks must still play");
        cursor.complete();
        cursor.prepare(2, List.of(), true);
        check(cursor.isEmpty(), "Silent song ticks do not create attacks");
        cursor.prepare(3, List.of("A", "A", "B"), true);
        cursor.complete();
        check(cursor.peek().equals("B"), "Duplicate simultaneous notes use one tap per note block");
        cursor.reset();
        check(cursor.isEmpty(), "Stop/load/disconnect clears pending notes");
        cursor.prepare(0, List.of("A", "B"), false);
        cursor.complete();
        check(cursor.isEmpty(), "The disabled polyphonic setting selects one note");
        cursor.reset();
        cursor.prepare(0, List.of("C"), true);
        check(cursor.peek().equals("C"), "A new song can restart at the same song tick");
        System.out.println("Notebot playback queue checks passed: " + checks);
    }
}
