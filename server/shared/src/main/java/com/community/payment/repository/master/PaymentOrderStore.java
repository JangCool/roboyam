package com.community.payment.repository.master;

import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.entity.PaymentOrderEntity;
import com.community.payment.entity.QPaymentOrderEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 결제 주문({@code payment_orders}) 읽기·쓰기. 상태 전이는 모두 한 문장 조건부 UPDATE({@code … WHERE id = ? AND status IN (…)}, QueryDSL — 즉시 실행) — 이긴 쪽만 바꾼다(commerce.md 3절).
 * 트랜잭션은 부르는 서비스의 것을 따른다(쓰기는 트랜잭션 안에서만). 판정·기록 시각은 서비스가 넘기는 지금이다.
 *
 * <p>QueryDSL 이 기본이다. 읽기는 한 줄 모양(프로젝션)이라 영속성 문맥의 낡은 엔티티를 보지 않고, 넣기는 넣은 뒤 바로 내보낸다({@code persistAndFlush}) — 결제 확정 트랜잭션은
 * 처리기({@code onPaid})를 JDBC 세이브포인트로 감싸므로 그 전에 미뤄 둔 변경이 남으면 안 된다(세이브포인트를 되돌릴 때 순서가 꼬인다).
 * 잠금은 결제 주문 표만 고르는 {@code PESSIMISTIC_WRITE}({@code FOR NO KEY UPDATE})로 잡고 칸은 그 뒤 프로젝션으로 읽는다 — 결제 주문을 FK 로 가리키는 줄(로보티콘 구매의
 * 결제 번호)은 이 행을 잠근 결제 확정 안에서 넣는다.
 */
@Repository
public class PaymentOrderStore extends RoboyamRepositoryQDSL {

    /** 결제 주문 — 한 줄 모양의 별칭(관리자 읽기 저장소가 함께 쓴다) */
    static final QPaymentOrderEntity O = QPaymentOrderEntity.paymentOrderEntity;

    /** 결제 주문 한 줄 */
    public record OrderRow(long id, String paymentId, long userId, PaymentOrderType orderType, long orderRefId, String orderName, long amount, String currency,
                           PaymentStatus status, Long paidAmount, long canceledAmount, PaymentAnomaly anomaly, String payMethod, String pgProvider, String pgTxId,
                           String receiptUrl, Instant paidAt, Instant expiresAt, ConfirmedVia confirmedVia, Instant createdAt, Instant updatedAt) {

        /** 받아들인 결제인가(결제된 계열이고 이상이 없다) */
        public boolean accepted() {
            return status.paid() && anomaly == null;
        }

        /** 남은 취소 가능 금액(결제 − 취소됨) — 결제되지 않았으면 0 */
        public long cancellable() {
            return paidAmount == null ? 0 : paidAmount - canceledAmount;
        }
    }

    /** 새 결제 주문 */
    public record NewOrder(long id, String paymentId, long userId, PaymentOrderType orderType, long orderRefId, String orderName, long amount, Instant expiresAt,
                           Instant now) {
    }

    /** 결제 확인 값(포트원 조회 결과에서) */
    public record PaidFields(long paidAmount, Instant paidAt, String payMethod, String pgProvider, String pgTxId, String receiptUrl) {
    }

    /** READY 정리 대상 한 건 */
    public record DueReady(long id, String paymentId, Instant expiresAt) {
    }

    private final PaymentOrderRepository repository;

    public PaymentOrderStore(JPAQueryFactory query, PaymentOrderRepository repository) {
        super(query);
        this.repository = repository;
    }

    /** 넣고 바로 내보낸다(확정·세이브포인트 경로에 미뤄 둔 쓰기를 남기지 않는다) */
    public void insert(NewOrder order) {
        repository.persistAndFlush(PaymentOrderEntity.ready(order.id(), order.paymentId(), order.userId(), order.orderType(), order.orderRefId(), order.orderName(),
                order.amount(), order.expiresAt(), order.now()));
    }

    public Optional<OrderRow> findByPaymentId(String paymentId) {
        return Optional.ofNullable(rows().where(O.paymentId.eq(paymentId)).fetchOne()).map(PaymentOrderStore::row);
    }

    public Optional<OrderRow> findById(long id) {
        return Optional.ofNullable(rows().where(O.id.eq(id)).fetchOne()).map(PaymentOrderStore::row);
    }

