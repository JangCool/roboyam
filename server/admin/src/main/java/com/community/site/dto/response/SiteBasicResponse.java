package com.community.site.dto.response;

/**
 * 사이트 기본 정보.
 *
 * @param tagline 한 줄 소개. 없으면 null
 * @param logoUrl 로고의 {@code <img src>} — 그림 파일이면 {@code /media/…} 주소, SVG 면 서버가 만든 data URI. 로고가 없으면 null
 */
public record SiteBasicResponse(String siteName, String tagline, String logoUrl) {
}
