package dev.tanghulu.addon.modules;

import dev.tanghulu.addon.BaseModule;
import dev.tanghulu.addon.util.notebot.TuneConfirmation;
import dev.tanghulu.addon.util.notebot.NotebotInteraction;
import dev.tanghulu.addon.util.notebot.PlaybackCursor;
import dev.tanghulu.addon.util.notebot.TuneRetryPolicy;
import dev.tanghulu.addon.util.notebot.SongToolsScreen;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.meteor.ActiveModulesChangedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.Notebot;
import meteordevelopment.meteorclient.utils.notebot.song.Note;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in tuning and playback companion to Meteor's 1.21 Notebot API. */
public final class NotebotFix extends BaseModule {
    public enum Result { Waiting, Complete, Stopped }

    private final Setting<Integer> interval = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("调音间隔").description("相邻右键至少间隔的游戏刻数；同时仍需等待服务器确认。")
        .defaultValue(2).min(1).sliderRange(1, 20).build());
    private final Setting<Boolean> simultaneousChords = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("同刻演奏和弦").description("同一歌曲刻的音符一起敲击，保持歌曲速度；关闭后恢复逐个旋转敲击。部分服务器可能拒绝同刻多目标交互。")
        .defaultValue(true).build());
    private final Setting<Integer> confirmationTimeout = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("服务器确认超时").description("等待音高更新的刻数；超时后重新检查音高，并按自动重试设置处理。")
        .defaultValue(60).min(10).sliderRange(20, 1200).build());
    private final Setting<Boolean> keepWaiting = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("超时自动重试").description("音高未变化时延迟重试；收到迟到的音高更新会重新计算差值，调音后再次核对。")
        .defaultValue(true).build());
    private final Setting<Integer> retryLimit = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("连续重试上限").description("同一音符盒音高不变时允许的连续重试次数；音高变化后重新计数。")
        .defaultValue(6).min(1).max(20).sliderRange(1, 10).build());

    private record Update(ClientWorld world, BlockPos pos, BlockState state) {}
    private final ConcurrentLinkedQueue<Update> updates = new ConcurrentLinkedQueue<>();
    private volatile Set<BlockPos> watched = Set.of();
    private volatile Set<BlockPos> playWatched = Set.of();
    private final AtomicInteger playEvents = new AtomicInteger();
    private final PlaybackCursor<BlockPos> playback = new PlaybackCursor<>();
    private final Map<BlockPos, Integer> confirmed = new HashMap<>();
    private final Map<BlockPos, Integer> clicks = new HashMap<>();
    private final TuneConfirmation confirmation = new TuneConfirmation();
    private final TuneRetryPolicy<BlockPos> retries = new TuneRetryPolicy<>();
    private ClientWorld sessionWorld;
    private BlockPos pending;
    private BlockPos retryTarget;
    private int verifyUntil = -1;
    private int clock, nextClick, generation, rotationWait;
    private boolean rotating;
    private boolean playbackRotating, noSoundWarned, missingNotesWarned;
    private int playClock, playSent, playbackRotationWait, firstPlayClock = -1;
    private WLabel statusLabel;
    private String status = "先开启修复，再在 Meteor 音符机器人中选歌";

    public NotebotFix() {
        super("音符机器人修复", "逐次确认调音、耐受高延迟、同刻演奏和弦；附歌曲材料预览。");
    }

    public static NotebotFix active() {
        Modules modules = Modules.get();
        if (modules == null) return null;
        NotebotFix fix = modules.get(NotebotFix.class);
        return fix != null && fix.isActive() ? fix : null;
    }

    @Override public void onActivate() {
        reset();
        Notebot bot = Modules.get().get(Notebot.class);
        if (bot != null && bot.isActive()) bot.stop();
        info("修复已开启，请在 Meteor 音符机器人中重新选歌。调音时保持空主手、不潜行，并站在可直接点击音符盒的位置。");
    }

    @Override public void onDeactivate() {
        // Invalidate scheduled rotation callbacks before reverting to Meteor's normal tuner.
        boolean wasTuning = sessionWorld != null;
        reset();
        if (wasTuning) {
            Notebot bot = Modules.get().get(Notebot.class);
            if (bot != null && bot.isActive()) bot.stop();
        }
    }

    public void reset() {
        generation++;
        sessionWorld = null;
        pending = null;
        rotating = false;
        clock = nextClick = rotationWait = 0;
        confirmed.clear();
        clicks.clear();
        watched = Set.of();
        updates.clear();
        confirmation.reset();
        retries.reset();
        retryTarget = null;
        verifyUntil = -1;
        playbackRotating = noSoundWarned = missingNotesWarned = false;
        playClock = playSent = playbackRotationWait = 0;
        firstPlayClock = -1;
        playback.reset();
        playWatched = Set.of();
        playEvents.set(0);
        status = "等待 Meteor 音符机器人开始调音";
    }

    @EventHandler private void onGameLeft(GameLeftEvent event) {
        reset();
        Notebot bot = Modules.get().get(Notebot.class);
        if (bot != null && bot.isActive()) bot.stop();
    }

    @EventHandler private void onPacket(PacketEvent.Receive event) {
        if (event.isCancelled()) return;
        if (event.packet instanceof BlockEventS2CPacket packet && packet.getBlock() == Blocks.NOTE_BLOCK
            && playWatched.contains(packet.getPos())) playEvents.incrementAndGet();
        if (watched.isEmpty()) return;
        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            if (watched.contains(packet.getPos())) updates.add(new Update(mc.world, packet.getPos().toImmutable(), packet.getState()));
        } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket packet) {
            ClientWorld world = mc.world;
            packet.visitUpdates((pos, state) -> {
                if (watched.contains(pos)) updates.add(new Update(world, pos.toImmutable(), state));
            });
        }
    }

    /** Raycast to a real surface. No through-wall hits, extra server reach margin or fixed DOWN face. */
    public BlockHitResult hit(BlockPos pos) {
        if (mc.world == null || mc.player == null || !mc.world.getBlockState(pos).isOf(Blocks.NOTE_BLOCK)) return null;
        return NotebotInteraction.findHit(mc.world, mc.player.getEyePos(), pos,
            mc.player.getBlockInteractionRange(), ShapeContext.of(mc.player));
    }

    public Result tune(Notebot bot, Map<Note, BlockPos> positions, Map<BlockPos, Integer> remaining, List<BlockPos> clicked) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return stop(bot, "世界或连接已不可用");
        if (sessionWorld == null) {
            sessionWorld = mc.world;
            updates.clear();
        }
        if (sessionWorld != mc.world) return stop(bot, "世界已切换，请重新选歌");
        if (mc.player.getAbilities().creativeMode) return stop(bot, "请切换到生存模式后重新选歌");
        if (mc.player.isSpectator() || mc.player.isSneaking() || !mc.player.getMainHandStack().isEmpty()
            || mc.player.isUsingItem() || mc.getCameraEntity() != mc.player) {
            return stop(bot, "调音需要空主手、不潜行、不使用物品，并使用玩家视角；调整后重新选歌");
        }
        clock++;
        Map<BlockPos, Integer> targets = new HashMap<>();
        positions.forEach((note, pos) -> targets.put(pos, note.getNoteLevel()));
        watched = Set.copyOf(targets.keySet());
        Set<BlockPos> updatedThisTick = new HashSet<>();
        Update update;
        while ((update = updates.poll()) != null) {
            if (update.world != sessionWorld || !targets.containsKey(update.pos)) continue;
            if (!update.state.isOf(Blocks.NOTE_BLOCK)) return stop(bot, "音符盒已被移除：" + update.pos.toShortString());
            int level = update.state.get(NoteBlock.NOTE);
            Integer previous = confirmed.get(update.pos);
            if (previous != null && previous != level) retries.progress(update.pos);
            confirmed.put(update.pos, level);
            updatedThisTick.add(update.pos);
        }
        if (rotating && ++rotationWait >= confirmationTimeout.get()) return stop(bot, "旋转回调未执行，请检查视角与其他旋转模块");

        remaining.clear();
        for (var entry : targets.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = mc.world.getBlockState(pos);
            if (!state.isOf(Blocks.NOTE_BLOCK)) return stop(bot, "音符盒已被移除：" + pos.toShortString());
            // NoteBlock.onUse does not predict the pitch on the client. The live world also
            // catches chunk refreshes and packet/world timing that the event queue may miss.
            int level = updatedThisTick.contains(pos) ? confirmed.get(pos) : state.get(NoteBlock.NOTE);
            Integer previous = confirmed.put(pos, level);
            if (previous != null && previous != level) retries.progress(pos);
            if (pos.equals(pending) && confirmation.observe(level)) {
                pending = null;
                retries.progress(pos);
                nextClick = Math.max(nextClick, clock + Math.min(20, Math.max(interval.get(), confirmation.elapsedTicks() / 2)));
            }
            int count = TuneConfirmation.remaining(level, entry.getValue());
            if (count != 0) remaining.put(pos, count);
        }
        if (confirmation.tick()) {
            BlockPos stalled = pending;
            if (!keepWaiting.get()) return stop(bot, "服务器未确认调音：" + stalled.toShortString() + "。可开启超时自动重试");
            int attempt = retries.schedule(stalled, retryLimit.get());
            if (attempt < 0) return stop(bot, "音符盒 " + stalled.toShortString() + " 连续 " + retryLimit.get()
                + " 次重试后音高仍未改变，请检查手动右键、权限或遮挡。不会继续无限补发");
            confirmation.retry();
            pending = null;
            retryTarget = stalled;
            nextClick = clock + TuneRetryPolicy.backoffTicks(attempt, interval.get());
            verifyUntil = -1;
            status = "音高未变化，自动重试 " + attempt + "/" + retryLimit.get() + "：" + stalled.toShortString();
            warning("音符盒 %s 超时，已重新核对音高；稍后自动重试（%d/%d）。", stalled.toShortString(), attempt, retryLimit.get());
        }
        if (confirmation.waiting() || rotating) {
            status = "等待服务器确认音高：" + confirmation.elapsedTicks() + " 刻";
            return Result.Waiting;
        }
        if (remaining.isEmpty()) {
            if (verifyUntil < 0) verifyUntil = clock + (retries.total() > 0 ? Math.max(20, Math.min(200, confirmationTimeout.get())) : 20);
            if (clock < verifyUntil) {
                status = "等待迟到更新并复核全部音高：剩余 " + (verifyUntil - clock) + " 刻";
                return Result.Waiting;
            }
            watched = Set.of();
            updates.clear();
            status = "全部音符盒已确认，准备兼容演奏";
            return Result.Complete;
        }
        verifyUntil = -1;
        if (clock < nextClick) return Result.Waiting;
        BlockPos pos = retryTarget != null && remaining.containsKey(retryTarget) ? retryTarget : remaining.keySet().iterator().next();
        BlockHitResult target = hit(pos);
        if (target == null) return stop(bot, "无法直接点击音符盒：" + pos.toShortString() + "。请靠近或移除遮挡后重新选歌");
        if (clicks.getOrDefault(pos, 0) >= 25 * (retryLimit.get() + 2)) return stop(bot, "音高反复变化：" + pos.toShortString() + "。请检查是否有其他玩家或插件持续修改音高");

        int token = generation;
        rotating = true;
        rotationWait = 0;
        Rotations.rotate(Rotations.getYaw(target.getPos()), Rotations.getPitch(target.getPos()), 100, () -> {
            if (token != generation || !isActive() || !bot.isActive() || sessionWorld != mc.world) return;
            rotating = false;
            BlockHitResult current = hit(pos);
            if (current == null || current.getSide() != target.getSide()
                || current.getPos().squaredDistanceTo(target.getPos()) > 0.0001) {
                stop(bot, "调音前位置或遮挡发生变化，请站稳后重新选歌");
                return;
            }
            int actualLevel = mc.world.getBlockState(pos).get(NoteBlock.NOTE);
            if (actualLevel != confirmed.get(pos)) {
                // A late reply arrived while the rotation was queued. Recompute, don't over-tune.
                confirmed.put(pos, actualLevel);
                retries.progress(pos);
                retryTarget = null;
                nextClick = clock + interval.get();
                return;
            }
            // interactBlock synchronizes the held slot and allocates a vanilla prediction sequence.
            pending = pos;
            confirmation.begin(confirmed.get(pos), confirmationTimeout.get());
            retryTarget = null;
            clicks.merge(pos, 1, Integer::sum);
            nextClick = clock + Math.max(interval.get(), bot.tickDelay.get());
            var result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, current);
            if (result.isAccepted() && bot.swingArm.get()) mc.player.swingHand(Hand.MAIN_HAND);
            clicked.add(pos);
        });
        status = "正在调音，剩余 " + remaining.size() + " 个音符盒";
        return Result.Waiting;
    }

    /** Dispatch a chord in one callback by default, retaining sequential mode for strict servers. */
    public void playTick(Notebot bot, int songTick, Collection<Note> notes, Map<Note, BlockPos> positions,
                         List<BlockPos> clicked, Runnable advance) {
        if (mc.world == null || mc.player == null || mc.interactionManager == null) { stop(bot, "演奏时连接不可用"); return; }
        if (sessionWorld == null) sessionWorld = mc.world;
        if (sessionWorld != mc.world) { stop(bot, "世界已切换，请重新选歌"); return; }
        if (mc.player.getAbilities().creativeMode || mc.player.isSpectator() || !mc.player.getMainHandStack().isEmpty()
            || mc.player.isUsingItem() || mc.getCameraEntity() != mc.player) {
            stop(bot, "演奏需要生存模式、空主手、不使用物品，并使用玩家视角"); return;
        }
        playClock++;
        playWatched = Set.copyOf(positions.values());
        if (playSent >= 20 && firstPlayClock >= 0 && playClock - firstPlayClock >= 100
            && playEvents.get() == 0 && !noSoundWarned) {
            noSoundWarned = true;
            warning("已发送演奏敲击，但尚未收到服务器音符事件。请保留本次现象反馈；客户端不会用本地音效伪装服务器演奏。");
        }
        if (playbackRotating) {
            if (++playbackRotationWait >= Math.max(200, confirmationTimeout.get())) stop(bot, "演奏旋转未完成，请检查其他旋转模块");
            return;
        }
        var chord = new ArrayList<BlockPos>();
        for (Note note : notes) {
            BlockPos pos = positions.get(note);
            if (pos != null) chord.add(pos);
            else if (!missingNotesWarned) {
                missingNotesWarned = true;
                warning("部分音符没有对应音符盒，将跳过这些音符，请检查音符盒数量和乐器设置。");
            }
        }
        playback.prepare(songTick, chord, bot.polyphonic.get());
        if (playback.isEmpty()) { advance.run(); return; }
        BlockPos pos = playback.peek();
        BlockHitResult target = hit(pos);
        if (target == null) { stop(bot, "演奏音符盒不可达或有遮挡：" + pos.toShortString()); return; }
        if (!mc.world.getBlockState(pos.up()).isAir()) { stop(bot, "音符盒上方被遮挡：" + pos.toShortString()); return; }
        int token = generation;
        playbackRotating = true;
        playbackRotationWait = 0;
        Rotations.rotate(Rotations.getYaw(target.getPos()), Rotations.getPitch(target.getPos()), 100, () -> {
            if (token != generation || !isActive() || !bot.isActive() || sessionWorld != mc.world) return;
            playbackRotating = false;
            do {
            BlockPos next = playback.peek();
            BlockHitResult current = hit(next);
            if (current == null || (next.equals(pos) && (current.getSide() != target.getSide()
                || current.getPos().squaredDistanceTo(target.getPos()) > 0.0001))) {
                stop(bot, "演奏前位置或遮挡发生变化，请站稳后重新选歌"); return;
            }
            if (!mc.world.getBlockState(next.up()).isAir()) {
                stop(bot, "音符盒上方被遮挡：" + next.toShortString()); return;
            }
            if (mc.player.getAbilities().creativeMode || mc.player.isSpectator() || !mc.player.getMainHandStack().isEmpty()
                || mc.player.isUsingItem() || mc.getCameraEntity() != mc.player
                || mc.world.getBlockState(next).calcBlockBreakingDelta(mc.player, mc.world, next) >= 1) {
                stop(bot, "当前状态可能瞬间破坏音符盒，请空手并移除过强的挖掘加速效果"); return;
            }
            // One tap: release any prior mining, START through vanilla, swing, then ABORT.
            // No breaking-progress updates or STOP_DESTROY packets are sent by the player.
            mc.interactionManager.cancelBlockBreaking();
            boolean accepted = mc.interactionManager.attackBlock(next, current.getSide());
            try {
                if (accepted) mc.player.swingHand(Hand.MAIN_HAND);
            } finally {
                mc.interactionManager.cancelBlockBreaking();
            }
            if (!accepted) { stop(bot, "原版交互管理器拒绝敲击：" + next.toShortString()); return; }
            if (token != generation) return;
            clicked.add(next);
            playback.complete();
            if (firstPlayClock < 0) firstPlayClock = playClock;
            playSent++;
            status = "兼容演奏：已敲击 " + playSent + " 次，收到服务器音符事件 " + playEvents.get() + " 次";
            } while (simultaneousChords.get() && !playback.isEmpty());
            if (playback.isEmpty()) advance.run();
        });
    }

    public void pausePlayback() {
        generation++;
        playbackRotating = false;
        playbackRotationWait = 0;
    }

    @EventHandler private void onTickPost(TickEvent.Post event) {
        if (statusLabel != null) statusLabel.set(status);
    }

    @EventHandler private void onActiveModulesChanged(ActiveModulesChangedEvent event) {
        Notebot bot = Modules.get().get(Notebot.class);
        if (bot != null && !bot.isActive()) notebotDisabled();
    }

    /** Called when Meteor is switched off so no pending interaction can escape later. */
    public void notebotDisabled() { reset(); }

    private Result stop(Notebot bot, String reason) {
        bot.stop();
        status = reason;
        error(reason);
        return Result.Stopped;
    }

    @Override public String getInfoString() { return confirmation.waiting() ? "等待确认" : playSent > 0 ? "演奏 " + playSent : "修复开启"; }

    @Override public WWidget getWidget(GuiTheme theme) {
        var list = theme.verticalList();
        list.add(theme.label("先开启本模块，再到 Meteor 音符机器人中选歌。"));
        list.add(theme.label("调音与演奏请空主手；调音不潜行，音符盒需在可达范围内。"));
        list.add(theme.label("默认同刻演奏和弦；服务器不接受时可关闭此选项。"));
        list.add(theme.label("调音超时会核对音高并自动重试，完成后复核迟到更新。"));
        statusLabel = list.add(theme.label(status)).widget();
        list.add(theme.button("打开 Meteor 音符机器人")).expandX().widget().action = () ->
            mc.setScreen(theme.moduleScreen(Modules.get().get(Notebot.class)));
        list.add(theme.button("歌曲材料预览")).expandX().widget().action = () ->
            mc.setScreen(new SongToolsScreen(theme));
        return list;
    }
}
