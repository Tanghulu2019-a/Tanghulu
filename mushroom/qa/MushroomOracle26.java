package dev.tanghulu.prediction26;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.util.Util;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Independently checks worker destinations against generated vanilla chunk biomes and biome zoom. */
public final class MushroomOracle26 {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);
        Path qa=args.length>1?Path.of(args[1]):root.resolve("mushroom-qa");
        SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        int checks=0;
        try {
            for(long seed:new long[]{0,-1,-7346913998703726680L}) {
                var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Xmx1G",
                    "-cp",root.resolve("build/mushroom/worker.jar")+java.io.File.pathSeparator+qa.resolve("26.3-runtime")+"/*",
                    "dev.tanghulu.mushroom.BiomeWorker",qa.resolve("no-mappings.txt").toString(),Long.toString(seed),"0","0","8192","64","all")
                    .redirectError(root.resolve("build/mushroom/oracle-worker.log").toFile()).start();
                List<int[]> targets=new ArrayList<>(); boolean done=false;
                try(var reader=process.inputReader()) {
                    for(String line;(line=reader.readLine())!=null;) {
                        if(line.startsWith("TARGET ")) { String[] p=line.split(" "); targets.add(new int[]{Integer.parseInt(p[1]),Integer.parseInt(p[3])}); }
                        if(line.startsWith("DONE "))done=true;
                    }
                }
                if(!process.waitFor(120,TimeUnit.SECONDS) || process.exitValue()!=0 || !done || targets.isEmpty())throw new AssertionError("worker failed");
                try(var server=Vanilla26.open(root.resolve("build/mushroom/oracle-all/seed-"+seed),seed)) {
                    var world=server.getLevel(Level.OVERWORLD);
                    var zoom=new BiomeManager((qx,qy,qz) -> world.getChunk(Math.floorDiv(qx,4),Math.floorDiv(qz,4),ChunkStatus.BIOMES)
                        .getNoiseBiome(qx,qy,qz),BiomeManager.obfuscateSeed(seed));
                    for(int[] target:targets)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
                        int x=target[0],z=target[1];
                        if(!zoom.getBiome(new BlockPos(x+dx,64,z+dz)).is(Biomes.MUSHROOM_FIELDS))
                            throw new AssertionError("not a generated mushroom biome: "+seed+" "+x+" "+z);
                        checks++;
                    }
                }
                System.out.println("ORACLE mushroom seed="+seed+" targets="+targets.size());
            }
            Files.writeString(root.resolve("validation/mushroom-oracle26.json"),"{\"passed\":true,\"checks\":"+checks+",\"seeds\":3}\n");
            System.out.println("MUSHROOM_ORACLE26 checks="+checks);
        } finally { Util.shutdownExecutors(); }
        System.exit(0);
    }
}
