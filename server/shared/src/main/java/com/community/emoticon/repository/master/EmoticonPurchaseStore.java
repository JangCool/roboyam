package com.community.emoticon.repository.master;

import com.community.emoticon.entity.EmoticonPurchaseEntity;
import com.community.emoticon.entity.EmoticonPurchaseStatus;
import com.community.emoticon.entity.EmoticonRefundKind;
import com.community.emoticon.entity.QEmoticonPurchaseEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.payment.entity.QPaymentOrderEntity;
import com.community.user.entity.QUserEntity;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 유료 팩 구매 기록({@code emoticon_purchases}, V144) — 결제 준비 한 번이 한 줄(결제 주문의 {@code order_ref_id}). 지우기·사본 고치기·되돌리기는 DB 트리거가 막는다.
 * 트랜잭션은 부르는 서비스가 연다.
 *
 * <p>QueryDSL 이다({@link EmoticonStoreSupport}) — 결제 주문({@code payment_orders})·회원({@code users})·운영자는 엔티티로 잇는다. 구매 행 잠금은 구매 표만 고르는 {@code PESSIMISTIC_WRITE}.
 * Native SQL 로 남긴 것과 이유: 같은 회원·팩의 결제 준비 줄 세우기({@code pg_advisory_xact_lock} — 아직 구매 줄이 없는 「회원 × 팩」 단위라 잠글 행이 없다).
 */
@Repository
public class EmoticonPurchaseStore extends EmoticonStoreSupport {

    /** 구매 한 줄 */
    public record PurchaseRow(long id, long userId, long packId, Long creatorId, String packName, int amount, EmoticonPurchaseStatus status,
                              Instant refundNoticeAgreedAt, Instant expiresAt, Instant createdAt, Instant closedAt, Long paymentOrderId, Instant paidAt,
                              Instant refundUntil, Instant usedAt, Instant refundedAt, EmoticonRefundKind refundKind, String refundReason, Long refundedBy,
                              Long refundCancelId, Instant refundCompletedAt) {
    }

    /** 새 구매(결제 대기) */
    public record NewPurchase(long id, long userId, long packId, Long creatorId, String packName, int amount, Instant agreedAt, Instant expiresAt, Instant now) {
    }

    /** 목록 줄 — 구매 + 결제 주문의 결제 ID·영수증 + 환불한 운영자 이름(지워진 운영자는 null) + 이 판매가 들어간 정산 달(S4 — 확정 전이면 null) */
    public record ListedRow(PurchaseRow purchase, String paymentId, String receiptUrl, String refundedByName, LocalDate settlementMonth) {
    }

    /** 관리자 판매 내역 조건 — null 이면 그 거르기 없음(결제 완료 계열만 본다) */
    public record SalesQuery(Instant from, Instant to, Long packId, EmoticonPurchaseStatus status, int offset, int limit) {
    }

    /** 팩 하나의 판매 — 보유 중(환불 뺀) 수·금액 · 결제 완료 계열 전체 수 */
    public record PackSales(int paidCount, long paidAmount, int anyCount) {
    }

    private static final QEmoticonPurchaseEntity P = QEmoticonPurchaseEntity.emoticonPurchaseEntity;
    private static final QPaymentOrderEntity O = QPaymentOrderEntity.paymentOrderEntity;
    private static final QOperatorEntity OP = QOperatorEntity.operatorEntity;
    private static final QUserEntity U = QUserEntity.userEntity;
    private static final Expression<?>[] COLUMNS = {P.id, P.userId, P.packId, P.creatorId, P.packName, P.amount, P.status, P.refundNoticeAgreedAt, P.expiresAt,
            P.createdAt, P.closedAt, P.paymentOrderId, P.paidAt, P.refundUntil, P.usedAt, P.refundedAt, P.refundKind, P.refundReason, P.refundedBy, P.refundCancelId,
            P.refundCompletedAt};

    public EmoticonPurchaseStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    public void insert(NewPurchase n) {
        insert(EmoticonPurchaseEntity.pending(n.id(), n.userId(), n.packId(), n.creatorId(), n.packName(), n.amount(), n.agreedAt(), n.expiresAt(), n.now()));
    }

    public Optional<PurchaseRow> find(long id) {
        return Optional.ofNullable(query.select(COLUMNS).from(P).where(P.id.eq(id)).fetchFirst()).map(EmoticonPurchaseStore::rowOf);
    }

