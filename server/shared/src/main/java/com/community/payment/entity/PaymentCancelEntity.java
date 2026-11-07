package com.community.payment.entity;

import com.community.payment.dto.CancelKind;
import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.ManualCancelMethod;
import com.community.payment.dto.PgCancelStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 취소 대기열 한 줄 — shared V97 의 {@code payment_cancels}(+ V102 {@code manual_method}, V100 「한 결제에 보내는 중은 하나」 부분 유일 색인).
 * <b>넣기·읽기·조건부 고치기 전용 매핑</b>이다 — 넣기는 같은 요청 키를 두 번 넣지 않는 {@code INSERT … ON CONFLICT (request_key) DO NOTHING}(네이티브),
 * 집기는 {@code UPDATE … RETURNING}(네이티브), 그 밖의 상태 전이는 저장소({@code PaymentCancelStore})의 QueryDSL 조건부 UPDATE 다. 엔티티로 상태를 고치지 않는다.
 */
@Entity
@Table(name = "payment_cancels")
public class PaymentCancelEntity {

    @Id
    private Long id;

    private Long paymentOrderId;
    private Long amount;
    /** 내부 사유(포트원에는 {@code rc:<취소 ID>} 표시를 붙인 사유가 따로 나간다) */
    private String reason;
    @Enumerated(EnumType.STRING)
    private CancelKind kind;
    /** 같은 취소를 두 번 넣지 않는 자연 키(유일) */
    private String requestKey;
    /** 우리 상태 — PENDING·SENDING·SUCCEEDED·GAVE_UP·MANUAL */
    @Enumerated(EnumType.STRING)
    private CancelStatus status;
    /** 포트원 응답 — REQUESTED·SUCCEEDED·FAILED */
    @Enumerated(EnumType.STRING)
    private PgCancelStatus pgStatus;
    /** 포트원 취소 내역 아이디(부분 유일) */
    private String pgCancelId;
    private Integer attempts;
    private Instant nextAttemptAt;
    /** 집은 서버 번호(SENDING 일 때만) */
    private Integer lockedBy;
    private Instant lockedAt;
    /** 가린 한 줄 */
    private String lastError;
    /** 요청한 운영자(시스템이면 null) */
    private Long requestedBy;
    private Long resolvedBy;
    private String resolveNote;
    @Enumerated(EnumType.STRING)
    private ManualCancelMethod manualMethod;
    private Instant createdAt;
    private Instant completedAt;

    protected PaymentCancelEntity() {
    }
}
