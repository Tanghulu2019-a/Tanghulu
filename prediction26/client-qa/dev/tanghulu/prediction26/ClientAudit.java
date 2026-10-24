package dev.tanghulu.prediction26;

import com.seedfinding.mccore.version.MCVersion;
import dev.tanghulu.addon.util.seeds.*;
import meteordevelopment.meteorclient.settings.EnumSetting;
import net.minecraft.nbt.NbtCompound;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class ClientAudit {
    static int checks;
    static void check(boolean value,String message) { checks++; if(!value)throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        var setting=new EnumSetting.Builder<SeedVersion>().name("MC版本").defaultValue(SeedVersion.v26_3).build();
        for(var old:MCVersion.values()) {
            var migrated=SeedVersion.valueOf(old.name());
            check(migrated.toString().equals(old.toString()),"label changed "+old);
            var saved=new NbtCompound(); saved.putString("value",old.toString());
            setting.load(saved); check(setting.get()==migrated,"old enum setting failed "+old);
            var original=new Seed(-7346913998703726680L,old);
            var restored=Seed.fromTag(original.toTag());
            check(restored.seed.equals(original.seed) && restored.version==old,"legacy seed tag failed");
        }
        var modern=new Seed(Long.MIN_VALUE,"26.3");
        var restored=Seed.fromTag(modern.toTag());
        check(restored.is26_3() && restored.seed==Long.MIN_VALUE,"modern seed tag lost version");
        check(setting.parse("26.3") && setting.get()==SeedVersion.v26_3,"modern UI value missing");
        try(var backend=Prediction26.open(0)) {
            check(backend.request(Map.of("op","version")).get("version").getAsString().equals("26.3"),"wrong backend version");
            check(!backend.request(Map.of("op","ores","dimension","the_nether","x",0,"z",0)).getAsJsonArray("ores").isEmpty(),"ore protocol empty");
            check(!backend.request(Map.of("op","structures","dimension","the_end","x",100,"z",100,"radius",100,
                "selected",List.of("end_city"),"ships",true)).getAsJsonArray("structures").isEmpty(),"ship protocol empty");
            var work=CompletableFuture.runAsync(() -> {
                try { backend.request(Map.of("op","structures","dimension","overworld","x",0,"z",0,"radius",2000,"selected",List.of("mineshaft","stronghold"))); }
                catch(Exception expected) { return; }
                throw new AssertionError("large search completed before cancellation");
            });
            Thread.sleep(200); backend.cancel(); work.get(10,TimeUnit.SECONDS);
            check(true,"cancel unblocks request");
        }
        Path cache=Path.of(System.getProperty("prediction26.testCache"),"tanghulu-prediction/26.3");
        try(var files=Files.list(cache)) { check(files.noneMatch(p -> p.getFileName().toString().startsWith("world-")),"temporary world leaked"); }
        Files.writeString(cache.getParent().getParent().resolve("client-audit.json"),
            "{\"passed\":true,\"checks\":"+checks+",\"legacyVersions\":"+MCVersion.values().length+",\"inGameTested\":false}");
        System.out.println("CLIENT26_AUDIT checks="+checks+" legacyVersions="+MCVersion.values().length);
    }
}
