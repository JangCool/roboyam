package com.community.terms.service;

import java.util.regex.Pattern;
import org.owasp.html.ElementPolicy;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.web.util.HtmlUtils;

/**
 * 약관 본문 HTML 정화(OWASP Java HTML Sanitizer) — 허용 목록 {@code p br strong em u s a ul ol li blockquote h2 h3 hr}
 * (게시판 {@code RichText} 의 간단 수준 + {@code h2 h3 hr}). 링크는 http·https·mailto 만, 늘 {@code target="_blank"} {@code rel="nofollow noopener noreferrer"}.
 * 그 밖의 태그·속성은 버리고 글자는 남긴다. 공개 화면이 이 결과를 {@code v-html} 로 그리므로 저장 전에 <b>반드시</b> 거친다.
 */
public final class TermsHtml {

    /** 정화 후 본문 상한(글자 수) */
    public static final int BODY_MAX = 200_000;

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern TRAILING_EMPTY_PARAGRAPHS = Pattern.compile("(?:<p></p>|<p><br\\s*/?></p>|\\s)+$");
    private static final Pattern LEADING_EMPTY_PARAGRAPHS = Pattern.compile("^(?:<p></p>|<p><br\\s*/?></p>|\\s)+");

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "strong", "em", "u", "s", "blockquote", "ul", "ol", "li", "h2", "h3", "hr")
            .allowElements(newTab(), "a")
            .allowUrlProtocols("http", "https", "mailto")
            .allowAttributes("href").onElements("a")
            .requireRelsOnLinks("nofollow", "noopener", "noreferrer")
            .toFactory();

    private TermsHtml() {
    }

    /** 허용 목록으로 정화한 HTML. 앞뒤의 빈 문단은 떼어 낸다 */
    public static String sanitize(String raw) {
        String safe = POLICY.sanitize(raw == null ? "" : raw);
        // 정화기는 rel 을 「noreferrer noopener nofollow」 차례로 쓴다 — 계약의 차례로 맞춘다(뜻은 같다, RichText 와 같다)
        safe = safe.replace(" rel=\"noreferrer noopener nofollow\"", " rel=\"nofollow noopener noreferrer\"");
        safe = TRAILING_EMPTY_PARAGRAPHS.matcher(safe).replaceFirst("");
        return LEADING_EMPTY_PARAGRAPHS.matcher(safe).replaceFirst("");
    }

    /** 정화된 HTML 에 보이는 글자가 있는가(태그를 떼고 엔티티를 푼 뒤 공백만 남지 않는가) */
    public static boolean hasVisibleText(String safeHtml) {
        if (safeHtml == null || safeHtml.isEmpty()) {
            return false;
        }
        String text = HtmlUtils.htmlUnescape(TAG.matcher(safeHtml).replaceAll(" ")).replace(' ', ' ');
        return !text.isBlank();
    }

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
}
