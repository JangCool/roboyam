package com.community.payment.dto.request;

/**
 * 멈춘 취소 「수동 처리 완료」(공구 계약 결정 16·74 — 2b-4 에서 화면이 방법을 고른다). 사유 5~200자({@code fields.reason}).
 *
 * @param method     실제 처리 방법 — {@code CONSOLE_CANCEL}(포트원 콘솔에서 취소 — 취소 ID 를 붙이고 취소 누계까지) · {@code BANK_TRANSFER}(계좌로 돌려줌 — 누계 그대로).
 *                   비우면 예전처럼 「표시만」(방법 모름)
 * @param pgCancelId 콘솔 취소면 포트원 취소 ID(1~100자)
 */
public record PaymentManualCancelRequest(String reason, String method, String pgCancelId) {
}
