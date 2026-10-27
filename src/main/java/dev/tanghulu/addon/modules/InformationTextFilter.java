package dev.tanghulu.addon.modules;

import dev.tanghulu.addon.BaseModule;
import dev.tanghulu.addon.util.textfilter.TextFilterRules;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.text.Text;

import java.util.List;

/** All entry points consult current settings, so render filtering is reversible. */
public final class InformationTextFilter extends Module {
    public enum Source { Chat, ActionBar, Title, Sign, EntityName, TextDisplay }

    public enum Mode {
        Hide("隐藏"), Asterisk("替换为 *");

        private final String label;
        Mode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private final Setting<Mode> mode = settings.getDefaultGroup().add(new EnumSetting.Builder<Mode>()
        .name("过滤模式").description("隐藏命中的整条文字，或将其替换为一个 *；告示牌按整面判断。")
        .defaultValue(Mode.Hide).build());

    private final SettingGroup sgScope = settings.createGroup("过滤范围");
    private final SettingGroup sgRules = settings.createGroup("过滤规则");
    private final Setting<Boolean> chat = scope("聊天框", "过滤命中规则的玩家聊天和服务器消息。", true);
    private final Setting<Boolean> actionBar = scope("动作栏", "过滤快捷栏上方命中规则的提示。", true);
    private final Setting<Boolean> titles = scope("屏幕标题", "过滤命中规则的大标题和副标题。", true);
    private final Setting<Boolean> signs = scope("告示牌", "过滤命中规则的告示牌一面文字，包含悬挂告示牌和文字漂浮。", true);
    private final Setting<Boolean> names = scope("实体名称", "过滤命中规则的实体名牌，包含生物、盔甲架及 Meteor 名牌。", true);
    private final Setting<Boolean> displays = scope("文字展示实体", "过滤命中规则的 text_display 悬浮文字。", true);

    private final Setting<Boolean> ads = rule("常见广告", "按内置招新、加群、交易推广等词语过滤，可用白名单放行。", true);
    private final Setting<Boolean> abuse = rule("常见辱骂", "按内置中英文辱骂词过滤，不代表能够识别所有辱骂语义。", true);
    private final Setting<Boolean> politics = rule("涉政引战", "过滤内置政治引战短语及暴力、分裂等话题附近的招募动员词；普通政治词和人物姓名不单独屏蔽。", true);
    private final Setting<Boolean> sexual = rule("色情招嫖", "过滤内置色情资源、裸聊、招嫖等词语；可用白名单纠正误判。", true);
    private final Setting<Boolean> gambling = rule("赌博推广", "过滤赌场平台、荷官、返利等短语，以及赌博话题附近的开户、充值等推广词。", true);
    private final Setting<Boolean> drugs = rule("涉毒交易", "过滤毒品名称附近的出售、求购、现货等交易词；仅提到禁毒话题不单独屏蔽。", true);
    private final Setting<Boolean> links = rule("网址和IP", "过滤网址、常见域名和 IPv4 地址；正常分享链接也会命中，可单独关闭。", true);
    private final Setting<Boolean> ignoreSeparators = rule("忽略间隔符号", "匹配时忽略词语间插入的空格、换行和标点，防止拆字绕过。", true);
    private final Setting<List<String>> blocked = sgRules.add(new StringListSetting.Builder()
        .name("自定义屏蔽词").description("每项一个关键词，按包含匹配，不区分大小写；不是正则表达式。")
        .defaultValue(List.of()).onChanged(value -> invalidateRules()).build());
    private final Setting<List<String>> allowed = sgRules.add(new StringListSetting.Builder()
        .name("白名单").description("每项一个关键词；整条文字包含任一白名单词即放行，优先于所有屏蔽规则。")
        .defaultValue(List.of()).onChanged(value -> invalidateRules()).build());

    private TextFilterRules rules;

    public InformationTextFilter() {
        super(BaseModule.CATEGORY, "信息文字过滤", "按可配置规则在本地隐藏垃圾文字，或将其替换为 *。",
            "Information Text Filter", "text-filter", "信息过滤");
    }

    private Setting<Boolean> scope(String name, String description, boolean value) {
        return sgScope.add(new BoolSetting.Builder().name(name).description(description).defaultValue(value).build());
    }

    private Setting<Boolean> rule(String name, String description, boolean value) {
        return sgRules.add(new BoolSetting.Builder().name(name).description(description).defaultValue(value)
            .onChanged(ignored -> invalidateRules()).build());
    }

    private void invalidateRules() { rules = null; }

    @Override public void onActivate() { invalidateRules(); }
    @Override public void onDeactivate() { invalidateRules(); }

    private boolean enabled(Source source) {
        return isActive() && switch (source) {
            case Chat -> chat.get();
            case ActionBar -> actionBar.get();
            case Title -> titles.get();
            case Sign -> signs.get();
            case EntityName -> names.get();
            case TextDisplay -> displays.get();
        };
    }

    private boolean matches(String text) {
        if (rules == null) rules = new TextFilterRules(ads.get(), abuse.get(), links.get(),
            ignoreSeparators.get(), politics.get(), sexual.get(), gambling.get(), drugs.get(), blocked.get(), allowed.get());
        return rules.blocks(text);
    }

    public static boolean blocks(Source source, Text text) {
        return text != null && blocks(source, text.getString());
    }

    public static boolean blocks(Source source, String text) {
        InformationTextFilter module = getModule();
        return module != null && module.enabled(source) && module.matches(text);
    }

    private static InformationTextFilter getModule() {
        Modules modules = Modules.get();
        return modules == null ? null : modules.get(InformationTextFilter.class);
    }

    public static boolean hides(Source source, Text text) {
        InformationTextFilter module = getModule();
        return module != null && module.mode.get() == Mode.Hide && blocks(source, text);
    }

    private boolean matchesSign(Text[] lines) {
        // A phrase can be spread across all four lines of a sign.
        StringBuilder joined = new StringBuilder();
        for (Text line : lines) {
            if (joined.length() > 0) joined.append('\n');
            if (line != null) joined.append(line.getString());
        }
        return matches(joined.toString());
    }

    /** Returns null for a hidden face; otherwise returns display-only lines. */
    public static Text[] filterSign(Text[] original) {
        InformationTextFilter module = getModule();
        if (original == null || module == null || !module.enabled(Source.Sign) || !module.matchesSign(original)) return original;
        if (module.mode.get() == Mode.Hide) return null;
        Text[] result = new Text[original.length];
        for (int i = 0; i < result.length; i++) result[i] = i == 0 ? Text.literal("*") : Text.empty();
        return result;
    }

    public static Text filter(Source source, Text original) {
        InformationTextFilter module = getModule();
        if (original == null || module == null || !module.enabled(source) || !module.matches(original.getString())) return original;
        // Fresh literal drops the blocked text's siblings, hover and click events.
        return module.mode.get() == Mode.Asterisk ? Text.literal("*") : Text.empty();
    }

    public static String filter(Source source, String original) {
        InformationTextFilter module = getModule();
        if (module == null || !module.enabled(source) || !module.matches(original)) return original;
        return module.mode.get() == Mode.Asterisk ? "*" : "";
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!enabled(Source.Chat) || !matches(event.getMessage().getString())) return;
        if (mode.get() == Mode.Hide) event.cancel();
        else event.setMessage(Text.literal("*"));
    }
}
