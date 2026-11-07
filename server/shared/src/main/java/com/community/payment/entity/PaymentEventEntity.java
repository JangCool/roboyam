package com.community.payment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 결제 이력 한 줄 — shared V97 의 {@code payment_events}(종류 CHECK 는 V98 이 RECHECKED 를 더했다). 거래 기록이라 5년 보관, {@code detail} 에 개인정보를 넣지 않는다.
 * 만들기는 {@link #of}(저장소가 넣고 바로 내보낸다). 처리한 웹훅({@code provider_event_id} 부분 유일)은 {@code ON CONFLICT … DO NOTHING}(네이티브)으로 넣는다.
 */
@Entity
@Table(name = "payment_events")
public class PaymentEventEntity {

    @Id
    private Long id;

    private Long paymentOrderId;
    /** 이력 종류 — {@code PaymentEventStore.Kind} 이름(DB CHECK 와 같은 목록) */
    private String kind;
    /** 웹훅 webhook-id(웹훅 이력만) */
    private String providerEventId;
    /** jsonb — 비었으면 null */
    @JdbcTypeCode(SqlTypes.JSON)
    private String detail;
    private Instant createdAt;

    protected PaymentEventEntity() {
    }

    /** 이력 한 줄(웹훅이 아닌 것 — {@code provider_event_id} 없음) */
    public static PaymentEventEntity of(long id, long paymentOrderId, String kind, String detailJson, Instant now) {
        PaymentEventEntity e = new PaymentEventEntity();
        e.id = id;
        e.paymentOrderId = paymentOrderId;
        e.kind = kind;
        e.detail = detailJson;
        e.createdAt = now;
        return e;
    }
}
