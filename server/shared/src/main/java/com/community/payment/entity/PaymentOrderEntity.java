package com.community.payment.entity;

import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.dto.PaymentStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 결제 주문 한 줄 — shared V97 의 {@code payment_orders}(종류 CHECK 는 V102·V112·V144 가 넓혔다). 만들기는 {@link #ready}(저장소가 넣고 바로 내보낸다).
 * 상태 전이(READY → PAID → 부분/전액 취소, READY → EXPIRED)와 이상 표시는 저장소의 조건부 UPDATE(이긴 쪽만 — commerce.md 3절)가 한다 — 엔티티로 고치지 않는다.
 * 읽기는 저장소가 한 줄 모양({@code PaymentOrderStore.OrderRow})으로 꺼낸다(영속성 문맥의 낡은 엔티티를 보지 않는다).
 *
 * <p>「받아들인 결제는 주문마다 하나」는 부분 유일 색인 {@code ux_payment_orders_accepted}(PAID·PARTIAL_CANCELED 이고 이상 없음)가 지킨다.
 */
@Entity
@Table(name = "payment_orders")
public class PaymentOrderEntity {

    @Id
    private Long id;

    /** 포트원 paymentId — {@code pay_} + 영숫자 32자, 유일 */
    private String paymentId;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private PaymentOrderType orderType;
    private Long orderRefId;
    private String orderName;
    /** 서버가 계산한 청구 금액(원) */
    private Long amount;
    private String currency;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    /** 포트원이 알려 준 결제 금액 — 결제 전에는 null */
    private Long paidAmount;
    /** 성공한 취소의 누계(0 ≤ 취소 ≤ 결제) */
    private Long canceledAmount;
    @Enumerated(EnumType.STRING)
    private PaymentAnomaly anomaly;
    private String payMethod;
    private String pgProvider;
    private String pgTxId;
    private String receiptUrl;
    private Instant paidAt;
    private Instant expiresAt;
    @Enumerated(EnumType.STRING)
    private ConfirmedVia confirmedVia;
    private Instant createdAt;
    private Instant updatedAt;

    protected PaymentOrderEntity() {
    }

    /** 새 결제 주문(READY, KRW, 취소 누계 0). 금액·주문명·유효 시각 검사는 부르는 서비스가 끝냈다 */
    public static PaymentOrderEntity ready(long id, String paymentId, long userId, PaymentOrderType orderType, long orderRefId, String orderName, long amount,
                                           Instant expiresAt, Instant now) {
        PaymentOrderEntity o = new PaymentOrderEntity();
        o.id = id;
        o.paymentId = paymentId;
        o.userId = userId;
        o.orderType = orderType;
        o.orderRefId = orderRefId;
        o.orderName = orderName;
        o.amount = amount;
        o.currency = "KRW";
        o.status = PaymentStatus.READY;
        o.canceledAmount = 0L;
        o.expiresAt = expiresAt;
        o.createdAt = now;
        o.updatedAt = now;
        return o;
    }
}
