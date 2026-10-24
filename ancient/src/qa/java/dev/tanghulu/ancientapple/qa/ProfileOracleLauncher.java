package dev.tanghulu.ancientapple.qa;

import net.fabricmc.loader.impl.transformer.PackageAccessFixer;
import org.objectweb.asm.*;
import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;

/** Apply Fabric's normal package-access fixes without starting a game or network listener. */
public final class ProfileOracleLauncher {
    public static void main(String[] args) throws Exception {
        URL[] urls=Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator)).map(p -> {
            try { return Path.of(p).toUri().toURL(); } catch(MalformedURLException e) { throw new UncheckedIOException(e); }
        }).toArray(URL[]::new);
        try(URLClassLoader isolated=new URLClassLoader(urls,ClassLoader.getPlatformClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                if(!name.startsWith("net.minecraft."))return super.findClass(name);
                URL resource=findResource(name.replace('.','/')+".class"); if(resource==null)throw new ClassNotFoundException(name);
                try(var stream=resource.openStream()) {
                    ClassReader reader=new ClassReader(stream); ClassWriter writer=new ClassWriter(0);
                    reader.accept(new PackageAccessFixer(Opcodes.ASM9,writer),0);
                    byte[] bytes=writer.toByteArray(); return defineClass(name,bytes,0,bytes.length);
                } catch(IOException e) { throw new ClassNotFoundException(name,e); }
            }
        }) {
            Thread.currentThread().setContextClassLoader(isolated);
            isolated.loadClass("dev.tanghulu.ancientapple.qa.LootProfilesOracle").getMethod("main",String[].class).invoke(null,(Object)args);
        }
    }
}
