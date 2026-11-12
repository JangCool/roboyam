package com.community.board.service;

import com.community.post.entity.ContentFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.owasp.html.ElementPolicy;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.web.util.HtmlUtils;

/**
 * 글·댓글 HTML 본문 — 허용 목록 정화(OWASP Java HTML Sanitizer)와 정화된 HTML 에서 뽑는 값(보이는 글자·링크·이미지 표식·YouTube 표식).
 * 에디터가 만든 HTML 이라도 <b>서버에서 반드시</b> 거친다. 목록에 없는 태그·속성(스크립트, 이벤트 속성, 임의 style, iframe …)은 모두 사라진다(글자는 남는다).
 * community {@code UtilPostHtml}·{@code PostMedia} 를 아이로빗 에디터 수준(간단·고급)에 맞춰 옮겼다.
 *
 * <ul>
 *   <li><b>간단</b>(글·댓글): {@code p br strong em u s a ul ol li blockquote}. 링크는 http·https·mailto 만, 저장할 때 {@code target="_blank"}
 *       {@code rel="nofollow noopener noreferrer"} 를 늘 붙인다.</li>
 *   <li><b>고급</b>(글만): 간단 + {@code h2 h3 hr code pre} + 이미지 표식 {@code <img data-image-id="숫자">}(주소·크기는 서버가 채운다 — {@link #expandImages})
 *       + 글자색·배경색 {@code span}({@code style} 의 {@code color}·{@code background-color} 중 팔레트 값만, 그 밖은 버리고 남는 것이 없으면 span 을 벗긴다)
 *       + YouTube 표식 {@code <div data-youtube-id="11자"></div>}(iframe 은 저장하지 않는다 — 화면이 ID 로 youtube-nocookie 를 그린다).</li>
 *   <li><b>댓글</b>: 간단 + 영상 표식(에디터가 붙여 넣은 영상 주소를 표식으로 바꾼다 — 사용자 결정 2026-11-06, 개수는 {@link ContentRules#COMMENT_VIDEO_MAX}).</li>
 * </ul>
 * 영상 표식은 둘이다 — YouTube {@code <div data-youtube-id="11자"></div>} 와 그 밖의 플랫폼 {@code <div data-embed="플랫폼:ID"></div>}
 * (치지직 클립 {@code chzzk:영문·숫자} · SOOP 다시보기 {@code soop:숫자} · 틱톡 {@code tiktok:숫자} · 인스타그램 {@code instagram:단축 코드}). 고급·댓글 수준이 받는다.
 * 주소는 저장하지 않는다 — 화면이 플랫폼마다 정해진 퍼가기 주소 틀에 ID 만 끼워 iframe 을 만든다(사용자가 쓴 주소가 iframe 에 들어가지 않는다).
 * 팔레트는 community 에디터({@code richFormats.TEXT_COLORS}·{@code BACKGROUND_COLORS})와 같은 값이다 — user-web 에디터도 같은 값을 써야 한다.
 */
public final class RichText {

    /** 에디터 수준 */
    public enum Level {
        SIMPLE,
        ADVANCED,
        /** 댓글 — 간단 + 영상 표식 */
        COMMENT
    }

    /** 글자색 팔레트(7) — 소문자 {@code #rrggbb} */
    public static final Set<String> TEXT_COLORS = Set.of("#dc2626", "#ea580c", "#ca8a04", "#16a34a", "#2563eb", "#9333ea", "#64748b");
    /** 배경색 팔레트(6) */
    public static final Set<String> BACKGROUND_COLORS = Set.of("#fef08a", "#bbf7d0", "#bfdbfe", "#fecaca", "#e9d5ff", "#e2e8f0");

