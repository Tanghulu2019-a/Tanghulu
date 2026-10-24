package dev.tanghulu.mushroom;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Runs only inside an isolated, version-specific Mojang runtime. No client classes. */
public final class BiomeWorker {
    private final Map<String, String> classes = new HashMap<>();
    private final Map<String, String> members = new HashMap<>();
    private static final Set<String> MAPPED_TYPES = Set.of(
        "net.minecraft.SharedConstants",
        "net.minecraft.core.Holder",
        "net.minecraft.core.HolderGetter",
        "net.minecraft.core.HolderLookup$Provider",
        "net.minecraft.core.registries.Registries",
        "net.minecraft.data.registries.VanillaRegistries",
        "net.minecraft.resources.ResourceKey",
        "net.minecraft.server.Bootstrap",
        "net.minecraft.world.level.biome.BiomeResolver",
        "net.minecraft.world.level.biome.Biomes",
        "net.minecraft.world.level.biome.Climate$Sampler",
        "net.minecraft.world.level.biome.MultiNoiseBiomeSource",
        "net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists",
        "net.minecraft.world.level.levelgen.NoiseGeneratorSettings",
        "net.minecraft.world.level.levelgen.RandomState",
        "net.minecraft.world.level.levelgen.densityfunction.SamplerContext");
    private Object source, sampler, mushroom;
    private Method biome, matches;

