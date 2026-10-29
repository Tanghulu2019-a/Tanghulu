package dev.tanghulu.addon.modules;

import dev.tanghulu.addon.BaseModule;
import dev.tanghulu.addon.util.tree.*;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.entity.MovementType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class AutoTreeChop extends Module {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Tanghulu/AutoTreeChop");
    private final SettingGroup sgArea = settings.createGroup("砍伐范围");
    private final Setting<Integer> radius = sgArea.add(new IntSetting.Builder().name("水平半径")
        .description("以开始位置为中心，仅处理已加载的树。单位：格。")
        .defaultValue(40).range(4, 256).sliderRange(4, 256).build());
    private final Setting<Integer> height = sgArea.add(new IntSetting.Builder().name("向上高度")
        .description("树木扫描高度，向下固定 8 格。")
        .defaultValue(48).range(8, 96).sliderRange(8, 96).build());
    private final SettingGroup sgWork = settings.createGroup("搭高与工具");
    private final Setting<Boolean> autoPillar = sgWork.add(new BoolSetting.Builder().name("自动搭高")
        .description("够不到高处原木时垂直搭高，之后沿原柱逐层挖回地面。")
        .defaultValue(true).build());
    private final Setting<List<Block>> blacklist = sgWork.add(new BlockListSetting.Builder().name("垫脚方块黑名单")
        .description("禁止消耗的垫脚方块。仅使用可回收的完整实心方块，排除重力方块、树叶和方块实体。")
        .defaultValue(Blocks.DIAMOND_BLOCK, Blocks.NETHERITE_BLOCK, Blocks.EMERALD_BLOCK, Blocks.GOLD_BLOCK,
            Blocks.IRON_BLOCK, Blocks.LAPIS_BLOCK, Blocks.REDSTONE_BLOCK, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN,
            Blocks.TNT, Blocks.MAGMA_BLOCK, Blocks.SLIME_BLOCK, Blocks.HONEY_BLOCK, Blocks.ICE,
            Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.OAK_LOG, Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG,
            Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.MANGROVE_LOG,
            Blocks.CHERRY_LOG, Blocks.PALE_OAK_LOG).build());
    private final Setting<Integer> maxPillar = sgWork.add(new IntSetting.Builder().name("最大搭高")
        .description("单根垫脚柱的最大高度，单位：格。")
        .defaultValue(48).range(1, 96).sliderRange(1, 96).build());
    private final Setting<Boolean> autoTool = sgWork.add(new BoolSetting.Builder().name("自动工具")
        .description("从背包选择最快的合适工具；砍木用斧、回收石头用镐、回收泥土用铲，避免用坏最后一点耐久。")
        .defaultValue(true).build());
    private final Setting<Integer> workSlot = sgWork.add(new IntSetting.Builder().name("工具与方块工作栏")
        .description("工具或垫脚方块不在快捷栏时，与此栏物品交换。1～9。")
        .defaultValue(9).range(1, 9).sliderRange(1, 9).build());
    private final SettingGroup sgLoot = settings.createGroup("逐棵拾取");
    private final Setting<Integer> settleSeconds = sgLoot.add(new IntSetting.Builder().name("掉落等待秒数")
        .description("返回后拾取原木的最长时间。到时仍未捡完则跳过本棵；背包满或路径不可达时直接跳过。")
        .defaultValue(2).range(2, 60).sliderRange(2, 60).build());
    private final SettingGroup sgTrees = settings.createGroup("树种（可多选）");
    private final Map<TreeSpecies, Setting<Boolean>> species = new EnumMap<>(TreeSpecies.class);
    private final Map<Block, TreeSpecies> logTypes = new HashMap<>(), leafTypes = new HashMap<>();
    private final PillarRoute<Block> route = new PillarRoute<>();
    private final Set<BlockPos> cut = new HashSet<>(), removedSupports = new HashSet<>();
    private final Map<BlockPos, BlockState> serverSupports = new HashMap<>();
    private final TreeLoot loot = new TreeLoot();
    private final TreeActionLog actions = new TreeActionLog();
    private final ConcurrentLinkedQueue<Change> changes = new ConcurrentLinkedQueue<>();
    private volatile Set<BlockPos> watched = Set.of();
    private record Change(BlockPos pos, BlockState state) {}
    private List<List<BlockPos>> trees = List.of();
    private final Set<BlockPos> failedStands = new HashSet<>();
    private final Set<BlockPos> abandonedSupports = new HashSet<>();
    private final TreeMovementProgress movement = new TreeMovementProgress();
    private final TreeApproach approach = new TreeApproach();
    private Map<BlockPos, Block> expectedLogs = Map.of();
    private ClientWorld taskWorld;
    private BlockPos column, walkTarget, jumpTarget, pendingPlace;
    private Block pendingBlock;
    private TreeScanner scanner;
    private TreeCycle cycle;
    private BaritoneTreeDriver driver;
    private boolean scanning, running, stopping, relocating, skipping;
    private String stopReason = "已结束", status = "等待开始";
    private WLabel statusLabel;
    private WButton controlButton;
    private int ticks, progressTicks, lastRemaining, confirmTicks, pendingTicks, jumpTicks, jumpFailures;
    private int walkTicks, walkIdle, originalSlot = -1, generation;
    private int returnTicks, returnSteps;
    private int skippedTrees;
    private boolean restartAfterReturn;
    private BlockPos centeringTarget;
    private volatile BlockPos descentTarget;
    private volatile int descentSequence = -1;
    private final ConcurrentLinkedQueue<Integer> actionAcks = new ConcurrentLinkedQueue<>();
    private int acknowledgedSequence = -1, ackTick = -1, syncWaitTicks;

    public AutoTreeChop() {
        super(BaseModule.CATEGORY, "自动砍树", "逐棵砍伐、搭高并原路回收、只捡对应原木、自动选工具。需要 Meteor Baritone。", "auto-tree-chop");
        for (TreeSpecies tree : TreeSpecies.values()) species.put(tree, sgTrees.add(new BoolSetting.Builder()
            .name(tree.title).description("砍伐此树种，树种与范围在下次开始生效。")
            .defaultValue(true).build()));
    }

    @Override public WWidget getWidget(GuiTheme theme) {
        var list = theme.verticalList();
        list.add(theme.label("到位砍伐 → 回收垫脚柱 → 只捡对应原木 → 下一棵"));
        list.add(theme.label("黑名单限制垫脚材料；原木捡不完自动跳过。请在树林中使用。"));
        controlButton = list.add(theme.button(running || scanning ? "结束" : "开始")).widget();
        controlButton.action = this::control;
        statusLabel = list.add(theme.label(status)).widget();
        return list;
    }
    @Override public void onActivate() { setStatus(route.isEmpty() ? "等待开始" : "点击开始将先回收上次的垫脚柱"); }
    @Override public void onDeactivate() { halt("模块已关闭", true); }

    private void control() {
        if (stopping && running) halt("已结束，回收已暂停；下次开始时继续", true);
        else if (running || scanning) requestStop("已结束");
        else start();
    }

    private void start() {
        if (running || scanning) { warning("正在运行，请先结束本次任务。"); return; }
        reconcileRoute();
        if (!route.isEmpty()) { resumeReturn(); return; }
        if (mc.player == null || mc.world == null || mc.player.isSpectator() || mc.player.isGliding()) {
            error("请先进入世界并站稳，旁观或鞘翅飞行时无法开始。"); return;
        }
        if (species.values().stream().noneMatch(Setting::get)) { error("请至少选择一种树。"); return; }
        if (!BaritoneUtils.IS_AVAILABLE) { error("需要与 Minecraft 1.21.11 匹配的 Meteor Baritone。"); return; }
        SeedMine seed = Modules.get().get(SeedMine.class);
        if (seed != null && seed.baritone()) { error("请先关闭种子矿透的 Baritone 同步及自动挖掘。"); return; }
        try { driver = new BaritoneTreeDriver(); }
        catch (RuntimeException | LinkageError e) { error("无法开始：%s", e.getMessage()); return; }
        if (!isActive()) toggle();
        taskWorld = mc.world;
        originalSlot = mc.player.getInventory().getSelectedSlot();
        logTypes.clear(); leafTypes.clear();
        for (TreeSpecies tree : TreeSpecies.values()) if (species.get(tree).get()) {
            logTypes.put(Registries.BLOCK.get(Identifier.ofVanilla(tree.logId())), tree);
            leafTypes.put(Registries.BLOCK.get(Identifier.ofVanilla(tree.leavesId())), tree);
        }
        scanner = new TreeScanner(taskWorld, mc.player.getBlockPos(), radius.get(), height.get(), logTypes, leafTypes);
        ticks = skippedTrees = 0; generation++; stopping = relocating = skipping = restartAfterReturn = false;
        actions.reset(); abandonedSupports.clear();
        actionAcks.clear(); acknowledgedSequence = ackTick = descentSequence = -1; descentTarget = null;
        changes.clear(); scanning = true;
        setStatus("正在扫描周围树木…");
        info("开始：扫描周围所选树种，水平半径 %d 格，向上 %d 格。", radius.get(), height.get());
    }

    private static TreePlan.Pos pos(BlockPos p) { return new TreePlan.Pos(p.getX(), p.getY(), p.getZ()); }
    private static BlockPos block(TreePlan.Pos p) { return new BlockPos(p.x(), p.y(), p.z()); }

    @EventHandler private void receive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerActionResponseS2CPacket ack && descentTarget != null) actionAcks.add(ack.sequence());
        if (watched.isEmpty()) return;
        if (event.packet instanceof BlockUpdateS2CPacket update) record(update.getPos(), update.getState());
        else if (event.packet instanceof ChunkDeltaUpdateS2CPacket update) update.visitUpdates(this::record);
    }
    @EventHandler private void send(PacketEvent.Send event) {
        if (descentTarget != null && event.packet instanceof PlayerActionC2SPacket action
            && action.getPos().equals(descentTarget)
            && (action.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK
                || (mc.player != null && mc.player.getAbilities().creativeMode
                    && action.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK))) {
            descentSequence = action.getSequence();
        }
    }
    private void record(BlockPos pos, BlockState state) {
        if (watched.contains(pos)) changes.add(new Change(pos.toImmutable(), state));
    }
    private void watch() {
        Set<BlockPos> positions = new HashSet<>(expectedLogs.keySet());
        for (var step : route.snapshot()) positions.add(block(step.pos()));
        if (pendingPlace != null) positions.add(pendingPlace);
        watched = Set.copyOf(positions);
    }
    private void confirmations() {
        for (Change change; (change = changes.poll()) != null;) {
            Block expected = expectedLogs.get(change.pos);
            BlockPos changedPos = change.pos;
            boolean scaffold = change.pos.equals(pendingPlace)
                || route.snapshot().stream().anyMatch(step -> changedPos.equals(block(step.pos())));
            if (expected != null && !scaffold) {
                if (change.state.isOf(expected)) cut.remove(change.pos); else cut.add(change.pos);
            }
            if (change.pos.equals(pendingPlace) && change.state.isOf(pendingBlock)) {
                route.confirm(pos(pendingPlace), pendingBlock);
                serverSupports.put(pendingPlace, change.state);
                pendingPlace = null; pendingBlock = null; jumpTarget = null; pendingTicks = jumpFailures = 0;
                watch();
            }
            for (var step : route.snapshot()) if (change.pos.equals(block(step.pos()))) {
                serverSupports.put(change.pos, change.state);
                if (change.state.isAir()) removedSupports.add(change.pos);
                else if (change.state.isOf(step.block())) removedSupports.remove(change.pos);
            }
        }
    }

    @EventHandler private void tick(TickEvent.Pre event) {
        if (!scanning && !running) return;
        if (mc.player == null || mc.world != taskWorld) { halt("世界或维度变化，已停止", false); route.clear(); return; }
        if (mc.player.isDead() || mc.player.isSpectator() || mc.player.isGliding()) { halt("玩家状态变化，已停止", true); return; }
        ticks++;
        centeringTarget = null;
        try {
            confirmations();
            for (Integer ack; (ack = actionAcks.poll()) != null;) {
                if (ack > acknowledgedSequence) { acknowledgedSequence = ack; ackTick = ticks; }
            }
            if (scanning) { scanSlice(); return; }
            if (ticks % 20 == 0 && expectedLogs.keySet().stream().anyMatch(p -> !taskWorld.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4))) {
                halt("目标区块已卸载，已停止", true); return;
            }
            if (cycle.phase() != TreeCycle.Phase.RETURN && !route.isEmpty() && column != null && horizontal(mc.player.getBlockPos(), column) > 0) {
                skipTree("已离开原垫脚柱", false); return;
            }
            if (driver.interrupted()) { halt("Baritone 被其他任务接管，已停止", true); return; }
            if (mc.currentScreen instanceof HandledScreen<?>) {
                // Screens pause interactions; navigation is cancelled before the pause.
                cancelWalk(); waitFor("界面打开，暂停操作"); return;
            }
            if (mc.player.isUsingItem()) { cancelWalk(); waitFor("正在使用物品，暂停操作"); return; }
            if (ticks % 5 == 0 && cycle.phase() != TreeCycle.Phase.COLLECT) loot.find(taskWorld.getEntities());
            if (pendingPlace != null) {
                if (column != null) centerColumn();
                if (++pendingTicks > 80) skipTree("垫脚方块未获服务器确认", false);
                return;
            }
            switch (cycle.phase()) {
                case CUT -> cutTree();
                case RETURN -> returnDown();
                case COLLECT -> collect();
                case DONE -> finishTask();
            }
        } catch (RuntimeException | LinkageError e) {
            halt("运行失败：" + e.getMessage(), true);
            error("自动砍树：%s", e.getMessage());
        }
    }

    private void scanSlice() {
        if (scanner.advance()) beginWork();
        else if (ticks % 5 == 0) setStatus("正在扫描 " + scanner.percent() + "%");
    }

    private void beginWork() {
        trees = scanner.trees();
        scanning = false; scanner = null;
        if (trees.isEmpty()) { halt("范围内没有所选树种的可砍伐树木", false); return; }
        driver.begin();
        cycle = new TreeCycle(trees.size(), settleSeconds.get() * 20);
        info("找到 %d 组树木，开始逐棵砍伐、返回和拾取。", trees.size());
        running = true; prepareTree();
    }

    private void prepareTree() {
        List<BlockPos> tree = trees.get(cycle.index());
        Map<BlockPos, Block> expected = new HashMap<>();
        cut.clear(); removedSupports.clear(); actions.reset();
        serverSupports.clear(); descentTarget = null; descentSequence = -1; syncWaitTicks = 0;
        for (BlockPos p : tree) {
            if (!taskWorld.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) throw new IllegalStateException("树木区块已卸载，请返回树林后重试。");
            TreeSpecies type = logTypes.get(taskWorld.getBlockState(p).getBlock());
            if (type != null) {
                expected.put(p, taskWorld.getBlockState(p).getBlock());
            }
        }
        expectedLogs = Map.copyOf(expected);
        loot.begin(tree, expected.values());
        failedStands.clear(); movement.reset(); approach.reset(); jumpFailures = 0;
        column = null; jumpTarget = null; relocating = skipping = false;
        confirmTicks = progressTicks = returnTicks = 0; lastRemaining = expectedLogs.size();
        watch(); setStatus("正在砍第 " + (cycle.index() + 1) + " / " + trees.size() + " 棵");
        info("处理第 %d / %d 棵树，共 %d 个原木；先走到树旁。", cycle.index() + 1, trees.size(), expected.size());
    }

    private List<BlockPos> remaining() {
        return expectedLogs.keySet().stream().filter(p -> !cut.contains(p)).toList();
    }

    private void cutTree() {
        List<BlockPos> left = remaining();
        if (left.size() < lastRemaining) progressTicks = 0;
        lastRemaining = left.size();
        if (++progressTicks > 2400) { skipTree("两分钟无砍伐进展", true); return; }
        if (left.isEmpty()) {
            cancelWalk(); cancelBreaking();
            if (++confirmTicks >= 40) cycle.returnToGround();
            waitFor("正在确认本棵砍伐结果…"); return;
        }
        confirmTicks = 0;
        if (jumpTarget != null) { ascend(); return; }
        // Pick and reach a work position before considering any reachable wood or foliage.
        if (!arriveForCut(left)) return;
        for (BlockPos p : left) if (reachable(p)) {
            cancelWalk(); mine(p); setStatus("砍第 " + (cycle.index() + 1) + " 棵，剩余原木 " + left.size()); return;
        }
        for (BlockPos p : left) {
            Vec3d target = Vec3d.ofCenter(p);
            if (mc.player.getEyePos().squaredDistanceTo(target) > 20) continue;
            var hit = taskWorld.raycast(new RaycastContext(mc.player.getEyePos(), target, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() == HitResult.Type.BLOCK && leafTypes.containsKey(taskWorld.getBlockState(hit.getBlockPos()).getBlock())
                && reachable(hit.getBlockPos())) { cancelWalk(); mine(hit.getBlockPos()); return; }
        }
        if (!route.isEmpty()) {
            BlockPos target = left.stream().min(Comparator.comparingDouble(p -> p.getSquaredDistance(mc.player.getEntityPos()))).orElseThrow();
            if (target.getY() >= mc.player.getY() && horizontal(target, column) <= 9) {
                beginAscent();
            } else { relocating = true; cycle.returnToGround(); }
            return;
        }
        beginAscent();
    }

    private boolean arriveForCut(List<BlockPos> left) {
        if (column == null) {
            approach.reset(); column = chooseStand(left);
        }
        if (column == null) { skipTree("本棵没有可到达的树旁位置", true); return false; }
        if (route.isEmpty()) {
            setStatus("正在走到第 " + (cycle.index() + 1) + " 棵树旁…");
            if (!walk(column)) { approach.reset(); cancelBreaking(); return false; }
        }
        boolean centered = centerColumn();
        if (!centered) approach.reset();
        if (!centered || !approach.update(pos(column.up(route.size())), mc.player.getX(), mc.player.getY(),
            mc.player.getZ(), mc.player.isOnGround(), ticks)) {
            cancelBreaking(); waitFor("正在对齐砍伐位置并等待站稳…"); return false;
        }
        return true;
    }

    private boolean cutPositionReady() {
        return column != null && jumpTarget == null && pendingPlace == null
            && approach.ready(pos(column.up(route.size())), mc.player.getX(), mc.player.getY(),
                mc.player.getZ(), mc.player.isOnGround(), ticks);
    }

    private boolean reachable(BlockPos p) {
        if (taskWorld.getBlockState(p).isAir()) return false;
        Vec3d eye = mc.player.getEyePos(), target = Vec3d.ofCenter(p);
        double reach = Math.min(4.5, mc.player.getBlockInteractionRange()) - 0.15;
        if (eye.squaredDistanceTo(target) > reach * reach) return false;
        var hit = taskWorld.raycast(new RaycastContext(eye, target, RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(p);
    }

    private void mine(BlockPos target) {
        TreeCycle.Phase phase = cycle.phase();
        if (phase == TreeCycle.Phase.CUT && !cutPositionReady()) return;
        selectTool(taskWorld.getBlockState(target));
        int token = generation;
        Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), 100, () -> {
            if (running && token == generation && mc.world == taskWorld && mc.player != null
                && cycle.phase() == phase && (phase != TreeCycle.Phase.CUT || cutPositionReady()) && reachable(target)) {
                String action = phase == TreeCycle.Phase.RETURN ? "回收垫脚方块"
                    : expectedLogs.containsKey(target) ? "砍伐原木" : "清理遮挡树叶";
                announce("mine", List.of(phase, target), action + "：" + target.toShortString());
                BlockUtils.breakBlock(target, true);
            }
        });
    }

    private void selectTool(BlockState state) {
        if (!autoTool.get()) return;
        var efficiency = taskWorld.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
        int selected = mc.player.getInventory().getSelectedSlot(), best = selected;
        double score = TreeMaterials.toolScore(mc.player.getInventory().getStack(selected), state,
            EnchantmentHelper.getLevel(efficiency, mc.player.getInventory().getStack(selected)));
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            double candidate = TreeMaterials.toolScore(stack, state, EnchantmentHelper.getLevel(efficiency, stack));
            if (candidate > score + 0.00001) { best = slot; score = candidate; }
        }
        if (best != selected) announce("tool", mc.player.getInventory().getStack(best).getItem(),
            "切换工具：" + mc.player.getInventory().getStack(best).getName().getString());
        if (best >= 9) { InvUtils.move().from(best).toHotbar(workSlot.get() - 1); best = workSlot.get() - 1; }
        InvUtils.swap(best, false);
    }

    private BlockPos chooseStand(List<BlockPos> left) {
        int baseY = trees.get(cycle.index()).stream().mapToInt(BlockPos::getY).min().orElse(mc.player.getBlockY());
        BlockPos target = left.stream().min(Comparator.<BlockPos>comparingInt(BlockPos::getY)
            .thenComparingDouble(p -> p.getSquaredDistance(mc.player.getEntityPos()))).orElseThrow();
        BlockPos best = null; double score = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -6; dy <= 4; dy++) {
                BlockPos p = new BlockPos(target.getX() + dx, baseY + dy, target.getZ() + dz);
                if (failedStands.contains(p) || abandonedSupports.contains(p.down())
                    || !safeGround(p.down()) || !open(p) || !open(p.up())) continue;
                if (!clearable(p.up(2)) || !clearable(p.up(3))) continue;
                double value = p.getSquaredDistance(mc.player.getEntityPos()) + (dx * dx + dz * dz) * 8;
                if (value < score) { best = p; score = value; }
            }
        }
        return best;
    }
    private boolean open(BlockPos p) {
        var state = taskWorld.getBlockState(p);
        return state.getFluidState().isEmpty() && state.getCollisionShape(taskWorld, p).isEmpty();
    }
    private boolean clearable(BlockPos p) {
        return open(p) || leafTypes.containsKey(taskWorld.getBlockState(p).getBlock());
    }
    private boolean safeGround(BlockPos p) {
        var state = taskWorld.getBlockState(p);
        return state.getFluidState().isEmpty() && Block.isShapeFullCube(state.getCollisionShape(taskWorld, p))
            && !state.isOf(Blocks.MAGMA_BLOCK) && !state.isOf(Blocks.SLIME_BLOCK) && !state.isOf(Blocks.HONEY_BLOCK);
    }
    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ(); return dx * dx + dz * dz;
    }

    private boolean walk(BlockPos target) {
        boolean arrived = mc.player.getBlockPos().equals(target);
        if (arrived && mc.player.isOnGround()) { cancelWalk(); return true; }
        if (!target.equals(walkTarget)) {
            cancelBreaking(); driver.walkTo(target, true); walkTarget = target.toImmutable(); walkTicks = walkIdle = 0;
            announce("move", List.of(cycle.phase(), target), "寻路：前往 " + target.toShortString());
            movement.reset();
        }
        walkTicks++;
        walkIdle = driver.active() ? 0 : walkIdle + 1;
        boolean stuck = movement.stuck(mc.player.getX(), mc.player.getZ(), cycle.phase() == TreeCycle.Phase.COLLECT ? 5 : 1);
        if (walkTicks > 1200 || walkIdle > 60 || stuck) {
            if (cycle.phase() == TreeCycle.Phase.COLLECT) {
                skipTree("原木拾取路线不可达，无法捡完", false);
            } else if (cycle.phase() == TreeCycle.Phase.CUT && route.isEmpty()) {
                failedStands.add(target.toImmutable()); cancelWalk(); column = null;
                if (failedStands.size() >= 8) { skipTree("多个树下位置均不可达", true); return false; }
                waitFor("当前位置无法靠近，正在换一个树下位置");
            } else if (cycle.phase() == TreeCycle.Phase.RETURN) {
                skipTree("返回路线无法到达", false);
            } else skipTree("无法到达本棵树的工作位置", true);
        }
        return false;
    }
    private void cancelWalk() { if (driver != null) driver.idle(); walkTarget = null; walkTicks = walkIdle = 0; movement.reset(); }
    private void cancelBreaking() { if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking(); }

    private boolean centerColumn() {
        centeringTarget = column;
        double dx = column.getX() + 0.5 - mc.player.getX(), dz = column.getZ() + 0.5 - mc.player.getZ();
        if (dx * dx + dz * dz < 0.008) {
            var velocity = mc.player.getVelocity(); mc.player.setVelocity(0, velocity.y, 0); return true;
        }
        var velocity = mc.player.getVelocity();
        mc.player.setVelocity(Math.clamp(dx * 0.3, -0.08, 0.08), velocity.y, Math.clamp(dz * 0.3, -0.08, 0.08));
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void move(PlayerMoveEvent event) {
        if (!running || mc.player == null || mc.world != taskWorld || centeringTarget == null
            || event.type != MovementType.SELF || event.movement == Vec3d.ZERO) return;
        // Change the actual vanilla movement vector before collision resolution. Do not teleport or change gravity.
        ((IVec3d) event.movement).meteor$setXZ(TreeDescent.centerStep(mc.player.getX(), centeringTarget.getX()),
            TreeDescent.centerStep(mc.player.getZ(), centeringTarget.getZ()));
    }

    private void beginAscent() {
        if (!autoPillar.get()) { skipTree("还有高处原木，但自动搭高已关闭", true); return; }
        if (route.size() >= maxPillar.get()) { skipTree("已到最大搭高高度", true); return; }
        if (!mc.player.isOnGround() || !centerColumn()) return;
        cancelWalk();
        if (!TreeMovementProgress.alignedForPillar(mc.player.getY(), column.getY(), route.size())) {
            skipTree("实际站立高度与垫脚柱不一致", true); return;
        }
        BlockPos next = column.up(route.size());
        if (next.getY() + 2 > taskWorld.getTopYInclusive()) { skipTree("已到世界高度限制", true); return; }
        for (int offset = 0; offset <= 3; offset++) {
            BlockPos head = next.up(offset);
            if (!open(head)) {
                if (leafTypes.containsKey(taskWorld.getBlockState(head).getBlock()) && reachable(head)) { mine(head); return; }
                skipTree("垫脚柱上方被遮挡", true); return;
            }
        }
        if (material(next) < 0) { skipTree("没有黑名单外可用的垫脚方块", true); return; }
        jumpTarget = next; jumpTicks = 0;
        info("搭高：在 %s 起跳放置第 %d 层垫脚方块。", next.toShortString(), route.size() + 1);
        cancelBreaking(); mc.player.jump();
    }
    private int material(BlockPos p) {
        int best = -1, count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (TreeMaterials.allowed(stack, blacklist.get(), taskWorld, p) && stack.getCount() > count) {
                best = slot; count = stack.getCount();
            }
        }
        return best;
    }
    private void ascend() {
        setStatus("正在搭高，已搭 " + route.size() + " 格");
        jumpTicks++;
        if (horizontal(mc.player.getBlockPos(), column) > 0) { skipTree("偏离原垫脚柱", false); return; }
        centerColumn();
        if (TreeMovementProgress.placementWindow(mc.player.getY(), jumpTarget.getY())) {
            int slot = material(jumpTarget);
            if (slot < 0) { jumpTarget = null; skipTree("垫脚材料已用完", true); return; }
            if (slot >= 9) { InvUtils.move().from(slot).toHotbar(workSlot.get() - 1); slot = workSlot.get() - 1; }
            ItemStack stack = mc.player.getInventory().getStack(slot);
            announce("tool", stack.getItem(), "切换垫脚材料：" + stack.getName().getString());
            pendingPlace = jumpTarget; pendingBlock = ((BlockItem) stack.getItem()).getBlock(); pendingTicks = 0; watch();
            BlockPos target = pendingPlace; int token = generation, chosenSlot = slot;
            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), 100, () -> {
                if (running && generation == token && target.equals(pendingPlace) && mc.world == taskWorld) {
                    if (TreeMovementProgress.placementWindow(mc.player.getY(), target.getY())
                        && TreeMaterials.allowed(mc.player.getInventory().getStack(chosenSlot), blacklist.get(), taskWorld, target)) {
                        if (BlockUtils.place(target, new FindItemResult(chosenSlot, 1), false, 100, true, true, true)) {
                            announce("place", target, "放置垫脚方块：" + target.toShortString() + "，等待服务器确认"); return;
                        }
                    }
                    pendingPlace = null; pendingBlock = null; watch();
                }
            });
            return;
        }
        if (jumpTicks > 5 && mc.player.isOnGround()) {
            jumpTarget = null;
            if (++jumpFailures >= 2) {
                if (route.isEmpty()) {
                    failedStands.add(column); column = null; jumpFailures = 0;
                    if (failedStands.size() >= 8) skipTree("多次搭高失败", true);
                    else setStatus("跳跃空间不足，正在更换搭高位置");
                }
                else skipTree("搭高跳跃失败", true);
            }
        } else if (jumpTicks > 40) {
            jumpTarget = null; skipTree("跳跃没有完成", true);
        }
    }

    private void returnDown() {
        announce("phase", "return", "返回：沿原柱逐层回收垫脚方块，剩余 " + route.size() + " 格。");
        if (route.size() != returnSteps) { returnSteps = route.size(); returnTicks = 0; }
        if (++returnTicks > 600) { skipTree("返回回收 30 秒无进展", false); return; }
        if (route.isEmpty()) {
            if (!mc.player.isOnGround()) return;
            cancelBreaking(); column = null;
            if (stopping) {
                boolean restart = restartAfterReturn; restartAfterReturn = false;
                halt(stopReason, false);
                if (restart) start();
                return;
            }
            if (skipping) { advanceSkippedTree(); return; }
            if (relocating) {
                relocating = false; cycle.resumeCut(true);
                announce("phase", "relocate", "本棵另一侧树枝够不到，返回地面后重新靠近。"); return;
            }
            cycle.returned(true); setStatus("正在拾取本棵掉落物"); return;
        }
        var top = route.top(); BlockPos p = block(top.pos());
        if (column == null || horizontal(mc.player.getBlockPos(), p) > 0) {
            recoverFromGround(); return;
        }
        cancelWalk();
        if (!p.equals(descentTarget)) {
            descentTarget = p; descentSequence = -1; syncWaitTicks = 0;
        }
        var local = taskWorld.getBlockState(p);
        var previousServer = serverSupports.get(p);
        if (local.isAir() && (previousServer == null || previousServer.isAir() || previousServer.isOf(top.block()))
            && descentSequence >= 0 && acknowledgedSequence >= descentSequence && ticks > ackTick) {
            removedSupports.add(p); serverSupports.put(p, Blocks.AIR.getDefaultState());
        }
        var server = serverSupports.get(p);
        boolean replaced = server != null && !server.isAir() && !server.isOf(top.block());
        var state = TreeDescent.classify(local.isOf(top.block()), local.isAir(), removedSupports.contains(p), replaced);
        boolean centered = centerColumn();
        var action = TreeDescent.decide(p.getY(), mc.player.getY(), mc.player.isOnGround(), centered,
            state, landingBelow(p));
        switch (action) {
            case COMPLETE -> {
                cancelBreaking(); route.landedAfterRemoval(); removedSupports.remove(p); serverSupports.remove(p);
                descentTarget = null; descentSequence = -1; syncWaitTicks = 0; watch();
            }
            case MINE -> { syncWaitTicks = 0; setStatus("沿原柱返回，待回收 " + route.size() + " 格"); mine(p); }
            case CENTER -> { cancelBreaking(); waitFor("正在对齐垫脚柱中心，解除脚边碰撞"); }
            case WAIT_LANDING -> { cancelBreaking(); waitFor("方块已回收，等待落到下一层"); }
            case WAIT_CONFIRMATION, WAIT_SUPPORT -> {
                cancelBreaking(); waitFor("等待服务器同步回收方块…");
                if (++syncWaitTicks > 160) skipTree("服务器回收同步超时", false);
            }
            case RECOVER -> { centeringTarget = null; recoverFromGround(); }
            case REPLACED -> skipTree("原垫脚方块已被替换", false);
            case UNSUPPORTED -> {
                cancelBreaking(); waitFor("正在复核下一层落脚面…");
                if (++syncWaitTicks >= 10) skipTree("下方没有可落脚面或存在岩浆等危险，无法沿柱返回", false);
            }
        }
    }

    private TreeDescent.Landing landingBelow(BlockPos support) {
        for (int down = 1; down <= 3; down++) {
            BlockPos p = support.down(down);
            if (!taskWorld.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) return TreeDescent.Landing.WAIT_SYNC;
            var state = taskWorld.getBlockState(p);
            if (TreeLanding.hazardous(state)) return TreeDescent.Landing.HAZARD;
            double surface = TreeLanding.surface(state, taskWorld, p);
            if (!Double.isNaN(surface)) return support.getY() + 1 - surface <= 2.25
                ? TreeDescent.Landing.SUPPORTED : TreeDescent.Landing.UNSUPPORTED;
            var confirmed = serverSupports.get(p);
            if (confirmed != null && !confirmed.isAir() && !removedSupports.contains(p)) return TreeDescent.Landing.WAIT_SYNC;
        }
        return TreeDescent.Landing.UNSUPPORTED;
    }

    private void reconcileRoute() {
        if (taskWorld == null || mc.world != taskWorld || pendingPlace != null) return;
        int pruned = route.prune(step -> {
            BlockPos p = block(step.pos());
            if (!taskWorld.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) return false;
            boolean settled = !running || (removedSupports.contains(p) && mc.player != null
                && mc.player.isOnGround() && mc.player.getY() <= p.getY() + 0.1);
            return settled && !taskWorld.getBlockState(p).isOf(step.block());
        });
        if (pruned > 0 && running) watch();
    }

    private void recoverFromGround() {
        if (!mc.player.isOnGround()) { setStatus("等待落地后继续回收"); return; }
        for (var step : route.snapshot().reversed()) {
            BlockPos p = block(step.pos());
            if (taskWorld.getBlockState(p).isOf(step.block()) && reachable(p)
                && !p.equals(mc.player.getBlockPos().down())) {
                cancelWalk(); mine(p); setStatus("从地面回收剩余垫脚方块 " + route.size()); return;
            }
        }
        BlockPos top = block(route.top().pos()), best = null;
        double score = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -4; dy <= 4; dy++) {
            BlockPos candidate = new BlockPos(top.getX() + dx, mc.player.getBlockY() + dy, top.getZ() + dz);
            if (!safeGround(candidate.down()) || !open(candidate) || !open(candidate.up())) continue;
            double distance = candidate.getSquaredDistance(mc.player.getEntityPos());
            if (distance < score) { best = candidate; score = distance; }
        }
        if (best == null || best.equals(mc.player.getBlockPos())) {
            skipTree("剩余垫脚方块超出可回收距离", false); return;
        }
        setStatus("正在靠近原垫脚柱回收"); walk(best);
    }

    private void collect() {
        if (ticks % 5 != 0) return;
        announce("phase", "collect", "拾取：仅收集本棵对应的原木，超过等待时间仍未捡完则跳过。");
        List<ItemEntity> drops = loot.find(taskWorld.getEntities());
        ItemEntity nearest = drops.stream().min(Comparator.comparingDouble(item -> item.squaredDistanceTo(mc.player))).orElse(null);
        if (nearest != null && !capacity(nearest.getStack())) {
            skipTree("背包没有空间，无法捡完对应原木", false); return;
        }
        TreeCycle.Collection result = cycle.observeDrops(drops.size(), true, 5);
        if (result == TreeCycle.Collection.TIMED_OUT) {
            skipTree("原木拾取等待超时，剩余 " + drops.size() + " 堆未捡完", false); return;
        }
        if (result == TreeCycle.Collection.COMPLETE) {
            cancelWalk();
            if (cycle.phase() == TreeCycle.Phase.DONE) finishTask();
            else prepareTree();
            return;
        }
        if (!drops.isEmpty()) {
            setStatus("拾取本棵掉落物，剩余 " + drops.size() + " 堆");
            announce("pickup", nearest.getUuid(), "拾取对应原木：" + nearest.getStack().getName().getString()
                + " ×" + nearest.getStack().getCount() + "，位置 " + nearest.getBlockPos().toShortString());
            walk(nearest.getBlockPos());
        } else {
            cancelWalk(); waitFor("确认对应原木已捡完…");
        }
    }
    private boolean capacity(ItemStack drop) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty() || (ItemStack.areItemsAndComponentsEqual(stack, drop) && stack.getCount() < stack.getMaxCount())) return true;
        }
        return false;
    }

    private void skipTree(String reason, boolean tryReturn) {
        // The user's End button still stops; automatic tree failures never stop the whole batch.
        if (stopping && restartAfterReturn) {
            cancelWalk(); cancelBreaking();
            warning("上次垫脚柱无法回收：%s，放弃旧记录并继续新任务。", reason);
            forgetSkippedRoute(); halt("继续扫描新的树木", false); start(); return;
        }
        if (stopping) { halt(reason, true); return; }
        cancelWalk(); cancelBreaking(); generation++; jumpTarget = null; centeringTarget = null;
        if (!skipping) warning("跳过第 %d / %d 棵树：%s。", cycle.index() + 1, trees.size(), reason);
        else warning("本棵回收未完成：%s，保留遗留方块并继续下一棵。", reason);
        skipping = true; relocating = false;
        if (tryReturn && (!route.isEmpty() || pendingPlace != null)) {
            cycle.returnToGround(); returnTicks = 0;
            setStatus("跳过本棵，先尝试回收垫脚柱…");
        } else advanceSkippedTree();
    }

    private void advanceSkippedTree() {
        forgetSkippedRoute();
        skippedTrees++; skipping = false;
        cycle.skip();
        if (cycle.phase() == TreeCycle.Phase.DONE) finishTask();
        else prepareTree();
    }

    private void forgetSkippedRoute() {
        BlockPos leftover = pendingPlace != null ? pendingPlace : route.isEmpty() ? null : block(route.top().pos());
        int abandoned = abandonRoute();
        if (abandoned > 0) warning("放弃本棵的 %d 条垫脚记录，遗留位置示例：%s；方块保留，可稍后手动回收。",
            abandoned, leftover.toShortString());
    }

    /** Forget active ownership without mining or moving any world block. */
    private int abandonRoute() {
        int count = route.size() + (pendingPlace == null ? 0 : 1);
        for (var step : route.snapshot()) abandonedSupports.add(block(step.pos()));
        if (pendingPlace != null) abandonedSupports.add(pendingPlace);
        route.clear(); pendingPlace = column = jumpTarget = walkTarget = centeringTarget = descentTarget = null;
        pendingBlock = null; descentSequence = acknowledgedSequence = ackTick = -1;
        pendingTicks = syncWaitTicks = returnTicks = returnSteps = 0;
        watched = Set.of(); changes.clear(); actionAcks.clear(); expectedLogs = Map.of();
        removedSupports.clear(); serverSupports.clear(); cut.clear(); approach.reset(); generation++;
        return count;
    }

    private void finishTask() { halt("本次树木处理完成，跳过 " + skippedTrees + " 棵", false); }

    private void requestStop(String reason) {
        if (!running) { halt(reason, false); return; }
        stopping = true; stopReason = reason; jumpTarget = null; generation++; cancelWalk(); cancelBreaking();
        info("结束：停止砍伐，先回收现有垫脚柱；再次点击结束可暂停回收。");
        cycle.returnToGround();
        if (cycle.phase() == TreeCycle.Phase.COLLECT || cycle.phase() == TreeCycle.Phase.DONE) halt(reason, false);
        else setStatus("正在结束并沿原柱返回…");
    }
    private void resumeReturn() {
        if (running || scanning) return;
        reconcileRoute();
        if (route.isEmpty()) { start(); return; }
        if (mc.player == null || mc.world != taskWorld) { error("必须回到原世界和垫脚柱位置才能继续回收。"); return; }
        try { driver = new BaritoneTreeDriver(); driver.begin(); }
        catch (RuntimeException | LinkageError e) { error("无法继续回收：%s", e.getMessage()); return; }
        if (!isActive()) toggle();
        originalSlot = mc.player.getInventory().getSelectedSlot();
        cycle = new TreeCycle(1, 40); cycle.returnToGround();
        column = block(route.snapshot().getFirst().pos());
        restartAfterReturn = true; returnTicks = 0; actions.reset();
        descentTarget = null; descentSequence = -1; syncWaitTicks = 0; actionAcks.clear();
        stopping = running = true; stopReason = "垫脚柱已回收"; generation++; watch();
        setStatus("正在回收上次的垫脚方块");
    }
    private void halt(String message, boolean preserveRoute) {
        if (!route.isEmpty() && taskWorld != null) {
            BlockPos p = block(route.top().pos());
            LOGGER.warn("Return stopped: {} remaining={} feet={} support={} local={} confirmed={} sequence={} ack={}",
                message, route.size(), mc.player == null ? null : mc.player.getEntityPos(), p,
                taskWorld.getBlockState(p), serverSupports.get(p), descentSequence, acknowledgedSequence);
        }
        scanning = running = stopping = skipping = false; scanner = null; generation++; cancelBreaking();
        restartAfterReturn = false;
        if (driver != null) {
            try { driver.stop(); } catch (RuntimeException | LinkageError ignored) {}
        }
        driver = null; walkTarget = jumpTarget = null;
        centeringTarget = descentTarget = null; descentSequence = -1; actionAcks.clear();
        if (pendingPlace != null && taskWorld != null && taskWorld.getBlockState(pendingPlace).isOf(pendingBlock)) {
            // Never claim an unacknowledged local prediction as owned scaffolding.
            warning("存在未确认的垫脚方块：%s，请检查后处理。", pendingPlace.toShortString());
        }
        pendingPlace = null; pendingBlock = null; watched = Set.of(); changes.clear();
        if (!preserveRoute && route.isEmpty()) expectedLogs = Map.of();
        if (originalSlot >= 0 && mc.player != null) InvUtils.swap(originalSlot, false);
        originalSlot = -1;
        setStatus(message);
        if (!route.isEmpty()) warning("%s；剩余 %d 个垫脚方块，点击开始将先尝试回收。", message, route.size());
        else info("%s", message);
    }
    @EventHandler private void left(GameLeftEvent event) { halt("已离开世界", false); route.clear(); taskWorld = null; }
    private void setStatus(String value) {
        status = value;
        if (statusLabel != null) statusLabel.set(value);
        if (controlButton != null) controlButton.set(running || scanning ? "结束" : "开始");
    }
    private void announce(String channel, Object key, String message) {
        if (actions.changed(channel, key)) info("%s", message);
    }
    private void waitFor(String message) { setStatus(message); announce("wait", message, message); }
    @Override public String getInfoString() {
        if (scanning) return "扫描中";
        if (!running) return "等待开始";
        return switch (cycle.phase()) {
            case CUT -> "砍树"; case RETURN -> "回收返回"; case COLLECT -> "拾取"; case DONE -> "完成";
        };
    }
}
