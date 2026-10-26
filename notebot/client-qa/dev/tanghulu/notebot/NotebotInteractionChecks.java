package dev.tanghulu.notebot;

import dev.tanghulu.addon.util.notebot.NotebotInteraction;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.util.ArrayList;

/** Actual 1.21.11 block shapes/raycast, without starting a game or joining a server. */
public final class NotebotInteractionChecks {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Scene implements BlockView {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.getDefaultState()); }
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        public int getHeight() { return 384; }
        public int getBottomY() { return -64; }
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
        Scene scene = new Scene();
        BlockPos pos = new BlockPos(0, 0, 0);
        scene.blocks.put(pos, Blocks.NOTE_BLOCK.getDefaultState());
        var shape = ShapeContext.absent();
        var top = NotebotInteraction.findHit(scene, new Vec3d(0.5, 3, 0.5), pos, 4.5, shape);
        check(top != null && top.getSide() == Direction.UP, "Use the visible top face, not the hardcoded underside");
        check(top.getPos().y == 1, "Send a point on the surface, not inside the block");
        var side = NotebotInteraction.findHit(scene, new Vec3d(3, 0.5, 0.5), pos, 4.5, shape);
        check(side != null && side.getSide() == Direction.EAST && side.getPos().x == 1, "Select an actual side face");
        check(NotebotInteraction.findHit(scene, new Vec3d(5.5, 0.5, 0.5), pos, 4.5, shape) != null, "A surface exactly at vanilla reach is usable");
        check(NotebotInteraction.findHit(scene, new Vec3d(5.51, 0.5, 0.5), pos, 4.5, shape) == null, "Reject blocks outside actual reach");
        scene.blocks.put(new BlockPos(1, 0, 0), Blocks.STONE.getDefaultState());
        check(NotebotInteraction.findHit(scene, new Vec3d(3, 0.5, 0.5), pos, 4.5, shape) == null, "Do not click through an intervening wall");
        scene.blocks.remove(new BlockPos(1, 0, 0));
        check(NotebotInteraction.findHit(scene, new Vec3d(0.5, 0.5, 0.5), pos, 4.5, shape) == null, "Do not interact from inside a solid block");
        scene.blocks.clear();
        check(NotebotInteraction.findHit(scene, new Vec3d(0.5, 3, 0.5), pos, 4.5, shape) == null, "A removed note block cannot be tuned");

        Class<?> bot = meteordevelopment.meteorclient.systems.modules.misc.Notebot.class;
        check(bot.getDeclaredMethod("tune").getReturnType() == void.class, "Pinned tuning injection target exists");
        check(bot.getDeclaredMethod("resetVariables").getReturnType() == void.class, "Pinned reset injection target exists");
        check(bot.getDeclaredMethod("isValidScanSpot", BlockPos.class).getReturnType() == boolean.class, "Pinned scan injection has matching descriptor");
        check(bot.getDeclaredField("clickedBlocks").getType() == java.util.List.class, "Pinned Meteor uses a List for clicked blocks");
        check(bot.getMethod("onDeactivate").getDeclaringClass() != bot, "Pinned Meteor inherits the empty lifecycle method we extend");
        check(bot.getDeclaredMethod("onTick", meteordevelopment.meteorclient.events.world.TickEvent.Pre.class).getReturnType() == void.class,
            "Playback intercept matches the actual -86 tick descriptor");
        check(bot.getDeclaredMethod("pause").getReturnType() == void.class, "Pause injection target exists");
        check(bot.getDeclaredField("song").getType() == meteordevelopment.meteorclient.utils.notebot.song.Song.class, "Song shadow descriptor matches");
        check(bot.getDeclaredField("playingMode").getType() == meteordevelopment.meteorclient.systems.modules.misc.Notebot.PlayingMode.class,
            "Preview can be kept separate from real block playback");
        check(bot.getDeclaredField("isPlaying").getType() == boolean.class && bot.getDeclaredField("currentTick").getType() == int.class,
            "Pause and progress shadows match the pinned runtime");

        // Check the compiled adapter, so regressions cannot move attackBlock outside the rotation callback.
        ClassNode fix = new ClassNode();
        try (var input = NotebotInteractionChecks.class.getClassLoader().getResourceAsStream("dev/tanghulu/addon/modules/NotebotFix.class")) {
            new ClassReader(input).accept(fix, 0);
        }
        var callback = fix.methods.stream().filter(method -> method.name.startsWith("lambda$playTick$")).findFirst().orElseThrow();
        var calls = new ArrayList<String>();
        for (var instruction : callback.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call.name);
        int attack = calls.indexOf("attackBlock"), swing = calls.indexOf("swingHand");
        check(attack > 0 && calls.indexOf("cancelBlockBreaking") < attack, "Release a prior mining operation before each tap");
        check(swing > attack && calls.lastIndexOf("cancelBlockBreaking") > swing, "Each actual tap sends attack, swing, release in order");
        check(calls.indexOf("getSide") < attack, "Attack uses a raycast face");
        check(!calls.contains("updateBlockBreakingProgress") && !calls.contains("breakBlock"), "Playback does not continue mining the note block");
        check(calls.indexOf("calcBlockBreakingDelta") < attack, "Reject instant-breaking conditions before sending a left click");
        var playTick = fix.methods.stream().filter(method -> method.name.equals("playTick")).findFirst().orElseThrow();
        var outerCalls = new ArrayList<String>();
        for (var instruction : playTick.instructions) if (instruction instanceof MethodInsnNode call) outerCalls.add(call.name);
        check(outerCalls.contains("rotate") && !outerCalls.contains("attackBlock"), "No attack can precede the scheduled rotation");
        int attackIndex = -1;
        for (var instruction : callback.instructions) if (instruction instanceof MethodInsnNode call && call.name.equals("attackBlock"))
            attackIndex = callback.instructions.indexOf(instruction);
        boolean sameCallbackLoop = false;
        for (var instruction : callback.instructions) if (instruction instanceof org.objectweb.asm.tree.JumpInsnNode jump
            && callback.instructions.indexOf(instruction) > attackIndex && callback.instructions.indexOf(jump.label) < attackIndex)
            sameCallbackLoop = true;
        check(sameCallbackLoop, "A full chord is dispatched inside the same rotation callback without waiting another game tick");
        var tuningCallback = fix.methods.stream().filter(method -> {
            for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.name.equals("interactBlock")) return true;
            return false;
        }).findFirst().orElseThrow();
        var tuningCalls = new ArrayList<String>();
        for (var instruction : tuningCallback.instructions) if (instruction instanceof MethodInsnNode call) tuningCalls.add(call.name);
        check(tuningCalls.indexOf("getBlockState") >= 0 && tuningCalls.indexOf("getBlockState") < tuningCalls.indexOf("begin"),
            "Reread live pitch in the rotation callback before starting a retry");
        var tuning = fix.methods.stream().filter(method -> method.name.equals("tune")).findFirst().orElseThrow();
        var tuningOuterCalls = new ArrayList<String>();
        for (var instruction : tuning.instructions) if (instruction instanceof MethodInsnNode call) tuningOuterCalls.add(call.name);
        check(tuningOuterCalls.contains("retry") && tuningOuterCalls.contains("backoffTicks"), "Expired requests actually enter the retry path with a cooldown");
        System.out.println("Notebot interaction and target checks passed: " + checks);
    }
}
