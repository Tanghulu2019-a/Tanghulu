package dev.tanghulu.addon.util.notebot;

import meteordevelopment.meteorclient.utils.notebot.song.Note;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.NoteBlockInstrument;

import java.util.*;

/** One physical note block for each distinct, already mapped instrument/pitch. */
public final class SongMaterials {
    private SongMaterials() {}
    public record Material(String name, Block block, boolean above) {}
    public record Row(Material material, List<Integer> pitches) { public int count() { return pitches.size(); } }

    public static Material material(NoteBlockInstrument instrument) {
        if (instrument == null) return new Material("任意音色（忽略音色模式）", null, false);
        return switch (instrument.name()) {
            case "HARP" -> new Material("竖琴", Blocks.DIRT, false);
            case "BASS" -> new Material("贝斯", Blocks.OAK_PLANKS, false);
            case "BASEDRUM" -> new Material("底鼓", Blocks.STONE, false);
            case "SNARE" -> new Material("小军鼓", Blocks.SAND, false);
            case "HAT" -> new Material("踩镲", Blocks.GLASS, false);
            case "GUITAR" -> new Material("吉他", Blocks.WHITE_WOOL, false);
            case "FLUTE" -> new Material("长笛", Blocks.CLAY, false);
            case "BELL" -> new Material("铃铛", Blocks.GOLD_BLOCK, false);
            case "CHIME" -> new Material("风铃", Blocks.PACKED_ICE, false);
            case "XYLOPHONE" -> new Material("木琴", Blocks.BONE_BLOCK, false);
            case "IRON_XYLOPHONE" -> new Material("铁木琴", Blocks.IRON_BLOCK, false);
            case "COW_BELL" -> new Material("牛铃", Blocks.SOUL_SAND, false);
            case "DIDGERIDOO" -> new Material("迪吉里杜管", Blocks.PUMPKIN, false);
            case "BIT" -> new Material("芯片音", Blocks.EMERALD_BLOCK, false);
            case "BANJO" -> new Material("班卓琴", Blocks.HAY_BLOCK, false);
            case "PLING" -> new Material("电钢琴", Blocks.GLOWSTONE, false);
            case "ZOMBIE" -> new Material("僵尸", Blocks.ZOMBIE_HEAD, true);
            case "SKELETON" -> new Material("骷髅", Blocks.SKELETON_SKULL, true);
            case "CREEPER" -> new Material("苦力怕", Blocks.CREEPER_HEAD, true);
            case "DRAGON" -> new Material("末影龙", Blocks.DRAGON_HEAD, true);
            case "WITHER_SKELETON" -> new Material("凋灵骷髅", Blocks.WITHER_SKELETON_SKULL, true);
            case "PIGLIN" -> new Material("猪灵", Blocks.PIGLIN_HEAD, true);
            default -> new Material("自定义头颅音色", Blocks.PLAYER_HEAD, true);
        };
    }

    public static List<Row> rows(Collection<Note> notes) {
        Map<String, SortedSet<Integer>> pitches = new TreeMap<>();
        Map<String, Material> materials = new HashMap<>();
        for (Note note : notes) {
            String key = note.getInstrument() == null ? "ANY" : note.getInstrument().name();
            pitches.computeIfAbsent(key, k -> new TreeSet<>()).add(note.getNoteLevel());
            materials.put(key, material(note.getInstrument()));
        }
        List<Row> result = new ArrayList<>();
        pitches.forEach((key, values) -> result.add(new Row(materials.get(key), List.copyOf(values))));
        return List.copyOf(result);
    }

    public static String translateWarning(String message) {
        if (message.startsWith("Missing note: ")) {
            String[] parts = message.substring(14).split(", ");
            if (parts.length == 2) {
                NoteBlockInstrument instrument = null;
                try { instrument = NoteBlockInstrument.valueOf(parts[0]); } catch (IllegalArgumentException ignored) {}
                return "缺少音符：" + material(instrument).name() + "，音高 " + parts[1];
            }
        }
        if (message.endsWith(" missing notes!")) return "共缺少 " + message.substring(0, message.length() - 15)
            + " 个音符，请按材料预览补充对应音色的音符盒。";
        return message;
    }
}
