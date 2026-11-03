package com.community.dashboard.dto.response.gadget;

/**
 * {@code OPERATOR_SECURITY} — 운영자 수 · 잠긴(운영자가 잠근 계정 — 로그인 실패 10분 잠금은 세지 않는다) · 2차 인증 미등록(승인·사용 중·만료 아님 운영자 중).
 */
public record DashboardOperatorSecurityView(long total, long locked, long mfaMissing) implements DashboardGadgetData {
}
