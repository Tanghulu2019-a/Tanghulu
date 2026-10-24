package dev.tanghulu.ancientapple.prediction;

import net.fabricmc.loader.impl.transformer.PackageAccessFixer;
import org.objectweb.asm.*;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.file.Path;

/** Child-first vanilla class isolation avoids applying the live game's mixins or lifecycle
 * listeners to a prediction world. The boundary uses only JDK Path/long/Long types. */
public final class TerrainBridge implements AutoCloseable {
    private final Object context;
    private final Method read,finish;
    private TerrainBridge(Object context,Method read,Method finish) { this.context=context; this.read=read; this.finish=finish; }
    public static TerrainBridge open(Path path,long seed) throws Exception {
        ClassLoader resources=TerrainBridge.class.getClassLoader();
        ClassLoader isolated=new ClassLoader(resources) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(!name.startsWith("net.minecraft.") && !name.startsWith(ScratchTerrain.class.getName()) && !name.equals(ScratchBootstrap.class.getName()))return super.loadClass(name,resolve);
                synchronized(getClassLoadingLock(name)) {
                    Class<?> loaded=findLoadedClass(name);
                    if(loaded==null) {
                        try(var stream=resources.getResourceAsStream(name.replace('.','/')+".class")) {
                            if(stream==null)throw new ClassNotFoundException(name);
                            byte[] bytes=stream.readAllBytes();
                            if(name.startsWith("net.minecraft.")) {
                                ClassReader reader=new ClassReader(bytes); ClassWriter writer=new ClassWriter(0);
                                reader.accept(new PackageAccessFixer(Opcodes.ASM9,writer),0); bytes=writer.toByteArray();
                            }
                            loaded=defineClass(name,bytes,0,bytes.length);
                        } catch(IOException e) { throw new ClassNotFoundException(name,e); }
                    }
                    if(resolve)resolveClass(loaded); return loaded;
                }
            }
        };
        Thread thread=Thread.currentThread(); ClassLoader previous=thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(isolated);
            Class<?> bootstrap=isolated.loadClass(ScratchBootstrap.class.getName());
            Object context=bootstrap.getMethod("open",Path.class,long.class).invoke(null,path,seed);
            return new TerrainBridge(context,context.getClass().getMethod("lootSeedAt",int.class,int.class,int.class),bootstrap.getMethod("finish",context.getClass()));
        } catch(InvocationTargetException e) { throw new IllegalStateException("原版地形补算环境初始化失败",e.getCause()); }
        finally { thread.setContextClassLoader(previous); }
    }
    public Long lootSeedAt(int x,int y,int z) throws Exception {
        Thread thread=Thread.currentThread(); ClassLoader previous=thread.getContextClassLoader();
        try { thread.setContextClassLoader(context.getClass().getClassLoader()); return (Long)read.invoke(context,x,y,z); }
        catch(InvocationTargetException e) { throw new IllegalStateException("原版区块补算失败",e.getCause()); }
        finally { thread.setContextClassLoader(previous); }
    }
    @Override public void close() {
        boolean interrupted=Thread.interrupted();
        Thread thread=Thread.currentThread(); ClassLoader previous=thread.getContextClassLoader();
        try { thread.setContextClassLoader(context.getClass().getClassLoader()); finish.invoke(null,context); }
        catch(ReflectiveOperationException e) { throw new IllegalStateException("补算缓存关闭失败",e); }
        finally { thread.setContextClassLoader(previous); if(interrupted)Thread.currentThread().interrupt(); }
    }
}
