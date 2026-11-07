package com.community.withdrawal.entity;

import com.community.withdrawal.dto.WithdrawalChoices;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 탈퇴 신청 한 줄 — shared V132·V134·V148 의 {@code member_withdrawals}. 개인정보는 없다(회원 번호·고른 것·금액·시각·운영 메모).
 * 넣기만 엔티티로 하고({@link #request}), 상태 전이(완료·취소)·스냅샷 고치기는 {@code MemberWithdrawalStore} 의 조건부 대량 UPDATE 다(진행 중일 때만 — 바뀐 줄 수로 안다).
 * 읽기도 그 저장소의 프로젝션이라 이 엔티티의 칸 값은 넣은 뒤로 읽지 않는다.
 */
@Entity
@Table(name = "member_withdrawals")
public class MemberWithdrawalEntity {

    @Id
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private WithdrawalStatus status;

    @Enumerated(EnumType.STRING)
    private WithdrawalSource source;

    /** 신청 전 계정 상태(ACTIVE·SUSPENDED·BANNED) */
    private String priorStatus;
    private String reason;
    private String reasonNote;
    private String paidPointChoice;
    private String deliveredChoice;
    private String bizChoice;

    /** 소모임 선택(JSON 배열 글자) */
    @JdbcTypeCode(SqlTypes.JSON)
    private String groupChoices;

    private Long forfeitPaidPoints;
    private Long forfeitRefundEstimate;
    private Long forfeitBizAmount;
    private Instant forfeitConsentedAt;

    /** 남은 일 스냅샷(JSON 배열 글자) */
    @JdbcTypeCode(SqlTypes.JSON)
    private String pendingSnapshot;

    private int pendingCount;
    private Instant expectedCompleteAt;
    private Instant rejoinUntil;
    private Boolean hasPaymentHistory;
    private Instant progressNotifiedAt;
    private Instant requestedAt;
    private Instant completedAt;
    private Instant canceledAt;
    private String forceReasonKind;
    private String forceMemo;
    private Long forceOperatorId;
    private String cancelChannel;
    private String cancelMemo;
    private Long cancelOperatorId;
    private Long forfeitEmoticonEarnings;

    protected MemberWithdrawalEntity() {
    }

    /**
     * 새 신청 줄. 상태가 완료(COMPLETED)로 들어오면 완료 시각 = 신청 시각이다(CHECK {@code ck_mw_completed}).
     *
     * @param forceReasonKind 운영자 강제 탈퇴의 사유 종류 — 회원 신청이면 {@code forceMemo}·{@code forceOperatorId} 와 함께 null
     */
    public static MemberWithdrawalEntity request(long id, long userId, WithdrawalStatus status, WithdrawalSource source, String priorStatus, String reason, String reasonNote,
                                                 WithdrawalChoices choices, String groupChoicesJson, Long forfeitPaidPoints, Long forfeitRefundEstimate, Long forfeitBizAmount,
                                                 Instant forfeitConsentedAt, String pendingJson, int pendingCount, Instant expectedCompleteAt, Instant rejoinUntil,
                                                 Boolean hasPaymentHistory, Instant requestedAt, String forceReasonKind, String forceMemo, Long forceOperatorId,
                                                 Long forfeitEmoticonEarnings) {
        MemberWithdrawalEntity w = new MemberWithdrawalEntity();
        w.id = id;
        w.userId = userId;
        w.status = status;
        w.source = source;
        w.priorStatus = priorStatus;
        w.reason = reason;
        w.reasonNote = reasonNote;
        w.paidPointChoice = choices.paidPointChoice();
        w.deliveredChoice = choices.deliveredChoice();
        w.bizChoice = choices.bizChoice();
        w.groupChoices = groupChoicesJson;
        w.forfeitPaidPoints = forfeitPaidPoints;
        w.forfeitRefundEstimate = forfeitRefundEstimate;
        w.forfeitBizAmount = forfeitBizAmount;
        w.forfeitConsentedAt = forfeitConsentedAt;
        w.pendingSnapshot = pendingJson;
        w.pendingCount = pendingCount;
        w.expectedCompleteAt = expectedCompleteAt;
        w.rejoinUntil = rejoinUntil;
        w.hasPaymentHistory = hasPaymentHistory;
        w.requestedAt = requestedAt;
        w.completedAt = status == WithdrawalStatus.COMPLETED ? requestedAt : null;
        w.forceReasonKind = forceReasonKind;
        w.forceMemo = forceMemo;
        w.forceOperatorId = forceOperatorId;
        w.forfeitEmoticonEarnings = forfeitEmoticonEarnings;
        return w;
    }

    public Long getId() {
        return id;
    }
}