    private static final Pattern DIGITS = Pattern.compile("[1-9]\\d{0,18}");
    private static final Pattern YOUTUBE_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    /** 그 밖의 플랫폼 영상 표식 값 {@code 플랫폼:ID} — user-web {@code UtilRichMedia} 의 ID 규칙과 같다 */
    private static final Pattern EMBED = Pattern.compile("chzzk:[A-Za-z0-9]{6,20}|soop:[1-9]\\d{0,11}|tiktok:\\d{10,25}|instagram:[A-Za-z0-9_-]{5,40}");
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-f]{6}");
    private static final Pattern RGB_COLOR = Pattern.compile("rgb[(] *([0-9]{1,3}) *, *([0-9]{1,3}) *, *([0-9]{1,3}) *[)]");

    /** 정화된 HTML 안의 표식·요소(정화기가 내보내는 모양 그대로라 정규식으로 읽어도 안전하다 — 글 속 {@code <} 는 모두 {@code &lt;} 다) */
    private static final Pattern IMG_MARKER = Pattern.compile("<img data-image-id=\"(\\d{1,19})\"\\s*/?>");
    private static final Pattern DIV_OPEN = Pattern.compile("<div\\b([^>]*)>");
    private static final Pattern YOUTUBE_ATTR = Pattern.compile("\\sdata-youtube-id=\"([A-Za-z0-9_-]{11})\"");
    private static final Pattern EMBED_ATTR = Pattern.compile("\\sdata-embed=\"([^\"]+)\"");
    private static final Pattern VIDEO_MARKER = Pattern.compile("<div data-(?:youtube-id|embed)=\"[^\"]+\"></div>");
    private static final Pattern ANCHOR = Pattern.compile("(?s)<a\\b[^>]*>.*?</a>");
    private static final Pattern HREF = Pattern.compile("<a\\b[^>]*?\\shref=\"([^\"]*)\"");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern TRAILING_EMPTY_PARAGRAPHS = Pattern.compile("(?:<p></p>|<p><br\\s*/?></p>|\\s)+$");
    private static final Pattern LEADING_EMPTY_PARAGRAPHS = Pattern.compile("^(?:<p></p>|<p><br\\s*/?></p>|\\s)+");
    private static final Pattern BREAK = Pattern.compile("(?i)<br\\s*/?>|<hr\\s*/?>|</(?:p|li|blockquote|h2|h3|pre|div)>");

    private static final PolicyFactory SIMPLE = base().toFactory();

    private static final PolicyFactory COMMENT = video(base()).toFactory();

    private static final PolicyFactory ADVANCED = video(base()
            .allowElements("h2", "h3", "hr", "code", "pre")
            .allowElements(colorSpan(), "span")
            .allowAttributes("style").onElements("span")
            .allowElements(requireAttribute("data-image-id", DIGITS), "img")
            .allowAttributes("data-image-id").matching(DIGITS).onElements("img"))
            .toFactory();

    private RichText() {
    }

    /** 영상 표식 {@code <div data-youtube-id="11자">} · {@code <div data-embed="플랫폼:ID">} 를 더한다(둘 중 하나가 형식에 맞는 div 만 남는다) */
    private static HtmlPolicyBuilder video(HtmlPolicyBuilder builder) {
        return builder
                .allowElements(requireAnyAttribute(Map.of("data-youtube-id", YOUTUBE_ID, "data-embed", EMBED)), "div")
                .allowAttributes("data-youtube-id").matching(YOUTUBE_ID).onElements("div")
                .allowAttributes("data-embed").matching(EMBED).onElements("div");
    }

    private static HtmlPolicyBuilder base() {
        return new HtmlPolicyBuilder()
                .allowElements("p", "br", "strong", "em", "u", "s", "blockquote", "ul", "ol", "li")
                .allowElements(newTab(), "a")
                .allowUrlProtocols("http", "https", "mailto")
                .allowAttributes("href").onElements("a")
                .requireRelsOnLinks("nofollow", "noopener", "noreferrer");
    }

    /** 에디터 값(SIMPLE · ADVANCED) → 수준. 평문(NONE)이면 null */
    public static Level level(String editor) {
        if (BoardFields.EDITOR_ADVANCED.equals(editor)) {
            return Level.ADVANCED;
        }
        return BoardFields.EDITOR_SIMPLE.equals(editor) ? Level.SIMPLE : null;
    }

    /**
     * 허용 목록으로 정화한 HTML. 앞뒤의 빈 문단은 저장하지 않고(에디터가 이미지·영상 뒤에 커서 자리로 붙인다), 영상 표식은 늘 속성 하나짜리 빈 블록으로 맞춘다
     * (안에 들어온 글자는 블록 뒤로 나온다. 두 속성이 다 있으면 YouTube 를 남긴다).
     */
    public static String sanitize(String raw, Level level) {
        PolicyFactory policy = level == null ? SIMPLE : switch (level) {
            case ADVANCED -> ADVANCED;
            case COMMENT -> COMMENT;
            case SIMPLE -> SIMPLE;
        };
        String safe = policy.sanitize(raw == null ? "" : raw);
        if (policy != SIMPLE) {
            // 정화기가 남기는 div 는 영상 표식뿐이다 — 닫는 태그를 모두 떼고 여는 태그를 빈 블록으로 바꾼다(균형이 깨지지 않는다)
            safe = DIV_OPEN.matcher(safe.replace("</div>", "")).replaceAll(match -> Matcher.quoteReplacement(videoMarker(match.group(1))));
        }
        // 정화기는 rel 을 「noreferrer noopener nofollow」 차례로 쓴다 — 계약의 차례로 맞춘다(뜻은 같다)
        safe = safe.replace(" rel=\"noreferrer noopener nofollow\"", " rel=\"nofollow noopener noreferrer\"");
        safe = TRAILING_EMPTY_PARAGRAPHS.matcher(safe).replaceFirst("");
        return LEADING_EMPTY_PARAGRAPHS.matcher(safe).replaceFirst("");
    }

    /**
     * 정화된 HTML 의 <b>보이는 글자</b> — 태그를 떼고 엔티티를 풀고, 문단·줄바꿈·목록 항목 같은 블록 경계는 줄바꿈 하나({@code \n}), 이어진 공백은 하나로 줄인다.
     * 이미지·YouTube 는 글자가 없다. 본문 길이 규칙·검색·같은 글 판정·알림 미리보기·금지어가 이 값을 쓴다.
     */
    public static String plainText(String safeHtml) {
        if (safeHtml == null || safeHtml.isEmpty()) {
            return "";
        }
        String s = safeHtml.replaceAll("[\\r\\n\\t]+", " ");
        s = BREAK.matcher(s).replaceAll("\n");
        s = TAG.matcher(s).replaceAll("");
        s = HtmlUtils.htmlUnescape(s).replace('\u00a0', ' ').replace("\u0000", "");
        s = s.replaceAll(" {2,}", " ").replaceAll(" *\n *", "\n").replaceAll("\n{2,}", "\n");
        return s.strip();
    }

    /** 저장된 본문의 보이는 글자 — HTML 이면 {@link #plainText}, TEXT 면 그대로(알림 미리보기·신고 스냅샷) */
    public static String plainOf(ContentFormat format, String content) {
        return format == ContentFormat.HTML ? plainText(content) : content;
    }

    /** 링크 수 — 링크 요소({@code <a href>}) 하나당 1 + 링크 요소 밖의 보이는 글자에 적힌 주소({@link ContentRules#linkCount}) */
    public static int linkCount(String safeHtml) {
        int anchors = count(HREF, safeHtml);
        String outside = ANCHOR.matcher(safeHtml).replaceAll(" ");
        return anchors + ContentRules.linkCount(plainText(outside));
    }

    /** 링크 주소들(엔티티를 푼 값, 본문 순서) — 금지어 검사가 주소도 본다 */
    public static List<String> hrefs(String safeHtml) {
        List<String> out = new ArrayList<>();
        Matcher m = HREF.matcher(safeHtml);
        while (m.find()) {
            out.add(HtmlUtils.htmlUnescape(m.group(1)));
        }
        return out;
    }

    /** 이미지 표식의 ID(본문 순서, 겹친 것 포함) */
    public static List<Long> imageIds(String safeHtml) {
        List<Long> ids = new ArrayList<>();
        Matcher m = IMG_MARKER.matcher(safeHtml);
        while (m.find()) {
            try {
                ids.add(Long.parseLong(m.group(1)));
            } catch (NumberFormatException e) {
                ids.add(-1L); // 표식 형식은 맞지만 범위를 넘는다 — 찾을 수 없는 이미지로 거절된다
            }
        }
        return ids;
    }

    /** 영상 표식 수(YouTube + 그 밖의 플랫폼) */
    public static int videoCount(String safeHtml) {
        return count(VIDEO_MARKER, safeHtml);
    }

    /** 정화된 div 여는 태그의 속성 → 빈 영상 표식 하나. 정화기가 형식을 이미 봤으므로 값은 그대로 쓴다 */
    private static String videoMarker(String attributes) {
        Matcher youtube = YOUTUBE_ATTR.matcher(attributes);
        if (youtube.find()) {
            return "<div data-youtube-id=\"" + youtube.group(1) + "\"></div>";
        }
        Matcher embed = EMBED_ATTR.matcher(attributes);
        return embed.find() && EMBED.matcher(embed.group(1)).matches() ? "<div data-embed=\"" + embed.group(1) + "\"></div>" : "";
    }

    /** 이미지 표식을 서버가 만든 태그로 바꾼다({@code tag} 가 ID → 태그. null 이면 그 표식을 지운다) */
    public static String expandImages(String safeHtml, Function<Long, String> tag) {
        return IMG_MARKER.matcher(safeHtml).replaceAll(match -> {
            String replacement;
            try {
                replacement = tag.apply(Long.parseLong(match.group(1)));
            } catch (NumberFormatException e) {
                replacement = null;
            }
            return replacement == null ? "" : Matcher.quoteReplacement(replacement);
        });
    }

    /**
     * 태그 밖의 글자에만 {@code mask} 를 건다(금지어 가림) — 태그·속성은 건드리지 않는다. 바뀐 조각만 엔티티를 풀어 가린 뒤 다시 이스케이프한다.
     */
    public static String maskText(String safeHtml, UnaryOperator<String> mask) {
        StringBuilder out = new StringBuilder(safeHtml.length());
        Matcher m = TAG.matcher(safeHtml);
        int last = 0;
        while (m.find()) {
            out.append(maskSegment(safeHtml.substring(last, m.start()), mask));
            out.append(m.group());
            last = m.end();
        }
        out.append(maskSegment(safeHtml.substring(last), mask));
        return out.toString();
    }

    private static String maskSegment(String segment, UnaryOperator<String> mask) {
        if (segment.isEmpty()) {
            return segment;
        }
        String text = HtmlUtils.htmlUnescape(segment);
        String masked = mask.apply(text);
        return masked.equals(text) ? segment : HtmlUtils.htmlEscape(masked);
    }

    private static int count(Pattern pattern, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Matcher m = pattern.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    // ── 정화 규칙 ─────────────────────────────────────────────────────────

    /** 링크는 늘 새 탭으로 연다(주소가 남은 링크만) — rel 은 정화기가 붙인다 */
    private static ElementPolicy newTab() {
        return (elementName, attributes) -> {
            boolean href = false;
            for (int i = 0; i + 1 < attributes.size(); i += 2) {
                if ("href".equals(attributes.get(i))) {
                    href = true;
                }
            }
            if (href) {
                attributes.add("target");
                attributes.add("_blank");
            }
            return elementName;
        };
    }

    /** 표식 속성이 있고 값이 형식에 맞을 때만 요소를 남긴다(없으면 요소만 지우고 안의 글자는 남긴다) */
    private static ElementPolicy requireAttribute(String attribute, Pattern valuePattern) {
        return (elementName, attributes) -> {
            for (int i = 0; i + 1 < attributes.size(); i += 2) {
                if (attributes.get(i).equalsIgnoreCase(attribute) && valuePattern.matcher(attributes.get(i + 1)).matches()) {
                    return elementName;
                }
            }
            return null;
        };
    }

    /** 여러 표식 속성 중 하나라도 있고 값이 형식에 맞으면 요소를 남긴다 */
    private static ElementPolicy requireAnyAttribute(Map<String, Pattern> patterns) {
        return (elementName, attributes) -> {
            for (int i = 0; i + 1 < attributes.size(); i += 2) {
                Pattern pattern = patterns.get(attributes.get(i).toLowerCase(Locale.ROOT));
                if (pattern != null && pattern.matcher(attributes.get(i + 1)).matches()) {
                    return elementName;
                }
            }
            return null;
        };
    }

    /** 글자색·배경색 span — 팔레트 안의 값만 {@code color:…;background-color:…} 로 다시 쓴다. 남는 것이 없으면 null(span 만 벗긴다) */
    private static ElementPolicy colorSpan() {
        return (elementName, attributes) -> {
            Map<String, String> declared = declarations(attributes);
            StringBuilder style = new StringBuilder();
            String color = normalizeColor(declared.get("color"));
            if (color != null && TEXT_COLORS.contains(color)) {
                style.append("color:").append(color);
            }
            String background = normalizeColor(declared.get("background-color"));
            if (background != null && BACKGROUND_COLORS.contains(background)) {
                if (!style.isEmpty()) {
                    style.append(';');
                }
                style.append("background-color:").append(background);
            }
            attributes.clear();
            if (style.isEmpty()) {
                return null;
            }
            attributes.add("style");
            attributes.add(style.toString());
            return elementName;
        };
    }

    /** 색 값을 소문자 {@code #rrggbb} 로 — 브라우저가 {@code rgb(r, g, b)} 로 바꿔 쓴 값도 받는다. 그 밖의 모양은 null */
    static String normalizeColor(String value) {
        if (value == null) {
            return null;
        }
        String v = value.strip().toLowerCase();
        if (HEX_COLOR.matcher(v).matches()) {
            return v;
        }
        Matcher m = RGB_COLOR.matcher(v);
        if (!m.matches()) {
            return null;
        }
        int r = Integer.parseInt(m.group(1));
        int g = Integer.parseInt(m.group(2));
        int b = Integer.parseInt(m.group(3));
        if (r > 255 || g > 255 || b > 255) {
            return null;
        }
        return String.format("#%02x%02x%02x", r, g, b);
    }

    /** 속성 목록의 {@code style} 값을 속성 → 값(소문자)으로 푼다. {@code !important} 가 붙은 값은 버린다 */
    private static Map<String, String> declarations(List<String> attributes) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i + 1 < attributes.size(); i += 2) {
            if (!attributes.get(i).equalsIgnoreCase("style")) {
                continue;
            }
            for (String declaration : attributes.get(i + 1).split(";")) {
                int colon = declaration.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String property = declaration.substring(0, colon).strip().toLowerCase();
                String value = declaration.substring(colon + 1).strip().toLowerCase();
                if (!value.contains("!")) {
                    result.put(property, value);
                }
            }
        }
        return result;
    }
}
