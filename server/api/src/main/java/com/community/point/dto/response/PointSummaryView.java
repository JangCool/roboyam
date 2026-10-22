package com.community.point.dto.response;

/**
 * {@code GET /api/me/points} — user-web {@code PointSummary}(+ community {@code api-point.md} 의 나머지 칸).
 *
 * @param balance             보유 포인트(0 이상, 한 번도 없으면 0)
 * @param ticketCount         보유 닉네임 변경권 수 — 교환 기능이 아직 없어 늘 0
 * @param attendanceAvailable 출석 기능이 있는가 — 아직 없어 늘 false(화면은 「오늘 출석하기」 안내를 그리지 않는다)
 */
public record PointSummaryView(long balance, int ticketCount, boolean attendanceAvailable, PointTodayView today, PointRulesView rules) {
}
