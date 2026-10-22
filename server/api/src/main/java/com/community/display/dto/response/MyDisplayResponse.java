package com.community.display.dto.response;

/**
 * {@code GET}·{@code PUT}·{@code DELETE /api/me/display} — user-web {@code MyDisplayResponse} 와 같은 모양.
 *
 * @param own       내가 고른 값. 정한 적이 없으면(행 없음) null
 * @param defaults  사이트 표시 기본값(설정 행이 없으면 처음 값)
 * @param effective {@code own ?? defaults}
 */
public record MyDisplayResponse(DisplayValuesView own, DisplayValuesView defaults, DisplayValuesView effective) {
}
