package com.community.aimember.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/**
 * 뉴스 기사 HTML 에서 <b>본문 글자</b>만 뽑는다(AI 기자가 요약할 자료 — {@link AiNewsArticleReader}). 언론사마다 모양이 달라 흔한 본문 칸을 차례로 찾는다.
 *
 * <ol>
 *   <li>스크립트·스타일·주석·그림 묶음 등 글이 아닌 덩어리를 뗀다.</li>
 *   <li>본문 칸: {@code itemprop="articleBody"} → id·class 에 흔한 본문 이름(article_body, newsct_article, entry-content …)이 든 요소 → {@code <article>}.
 *       글자가 {@value #REGION_MIN}자 넘는 첫 칸을 쓴다. 못 찾으면 페이지 전체에서 문장처럼 보이는 줄만 고른다.</li>
 *   <li>줄로 나눠 짧은 줄·저작권·기자 이메일·관련 기사·공유 단추 같은 줄을 빼고, 통신사 머리(「(서울=연합뉴스) 홍길동 기자 =」)를 뗀다.</li>
 *   <li>{@value #MAX_CHARS}자까지(줄 단위로 자른다).</li>
 * </ol>
 * 못 뽑으면 null.
 */
public final class AiNewsArticleText {

    /** 모델에 주는 본문 발췌의 최대 글자 */
    public static final int MAX_CHARS = 3000;
    static final int REGION_MIN = 200;
    private static final int LINE_MIN = 25;
    private static final int LOOSE_LINE_MIN = 40;

    private static final Pattern NOISE = Pattern.compile(
            "(?is)<!--.*?-->|<(script|style|noscript|svg|iframe|template|button|form|select|figure|figcaption|video|audio|picture|table)\\b[^>]*>.*?</\\1\\s*>");
    private static final Pattern BODY_REGION = Pattern.compile(
            "(?i)<(div|section|article|td)\\b[^>]*(?:itemprop\\s*=\\s*[\"']articleBody[\"']|(?:id|class)\\s*=\\s*[\"'][^\"']*(?:article_?body|articlebody|article-body|article_txt"
                    + "|article-text|article_text|art_body|art_txt|newsct_article|news_?body|news-body|news_content|news_txt|newsview|view_con|view_text|viewcontent|cont_view"
                    + "|story-body|story_body|entry-content|post-content|article_content|article-content|articlecont|article_view|news_article|txt_article|dic_area)[^\"']*[\"'])[^>]*>");
    private static final Pattern ARTICLE = Pattern.compile("(?i)<article\\b[^>]*>");
    private static final Pattern BODY = Pattern.compile("(?i)<body\\b[^>]*>");
    private static final Pattern BREAKS = Pattern.compile("(?i)<br\\s*/?>|</(?:p|div|li|h[1-6]|tr|section|article|blockquote|dd|dt)\\s*>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f\\r\\u00A0\\u200B\\u3000]+");
    /** 통신사·언론사 머리 — 「(서울=연합뉴스) 홍길동 기자 =」, 「[전자신문] 홍길동 기자 =」, 「홍길동 기자 =」 */
    private static final Pattern WIRE_HEAD = Pattern.compile("^(?:[\\(\\[][^\\)\\]]{1,30}[\\)\\]]\\s*)?(?:[가-힣]{2,4}\\s*(?:기자|특파원|객원기자)\\s*[=:]\\s*)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    /** 들어 있으면 늘 빼는 줄 — 저작권 고지·바로가기 표시 */
    private static final List<String> JUNK = List.of("무단 전재", "무단전재", "재배포", "copyright", "ⓒ", "©", "all rights reserved", "▶", "☞");
    /** 짧은 줄({@value #SHORT_LINE}자 미만)일 때만 빼는 말 — 메뉴·단추·꼬리. 본문 문장에도 나올 수 있는 낱말이라 긴 줄은 둔다 */
    private static final List<String> JUNK_SHORT = List.of("관련기사", "관련 기사", "구독", "좋아요", "공유", "카카오톡", "페이스북", "트위터", "글자크기", "기사제보", "제보하기",
            "많이 본", "인기 기사", "댓글", "광고", "앱 다운", "저작권", "기자 구독", "기자의 다른 기사");
    private static final int SHORT_LINE = 60;

    private AiNewsArticleText() {
    }

    /** 본문 발췌 — 못 뽑으면 null */
    public static String extract(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String clean = NOISE.matcher(html).replaceAll(" ");
        for (Pattern p : List.of(BODY_REGION, ARTICLE)) {
            Matcher m = p.matcher(clean);
            while (m.find()) {
                String region = region(clean, m);
                String text = text(region, false);
                if (text != null && text.length() >= REGION_MIN) {
                    return text;
                }
            }
        }
        Matcher body = BODY.matcher(clean);
        String whole = body.find() ? clean.substring(body.end()) : clean;
        String text = text(whole, true);
        return text == null || text.length() < REGION_MIN ? null : text;
    }

    /** 찾은 여는 태그부터 짝이 맞는 닫는 태그까지(같은 이름 태그의 깊이를 센다). 짝이 없으면 끝까지 */
    static String region(String html, Matcher open) {
        String name = open.groupCount() < 1 || open.group(1) == null ? "article" : open.group(1).toLowerCase(Locale.ROOT);
        Pattern tags = Pattern.compile("(?i)<(/?)" + name + "\\b[^>]*>");
        Matcher t = tags.matcher(html);
        int depth = 1;
        int from = open.end();
        t.region(from, html.length());
        while (t.find()) {
            depth += t.group(1).isEmpty() ? 1 : -1;
            if (depth == 0) {
                return html.substring(from, t.start());
            }
        }
        return html.substring(from);
    }

    /** HTML 조각 → 줄 고르기 → 이은 글자. {@code loose} = 본문 칸을 못 찾았다 — 문장처럼 보이는 긴 줄만 */
    static String text(String fragment, boolean loose) {
        String plain = TAG.matcher(BREAKS.matcher(fragment).replaceAll("\n")).replaceAll(" ");
        plain = HtmlUtils.htmlUnescape(plain);
        Set<String> lines = new LinkedHashSet<>();
        for (String raw : plain.split("\n")) {
            String line = SPACES.matcher(raw).replaceAll(" ").strip();
            line = WIRE_HEAD.matcher(line).replaceFirst("").strip();
            if (line.length() < (loose ? LOOSE_LINE_MIN : LINE_MIN) || junk(line)) {
                continue;
            }
            if (loose && !(line.endsWith(".") || line.endsWith("다") || line.endsWith("\"") || line.endsWith("”"))) {
                continue;
            }
            lines.add(line);
        }
        if (lines.isEmpty()) {
            return null;
        }
        List<String> kept = new ArrayList<>();
        int size = 0;
        for (String line : lines) {
            if (size + line.length() + 1 > MAX_CHARS) {
                if (kept.isEmpty()) {
                    kept.add(line.substring(0, MAX_CHARS));
                }
                break;
            }
            kept.add(line);
            size += line.length() + 1;
        }
        return String.join("\n", kept);
    }

    private static boolean junk(String line) {
        if (EMAIL.matcher(line).find()) {
            return true;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        for (String word : JUNK) {
            if (lower.contains(word)) {
                return true;
            }
        }
        if (line.length() < SHORT_LINE) {
            for (String word : JUNK_SHORT) {
                if (lower.contains(word)) {
                    return true;
                }
            }
        }
        return false;
    }
}
