package dev.tanghulu.addon.util;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** No optional map classes: clearing saved results also works without XaeroPlus. */
public final class StashSearchRecords {
    private StashSearchRecords() {}

    public interface Cache {
        void clearSavedStashCache();
    }

    public static Snapshot snapshotCurrentWorld() throws IOException {
        String worldName = Utils.getFileWorldName();
        if (worldName == null || worldName.isBlank()) throw new IOException("尚未识别仓库记录的世界标识");
        Path directory = MeteorClient.FOLDER.toPath().resolve("better-stash-finder").resolve(worldName);
        Map<Path, byte[]> files = new LinkedHashMap<>();
        for (String name : new String[]{"stashes.json", "stashes.csv"}) {
            Path file = directory.resolve(name);
            if (Files.exists(file)) files.put(file, Files.readAllBytes(file));
        }
        return new Snapshot(files);
    }

    public static final class Snapshot {
        private final Map<Path, byte[]> files;

        private Snapshot(Map<Path, byte[]> files) { this.files = files; }

        public boolean hasSavedFiles() { return !files.isEmpty(); }

        public void clearFiles() throws IOException {
            for (Path file : files.keySet()) Files.delete(file);
        }

        public void restoreFiles(Throwable failure) {
            for (var entry : files.entrySet()) {
                try { Files.write(entry.getKey(), entry.getValue()); }
                catch (IOException | RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
        }

        public void clearCache() {
            for (var module : Modules.get().getAll()) {
                if (module instanceof Cache cache) cache.clearSavedStashCache();
            }
        }
    }
}
