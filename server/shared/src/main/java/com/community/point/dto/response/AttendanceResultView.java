package com.community.point.dto.response;

/**
 * {@code POST /api/me/attendance} — 오늘 출석한 결과.
 *
 * @param date       출석한 날(한국 시간 {@code YYYY-MM-DD})
 * @param earned     출석 포인트로 실제 받은 양(정책 0·포인트 동결·하루 총 한도면 0 — 출석은 남는다)
 * @param streakBonus 연속 출석 보너스로 실제 받은 양(배수 날이 아니거나 꺼졌으면 0)
 * @param streak     이 출석으로 이어진 연속 일수(1 이상)
 * @param balance    출석 뒤 포인트 합계
 */
public record AttendanceResultView(String date, long earned, long streakBonus, int streak, long balance) {
}
