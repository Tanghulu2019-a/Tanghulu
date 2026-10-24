package dev.tanghulu.ancientapple.integration;

import dev.tanghulu.ancientapple.core.ChestTarget;
import dev.tanghulu.ancientapple.core.MarkerSelection;
import net.fabricmc.loader.api.FabricLoader;
import java.lang.reflect.*;
import java.util.Collection;
import java.util.ArrayList;
import net.minecraft.util.Identifier;

/** Optional integration: only Xaero's public non-obfuscated API is reflected. */
public final class XaeroBridge {
    public static final String MARKER_NAME="附魔金苹果";
    public static final String MARKER_SYMBOL="◆";
    private static final String GROUP="古城附魔金苹果箱";
    private Object ownedWorld,ownedSet;
    private String previousSet;
    private static final Identifier ORIGIN=Identifier.of("ancient-apple-hunter","chests");
    public String status="未连接";
    /** Match ownership, never waypoint name or symbol: user waypoints stay untouched. */
    public static boolean isOwnedWaypoint(Object waypoint) {
        if(waypoint==null)return false;
        try { return ORIGIN.equals(call(waypoint,"getThirdPartyOrigin")); }
        catch(ReflectiveOperationException|LinkageError|RuntimeException e) { return false; }
    }
    public String worldId() {
        try { Object world=currentWorld(); return world==null?"":call(world,"getFullPath").toString(); }
        catch(ReflectiveOperationException|LinkageError e) { return ""; }
    }
    public void sync(Collection<ChestTarget> targets,boolean showCompleted) {
        if(!FabricLoader.getInstance().isModLoaded("xaerominimap")) { status="未安装 Xaero，使用世界内标记"; return; }
        try {
            Object world=currentWorld(); if(world==null) { status="等待 Xaero 世界会话"; return; }
            if(world!=ownedWorld) {
                release(); ownedWorld=world; previousSet=(String)call(world,"getCurrentWaypointSetId");
                String name=GROUP+" [AAH]";
                ownedSet=call(world,"getWaypointSet",new Class<?>[]{String.class},name);
                if(ownedSet==null) { call(world,"addWaypointSet",new Class<?>[]{String.class},name); ownedSet=call(world,"getWaypointSet",new Class<?>[]{String.class},name); }
                call(world,"setCurrentWaypointSetId",new Class<?>[]{String.class},name);
            }
            removeOwned();
            Class<?> type=Class.forName("xaero.common.minimap.waypoints.Waypoint");
            Constructor<?> constructor=type.getConstructor(int.class,int.class,int.class,String.class,String.class,int.class);
            for(ChestTarget target:targets) {
                if(!shouldMark(target,showCompleted))continue;
                Object waypoint=constructor.newInstance(target.x,target.y,target.z,MARKER_NAME,MARKER_SYMBOL,6);
                call(waypoint,"setTemporary",new Class<?>[]{boolean.class},true);
                call(waypoint,"setYIncluded",new Class<?>[]{boolean.class},true);
                call(waypoint,"setThirdPartyOrigin",new Class<?>[]{Identifier.class},ORIGIN);
                call(ownedSet,"add",new Class<?>[]{type},waypoint);
            }
            status="已同步";
        } catch(ReflectiveOperationException|LinkageError|RuntimeException e) { status="Xaero 接口不兼容: "+e.getClass().getSimpleName(); }
    }
    public void release() {
        if(ownedWorld!=null) try {
            removeOwned();
            if((GROUP+" [AAH]").equals(call(ownedWorld,"getCurrentWaypointSetId")) && previousSet!=null)
                call(ownedWorld,"setCurrentWaypointSetId",new Class<?>[]{String.class},previousSet);
        } catch(ReflectiveOperationException|RuntimeException ignored) { }
        ownedWorld=null; ownedSet=null; previousSet=null;
    }
    private Object currentWorld() throws ReflectiveOperationException {
        Object session=Class.forName("xaero.common.XaeroMinimapSession").getMethod("getCurrentSession").invoke(null);
        if(session==null) return null;
        Object minimap=call(call(session,"getMinimapProcessor"),"getSession");
        return call(call(minimap,"getWorldManager"),"getCurrentWorld");
    }
    private void removeOwned() throws ReflectiveOperationException {
        if(ownedSet==null)return;
        ArrayList<Object> remove=new ArrayList<>();
        for(Object waypoint:(Iterable<?>)call(ownedSet,"getWaypoints"))if(ORIGIN.equals(call(waypoint,"getThirdPartyOrigin")))remove.add(waypoint);
        Class<?> type=Class.forName("xaero.common.minimap.waypoints.Waypoint");
        for(Object waypoint:remove)call(ownedSet,"remove",new Class<?>[]{type},waypoint);
    }
    private static Object call(Object target,String name) throws ReflectiveOperationException { return call(target,name,new Class<?>[0]); }
    private static Object call(Object target,String name,Class<?>[] types,Object... args) throws ReflectiveOperationException { return target.getClass().getMethod(name,types).invoke(target,args); }
    /** Collection blacklists are absolute, including when completed records are displayed. */
    public static boolean shouldMark(ChestTarget target,boolean showCompleted) {
        return MarkerSelection.shouldMark(target,showCompleted);
    }
    public static String label(ChestTarget.Status status) {
        return switch(status) { case PREDICTED -> "预测"; case CONFIRMED -> "确认"; case COLLECTED -> "已搜取"; case EMPTY -> "空箱"; case MISSING -> "缺失"; case MISMATCH -> "不符"; case UNREACHABLE -> "不可达"; case DEFERRED -> "待重试"; };
    }
    public static String label(ChestTarget target) { return target.pendingVerification() && target.status==ChestTarget.Status.PREDICTED?"待核验":label(target.status); }
}
