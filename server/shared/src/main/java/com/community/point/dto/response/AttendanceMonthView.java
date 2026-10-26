package com.community.point.dto.response;

import java.util.List;

/**
 * {@code GET /api/me/attendance?month=YYYY-MM} — 한 달의 출석 달력(community {@code docs/api-point.md} 「출석」 + 로보얌 연속 일수).
 *
 * @param month      {@code YYYY-MM}(없이 부르면 이번 달 — 한국 시간)
 * @param days       그 달에 출석한 날의 일 번호(오름차순) — 미래의 달은 빈 목록
 * @param monthCount {@code days} 의 수
 * @param streak     오늘 또는 어제까지 이어진 연속 출석 일수(끊겼으면 0) — 달과 상관없이 오늘 기준
 * @param today      오늘 출석 여부
 */
public record AttendanceMonthView(String month, List<Integer> days, int monthCount, int streak, AttendanceTodayView today) {
}
