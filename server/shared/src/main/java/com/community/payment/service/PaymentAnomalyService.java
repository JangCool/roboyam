package com.community.payment.service;

import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 결제 이상을 운영자에게 알린다(결정 10 — {@code COMMERCE_PAYMENT_ANOMALY}, CRITICAL, 받는 사람 {@code commerce.reconcile.manage} 보유자). 알리는 일은 둘:
 * 금액 불일치(확정 때)와 취소 최종 실패(GAVE_UP). 늦은 결제·도메인 거절은 자동 취소가 정상 경로라 알리지 않는다(취소가 끝내 실패하면 GAVE_UP 으로 알린다).
 * 업무 트랜잭션 안에서 부른다 — 알림 실패는 업무를 깨지 않는다(발행기의 세이브포인트). 같은 결제의 알림은 묶음 키(결제 ID)로 묶인다.
 */
@Service
public class PaymentAnomalyService {

    private static final Logger log = LoggerFactory.getLogger(PaymentAnomalyService.class);

    /** 알리는 까닭 */
    public enum Reason {
        AMOUNT_MISMATCH,
        CANCEL_GAVE_UP
    }

    private final NotificationPublisher publisher;

    public PaymentAnomalyService(NotificationPublisher publisher) {
        this.publisher = publisher;
    }

    public void notifyOperators(OrderRow order, Reason reason) {
        log.warn("결제 이상 paymentOrderId={} paymentId={} reason={}", order.id(), order.paymentId(), reason);
        publisher.publish(AdminNotificationEvent.COMMERCE_PAYMENT_ANOMALY, NotificationContext.empty().dedupe(order.paymentId()));
    }
}
