package com.community.site.service;

import com.community.terms.service.TermsHtml;

/**
 * 사이트 소개(공개 사이트 푸터 「사이트 소개」 페이지, V203 {@code site_settings.about_html}) 저장 규칙.
 *
 * <ul>
 *   <li>길이: 보낸 HTML(소독 <b>전</b>) {@value #HTML_MAX}자 이하 — 넘으면 {@code 400 VALIDATION} {@code fields.html}.</li>
 *   <li>소독: 약관 본문과 같은 허용 목록({@link TermsHtml} — {@code p br strong em u s a ul ol li blockquote h2 h3 hr}, 링크는 http·https·mailto 만,
 *       늘 {@code target="_blank"} {@code rel="nofollow noopener noreferrer"}). 스크립트·style·이벤트 속성은 사라진다(글자는 남는다).</li>
 *   <li>빈 값: 비우거나 공백뿐이거나 소독 뒤 보이는 글자가 없으면 {@code null}(= 소개 없음, 공개 푸터 링크를 숨긴다).</li>
 * </ul>
 */
public final class SiteAbouts {

    /** 보낸 HTML 의 상한(소독 전, 글자 수 = {@link String#length()}) */
    public static final int HTML_MAX = 20_000;

    private SiteAbouts() {
    }

    /** 보낸 HTML 이 상한을 넘는가 */
    public static boolean tooLong(String raw) {
        return raw != null && raw.length() > HTML_MAX;
    }

    /** 저장할 값 — 소독한 HTML, 보이는 글자가 없으면 null */
    public static String clean(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String safe = TermsHtml.sanitize(raw);
        return TermsHtml.hasVisibleText(safe) ? safe : null;
    }

    /** 소개가 있는가(공개 사이트 정보의 {@code about}) */
    public static boolean present(String storedHtml) {
        return storedHtml != null && !storedHtml.isBlank();
    }
}
