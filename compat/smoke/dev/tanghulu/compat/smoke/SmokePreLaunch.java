package dev.tanghulu.compat.smoke;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;

/** Test-only Fabric mod. Exits in preLaunch, before Minecraft opens a window. */
public final class SmokePreLaunch implements PreLaunchEntrypoint {
    @Override public void onPreLaunch() {
        try {
            var targets = new TreeSet<String>();
            var addonClasses = new TreeSet<String>();
            for (String root : System.getProperty("tanghulu.smoke.classes").split(java.io.File.pathSeparator)) {
                Path base = Path.of(root);
                if (Files.isRegularFile(base)) {
                    try (var jar = new java.util.jar.JarFile(base.toFile())) {
                        for (var entry : jar.stream().filter(e -> e.getName().startsWith("dev/tanghulu/") && e.getName().endsWith(".class")).toList()) {
                            try (var in = jar.getInputStream(entry)) { scan(in.readAllBytes(), targets, addonClasses); }
                        }
                    }
                    continue;
                }
                try (var paths = Files.walk(base)) {
                    for (Path path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                        scan(Files.readAllBytes(path), targets, addonClasses);
                    }
                }
            }
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            for (String target : targets) Class.forName(target, false, loader);
            boolean baritone = Boolean.getBoolean("tanghulu.smoke.baritone");
            if (baritone) {
                Class<?> mine = Class.forName("baritone.process.MineProcess", false, loader);
                var methods = Arrays.stream(mine.getDeclaredMethods()).map(java.lang.reflect.Method::getName).toList();
                if (methods.stream().noneMatch(n -> n.contains("onRescan"))
                    || methods.stream().noneMatch(n -> n.contains("onPruneStream"))) {
                    throw new AssertionError("SeedMine Baritone mixins were not applied");
                }
                if (Boolean.getBoolean("tanghulu.smoke.mikuConflict")
                    && methods.stream().noneMatch(n -> n.contains("tanghuluMiku$prune"))) {
                    throw new AssertionError("Miku conflict fixture was not applied");
                }
            }
            int optionalApisAbsent = 0;
            for (String name : addonClasses) {
                // No initialization: isolated prediction helpers use their own vanilla classloader.
                try {
                    Class<?> type = Class.forName(name, false, loader);
                    type.getDeclaredMethods();
                    type.getDeclaredConstructors();
                } catch (NoClassDefFoundError absent) {
                    String dependency = absent.getMessage();
                    if (dependency == null || !(dependency.startsWith("baritone/") || dependency.startsWith("xaero/")
                        || dependency.startsWith("xaeroplus/"))) throw absent;
                    optionalApisAbsent++;
                }
            }
            if (baritone) MiningRuntimeChecks.run();
            TextFilterRuntimeChecks.run();
            if (baritone) BaritoneMixinRuntimeChecks.run();
            TreeRuntimeChecks.run();
            TreeCollisionRuntimeChecks.run();
            if (baritone) TreeNavigationRuntimeChecks.run();
            String result = "FABRIC_COMPAT_SMOKE PASS minecraft=" + System.getProperty("tanghulu.smoke.minecraft")
                + " mixinTargets=" + targets.size() + " addonClasses=" + addonClasses.size()
                + " optionalApisAbsent=" + optionalApisAbsent + " baritone=" + baritone;
            System.out.println(result);
            Files.writeString(Path.of("smoke-result.txt"), result + "\n");
            Runtime.getRuntime().halt(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            Runtime.getRuntime().halt(1);
        }
    }

    private static void scan(byte[] bytes, Set<String> targets, Set<String> addonClasses) {
        var node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE);
        if (!node.name.contains("/mixin/") && !node.name.contains("/vendor/")) addonClasses.add(node.name.replace('/', '.'));
        var annotations = new ArrayList<AnnotationNode>();
        if (node.visibleAnnotations != null) annotations.addAll(node.visibleAnnotations);
        if (node.invisibleAnnotations != null) annotations.addAll(node.invisibleAnnotations);
        if (annotations.stream().anyMatch(a -> a.desc.endsWith("/Pseudo;"))) return;
        for (var a : annotations) if (a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
            for (int i = 0; i < a.values.size(); i += 2) if (a.values.get(i).equals("value"))
                for (Object type : (List<?>) a.values.get(i + 1)) targets.add(((Type) type).getClassName());
        }
    }
}
