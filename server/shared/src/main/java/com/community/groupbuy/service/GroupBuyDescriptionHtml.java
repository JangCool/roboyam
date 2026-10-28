package com.community.groupbuy.service;

import com.community.groupbuy.dto.ProductDraft;
import java.util.regex.Pattern;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/**
 * 상품 설명 HTML 정화(계약 결정 2 — 공용 편집기 {@code @roboyam/rich-editor} 가 만든 HTML 도 <b>서버에서 반드시</b> 거친다, OWASP Java HTML Sanitizer). 회원 상세가 이 결과를
 * {@code v-html} 로 그린다. 허용: {@code p br strong em u s blockquote ul ol li h2 h3 hr} + 링크(http·https·mailto, 늘 새 탭·{@code rel="nofollow noopener noreferrer"})
 * + 이미지({@code src} 는 https 또는 우리 미디어 경로 {@code /media/…} 만, {@code alt}·{@code width}·{@code height}). 스크립트·이벤트 속성·style·iframe 은 모두 사라진다(글자는 남는다).
 */
public final class GroupBuyDescriptionHtml {

    /** 정화 후 상한(글자 수) */
    public static final int MAX = 100_000;

    private static final Pattern MEDIA_OR_HTTPS = Pattern.compile("^(https://[^\\s\"'<>]+|/media/[A-Za-z0-9/_.\\-]+)$");
    private static final Pattern NUMBER = Pattern.compile("^[0-9]{1,4}$");
    private static final Pattern REL = Pattern.compile(" rel=\"(?:(?:nofollow|noopener|noreferrer) ?){3}\"");

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "strong", "em", "u", "s", "blockquote", "ul", "ol", "li", "h2", "h3", "hr", "img")
            .allowElements(newTab(), "a")
            .allowUrlProtocols("http", "https", "mailto")
            .allowAttributes("href").onElements("a")
            .requireRelsOnLinks("nofollow", "noopener", "noreferrer")
            .allowAttributes("src").matching(MEDIA_OR_HTTPS).onElements("img")
            .allowAttributes("alt").onElements("img")
            .allowAttributes("width", "height").matching(NUMBER).onElements("img")
            .allowStandardUrlProtocols()
            .toFactory();

    private GroupBuyDescriptionHtml() {
    }

    /** 정화한 HTML(null 이면 빈 글자) */
    public static String sanitize(String raw) {
        String safe = POLICY.sanitize(raw == null ? "" : raw);
        // 정화기가 쓰는 rel 차례를 계약의 차례로 맞춘다(뜻은 같다 — TermsHtml 과 같다)
        safe = REL.matcher(safe).replaceAll(" rel=\"nofollow noopener noreferrer\"");
        return safe.length() > MAX ? safe.substring(0, MAX) : safe;
    }

    /** 링크는 늘 새 탭으로(주소가 남은 링크만) — rel 은 정화기가 붙인다 */
    private static org.owasp.html.ElementPolicy newTab() {
        return (elementName, attributes) -> {
            for (int i = 0; i + 1 < attributes.size(); i += 2) {
                if ("href".equals(attributes.get(i))) {
                    attributes.add("target");
                    attributes.add("_blank");
                    break;
                }
            }
            return elementName;
        };
    }

    /** 설명만 정화한 상품 저장 값 */
    public static ProductDraft sanitized(ProductDraft d) {
        if (d == null) {
            return null;
        }
        return new ProductDraft(d.categoryId(), d.name(), d.summary(), d.spec(), sanitize(d.description()), d.normalPrice(), d.normalPriceBasis(), d.normalPriceSource(),
                d.withdrawalRestriction(), d.optionTypeCodes(), d.skus(), d.images());
    }
}
