package dev.tanghulu.addon.modules;

import dev.tanghulu.addon.BaseModule;
import dev.tanghulu.addon.mixin.LivingEntityRendererInvoker;
import dev.tanghulu.addon.util.PlayerSkeletonGeometry;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class PlayerSkeleton extends Module {
    private static final Color RED = new Color(255, 0, 0, 255);
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("显示距离")
        .description("显示此距离内已加载玩家的骨架。")
        .defaultValue(128d)
        .range(1d, 512d)
        .sliderRange(1d, 256d)
        .build());

    private final Setting<Boolean> showSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("显示自己")
        .description("第三人称下也显示自己的骨架，第一人称下始终隐藏镜头所附着玩家的骨架。")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> showInvisible = sgGeneral.add(new BoolSetting.Builder()
        .name("显示隐身玩家")
        .description("也显示处于隐身状态的玩家。")
        .defaultValue(false)
        .build());

    // Private models/state: never modify a model or state queued for vanilla rendering.
    private final PlayerEntityModel normalModel = createModel(false);
    private final PlayerEntityModel slimModel = createModel(true);
    private PlayerEntityRenderState state;

    public PlayerSkeleton() {
        super(BaseModule.CATEGORY, "玩家骨架显示", "用红线连接玩家的头部、躯干、手臂和双腿，并跟随模型姿势。");
    }

    private static PlayerEntityModel createModel(boolean slim) {
        return new PlayerEntityModel(PlayerEntityModel.getTexturedModelData(Dilation.NONE, slim)
            .getRoot().createPart(64, 64), slim);
    }

    @Override
    public void onDeactivate() {
        state = null;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        // Meteor attaches the source entity to render states; release its world on disconnect.
        state = null;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        double maxDistanceSquared = range.get() * range.get();

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            if (player == mc.player && !showSelf.get()) continue;
            if (player == mc.getCameraEntity() && mc.options.getPerspective().isFirstPerson()) continue;
            if (player.isInvisible() && !showInvisible.get()) continue;
            if (player.squaredDistanceTo(camera) > maxDistanceSquared) continue;
            renderPlayer(event, player);
        }
    }

    private void renderPlayer(Render3DEvent event, AbstractClientPlayerEntity player) {
        if (state == null) state = new PlayerEntityRenderState();
        PlayerEntityRenderer<AbstractClientPlayerEntity> renderer = mc.getEntityRenderDispatcher().getPlayerRenderer(player);
        renderer.updateRenderState(player, state, event.tickDelta);
        boolean slim = state.skinTextures.model() == PlayerSkinType.SLIM;
        PlayerEntityModel model = slim ? slimModel : normalModel;
        model.setAngles(state);

        // Same transform order as LivingEntityRenderer.render, without a world-space float translation.
        MatrixStack matrices = new MatrixStack();
        Direction sleepingDirection = state.sleepingDirection;
        if (state.isInPose(EntityPose.SLEEPING) && sleepingDirection != null) {
            float offset = state.standingEyeHeight - 0.1f;
            matrices.translate(-sleepingDirection.getOffsetX() * offset, 0, -sleepingDirection.getOffsetZ() * offset);
        }
        matrices.scale(state.baseScale, state.baseScale, state.baseScale);
        LivingEntityRendererInvoker transforms = (LivingEntityRendererInvoker) renderer;
        transforms.tanghulu$setupTransforms(state, matrices, state.bodyYaw, state.baseScale);
        matrices.scale(-1, -1, 1);
        transforms.tanghulu$scale(state, matrices);
        matrices.translate(0, -1.501f, 0);

        Vec3d offset = renderer.getPositionOffset(state);
        double x = state.x + offset.x;
        double y = state.y + offset.y;
        double z = state.z + offset.z;
        PlayerSkeletonGeometry.draw(model, matrices, slim, (from, to) -> {
            if (from.distanceSquared(to) < 1e-10f) return;
            // The non-depth-tested Meteor renderer keeps lines inside the model visible.
            event.renderer.line(x + from.x, y + from.y, z + from.z,
                x + to.x, y + to.y, z + to.z, RED);
        });
    }
}
