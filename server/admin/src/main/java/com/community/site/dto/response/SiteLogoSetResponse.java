package com.community.site.dto.response;

import com.community.site.dto.SiteLogoUrls;

/**
 * 사이트 로고 4칸 + 「로고에 사이트 이름이 들어 있음」(V237) — {@code /api/admin/site/logos/**} 가 읽기·바꾸기마다 전체를 돌려준다.
 *
 * @param logos      칸별 {@code <img src>}({@code koLight}·{@code koDark}·{@code enLight}·{@code enDark}) — 빈 칸은 null(대체 순서는 화면 규칙)
 * @param nameInLogo 켜져 있으면 공개 헤더가 로고 옆 이름 글자를 붙이지 않는다
 */
public record SiteLogoSetResponse(SiteLogoUrls logos, boolean nameInLogo) {
}
