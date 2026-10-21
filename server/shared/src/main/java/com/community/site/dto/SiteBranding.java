package com.community.site.dto;

/**
 * 화면 상단에 쓰는 사이트 이름과 로고.
 *
 * @param logoUrl 로고의 {@code <img src>}(그림 파일 주소 또는 SVG data URI). 로고가 없으면 null
 */
public record SiteBranding(String siteName, String logoUrl) {
}
