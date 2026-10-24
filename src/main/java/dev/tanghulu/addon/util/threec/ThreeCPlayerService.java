package dev.tanghulu.addon.util.threec;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/** Public 3c3u.info player API. Both duration fields are seconds, as on its player page. */
public final class ThreeCPlayerService {
    public static final String SOURCE = "3c3u.info";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8)).build();

    private ThreeCPlayerService() {}

    public record PlayerTime(String name, long totalSeconds, Long monthSeconds) {
        public boolean isNewbie(int thresholdHours) {
            return totalSeconds < thresholdHours * 3600L;
        }
    }

    public static String validateName(String input) {
        String name = input == null ? "" : input.strip();
        // The query site also supports server prefixes such as |name and ||name.
        if (!name.matches("\\|{0,2}[A-Za-z0-9_]{3,16}")) {
            throw new IllegalArgumentException("请输入 3–16 位玩家名（字母、数字、下划线；支持 | 前缀）");
        }
        return name;
    }

    public static PlayerTime query(String input) throws IOException, InterruptedException {
        String name = validateName(input);
        URI uri = URI.create("https://3c3u.info/api/getPlayer?name="
            + URLEncoder.encode(name, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
            .header("User-Agent", "Tanghulu/1.0.1 (3CNewbieCheck)").GET().build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 429) throw new IOException("查询过于频繁，请稍后再试");
        if (response.statusCode() != 200) throw new IOException("查询服务返回 HTTP " + response.statusCode() + "，请稍后重试");
        return parse(response.body(), name);
    }

    public static PlayerTime parse(String body, String requestedName) throws IOException {
        try {
            if (body.length() > 1024 * 1024) throw new IllegalArgumentException();
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            int code = root.get("code").getAsBigDecimal().intValueExact();
            if (code != 200) throw new IOException("查询站未返回玩家数据，请检查名字是否正确或稍后重试（代码 " + code + "）");
            JsonObject data = root.getAsJsonObject("data");
            String name = validateName(data.get("name").getAsString());
            if (!name.equalsIgnoreCase(requestedName)) throw new IOException("查询站返回了其他玩家的数据，未进行判断");
            long total = seconds(data.get("duration"));
            JsonElement month = data.get("month_duration");
            return new PlayerTime(name, total, month == null || month.isJsonNull() ? null : seconds(month));
        } catch (RuntimeException e) {
            throw new IOException("查询站数据格式异常，无法读取时长（不会按 0 小时判断）", e);
        }
    }

    private static long seconds(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Missing duration");
        }
        long seconds = value.getAsBigDecimal().longValueExact();
        if (seconds < 0) throw new IllegalArgumentException("Negative duration");
        return seconds;
    }

    public static String formatTime(long seconds) {
        return String.format(Locale.ROOT, "%d 天 %d 小时 %d 分 %d 秒（%.2f 小时）",
            seconds / 86400, seconds / 3600 % 24, seconds / 60 % 60, seconds % 60, seconds / 3600.0);
    }
}
