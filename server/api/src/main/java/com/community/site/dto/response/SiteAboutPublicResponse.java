package com.community.site.dto.response;

/**
 * {@code GET /api/site/about}(비로그인) — 공개 푸터 「사이트 소개」 페이지 본문.
 *
 * @param html 사이트 소개(관리자 저장 때 소독한 HTML — 화면은 {@code v-html} 로 그린다). 없으면 null
 */
public record SiteAboutPublicResponse(String html) {
}
