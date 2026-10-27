package com.community.payment.dto.request;

/**
 * 사유가 필요한 결제 처리(다시 시도·수동 처리 완료·포트원에서 다시 확인·대사 이슈 처리) — 사유 5~200자({@code fields.reason}), 감사(돈 관련)에 남는다.
 */
public record PaymentReasonRequest(String reason) {
}
