package com.community.hotdeal.og;

import com.community.hotdeal.og.DealPageFetcher.OgTags;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 머리에서 OG 칸만 뽑는다(정규식 — HTML 해석기를 들이지 않는다). {@code og:title}({@code <title>} 로 대신) · {@code og:site_name} · {@code og:image}
 * ({@code og:image:secure_url}·{@code twitter:image} 로 대신). 글자 집합은 응답 헤더 → {@code <meta charset>} → UTF-8. 그림 주소는 페이지 주소 기준 절대 주소(http·https 만).
 * 뽑은 글자는 화면에 그대로 보이지 않고 미리 채움 값으로만 쓰인다 — 길이를 자르고 제어 문자를 지운다.
 */
public final class OgParser {

    private static final Pattern META = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile("([a-zA-Z:_-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))");
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>([^<]*)</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_CHARSET = Pattern.compile("<meta[^>]+charset\\s*=\\s*[\"']?([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]{1,6});");
    private static final int TEXT_MAX = 200;
    private static final int URL_MAX = 2000;

    private OgParser() {
    }

    public static OgTags parse(byte[] body, String headerCharset, String pageUrl) {
        if (body == null || body.length == 0) {
            return new OgTags(null, null, null);
        }
        String html = new String(body, charset(body, headerCharset));
        Map<String, String> og = new HashMap<>();
        Matcher meta = META.matcher(html);
        while (meta.find()) {
            Map<String, String> attrs = attrs(meta.group());
            String key = attrs.containsKey("property") ? attrs.get("property") : attrs.get("name");
            String content = attrs.get("content");
            if (key != null && content != null) {
                og.putIfAbsent(key.toLowerCase(Locale.ROOT), content);
            }
        }
        String title = first(og, "og:title");
        if (title == null) {
            Matcher t = TITLE.matcher(html);
            title = t.find() ? t.group(1) : null;
        }
        String image = first(og, "og:image:secure_url", "og:image", "og:image:url", "twitter:image");
        return new OgTags(text(title), text(first(og, "og:site_name")), imageUrl(pageUrl, image));
    }

    private static Charset charset(byte[] body, String headerCharset) {
        String name = headerCharset;
        if (name == null) {
            String head = new String(body, 0, Math.min(body.length, 2048), StandardCharsets.ISO_8859_1);
            Matcher m = META_CHARSET.matcher(head);
            name = m.find() ? m.group(1) : null;
        }
        if (name != null) {
            try {
                return Charset.forName(name);
            } catch (RuntimeException e) {
                // 모르는 글자 집합 — UTF-8 로 읽는다
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static Map<String, String> attrs(String tag) {
        Map<String, String> out = new HashMap<>();
        Matcher m = ATTR.matcher(tag);
        while (m.find()) {
            String value = m.group(3) != null ? m.group(3) : m.group(4) != null ? m.group(4) : m.group(5);
            out.putIfAbsent(m.group(1).toLowerCase(Locale.ROOT), unescape(value));
        }
        return out;
    }

    private static String first(Map<String, String> og, String... keys) {
        for (String key : keys) {
            String v = og.get(key);
            if (v != null && !v.isBlank()) {
                return v.strip();
            }
        }
        return null;
    }

    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String t = unescape(value).replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").strip();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > TEXT_MAX ? t.substring(0, TEXT_MAX) : t;
    }

    private static String imageUrl(String pageUrl, String image) {
        if (image == null || image.length() > URL_MAX) {
            return null;
        }
        try {
            URI resolved = new URI(pageUrl).resolve(image.strip().replace(" ", "%20"));
            String scheme = resolved.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || resolved.getHost() == null) {
                return null;
            }
            return resolved.toString();
        } catch (Exception e) {
            return null;
        }
    }

    static String unescape(String value) {
        if (value == null || value.indexOf('&') < 0) {
            return value;
        }
        String v = value.replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
        Matcher m = NUMERIC_ENTITY.matcher(v);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int code;
            try {
                code = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            } catch (NumberFormatException e) {
                code = -1;
            }
            m.appendReplacement(out, code > 0 && Character.isValidCodePoint(code) ? Matcher.quoteReplacement(new String(Character.toChars(code))) : "");
        }
        m.appendTail(out);
        return out.toString().replace("&amp;", "&");
    }
}
