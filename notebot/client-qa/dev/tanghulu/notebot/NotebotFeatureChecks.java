package dev.tanghulu.notebot;

import dev.tanghulu.addon.util.notebot.*;
import meteordevelopment.meteorclient.utils.notebot.song.Note;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.NoteBlockInstrument;
import java.util.*;

public final class NotebotFeatureChecks {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
        var rows = SongMaterials.rows(List.of(new Note(NoteBlockInstrument.HARP, 5), new Note(NoteBlockInstrument.HARP, 5),
            new Note(NoteBlockInstrument.HARP, 17), new Note(NoteBlockInstrument.BASS, 5)));
        check(rows.size() == 2 && rows.stream().mapToInt(SongMaterials.Row::count).sum() == 3, "Count unique pitches per instrument");
        check(SongMaterials.material(NoteBlockInstrument.HARP).block() == Blocks.DIRT, "Harp needs a neutral substrate");
        check(SongMaterials.material(NoteBlockInstrument.BASS).block() == Blocks.OAK_PLANKS, "Bass uses wood");
        check(SongMaterials.material(NoteBlockInstrument.GUITAR).block() == Blocks.WHITE_WOOL, "Guitar uses wool");
        check(SongMaterials.material(NoteBlockInstrument.CREEPER).above(), "Heads belong above, not below");
        check(SongMaterials.rows(List.of(new Note(null, 5), new Note(null, 5))).getFirst().count() == 1, "Ignore-instrument mode deduplicates pitches");
        check(SongMaterials.translateWarning("Missing note: HARP, 17").equals("缺少音符：竖琴，音高 17"), "Translate missing pitch and instrument");
        check(SongMaterials.translateWarning("5 missing notes!").startsWith("共缺少 5 个音符"), "Translate missing total");
        check(SongMaterials.translateWarning("other").equals("other"), "Preserve unrelated messages");

        System.out.println("Notebot material checks passed: " + checks);
    }
}
