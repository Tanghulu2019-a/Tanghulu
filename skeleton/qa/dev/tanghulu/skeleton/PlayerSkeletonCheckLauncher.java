package dev.tanghulu.skeleton;

import net.fabricmc.loader.impl.transformer.PackageAccessFixer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;

/** Match Fabric's normal package access fixes when testing the named Minecraft JAR. */
public final class PlayerSkeletonCheckLauncher {
    public static void main(String[] args) throws Exception {
        URL[] urls = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator)).map(path -> {
            try { return Path.of(path).toUri().toURL(); }
            catch (MalformedURLException exception) { throw new UncheckedIOException(exception); }
        }).toArray(URL[]::new);
        try (URLClassLoader isolated = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (!name.startsWith("net.minecraft.")) return super.findClass(name);
                URL resource = findResource(name.replace('.', '/') + ".class");
                if (resource == null) throw new ClassNotFoundException(name);
                try (var stream = resource.openStream()) {
                    ClassReader reader = new ClassReader(stream);
                    ClassWriter writer = new ClassWriter(0);
                    reader.accept(new PackageAccessFixer(Opcodes.ASM9, writer), 0);
                    byte[] bytes = writer.toByteArray();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (IOException exception) { throw new ClassNotFoundException(name, exception); }
            }
        }) {
            Thread.currentThread().setContextClassLoader(isolated);
            isolated.loadClass("dev.tanghulu.skeleton.PlayerSkeletonChecks")
                .getMethod("main", String[].class).invoke(null, (Object) args);
        }
    }
}
