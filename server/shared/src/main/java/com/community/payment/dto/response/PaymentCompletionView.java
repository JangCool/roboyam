package com.community.payment.dto.response;

import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentStatus;
import java.time.Instant;

/**
 * 완료 API 의 응답 — 확정 뒤 결제 주문의 상태.
 *
 * @param accepted  받아들인 결제인가(결제됨 + 이상 없음). false 면 이상 결제(자동 취소 진행) — 화면은 「결제가 취소돼요」 안내를 띄운다
 * @param anomaly   이상(없으면 null)
 * @param paidAmount 포트원 결제 금액
 */
public record PaymentCompletionView(String paymentId, String orderType, String orderRefId, PaymentStatus status, boolean accepted, PaymentAnomaly anomaly, long amount,
                                    Long paidAmount, Instant paidAt) {
}
