package com.community.title.dto.request;

/**
 * 대표 칭호 고르기({@code PUT /api/me/titles/representative}) — {@code key} 가진 칭호의 키(null = 기본 — 가진 작가 칭호 중 가장 높은 것), {@code hidden} true 면 「안 보이기」(key 무시).
 */
public record RepresentativeTitleRequest(String key, Boolean hidden) {
}
