package com.community.site.dto.response;

/**
 * {@code GET /api/site/theme}(비로그인) — 운영자가 고른 공개 사이트 테마. user-web {@code ThemeResponse} 와 같은 모양이고 {@code /api/site/info} 의 {@code theme} 과 같은 값이다.
 *
 * @param theme 테마 id — 설정 행이 없거나 목록에 없는 값이면 {@code default}
 */
public record SiteThemeResponse(String theme) {
}
