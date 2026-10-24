package dev.tanghulu.mushroom;

/** Synthetic geography tests isolate complete enumeration and component deduplication from vanilla noise. */
public final class IslandScannerAudit {
    static int checks;
    static void check(boolean condition,String message) { checks++; if(!condition)throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        var two=IslandScanner.scan(0,0,128,8,(x,z)->Math.abs(z)<=24 && ((x>=-88 && x<=-32)||(x>=32 && x<=88)),
            (x,z)->true,p->{});
        check(two.size()==2,"must return both islands, not every sampled point");
        check(two.stream().anyMatch(p->p[0]<0) && two.stream().anyMatch(p->p[0]>0),"missing island");
        var oceanStrip=IslandScanner.scan(0,0,128,32,(x,z)->Math.abs(x)<=64 && Math.abs(z)<=32 && x!=16,
            (x,z)->true,p->{});
        check(oceanStrip.size()==2,"coarse sampling joined islands across a narrow ocean strip");
        var neck=IslandScanner.scan(0,0,128,32,(x,z)->(Math.abs(x)>=32 && Math.abs(x)<=64 && Math.abs(z)<=16)
            || (Math.abs(x)<=32 && z==4),(x,z)->true,p->{});
        check(neck.size()==1,"narrow land connection split one island into multiple markers");
        var connected=IslandScanner.scan(-96,-96,64,8,(x,z)->true,(x,z)->true,p->{});
        check(connected.size()==1,"one island has multiple markers");
        var clipped=IslandScanner.scan(0,0,32,8,(x,z)->x==32 && z==32,(x,z)->true,p->{});
        check(clipped.isEmpty(),"square corner escaped circular radius");
        var empty=IslandScanner.scan(0,0,64,8,(x,z)->false,(x,z)->true,p->{});
        check(empty.isEmpty(),"false island in empty ocean");
        var unsafe=IslandScanner.scan(0,0,64,8,(x,z)->true,(x,z)->false,p->{});
        check(unsafe.isEmpty(),"unverified shore point emitted");
        try { IslandScanner.scan(0,0,100000,4,(x,z)->true,(x,z)->true,p->{}); throw new AssertionError("unbounded memory accepted"); }
        catch(IllegalArgumentException expected) { checks++; }
        System.out.println("ISLAND_SCANNER_AUDIT checks="+checks);
    }
}
