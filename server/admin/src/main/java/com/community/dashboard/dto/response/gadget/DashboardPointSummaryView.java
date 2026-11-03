package com.community.dashboard.dto.response.gadget;

/**
 * {@code POINT_SUMMARY} — 오늘(한국 날짜) 지급(EARN 합) · 사용(USE 합) · 7일 안에 만료될 무료 포인트 남은 양.
 *
 * @param refundPending 환불 승인 대기 — 승인 기능(point.refund.approve)이 아직 없어 지금은 늘 null
 */
public record DashboardPointSummaryView(long grantedToday, long usedToday, long expiringSoon, Long refundPending) implements DashboardGadgetData {
}
