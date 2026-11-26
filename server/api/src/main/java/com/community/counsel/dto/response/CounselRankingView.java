package com.community.counsel.dto.response;

import java.util.List;

/**
 * 이번 주 상담 순위(user-web {@code CounselRanking} — {@code GET /api/boards/{slug}/counsel/ranking}). 사연 쓴 사람이 「가장 도움 된 조언」으로 고른 수 기준.
 *
 * @param weekStart 집계 구간 첫날(그 주 월요일, 한국 날짜 YYYY-MM-DD)
 * @param weekEnd   마지막 날(일요일)
 */
public record CounselRankingView(String weekStart, String weekEnd, int totalPicks, List<CounselRankRowView> rows) {
}
