package dev.tanghulu.treechop;

import dev.tanghulu.addon.util.tree.TreePlan;
import dev.tanghulu.addon.util.tree.TreeSpecies;
import dev.tanghulu.addon.util.tree.TreeCycle;
import dev.tanghulu.addon.util.tree.PillarRoute;
import dev.tanghulu.addon.util.tree.TreeMovementProgress;
import dev.tanghulu.addon.util.tree.TreeDescent;
import dev.tanghulu.addon.util.tree.TreeApproach;
import dev.tanghulu.addon.util.tree.TreeActionLog;
import java.util.Set;
import static dev.tanghulu.addon.util.tree.TreeCycle.Collection.*;

public final class TreePlanChecks {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static TreePlan.Pos p(int x, int y, int z) { return new TreePlan.Pos(x, y, z); }
    private static TreePlan plan() { return new TreePlan(p(0, 64, 0), 8, 8, 48); }
    public static void main(String[] args) {
        var bounds = plan();
        check(bounds.contains(p(8, 64, 0)), "include cylinder boundary");
        check(!bounds.contains(p(8, 64, 1)), "exclude outside cylinder");
        check(!bounds.contains(p(0, 55, 0)), "exclude below range");
        check(!bounds.contains(p(0, 113, 0)), "exclude above range");
        check(bounds.contains(p(0, 56, 0)) && bounds.contains(p(0, 112, 0)), "include height endpoints");
        var hundred = new TreePlan(p(0, 64, 0), 100, 8, 48);
        check(hundred.contains(p(100, 64, 0)), "configured radius includes logs one hundred blocks away");
        check(!hundred.contains(p(101, 64, 0)), "configured radius remains bounded");
        var large = new TreePlan(p(0, 64, 0), 256, 8, 96);
        check(large.contains(p(-256, 64, 0)), "expanded maximum radius supports negative boundary");
        check(!large.contains(p(256, 64, 1)), "expanded maximum still respects circular boundary");

        var tree = plan();
        tree.addLog(p(1, 64, 0), TreeSpecies.OAK);
        tree.addLog(p(1, 65, 0), TreeSpecies.OAK);
        tree.addLog(p(2, 66, 1), TreeSpecies.OAK); // diagonal branch
        tree.addNaturalLeaves(p(2, 67, 1), TreeSpecies.OAK);
        check(tree.targets().size() == 3, "include trunk and diagonal branch");
        check(tree.targets().getFirst().equals(p(1, 64, 0)), "near targets first");
        var snapshot = tree.targets();
        tree.addLog(p(2, 67, 1), TreeSpecies.OAK);
        check(snapshot.size() == 3 && tree.targets().size() == 4, "immutable snapshot");

        var building = plan();
        building.addLog(p(0, 64, 0), TreeSpecies.OAK);
        building.addLog(p(0, 65, 0), TreeSpecies.OAK);
        check(building.targets().isEmpty(), "reject logs without natural foliage");
        building.addNaturalLeaves(p(0, 66, 0), TreeSpecies.BIRCH);
        check(building.targets().isEmpty(), "reject foliage from another species");

        var mixed = plan();
        mixed.addLog(p(0, 64, 0), TreeSpecies.OAK);
        mixed.addLog(p(0, 65, 0), TreeSpecies.OAK);
        mixed.addNaturalLeaves(p(0, 66, 0), TreeSpecies.OAK);
        mixed.addLog(p(1, 64, 0), TreeSpecies.BIRCH);
        mixed.addLog(p(1, 65, 0), TreeSpecies.BIRCH);
        mixed.addLog(p(7, 64, 0), TreeSpecies.OAK);
        check(Set.copyOf(mixed.targets()).equals(Set.of(p(0, 64, 0), p(0, 65, 0))),
            "do not absorb neighboring species or isolated logs");

        var edge = plan();
        edge.addLog(p(8, 64, 0), TreeSpecies.CHERRY);
        edge.addLog(p(8, 65, 0), TreeSpecies.CHERRY);
        edge.addLog(p(9, 65, 0), TreeSpecies.CHERRY);
        edge.addNaturalLeaves(p(9, 66, 0), TreeSpecies.CHERRY);
        check(edge.logCount() == 2 && edge.targets().size() == 2, "foliage may border scope; targets stay bounded");
        for (TreeSpecies species : TreeSpecies.values()) {
            var single = plan();
            single.addLog(p(0, 64, 0), species);
            single.addLog(p(0, 65, 0), species);
            single.addNaturalLeaves(p(1, 65, 0), species);
            check(single.targets().size() == 2, "supported species " + species);
        }
        check(new TreePlan(p(30_000_000, 64, -30_000_000), 48, 8, 96)
            .contains(p(30_000_048, 64, -30_000_000)), "world-border coordinates do not overflow");
        var separate = plan();
        for (int tx : new int[]{-4, 4}) {
            separate.addLog(p(tx, 64, 0), TreeSpecies.OAK);
            separate.addLog(p(tx, 65, 0), TreeSpecies.OAK);
            separate.addNaturalLeaves(p(tx, 66, 0), TreeSpecies.OAK);
        }
        check(separate.trees().size() == 2 && separate.targets().size() == 4, "separate trees remain separate jobs");
        var cycle = new TreeCycle(2, 200);
        check(cycle.observeDrops(0, true, 500) == WAIT && cycle.index() == 0, "cannot collect before cutting");
        cycle.returnToGround(); cycle.returned(false);
        check(cycle.phase() == TreeCycle.Phase.RETURN, "cannot collect before scaffolds recovered");
        cycle.returned(true);
        check(cycle.observeDrops(1, true, 100) == WAIT && cycle.index() == 0, "try collecting before configured deadline");
        check(cycle.observeDrops(0, false, 50) == WAIT, "full inventory cannot mark collection complete");
        check(cycle.observeDrops(0, true, 49) == WAIT, "observe configured time before confirming empty area");
        check(cycle.observeDrops(0, true, 1) == COMPLETE && cycle.index() == 1, "empty area completes at deadline");
        cycle.returnToGround(); cycle.resumeCut(true);
        check(cycle.index() == 1 && cycle.phase() == TreeCycle.Phase.CUT, "relocation resumes same tree");
        cycle.returnToGround(); cycle.returned(true);
        check(cycle.observeDrops(0, true, 40) == WAIT, "configured observation starts fresh for every tree");
        check(cycle.observeDrops(0, true, 160) == COMPLETE && cycle.phase() == TreeCycle.Phase.DONE, "last tree completes after collection");
        var skipped = new TreeCycle(3, 40);
        skipped.skip();
        check(skipped.index() == 1 && skipped.phase() == TreeCycle.Phase.CUT,
            "obstructed first tree skips directly to second without ending batch");
        check(skipped.observeDrops(0, true, 200) == WAIT && skipped.index() == 1,
            "skipped tree's collection cannot advance the new tree");
        skipped.returnToGround(); skipped.returned(false);
        skipped.skip();
        check(skipped.index() == 2 && skipped.phase() == TreeCycle.Phase.CUT,
            "failed recovery explicitly abandons second tree and continues third");
        boolean cannotResumeSkipped = false;
        try { skipped.resumeCut(true); } catch (IllegalStateException expected) { cannotResumeSkipped = true; }
        check(cannotResumeSkipped, "old relocation cannot resume after skip");
        skipped.returnToGround(); skipped.returned(true);
        check(skipped.observeDrops(0, true, 20) == WAIT, "next tree starts a fresh observation period after skip");
        check(skipped.observeDrops(0, true, 20) == COMPLETE && skipped.phase() == TreeCycle.Phase.DONE,
            "remaining accessible tree still completes normally");
        skipped.skip();
        check(skipped.index() == 3, "skip after final completion cannot overrun tree list");
        var allSkipped = new TreeCycle(2, 200);
        allSkipped.skip(); allSkipped.skip();
        check(allSkipped.phase() == TreeCycle.Phase.DONE && allSkipped.index() == 2,
            "finish only after all remaining trees have been considered");
        var timed = new TreeCycle(3, 40);
        timed.returnToGround(); timed.returned(true);
        check(timed.observeDrops(2, true, 35) == WAIT, "allow pickup attempts before two-second limit");
        check(timed.observeDrops(2, true, 5) == TIMED_OUT && timed.index() == 0,
            "uncollected logs time out at two seconds without being reported collected");
        check(timed.observeDrops(2, true, 5) == TIMED_OUT && timed.index() == 0,
            "timeout does not advance twice before controller skips tree");
        timed.skip(); timed.returnToGround(); timed.returned(true);
        check(timed.observeDrops(1, true, 35) == WAIT, "new tree has its own pickup budget");
        check(timed.observeDrops(0, true, 5) == COMPLETE && timed.index() == 2,
            "logs collected on last tick complete normally without an extra quiet wait");
        timed.returnToGround(); timed.returned(true);
        check(timed.observeDrops(1, false, 40) == TIMED_OUT,
            "lack of inventory space never creates an indefinite collection wait");
        timed.skip();
        check(timed.phase() == TreeCycle.Phase.DONE, "timed-out final tree can be skipped to finish batch");
        var customWait = new TreeCycle(1, 120);
        customWait.returnToGround(); customWait.returned(true);
        check(customWait.observeDrops(1, true, 119) == WAIT && customWait.observeDrops(1, true, 1) == TIMED_OUT,
            "custom pickup deadline is respected instead of hardcoding two seconds");
        var route = new PillarRoute<String>();
        route.confirm(p(2, 64, 2), "dirt"); route.confirm(p(2, 65, 2), "cobblestone");
        check(route.top().block().equals("cobblestone"), "recover last placed block first");
        check(route.snapshot().getFirst().pos().equals(p(2, 64, 2)), "first support retained for route base");
        route.landedAfterRemoval();
        check(route.size() == 1 && route.top().block().equals("dirt"), "landing advances one recovery step");
        boolean rejected = false;
        try { route.confirm(p(3, 65, 2), "stone"); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected && route.size() == 1, "reject sideways or untracked recovery routes");
        route.landedAfterRemoval();
        check(route.isEmpty() && route.top() == null, "natural ground is not part of recovery route");
        route.confirm(p(0, 64, 0), "dirt"); route.confirm(p(0, 65, 0), "stone");
        check(route.prune(step -> step.block().equals("stone")) == 1 && route.top().block().equals("dirt"),
            "missing support removed without dropping real supports");
        check(route.prune(step -> true) == 1 && route.isEmpty(), "fully removed pillar cannot block restart");
        var motion = new TreeMovementProgress();
        boolean earlyStall = false;
        for (int tick = 0; tick < 60; tick++) earlyStall |= motion.stuck(10, 20, 1);
        check(!earlyStall, "allow initial path calculation");
        check(motion.stuck(10, 20, 1), "stationary jumping detected within three seconds");
        motion.reset();
        boolean slowStall = false;
        for (int tick = 0; tick < 100; tick++) slowStall |= motion.stuck(tick * 0.05, 0, 1);
        check(!slowStall, "slow horizontal travel is progress");
        motion.reset(); motion.stuck(0, 0, 1);
        boolean jitterStuck = false;
        for (int tick = 0; tick < 65; tick++) jitterStuck |= motion.stuck(tick % 2 == 0 ? 0.05 : -0.05, 0, 1);
        check(jitterStuck, "tiny collision jitter is not progress");
        check(TreeMovementProgress.alignedForPillar(65, 64, 1), "pillar ascent requires matching feet height");
        check(!TreeMovementProgress.alignedForPillar(64, 64, 2), "server height correction cannot trigger more jumps");
        check(TreeMovementProgress.placementWindow(65.17, 64), "place during normal jump");
        check(TreeMovementProgress.placementWindow(65.01, 64), "apex or ceiling collision can still place");
        check(!TreeMovementProgress.placementWindow(64.99, 64), "do not place into player's body");
        var predicted = TreeDescent.classify(false, true, false, false);
        check(predicted == TreeDescent.BlockStatus.PREDICTED_AIR, "local block prediction is not foreign replacement");
        check(TreeDescent.decide(66, 67, true, true, predicted, TreeDescent.Landing.SUPPORTED)
            == TreeDescent.Action.WAIT_CONFIRMATION, "wait for server instead of false unsafe stop");
        check(TreeDescent.decide(66, 66, true, true, predicted, TreeDescent.Landing.SUPPORTED)
            == TreeDescent.Action.WAIT_CONFIRMATION, "local landing cannot skip server confirmation");
        check(TreeDescent.classify(true, false, true, false) == TreeDescent.BlockStatus.UNKNOWN,
            "server removal arriving before local state waits for world update");
        check(TreeDescent.decide(66, 67, true, true, TreeDescent.classify(true, false, false, false), TreeDescent.Landing.SUPPORTED)
            == TreeDescent.Action.MINE, "rejected prediction can retry owned block");
        check(TreeDescent.decide(66, 66.5, false, true, TreeDescent.BlockStatus.REMOVED, TreeDescent.Landing.SUPPORTED)
            == TreeDescent.Action.WAIT_LANDING, "do not mine next support while airborne");
        check(TreeDescent.decide(66, 66, true, true, TreeDescent.BlockStatus.REMOVED, TreeDescent.Landing.UNSUPPORTED)
            == TreeDescent.Action.COMPLETE, "actual confirmed landing completes one step");
        check(TreeDescent.decide(66, 67, true, false, TreeDescent.BlockStatus.REMOVED, TreeDescent.Landing.SUPPORTED)
            == TreeDescent.Action.CENTER, "correct edge catch after block removal");
        check(TreeDescent.decide(66, 67, true, true, TreeDescent.BlockStatus.OWNED, TreeDescent.Landing.WAIT_SYNC)
            == TreeDescent.Action.WAIT_SUPPORT, "pending lower support is not unsafe terrain");
        check(TreeDescent.decide(66, 67, true, true, TreeDescent.BlockStatus.OWNED, TreeDescent.Landing.HAZARD)
            == TreeDescent.Action.UNSUPPORTED, "real hazard still blocks removal");
        check(TreeDescent.decide(66, 67, true, true, TreeDescent.BlockStatus.REPLACED, TreeDescent.Landing.SUPPORTED)
            == TreeDescent.Action.REPLACED, "never mine replacement belonging to someone else");
        check(TreeDescent.decide(66, 65, true, true, TreeDescent.BlockStatus.OWNED, TreeDescent.Landing.SUPPORTED)
            == TreeDescent.Action.RECOVER, "standing below a recorded support invokes ground recovery");
        var approach = new TreeApproach();
        var stand = p(4, 64, 0);
        for (int tick = 1; tick <= 3; tick++) check(!approach.update(stand, 2.5, 64, 0.5, true, tick),
            "being within interaction reach cannot start chopping before arrival");
        check(!approach.update(stand, 4.05, 64, 0.5, true, 4), "entering goal block is not centered arrival");
        check(!approach.update(stand, 4.5, 64.4, 0.5, false, 5), "airborne arrival cannot start chopping");
        check(!approach.update(stand, 4.5, 65, 0.5, true, 6), "same horizontal location on wrong level is not arrival");
        check(!approach.update(stand, 4.5, 64, 0.5, true, 7), "first centered tick waits for settling");
        check(!approach.update(stand, 4.5, 64, 0.5, true, 7), "duplicate tick cannot count twice");
        check(!approach.update(stand, 4.5, 64, 0.5, true, 8), "second centered tick still waits");
        check(approach.update(stand, 4.5, 64, 0.5, true, 9), "three centered grounded ticks enable chopping");
        check(!approach.ready(stand, 4.8, 64, 0.5, true, 9), "rotation callback rejects movement after approval");
        check(!approach.ready(stand, 4.5, 64, 0.5, false, 9), "rotation callback rejects jumping after approval");
        check(!approach.ready(stand, 4.5, 64, 0.5, true, 10), "approval from previous tick is not reused");
        check(!approach.update(stand, 4.5, 64, 0.5, true, 12), "pause resets consecutive settling ticks");
        check(!approach.update(p(5, 64, 0), 5.5, 64, 0.5, true, 13), "changed work position requires new arrival");
        check(!approach.update(p(5, 65, 0), 5.5, 65, 0.5, true, 14), "new scaffold level requires landing and settling");
        approach.update(p(5, 65, 0), 5.5, 65, 0.5, true, 15);
        check(approach.update(p(5, 65, 0), 5.5, 65, 0.5, true, 16), "chopping resumes after settling on scaffold");
        approach.reset();
        check(!approach.ready(p(5, 65, 0), 5.5, 65, 0.5, true, 16), "new tree cannot inherit previous approval");
        TreeActionLog actions = new TreeActionLog();
        check(actions.changed("move", p(0, 64, 0)), "first navigation action is announced");
        check(!actions.changed("move", p(0, 64, 0)), "continued movement cannot flood chat");
        check(actions.changed("tool", "axe"), "tool selection uses its own announcement channel");
        check(actions.changed("mine", p(1, 64, 0)), "new block mining is announced");
        check(!actions.changed("tool", "axe") && !actions.changed("mine", p(1, 64, 0)),
            "alternating tool and mining checks cannot announce each other repeatedly");
        check(actions.changed("mine", p(1, 65, 0)), "each next block is announced");
        check(actions.changed("move", p(2, 64, 0)), "changed destination is announced");
        check(actions.changed("wait", "inventory full") && !actions.changed("wait", "inventory full"),
            "pause reason is announced once while unchanged");
        actions.reset();
        check(actions.changed("mine", p(1, 65, 0)), "new tree or resumed task resets announcement history");
        System.out.println("Auto tree plan checks passed: " + checks);
    }
}
