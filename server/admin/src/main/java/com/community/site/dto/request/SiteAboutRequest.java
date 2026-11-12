package com.community.site.dto.request;

/**
 * 사이트 소개 저장 요청({@code PUT /api/admin/site/about}).
 *
 * @param html 소개 HTML — 서버가 소독한다({@code SiteAbouts}). 소독 전 20000자 이하. null·빈 글자·공백뿐이면 소개 없음
 */
public record SiteAboutRequest(String html) {
}
