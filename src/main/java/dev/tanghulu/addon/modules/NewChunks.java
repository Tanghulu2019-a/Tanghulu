// Adapted from Numby Hack v2.6.24 (GPL-3.0); localized and integrated by Tanghulu, 2026-10-24.
package dev.tanghulu.addon.modules;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import dev.tanghulu.addon.BaseModule;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

/**
 * modified by cqb13
 * Ported from:
 * https://github.com/BleachDrinker420/BleachHack/blob/master/BleachHack-Fabric-1.16/src/main/java/bleach/hack/module/mods/NewChunks.java
 */
public class NewChunks extends Module {

    private final SettingGroup sgGeneral = settings.createGroup("常规设置");
    private final SettingGroup sgRender = settings.createGroup("渲染设置");

    // general

    private final Setting<Boolean> remove = sgGeneral.add(new BoolSetting.Builder()
            .name("关闭时清空缓存")
            .description("关闭模块时清空区块标记；切换世界或维度时始终清空。")
            .defaultValue(true)
            .build());

    // render
    public final Setting<Integer> yOffset = sgRender.add(new IntSetting.Builder()
            .name("显示高度")
            .description("区块标记底面的世界坐标高度。")
            .defaultValue(0)
            .min(-64)
            .sliderRange(-64, 320)
            .build());

    public final Setting<Integer> boxHeight = sgRender.add(new IntSetting.Builder()
            .name("标记高度")
            .description("区块标记的高度，设为零时显示平面。")
            .defaultValue(0)
            .min(0)
            .sliderRange(0, 100)
            .build());

    private final Setting<RenderMode> shapeMode = sgRender.add(new EnumSetting.Builder<RenderMode>()
            .name("渲染模式")
            .description("选择填充、边框或两者同时显示。")
            .defaultValue(RenderMode.Both)
            .build());

    private final Setting<SettingColor> newChunksSideColor = sgRender.add(new ColorSetting.Builder()
            .name("新区块填充颜色")
            .description("疑似新生成区块的颜色；流体特征检测可能误判。")
            .defaultValue(new SettingColor(255, 0, 0, 75))
            .visible(() -> shapeMode.get() == RenderMode.Sides || shapeMode.get() == RenderMode.Both)
            .build());

    private final Setting<SettingColor> oldChunksSideColor = sgRender.add(new ColorSetting.Builder()
            .name("旧区块填充颜色")
            .description("疑似曾加载区块的颜色；流体特征检测可能误判。")
            .defaultValue(new SettingColor(0, 255, 0, 75))
            .visible(() -> shapeMode.get() == RenderMode.Sides || shapeMode.get() == RenderMode.Both)
            .build());

    private final Setting<SettingColor> newChunksLineColor = sgRender.add(new ColorSetting.Builder()
            .name("新区块边框颜色")
            .description("疑似新生成区块的颜色；流体特征检测可能误判。")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .visible(() -> shapeMode.get() == RenderMode.Lines || shapeMode.get() == RenderMode.Both)
            .build());

    private final Setting<SettingColor> oldChunksLineColor = sgRender.add(new ColorSetting.Builder()
            .name("旧区块边框颜色")
            .description("疑似曾加载区块的颜色；流体特征检测可能误判。")
            .defaultValue(new SettingColor(0, 255, 0, 255))
            .visible(() -> shapeMode.get() == RenderMode.Lines || shapeMode.get() == RenderMode.Both)
            .build());

    public enum RenderMode {
        Lines("仅边框", ShapeMode.Lines), Sides("仅填充", ShapeMode.Sides), Both("填充与边框", ShapeMode.Both);
        private final String label;
        private final ShapeMode shape;
        RenderMode(String label, ShapeMode shape) { this.label = label; this.shape = shape; }
        @Override public String toString() { return label; }
    }

    private net.minecraft.client.world.ClientWorld cachedWorld;
    private volatile long generation;

    private void updateWorld() {
        if (cachedWorld != mc.world) {
            newChunks.clear();
            oldChunks.clear();
            cachedWorld = mc.world;
            generation++;
        }
    }

    @Override public void onActivate() { updateWorld(); }

    @EventHandler
    private void onTick(meteordevelopment.meteorclient.events.world.TickEvent.Pre event) { updateWorld(); }

    @EventHandler
    private void onLeave(meteordevelopment.meteorclient.events.game.GameLeftEvent event) {
        newChunks.clear();
        oldChunks.clear();
        cachedWorld = null;
        generation++;
    }
    private final Set<ChunkPos> newChunks = Collections.synchronizedSet(new HashSet<>());
    private final Set<ChunkPos> oldChunks = Collections.synchronizedSet(new HashSet<>());
    private static final Direction[] searchDirs = new Direction[] { Direction.EAST, Direction.NORTH, Direction.WEST,
            Direction.SOUTH, Direction.UP };

    public NewChunks() {
        super(BaseModule.CATEGORY, "新区块探测", "【测试版】根据流体更新特征标记疑似新旧区块，结果仅供参考。", "New Chunks", "new-chunks");
    }

