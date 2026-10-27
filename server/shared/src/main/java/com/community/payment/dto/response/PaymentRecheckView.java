package com.community.payment.dto.response;

/**
 * 「포트원에서 다시 확인」 결과(확정한 목업 가정 2) — 무엇이 됐는지 + 새 상세.
 *
 * @param outcome CONFIRMED(이번에 확정 — {@code confirmedVia: ADMIN_RECHECK}) · UNCHANGED(이미 확정된 결제 — 취소 목록만 포트원과 맞췄다) · PG_NOT_PAID(포트원도 아직 결제 전)
 */
public record PaymentRecheckView(String outcome, PaymentOrderDetailView detail) {
}
