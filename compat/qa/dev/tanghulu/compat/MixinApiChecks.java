package dev.tanghulu.compat;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;

/** Reads bytecode without initializing Minecraft, Meteor, optional mods or addon classes. */
public final class MixinApiChecks {
    private static int checks;
    private static final Map<String, ClassNode> cache = new HashMap<>();

    private static ClassNode load(String name) throws Exception {
        if (cache.containsKey(name)) return cache.get(name);
        try (var in = MixinApiChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (in == null) return null;
            var node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            cache.put(name, node);
            return node;
        }
    }

    private static Object value(AnnotationNode a, String key, Object fallback) {
        if (a.values != null) for (int i = 0; i < a.values.size(); i += 2)
            if (a.values.get(i).equals(key)) return a.values.get(i + 1);
        return fallback;
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        var result = new ArrayList<AnnotationNode>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }

    private static List<MethodNode> methods(ClassNode target, String selector) {
        int split = selector.indexOf('(');
        String name = split < 0 ? selector : selector.substring(0, split);
        String desc = split < 0 ? null : selector.substring(split);
        return target.methods.stream().filter(m -> m.name.equals(name) && (desc == null || m.desc.equals(desc))).toList();
    }

    private static boolean member(ClassNode target, String name, String desc, boolean field) throws Exception {
        if (target == null) return false;
        if (field ? target.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(desc))
                  : target.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc))) return true;
        if (target.superName != null && member(load(target.superName), name, desc, field)) return true;
        for (String iface : target.interfaces) if (member(load(iface), name, desc, field)) return true;
        return false;
    }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    @SuppressWarnings("unchecked")
    private static void inspect(ClassNode mixin) throws Exception {
        var classAnnotations = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations);
        var marker = classAnnotations.stream().filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst();
        if (marker.isEmpty()) return;
        // @Pseudo integrations deliberately accept multiple optional Xaero APIs (require=0).
        if (classAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Pseudo;"))) return;
        var targets = new ArrayList<String>();
        for (Type t : (List<Type>) value(marker.get(), "value", List.of())) targets.add(t.getInternalName());
        for (String t : (List<String>) value(marker.get(), "targets", List.of())) targets.add(t.replace('.', '/'));
        for (String name : targets) {
            ClassNode target = load(name);
            check(target != null, "Missing mixin target: " + name);
            for (FieldNode f : mixin.fields) for (AnnotationNode a : annotations(f.visibleAnnotations, f.invisibleAnnotations)) {
                if (a.desc.endsWith("/Shadow;")) check(member(target, f.name, f.desc, true), mixin.name + " shadow " + f.name + f.desc);
            }
            for (MethodNode m : mixin.methods) for (AnnotationNode a : annotations(m.visibleAnnotations, m.invisibleAnnotations)) {
                if (a.desc.endsWith("/Shadow;")) check(member(target, m.name, m.desc, false), mixin.name + " shadow " + m.name + m.desc);
                if (a.desc.endsWith("/Invoker;")) {
                    String method = (String) value(a, "value", "");
                    check(member(target, method, m.desc, false), mixin.name + " invoker " + method + m.desc);
                }
                if (a.desc.endsWith("/Accessor;")) {
                    String field = (String) value(a, "value", "");
                    Type[] args = Type.getArgumentTypes(m.desc);
                    String desc = args.length == 0 ? Type.getReturnType(m.desc).getDescriptor() : args[0].getDescriptor();
                    check(member(target, field, desc, true), mixin.name + " accessor " + field + desc);
                }
                if (!(a.desc.endsWith("/Inject;") || a.desc.endsWith("/Redirect;") || a.desc.endsWith("/ModifyExpressionValue;"))) continue;
                if ((Integer) value(a, "require", -1) == 0) continue;
                for (String selector : (List<String>) value(a, "method", List.of())) {
                    List<MethodNode> selected = methods(target, selector);
                    check(!selected.isEmpty(), mixin.name + ": missing " + name + "." + selector);
                    Object at = value(a, "at", List.of());
                    List<AnnotationNode> points = at instanceof AnnotationNode point ? List.of(point) : (List<AnnotationNode>) at;
                    for (AnnotationNode point : points) {
                        String kind = (String) value(point, "value", "");
                        String reference = (String) value(point, "target", "");
                        if (!(kind.equals("INVOKE") || kind.equals("FIELD")) || reference.isEmpty()) continue;
                        int count = 0;
                        for (MethodNode selectedMethod : selected) for (var insn : selectedMethod.instructions) {
                            String actual = "";
                            if (insn instanceof MethodInsnNode call) actual = "L" + call.owner + ";" + call.name + call.desc;
                            if (insn instanceof FieldInsnNode field) actual = "L" + field.owner + ";" + field.name + ":" + field.desc;
                            if (actual.equals(reference)) count++;
                        }
                        int ordinal = (Integer) value(point, "ordinal", -1);
                        check(count > Math.max(ordinal, 0), mixin.name + ": " + selector + " missing " + reference + " ordinal " + ordinal);
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        for (String root : args) try (var paths = Files.walk(Path.of(root))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".class") && p.toString().contains("mixin")).toList()) {
                var node = new ClassNode();
                new ClassReader(Files.readAllBytes(path)).accept(node, 0);
                inspect(node);
            }
        }
        System.out.println("Mixin API targets, shadows, accessors, invokers and injection points passed: " + checks);
    }
}