    @Override
    public void onDeactivate() {
        generation++;
        if (remove.get()) {
            newChunks.clear();
            oldChunks.clear();
        }
        super.onDeactivate();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        updateWorld();
        if (mc.world == null || mc.getCameraEntity() == null) return;
        if (newChunksLineColor.get().a > 5 || newChunksSideColor.get().a > 5) {
            synchronized (newChunks) {
                for (ChunkPos c : newChunks) {
                    if (mc.getCameraEntity().getBlockPos().isWithinDistance(c.getStartPos(), 1024)) {
                        render(new Box(
                                new Vec3d(c.getStartPos().getX(), c.getStartPos().getY() + yOffset.get(),
                                        c.getStartPos().getZ()),
                                new Vec3d(c.getStartPos().getX() + 16, c.getStartPos().getY() + yOffset.get(),
                                        c.getStartPos().getZ() + 16)),
                                newChunksSideColor.get(), newChunksLineColor.get(), shapeMode.get().shape, event);
                    }
                }
            }
        }

        if (oldChunksLineColor.get().a > 5 || oldChunksSideColor.get().a > 5) {
            synchronized (oldChunks) {
                for (ChunkPos c : oldChunks) {
                    if (mc.getCameraEntity().getBlockPos().isWithinDistance(c.getStartPos(), 1024)) {
                        render(new Box(
                                new Vec3d(c.getStartPos().getX(), c.getStartPos().getY() + yOffset.get(),
                                        c.getStartPos().getZ()),
                                new Vec3d(c.getStartPos().getX() + 16, c.getStartPos().getY() + yOffset.get(),
                                        c.getStartPos().getZ() + 16)),
                                oldChunksSideColor.get(), oldChunksLineColor.get(), shapeMode.get().shape, event);
                    }
                }
            }
        }
    }

    private void render(Box box, Color sides, Color lines, ShapeMode shapeMode, Render3DEvent event) {
        event.renderer.box(
                box.minX, box.minY, box.minZ, box.maxX, box.maxY + boxHeight.get(), box.maxZ, sides, lines, shapeMode,
                0);
    }

    @EventHandler
    private void onReadPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof ChunkDataS2CPacket || event.packet instanceof BlockUpdateS2CPacket
                || event.packet instanceof ChunkDeltaUpdateS2CPacket)) return;
        // Packet callbacks can run on Netty. Decode and inspect the world on the client thread.
        var world = mc.world;
        var packet = event.packet;
        long receivedGeneration = generation;
        mc.execute(() -> {
            if (!isActive() || world == null || world != mc.world || receivedGeneration != generation) return;
            updateWorld();
            readPacket(packet);
        });
    }

    private void readPacket(net.minecraft.network.packet.Packet<?> receivedPacket) {
        if (receivedPacket instanceof ChunkDeltaUpdateS2CPacket) {
            ChunkDeltaUpdateS2CPacket packet = (ChunkDeltaUpdateS2CPacket) receivedPacket;

            packet.visitUpdates((pos, state) -> {
                if (!state.getFluidState().isEmpty() && !state.getFluidState().isStill()) {
                    ChunkPos chunkPos = new ChunkPos(pos);

                    for (Direction dir : searchDirs) {
                        if (mc.world.getBlockState(pos.offset(dir)).getFluidState().isStill()
                                && !oldChunks.contains(chunkPos)) {
                            newChunks.add(chunkPos);
                            return;
                        }
                    }
                }
            });
        }

        else if (receivedPacket instanceof BlockUpdateS2CPacket) {
            BlockUpdateS2CPacket packet = (BlockUpdateS2CPacket) receivedPacket;

            if (!packet.getState().getFluidState().isEmpty() && !packet.getState().getFluidState().isStill()) {
                ChunkPos chunkPos = new ChunkPos(packet.getPos());

                for (Direction dir : searchDirs) {
                    if (mc.world.getBlockState(packet.getPos().offset(dir)).getFluidState().isStill()
                            && !oldChunks.contains(chunkPos)) {
                        newChunks.add(chunkPos);
                        return;
                    }
                }
            }
        }

        else if (receivedPacket instanceof ChunkDataS2CPacket && mc.world != null) {
            ChunkDataS2CPacket packet = (ChunkDataS2CPacket) receivedPacket;

            ChunkPos pos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());

            if (!newChunks.contains(pos) && !oldChunks.contains(pos)
                    && mc.world.getChunkManager().getChunk(packet.getChunkX(), packet.getChunkZ()) == null) {
                WorldChunk chunk = new WorldChunk(mc.world, pos);
                try {
                    // Decode before classification; use the real heightmaps rather than fabricated data.
                    var data = packet.getChunkData().getSectionsDataBuf();
                    try {
                        chunk.loadFromPacket(data, packet.getChunkData().getHeightmap(),
                            packet.getChunkData().getBlockEntities(packet.getChunkX(), packet.getChunkZ()));
                    } finally {
                        data.release();
                    }
                } catch (IndexOutOfBoundsException e) {
                    return;
                }

                // Palette inspection avoids walking every block on the render thread.
                for (var section : chunk.getSectionArray()) {
                    if (!section.isEmpty() && section.hasAny(state -> {
                        var fluid = state.getFluidState();
                        return !fluid.isEmpty() && !fluid.isStill();
                    })) {
                        oldChunks.add(pos);
                        return;
                    }
                }
            }
        }
    }
}
