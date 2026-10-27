package dev.tanghulu.addon.util.textfilter;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Local, deterministic rules. No network calls or user-supplied regular expressions. */
public final class TextFilterRules {
    private static final int CACHE_SIZE = 2048;
    private static final int MAX_CACHED_LENGTH = 2048;
    private static final List<String> ADS = List.of(
        "加群", "进群", "入群", "qq群", "q群", "群号", "加qq", "加我qq", "加微信", "加我微信",
        "加v", "加我v", "私服", "公益服", "服务器招新", "服务器招人", "服务器宣传", "新服开荒",
        "代练", "代刷", "代肝", "低价出售", "低价收购", "有偿", "刷钻", "刷币", "免费领取",
        "扫码领取", "扫码加入");
    private static final List<String> ABUSE = List.of(
        "傻逼", "傻比", "傻屄", "煞笔", "傻叉", "脑残", "智障", "弱智", "废物", "狗东西",
        "你妈死了", "你爹死了", "死全家", "操你妈", "草你妈", "曹你妈", "艹你妈", "肏你妈",
        "妈了个逼", "他妈的", "去你妈", "滚你妈", "狗日的", "婊子", "杂种", "畜生");
    private static final List<String> ENGLISH_ABUSE = List.of(
        "fuck", "fucking", "fucker", "motherfucker", "shit", "bullshit", "bitch", "asshole",
        "cunt", "nmsl", "cnm", "cnmd", "tmd", "sb");
    // Topic names alone are not treated as illegal. Match specific spam phrases or nearby solicitation.
    private static final List<String> POLITICAL = List.of("政治引战", "政治刷屏", "政治骂战");
    private static final List<String> POLITICAL_TOPICS = List.of(
        "暴力夺权", "暴力政变", "颠覆政权", "武装推翻政府", "分裂国家", "恐怖袭击");
    private static final List<String> POLITICAL_CALLS = List.of(
        "加入我们", "跟我一起", "联系我们", "立即行动", "一起参加", "招募成员", "招募队员",
        "欢迎加入", "立即报名", "私聊报名");
    private static final List<String> SEXUAL = List.of(
        "成人视频", "色情网", "色情网站", "色情视频", "色情直播", "色情资源", "成人色情",
        "黄色网站", "裸聊", "招嫖", "援交", "约炮", "卖淫", "买春");
    private static final List<String> ENGLISH_SEXUAL = List.of("porn", "porno", "pornography", "sexcam");
    private static final List<String> GAMBLING = List.of(
        "在线赌场", "线上赌场", "网络赌场", "博彩平台", "赌博平台", "真人荷官", "美女荷官",
        "真人百家乐", "六合彩开奖", "时时彩投注", "充值送彩金", "存款送彩金", "投注返利", "下注返利");
    private static final List<String> GAMBLING_TOPICS = List.of(
        "博彩", "赌博", "赌场", "百家乐", "六合彩", "时时彩", "体育投注", "网络投注", "娱乐城");
    private static final List<String> GAMBLING_PROMOTION = List.of(
        "开户", "充值", "下注", "投注", "返利", "返佣", "送彩金", "包赢", "带玩", "代理招募",
        "招代理", "私聊", "联系");
    private static final List<String> DRUG_TOPICS = List.of(
        "毒品", "冰毒", "海洛因", "可卡因", "麻古", "摇头丸", "大麻", "k粉");
    private static final List<String> DRUG_TRADE = List.of(
        "出售", "售卖", "贩卖", "求购", "收购", "代购", "批发", "零售", "现货", "到货",
        "包邮", "货到付款", "私聊拿货", "联系拿货");
    private static final Pattern LINKS = Pattern.compile(
        "https?://\\S+|www\\.\\S+|(?<![\\p{L}\\p{N}_])"
        + "(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.){1,8}"
        + "(?:com|net|org|cn|top|xyz|club|vip|cc|gg|me|io|shop|site|fun|online|store|info|biz|link|live|space|app|dev|co|tv|pro)"
        + "(?![\\p{L}\\p{N}_-])");
    private static final String OCTET = "(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";
    private static final Pattern IP = Pattern.compile(
        "(?<![\\p{L}\\p{N}_.])(?:" + OCTET + "\\.){3}" + OCTET + "(?![\\p{L}\\p{N}_.])");