    BiomeWorker(Path mappings, long seed) throws Exception {
        if (Files.isRegularFile(mappings)) {
            String owner = "";
            try (var lines = Files.newBufferedReader(mappings)) {
                for (String line; (line = lines.readLine()) != null;) {
                    if (line.startsWith("#") || line.isBlank()) continue;
                    if (!line.startsWith(" ")) {
                        String[] parts = line.substring(0, line.length()-1).split(" -> ");
                        owner = MAPPED_TYPES.contains(parts[0]) ? parts[0] : "";
                        if (!owner.isEmpty()) classes.put(owner, parts[1]);
                    } else if (!owner.isEmpty()) {
                        String[] parts = line.strip().split(" -> ");
                        String signature = parts[0].replaceFirst("^(\\d+:\\d+:)+", "");
                        members.put(owner + ":" + signature.substring(signature.indexOf(' ') + 1), parts[1]);
                    }
                }
            }
        }
        call("net.minecraft.SharedConstants", "tryDetectVersion", null, new String[0]);
        call("net.minecraft.server.Bootstrap", "bootStrap", null, new String[0]);
        Object registries;
        try { registries = call("net.minecraft.data.registries.VanillaRegistries", "createLookup", null, new String[0]); }
        catch(NoSuchMethodException e) { registries = call("net.minecraft.data.registries.VanillaRegistries", "createWorldLookup", null, new String[0]); }
        Object settings = lookup(registries, "NOISE_SETTINGS", "net.minecraft.world.level.levelgen.NoiseGeneratorSettings", "OVERWORLD");
        Object noises = registry(registries, "NOISE");
        Object random;
        boolean resolver = false;
        try { random = call("net.minecraft.world.level.levelgen.RandomState", "create", null,
            new String[]{"net.minecraft.world.level.levelgen.NoiseGeneratorSettings", "net.minecraft.core.HolderGetter", "long"},
            call("net.minecraft.core.Holder", "value", settings, new String[0]), noises, seed);
            sampler = call("net.minecraft.world.level.levelgen.RandomState", "sampler", random, new String[0]);
        } catch(NoSuchMethodException e) {
            random = call("net.minecraft.world.level.levelgen.RandomState", "create", null,
                new String[]{"net.minecraft.core.HolderGetter", "long", "net.minecraft.world.level.levelgen.NoiseGeneratorSettings"},
                noises, seed, call("net.minecraft.core.Holder", "value", settings, new String[0]));
            sampler = call("net.minecraft.world.level.levelgen.RandomState", "createClimateSampler", random,
                new String[]{"net.minecraft.world.level.levelgen.densityfunction.SamplerContext"},
                field("net.minecraft.world.level.levelgen.densityfunction.SamplerContext", "EMPTY_UNCACHED"));
            resolver = true;
        }
        Object preset = lookup(registries, "MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST",
            "net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists", "OVERWORLD");
        source = call("net.minecraft.world.level.biome.MultiNoiseBiomeSource", "createFromPreset", null,
            new String[]{"net.minecraft.core.Holder"}, preset);
        mushroom = field("net.minecraft.world.level.biome.Biomes", "MUSHROOM_FIELDS");
        if(resolver) {
            source = call("net.minecraft.world.level.biome.MultiNoiseBiomeSource", "createResolver", source,
                new String[]{"net.minecraft.world.level.biome.Climate$Sampler"},sampler);
            biome = method("net.minecraft.world.level.biome.BiomeResolver", "getNoiseBiome", "int", "int", "int");
        } else biome = method("net.minecraft.world.level.biome.MultiNoiseBiomeSource", "getNoiseBiome",
                "int", "int", "int", "net.minecraft.world.level.biome.Climate$Sampler");
        matches = method("net.minecraft.core.Holder", "is", "net.minecraft.resources.ResourceKey");
        classes.clear(); members.clear();
    }
    Object registry(Object provider, String name) throws Exception {
        return call("net.minecraft.core.HolderLookup$Provider", "lookupOrThrow", provider,
            new String[]{"net.minecraft.resources.ResourceKey"}, field("net.minecraft.core.registries.Registries", name));
    }
    Object lookup(Object provider, String registry, String keys, String key) throws Exception {
        return call("net.minecraft.core.HolderGetter", "getOrThrow", registry(provider, registry),
            new String[]{"net.minecraft.resources.ResourceKey"}, field(keys, key));
    }
    Class<?> type(String name) throws Exception {
        return switch(name) { case "int" -> int.class; case "long" -> long.class;
            default -> Class.forName(classes.getOrDefault(name, name)); };
    }
    Method method(String owner, String name, String... parameters) throws Exception {
        Class<?>[] types = new Class<?>[parameters.length];
        for (int i=0;i<types.length;i++) types[i]=type(parameters[i]);
        return type(owner).getMethod(members.getOrDefault(owner+":"+name+"("+String.join(",",parameters)+")", name), types);
    }
    Object call(String owner, String name, Object target, String[] parameters, Object... arguments) throws Exception {
        return method(owner,name,parameters).invoke(target,arguments);
    }
    Object field(String owner, String name) throws Exception {
        return type(owner).getField(members.getOrDefault(owner+":"+name,name)).get(null);
    }
    boolean mushroom(int x, int y, int z) throws Exception {
        Object[] args = biome.getParameterCount()==3
            ? new Object[]{Math.floorDiv(x,4),Math.floorDiv(y,4),Math.floorDiv(z,4)}
            : new Object[]{Math.floorDiv(x,4),Math.floorDiv(y,4),Math.floorDiv(z,4),sampler};
        return (boolean)matches.invoke(biome.invoke(source,args),mushroom);
    }
    // A bounded ring search; all returned points are checked by the exact version's biome source.
    // This finds a nearby island, not necessarily the mathematically nearest coastline block.
    int[] search(int x, int z, int radius, int step) throws Exception {
        int limit=(radius+step-1)/step;
        for(int ring=0;ring<=limit;ring++) {
            for(int dx=-ring;dx<=ring;dx++) {
                int[] result=tryPoint(x,z,dx*step,-ring*step,radius);
                if(result!=null)return result;
                if(ring!=0 && (result=tryPoint(x,z,dx*step,ring*step,radius))!=null)return result;
            }
            for(int dz=-ring+1;dz<ring;dz++) {
                int[] result=tryPoint(x,z,-ring*step,dz*step,radius);
                if(result!=null)return result;
                if((result=tryPoint(x,z,ring*step,dz*step,radius))!=null)return result;
            }
            if(ring%16==0) System.out.println("PROGRESS "+Math.min(radius,ring*step));
        }
        return null;
    }
    int[] tryPoint(int x,int z,int dx,int dz,int radius) throws Exception {
        if((long)dx*dx+(long)dz*dz>(long)radius*radius)return null;
        long px=(long)x+dx,pz=(long)z+dz;
        if(Math.abs(px)>29999984 || Math.abs(pz)>29999984)return null;
        // Check both vertical quart layers used by vanilla's biome zoom at Y=64.
        // The surrounding quart cells make the exact block independent of zoom jitter.
        for(int ox=-4;ox<=4;ox+=4)for(int oz=-4;oz<=4;oz+=4)for(int y=60;y<=64;y+=4)
            if(!mushroom((int)px+ox,y,(int)pz+oz))return null;
        return new int[]{(int)px,64,(int)pz};
    }
    public static void main(String[] args) {
        PrintStream protocol = new PrintStream(new FileOutputStream(FileDescriptor.out),true,StandardCharsets.UTF_8);
        System.setOut(System.err);
        try {
            var worker=new BiomeWorker(Path.of(args[0]),Long.parseLong(args[1]));
            System.setOut(protocol);
            protocol.println("READY");
            int x=Integer.parseInt(args[2]),z=Integer.parseInt(args[3]),radius=Integer.parseInt(args[4]),step=Integer.parseInt(args[5]);
            if(args.length>6 && args[6].equals("all")) {
                var targets=IslandScanner.scan(x,z,radius,step,(px,pz) -> worker.mushroom(px,64,pz),
                    (px,pz) -> worker.tryPoint(x,z,px-x,pz-z,radius)!=null,p -> protocol.println("PERCENT "+p));
                for(int[] p:targets)protocol.println("TARGET "+p[0]+" "+p[1]+" "+p[2]);
                protocol.println("DONE "+targets.size());
            } else {
                int[] pos=worker.search(x,z,radius,step);
                protocol.println(pos==null?"NONE":"FOUND "+pos[0]+" "+pos[1]+" "+pos[2]);
            }
        } catch(Throwable e) {
            e.printStackTrace(System.err);
            protocol.println("ERROR "+e);
            System.exit(1);
        }
        System.exit(0);
    }
}
