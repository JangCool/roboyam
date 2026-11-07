package com.community.bizapplication.repository.master;

import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizRefundEntity;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.BizRefundType;
import com.community.bizapplication.entity.QBizRefundEntity;
import com.community.zconfiguration.jpa.JpaStoreWrites;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 환불 줄(V112 {@code biz_refunds}) — 신청 건의 refund 하위 값은 가장 최근 줄이다. 열린(REQUESTED) 줄은 신청 건당 하나(부분 유일 색인).
 * QueryDSL 만 쓴다 — 읽기는 한 줄 모양, 상태 전이는 조건부 DML, 넣기는 {@code persist} + {@code flush}(유일 위반 → {@code DuplicateKeyException}).
 */
@Repository
public class BizRefundStore extends RoboyamRepositoryQDSL {

    /** 송금 상태(수동) */
    public static final String TRANSFER_PENDING = "PENDING";
    public static final String TRANSFER_DONE = "DONE";

    private static final QBizRefundEntity R = QBizRefundEntity.bizRefundEntity;

    public record RefundRow(long id, long applicationId, BizRefundType type, BizRefundStatus status, BizPaymentMethod method, long paidAmount, long proposedAmount,
                            int usedDays, int totalDays, Long approvedAmount, String reason, String decisionReason, Long paymentCancelId, String transferStatus,
                            Instant transferredAt, Long transferredBy, String transferNote, Instant completedAt, Instant requestedAt, Instant decidedAt,
                            Long decidedBy, Long proratedAmount, Integer feeRate, Long feeAmount, Instant basisAt) {
    }

    /**
     * 광고주 요청 중단의 일할 환불(V127 {@code STOP_PRORATED} — 심사 대기) — 제안 내역: 일할 · 수수료율 · 수수료 · 기준 시각. 예상액({@code proposed}) = 일할 − 수수료.
     */
    public record NewStopRefund(long id, long applicationId, BizPaymentMethod method, long paidAmount, long proratedAmount, int feeRate, long feeAmount, int usedDays,
                                int totalDays, Instant basisAt, String reason, Instant requestedAt) {

        public long proposed() {
            return proratedAmount - feeAmount;
        }
    }

    /** 새 환불 줄 */
    public record NewRefund(long id, long applicationId, BizRefundType type, BizRefundStatus status, BizPaymentMethod method, long paidAmount, long proposedAmount,
                            int usedDays, int totalDays, Long approvedAmount, String reason, String transferStatus, Instant requestedAt, Instant decidedAt,
                            Long decidedBy, Instant completedAt) {
    }

    private static final Expression<RefundRow> ROW = Projections.constructor(RefundRow.class, R.id, R.applicationId, R.type, R.status, R.method, R.paidAmount,
            R.proposedAmount, R.usedDays, R.totalDays, R.approvedAmount, R.reason, R.decisionReason, R.paymentCancelId, R.transferStatus, R.transferredAt,
            R.transferredBy, R.transferNote, R.completedAt, R.requestedAt, R.decidedAt, R.decidedBy, R.proratedAmount, R.feeRate, R.feeAmount, R.basisAt);

    private final EntityManager entityManager;

