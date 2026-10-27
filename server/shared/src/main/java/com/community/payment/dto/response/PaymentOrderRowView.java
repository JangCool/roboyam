package com.community.payment.dto.response;

import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.dto.PaymentStatus;
import java.time.Instant;

/**
 * 결제 주문 목록 한 줄({@code GET /api/admin/payments}) — admin-web {@code PaymentOrderRow} 모양.
 *
 * @param payMethod     포트원 결제 수단을 요청 언어로 푼 글자(모르는 값은 그대로)
 * @param cancelPending 끝나지 않은 취소(PENDING·SENDING·GAVE_UP)가 있는가
 */
public record PaymentOrderRowView(String id, String paymentId, PaymentMemberView member, PaymentOrderType orderType, String orderRefId, String orderName, long amount,
                                  Long paidAmount, long canceledAmount, PaymentStatus status, PaymentAnomaly anomaly, String payMethod, Instant paidAt, Instant expiresAt,
                                  Instant createdAt, boolean cancelPending) {
}
