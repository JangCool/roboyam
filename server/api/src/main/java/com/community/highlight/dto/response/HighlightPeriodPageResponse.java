package com.community.highlight.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/highlights}(user-web {@code AchuListResponse}) — 그 기간에 고른 홈 하이라이트, 고른 시각 최신순(같으면 id 큰 것 먼저), 쪽 번호.
 *
 * @param page       요청한 쪽(1부터) — 하나도 없으면 1
 * @param totalPages 최소 1
 * @param totalCount 지금 볼 수 있는 글만 센 수
 * @param period     {@code DAY} · {@code WEEK} · {@code MONTH}
 * @param from       그 기간의 시작(서비스 시간대 한국시간 달력의 0시)
 * @param to         다음 기간의 시작(미포함)
 * @param weekStart  인기글 설정의 주 시작 요일 {@code MONDAY} · {@code SUNDAY} — 주간의 범위를 정한다. 어느 기간에도 늘 실린다
 */
public record HighlightPeriodPageResponse(List<HighlightPeriodItemView> items, int page, int totalPages, long totalCount, boolean hasNext, String period,
                                          Instant from, Instant to, String weekStart) {
}
