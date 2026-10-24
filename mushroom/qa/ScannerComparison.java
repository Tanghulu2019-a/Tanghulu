package dev.tanghulu.mushroom;

import java.util.*;

/** Differential tests use the scanner extracted from the user-supplied source ZIP. */
public final class ScannerComparison {
    private static long originalCalls, optimizedCalls;
    private static int checks;

    private static void compare(int x,int z,int radius,int step,IslandScanner.Probe biome,
                                IslandScanner.Probe safe) throws Exception {
        var beforeProgress=new ArrayList<Integer>();
        var afterProgress=new ArrayList<Integer>();
        var before=BaselineIslandScanner.scan(x,z,radius,step,(px,pz)->{
            originalCalls++; return biome.test(px,pz);
        },safe::test,beforeProgress::add);
        var after=IslandScanner.scan(x,z,radius,step,(px,pz)->{
            optimizedCalls++; return biome.test(px,pz);
        },safe,afterProgress::add);
        if(!Arrays.deepEquals(before.toArray(),after.toArray()) || !beforeProgress.equals(afterProgress))
            throw new AssertionError("Scanner mismatch: "+List.of(x,z,radius,step));
        checks++;
    }

    public static void main(String[] args) throws Exception {
        var random=new Random(20261019);
        int[] steps={4,8,12,16,32,64,128};
        for(int i=0;i<400;i++) {
            int x=random.nextInt(4096)-2048,z=random.nextInt(4096)-2048;
            int radius=16+random.nextInt(300),step=steps[i%steps.length];
            int mode=i%5,salt=random.nextInt();
            compare(x,z,radius,step,(px,pz)->switch(mode) {
                case 0 -> true;
                case 1 -> false;
                case 2 -> Math.floorMod(px/4*31+pz/4*17+salt,7)<3;
                case 3 -> Math.abs(px-x)<radius/2 && Math.abs(pz-z)<radius/3 && px!=x+16;
                default -> (px-x)*(px-x)+(pz-z)*(pz-z)<radius*radius/3 || Math.abs(pz-z)<5;
            },(px,pz)->Math.floorMod(px*7+pz*11,19)>5);
        }
        // A wide flood frontier forces the primitive ring buffer to grow and wrap.
        compare(-17,23,2048,32,(x,z)->true,(x,z)->true);
        for(int x:new int[]{-29999984,29999984})for(int z:new int[]{-29999984,29999984})
            compare(x,z,256,8,(px,pz)->true,(px,pz)->true);
        if(optimizedCalls>=originalCalls)throw new AssertionError("No queries were saved");
        // Per-thread allocation avoids timing noise from other processes and GC.
        var memory=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        long thread=Thread.currentThread().threadId(),start=memory.getThreadAllocatedBytes(thread);
        BaselineIslandScanner.scan(0,0,2048,32,(x,z)->true,(x,z)->true,p->{});
        long beforeBytes=memory.getThreadAllocatedBytes(thread)-start;
        start=memory.getThreadAllocatedBytes(thread);
        IslandScanner.scan(0,0,2048,32,(x,z)->true,(x,z)->true,p->{});
        long afterBytes=memory.getThreadAllocatedBytes(thread)-start;
        if(afterBytes>=beforeBytes)throw new AssertionError("Allocation regression");
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"originalProbes\":"+originalCalls+
            ",\"optimizedProbes\":"+optimizedCalls+",\"originalAllocatedBytes\":"+beforeBytes+
            ",\"optimizedAllocatedBytes\":"+afterBytes+"}");
    }
}
