package dev.tanghulu.seedsearch;

import dev.tanghulu.addon.util.seeds.OreSearchProgress;
import java.util.List;

public final class OreSearchChecks {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var progress = new OreSearchProgress();
        check(!progress.running() && progress.percent() == 0, "Idle is not a completed search");
        long first = progress.begin(List.of(1L, 2L, 2L, 3L));
        check(progress.running() && progress.total() == 3, "Snapshot deduplicates chunk coordinates");
        check(progress.complete(first, 3L, 5), "Out of order result accepted");
        check(progress.completed() == 1 && progress.percent() == 33 && progress.found() == 5, "Progress reflects actual completed work");
        check(!progress.complete(first, 3L, 5), "Duplicate callback cannot inflate progress");
        check(!progress.complete(first, 99L, 5), "Newly loaded chunk cannot change the fixed batch");
        check(progress.running(), "A partial batch is not complete");
        progress.cancel();
        check(!progress.running() && progress.state() == OreSearchProgress.State.Cancelled, "Explicit cancellation is not success");
        check(!progress.complete(first, 1L, 2), "Cancelled result ignored");
        long second = progress.begin(List.of(1L, 2L));
        check(progress.found() == 0 && progress.completed() == 0, "Restart resets counts");
        check(!progress.complete(first, 1L, 100), "Old result for same chunk cannot finish a new search");
        check(!progress.fail(first), "Old error cannot fail a new search");
        check(progress.complete(second, 2L, 0), "Empty chunk still counts as completed");
        check(progress.running() && progress.percent() == 50, "Remain running until all callbacks return");
        check(progress.complete(second, 1L, 7), "Last result accepted");
        check(progress.state() == OreSearchProgress.State.Complete && progress.percent() == 100 && progress.found() == 7,
            "Completion and found count describe the entire batch");
        check(!progress.fail(second) && !progress.complete(second, 1L, 7), "Terminal completion announced once");
        long failed = progress.begin(List.of(4L, 5L));
        progress.complete(failed, 4L, 2);
        check(progress.fail(failed) && progress.state() == OreSearchProgress.State.Failed, "Failure is distinct from completion");
        check(!progress.complete(failed, 5L, 3) && progress.percent() == 50, "Failed batch must not claim 100 percent");
        progress.begin(List.of());
        check(progress.state() == OreSearchProgress.State.Complete && progress.percent() == 100, "Empty batch ends without hanging");
        System.out.println("Ore search lifecycle checks passed: " + checks);
    }
}
