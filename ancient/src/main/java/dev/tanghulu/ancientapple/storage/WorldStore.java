package dev.tanghulu.ancientapple.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.tanghulu.ancientapple.core.Calibration;
import dev.tanghulu.ancientapple.core.ChestTarget;
import dev.tanghulu.ancientapple.prediction.LootVersion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public final class WorldStore {
    public static final String ALGORITHM="java-1.21.11-a3-order-aware";
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final Path legacyFile;
    private final Path blacklistFile;
    private final Set<Path> migrationFiles=new LinkedHashSet<>();
    private final Set<String> blacklisted=new HashSet<>();
    private boolean migratedBlacklist;
    private boolean blacklistEnabled=true;
    public void setBlacklistEnabled(boolean enabled) { blacklistEnabled=enabled; }
    public List<String> blacklistPositions() throws IOException { loadBlacklist(); return blacklisted.stream().sorted().toList(); }
    public List<String> openedPositions() throws IOException {
        loadBlacklist(); Set<String> positions=new TreeSet<>();
        for(Path source:migrationFiles)if(Files.exists(source)) {
            Snapshot saved=readSnapshot(source);
            saved.targets.stream().filter(t -> t.openedWithApples && !blacklisted.contains(t.key())).forEach(t -> positions.add(t.key()));
        }
        return List.copyOf(positions);
    }
    /** Restore only explicit coordinates, including their profile-specific hidden markers. */
    public int restoreRecords(Collection<String> positions,Collection<ChestTarget> targets,boolean openedOnly) throws IOException {
        Set<String> keys=new HashSet<>(positions);
        if(keys.stream().anyMatch(k -> !validKey(k)))throw new IOException("无效的箱子坐标");
        loadBlacklist();
        Set<String> existing=new HashSet<>(openedOnly?openedPositions():blacklisted);
        existing.retainAll(keys);
        for(Path source:migrationFiles)if(Files.exists(source)) {
            Snapshot saved=readSnapshot(source); boolean changed=false;
            for(var target:saved.targets)if(existing.contains(target.key())) {
                if(openedOnly)target.openedWithApples=false; else target.resetCollection();
                changed=true;
            }
            if(changed)writeJson(source,saved);
        }
        if(!openedOnly) {
            Set<String> remaining=new TreeSet<>(blacklisted); remaining.removeAll(existing);
            BlacklistSnapshot saved=new BlacklistSnapshot(); saved.positions=remaining;
            writeJson(blacklistFile,saved); blacklisted.clear(); blacklisted.addAll(remaining); migratedBlacklist=true;
        }
        for(var target:targets)if(existing.contains(target.key())) {
            if(openedOnly)target.openedWithApples=false; else target.resetCollection();
        }
        return existing.size();
    }
    private Snapshot readSnapshot(Path source) throws IOException {
        Snapshot saved=GSON.fromJson(Files.readString(source),Snapshot.class);
        if(saved==null || !ALGORITHM.equals(saved.algorithm) || saved.targets==null || saved.targets.contains(null))
            throw new IOException("不兼容的搜取缓存："+source);
        return saved;
    }
    public void refreshBlacklistView(Collection<ChestTarget> targets) {
        for(var target:targets) {
            if(!blacklistEnabled && target.isBlacklisted())target.resetCollection();
            else applyBlacklist(target);
        }
    }
    /** Clear every supported loot cache as well, so a later profile switch cannot restore old history. */
    public int clearBlacklist(Collection<ChestTarget> targets) throws IOException {
        loadBlacklist(); int count=blacklisted.size();
        for(Path source:migrationFiles)if(Files.exists(source)) {
            Snapshot snapshot=GSON.fromJson(Files.readString(source),Snapshot.class);
            if(snapshot==null || !ALGORITHM.equals(snapshot.algorithm) || snapshot.targets==null)
                throw new IOException("无法清除不兼容的搜取缓存："+source);
            snapshot.targets.stream().filter(t -> t.isBlacklisted() || t.openedWithApples).forEach(ChestTarget::resetCollection);
            writeJson(source,snapshot);
        }
        var empty=new BlacklistSnapshot(); writeJson(blacklistFile,empty);
        blacklisted.clear(); migratedBlacklist=true;
        targets.stream().filter(t -> t.isBlacklisted() || t.openedWithApples).forEach(ChestTarget::resetCollection);
        return count;
    }
    public WorldStore(Path directory, String worldIdentity, long seed) {
        this(directory,worldIdentity,seed,"1.21.11");
    }
    public WorldStore(Path directory,String worldIdentity,long seed,String lootVersion) {
        try {
            String identity=worldIdentity+"|"+seed+"|"+ALGORITHM;
            legacyFile=directory.resolve(hash(identity)+".json");
            file=directory.resolve(hash(identity+(lootVersion.equals("1.21.11")?"":"|loot="+lootVersion))+".json");
            // Actual collection coordinates belong to this world/seed, independently of loot models or prediction revisions.
            blacklistFile=directory.resolve(hash("collected-chests-v1|"+worldIdentity+"|"+seed)+".blacklist.json");
            migrationFiles.add(legacyFile); migrationFiles.add(file);
            // Include every supported profile: clear/restore must survive later switches.
            for(LootVersion version:LootVersion.values())if(version!=LootVersion.MC_1_21_11)
                migrationFiles.add(directory.resolve(hash(identity+"|loot="+version)+".json"));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public Snapshot read() throws IOException {
        boolean blacklistChanged=loadBlacklist();
        boolean migrate=!Files.exists(file) && !file.equals(legacyFile) && Files.exists(legacyFile);
        if(blacklistChanged)writeBlacklist();
        if (!Files.exists(file) && !migrate) return new Snapshot();
        Snapshot value=GSON.fromJson(Files.readString(migrate?legacyFile:file),Snapshot.class);
        if (value==null || !ALGORITHM.equals(value.algorithm) || value.targets==null || value.matches==null)
            throw new IOException("缓存格式或算法版本不匹配");
        if(migrate) {
            // A different loot model invalidates estimates and sample matches, not actual visits.
            value.targets.removeIf(t -> !t.isBlacklisted() && t.status!=ChestTarget.Status.EMPTY);
            value.matches.clear(); value.failed=false;
        }
        for(ChestTarget target:value.targets) {
            if(target.isBlacklisted() && (!blacklistEnabled || !blacklisted.contains(target.key())))target.resetCollection();
            applyBlacklist(target);
            if(target.status==ChestTarget.Status.UNREACHABLE) {
                target.status=ChestTarget.Status.DEFERRED; target.reason="旧版不可达记录，重新尝试";
            }
        }
        return value;
    }
    /** Apply after merging each new prediction, even if that coordinate has no entry in this loot model's cache. */
    public boolean applyBlacklist(ChestTarget target) {
        if(!blacklistEnabled)return false;
        if(target.isBlacklisted() || blacklisted.contains(target.key())) {
            target.markCollected(); return true;
        }
        return false;
    }
    /** Only pass actual connected halves after confirmed collection, never predictions or mere container observations. */
    public void blacklist(Collection<ChestTarget> collected) throws IOException {
        boolean changed=loadBlacklist();
        for(ChestTarget target:collected) {
            target.markCollected(); changed|=blacklisted.add(target.key());
        }
        if(changed)writeBlacklist();
    }
    public void write(Collection<ChestTarget> targets, Calibration calibration) throws IOException {
        boolean changed=loadBlacklist();
        for(ChestTarget target:targets)if(target.isBlacklisted())changed|=blacklisted.add(target.key());
        // Persist irreversible collection history first, before writing replaceable prediction caches.
        if(changed)writeBlacklist();
        targets.forEach(this::applyBlacklist);
        Snapshot s=new Snapshot(); s.targets=new ArrayList<>(targets); s.matches=calibration.matchedKeys(); s.failed=calibration.failed();
        writeJson(file,s);
    }
    private boolean loadBlacklist() throws IOException {
        boolean changed=false;
        if(Files.exists(blacklistFile)) {
            BlacklistSnapshot saved=GSON.fromJson(Files.readString(blacklistFile),BlacklistSnapshot.class);
            if(saved==null || saved.version!=1 || saved.positions==null || saved.positions.stream().anyMatch(k -> !validKey(k)))
                throw new IOException("搜取黑名单格式不匹配");
            // Retry a previously failed flush without forgetting in-memory collection history.
            changed=!saved.positions.containsAll(blacklisted);
            blacklisted.addAll(saved.positions);
        }
        if(!migratedBlacklist && !Files.exists(blacklistFile)) {
            for(Path source:migrationFiles)if(Files.exists(source)) {
                Snapshot old=GSON.fromJson(Files.readString(source),Snapshot.class);
                if(old!=null && ALGORITHM.equals(old.algorithm) && old.targets!=null)
                    for(ChestTarget target:old.targets)if(target!=null && target.isBlacklisted())changed|=blacklisted.add(target.key());
            }
            migratedBlacklist=true;
        }
        return changed || (!blacklisted.isEmpty() && !Files.exists(blacklistFile));
    }
    private void writeBlacklist() throws IOException {
        BlacklistSnapshot saved=new BlacklistSnapshot(); saved.positions=new TreeSet<>(blacklisted);
        writeJson(blacklistFile,saved);
    }
    private static String hash(String value) throws java.security.NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    private static boolean validKey(String key) {
        if(key==null)return false;
        String[] parts=key.split(",",-1); if(parts.length!=3)return false;
        try { for(String part:parts)Integer.parseInt(part); return true; }
        catch(NumberFormatException e) { return false; }
    }
    private static void writeJson(Path destination,Object value) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temp=Files.createTempFile(destination.getParent(),"hunter-",".tmp");
        try {
            Files.writeString(temp,GSON.toJson(value));
            try { Files.move(temp,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp,destination,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    private static final class BlacklistSnapshot {
        public int version=1;
        public Set<String> positions=new TreeSet<>();
    }
    public static final class Snapshot {
        public String algorithm=ALGORITHM;
        public List<ChestTarget> targets=new ArrayList<>();
        public Set<String> matches=new HashSet<>();
        public boolean failed;
    }
}
