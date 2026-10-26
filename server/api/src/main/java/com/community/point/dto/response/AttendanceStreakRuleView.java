package com.community.point.dto.response;

/** 연속 출석 보너스 — 연속 {@code days} 일마다 {@code bonus} P(둘 중 0 이면 꺼짐) */
public record AttendanceStreakRuleView(int days, int bonus) {
}
