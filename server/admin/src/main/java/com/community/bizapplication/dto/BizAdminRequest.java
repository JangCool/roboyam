package com.community.bizapplication.dto;

/** 사업자 신청 건 심사 처리 본문 — 검사는 서비스(400 {@code fields.*}) */
public final class BizAdminRequest {

    private BizAdminRequest() {
    }

    public record Period(String startAt, String endAt) {
    }

    /**
     * 승인 — @param method ONLINE·MANUAL · @param supply 공급가(부가세는 서버가 10% 버림) · @param adjustReason 요금표와 다르면 1~200자 · @param deadlineAt 결제·입금 기한(ISO)
     */
    public record Approve(String method, Long supply, String adjustReason, String deadlineAt, Period period) {
    }

    /** @param code {@code BizRejectCode} · @param text 1~200자 */
    public record Reject(String code, String text) {
    }

    public record Manual(String deadlineAt, String reason) {
    }

    /** @param depositedOn {@code YYYY-MM-DD}(한국, 오늘까지) */
    public record Deposit(Long amount, String depositedOn, String depositorName, String mismatchReason) {
    }

    public record Deadline(String deadlineAt) {
    }

    public record Reason(String reason) {
    }

    public record RefundApprove(Long amount, String reason) {
    }

    public record Transfer(String note) {
    }
}
