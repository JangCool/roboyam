package com.community.dashboard.dto.response.gadget;

/**
 * {@code HOTDEAL_STATUS} — 진행 중(살아 있는 글의 진행 중 딜 — 확인 필요 포함) · 오늘(한국 날짜) 등록 · 오늘 마감 예정(진행 중 중) · 확인 필요(진행 중 + {@code flagged_at}).
 */
public record DashboardHotdealStatusView(long running, long registeredToday, long endingToday, long flagged) implements DashboardGadgetData {
}
