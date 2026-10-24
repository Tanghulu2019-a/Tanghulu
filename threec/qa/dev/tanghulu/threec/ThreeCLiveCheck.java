package dev.tanghulu.threec;

import dev.tanghulu.addon.util.threec.ThreeCPlayerService;

/** Optional real HTTP smoke check; not part of the offline regression suite. */
public final class ThreeCLiveCheck {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass an explicit player name with -PplayerName=<name>");
        var player = ThreeCPlayerService.query(args[0]);
        System.out.println("Live Java HTTP check passed: " + player.name() + ", totalSeconds="
            + player.totalSeconds() + ", monthSeconds=" + player.monthSeconds());
    }
}
