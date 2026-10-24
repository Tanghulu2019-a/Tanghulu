package dev.tanghulu.mushroom;

import java.util.*;
import java.util.function.IntConsumer;

/** Scans the complete circular grid, then groups connected mushroom cells into islands. */
public final class IslandScanner {
    @FunctionalInterface public interface Probe { boolean test(int x,int z) throws Exception; }
    public static List<int[]> scan(int x,int z,int radius,int step,Probe biome,Probe safe,IntConsumer progress) throws Exception {
        if(radius<1 || radius>100000 || step<4 || step>128)throw new IllegalArgumentException("搜索参数无效");
        int minX=Math.floorDiv(x-radius,step)*step,minZ=Math.floorDiv(z-radius,step)*step;
        int width=Math.floorDiv(x+radius-minX,step)+1,height=Math.floorDiv(z+radius-minZ,step)+1;
        long size=(long)width*height;
        if(size>100000000)throw new IllegalArgumentException("搜索范围过大，请缩小半径或降低搜索精细度");
        BitSet cells=new BitSet((int)size);
        long radiusSq=(long)radius*radius;
        int last=-1;
        for(int row=0;row<height;row++) {
            int pz=minZ+row*step;
            for(int col=0;col<width;col++) {
                int px=minX+col*step;
                if(Math.abs((long)px)>29999984 || Math.abs((long)pz)>29999984)continue;
                long dx=(long)px-x,dz=(long)pz-z;
                if(dx*dx+dz*dz<=radiusSq && biome.test(px,pz))cells.set(row*width+col);
            }
            int percent=(row+1)*80/height;
            if(percent!=last) { progress.accept(percent); last=percent; }
        }
        List<int[]> islands=new ArrayList<>();
        LongQueue queue=new LongQueue();
        // Refine each discovered island at the native 4-block biome grid so a narrow
        // land connection is not split into multiple islands by the user's coarse setting.
        int fineMinX=Math.floorDiv(x-radius,4)*4,fineMinZ=Math.floorDiv(z-radius,4)*4;
        int fineWidth=Math.floorDiv(x+radius-fineMinX,4)+1,fineHeight=Math.floorDiv(z+radius-fineMinZ,4)+1;
        BitSet[] visited=new BitSet[(int)(((long)fineWidth*fineHeight+65535)>>>16)];
        int initial=cells.cardinality(),remaining=initial;
        for(int first=cells.nextSetBit(0);first>=0;first=cells.nextSetBit(first+1)) {
            int firstX=minX+(first%width)*step,firstZ=minZ+(first/width)*step;
            long seed=(long)((firstZ-fineMinZ)/4)*fineWidth+(firstX-fineMinX)/4;
            mark(visited,seed); queue.add(seed);
            int[] target=null;
            while(!queue.isEmpty()) {
                long node=queue.removeFirst(); int row=(int)(node/fineWidth),col=(int)(node%fineWidth);
                int px=fineMinX+col*4,pz=fineMinZ+row*4;
                if((px-minX)%step==0 && (pz-minZ)%step==0) {
                    int coarse=(pz-minZ)/step*width+(px-minX)/step;
                    if(cells.get(coarse)) { cells.clear(coarse); remaining--; }
                }
                if(target==null && safe.test(px,pz))target=new int[]{px,64,pz};
                for(int direction=0;direction<4;direction++) {
                    int nc=col+(direction==0?-1:direction==1?1:0);
                    int nr=row+(direction==2?-1:direction==3?1:0);
                    if(nc<0 || nc>=fineWidth || nr<0 || nr>=fineHeight)continue;
                    long next=(long)nr*fineWidth+nc;
                    if(!mark(visited,next))continue;
                    int nx=fineMinX+nc*4,nz=fineMinZ+nr*4;
                    long dx=(long)nx-x,dz=(long)nz-z;
                    if(dx*dx+dz*dz>radiusSq || Math.abs((long)nx)>29999984 || Math.abs((long)nz)>29999984)continue;
                    // An unvisited coarse cell still contains its original sample result.
                    // Reuse both hits and misses rather than evaluate the noise twice.
                    boolean coarse=(nx-minX)%step==0 && (nz-minZ)%step==0;
                    if(coarse ? cells.get((nz-minZ)/step*width+(nx-minX)/step) : biome.test(nx,nz))queue.add(next);
                }
            }
            if(target!=null)islands.add(target);
            int percent=initial==0?100:80+(int)((long)(initial-remaining)*20/initial);
            if(percent!=last) { progress.accept(percent); last=percent; }
        }
        islands.sort(Comparator.comparingLong(p -> (long)(p[0]-x)*(p[0]-x)+(long)(p[2]-z)*(p[2]-z)));
        progress.accept(100);
        return islands;
    }
    private static boolean mark(BitSet[] pages,long index) {
        int slot=(int)(index>>>16);
        BitSet page=pages[slot];
        if(page==null)pages[slot]=page=new BitSet();
        int bit=(int)(index&65535);
        if(page.get(bit))return false;
        page.set(bit); return true;
    }

    /** Primitive ring buffer: a large island needs no boxed Long per visited cell. */
    private static final class LongQueue {
        private long[] values=new long[256];
        private int head,size;
        boolean isEmpty() { return size==0; }
        void add(long value) {
            if(size==values.length) {
                long[] grown=new long[values.length*2];
                int tail=values.length-head;
                System.arraycopy(values,head,grown,0,tail);
                System.arraycopy(values,0,grown,tail,head);
                values=grown; head=0;
            }
            values[(head+size)&(values.length-1)]=value; size++;
        }
        long removeFirst() {
            long value=values[head];
            head=(head+1)&(values.length-1); size--; return value;
        }
    }
}