    public BizRefundStore(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    public void insert(NewRefund r) {
        JpaStoreWrites.insert(entityManager, BizRefundEntity.create(r.id(), r.applicationId(), r.type(), r.status(), r.method(), r.paidAmount(), r.proposedAmount(),
                r.usedDays(), r.totalDays(), r.approvedAmount(), r.reason(), null, r.transferStatus(), r.requestedAt(), r.decidedAt(), r.decidedBy(), r.completedAt(),
                null, null, null, null));
    }

    /** 중단 일할 환불 줄(심사 대기) — 열린 줄이 이미 있으면 부분 유일 색인이 막는다({@code DuplicateKeyException}) */
    public void insertStop(NewStopRefund r) {
        JpaStoreWrites.insert(entityManager, BizRefundEntity.create(r.id(), r.applicationId(), BizRefundType.STOP_PRORATED, BizRefundStatus.REQUESTED, r.method(),
                r.paidAmount(), r.proposed(), r.usedDays(), r.totalDays(), null, r.reason(), null, null, r.requestedAt(), null, null, null, r.proratedAmount(),
                r.feeRate(), r.feeAmount(), r.basisAt()));
    }

    /**
     * 운영자 추가 환불(클레임 — V128) 줄 — 바로 승인. 사유는 운영자 결정 사유({@code decision_reason}, 관리자만 봄)에 두고 요청 사유({@code reason})는 비운다.
     * 수동은 송금 대기(PENDING), 온라인은 결제 모듈 취소를 부르는 쪽이 건다.
     */
    public void insertClaim(long id, long applicationId, BizPaymentMethod method, long paidAmount, long amount, int totalDays, String decisionReason, long operatorId,
                            Instant now) {
        JpaStoreWrites.insert(entityManager, BizRefundEntity.create(id, applicationId, BizRefundType.CLAIM, BizRefundStatus.APPROVED, method, paidAmount, amount, 0,
                totalDays, amount, null, decisionReason, method == BizPaymentMethod.MANUAL ? TRANSFER_PENDING : null, now, now, operatorId, null, null, null, null,
                null));
    }

    /** 송금 대기(수동 환불의 돈이 아직 안 나감) 줄이 있나 */
    public boolean transferPending(long applicationId) {
        return query.selectOne().from(R).where(R.applicationId.eq(applicationId), R.transferStatus.eq(TRANSFER_PENDING)).fetchFirst() != null;
    }

    public Optional<RefundRow> latest(long applicationId) {
        return Optional.ofNullable(query.select(ROW).from(R).where(R.applicationId.eq(applicationId)).orderBy(R.requestedAt.desc(), R.id.desc()).fetchFirst());
    }

    /** 건마다 가장 최근 줄(요청 시각, 같으면 큰 ID) — 더 늦은 줄이 없는 줄(예전 {@code DISTINCT ON} 과 같은 판정) */
    public Map<Long, RefundRow> latest(Collection<Long> applicationIds) {
        Map<Long, RefundRow> out = new HashMap<>();
        if (applicationIds.isEmpty()) {
            return out;
        }
        QBizRefundEntity later = new QBizRefundEntity("later");
        query.select(ROW).from(R).where(R.applicationId.in(applicationIds), JPAExpressions.selectOne().from(later)
                        .where(later.applicationId.eq(R.applicationId), later.requestedAt.gt(R.requestedAt).or(later.requestedAt.eq(R.requestedAt).and(later.id.gt(R.id))))
                        .notExists())
                .fetch().forEach(row -> out.put(row.applicationId(), row));
        return out;
    }

    public List<RefundRow> all(long applicationId) {
        return query.select(ROW).from(R).where(R.applicationId.eq(applicationId)).orderBy(R.requestedAt.asc(), R.id.asc()).fetch();
    }

    /** 열린(심사 대기) 줄을 잠근다 — 잠그는 문장(루트 표 id) 뒤 다시 읽는다 */
    public Optional<RefundRow> lockOpen(long applicationId) {
        Long id = query.select(R.id).from(R).where(R.applicationId.eq(applicationId), R.status.eq(BizRefundStatus.REQUESTED))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchFirst();
        return id == null ? Optional.empty() : Optional.ofNullable(query.select(ROW).from(R).where(R.id.eq(id)).fetchFirst());
    }

    /** 환불 줄이 있었나 — 긴급 내림 되돌리기의 보상(DOWNTIME)은 사업자 환불 요청을 막지 않아 빼고 센다 */
    public boolean exists(long applicationId) {
        return query.selectOne().from(R).where(R.applicationId.eq(applicationId), R.type.ne(BizRefundType.DOWNTIME)).fetchFirst() != null;
    }

    /** 이미 승인(돌려주기로 한) 금액 합 */
    public long approvedTotal(long applicationId) {
        Long n = query.select(Expressions.numberTemplate(Long.class, "sum({0})", R.approvedAmount)).from(R)
                .where(R.applicationId.eq(applicationId), R.status.eq(BizRefundStatus.APPROVED)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 심사 결정 — 열린 줄에서만 */
    public boolean decide(long id, BizRefundStatus to, Long approvedAmount, String decisionReason, String transferStatus, Instant completedAt, Long operatorId, Instant now) {
        return query.update(R).set(R.status, to).set(R.approvedAmount, approvedAmount).set(R.decisionReason, decisionReason).set(R.transferStatus, transferStatus)
                .set(R.completedAt, completedAt).set(R.decidedAt, now).set(R.decidedBy, operatorId)
                .where(R.id.eq(id), R.status.eq(BizRefundStatus.REQUESTED)).execute() == 1;
    }

    public void linkCancel(long id, long paymentCancelId) {
        query.update(R).set(R.paymentCancelId, paymentCancelId).where(R.id.eq(id)).execute();
    }

    /**
     * PG 취소가 끝났다(결제 모듈 onCanceled) — 그 취소에 걸린 줄(완료 시각은 처음 것을 둔다). 조건 없는 멱등 UPDATE 라 이긴 쪽을 가를 필요가 없어 UPDATE 뒤 다시 읽는다
     * (예전 {@code UPDATE … RETURNING} 과 같은 줄).
     */
    public Optional<RefundRow> completeByCancel(long paymentCancelId, Instant now) {
        query.update(R).set(R.completedAt, R.completedAt.coalesce(now)).where(R.paymentCancelId.eq(paymentCancelId)).execute();
        return Optional.ofNullable(query.select(ROW).from(R).where(R.paymentCancelId.eq(paymentCancelId)).fetchFirst());
    }

    /** 수동 송금 완료 표시 — 송금 대기에서만 */
    public boolean markTransferred(long id, long operatorId, String note, Instant now) {
        return query.update(R).set(R.transferStatus, TRANSFER_DONE).set(R.transferredAt, now).set(R.transferredBy, operatorId).set(R.transferNote, note)
                .set(R.completedAt, R.completedAt.coalesce(now)).where(R.id.eq(id), R.transferStatus.eq(TRANSFER_PENDING)).execute() == 1;
    }
}
