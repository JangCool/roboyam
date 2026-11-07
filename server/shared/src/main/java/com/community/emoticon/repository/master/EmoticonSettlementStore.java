package com.community.emoticon.repository.master;

import com.community.emoticon.entity.EmoticonSettlementEventEntity;
import com.community.emoticon.entity.EmoticonSettlementStatus;
import com.community.emoticon.entity.QEmoticonPurchaseEntity;
import com.community.emoticon.entity.QEmoticonSettlementEntity;
import com.community.emoticon.entity.QEmoticonSettlementEventEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 작가 월 정산({@code emoticon_settlements} · {@code emoticon_settlement_events} · 구매 기록의 정산 달 칸, V145). 예정 줄의 금액은 저장하지 않는다 — 계산은
 * {@code EmoticonSettlementCalculator}. 확정 뒤 금액·지급 기록 고치기·지우기는 DB 트리거가 막는다. 트랜잭션은 부르는 서비스가 연다.
 *
 * <p>QueryDSL 이다({@link EmoticonStoreSupport}) — 상태 전이는 조건부 UPDATE({@code execute() == 1}), 처리 기록은 {@code persist}, 확정의 구매 행 잠금은 구매 표만 고르는
 * {@code PESSIMISTIC_WRITE}(ID 차례). Native SQL 로 남긴 것과 이유: 작가 단위 줄 세우기({@code pg_advisory_xact_lock} — 정산 줄이 아직 없을 수 있는 「작가」 단위),
 * 예정 줄 만들기({@code ON CONFLICT (creator_id, sales_month) DO NOTHING} — 있으면 그대로), 가장 이른 정산 달({@code date_trunc('month', … AT TIME ZONE 'Asia/Seoul')} — 판매 달 묶기).
 */
@Repository
public class EmoticonSettlementStore extends EmoticonStoreSupport {

    /** 확정 때 굳힌 값 */
    public record Frozen(int salesCount, long salesAmount, int refundCount, long refundAmount, long vat, long base, int feeRatePercent, long fee, long share,
                         long carriedIn, long withholding, long payout, long carriedOut, String payeeType, LocalDate payDate, Instant closesAt) {
    }

    /** 정산 줄 하나 — {@code frozen} 은 확정 전이면 null. 처리한 운영자 이름은 지워졌으면 null */
    public record SettlementRow(long creatorId, LocalDate month, EmoticonSettlementStatus status, Frozen frozen, Instant confirmedAt, String confirmedByName,
                                String holdReason, Instant heldAt, String heldByName, Instant paidAt, String paidByName, LocalDate transferDate, String paidMemo,
                                Instant createdAt) {

        /**
         * 굳힌 줄 다음 달로 이어지는 금액 — 굳힌 넘긴 금액 그대로, 다만 포기(WAIVED, V148)한 줄의 양수는 포기한 금액이라 이어지지 않는다(음수 = 받을 돈은 그대로 이어진다).
         * 굳히지 않은 줄이면 0.
         */
        public long carryForward() {
            if (frozen == null) {
                return 0;
            }
            return status == EmoticonSettlementStatus.WAIVED ? Math.min(0, frozen.carriedOut()) : frozen.carriedOut();
        }

        /** 포기한 금액(포기 줄의 양수 넘긴 금액, 아니면 0) */
        public long waivedAmount() {
            return status == EmoticonSettlementStatus.WAIVED && frozen != null ? Math.max(0, frozen.carriedOut()) : 0;
        }
    }

    /** 정산 입력이 되는 구매 한 줄(판매 · 환불) — 정산 달 칸은 이미 들어간 달(없으면 null) */
    public record ActivityRow(long purchaseId, long creatorId, long packId, String packName, int amount, Instant paidAt, Instant refundUntil, Instant refundedAt,
                              LocalDate settlementMonth, LocalDate refundSettlementMonth) {
    }

    /**
     * 처리 기록 한 줄 — {@code amount} 는 포기(WAIVED, V148 — 포기한 금액)·수동 지급(MANUAL_PAID, V149 — 그 줄의 지급액) 기록만, {@code consentedAt} 은 포기 기록만(회원 동의 시각,
     * 운영자 포기 처리는 없음)
     */
    public record EventRow(String action, Instant at, String byName, String note, Long amount, Instant consentedAt) {
    }

