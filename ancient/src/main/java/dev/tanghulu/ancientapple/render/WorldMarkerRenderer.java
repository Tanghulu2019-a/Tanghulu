package dev.tanghulu.ancientapple.render;

import dev.tanghulu.ancientapple.core.ChestTarget;
import dev.tanghulu.ancientapple.core.MarkerSelection;
import dev.tanghulu.ancientapple.core.LootSelection;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.List;

/** Renders world markers; settings, prediction and persistence belong to the module. */
public final class WorldMarkerRenderer {
    private static final int MAX_MARKERS = 128;
    private static final Color BEAM_SIDE = new Color(255, 208, 64, 55);
    private static final Color BEAM_EDGE = new Color(255, 224, 128, 170);
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final ItemStack apple = Items.ENCHANTED_GOLDEN_APPLE.getDefaultStack();

    public void renderIcons(Render2DEvent event, Collection<ChestTarget> targets,
                            boolean showCompleted, boolean throughWalls, int distance, LootSelection selection) {
        if (!ready()) return;
        var context = event.drawContext;
        for (ChestTarget target : select(targets, showCompleted, throughWalls, distance, selection)) {
            Vector3d projected = new Vector3d(target.x + .5, target.y + 1.35, target.z + .5);
            if (!NametagUtils.to2D(projected, 1.0)) continue;
            NametagUtils.begin(projected, context);
            context.drawItem(selection.bookIcon(target) ? Items.ENCHANTED_BOOK.getDefaultStack() : apple, -8, -20);
            String text = selection.label(target);
            int color = target.status == ChestTarget.Status.CONFIRMED ? 0xffa0ffb0 : 0xffffe6a3;
            context.drawTextWithShadow(mc.textRenderer, text, -mc.textRenderer.getWidth(text) / 2, 0, color);
            NametagUtils.end(context);
        }
    }

    public void renderBeams(Render3DEvent event, Collection<ChestTarget> targets,
                            boolean showCompleted, boolean throughWalls, int distance, LootSelection selection) {
        if (!ready()) return;
        var renderer = throughWalls ? event.renderer : event.depthRenderer;
        for (ChestTarget target : select(targets, showCompleted, throughWalls, distance, selection)) {
            renderer.box(target.x + .38, target.y + 1, target.z + .38,
                target.x + .62, mc.world.getTopYInclusive() + 1, target.z + .62,
                BEAM_SIDE, BEAM_EDGE, ShapeMode.Both, 0);
        }
    }

    private List<ChestTarget> select(Collection<ChestTarget> targets, boolean showCompleted,
                                      boolean throughWalls, int distance, LootSelection selection) {
        return MarkerSelection.nearest(targets, showCompleted,
            mc.player.getX(), mc.player.getY(), mc.player.getZ(), distance, MAX_MARKERS,
            target -> selection.matches(target) && (throughWalls || visible(target)));
    }

    private boolean visible(ChestTarget target) {
        var hit = mc.world.raycast(new RaycastContext(mc.player.getEyePos(),
            new Vec3d(target.x + .5, target.y + .7, target.z + .5),
            RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
            && block.getBlockPos().equals(new BlockPos(target.x, target.y, target.z));
    }

    private boolean ready() {
        return mc.world != null && mc.player != null && mc.world.getRegistryKey() == World.OVERWORLD;
    }
}
