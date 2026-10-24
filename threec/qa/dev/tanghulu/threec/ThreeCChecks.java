package dev.tanghulu.threec;

import dev.tanghulu.addon.util.threec.NearbyPlayerSelector;
import dev.tanghulu.addon.util.threec.NearbyPlayerSelector.Candidate;
import dev.tanghulu.addon.util.threec.ThreeCPlayerService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class ThreeCChecks {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void reject(String json) throws Exception {
        try { ThreeCPlayerService.parse(json, "Tester"); }
        catch (IOException expected) { checks++; return; }
        throw new AssertionError("Accepted invalid response: " + json);
    }
    public static void main(String[] args) throws Exception {
        var sample = ThreeCPlayerService.parse(Files.readString(Path.of(args[0], "threec/fixtures/player-example.json")), "EXAMPLEPLAYER");
        check(sample.totalSeconds() == 90061 && sample.monthSeconds() == 1234, "Synthetic fixture field units");
        check(!sample.isNewbie(24) && sample.isNewbie(100), "Configurable threshold");
        check(new ThreeCPlayerService.PlayerTime("Tester", 86399, null).isNewbie(24), "Below 24h");
        check(!new ThreeCPlayerService.PlayerTime("Tester", 86400, null).isNewbie(24), "Exactly 24h");
        check(!new ThreeCPlayerService.PlayerTime("Tester", 86401, null).isNewbie(24), "Above 24h");
        var zero = ThreeCPlayerService.parse("{\"code\":200,\"data\":{\"name\":\"Tester\",\"duration\":0}}", "tester");
        check(zero.isNewbie(24) && zero.monthSeconds() == null, "True zero and missing optional month");
        check(ThreeCPlayerService.formatTime(90061).startsWith("1 天 1 小时 1 分 1 秒"), "Duration rendering");
        check(ThreeCPlayerService.validateName(" ||Tester ").equals("||Tester"), "Server name prefix");
        try { ThreeCPlayerService.validateName("Tester&name=other"); throw new AssertionError("Unsafe name accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        reject("<html>Cloudflare</html>");
        reject("{\"code\":404,\"msg\":\"not found\"}");
        reject("{\"code\":200,\"data\":null}");
        for (String duration : List.of("null", "-1", "1.5", "\"0\"", "9223372036854775808")) {
            reject("{\"code\":200,\"data\":{\"name\":\"Tester\",\"duration\":" + duration + "}}");
        }
        reject("{\"code\":200,\"data\":{\"name\":\"Tester\"}}");
        reject("{\"code\":200,\"data\":{\"name\":\"Other\",\"duration\":1}}");
        UUID self = UUID.randomUUID();
        var me = new Candidate(self, "Me", 0, 0, 0, false);
        var boundary = candidate("Edge", 200, 0, 0, false);
        check(NearbyPlayerSelector.nearest(self, 0, 0, 0, List.of(me)) == null, "Exclude self");
        check(NearbyPlayerSelector.nearest(self, 0, 0, 0, List.of(boundary)) == boundary, "Include exact 200m");
        check(NearbyPlayerSelector.nearest(self, 0, 0, 0, List.of(candidate("Far", 200.01, 0, 0, false))) == null, "Exclude outside range");
        check(NearbyPlayerSelector.nearest(self, 0, 0, 0, List.of(candidate("Above", 0, 201, 0, false))) == null, "Use vertical distance");
        var near = candidate("Near", 3, 4, 0, false);
        check(NearbyPlayerSelector.nearest(self, 0, 0, 0, List.of(boundary, me, near, candidate("Spectator", 0, 0, 1, true))) == near, "Closest eligible player");
        check(NearbyPlayerSelector.nearest(self, 0, 0, 0, List.of(candidate("Broken", Double.NaN, 0, 0, false))) == null, "Nonfinite coordinates");
        var tieA = candidate("Alice", 5, 0, 0, false);
        var tieB = candidate("Bob", -5, 0, 0, false);
        check(NearbyPlayerSelector.nearest(self, 0, 0, 0, List.of(tieB, tieA)) == tieA, "Stable equal distance selection");
        check(NearbyPlayerSelector.nearest(self, 0, 0, 0, List.of()) == null, "No loaded candidates");
        var customEdge = candidate("CustomEdge", 0, 30, 40, false);
        var within = NearbyPlayerSelector.within(self, 0, 0, 0, 50,
            List.of(boundary, me, near, customEdge, candidate("Spectator", 1, 0, 0, true),
                candidate("Outside", 50.01, 0, 0, false), candidate("Broken", Double.NaN, 0, 0, false)));
        check(within.equals(List.of(near, customEdge)), "Automatic range: all eligible players, 3D boundary, exclusions, nearest first");
        check(NearbyPlayerSelector.within(self, 0, 0, 0, 4, List.of(near)).isEmpty(), "Changed automatic range");
        ThreeCQueueChecks.run();
        ThreeCNametagChecks.run();
        System.out.println("3C checks passed: " + checks);
    }
    private static Candidate candidate(String name, double x, double y, double z, boolean spectator) {
        return new Candidate(UUID.randomUUID(), name, x, y, z, spectator);
    }
}