    private static final QEmoticonSettlementEntity S = QEmoticonSettlementEntity.emoticonSettlementEntity;
    private static final QEmoticonSettlementEntity S2 = new QEmoticonSettlementEntity("s2");
    private static final QEmoticonSettlementEventEntity E = QEmoticonSettlementEventEntity.emoticonSettlementEventEntity;
    private static final QEmoticonPurchaseEntity P = QEmoticonPurchaseEntity.emoticonPurchaseEntity;
    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QOperatorEntity OC = new QOperatorEntity("oc");
    private static final QOperatorEntity OH = new QOperatorEntity("oh");
    private static final QOperatorEntity OP = new QOperatorEntity("op");
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;

    public EmoticonSettlementStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    /**
     * 한 작가의 정산 처리를 줄 세운다(확정 · 보류 · 지급 기록 · 계좌 보기 — 트랜잭션이 끝나면 풀린다).
     * Native SQL 인 이유: {@code pg_advisory_xact_lock} — 정산 줄이 아직 없을 수 있는 「작가」 단위라 잠글 행이 없다.
     */
    public void lockCreator(long creatorId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended('emoticon-settlement:' || ?::text, 0))", rs -> {
        }, creatorId);
    }

    /** 그 가운데 탈퇴한 작가(회원 {@code status = 'WITHDRAWN'} — 파기 배치 R__batch_41 과 같은 판정) — 최소 지급액 없이 지급한다(후속 결정 6) */
    public Set<Long> withdrawn(Collection<Long> creatorIds) {
        if (creatorIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(query.select(U.id).from(U).where(U.status.eq(UserStatus.WITHDRAWN), U.id.in(creatorIds)).fetch());
    }

    // ── 정산 줄 ─────────────────────────────────────────────────────────────

    private JPAQuery<Tuple> rows() {
        return query.select(S.creatorId, S.salesMonth, S.status, S.salesCount, S.salesAmount, S.refundCount, S.refundAmount, S.vatAmount, S.baseAmount, S.feeRatePercent,
                        S.feeAmount, S.shareAmount, S.carriedIn, S.withholdingAmount, S.payoutAmount, S.carriedOut, S.payeeType, S.payDate, S.closesAt, S.confirmedAt,
                        OC.operatorName, S.holdReason, S.heldAt, OH.operatorName, S.paidAt, OP.operatorName, S.transferDate, S.paidMemo, S.createdAt)
                .from(S).leftJoin(OC).on(OC.id.eq(S.confirmedBy)).leftJoin(OH).on(OH.id.eq(S.heldBy)).leftJoin(OP).on(OP.id.eq(S.paidBy));
    }

    public Optional<SettlementRow> find(long creatorId, LocalDate month) {
        return Optional.ofNullable(rows().where(S.creatorId.eq(creatorId), S.salesMonth.eq(month)).fetchFirst()).map(EmoticonSettlementStore::rowOf);
    }

    /** 한 작가의 모든 줄(오래된 달 먼저) */
    public List<SettlementRow> byCreator(long creatorId) {
        return rows().where(S.creatorId.eq(creatorId)).orderBy(S.salesMonth.asc()).fetch().stream().map(EmoticonSettlementStore::rowOf).collect(Collectors.toList());
    }

    /**
     * 작가마다 {@code month} 앞의 마지막 굳힌 줄(이월의 시작) — 판매 달이 그 작가의 {@code month} 앞 굳힌 줄 가운데 가장 늦은 줄(작가 × 달 유일 — 예전 {@code DISTINCT ON} 과 같은 줄,
     * 작가 ID 차례).
     */
    public Map<Long, SettlementRow> latestFrozenBefore(LocalDate month) {
        Map<Long, SettlementRow> out = new LinkedHashMap<>();
        for (Tuple t : rows().where(S.salesMonth.lt(month), S.confirmedAt.isNotNull(),
                S.salesMonth.eq(JPAExpressions.select(S2.salesMonth.max()).from(S2).where(S2.creatorId.eq(S.creatorId), S2.salesMonth.lt(month), S2.confirmedAt.isNotNull())))
                .orderBy(S.creatorId.asc()).fetch()) {
            SettlementRow r = rowOf(t);
            out.put(r.creatorId(), r);
        }
        return out;
    }

    /** {@code month} 까지의 굳히지 않은 줄 + {@code month} 의 줄(굳혔어도) — 오래된 달 먼저 */
    public List<SettlementRow> openUpTo(LocalDate month) {
        return rows().where(S.salesMonth.loe(month), S.confirmedAt.isNull().or(S.salesMonth.eq(month))).orderBy(S.creatorId.asc(), S.salesMonth.asc()).fetch().stream()
                .map(EmoticonSettlementStore::rowOf).collect(Collectors.toList());
    }

    /**
     * 가장 이른 정산 달(작가 팩 판매가 처음 결제된 달 · 가장 이른 줄) — 없으면 null.
     * Native SQL 인 이유: 결제 시각을 판매 달로 묶는 {@code date_trunc('month', … AT TIME ZONE 'Asia/Seoul')::date} — 표의 CHECK 와 같은 식이다.
     */
    public LocalDate firstMonth() {
        return jdbc.queryForObject("SELECT least((SELECT date_trunc('month', min(paid_at) AT TIME ZONE 'Asia/Seoul')::date FROM emoticon_purchases "
                + "WHERE paid_at IS NOT NULL AND creator_id IS NOT NULL), (SELECT min(sales_month) FROM emoticon_settlements))", LocalDate.class);
    }

    /**
     * 굳히지 않은(예정) 줄을 만든다 — 이미 있으면 그대로(보류 · 계좌 보기처럼 기록이 생길 때).
     * Native SQL 인 이유: {@code ON CONFLICT (creator_id, sales_month) DO NOTHING} — 있으면 건드리지 않는 넣기.
     */
    public void insertIfAbsent(long creatorId, LocalDate month, Instant now) {
        jdbc.update("INSERT INTO emoticon_settlements (creator_id, sales_month, status, created_at, updated_at) VALUES (?, ?, 'ESTIMATED', ?, ?) "
                + "ON CONFLICT (creator_id, sales_month) DO NOTHING", creatorId, Date.valueOf(month), Timestamp.from(now), Timestamp.from(now));
    }

    /** 굳힌 금액 칸 — 확정 · 포기 · 수동 지급이 같이 쓴다(원천징수·지급액·넘긴 금액은 부르는 쪽이 정한다) */
    private JPAUpdateClause frozen(Frozen f) {
        return query.update(S).set(S.salesCount, f.salesCount()).set(S.salesAmount, f.salesAmount()).set(S.refundCount, f.refundCount())
                .set(S.refundAmount, f.refundAmount()).set(S.vatAmount, f.vat()).set(S.baseAmount, f.base()).set(S.feeRatePercent, f.feeRatePercent())
                .set(S.feeAmount, f.fee()).set(S.shareAmount, f.share()).set(S.carriedIn, f.carriedIn()).set(S.payeeType, f.payeeType()).set(S.payDate, f.payDate())
                .set(S.closesAt, f.closesAt());
    }

    private static BooleanExpression row(long creatorId, LocalDate month) {
        return S.creatorId.eq(creatorId).and(S.salesMonth.eq(month));
    }

    /** 확정 — 금액을 굳힌다(예정 줄이 있으면 그 줄을, 없으면 새 줄). 예정이었을 때만 */
    public boolean confirm(long creatorId, LocalDate month, Frozen f, long operatorId, Instant now) {
        insertIfAbsent(creatorId, month, now);
        return frozen(f).set(S.status, EmoticonSettlementStatus.CONFIRMED).set(S.withholdingAmount, f.withholding()).set(S.payoutAmount, f.payout())
                .set(S.carriedOut, f.carriedOut()).set(S.confirmedAt, now).set(S.confirmedBy, operatorId).set(S.updatedAt, now)
                .where(row(creatorId, month), S.status.eq(EmoticonSettlementStatus.ESTIMATED)).execute() == 1;
    }

    /** 확정한 줄에 들어간 판매 · 환불에 정산 달을 찍는다(판매 먼저 — 환불 칸은 판매 칸이 있어야 한다) */
    public void markPurchases(long creatorId, LocalDate month, Collection<Long> saleIds, Collection<Long> refundIds) {
        if (!saleIds.isEmpty()) {
            query.update(P).set(P.settlementMonth, month).where(P.creatorId.eq(creatorId), P.settlementMonth.isNull(), P.id.in(saleIds)).execute();
        }
        if (!refundIds.isEmpty()) {
            query.update(P).set(P.refundSettlementMonth, month).where(P.creatorId.eq(creatorId), P.refundSettlementMonth.isNull(), P.id.in(refundIds)).execute();
        }
    }

    /**
     * 포기(V148) — 굳히지 않은 예정·보류 줄만. 지급액·원천징수 0 으로 굳히고(넘긴 금액 = 작가 몫 + 넘어온 금액 — 부르는 쪽이 그렇게 만든 {@code f}), 보류 칸을 비운다.
     * 줄이 없으면 만든다. {@code operatorId} 는 운영자 포기 처리일 때만(회원 동의로 탈퇴 때 포기하면 null — 시스템).
     */
    public boolean waive(long creatorId, LocalDate month, Frozen f, Long operatorId, Instant now) {
        if (f.payout() != 0 || f.withholding() != 0 || f.carriedOut() != f.share() + f.carriedIn()) {
            throw new IllegalArgumentException("포기한 줄은 지급액·원천징수가 0 이고 넘긴 금액 = 작가 몫 + 넘어온 금액이어야 한다");
        }
        insertIfAbsent(creatorId, month, now);
        return frozen(f).set(S.status, EmoticonSettlementStatus.WAIVED).set(S.withholdingAmount, 0L).set(S.payoutAmount, 0L).set(S.carriedOut, f.carriedOut())
                .set(S.confirmedAt, now).set(S.confirmedBy, operatorId).setNull(S.holdReason).setNull(S.heldAt).setNull(S.heldBy).set(S.updatedAt, now)
                .where(row(creatorId, month), S.status.in(EmoticonSettlementStatus.ESTIMATED, EmoticonSettlementStatus.HELD), S.confirmedAt.isNull()).execute() == 1;
    }

    /** 시스템 보류(작가 탈퇴 — 정산 정보 없음, V148) — 예정(굳히지 않은) 줄만, 줄이 없으면 만든다. 보류한 운영자 없음 */
    public boolean holdBySystem(long creatorId, LocalDate month, String reason, Instant now) {
        insertIfAbsent(creatorId, month, now);
        return query.update(S).set(S.status, EmoticonSettlementStatus.HELD).set(S.holdReason, reason).set(S.heldAt, now).setNull(S.heldBy).set(S.updatedAt, now)
                .where(row(creatorId, month), S.status.eq(EmoticonSettlementStatus.ESTIMATED), S.confirmedAt.isNull()).execute() == 1;
    }

    /** 보류 — 예정·확정일 때만 */
    public boolean hold(long creatorId, LocalDate month, String reason, long operatorId, Instant now) {
        return query.update(S).set(S.status, EmoticonSettlementStatus.HELD).set(S.holdReason, reason).set(S.heldAt, now).set(S.heldBy, operatorId).set(S.updatedAt, now)
                .where(row(creatorId, month), S.status.in(EmoticonSettlementStatus.ESTIMATED, EmoticonSettlementStatus.CONFIRMED)).execute() == 1;
    }

    /**
     * 보류 풀기 — 보류 전 상태로(굳힌 줄은 확정, 아니면 예정). 한 줄(작가 × 달 키)이라 굳힌 줄 · 굳히지 않은 줄 두 조건부 UPDATE 중 많아야 하나만 바뀐다
     * (예전 {@code CASE WHEN confirmed_at IS NULL …} 한 문장과 같다).
     */
    public boolean unhold(long creatorId, LocalDate month, Instant now) {
        long estimated = query.update(S).set(S.status, EmoticonSettlementStatus.ESTIMATED).setNull(S.holdReason).setNull(S.heldAt).setNull(S.heldBy).set(S.updatedAt, now)
                .where(row(creatorId, month), S.status.eq(EmoticonSettlementStatus.HELD), S.confirmedAt.isNull()).execute();
        long confirmed = query.update(S).set(S.status, EmoticonSettlementStatus.CONFIRMED).setNull(S.holdReason).setNull(S.heldAt).setNull(S.heldBy).set(S.updatedAt, now)
                .where(row(creatorId, month), S.status.eq(EmoticonSettlementStatus.HELD), S.confirmedAt.isNotNull()).execute();
        return estimated + confirmed == 1;
    }

    /** 지급 완료 기록 — 확정일 때만 */
    public boolean markPaid(long creatorId, LocalDate month, LocalDate transferDate, String memo, long operatorId, Instant now) {
        return query.update(S).set(S.status, EmoticonSettlementStatus.PAID).set(S.paidAt, now).set(S.paidBy, operatorId).set(S.transferDate, transferDate)
                .set(S.paidMemo, memo).set(S.updatedAt, now).where(row(creatorId, month), S.status.eq(EmoticonSettlementStatus.CONFIRMED)).execute() == 1;
    }

    /**
     * 수동 지급 기록(V149 — 정산 정보 없이 탈퇴한 작가) — 굳히지 않은 예정·보류 줄만. 금액을 굳히고({@code f} — 탈퇴 작가 규칙 · 개인), 보류 칸을 비운다. 지급액이 있으면 곧바로 지급 완료
     * (확정 시각 = 지급 기록 시각 · 이체일 · 메모), 0 이면 확정(음수 이월 — 지급 기록 없음). 줄이 없으면 만든다.
     */
    public boolean manualPay(long creatorId, LocalDate month, Frozen f, LocalDate transferDate, String memo, long operatorId, Instant now) {
        boolean pays = f.payout() > 0;
        insertIfAbsent(creatorId, month, now);
        JPAUpdateClause update = frozen(f).set(S.status, pays ? EmoticonSettlementStatus.PAID : EmoticonSettlementStatus.CONFIRMED).set(S.withholdingAmount, f.withholding())
                .set(S.payoutAmount, f.payout()).set(S.carriedOut, f.carriedOut()).set(S.confirmedAt, now).set(S.confirmedBy, operatorId).setNull(S.holdReason)
                .setNull(S.heldAt).setNull(S.heldBy).set(S.updatedAt, now);
        if (pays) {
            update.set(S.paidAt, now).set(S.paidBy, operatorId).set(S.transferDate, transferDate).set(S.paidMemo, memo);
        } else {
            update.setNull(S.paidAt).setNull(S.paidBy).setNull(S.transferDate).setNull(S.paidMemo);
        }
        return update.where(row(creatorId, month), S.status.in(EmoticonSettlementStatus.ESTIMATED, EmoticonSettlementStatus.HELD), S.confirmedAt.isNull()).execute() == 1;
    }

    // ── 처리 기록 ───────────────────────────────────────────────────────────

    /** 수동 지급 기록(V149) — 사유 · 그 줄의 지급액(0 이상) */
    public void insertManualPaidEvent(long id, long creatorId, LocalDate month, Instant at, long operatorId, String note, long amount) {
        insert(EmoticonSettlementEventEntity.create(id, creatorId, month, "MANUAL_PAID", at, operatorId, note, amount, null));
    }

    public void insertEvent(long id, long creatorId, LocalDate month, String action, Instant at, Long operatorId, String note) {
        insert(EmoticonSettlementEventEntity.create(id, creatorId, month, action, at, operatorId, note, null, null));
    }

    /** 포기 기록(V148) — 그 줄에서 포기한 금액 · 회원 동의 시각(운영자 포기 처리면 null) · 사유 */
    public void insertWaivedEvent(long id, long creatorId, LocalDate month, Instant at, Long operatorId, String note, long amount, Instant consentedAt) {
        insert(EmoticonSettlementEventEntity.create(id, creatorId, month, "WAIVED", at, operatorId, note, amount, consentedAt));
    }

    /** 처리 기록(오래된 것 먼저) */
    public List<EventRow> events(long creatorId, LocalDate month) {
        return query.select(E.action, E.at, O.operatorName, E.note, E.amount, E.consentedAt).from(E).leftJoin(O).on(O.id.eq(E.operatorId))
                .where(E.creatorId.eq(creatorId), E.salesMonth.eq(month)).orderBy(E.at.asc(), E.id.asc()).fetch().stream()
                .map(t -> new EventRow(t.get(E.action), t.get(E.at), t.get(O.operatorName), t.get(E.note), t.get(E.amount), t.get(E.consentedAt)))
                .collect(Collectors.toList());
    }

    // ── 정산 입력(구매 기록) ────────────────────────────────────────────────

    private JPAQuery<Tuple> activity() {
        return query.select(P.id, P.creatorId, P.packId, P.packName, P.amount, P.paidAt, P.refundUntil, P.refundedAt, P.settlementMonth, P.refundSettlementMonth).from(P);
    }

    /** 아직 정산에 들어가지 않은 판매 · 환불(V145 부분 색인 두 개와 같은 조건) */
    private static BooleanExpression unsettledWhere() {
        return P.paidAt.isNotNull().and(P.settlementMonth.isNull()).and(P.creatorId.isNotNull())
                .or(P.refundedAt.isNotNull().and(P.refundSettlementMonth.isNull()).and(P.creatorId.isNotNull()));
    }

    /** 아직 정산에 들어가지 않은 판매·환불 — 모든 작가 */
    public List<ActivityRow> unsettled() {
        return activity().where(unsettledWhere()).fetch().stream().map(EmoticonSettlementStore::activityOf).collect(Collectors.toList());
    }

    /**
     * 한 작가의 아직 정산에 들어가지 않은 판매·환불 — {@code lock} 이면 그 구매 행을 ID 차례로 잠근다(확정 — 같은 구매의 환불이 확정 계산과 엇갈리지 않게).
     * 잠금은 구매 표만 고르는 ID 문장, 칸은 잠근 뒤 읽는다(ID 차례).
     */
    public List<ActivityRow> unsettled(long creatorId, boolean lock) {
        if (!lock) {
            return activity().where(P.creatorId.eq(creatorId), unsettledWhere()).fetch().stream().map(EmoticonSettlementStore::activityOf).collect(Collectors.toList());
        }
        List<Long> ids = query.select(P.id).from(P).where(P.creatorId.eq(creatorId), unsettledWhere()).orderBy(P.id.asc()).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetch();
        if (ids.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return activity().where(P.id.in(ids)).orderBy(P.id.asc()).fetch().stream().map(EmoticonSettlementStore::activityOf).collect(Collectors.toList());
    }

    /** 굳힌 줄에 들어간 판매·환불(팩별 내역) */
    public List<ActivityRow> settled(long creatorId, LocalDate month) {
        return activity().where(P.creatorId.eq(creatorId), P.settlementMonth.eq(month).or(P.refundSettlementMonth.eq(month))).fetch().stream()
                .map(EmoticonSettlementStore::activityOf).collect(Collectors.toList());
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private static SettlementRow rowOf(Tuple t) {
        Frozen frozen = null;
        if (t.get(S.confirmedAt) != null) {
            frozen = new Frozen(i(t.get(S.salesCount)), n(t.get(S.salesAmount)), i(t.get(S.refundCount)), n(t.get(S.refundAmount)), n(t.get(S.vatAmount)),
                    n(t.get(S.baseAmount)), i(t.get(S.feeRatePercent)), n(t.get(S.feeAmount)), n(t.get(S.shareAmount)), n(t.get(S.carriedIn)),
                    n(t.get(S.withholdingAmount)), n(t.get(S.payoutAmount)), n(t.get(S.carriedOut)), t.get(S.payeeType), t.get(S.payDate), t.get(S.closesAt));
        }
        return new SettlementRow(t.get(S.creatorId), t.get(S.salesMonth), t.get(S.status), frozen, t.get(S.confirmedAt), t.get(OC.operatorName), t.get(S.holdReason),
                t.get(S.heldAt), t.get(OH.operatorName), t.get(S.paidAt), t.get(OP.operatorName), t.get(S.transferDate), t.get(S.paidMemo), t.get(S.createdAt));
    }

    private static ActivityRow activityOf(Tuple t) {
        return new ActivityRow(t.get(P.id), t.get(P.creatorId), t.get(P.packId), t.get(P.packName), t.get(P.amount), t.get(P.paidAt), t.get(P.refundUntil),
                t.get(P.refundedAt), t.get(P.settlementMonth), t.get(P.refundSettlementMonth));
    }

    private static int i(Integer value) {
        return value == null ? 0 : value;
    }
}
