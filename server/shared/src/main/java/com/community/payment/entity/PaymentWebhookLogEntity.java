package com.community.payment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 웹훅 수신 기록 한 줄 — shared V97 의 {@code payment_webhook_logs}. 받은 웹훅 전부(결과·HTTP 상태·오류 코드), <b>본문은 저장하지 않는다</b>. 90일 정리는 DB 배치 {@code R__batch_31}.
 * 만들기는 {@link #of}(칸 길이는 저장소가 자른다).
 */
@Entity
@Table(name = "payment_webhook_logs")
public class PaymentWebhookLogEntity {

    @Id
    private Long id;

    private String webhookId;
    private String eventType;
    private String paymentId;
    /** 처리 결과 — {@code PaymentWebhookLogStore.Result} 이름(DB CHECK 와 같은 목록) */
    private String result;
    private Integer httpStatus;
    private String errorCode;
    private Instant receivedAt;

    protected PaymentWebhookLogEntity() {
    }

    public static PaymentWebhookLogEntity of(long id, String webhookId, String eventType, String paymentId, String result, int httpStatus, String errorCode,
                                             Instant receivedAt) {
        PaymentWebhookLogEntity w = new PaymentWebhookLogEntity();
        w.id = id;
        w.webhookId = webhookId;
        w.eventType = eventType;
        w.paymentId = paymentId;
        w.result = result;
        w.httpStatus = httpStatus;
        w.errorCode = errorCode;
        w.receivedAt = receivedAt;
        return w;
    }
}
