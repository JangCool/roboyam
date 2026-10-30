package com.community.business.dto;

/** 사업자 심사 처리 본문 — 검사는 서비스(400 {@code fields.*}) */
public final class BusinessAdminRequest {

    private BusinessAdminRequest() {
    }

    /** @param ntsOverrideReason 국세청 조회 불가인데 승인할 때만(5~200자) */
    public record Approve(String ntsOverrideReason) {
    }

    /** @param code {@code BusinessRejectCode} · @param text 1~200자 */
    public record Reject(String code, String text) {
    }

    /** 정지(H2) — 진행 건 처리 셋은 필수(화면이 기본값을 채워 보낸다) */
    public record Suspend(String code, String text, String pendingAction, String scheduledAction, String runningAction) {
    }

    /** 정지 해제 — @param restore {@code EXTEND_RESTORE}(남은 기간 연장 후 되돌리기) · {@code KEEP_REFUND}(환불 유지), @param note 0~200자 */
    public record Unsuspend(String restore, String note) {
    }

    /** 평문 열람 사유 5~200자 */
    public record Reveal(String reason) {
    }
}
