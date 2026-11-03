package com.community.dashboard.dto.response.gadget;

/**
 * {@code MEMBER_STATS} — 오늘(한국 날짜) 가입 · 오늘 탈퇴(완료) · 지금 정지 중(기간 정지 + 영구 정지) · 탈퇴 대기(예약 — 남은 일이 끝나면 자동 완료) 회원 수.
 * 휴면 기능이 없어 휴면 예정 칸은 없다(사용자 결정 2026-11-03).
 */
public record DashboardMemberStatsView(long joinedToday, long withdrawnToday, long suspended, long withdrawing) implements DashboardGadgetData {
}
