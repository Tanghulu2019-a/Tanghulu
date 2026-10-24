package dev.tanghulu.threec;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.util.ArrayList;
import java.util.List;

/** Check the shipped Meteor bytecode, including that both ordinal selectors refer to the name. */
public final class ThreeCNametagChecks {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static ClassNode read(String name) throws Exception {
        try (var in = ThreeCNametagChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (in == null) throw new AssertionError("Missing class: " + name);
            var node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        }
    }
    private static AbstractInsnNode previous(AbstractInsnNode node, int count) {
        for (int i = 0; i < count; i++) {
            do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        }
        return node;
    }
    public static void run() throws Exception {
        var meteor = read("meteordevelopment/meteorclient/systems/modules/render/Nametags");
        var mixin = read("dev/tanghulu/addon/mixin/ThreeCNametagsMixin");
        var target = meteor.methods.stream().filter(m -> m.name.equals("renderNametagPlayer")).findFirst().orElseThrow();
        var inject = mixin.methods.stream().filter(m -> m.name.equals("tanghulu$selectBadge")).findFirst().orElseThrow();
        check(inject.desc.equals(target.desc.replace(")V", "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V")),
            "HEAD injection signature matches actual Meteor player renderer");
        List<MethodInsnNode> widths = new ArrayList<>(), renders = new ArrayList<>();
        for (var instruction : target.instructions) {
            if (!(instruction instanceof MethodInsnNode call)
                || !call.owner.equals("meteordevelopment/meteorclient/renderer/text/TextRenderer")) continue;
            if (call.name.equals("getWidth") && call.desc.equals("(Ljava/lang/String;Z)D")) widths.add(call);
            if (call.name.equals("render") && call.desc.equals("(Ljava/lang/String;DDLmeteordevelopment/meteorclient/utils/render/color/Color;Z)D")) renders.add(call);
        }
        check(widths.size() >= 5 && renders.size() >= 6, "Expected text width and render calls exist");
        var widthName = previous(widths.get(1), 2);
        var renderName = previous(renders.get(1), 5);
        check(widthName instanceof VarInsnNode && renderName instanceof VarInsnNode
            && ((VarInsnNode) widthName).var == ((VarInsnNode) renderName).var,
            "Both ordinal 1 redirects use the same player name local");
        int redirects = 0;
        for (var method : mixin.methods) {
            if (method.visibleAnnotations == null) continue;
            for (var annotation : method.visibleAnnotations) {
                if (!annotation.desc.endsWith("/Redirect;")) continue;
                redirects++;
                check(annotation.values.get(annotation.values.indexOf("method") + 1).equals(List.of("renderNametagPlayer")),
                    "Redirect selects player renderer");
                int at = annotation.values.indexOf("at");
                var point = (AnnotationNode) annotation.values.get(at + 1);
                check(point.values.get(point.values.indexOf("ordinal") + 1).equals(1), "Redirect uses verified name ordinal");
                String selector = (String) point.values.get(point.values.indexOf("target") + 1);
                var expected = method.name.endsWith("badgeWidth") ? widths.get(1) : renders.get(1);
                check(selector.equals("L" + expected.owner + ";" + expected.name + expected.desc), "Exact renderer invocation descriptor");
            }
        }
        check(redirects == 2, "Layout and draw redirects both present");
        System.out.println("3C nametag bytecode checks passed: " + checks);
    }
}
