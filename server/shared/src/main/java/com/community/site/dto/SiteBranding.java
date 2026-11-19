package com.community.site.dto;

/**
 * 화면 상단에 쓰는 사이트 이름과 로고.
 *
 * @param logoUrl    로고의 {@code <img src>}(그림 파일 주소 또는 SVG data URI) — 평소 묶음의 대표 한 장(① 한글·밝은 칸, 비었으면 채워진 첫 칸). 로고가 없으면 null
 * @param logos      평소 묶음 4칸(한글·영문 × 밝은·어두운 바탕용, SVG 는 data URI) — 관리자 앱이 놓인 면(강조색 상단 바·밝은 로그인 카드)에 맞는 칸을 고른다. 묶음이 없으면 {@link SiteLogoUrls#EMPTY}
 * @param nameInLogo 그림에 사이트 이름이 들어 있다(묶음 설정) — 화면이 이름 글자를 따로 붙이지 않는다
 */
public record SiteBranding(String siteName, String logoUrl, SiteLogoUrls logos, boolean nameInLogo) {
}
