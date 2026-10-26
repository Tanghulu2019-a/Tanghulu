package dev.tanghulu.addon.mixin;

import dev.tanghulu.addon.modules.NotebotFix;
import dev.tanghulu.addon.util.notebot.SongMaterials;
import meteordevelopment.meteorclient.systems.modules.misc.Notebot;
import meteordevelopment.meteorclient.utils.notebot.song.Note;
import meteordevelopment.meteorclient.utils.notebot.song.Song;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/** Hooks for Meteor's 1.21 Notebot API; lifecycle cleanup uses Meteor's module event. */
@Mixin(value = Notebot.class, remap = false)
public abstract class NotebotMixin {
    @Redirect(method = "setupNoteblocksMap", at = @At(value = "INVOKE",
        target = "Lmeteordevelopment/meteorclient/systems/modules/misc/Notebot;warning(Ljava/lang/String;[Ljava/lang/Object;)V"))
    private void tanghulu$chineseMissingNotes(Notebot bot, String message, Object[] args) {
        bot.warning(SongMaterials.translateWarning(message), args);
    }

    @Shadow @Final private Map<Note, BlockPos> noteBlockPositions;
    @Shadow @Final private Map<BlockPos, Integer> tuneHits;
    @Shadow @Final private List<BlockPos> clickedBlocks;
    @Shadow private Notebot.Stage stage;
    @Shadow private boolean anyNoteblockTuned;
    @Shadow private Notebot.PlayingMode playingMode;
    @Shadow private boolean isPlaying;
    @Shadow private int currentTick;
    @Shadow private Song song;
    @Shadow public abstract void play();
    @Shadow public abstract void onSongEnd();
    @Shadow public abstract void updateStatus();

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void tanghulu$playAfterRotation(TickEvent.Pre event, CallbackInfo ci) {
        NotebotFix fix = NotebotFix.active();
        if (fix == null || stage != Notebot.Stage.Playing || playingMode != Notebot.PlayingMode.Noteblocks) return;
        ci.cancel();
        clickedBlocks.clear();
        if (!isPlaying) return;
        if (song == null || currentTick > song.getLastTick()) { onSongEnd(); return; }
        fix.playTick((Notebot) (Object) this, currentTick, song.getNotesMap().get(currentTick),
            noteBlockPositions, clickedBlocks, () -> { currentTick++; updateStatus(); });
        updateStatus();
    }

    @Inject(method = "pause", at = @At("HEAD"))
    private void tanghulu$pausePlayback(CallbackInfo ci) {
        NotebotFix fix = NotebotFix.active();
        if (fix != null && isPlaying) fix.pausePlayback();
    }

    @Inject(method = "tune", at = @At("HEAD"), cancellable = true)
    private void tanghulu$confirmedTuning(CallbackInfo ci) {
        NotebotFix fix = NotebotFix.active();
        if (fix == null) return;
        ci.cancel();
        if (fix.tune((Notebot) (Object) this, noteBlockPositions, tuneHits, clickedBlocks) == NotebotFix.Result.Complete) {
            anyNoteblockTuned = false;
            stage = Notebot.Stage.Playing;
            play();
        }
        updateStatus();
    }

    @Inject(method = "resetVariables", at = @At("HEAD"))
    private void tanghulu$invalidateTuning(CallbackInfo ci) {
        NotebotFix fix = NotebotFix.active();
        if (fix != null) fix.reset();
    }

    @Inject(method = "isValidScanSpot", at = @At("RETURN"), cancellable = true)
    private void tanghulu$scanReachable(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        NotebotFix fix = NotebotFix.active();
        if (fix != null && cir.getReturnValue() && fix.hit(pos) == null) cir.setReturnValue(false);
    }

}
