package dev.tanghulu.stash.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

import dev.tanghulu.stash.Addon;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import static dev.tanghulu.stash.Utils.*;

public class ElytraFlyPlusPlus extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgObstaclePasser = settings.createGroup("Obstacle Passer");

    private final Setting<Boolean> bounce = sgGeneral.add(new BoolSetting.Builder()
        .name("bounce")
        .description("自动执行鞘翅弹跳飞行。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> motionYBoost = sgGeneral.add(new BoolSetting.Builder()
        .name("motion-y-boost")
        .description("通过抵消垂直动量来提高水平速度。")
        .defaultValue(false)
        .visible(bounce::get)
        .build()
    );

    private final Setting<Boolean> onlyWhileColliding = sgGeneral.add(new BoolSetting.Builder()
        .name("only-while-colliding")
        .description("只有碰到墙壁时才启用垂直动量加速。")
        .defaultValue(true)
        .visible(() -> bounce.get() && motionYBoost.get())
        .build()
    );

    private final Setting<Boolean> tunnelBounce = sgGeneral.add(new BoolSetting.Builder()
        .name("tunnel-bounce")
        .description("允许在 1 格宽、2 格高的隧道内弹跳；离开隧道时应关闭。")
        .defaultValue(false)
        .visible(() -> bounce.get() && motionYBoost.get())
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("希望维持的速度，单位：格/秒。")
        .defaultValue(100.0)
        .sliderRange(20, 250)
        .visible(() -> bounce.get() && motionYBoost.get())
        .build()
    );

    private final Setting<Boolean> lockPitch = sgGeneral.add(new BoolSetting.Builder()
        .name("lock-pitch")
        .description("弹跳飞行时保持指定俯仰角。")
        .defaultValue(true)
        .visible(bounce::get)
        .build()
    );

    private final Setting<Double> pitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch")
        .description("弹跳飞行时使用的上下视角。")
        .defaultValue(90.0)
        .sliderRange(-90, 90)
        .visible(() -> bounce.get() && lockPitch.get())
        .build()
    );

    private final Setting<Boolean> lockYaw = sgGeneral.add(new BoolSetting.Builder()
        .name("lock-yaw")
        .description("弹跳飞行时保持指定水平朝向。")
        .defaultValue(false)
        .visible(bounce::get)
        .build()
    );

    private final Setting<Boolean> useCustomYaw = sgGeneral.add(new BoolSetting.Builder()
        .name("use-custom-yaw")
        .description("允许使用非 45 度倍数的朝向，也会影响 Baritone 绕障目标；仅锁视角可使用 Meteor 的旋转模块。")
        .defaultValue(false)
        .visible(bounce::get)
        .build()
    );

    private final Setting<Double> yaw = sgGeneral.add(new DoubleSetting.Builder()
        .name("yaw")
        .description("默认吸附到最近的 45 度方向；启用自定义朝向后使用此角度，同时影响绕障目标。")
        .defaultValue(0.0)
        .sliderRange(0, 359)
        .visible(() -> bounce.get() && useCustomYaw.get())
        .build()
    );

    private final Setting<Boolean> highwayObstaclePasser = sgObstaclePasser.add(new BoolSetting.Builder()
        .name("highway-obstacle-passer")
        .description("使用 Baritone 绕过道路上的障碍。")
        .defaultValue(false)
        .visible(bounce::get)
        .build()
    );

    private final Setting<Boolean> useCustomStartPos = sgObstaclePasser.add(new BoolSetting.Builder()
        .name("use-custom-start-position")
        .description("环线道路或不沿原点道路飞行时使用；关闭时起点为 (0, 0)。")
        .defaultValue(false)
        .visible(() -> bounce.get() && highwayObstaclePasser.get())
        .build()
    );

    private final Setting<BlockPos> startPos = sgObstaclePasser.add(new BlockPosSetting.Builder()
        .name("start-position")
        .description("启用自定义起点后，用于确定飞行路线的起点。")
        .defaultValue(new BlockPos(0,0,0))
        .visible(() -> bounce.get() && highwayObstaclePasser.get() && useCustomStartPos.get())
        .build()
    );

    private final Setting<Boolean> awayFromStartPos = sgObstaclePasser.add(new BoolSetting.Builder()
        .name("away-from-start-position")
        .description("开启时远离起点，关闭时朝向起点；默认起点为 (0, 0)。")
        .defaultValue(true)
        .visible(() -> bounce.get() && highwayObstaclePasser.get())
        .build()
    );

    private final Setting<Double> distance = sgObstaclePasser.add(new DoubleSetting.Builder()
        .name("distance")
        .description("为 Baritone 重新对齐路线时设置的前方目标距离。")
        .defaultValue(10.0)
        .visible(() -> bounce.get() && highwayObstaclePasser.get())
        .build()
    );

    private final Setting<Integer> targetY = sgObstaclePasser.add(new IntSetting.Builder()
        .name("y-level")
        .description("道路的 Y 高度；设置错误会导致弹跳无法正常开始。")
        .defaultValue(120)
        .visible(() -> bounce.get() && highwayObstaclePasser.get())
        .build()
    );

    private final Setting<Boolean> avoidPortalTraps = sgObstaclePasser.add(new BoolSetting.Builder()
        .name("avoid-portal-traps")
        .description("加载区块时尝试识别并绕过传送门陷阱。")
        .defaultValue(false)
        .visible(() -> bounce.get() && highwayObstaclePasser.get())
        .build()
    );

    private final Setting<Double> portalAvoidDistance = sgObstaclePasser.add(new DoubleSetting.Builder()
        .name("portal-avoid-distance")
        .description("接近传送门陷阱到此距离时交给绕障功能处理。")
        .defaultValue(20)
        .min(0)
        .sliderMax(50)
        .visible(() -> bounce.get() && highwayObstaclePasser.get() && avoidPortalTraps.get())
        .build()
    );

    private final Setting<Integer> portalScanWidth = sgObstaclePasser.add(new IntSetting.Builder()
        .name("portal-scan-width")
        .description("沿道路两侧检查传送门陷阱的范围。")
        .defaultValue(5)
        .min(3)
        .sliderMax(10)
        .visible(() -> bounce.get() && highwayObstaclePasser.get() && avoidPortalTraps.get())
        .build()
    );

    private final Setting<Boolean> fakeFly = sgGeneral.add(new BoolSetting.Builder()
        .name("chestplate-fakefly")
        .description("穿胸甲飞行以减少鞘翅耐久消耗；快捷栏中必须有鞘翅。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toggleElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-elytra")
        .description("启用时穿上鞘翅，关闭时换回胸甲。")
        .defaultValue(false)
        .visible(() -> !fakeFly.get())
        .build()
    );

    public ElytraFlyPlusPlus() {
        super(
            Addon.CATEGORY,
            "ElytraFlyPlusPlus",
            "提供弹跳飞行、速度增强、自动绕障及胸甲飞行等功能。"
        );
    }

    private boolean startSprinting;
    private BlockPos portalTrap = null;
    private boolean paused = false;

    private boolean elytraToggled = false;

    private Vec3d lastUnstuckPos;
    private int stuckTimer = 0;

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event)
    {
        if (event.packet instanceof PlayerPositionLookS2CPacket packet)
        {
//            onActivate();
        }
        else if (event.packet instanceof CloseScreenS2CPacket)
        {
            event.cancel();
        }
    }

    @Override
    public void onActivate()
    {
        if (mc.player == null || mc.player.getAbilities().allowFlying) return;

        startSprinting = mc.player.isSprinting();
        tempPath = null;
        portalTrap = null;
        paused = false;
        waitingForChunksToLoad = false;
        elytraToggled = false;
        lastPos = mc.player.getEntityPos();
        lastUnstuckPos = mc.player.getEntityPos();
        stuckTimer = 0;

        // I don't know any other way to fix this stupid shit
        if (bounce.get() && mc.player.getEntityPos().multiply(1, 0, 1).length() >= 100)
        {
            if (BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().currentDestination() == null)
            {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
            }

            if (!useCustomStartPos.get())
            {
                startPos.set(new BlockPos(0, 0, 0));
            }

            if (!useCustomYaw.get())
            {
                // If less than 100 blocks from the start pos, angle calculation may be wrong, so just use players yaw
                if (mc.player.getBlockPos().getSquaredDistance(startPos.get()) < 10_000 || !highwayObstaclePasser.get())
                {
                    double playerAngleNormalized = angleOnAxis(mc.player.getYaw());
                    yaw.set(playerAngleNormalized);
                }
                else
                {
                    // Otherwise use the angle from the starting position to the players position
                    BlockPos directionVec = mc.player.getBlockPos().subtract(startPos.get());
                    double angle = Math.toDegrees(Math.atan2(-directionVec.getX(), directionVec.getZ()));
                    double angleNormalized = angleOnAxis(angle);
                    if (!awayFromStartPos.get())
                    {
                        angleNormalized += 180;
                    }

                    yaw.set(angleNormalized);
                }
            }
        }
    }

    private Vec3d lastPos;

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (mc.player == null || event.type != MovementType.SELF || !enabled() || !motionYBoost.get() || !bounce.get()) return;

        if (onlyWhileColliding.get() && !mc.player.horizontalCollision) return;

        if (lastPos != null)
        {
            double speedBps = mc.player.getEntityPos().subtract(lastPos).multiply(20, 0, 20).length();

            Timer timer = Modules.get().get(Timer.class);
            if (timer.isActive()) {
                speedBps *= timer.getMultiplier();
            }

            if (mc.player.isOnGround() && mc.player.isSprinting() && speedBps < speed.get())
            {
                if (speedBps > 20 || tunnelBounce.get())
                {
                    ((IVec3d)event.movement).meteor$setY(0.0);
                }
                mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
            }
        }

        lastPos = mc.player.getEntityPos();
    }

    @Override
    public void onDeactivate()
    {
        if (mc.player == null) return;

        if (bounce.get())
        {
            if (BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().currentDestination() == null)
            {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
            }
        }

        mc.player.setSprinting(startSprinting);

        if (toggleElytra.get() && !fakeFly.get())
        {
            if (!mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().toString().contains("chestplate")) {
                Modules.get().get(ChestSwap.class).swap();
            }
        }
    }

    // 5 chunks forwards
    private final double maxDistance = 16 * 5;

    // a path used when there are no valid blocks in range.
    // it will instead path to this and then when it gets close it will look for a valid block again
    private BlockPos tempPath = null;

    private boolean waitingForChunksToLoad;

    @EventHandler
    private void onTick(TickEvent.Pre event)
    {
        if (mc.player == null || mc.player.getAbilities().allowFlying) return;

        if (toggleElytra.get() && !fakeFly.get() && !elytraToggled)
        {
            if (!(mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA)))
            {
                Modules.get().get(ChestSwap.class).swap();
            }
            else
            {
                elytraToggled = true;
            }
        }

        if (enabled()) mc.player.setSprinting(true);

        if (bounce.get())
        {
            if (tempPath != null && mc.player.getBlockPos().getSquaredDistance(tempPath) < 500)
            {
                tempPath = null;
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
            }
            else if (tempPath != null)
            {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(tempPath));
                return;
            }

            // if still pathing, wait for that to complete
            if (highwayObstaclePasser.get() && BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().getGoal() != null)
            {
                return;
            }

            if (mc.player.squaredDistanceTo(lastUnstuckPos) < 25)
            {
                stuckTimer++;
            }
            else
            {
                stuckTimer = 0;
                lastUnstuckPos = mc.player.getEntityPos();
            }

            if (highwayObstaclePasser.get() && mc.player.getEntityPos().length() > 100 && // > 100 check needed bc server sends queue coordinates when joining in first tick causing goal coordinates to be set to (0, 0)
                (mc.player.getY() < targetY.get() || mc.player.getY() > targetY.get() + 2 || (mc.player.horizontalCollision && !mc.player.collidedSoftly) // collisions / out of highway
                || (portalTrap != null && portalTrap.getSquaredDistance(mc.player.getBlockPos()) < portalAvoidDistance.get() * portalAvoidDistance.get()) // portal trap detection
                || waitingForChunksToLoad // waiting for chunks to load
                || stuckTimer > 50))
            {
                waitingForChunksToLoad = false;
                paused = true;
                BlockPos goal = mc.player.getBlockPos();
                double currDistance = distance.get(); // Keep checking farther distances until a goal is found that has a block beneath it

                if (portalTrap != null) {
                    currDistance += mc.player.getEntityPos().distanceTo(portalTrap.toCenterPos());
                    portalTrap = null;
                    info("正在绕过传送门。");
                }

                do
                {
                    if (currDistance > maxDistance)
                    {
                        tempPath = goal;
                        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
                        return;
                    }
                    Vec3d unitYawVec = yawToDirection(yaw.get());
                    Vec3d travelVec = mc.player.getEntityPos().subtract(startPos.get().toCenterPos());

                    double parallelCurrPosDot = travelVec.multiply(new Vec3d(1, 0, 1)).dotProduct(unitYawVec);
                    Vec3d parallelCurrPosComponent = unitYawVec.multiply(parallelCurrPosDot);

                    Vec3d pos = startPos.get().toCenterPos().add(parallelCurrPosComponent);
                    pos = positionInDirection(pos, yaw.get(), currDistance);

                    goal = new BlockPos((int)(Math.floor(pos.x)), targetY.get(), (int)Math.floor(pos.z));
                    currDistance++;

                    // Blocks in unloaded chunks are void air, for some reason checking if the chunk is loaded was always true, so I check this instead
                    if (mc.world.getBlockState(goal).getBlock() == Blocks.VOID_AIR)
                    {
                        waitingForChunksToLoad = true;
                        return;
                    }
                }
                // avoid pathing on air cause baritone freaks out, and dont path into portals in case a mod is avoiding portals
                while (!mc.world.getBlockState(goal.down()).isSolidBlock(mc.world, goal.down()) ||
                    mc.world.getBlockState(goal).getBlock() == Blocks.NETHER_PORTAL ||
                    !mc.world.getBlockState(goal).isAir());
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
            }
            else
            {
                // keep jumping
                paused = false;
                if (!enabled()) return;

                if (!fakeFly.get())
                {
                    if (mc.player.isOnGround() && (!motionYBoost.get() || Utils.getPlayerSpeed().multiply(1, 0, 1).length() < speed.get()))
                    {
                        mc.player.jump();
                    }
                }

                // set yaw and pitch
                if (lockYaw.get())
                {
                    mc.player.setYaw(yaw.get().floatValue());
                }
                if (lockPitch.get())
                {
                    mc.player.setPitch(pitch.get().floatValue());
                }
            }
        }

        if (enabled())
        {
            if (fakeFly.get())
            {
                doGrimEflyStuff();
            }
            else
            {
                sendStartFlyingPacket();
            }
        }
    }

    public boolean enabled()
    {
        return this.isActive() && !paused && mc.player != null && (fakeFly.get() || mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA));
    }

    private void doGrimEflyStuff()
    {
        FindItemResult itemResult = InvUtils.findInHotbar(Items.ELYTRA);
        if (!itemResult.found()) return;

        swapToItem(itemResult.slot());

        sendStartFlyingPacket();

        if (bounce.get() && mc.player.isOnGround() && (!motionYBoost.get() || Utils.getPlayerSpeed().multiply(1, 0, 1).length() < speed.get()))
        {
            mc.player.jump();
        }

        swapToItem(itemResult.slot());

    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event)
    {
        if (!fakeFly.get()) return;
        List<Identifier> armorEquipSounds = List.of(
            Identifier.of("minecraft:item.armor.equip_generic"),
            Identifier.of("minecraft:item.armor.equip_netherite"),
            Identifier.of("minecraft:item.armor.equip_elytra"),
            Identifier.of("minecraft:item.armor.equip_diamond"),
            Identifier.of("minecraft:item.armor.equip_gold"),
            Identifier.of("minecraft:item.armor.equip_iron"),
            Identifier.of("minecraft:item.armor.equip_chain"),
            Identifier.of("minecraft:item.armor.equip_leather"),
            Identifier.of("minecraft:item.elytra.flying")
        );
        for (Identifier identifier : armorEquipSounds) {
            if (identifier.equals(event.sound.getId())) {
                event.cancel();
                break;
            }
        }
    }

    // 38 is the meteor mapping for chestplate
    // serverside uses default mappings: https://imgs.search.brave.com/cyvAxjIhLweeF1qeRXpC_8ESRlImhUmMGWbV_n2to_A/rs:fit:860:0:0:0/g:ce/aHR0cHM6Ly9jNGsz/LmdpdGh1Yi5pby93/aWtpLnZnL2ltYWdl/cy8xLzEzL0ludmVu/dG9yeS1zbG90cy5w/bmc
    private void swapToItem(int slot) {
        ItemStack chestItem = mc.player.getInventory().getStack(38);
        ItemStack hotbarSwapItem = mc.player.getInventory().getStack(slot);

        Int2ObjectMap<ItemStack> changedSlots = new Int2ObjectOpenHashMap<>();
        changedSlots.put(6, hotbarSwapItem);
        changedSlots.put(slot + 36, chestItem);

        sendSwapPacket(changedSlots, slot);
    }

    private void sendStartFlyingPacket() {
        if (mc.player == null) return;
        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(
            mc.player,
            ClientCommandC2SPacket.Mode.START_FALL_FLYING
        ));
    }

    private void sendSwapPacket(Int2ObjectMap<ItemStack> changedSlots, int buttonNum) {
        int syncId  = mc.player.currentScreenHandler.syncId;
        int stateId = mc.player.currentScreenHandler.getRevision();

        Int2ObjectMap<ItemStackHash> changedHashes = new Int2ObjectOpenHashMap<>();
        var hasher = mc.player.networkHandler.getComponentHasher();
        for (var entry : changedSlots.int2ObjectEntrySet()) {
            changedHashes.put(entry.getIntKey(), ItemStackHash.fromItemStack(entry.getValue(), hasher));
        }

        mc.player.networkHandler.sendPacket(new ClickSlotC2SPacket(
            syncId,
            stateId,
            (short) 6,
            (byte) buttonNum,
            SlotActionType.SWAP,
            changedHashes,
            ItemStackHash.EMPTY
        ));
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event)
    {
        if (!avoidPortalTraps.get() || !highwayObstaclePasser.get()) return;
        ChunkPos pos = event.chunk().getPos();

        BlockPos centerPos = pos.getCenterAtY(targetY.get());

        // Check if chunk is on the players path
        Vec3d moveDir = yawToDirection(yaw.get());
        double distanceToHighway = distancePointToDirection(Vec3d.of(centerPos), moveDir, mc.player.getEntityPos());

        if (distanceToHighway > 21) return;

        for (int x = 0; x < 16; x++)
        {
            for (int z = 0; z < 16; z++)
            {
                for (int y = targetY.get(); y < targetY.get() + 3; y++)
                {
                    BlockPos position = new BlockPos(pos.x * 16 + x, y, pos.z * 16 + z);

                    if (distancePointToDirection(Vec3d.of(position), moveDir, mc.player.getEntityPos()) > portalScanWidth.get()) continue;

                    if (mc.world.getBlockState(position).getBlock().equals(Blocks.NETHER_PORTAL)) // TODO: This position could be unloaded
                    {
                        BlockPos posBehind = new BlockPos((int)Math.floor(position.getX() + moveDir.x), position.getY(), (int) Math.floor(position.getZ() + moveDir.z));

                        // Trap is detected when a portal has a solid block or another portal behind it
                        if (mc.world.getBlockState(posBehind).isSolidBlock(mc.world, posBehind) ||
                            mc.world.getBlockState(posBehind).getBlock() == Blocks.NETHER_PORTAL)
                        {
                            if (portalTrap == null || (
                                portalTrap.getSquaredDistance(posBehind) > 100 &&
                                    mc.player.getBlockPos().getSquaredDistance(posBehind) < mc.player.getBlockPos().getSquaredDistance(portalTrap))
                            )
                            {
                                portalTrap = posBehind;
                            }
                        }
                    }
                }
            }
        }
    }
}
