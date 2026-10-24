package com.community.highlight.dto.response;

import java.util.List;

/**
 * 한 입장의 의견 쪽 — 고정한 의견 먼저 → 추천 많은 순 → 최근 순. 숨긴 의견도 들어 있다(지운 의견은 없다).
 *
 * @param total 그 쪽 전체 의견 수(숨긴 것 포함)
 * @param items {@code stance} 로 다른 쪽만 읽었으면 빈 배열
 */
public record HighlightOpinionPageView(int total, int page, int size, boolean hasNext, List<HighlightOpinionView> items) {
}
