package com.community.payment.dto;

import java.time.Instant;

/**
 * 처리기에 넘기는 「끝난 취소」 — 취소 한 건이 포트원에서 성공했고 결제 주문의 취소 누계에 반영됐다(취소 한 건마다 한 번).
 * 회원 알림·메일(자동 취소 안내 등)은 도메인이 이것을 받아 보낸다(결정 11).
 *
 * @param cancelId       취소 대기열 ID
 * @param kind           취소 종류
 * @param amount         이번 취소 금액
 * @param canceledTotal  반영한 뒤의 취소 누계
 * @param fullyCanceled  전액이 취소됐는가(결제 주문이 CANCELED)
 * @param anomaly        결제 이상(자동 취소면 그 까닭, 아니면 null)
 */
public record CanceledPayment(long paymentOrderId, String paymentId, long userId, PaymentOrderType orderType, long orderRefId, long cancelId, CancelKind kind,
                              long amount, long canceledTotal, boolean fullyCanceled, PaymentAnomaly anomaly, Instant canceledAt) {
}
