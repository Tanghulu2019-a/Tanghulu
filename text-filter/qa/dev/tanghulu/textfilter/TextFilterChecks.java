package dev.tanghulu.textfilter;

import dev.tanghulu.addon.util.textfilter.TextFilterRules;
import java.util.List;

/** Behavioral cases, including bypasses and normal game messages that must survive. */
public final class TextFilterChecks {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static TextFilterRules rules(boolean ads, boolean abuse, boolean links, boolean gaps,
                                         List<String> blocked, List<String> allowed) {
        return new TextFilterRules(ads, abuse, links, gaps, blocked, allowed);
    }

    private static TextFilterRules content(boolean politics, boolean sexual, boolean gambling, boolean drugs,
                                           boolean gaps, List<String> allowed) {
        return new TextFilterRules(false, false, false, gaps, politics, sexual, gambling, drugs, List.of(), allowed);
    }

    private static void contentChecks() {
        List<List<String>> samples = List.of(
            List.of("政治引战刷屏", "招募成员参与暴力政变", "暴力夺权欢迎加入", "招 募 成 员 暴 力 夺 权",
                "招募成员\n颠覆政权", "跟我一起武装推翻政府"),
            List.of("成人视频资源", "裸聊", "招嫖", "色情直播", "援交", "ｐｏｒｎ", "p.o.r.n",
                "ＰＯＲＮＯＧＲＡＰＨＹ", "sexcam", "裸\u200b聊", "§c色§a情视频"),
            List.of("博彩平台", "真人荷官在线", "赌博充值", "娱乐城开户", "百家乐下注", "充值送彩金", "赌 博 充 值"),
            List.of("冰毒现货", "出售海洛因", "可卡因包邮", "求购摇头丸", "K粉现货", "出售大麻",
                "冰 毒 现 货", "冰\u200b毒现货", "冰毒\n现货")
        );
        TextFilterRules all = content(true, true, true, true, true, List.of());
        for (int category = 0; category < samples.size(); category++) {
            TextFilterRules only = content(category == 0, category == 1, category == 2, category == 3, true, List.of());
            TextFilterRules except = content(category != 0, category != 1, category != 2, category != 3, true, List.of());
            for (String sample : samples.get(category)) {
                check(all.blocks(sample), "All content categories: " + sample);
                check(only.blocks(sample), "Category enabled: " + sample);
                check(!except.blocks(sample), "Category independently disabled: " + sample);
                check(!content(true, true, true, true, true, List.of("测试白名单")).blocks("测试白名单 " + sample),
                    "Whitelist precedes content category: " + sample);
            }
        }
        TextFilterRules production = new TextFilterRules(true, true, true, true, true, true, true, true, List.of(), List.of());
        for (String normal : List.of("今天的政治课讲历史", "中华人民共和国", "新闻报道了暴力政变事件",
            "同学们讨论政治制度", "禁毒宣传：珍爱生命，远离毒品", "警方通报查获海洛因", "毒品危害科普",
            "拒绝赌博，谨防诈骗", "博彩行业新闻", "戒赌咨询", "性教育与生殖健康", "青春期健康科普",
            "成人礼顺利举办", "transport", "scorpion", "pornographyresearch", "sexcamper", "获得钻石，正常聊天")) {
            check(!production.blocks(normal), "Content false positive: " + normal);
        }
        TextFilterRules adsOnly = new TextFilterRules(true, false, false, true, false, false, false, false, List.of(), List.of());
        for (String migrated : List.of("成人视频", "色情网", "博彩平台", "赌博充值", "娱乐城开户")) {
            check(!adsOnly.blocks(migrated), "Migrated rule respects category switch even with ads on: " + migrated);
        }
        TextFilterRules noGaps = content(true, true, true, true, false, List.of());
        check(!noGaps.blocks("裸 聊"), "Sexual separator switch");
        check(!noGaps.blocks("冰 毒现货"), "Drug separator switch");
        check(!noGaps.blocks("赌 博充值"), "Gambling separator switch");
        check(!noGaps.blocks("招募成员暴 力夺权"), "Political separator switch");
        check(!noGaps.blocks("p.o.r.n"), "English content separator switch");
        check(noGaps.blocks("冰毒\n现货"), "Context can span sign lines without splitting keywords");
        check(all.blocks("赌场" + "字".repeat(12) + "返佣"), "Context gap at limit");
        check(!all.blocks("赌场" + "字".repeat(13) + "返佣"), "Do not join unrelated distant topics");
        check(all.blocks("出售" + "字".repeat(12) + "冰毒"), "Reverse context at gap limit");
        check(!all.blocks("出售" + "字".repeat(13) + "冰毒"), "Reverse context beyond gap limit");
        check(all.blocks("招募成员\n暴力夺权"), "Multiline political context");
        check(all.blocks("a".repeat(10000) + "冰毒现货"), "No long-message content bypass");
        check(all.blocks("冰毒现货"), "Cached content match");
        check(!all.blocks("禁毒宣传"), "Cached harmless topic");
    }
    public static void main(String[] args) {
        TextFilterRules defaults = rules(true, true, true, true, List.of(), List.of());
        for (String spam : List.of("加群 123456", "加 ＱＱ：123456", "服务器招新", "低价出售钻石", "扫码领取礼包",
            "傻逼", "傻 比", "傻\n逼", "傻💩逼", "傻\u200b逼", "傻\u2060逼", "傻\ufeff逼", "傻\ufe0f逼",
            "§c傻§a逼", "§x§f§f§0§0§0§0傻逼", "§C傻§R逼", "ＦＵＣＫ", "please FUCK off", "f.u.c.k", "f u c k",
            "f\u200bu\u200bc\u200bk", "you asshole!", "nmsl", "SB!", "https://example.com/ad", "www.example.org",
            "来 play.example.net:25565", "ｈｔｔｐｓ：／／ｅｘａｍｐｌｅ．ｃｏｍ", "加 192.168.1.10:25565", "0.0.0.0")) {
            check(defaults.blocks(spam), "Expected blocked: " + spam);
        }
        for (String normal : List.of("你好", "Steve joined the game", "Alex fell from a high place", "获得进度：石器时代",
            "传送冷却还有 5 秒", "坐标 -123 64 456", "Minecraft 1.21.11", "class", "grass", "Scunthorpe", "skill issue",
            "USB cable", "tmdragon", "cnmc", "diamond_sword", "击败末影龙", "【公告】服务器将在十分钟后重启",
            "999.999.999.999", "", "§a", "...", "xexample.comx")) {
            check(!defaults.blocks(normal), "False positive: " + normal);
        }
        check(!defaults.blocks(null), "Null is harmless");
        check(!rules(false, false, false, true, List.of(), List.of()).blocks("加群 傻逼 https://example.com"), "Rules independently disabled");
        check(!rules(false, true, false, true, List.of(), List.of()).blocks("加群"), "Advertising switch");
        check(!rules(true, false, false, true, List.of(), List.of()).blocks("fuck"), "Abuse switch");
        check(!rules(false, false, false, true, List.of(), List.of()).blocks("play.example.net"), "Link switch");
        TextFilterRules custom = rules(false, false, false, true, List.of("spam", "商店广告", "", "   ", "!!!"), List.of());
        check(custom.blocks("SPAM 广告"), "Custom case folding");
        check(custom.blocks("s.p.a.m"), "Custom separator removal");
        check(custom.blocks("商店\n广\n告"), "Multiline sign or text display");
        check(!custom.blocks("普通聊天"), "Empty custom terms cannot match everything");
        TextFilterRules literal = rules(false, false, false, false, List.of("a.b"), List.of());
        check(literal.blocks("a.b"), "Custom rules are literals");
        check(!literal.blocks("axb"), "No accidental regex");
        check(!rules(true, true, true, false, List.of(), List.of()).blocks("傻 逼"), "Disable separator handling");
        TextFilterRules whitelist = rules(true, true, true, true, List.of(), List.of("官方公告", " ", "!!!"));
        check(!whitelist.blocks("【官方 公告】加群 https://example.com"), "Whitelist precedes every block rule");
        check(whitelist.blocks("其他加群广告"), "Whitelist does not disable other filtering");
        // Cached render decisions and cache eviction must not change the outcome.
        check(defaults.blocks("傻逼"), "Cached blocked result");
        check(!defaults.blocks("你好"), "Cached allowed result");
        for (int i = 0; i < 2500; i++) check(!defaults.blocks("ordinary message " + i), "Cache eviction normal " + i);
        check(defaults.blocks("傻逼"), "Blocked after cache eviction");
        check(defaults.blocks("a".repeat(10000) + " 傻逼"), "No truncation bypass in long text");
        check(!defaults.blocks("a".repeat(10000)), "Long normal message");
        contentChecks();
        System.out.println("Information text filter behavioral checks passed: " + checks);
    }
}
