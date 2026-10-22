package com.community.display.dto.response;

/**
 * {@code GET /api/display/effective} — 지금 사람이 볼 표시 값. user-web {@code DisplayEffectiveResponse} 와 같은 모양.
 *
 * @param values        효과값(개인 값 → 사이트 표시 기본값 → 시스템 폴백)
 * @param from          {@code USER}(개인 값 행이 있다) · {@code DEFAULT}(사이트 설정 행의 기본값) · {@code FALLBACK}(사이트 설정 행이 없다 — 설치 전·시험)
 * @param serviceZoneId 서비스 시간대(서비스 하루의 기준) — 표시 시간대와 다를 때 「한국 시간 기준」 같은 덧말의 재료
 */
public record DisplayEffectiveResponse(DisplayValuesView values, String from, String serviceZoneId) {
}
