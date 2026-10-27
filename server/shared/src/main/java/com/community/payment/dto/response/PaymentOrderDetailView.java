package com.community.payment.dto.response;

import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.dto.PaymentStatus;
import java.time.Instant;
import java.util.List;

/**
 * 결제 주문 상세({@code GET /api/admin/payments/{id}}) — admin-web {@code PaymentOrderDetail}(목록 한 줄의 칸 + 아래 칸) 모양. 열면 {@code PAYMENT_ORDER_VIEW} 감사(개인정보 열람).
 *
 * @param events         이력(오래된 것 위)
 * @param cancels        취소 대기열(넣은 차례)
 * @param webhooks       이 결제 번호로 받은 웹훅(최근 것 위)
 * @param openIssueCount 이 결제에 걸린 열린 대사 이슈 수
 */
public record PaymentOrderDetailView(String id, String paymentId, PaymentMemberView member, PaymentOrderType orderType, String orderRefId, String orderName, long amount,
                                     Long paidAmount, long canceledAmount, PaymentStatus status, PaymentAnomaly anomaly, String payMethod, Instant paidAt,
                                     Instant expiresAt, Instant createdAt, boolean cancelPending, String currency, String pgProvider, String pgTxId, String receiptUrl,
                                     ConfirmedVia confirmedVia, Instant updatedAt, List<PaymentEventView> events, List<PaymentCancelView> cancels,
                                     List<PaymentWebhookRowView> webhooks, int openIssueCount) {
}
