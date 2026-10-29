package dev.tanghulu.compat.smoke;

import dev.tanghulu.addon.util.tree.TreeMaterials;
import dev.tanghulu.addon.util.tree.PillarRoute;
import dev.tanghulu.addon.util.tree.TreeLoot;
import dev.tanghulu.addon.util.tree.TreeSpecies;
import dev.tanghulu.addon.util.tree.TreeApproach;
import dev.tanghulu.addon.util.tree.TreeScanner;
import dev.tanghulu.addon.modules.AutoTreeChop;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.lang.reflect.*;

/** Test real Minecraft materials and tools after registry bootstrap, without opening a window. */
public final class TreeRuntimeChecks {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void run() throws Exception {
        // PreLaunch bootstraps registries, but has not received a world's block tags yet.
        // Bind the three standard vanilla mining tags used by these real ItemStack fixtures.
        var bindTags = Arrays.stream(RegistryEntry.Reference.class.getDeclaredMethods())
            .filter(m -> Arrays.equals(m.getParameterTypes(), new Class<?>[]{Collection.class}))
            .findFirst().orElseThrow();
        bindTags.setAccessible(true);
        for (var fixture : Map.of(Blocks.OAK_LOG, BlockTags.AXE_MINEABLE,
            Blocks.COBBLESTONE, BlockTags.PICKAXE_MINEABLE, Blocks.DIRT, BlockTags.SHOVEL_MINEABLE).entrySet()) {
            var entry = fixture.getKey().getRegistryEntry();
            var tags = new HashSet<>(entry.streamTags().toList()); tags.add(fixture.getValue());
            bindTags.invoke(entry, tags);
        }
        var air = EmptyBlockView.INSTANCE;
        check(TreeMaterials.allowed(new ItemStack(Items.DIRT), List.of(), air, BlockPos.ORIGIN), "ordinary dirt is usable");
        check(!TreeMaterials.allowed(new ItemStack(Items.DIRT), List.of(Blocks.DIRT), air, BlockPos.ORIGIN), "blacklist enforced");
        check(!TreeMaterials.allowed(new ItemStack(Items.SAND), List.of(), air, BlockPos.ORIGIN), "gravity block rejected");
        check(!TreeMaterials.allowed(new ItemStack(Items.CHEST), List.of(), air, BlockPos.ORIGIN), "container rejected");
        check(!TreeMaterials.allowed(new ItemStack(Items.OAK_LEAVES), List.of(), air, BlockPos.ORIGIN), "leaves rejected");
        check(!TreeMaterials.allowed(new ItemStack(Items.STONE_SLAB), List.of(), air, BlockPos.ORIGIN), "partial support rejected");
        check(!TreeMaterials.allowed(new ItemStack(Items.DIAMOND_AXE), List.of(), air, BlockPos.ORIGIN), "tool is not scaffold material");
        var axe = new ItemStack(Items.DIAMOND_AXE);
        var pick = new ItemStack(Items.DIAMOND_PICKAXE);
        var shovel = new ItemStack(Items.DIAMOND_SHOVEL);
        check(TreeMaterials.toolScore(axe, Blocks.OAK_LOG.getDefaultState(), 0)
            > TreeMaterials.toolScore(pick, Blocks.OAK_LOG.getDefaultState(), 0), "axe selected for wood");
        check(TreeMaterials.toolScore(pick, Blocks.COBBLESTONE.getDefaultState(), 0)
            > TreeMaterials.toolScore(axe, Blocks.COBBLESTONE.getDefaultState(), 0), "pick selected for stone recovery");
        check(TreeMaterials.toolScore(shovel, Blocks.DIRT.getDefaultState(), 0)
            > TreeMaterials.toolScore(axe, Blocks.DIRT.getDefaultState(), 0), "shovel selected for dirt recovery");
        check(TreeMaterials.toolScore(axe, Blocks.OAK_LOG.getDefaultState(), 4)
            > TreeMaterials.toolScore(axe, Blocks.OAK_LOG.getDefaultState(), 0), "efficiency included");
        axe.setDamage(axe.getMaxDamage() - 1);
        check(TreeMaterials.toolScore(axe, Blocks.OAK_LOG.getDefaultState(), 5) < 0, "last durability protected");
        confirmations();
        logOnlyLoot();
        largeScanProgress();
        check(Arrays.stream(AutoTreeChop.class.getDeclaredFields()).noneMatch(f -> f.getName().equals("waitLeaves")),
            "leaf decay waiting option removed");
        System.out.println("TREE_RUNTIME_CHECKS PASS materialsAndTools=" + checks);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field f = owner.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static void largeScanProgress() throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Object unsafe = field(unsafeType, "theUnsafe").get(null);
        TreeScanner scan = (TreeScanner) unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, TreeScanner.class);
        long total = 525L * 525 * 111; // Maximum radius plus foliage border, at maximum scan height.
        field(TreeScanner.class, "total").setLong(scan, total);
        field(TreeScanner.class, "sampled").setLong(scan, 0);
        check(scan.percent() == 0, "expanded scan starts at zero percent");
        field(TreeScanner.class, "sampled").setLong(scan, total / 2 + 1);
        check(scan.percent() == 50, "large scan progress does not overflow at halfway");
        field(TreeScanner.class, "sampled").setLong(scan, total - 1);
        check(scan.percent() == 99, "large scan is not complete one sample early");
        field(TreeScanner.class, "sampled").setLong(scan, total);
        check(scan.percent() == 100, "maximum-radius scan reaches exactly one hundred percent");
    }
    @SuppressWarnings("unchecked")
    private static void confirmations() throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Object unsafe = field(unsafeType, "theUnsafe").get(null);
        AutoTreeChop worker = (AutoTreeChop) unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, AutoTreeChop.class);
        var route = new PillarRoute<>();
        var cut = new HashSet<BlockPos>(); var removed = new HashSet<BlockPos>();
        var queue = new ConcurrentLinkedQueue<>();
        for (var entry : Map.<String, Object>of("route", route, "cut", cut, "removedSupports", removed,
            "changes", queue).entrySet()) field(AutoTreeChop.class, entry.getKey()).set(worker, entry.getValue());
        field(AutoTreeChop.class, "serverSupports").set(worker, new HashMap<>());
        BlockPos support = new BlockPos(0, 64, 0), log = new BlockPos(1, 64, 0);
        field(AutoTreeChop.class, "pendingPlace").set(worker, support);
        field(AutoTreeChop.class, "pendingBlock").set(worker, Blocks.DIRT);
        field(AutoTreeChop.class, "expectedLogs").set(worker, Map.of(log, Blocks.OAK_LOG));
        field(AutoTreeChop.class, "watched").set(worker, Set.of(support, log));
        var record = AutoTreeChop.class.getDeclaredMethod("record", BlockPos.class, net.minecraft.block.BlockState.class);
        var apply = AutoTreeChop.class.getDeclaredMethod("confirmations"); record.setAccessible(true); apply.setAccessible(true);
        check(route.isEmpty(), "unconfirmed placement not owned");
        record.invoke(worker, support, Blocks.STONE.getDefaultState()); apply.invoke(worker);
        check(route.isEmpty(), "unexpected block update not claimed as scaffold");
        record.invoke(worker, support, Blocks.DIRT.getDefaultState()); apply.invoke(worker);
        check(route.size() == 1 && field(AutoTreeChop.class, "pendingPlace").get(worker) == null, "server confirms owned support");
        record.invoke(worker, support, Blocks.DIRT.getDefaultState()); apply.invoke(worker);
        check(route.size() == 1, "duplicate update cannot duplicate route step");
        record.invoke(worker, log, Blocks.AIR.getDefaultState()); apply.invoke(worker);
        check(cut.contains(log), "server confirms log removal");
        record.invoke(worker, log, Blocks.OAK_LOG.getDefaultState()); apply.invoke(worker);
        check(!cut.contains(log), "server rejection restores outstanding log");
        record.invoke(worker, support, Blocks.AIR.getDefaultState()); apply.invoke(worker);
        check(removed.contains(support) && route.size() == 1, "support removal still waits for landing before route advances");

        // Abandoning an unreachable return must not let the old route block the next tree.
        var abandoned = new HashSet<BlockPos>();
        var acks = new ConcurrentLinkedQueue<Integer>(); acks.add(42);
        field(AutoTreeChop.class, "abandonedSupports").set(worker, abandoned);
        field(AutoTreeChop.class, "actionAcks").set(worker, acks);
        field(AutoTreeChop.class, "approach").set(worker, new TreeApproach());
        field(AutoTreeChop.class, "pendingPlace").set(worker, support.up());
        field(AutoTreeChop.class, "pendingBlock").set(worker, Blocks.DIRT);
        for (String name : List.of("column", "jumpTarget", "walkTarget", "centeringTarget", "descentTarget"))
            field(AutoTreeChop.class, name).set(worker, support);
        record.invoke(worker, log, Blocks.AIR.getDefaultState());
        var abandon = AutoTreeChop.class.getDeclaredMethod("abandonRoute"); abandon.setAccessible(true);
        int generation = field(AutoTreeChop.class, "generation").getInt(worker);
        check((int) abandon.invoke(worker) == 2, "both confirmed and pending supports are abandoned");
        check(route.isEmpty() && abandoned.containsAll(Set.of(support, support.up())),
            "skipped route is detached and retained as excluded stands without touching world blocks");
        check(queue.isEmpty() && acks.isEmpty() && ((Set<?>) field(AutoTreeChop.class, "watched").get(worker)).isEmpty(),
            "old block updates and acknowledgements cannot affect next tree");
        check(field(AutoTreeChop.class, "generation").getInt(worker) > generation,
            "skip invalidates queued mining and placement rotations");
        check(field(AutoTreeChop.class, "pendingPlace").get(worker) == null
            && field(AutoTreeChop.class, "column").get(worker) == null
            && field(AutoTreeChop.class, "centeringTarget").get(worker) == null,
            "old placement and centering cannot pin player to abandoned pillar");
        check(removed.isEmpty() && cut.isEmpty()
            && ((Map<?, ?>) field(AutoTreeChop.class, "serverSupports").get(worker)).isEmpty(),
            "next tree receives no old recovery or cutting state");
    }

    private static void logOnlyLoot() {
        TreeLoot loot = new TreeLoot();
        Vec3d inside = new Vec3d(0.5, 64, 0.5), outside = new Vec3d(30, 64, 30);
        List<BlockPos> tree = List.of(new BlockPos(0, 64, 0), new BlockPos(0, 65, 0));
        UUID tracked = UUID.randomUUID();
        loot.begin(tree, List.of(Blocks.BIRCH_LOG));
        check(loot.accepts(Items.BIRCH_LOG, tracked, inside), "collect current birch logs");
        for (var item : List.of(Items.OAK_LOG, Items.BIRCH_SAPLING, Items.BIRCH_LEAVES, Items.STICK,
            Items.APPLE, Items.DIRT, Items.COBBLESTONE, Items.WHITE_WOOL)) {
            check(!loot.accepts(item, UUID.randomUUID(), inside), "ignore unrelated item near tree or scaffold: " + item);
        }
        check(loot.accepts(Items.BIRCH_LOG, tracked, outside), "track current logs drifting out of original box");
        check(!loot.accepts(Items.BIRCH_LOG, UUID.randomUUID(), outside), "do not target distant unrelated log drops");
        check(!loot.accepts(Items.APPLE, tracked, inside), "tracked UUID cannot bypass log type filter");
        loot.begin(tree, List.of(Blocks.OAK_LOG));
        check(!loot.accepts(Items.BIRCH_LOG, tracked, inside), "new tree clears previous species");
        check(!loot.accepts(Items.OAK_LOG, tracked, outside), "new tree clears tracked entity identities");
        for (TreeSpecies type : TreeSpecies.values()) {
            Block block = Registries.BLOCK.get(Identifier.ofVanilla(type.logId()));
            loot.begin(tree, List.of(block));
            check(loot.accepts(block.asItem(), UUID.randomUUID(), inside), "collect matching logs for " + type);
            check(!loot.accepts(Items.STICK, UUID.randomUUID(), inside), "ignore sticks for " + type);
        }
    }
}
