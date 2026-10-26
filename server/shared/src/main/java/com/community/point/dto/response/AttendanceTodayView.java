package com.community.point.dto.response;

/**
 * 오늘(한국 시간) 출석 여부 — 달력이 어느 달을 보든 늘 오늘 기준.
 *
 * @param date     서버가 정한 오늘 {@code YYYY-MM-DD}(한국 시간) — 화면은 브라우저 시계로 오늘을 계산하지 않는다
 * @param attended 오늘 출석했나
 */
public record AttendanceTodayView(String date, boolean attended) {
}
