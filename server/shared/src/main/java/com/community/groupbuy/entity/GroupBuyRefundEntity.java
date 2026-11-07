package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 환불 기록(결정 36·75) — shared V101 · V102.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_refunds")
public class GroupBuyRefundEntity {

    @Id
    private Long id;
    private Long participationId;
    private Long groupBuyId;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private RefundCause cause;
    private Long requestId;
    private Long itemRefund;
    private Long shippingRefund;
    private Long returnFee;
    private Long reshippingFee;
    private Long reshippingRefund;
    private Long compensationPg;
    private Long compensationBank;
    private Long amount;
    private String method;
    private Long paymentOrderId;
    private Long paymentCancelId;
    private String requestKey;
    @Enumerated(EnumType.STRING)
    private RefundStatus status;
    @JdbcTypeCode(SqlTypes.JSON)
    private String lines;
    private Long actorId;
    private String reason;
    private Instant createdAt;
    private Instant completedAt;
    private Long feeCovered;
    private Long feeRefund;
    private Long operatorAmount;

    protected GroupBuyRefundEntity() {
    }

    /**
     * 새 환불 기록 — 0원이면 취소를 넣지 않고 바로 완료(method NONE — 결정 68), 아니면 진행 중(PG_CANCEL). 요청 키 = {@code requestKey}(결제 취소 대기열과 같은 키).
     */
    public static GroupBuyRefundEntity create(long id, long participationId, long groupBuyId, long userId, RefundCause cause, Long requestId, long itemRefund,
                                              long shippingRefund, long reshippingFee, long reshippingRefund, long returnFee, long compensationPg, long compensationBank,
                                              long feeCovered, long feeRefund, long operatorAmount, long amount, Long paymentOrderId, String requestKey, String linesJson,
                                              Long actorId, String reason, Instant now) {
        boolean none = amount == 0;
        GroupBuyRefundEntity r = new GroupBuyRefundEntity();
        r.id = id;
        r.participationId = participationId;
        r.groupBuyId = groupBuyId;
        r.userId = userId;
        r.cause = cause;
        r.requestId = requestId;
        r.itemRefund = itemRefund;
        r.shippingRefund = shippingRefund;
        r.reshippingFee = reshippingFee;
        r.reshippingRefund = reshippingRefund;
        r.returnFee = returnFee;
        r.compensationPg = compensationPg;
        r.compensationBank = compensationBank;
        r.feeCovered = feeCovered;
        r.feeRefund = feeRefund;
        r.operatorAmount = operatorAmount;
        r.amount = amount;
        r.method = none ? "NONE" : "PG_CANCEL";
        r.paymentOrderId = paymentOrderId;
        r.requestKey = requestKey;
        r.status = none ? RefundStatus.COMPLETED : RefundStatus.IN_PROGRESS;
        r.lines = linesJson;
        r.actorId = actorId;
        r.reason = reason;
        r.createdAt = now;
        r.completedAt = none ? now : null;
        return r;
    }
}
