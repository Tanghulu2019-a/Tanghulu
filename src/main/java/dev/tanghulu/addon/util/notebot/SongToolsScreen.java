package dev.tanghulu.addon.util.notebot;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.Notebot;
import meteordevelopment.meteorclient.utils.notebot.NotebotUtils;
import meteordevelopment.meteorclient.utils.notebot.decoder.SongDecoders;
import meteordevelopment.meteorclient.utils.notebot.song.Note;
import meteordevelopment.meteorclient.utils.notebot.song.Song;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Choose a song and show only the materials needed to build it. */
public final class SongToolsScreen extends WindowScreen {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Path directory = MeteorClient.FOLDER.toPath().resolve("notebot").toAbsolutePath();
    private WLabel songLabel, statusLabel;
    private WVerticalList materials;
    private String selected = "未选择歌曲", status = "选择歌曲后，会自动显示所需材料。";
    private AtomicBoolean cancellation;
    private List<SongMaterials.Row> rows = List.of();

    public SongToolsScreen(GuiTheme theme) { super(theme, "歌曲材料预览"); }

    @Override public void initWidgets() {
        var list = add(theme.verticalList()).expandX().widget();
        list.add(theme.button("选择歌曲")).expandX().widget().action = () -> taskAfterRender = this::chooseFile;
        songLabel = list.add(theme.label(selected)).widget();
        statusLabel = list.add(theme.label(status)).widget();
        materials = list.add(theme.verticalList()).expandX().widget();
        renderMaterials();
    }

    private void chooseFile() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var filters = stack.mallocPointer(2);
            filters.put(stack.UTF8("*.nbs")).put(stack.UTF8("*.txt")).flip();
            String result = TinyFileDialogs.tinyfd_openFileDialog("选择歌曲", directory + File.separator,
                filters, "NBS / TXT", false);
            if (result == null) return;
            cancelPending();
            Path path = Path.of(result).toAbsolutePath();
            String name = path.getFileName().toString();
            selected = "歌曲：" + (name.length() > 36 ? name.substring(0, 33) + "…" : name);
            songLabel.set(selected);
            rows = List.of(); renderMaterials();
            analyze(path);
        } catch (Exception e) { setStatus("无法打开歌曲，请重新选择。"); }
    }

    private void analyze(Path path) {
        try {
            if (!Files.isRegularFile(path) || !SongDecoders.hasDecoder(path)) {
                setStatus("请选择 NBS 或 TXT 格式的歌曲。"); return;
            }
            if (Files.size(path) > 32L * 1024 * 1024) { setStatus("歌曲文件不能超过 32 MB。"); return; }
            AtomicBoolean token = new AtomicBoolean();
            cancellation = token;
            setStatus("正在读取歌曲……");
            Thread.ofPlatform().daemon().name("Tanghulu-song-materials").start(() -> {
                try {
                    Song song = SongDecoders.getDecoder(path.toFile()).parse(path.toFile());
                    mc.execute(() -> {
                        if (token.get()) return;
                        try { showSong(song); } catch (Exception e) { setStatus("歌曲读取失败，请换一首歌曲。"); }
                    });
                } catch (Exception e) {
                    mc.execute(() -> { if (!token.get()) setStatus("歌曲读取失败，请换一首歌曲。"); });
                }
            });
        } catch (Exception e) { setStatus("无法读取歌曲，请重新选择。"); }
    }

    private void showSong(Song song) {
        Notebot bot = Modules.get().get(Notebot.class);
        List<Note> mapped = new ArrayList<>();
        for (Note note : song.getNotesMap().values()) {
            int level = note.getNoteLevel();
            if (level < 0 || level > 24) {
                if (!bot.roundOutOfRange.get()) continue;
                level = Math.max(0, Math.min(24, level));
            }
            var instrument = note.getInstrument();
            if (bot.mode.get() == NotebotUtils.NotebotMode.ExactInstruments) {
                var replacement = bot.getMappedInstrument(instrument);
                if (replacement != null) instrument = replacement;
            } else instrument = null;
            mapped.add(new Note(instrument, level));
        }
        rows = SongMaterials.rows(mapped);
        renderMaterials();
        setStatus(rows.isEmpty() ? "这首歌没有可用的音符，请换一首歌曲。" : "需要准备以下材料：");
    }

    private void renderMaterials() {
        if (materials == null) return;
        materials.clear();
        if (rows.isEmpty()) return;
        int total = rows.stream().mapToInt(SongMaterials.Row::count).sum();
        var totalRow = materials.add(theme.horizontalList()).widget();
        totalRow.add(theme.item(new ItemStack(Blocks.NOTE_BLOCK)));
        totalRow.add(theme.label("音符盒 × " + total));
        for (var entry : rows) {
            var row = materials.add(theme.horizontalList()).widget();
            var material = entry.material();
            if (material.block() != null) row.add(theme.item(new ItemStack(material.block())));
            String block = material.block() == null ? "任意可用垫底方块" : material.block().getName().getString();
            row.add(theme.label(block + " × " + entry.count() + "（" + material.name()
                + (material.above() ? "，放在音符盒上方" : "") + "）"));
        }
        materials.add(theme.label("普通方块垫在音符盒下方，上方留空。"));
        if (rows.stream().anyMatch(row -> row.material().above()))
            materials.add(theme.label("本曲含头颅音色，目前修复演奏不支持。"));
    }

    private void setStatus(String message) { status = message; if (statusLabel != null) statusLabel.set(message); }
    private void cancelPending() { if (cancellation != null) cancellation.set(true); }
    @Override protected void onClosed() { cancelPending(); super.onClosed(); }
}
