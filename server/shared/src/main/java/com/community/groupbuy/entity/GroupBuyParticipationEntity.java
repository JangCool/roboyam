package com.community.groupbuy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 주문 — shared V101.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_participations")
public class GroupBuyParticipationEntity {

    @Id
    private Long id;
    private String orderNo;
    private Long groupBuyId;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private ParticipationStatus status;
    private Long itemsAmount;
    private Long shippingFee;
    private Long amount;
    private Long pointAmount;
    private Long cashAmount;
    private Long refundedAmount;
    private Long compensationAmount;
    private Long pointRefunded;
    private Long reshippingFeeCharged;
    private Long shippedShippingFee;
    private Integer groupBuyRevision;
    private Long paymentOrderId;
    private Instant reservedUntil;
    @Enumerated(EnumType.STRING)
    private ExpireCause expireCause;
    private String recipientEnc;
    private String phoneEnc;
    private String postcode;
    @Column(name = "address1_enc")
    private String address1Enc;
    @Column(name = "address2_enc")
    private String address2Enc;
    private String memoEnc;
    private Instant addressMaskedAt;
    private String cashReceiptType;
    private String cashReceiptNoEnc;
    private String adminMemo;
    private Instant joinedAt;
    private Instant confirmedAt;
    private Instant canceledAt;
    private Instant refundedAt;
    private Instant expiredAt;
    private Instant orderedAt;
    private Instant shippedAt;
    private Instant deliveredAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected GroupBuyParticipationEntity() {
    }

    /** 새 주문(결제 중) — 현금 결제액 = 금액(포인트 결제 없음), 환불 누계 등은 DB 기본값(0) */
    public static GroupBuyParticipationEntity pending(long id, String orderNo, long groupBuyId, long userId, long itemsAmount, long shippingFee, long amount,
                                                      int groupBuyRevision, Instant reservedUntil, String recipientEnc, String phoneEnc, String postcode,
                                                      String address1Enc, String address2Enc, String memoEnc, Instant now) {
        GroupBuyParticipationEntity p = new GroupBuyParticipationEntity();
        p.id = id;
        p.orderNo = orderNo;
        p.groupBuyId = groupBuyId;
        p.userId = userId;
        p.status = ParticipationStatus.PENDING_PAYMENT;
        p.itemsAmount = itemsAmount;
        p.shippingFee = shippingFee;
        p.amount = amount;
        p.cashAmount = amount;
        p.groupBuyRevision = groupBuyRevision;
        p.reservedUntil = reservedUntil;
        p.recipientEnc = recipientEnc;
        p.phoneEnc = phoneEnc;
        p.postcode = postcode;
        p.address1Enc = address1Enc;
        p.address2Enc = address2Enc;
        p.memoEnc = memoEnc;
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }
}
