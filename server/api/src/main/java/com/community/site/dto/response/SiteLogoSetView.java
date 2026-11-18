package com.community.site.dto.response;

import com.community.site.dto.SiteLogoUrls;

/**
 * 로고 묶음 한 벌의 공개 모양 — {@code GET /api/site/info} 의 {@code genderLogos.male}·{@code female}(V249).
 *
 * @param logos        4칸({@code koLight}·{@code koDark}·{@code enLight}·{@code enDark}) — 그림 파일은 저장소 주소, SVG 칸은 {@code /api/site/logos/{칸}.svg?b=묶음&v=지문}. 빈 칸은 null
 * @param nameIncluded 로고 그림에 사이트 이름이 들어 있는가 — 켜져 있으면 로고 옆 이름 글자를 숨긴다
 */
public record SiteLogoSetView(SiteLogoUrls logos, boolean nameIncluded) {
}
