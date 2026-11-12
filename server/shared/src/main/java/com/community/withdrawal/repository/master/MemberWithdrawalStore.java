package com.community.withdrawal.repository.master;

import com.community.withdrawal.dto.WithdrawalChoices;
import com.community.withdrawal.entity.MemberWithdrawalEntity;
import com.community.withdrawal.entity.QMemberWithdrawalEntity;
import com.community.withdrawal.entity.WithdrawalSource;
import com.community.withdrawal.entity.WithdrawalStatus;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 탈퇴 신청 기록 표({@code member_withdrawals}, shared V132·V134·V148 — {@link MemberWithdrawalEntity}) — 신청·완료·취소와 자동 완료 배치가 쓴다. 개인정보는 없다(회원 번호·고른 것·금액·시각·운영 메모).
 * 관리자 목록·요약 읽기는 admin 의 {@code MemberWithdrawalAdminStore} 가 {@link #columns()}·{@link #row(Tuple)} 를 같이 쓴다. <b>트랜잭션은 부르는 서비스가 연다.</b>
 *
 * <p>넣기는 엔티티({@code persistAndFlush} — 예전 JDBC 처럼 그 자리에서 INSERT 가 나가 문장·잠금 차례가 같다), 상태 전이는 조건부 대량 UPDATE(진행 중일 때만 — 바뀐 줄 수로 안다)다.
 * 읽기는 모두 프로젝션이다 — 대량 UPDATE 는 영속성 컨텍스트의 엔티티를 고치지 않으므로, 같은 트랜잭션에서 넣거나 잠근 엔티티를 다시 읽으면 낡은 값이 나온다.
 */
@Repository
public class MemberWithdrawalStore extends IrobitRepositoryQDSL {

    private static final QMemberWithdrawalEntity W = QMemberWithdrawalEntity.memberWithdrawalEntity;

    /** {@link #row(Tuple)} 가 읽는 칸 차례 */
    private static final Expression<?>[] COLUMNS = {W.id, W.userId, W.status, W.source, W.priorStatus, W.reason, W.reasonNote, W.paidPointChoice, W.deliveredChoice,
            W.bizChoice, W.groupChoices, W.forfeitPaidPoints, W.forfeitRefundEstimate, W.forfeitBizAmount, W.forfeitConsentedAt, W.pendingSnapshot, W.pendingCount,
            W.expectedCompleteAt, W.rejoinUntil, W.hasPaymentHistory, W.progressNotifiedAt, W.requestedAt, W.completedAt, W.canceledAt, W.forceReasonKind, W.forceMemo,
            W.forceOperatorId, W.cancelChannel, W.cancelMemo, W.cancelOperatorId, W.forfeitEmoticonEarnings};

    /**
     * 한 줄 — {@code groupChoicesJson}·{@code pendingJson} 은 저장한 JSON 글 그대로.
     *
     * @param source           신청 경로(회원 본인 · 운영자 강제)
     * @param forceReasonKind  운영자 강제 탈퇴의 사유 종류(회원 신청이면 null) — {@code forceMemo}·{@code forceOperatorId} 도 같다
     * @param cancelChannel    운영자 대리 취소의 요청 경로(회원 본인 취소·취소 아님이면 null) — {@code cancelMemo}·{@code cancelOperatorId} 도 같다
     * @param forfeitEmoticonEarnings 로보티콘 수익 포기 동의 금액(V148 — 정산 정보 없이 수익이 남은 작가가 동의했을 때만, 동의 시각은 {@code forfeitConsentedAt})
     */
    public record Row(long id, long userId, WithdrawalStatus status, WithdrawalSource source, String priorStatus, String reason, String reasonNote, WithdrawalChoices choices,
                      String groupChoicesJson, Long forfeitPaidPoints, Long forfeitRefundEstimate, Long forfeitBizAmount, Instant forfeitConsentedAt, String pendingJson,
                      int pendingCount, Instant expectedCompleteAt, Instant rejoinUntil, Boolean hasPaymentHistory, Instant progressNotifiedAt, Instant requestedAt,
                      Instant completedAt, Instant canceledAt, String forceReasonKind, String forceMemo, Long forceOperatorId, String cancelChannel, String cancelMemo,
                      Long cancelOperatorId, Long forfeitEmoticonEarnings) {

        public boolean scheduled() {
            return status == WithdrawalStatus.SCHEDULED;
        }
    }

    /** 새 신청 줄 — {@code force} 는 운영자 강제 탈퇴일 때만(회원 신청이면 null) */
    public record NewRow(long id, long userId, WithdrawalStatus status, WithdrawalSource source, String priorStatus, String reason, String reasonNote, WithdrawalChoices choices,
                         String groupChoicesJson, Long forfeitPaidPoints, Long forfeitRefundEstimate, Long forfeitBizAmount, Instant forfeitConsentedAt, String pendingJson,
                         int pendingCount, Instant expectedCompleteAt, Instant rejoinUntil, Boolean hasPaymentHistory, Instant requestedAt, Force force,
                         Long forfeitEmoticonEarnings) {
    }

    /** 운영자 강제 탈퇴 정보 */
    public record Force(String reasonKind, String memo, long operatorId) {
    }

    /** 운영자 대리 취소 정보 */
    public record OperatorCancel(String channel, String memo, long operatorId) {
    }

    private final MemberWithdrawalRepository repository;

    public MemberWithdrawalStore(JPAQueryFactory query, MemberWithdrawalRepository repository) {
        super(query);
        this.repository = repository;
    }

    public void insert(NewRow r) {
        Force force = r.force();
        repository.persistAndFlush(MemberWithdrawalEntity.request(r.id(), r.userId(), r.status(), r.source(), r.priorStatus(), r.reason(), r.reasonNote(), r.choices(),
                r.groupChoicesJson(), r.forfeitPaidPoints(), r.forfeitRefundEstimate(), r.forfeitBizAmount(), r.forfeitConsentedAt(), r.pendingJson(), r.pendingCount(),
                r.expectedCompleteAt(), r.rejoinUntil(), r.hasPaymentHistory(), r.requestedAt(), force == null ? null : force.reasonKind(), force == null ? null : force.memo(),
                force == null ? null : force.operatorId(), r.forfeitEmoticonEarnings()));
    }

    public Optional<Row> find(long id) {
        return Optional.ofNullable(query.select(COLUMNS).from(W).where(W.id.eq(id)).fetchFirst()).map(MemberWithdrawalStore::row);
    }

    /** 그 회원의 진행 중 신청(회원당 하나) */
    public Optional<Row> findScheduled(long userId) {
        return Optional.ofNullable(query.select(COLUMNS).from(W).where(W.userId.eq(userId), W.status.eq(WithdrawalStatus.SCHEDULED)).fetchFirst())
                .map(MemberWithdrawalStore::row);
    }

    /** 그 회원의 가장 최근 신청(진행 중·완료·취소 무관) — 신청한 적 없으면 비어 있다 */
    public Optional<Row> findLatest(long userId) {
        return Optional.ofNullable(query.select(COLUMNS).from(W).where(W.userId.eq(userId)).orderBy(W.requestedAt.desc(), W.id.desc()).fetchFirst())
                .map(MemberWithdrawalStore::row);
    }

    /**
     * 진행 중 신청을 잠가 읽는다(배치·완료가 같은 줄을 두 번 처리하지 않게 — 신청 표만 고르는 조건 문장의 {@code PESSIMISTIC_WRITE} = {@code FOR NO KEY UPDATE}: 이 표를
     * FK 로 가리키는 표가 없어 {@code FOR UPDATE} 와 막는 범위가 같다. PostgreSQL 이 잠근 뒤 조건을 다시 본다).
     * 잠근 뒤 값은 프로젝션으로 다시 읽는다 — 같은 트랜잭션에서 넣은 엔티티가 컨텍스트에 있으면 엔티티 질의가 그 (낡을 수 있는) 인스턴스를 돌려주기 때문이다.
     */
    public Optional<Row> lockScheduled(long userId) {
        List<Long> locked = query.select(W.id).from(W).where(W.userId.eq(userId), W.status.eq(WithdrawalStatus.SCHEDULED)).orderBy(W.id.asc())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).fetch();
        Long id = locked.isEmpty() ? null
                : query.select(W.id).from(W).where(W.id.in(locked), W.userId.eq(userId), W.status.eq(WithdrawalStatus.SCHEDULED)).orderBy(W.id.asc()).fetchFirst();
        return id == null ? Optional.empty() : find(id);
    }

    /** 진행 중 신청 ID — 신청 ID 차례(배치의 덩어리 읽기, {@code afterId} 다음부터) */
    public List<Long> scheduledIds(long afterId, int limit) {
        return query.select(W.id).from(W).where(W.status.eq(WithdrawalStatus.SCHEDULED), W.id.gt(afterId)).orderBy(W.id.asc()).limit(limit).fetch();
    }

    /** 남은 일 스냅샷을 고친다(진행 중 신청만) */
    public void updatePending(long id, String pendingJson, int pendingCount, Instant expectedCompleteAt) {
        JPAUpdateClause update = query.update(W).set(W.pendingSnapshot, pendingJson).set(W.pendingCount, pendingCount);
        setOrNull(update, W.expectedCompleteAt, expectedCompleteAt);
        update.where(W.id.eq(id), W.status.eq(WithdrawalStatus.SCHEDULED)).execute();
    }

    /** 완료 — 진행 중이었을 때만 바뀐다. 남은 일 스냅샷은 마지막 모습 그대로 둔다(관리자 화면이 「기다렸던 일」을 보인다), 남은 수는 0. {@code forfeitBizAmount} 가 null 이면 그 칸은 그대로 */
    public boolean complete(long id, Instant now, Instant rejoinUntil, boolean hasPaymentHistory, Long forfeitBizAmount) {
        JPAUpdateClause update = query.update(W).set(W.status, WithdrawalStatus.COMPLETED).set(W.completedAt, now).set(W.hasPaymentHistory, hasPaymentHistory)
                .set(W.pendingCount, 0);
        setOrNull(update, W.rejoinUntil, rejoinUntil);
        if (forfeitBizAmount != null) {
            update.set(W.forfeitBizAmount, forfeitBizAmount);
        }
        return update.where(W.id.eq(id), W.status.eq(WithdrawalStatus.SCHEDULED)).execute() > 0;
    }

    /** 회원 본인 취소 — 진행 중이었을 때만 바뀐다. 스냅샷은 그대로, 남은 수는 0 */
    public boolean cancel(long id, Instant now) {
        return query.update(W).set(W.status, WithdrawalStatus.CANCELED).set(W.canceledAt, now).set(W.pendingCount, 0)
                .where(W.id.eq(id), W.status.eq(WithdrawalStatus.SCHEDULED)).execute() > 0;
    }

    /** 운영자 대리 취소 — 회원 본인 취소와 같되 요청 경로·메모·운영자를 남긴다 */
    public boolean cancelByOperator(long id, Instant now, OperatorCancel by) {
        return query.update(W).set(W.status, WithdrawalStatus.CANCELED).set(W.canceledAt, now).set(W.pendingCount, 0).set(W.cancelChannel, by.channel())
                .set(W.cancelMemo, by.memo()).set(W.cancelOperatorId, by.operatorId())
                .where(W.id.eq(id), W.status.eq(WithdrawalStatus.SCHEDULED)).execute() > 0;
    }

    public void markProgressNotified(long id, Instant now) {
        query.update(W).set(W.progressNotifiedAt, now).where(W.id.eq(id)).execute();
    }

    /** {@link #row(Tuple)} 가 읽는 칸(관리자 목록이 닉네임 칸을 덧붙여 쓴다) */
    public static Expression<?>[] columns() {
        return COLUMNS.clone();
    }

    /** {@link #columns()} 를 고른 줄 → {@link Row} */
    public static Row row(Tuple t) {
        return new Row(t.get(W.id), t.get(W.userId), t.get(W.status), t.get(W.source), t.get(W.priorStatus), t.get(W.reason), t.get(W.reasonNote),
                new WithdrawalChoices(t.get(W.paidPointChoice), t.get(W.deliveredChoice), t.get(W.bizChoice)), t.get(W.groupChoices), t.get(W.forfeitPaidPoints),
                t.get(W.forfeitRefundEstimate), t.get(W.forfeitBizAmount), t.get(W.forfeitConsentedAt), t.get(W.pendingSnapshot), t.get(W.pendingCount),
                t.get(W.expectedCompleteAt), t.get(W.rejoinUntil), t.get(W.hasPaymentHistory), t.get(W.progressNotifiedAt), t.get(W.requestedAt), t.get(W.completedAt),
                t.get(W.canceledAt), t.get(W.forceReasonKind), t.get(W.forceMemo), t.get(W.forceOperatorId), t.get(W.cancelChannel), t.get(W.cancelMemo),
                t.get(W.cancelOperatorId), t.get(W.forfeitEmoticonEarnings));
    }

    private static <T extends Comparable<?>> void setOrNull(JPAUpdateClause update, DateTimePath<T> path, T value) {
        if (value == null) {
            update.setNull(path);
        } else {
            update.set(path, value);
        }
    }
}