    /** 바꾸기 전에 — 구매 행을 잠근다(결제 확정 · 환불 · 사용함 기록이 같은 구매에서 줄을 선다). 잠금은 구매 표만 고르는 문장, 칸은 그 뒤에 읽는다 */
    public Optional<PurchaseRow> lock(long id) {
        if (query.select(P.id).from(P).where(P.id.eq(id)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchFirst() == null) {
            return Optional.empty();
        }
        return find(id);
    }

    /**
     * 같은 회원·팩의 결제 준비를 줄 세운다(트랜잭션이 끝나면 풀린다 — 결제 확정은 이 잠금을 잡지 않는다).
     * Native SQL 인 이유: {@code pg_advisory_xact_lock} — 결제 대기 줄을 만들기 전이라 잠글 행이 없는 「회원 × 팩」 단위다.
     */
    public void lockMemberPack(long userId, long packId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended('emoticon-purchase:' || ?::text || ':' || ?::text, 0))", rs -> {
        }, userId, packId);
    }

    /** 회원 이메일(포트원 구매자 칸 — 없으면 빈 글자) */
    public String email(long userId) {
        String email = query.select(U.email).from(U).where(U.id.eq(userId)).fetchFirst();
        return email == null ? "" : email;
    }

    /** 그 회원·팩의 결제 대기 구매 */
    public List<Long> pendingIds(long userId, long packId) {
        return query.select(P.id).from(P).where(P.userId.eq(userId), P.packId.eq(packId), P.status.eq(EmoticonPurchaseStatus.PENDING)).fetch();
    }

    /** 결제 대기를 닫는다(새 준비가 대신했다 · 결제가 거절됐다) — 결제 대기였을 때만 */
    public boolean close(long id, Instant now) {
        return query.update(P).set(P.status, EmoticonPurchaseStatus.CLOSED).set(P.closedAt, now).where(P.id.eq(id), P.status.eq(EmoticonPurchaseStatus.PENDING))
                .execute() == 1;
    }

    /** 결제 완료 — 결제 대기였을 때만(보유는 부르는 쪽이 받기 줄에) */
    public boolean markPaid(long id, long paymentOrderId, Instant paidAt, Instant refundUntil) {
        return query.update(P).set(P.status, EmoticonPurchaseStatus.PAID).set(P.paymentOrderId, paymentOrderId).set(P.paidAt, paidAt).set(P.refundUntil, refundUntil)
                .where(P.id.eq(id), P.status.eq(EmoticonPurchaseStatus.PENDING)).execute() == 1;
    }

    /**
     * 「사용함」 — 그 회원이 이 팩들을 산(보유 중인) 구매에 처음 쓴 시각을 찍는다(이미 찍혔으면 그대로). 글·댓글 저장 트랜잭션 안에서 부른다. 찍은 줄 수.
     */
    public int markUsed(long userId, Collection<Long> packIds, Instant now) {
        if (packIds.isEmpty()) {
            return 0;
        }
        return (int) query.update(P).set(P.usedAt, now)
                .where(P.userId.eq(userId), P.status.eq(EmoticonPurchaseStatus.PAID), P.usedAt.isNull(), P.packId.in(packIds)).execute();
    }

    /** 환불 — 보유 중일 때만(취소 대기열 줄 · 종류 · 사유 · 운영자) */
    public boolean markRefunded(long id, EmoticonRefundKind kind, String reason, Long operatorId, long cancelId, Instant now) {
        return query.update(P).set(P.status, EmoticonPurchaseStatus.REFUNDED).set(P.refundedAt, now).set(P.refundKind, kind).set(P.refundReason, reason)
                .set(P.refundedBy, operatorId).set(P.refundCancelId, cancelId).where(P.id.eq(id), P.status.eq(EmoticonPurchaseStatus.PAID)).execute() == 1;
    }

    /** PG 취소가 끝났다(취소 대기열 줄로 찾는다 — 처음 한 번만) */
    public int completeRefund(long cancelId, Instant now) {
        return (int) query.update(P).set(P.refundCompletedAt, now).where(P.refundCancelId.eq(cancelId), P.refundCompletedAt.isNull()).execute();
    }

    // ── 목록 ────────────────────────────────────────────────────────────────

    /** 구매 + 결제 주문(있어야 한다 — 결제 완료 계열) + 환불한 운영자 */
    private JPAQuery<Tuple> listedRows() {
        Expression<?>[] columns = new Expression<?>[COLUMNS.length + 4];
        System.arraycopy(COLUMNS, 0, columns, 0, COLUMNS.length);
        columns[COLUMNS.length] = O.paymentId;
        columns[COLUMNS.length + 1] = O.receiptUrl;
        columns[COLUMNS.length + 2] = OP.operatorName;
        columns[COLUMNS.length + 3] = P.settlementMonth;
        return query.select(columns).from(P).join(O).on(O.id.eq(P.paymentOrderId)).leftJoin(OP).on(OP.id.eq(P.refundedBy));
    }

    /** 회원의 구매 내역(결제 완료 계열 — 최근 결제 먼저) */
    public List<ListedRow> mine(long userId) {
        return listedRows().where(P.userId.eq(userId), P.paidAt.isNotNull()).orderBy(P.paidAt.desc(), P.id.desc()).fetch().stream().map(EmoticonPurchaseStore::listedOf)
                .collect(Collectors.toList());
    }

    /** 결제 완료 계열 한 줄(결제 주문 칸 포함) */
    public Optional<ListedRow> listed(long id) {
        return Optional.ofNullable(listedRows().where(P.id.eq(id), P.paidAt.isNotNull()).fetchFirst()).map(EmoticonPurchaseStore::listedOf);
    }

    /** 관리자 판매 내역 한 쪽(최근 결제 먼저) */
    public List<ListedRow> sales(SalesQuery q) {
        return listedRows().where(salesWhere(q)).orderBy(P.paidAt.desc(), P.id.desc()).limit(q.limit()).offset(q.offset()).fetch().stream()
                .map(EmoticonPurchaseStore::listedOf).collect(Collectors.toList());
    }

    /** 거른 결과 전체의 수 · 결제 완료(보유 중) 금액 합 */
    public long[] salesTotals(SalesQuery q) {
        NumberExpression<Long> paid = sumLong(paidAmount());
        Tuple t = query.select(P.count(), paid).from(P).where(salesWhere(q)).fetchFirst();
        return new long[] {t == null ? 0 : n(t.get(P.count())), t == null ? 0 : n(t.get(paid))};
    }

    private static BooleanExpression[] salesWhere(SalesQuery q) {
        List<BooleanExpression> where = new ArrayList<>();
        where.add(P.paidAt.isNotNull());
        if (q.from() != null) {
            where.add(P.paidAt.goe(q.from()));
        }
        if (q.to() != null) {
            where.add(P.paidAt.lt(q.to()));
        }
        if (q.packId() != null) {
            where.add(P.packId.eq(q.packId()));
        }
        if (q.status() != null) {
            where.add(P.status.eq(q.status()));
        }
        return where.toArray(BooleanExpression[]::new);
    }

    // ── 집계 ────────────────────────────────────────────────────────────────

    /** 보유 중(PAID)이면 금액, 아니면 0 — 예전 {@code sum(amount) FILTER (WHERE status = 'PAID')} */
    private static NumberExpression<Integer> paidAmount() {
        return new CaseBuilder().when(P.status.eq(EmoticonPurchaseStatus.PAID)).then(P.amount).otherwise(0);
    }

    /** 팩마다 판매(결제 완료 계열이 있는 팩만) */
    public Map<Long, PackSales> packSales(Collection<Long> packIds) {
        Map<Long, PackSales> out = new HashMap<>();
        if (packIds.isEmpty()) {
            return out;
        }
        NumberExpression<Long> paidCount = sumLong(new CaseBuilder().when(P.status.eq(EmoticonPurchaseStatus.PAID)).then(1).otherwise(0));
        NumberExpression<Long> paidAmount = sumLong(paidAmount());
        for (Tuple t : query.select(P.packId, paidCount, paidAmount, P.count()).from(P).where(P.paidAt.isNotNull(), P.packId.in(packIds)).groupBy(P.packId).fetch()) {
            out.put(t.get(P.packId), new PackSales((int) n(t.get(paidCount)), n(t.get(paidAmount)), (int) n(t.get(P.count()))));
        }
        return out;
    }

    /** 작가의 유료 판매액 합계(환불 뺀 — 보유 중인 구매) */
    public long creatorPaidAmount(long creatorId) {
        return n(query.select(sumLong(P.amount)).from(P).where(P.creatorId.eq(creatorId), P.status.eq(EmoticonPurchaseStatus.PAID)).fetchFirst());
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private static PurchaseRow rowOf(Tuple t) {
        return new PurchaseRow(t.get(P.id), t.get(P.userId), t.get(P.packId), t.get(P.creatorId), t.get(P.packName), t.get(P.amount), t.get(P.status),
                t.get(P.refundNoticeAgreedAt), t.get(P.expiresAt), t.get(P.createdAt), t.get(P.closedAt), t.get(P.paymentOrderId), t.get(P.paidAt), t.get(P.refundUntil),
                t.get(P.usedAt), t.get(P.refundedAt), t.get(P.refundKind), t.get(P.refundReason), t.get(P.refundedBy), t.get(P.refundCancelId), t.get(P.refundCompletedAt));
    }

    private static ListedRow listedOf(Tuple t) {
        return new ListedRow(rowOf(t), t.get(O.paymentId), t.get(O.receiptUrl), t.get(OP.operatorName), t.get(P.settlementMonth));
    }
}
