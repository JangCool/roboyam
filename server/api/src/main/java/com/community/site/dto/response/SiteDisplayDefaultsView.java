package com.community.site.dto.response;

/** 관리자가 정한 사이트 표시 기본값(언어·시간대·시간 형식·날짜 형식) — 설정 행이 없으면 설치 직후의 처음 값 */
public record SiteDisplayDefaultsView(String language, String zoneId, String timeFormat, String dateFormat) {
}