    private final boolean ignoreSeparators;
    private final boolean blockLinks;
    private final List<String> blocked;
    private final List<String> allowed;
    private final List<Pattern> english;
    private final List<Pattern> contextual = new ArrayList<>();
    private final Map<String, Boolean> cache = new LinkedHashMap<>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> entry) {
            return size() > CACHE_SIZE;
        }
    };

    public TextFilterRules(boolean ads, boolean abuse, boolean links, boolean ignoreSeparators,
                           List<String> blocked, List<String> allowed) {
        this(ads, abuse, links, ignoreSeparators, false, false, false, false, blocked, allowed);
    }

    public TextFilterRules(boolean ads, boolean abuse, boolean links, boolean ignoreSeparators,
                           boolean politics, boolean sexual, boolean gambling, boolean drugs,
                           List<String> blocked, List<String> allowed) {
        this.ignoreSeparators = ignoreSeparators;
        this.blockLinks = links;
        this.blocked = new ArrayList<>(normalizeTerms(blocked));
        if (ads) this.blocked.addAll(normalizeTerms(ADS));
        if (abuse) this.blocked.addAll(normalizeTerms(ABUSE));
        if (politics) {
            this.blocked.addAll(normalizeTerms(POLITICAL));
            contextual.add(nearby(POLITICAL_TOPICS, POLITICAL_CALLS));
        }
        if (sexual) this.blocked.addAll(normalizeTerms(SEXUAL));
        if (gambling) {
            this.blocked.addAll(normalizeTerms(GAMBLING));
            contextual.add(nearby(GAMBLING_TOPICS, GAMBLING_PROMOTION));
        }
        if (drugs) contextual.add(nearby(DRUG_TOPICS, DRUG_TRADE));
        this.allowed = normalizeTerms(allowed);
        List<String> englishTerms = new ArrayList<>();
        if (abuse) englishTerms.addAll(ENGLISH_ABUSE);
        if (sexual) englishTerms.addAll(ENGLISH_SEXUAL);
        this.english = englishTerms.stream().map(this::englishWord).toList();
    }

    public synchronized boolean blocks(String text) {
        if (text == null || text.isEmpty()) return false;
        Boolean cached = cache.get(text);
        if (cached != null) return cached;
        String plain = normalize(text);
        String match = ignoreSeparators ? compact(plain) : plain;
        boolean result = false;
        if (!match.isEmpty() && allowed.stream().noneMatch(match::contains)) {
            result = blocked.stream().anyMatch(match::contains)
                || contextual.stream().anyMatch(pattern -> pattern.matcher(match).find())
                || english.stream().anyMatch(pattern -> pattern.matcher(plain).find())
                || blockLinks && (LINKS.matcher(plain).find() || IP.matcher(plain).find());
        }
        // Very long server text is still checked, but cannot fill the render cache.
        if (text.length() <= MAX_CACHED_LENGTH) cache.put(text, result);
        return result;
    }

    private List<String> normalizeTerms(List<String> terms) {
        if (terms == null) return List.of();
        return terms.stream().filter(term -> term != null).map(TextFilterRules::normalize)
            .map(term -> ignoreSeparators ? compact(term) : term).filter(term -> !term.isEmpty()).distinct().toList();
    }

    private Pattern englishWord(String word) {
        String gap = ignoreSeparators ? "[\\p{P}\\p{Z}\\s]*" : "";
        String body = String.join(gap, word.chars().mapToObj(c -> Pattern.quote(String.valueOf((char) c))).toList());
        // Unicode boundaries avoid false positives in names such as Scunthorpe.
        return Pattern.compile("(?<![\\p{L}\\p{N}_])" + body + "(?![\\p{L}\\p{N}_])");
    }

    private Pattern nearby(List<String> topics, List<String> signals) {
        String topic = alternatives(topics);
        String signal = alternatives(signals);
        // Bound the gap to avoid combining a topic with unrelated text far away in a message.
        return Pattern.compile(topic + ".{0,12}" + signal + "|" + signal + ".{0,12}" + topic, Pattern.DOTALL);
    }

    private String alternatives(List<String> terms) {
        return "(?:" + String.join("|", normalizeTerms(terms).stream().map(Pattern::quote).toList()) + ")";
    }

    static String normalize(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length();) {
            int c = normalized.codePointAt(i);
            i += Character.charCount(c);
            // Legacy formatting, including the individual components of RGB sequences.
            if (c == '\u00a7' && i < normalized.length()
                && "0123456789abcdefklmnorx".indexOf(normalized.charAt(i)) >= 0) {
                i++;
                continue;
            }
            int type = Character.getType(c);
            if (type == Character.FORMAT || type == Character.NON_SPACING_MARK
                || type == Character.ENCLOSING_MARK || c == 0x034f) continue;
            if (Character.isISOControl(c) && !Character.isWhitespace(c)) continue;
            out.appendCodePoint(c);
        }
        return out.toString();
    }

    private static String compact(String text) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().filter(Character::isLetterOrDigit)
            .forEach(out::appendCodePoint);
        return out.toString();
    }
}
