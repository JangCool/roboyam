package com.community.payment.dto.response;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 대사 이슈 한 줄 — {@code detail} 은 서버가 요청 언어로 푼 한 줄(포트원 값 ↔ 우리 값), 처리한 운영자는 이름만.
 *
 * @param paymentOrderId 우리 결제 주문(없으면 null — 「우리 주문 없음」)
 */
public record ReconcileIssueView(String id, String type, String paymentId, String paymentOrderId, LocalDate detectedDay, String detail, String status,
                                 String resolveNote, String resolvedBy, Instant resolvedAt, Instant createdAt) {
}