    /**
     * 행을 잠그고 읽는다(트랜잭션 안에서만 의미가 있다) — 잠금은 결제 주문 표만 고르는 id 문장({@code FOR NO KEY UPDATE}), 칸은 그 뒤 읽는다(잠근 뒤의 값).
     * 같은 결제의 확정·취소·관리자 처리가 이 행에서 줄을 선다(잠그는 차례: 결제 주문 행 → 취소 줄 → 도메인 주문).
     */
    public Optional<OrderRow> lockById(long id) {
        if (query.select(O.id).from(O).where(O.id.eq(id)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne() == null) {
            return Optional.empty();
        }
        return findById(id);
    }

    /** 같은 주문의 열린 READY(가장 최근 하나 — 재결제마다 앞 READY 를 닫으므로 보통 하나뿐이다) */
    public Optional<OrderRow> findOpenReady(PaymentOrderType orderType, long orderRefId) {
        return Optional.ofNullable(rows().where(O.orderType.eq(orderType), O.orderRefId.eq(orderRefId), O.status.eq(PaymentStatus.READY))
                .orderBy(O.createdAt.desc(), O.id.desc()).limit(1).fetchOne()).map(PaymentOrderStore::row);
    }

    /**
     * 같은 주문의 열린 READY 를 모두 EXPIRED 로 닫는다(재결제마다 새 번호 — commerce.md 8-1). 닫은 결제 주문 ID(번호 차례).
     *
     * <p>예전 {@code UPDATE … RETURNING id} 와 같은 결과다: 열린 READY 줄을 먼저 잠그고({@code FOR NO KEY UPDATE} — UPDATE 가 잡는 잠금과 같은 세기, 기다린 뒤 조건을 다시 본다)
     * 잠근 줄만 닫는다. 잠근 줄은 이 트랜잭션이 끝날 때까지 다른 길이 바꾸지 못하므로 돌려주는 ID 와 닫은 줄이 같다.
     */
    public List<Long> expireOpenReady(PaymentOrderType orderType, long orderRefId, Instant now) {
        List<Long> open = query.select(O.id).from(O).where(O.orderType.eq(orderType), O.orderRefId.eq(orderRefId), O.status.eq(PaymentStatus.READY))
                .orderBy(O.id.asc()).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetch();
        if (open.isEmpty()) {
            return List.of();
        }
        query.update(O).set(O.status, PaymentStatus.EXPIRED).set(O.updatedAt, now).where(O.id.in(open), O.status.eq(PaymentStatus.READY)).execute();
        return open;
    }

    /**
     * READY·EXPIRED → PAID(결제 확인 값·이상·확정 길). 이긴 쪽만 true. 이상이 있으면 「받아들인 결제 하나」 색인에 세지 않는다(결정 3).
     */
    public boolean markPaid(long id, PaidFields paid, PaymentAnomaly anomaly, ConfirmedVia via, Instant now) {
        JPAUpdateClause update = query.update(O).set(O.status, PaymentStatus.PAID).set(O.paidAmount, paid.paidAmount()).set(O.paidAt, paid.paidAt())
                .set(O.confirmedVia, via).set(O.updatedAt, now);
        setOrNull(update, O.payMethod, cut(paid.payMethod(), 40));
        setOrNull(update, O.pgProvider, cut(paid.pgProvider(), 40));
        setOrNull(update, O.pgTxId, cut(paid.pgTxId(), 200));
        setOrNull(update, O.receiptUrl, cut(paid.receiptUrl(), 1000));
        if (anomaly == null) {
            update.setNull(O.anomaly);
        } else {
            update.set(O.anomaly, anomaly);
        }
        return update.where(O.id.eq(id), O.status.in(PaymentStatus.READY, PaymentStatus.EXPIRED)).execute() == 1;
    }

    /** 받아들인 결제에 이상을 표시한다(도메인 거절) — 이상이 없던 결제된 계열만 */
    public boolean markAnomaly(long id, PaymentAnomaly anomaly, Instant now) {
        return query.update(O).set(O.anomaly, anomaly).set(O.updatedAt, now)
                .where(O.id.eq(id), O.anomaly.isNull(), O.status.in(PaymentStatus.PAID, PaymentStatus.PARTIAL_CANCELED)).execute() == 1;
    }

    /** READY → EXPIRED(READY 정리). 이긴 쪽만 true */
    public boolean expireReady(long id, Instant now) {
        return query.update(O).set(O.status, PaymentStatus.EXPIRED).set(O.updatedAt, now).where(O.id.eq(id), O.status.eq(PaymentStatus.READY)).execute() == 1;
    }

    /**
     * 취소 누계를 조건부로 늘린다({@code canceled_amount + amount ≤ paid_amount} 일 때만 — commerce.md 8-3) — 전액이면 CANCELED, 아니면 PARTIAL_CANCELED.
     * SET 의 식은 모두 바꾸기 전 값으로 계산된다(한 문장).
     *
     * @return 늘렸으면 true(넘치면 false — 아무것도 바꾸지 않는다)
     */
    public boolean addCanceled(long id, long amount, Instant now) {
        NumberExpression<Long> after = O.canceledAmount.add(amount);
        return query.update(O).set(O.canceledAmount, after)
                .set(O.status, new CaseBuilder().when(after.eq(O.paidAmount)).then(literal(PaymentStatus.CANCELED)).otherwise(literal(PaymentStatus.PARTIAL_CANCELED)))
                .set(O.updatedAt, now)
                .where(O.id.eq(id), O.status.in(PaymentStatus.PAID, PaymentStatus.PARTIAL_CANCELED), after.loe(O.paidAmount)).execute() == 1;
    }

    /**
     * READY 정리 대상 — 유효 시각이 {@code cutoff} 이하인 READY 전부(「아직 처리 안 한 것 전부」), (유효 시각, ID) 키셋 차례. 부분 색인 {@code ix_payment_orders_ready_expiry}.
     *
     * @param afterExpiresAt 이 키 다음부터(null 이면 처음부터)
     */
    public List<DueReady> dueReady(Instant cutoff, Instant afterExpiresAt, long afterId, int limit) {
        JPAQuery<Tuple> due = query.select(O.id, O.paymentId, O.expiresAt).from(O).where(O.status.eq(PaymentStatus.READY), O.expiresAt.loe(cutoff));
        if (afterExpiresAt != null) {
            // (expires_at, id) > (?, ?) 와 같은 키셋 — 유효 시각은 NOT NULL 이다
            due.where(O.expiresAt.gt(afterExpiresAt).or(O.expiresAt.eq(afterExpiresAt).and(O.id.gt(afterId))));
        }
        return due.orderBy(O.expiresAt.asc(), O.id.asc()).limit(limit).fetch().stream().map(PaymentOrderStore::due).toList();
    }

    /** 「실패 건만 다시」 — 결제 주문 ID 로 다시 읽는다(아직 READY 이고 기준 시각이 지난 것만) */
    public List<DueReady> dueReadyByIds(Instant cutoff, List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return query.select(O.id, O.paymentId, O.expiresAt).from(O).where(O.status.eq(PaymentStatus.READY), O.expiresAt.loe(cutoff), O.id.in(ids))
                .orderBy(O.expiresAt.asc(), O.id.asc()).fetch().stream().map(PaymentOrderStore::due).toList();
    }

    // ── 한 줄 모양 ──────────────────────────────────────────────────────────

    /** 한 줄의 칸(차례는 {@link #row} 와 같다) — 관리자 읽기 저장소가 칸을 더해 쓴다 */
    static final Expression<?>[] COLUMNS = {O.id, O.paymentId, O.userId, O.orderType, O.orderRefId, O.orderName, O.amount, O.currency, O.status, O.paidAmount,
            O.canceledAmount, O.anomaly, O.payMethod, O.pgProvider, O.pgTxId, O.receiptUrl, O.paidAt, O.expiresAt, O.confirmedVia, O.createdAt, O.updatedAt};

    private JPAQuery<Tuple> rows() {
        return query.select(COLUMNS).from(O);
    }

    static OrderRow row(Tuple t) {
        return new OrderRow(t.get(O.id), t.get(O.paymentId), t.get(O.userId), t.get(O.orderType), t.get(O.orderRefId), t.get(O.orderName), t.get(O.amount),
                t.get(O.currency), t.get(O.status), t.get(O.paidAmount), t.get(O.canceledAmount), t.get(O.anomaly), t.get(O.payMethod), t.get(O.pgProvider),
                t.get(O.pgTxId), t.get(O.receiptUrl), t.get(O.paidAt), t.get(O.expiresAt), t.get(O.confirmedVia), t.get(O.createdAt), t.get(O.updatedAt));
    }

    private static DueReady due(Tuple t) {
        return new DueReady(t.get(O.id), t.get(O.paymentId), t.get(O.expiresAt));
    }

    /**
     * 상태 값을 HQL 의 enum 글자(완전한 이름)로 — CASE 의 THEN·ELSE 에 인자를 두면 Hibernate 가 인자의 형을 정하지 못한다(SET 칸의 형으로 추론하지 않는다).
     */
    private static Expression<PaymentStatus> literal(PaymentStatus status) {
        return Expressions.template(PaymentStatus.class, PaymentStatus.class.getName() + "." + status.name());
    }

    private static void setOrNull(JPAUpdateClause update, StringPath path, String value) {
        if (value == null) {
            update.setNull(path);
        } else {
            update.set(path, value);
        }
    }

    /** 네이티브 문장의 시각 인자(UTC — 남겨 둔 네이티브 SQL 이 쓴다) */
    static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
