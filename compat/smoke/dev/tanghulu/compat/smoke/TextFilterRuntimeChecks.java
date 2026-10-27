package dev.tanghulu.compat.smoke;

import dev.tanghulu.addon.modules.InformationTextFilter;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.NameProtect;
import net.minecraft.block.entity.SignText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/** Uses the packaged addon and real Fabric/Meteor classes, without opening a world. */
public final class TextFilterRuntimeChecks {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Object unsafe = field(unsafeType, "theUnsafe").get(null);
        return type.cast(unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type));
    }

    @SuppressWarnings("unchecked")
    private static <T> void setting(InformationTextFilter module, String name, T value) throws Exception {
        ((Setting<T>) field(InformationTextFilter.class, name).get(module)).set(value);
    }

    private static InformationTextFilter fixture() throws Exception {
        // Meteor's module/setting constructors initialize GPU-backed Utils in preLaunch.
        // Allocate real settings with controlled values; their get/set paths still run normally.
        var filter = allocate(InformationTextFilter.class);
        for (Field entry : InformationTextFilter.class.getDeclaredFields()) {
            if (entry.getType() != Setting.class) continue;
            entry.setAccessible(true);
            Class<?> settingType = switch (entry.getName()) {
                case "mode" -> EnumSetting.class;
                case "blocked", "allowed" -> StringListSetting.class;
                default -> BoolSetting.class;
            };
            Object setting = allocate(settingType);
            Object value = switch (entry.getName()) {
                case "mode" -> InformationTextFilter.Mode.Hide;
                case "blocked", "allowed" -> List.of();
                default -> Boolean.TRUE;
            };
            field(Setting.class, "value").set(setting, value);
            if (List.of("ads", "abuse", "politics", "sexual", "gambling", "drugs", "links", "ignoreSeparators", "blocked", "allowed").contains(entry.getName())) {
                field(Setting.class, "onChanged").set(setting,
                    (java.util.function.Consumer<Object>) ignored -> filter.onActivate());
            }
            entry.set(filter, setting);
        }
        return filter;
    }

    private static String plain(OrderedText text) {
        StringBuilder result = new StringBuilder();
        text.accept((index, style, codePoint) -> { result.appendCodePoint(codePoint); return true; });
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    public static void run() throws Exception {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
        var systems = (Map<Class<?>, Object>) field(Systems.class, "systems").get(null);
        Object previous = systems.get(Modules.class);
        var modules = new Modules();
        var instances = (Map<Class<?>, Module>) field(Modules.class, "moduleInstances").get(modules);
        var filter = fixture();
        instances.put(InformationTextFilter.class, filter);
        // Meteor's text visitor consults NameProtect even when it is inactive.
        instances.put(NameProtect.class, allocate(NameProtect.class));
        systems.put(Modules.class, modules);
        Field active = field(Module.class, "active");
        var receive = InformationTextFilter.class.getDeclaredMethod("onReceiveMessage", ReceiveMessageEvent.class);
        receive.setAccessible(true);
        try {
            Text spam = Text.literal("加群 12345").append(Text.literal(" 广告"))
                .styled(style -> style.withHoverEvent(new net.minecraft.text.HoverEvent.ShowText(Text.literal("原始广告"))));
            Text normal = Text.literal("你好，获得钻石");
            check(InformationTextFilter.filter(InformationTextFilter.Source.Chat, spam) == spam, "Inactive module preserves text");
            active.setBoolean(filter, true);
            check(InformationTextFilter.filter(InformationTextFilter.Source.Chat, spam).getString().isEmpty(), "Hide mode");
            var event = ReceiveMessageEvent.get(spam, null, 0);
            receive.invoke(filter, event);
            check(event.isCancelled(), "Hide cancels chat");

            setting(filter, "mode", InformationTextFilter.Mode.Asterisk);
            for (var source : InformationTextFilter.Source.values()) {
                Text result = InformationTextFilter.filter(source, spam);
                check(result.getString().equals("*"), "Asterisk for " + source);
                check(InformationTextFilter.filter(source, spam.getString()).equals("*"), "String asterisk for " + source);
                check(!InformationTextFilter.hides(source, spam), "Asterisk remains visible for " + source);
                check(result.getStyle().isEmpty() && result.getSiblings().isEmpty(), "No original metadata for " + source);
                check(InformationTextFilter.filter(source, normal) == normal, "Normal text for " + source);
            }
            event = ReceiveMessageEvent.get(spam, null, 0);
            receive.invoke(filter, event);
            check(!event.isCancelled() && event.isModified() && event.getMessage().getString().equals("*"), "Chat is replaced, not cancelled");

            var sign = new SignText().withMessage(0, Text.literal("加"))
                .withMessage(1, Text.literal("群 12345")).withMessage(2, normal);
            Text[] raw = sign.getMessages(false);
            Text[] shown = InformationTextFilter.filterSign(raw);
            check(shown[0].getString().equals("*") && shown[1].getString().isEmpty()
                && shown[2].getString().isEmpty() && shown[3].getString().isEmpty(), "Multiline sign masks entire face");
            var ordered = sign.getOrderedMessages(false, Text::asOrderedText);
            check(plain(ordered[0]).equals("*") && plain(ordered[1]).isEmpty(), "Actual sign mixin applies asterisk");
            check(sign.getMessages(false) == raw && raw[0].getString().equals("加") && raw[2] == normal, "Sign data unchanged");

            setting(filter, "mode", InformationTextFilter.Mode.Hide);
            check(InformationTextFilter.filterSign(raw) == null, "Sign hide mode");
            check(plain(sign.getOrderedMessages(false, Text::asOrderedText)[0]).isEmpty(), "Actual sign mixin hides");
            setting(filter, "mode", InformationTextFilter.Mode.Asterisk);
            check(plain(sign.getOrderedMessages(false, Text::asOrderedText)[0]).equals("*"), "Mode change bypasses sign cache");
            setting(filter, "signs", false);
            check(plain(sign.getOrderedMessages(false, Text::asOrderedText)[0]).equals("加"), "Scope off restores original sign");
            setting(filter, "signs", true);
            check(plain(sign.getOrderedMessages(false, Text::asOrderedText)[0]).equals("*"), "Scope on masks cached sign");

            setting(filter, "chat", false);
            check(InformationTextFilter.filter(InformationTextFilter.Source.Chat, spam) == spam, "Chat scope off");
            event = ReceiveMessageEvent.get(spam, null, 0);
            receive.invoke(filter, event);
            check(!event.isCancelled() && !event.isModified(), "Disabled chat scope preserves event");
            setting(filter, "chat", true);
            setting(filter, "allowed", List.of("加群"));
            check(InformationTextFilter.filter(InformationTextFilter.Source.Chat, spam) == spam, "Whitelist wins in asterisk mode");
            setting(filter, "allowed", List.of());
            check(InformationTextFilter.filter(InformationTextFilter.Source.Chat, spam).getString().equals("*"), "Whitelist change invalidates cache");

            Map<String, String> categories = Map.of("politics", "招募成员参与暴力政变", "sexual", "成人视频资源",
                "gambling", "博彩平台", "drugs", "冰毒现货");
            for (var category : categories.entrySet()) {
                Text sample = Text.literal(category.getValue());
                for (var source : InformationTextFilter.Source.values()) {
                    check(InformationTextFilter.filter(source, sample).getString().equals("*"), "Content mode for " + category.getKey() + ": " + source);
                }
                setting(filter, category.getKey(), false);
                check(InformationTextFilter.filter(InformationTextFilter.Source.Chat, sample) == sample, "Category off invalidates blocked cache");
                setting(filter, category.getKey(), true);
                check(InformationTextFilter.filter(InformationTextFilter.Source.Chat, sample).getString().equals("*"), "Category on invalidates allowed cache");
                setting(filter, "allowed", List.of(category.getValue()));
                check(InformationTextFilter.filter(InformationTextFilter.Source.Chat, sample) == sample, "Category whitelist");
                setting(filter, "allowed", List.of());
                setting(filter, "mode", InformationTextFilter.Mode.Hide);
                event = ReceiveMessageEvent.get(sample, null, 0);
                receive.invoke(filter, event);
                check(event.isCancelled(), "Content chat hide mode");
                setting(filter, "mode", InformationTextFilter.Mode.Asterisk);
                event = ReceiveMessageEvent.get(sample, null, 0);
                receive.invoke(filter, event);
                check(!event.isCancelled() && event.getMessage().getString().equals("*"), "Content chat asterisk mode");
            }
            var contentSign = new SignText().withMessage(0, Text.literal("冰 毒"))
                .withMessage(1, Text.literal("现货"));
            check(plain(contentSign.getOrderedMessages(false, Text::asOrderedText)[0]).equals("*"), "Drug category through actual sign mixin");
            setting(filter, "drugs", false);
            check(plain(contentSign.getOrderedMessages(false, Text::asOrderedText)[0]).equals("冰 毒"), "Disabling category restores actual cached sign");
            setting(filter, "drugs", true);
            active.setBoolean(filter, false);
            check(InformationTextFilter.filter(InformationTextFilter.Source.Chat, spam) == spam, "Module off restores text");
            check(plain(sign.getOrderedMessages(false, Text::asOrderedText)[0]).equals("加"), "Module off restores cached sign");
            check(InformationTextFilter.filter(InformationTextFilter.Source.Chat, (Text) null) == null, "Null text safe");
            System.out.println("TEXT_FILTER_RUNTIME_CHECKS PASS checks=" + checks);
        } finally {
            if (previous == null) systems.remove(Modules.class);
            else systems.put(Modules.class, previous);
        }
    }
}
