package com.community.payment.repository.master;

import static com.community.payment.repository.master.PaymentOrderStore.ts;

import com.community.payment.dto.CancelKind;
import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.ManualCancelMethod;
import com.community.payment.dto.PgCancelStatus;
import com.community.payment.entity.QPaymentCancelEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.hibernate.query.NativeQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 취소 대기열({@code payment_cancels}) 읽기·쓰기. 상태 전이는 한 문장 조건부 UPDATE — 워커·즉시 시도는 {@code PENDING → SENDING}(+{@code locked_by}) 으로 한쪽만 집고,
 * 끝낼 때는 {@code … AND status = 'SENDING' AND locked_by = ?} 로 집은 쪽만 바꾼다(계약 「확정 흐름」 4). 트랜잭션은 부르는 서비스의 것을 따른다.
 *
 * <p>QueryDSL 이 기본이다(읽기는 프로젝션, 끝내기·재시도·멈춤·되돌리기·수동 처리는 조건부 UPDATE — 즉시 실행). Native SQL 로 남긴 것과 이유:
 * <ul>
 *   <li>{@link #insert} — {@code INSERT … ON CONFLICT (request_key) DO NOTHING}: 같은 요청 키를 동시에 넣어도 유일 색인이 한 번만 받는다(「찾아보고 없으면 넣기」는 경합에 깨진다).</li>
 *   <li>{@link #claimOne}·{@link #claimDue} — 집기({@code UPDATE … RETURNING}, 워커는 {@code … WHERE id IN (SELECT … FOR UPDATE OF c SKIP LOCKED)}): 집은 줄과 돌려주는 줄이
 *       같은 문장의 결과여야 하고(나누면 그 사이에 다른 서버가 집은 줄을 돌려줄 수 있다), 한 결제에 보내는 중은 하나(V100 부분 유일 색인)라 서버끼리 부딪치면
 *       부르는 쪽이 {@link org.springframework.dao.DuplicateKeyException} 을 받아 「못 집음」으로 다룬다 — 그 예외 계약 때문에 {@code JdbcTemplate} 으로 둔다
 *       (같은 트랜잭션이면 JPA 트랜잭션의 연결을 그대로 쓴다). 엔티티 잠금 + UPDATE 로 나누면 집은 줄 집합을 같은 문장에서 받지 못해 바꾸지 않았다.</li>
 * </ul>
 */
@Repository
public class PaymentCancelStore extends IrobitRepositoryQDSL {

    static final QPaymentCancelEntity C = QPaymentCancelEntity.paymentCancelEntity;

    private static final String COLUMNS = "id, payment_order_id, amount, reason, kind, request_key, status, pg_status, pg_cancel_id, attempts, next_attempt_at, "
            + "locked_by, locked_at, last_error, requested_by, created_at, completed_at";

    /** 취소 한 줄 */
    public record CancelRow(long id, long paymentOrderId, long amount, String reason, CancelKind kind, String requestKey, CancelStatus status, PgCancelStatus pgStatus,
                            String pgCancelId, int attempts, Instant nextAttemptAt, Integer lockedBy, Instant lockedAt, String lastError, Long requestedBy,
                            Instant createdAt, Instant completedAt) {
    }

    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;

    public PaymentCancelStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query);
        this.entityManager = entityManager;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 넣는다 — 같은 요청 키가 이미 있으면 넣지 않는다. 넣었으면 true.
     *
     * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT (request_key) DO NOTHING} 은 JPQL·QueryDSL 에 대응 문법이 없다.
     */
    public boolean insert(long id, long paymentOrderId, long amount, String reason, CancelKind kind, String requestKey, Long requestedBy, Instant now) {
        return entityManager.createNativeQuery("INSERT INTO payment_cancels (id, payment_order_id, amount, reason, kind, request_key, status, attempts, next_attempt_at, "
                        + "requested_by, created_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, 'PENDING', 0, ?7, ?8, ?7) ON CONFLICT (request_key) DO NOTHING")
                .unwrap(NativeQuery.class)
                .setParameter(1, id).setParameter(2, paymentOrderId).setParameter(3, amount).setParameter(4, reason).setParameter(5, kind.name())
                .setParameter(6, requestKey).setParameter(7, ts(now)).setParameter(8, requestedBy, Long.class).executeUpdate() == 1;
    }

    public Optional<CancelRow> findById(long id) {
        return one(rows().where(C.id.eq(id)));
    }

    public Optional<CancelRow> findByRequestKey(String requestKey) {
        return one(rows().where(C.requestKey.eq(requestKey)));
    }

    public Optional<CancelRow> findByPgCancelId(String pgCancelId) {
        return one(rows().where(C.pgCancelId.eq(pgCancelId)));
    }

    public List<CancelRow> findByOrder(long paymentOrderId) {
        return rows().where(C.paymentOrderId.eq(paymentOrderId)).orderBy(C.id.asc()).fetch().stream().map(PaymentCancelStore::row).toList();
    }

    /** 아직 끝나지 않은 취소(PENDING·SENDING·GAVE_UP — 멈춘 것도 운영자가 다시 시도할 수 있어 남은 금액을 잡아 둔다)의 금액 합 */
    public long openAmount(long paymentOrderId) {
        Long sum = query.select(C.amount.sum().coalesce(0L)).from(C)
                .where(C.paymentOrderId.eq(paymentOrderId), C.status.in(CancelStatus.PENDING, CancelStatus.SENDING, CancelStatus.GAVE_UP)).fetchOne();
        return sum == null ? 0 : sum;
    }

    /** 같은 결제 주문에 보내는 중(SENDING)인 취소가 없다 — 한 결제의 취소는 하나씩 나간다(V100 의 부분 유일 색인이 마지막 방어선) */
    private static final String NO_SENDING_SIBLING = "NOT EXISTS (SELECT 1 FROM payment_cancels s WHERE s.payment_order_id = c.payment_order_id AND s.status = 'SENDING')";

    /**
     * 그 한 줄을 집는다(즉시 시도) — PENDING 이고 차례가 됐고 같은 결제에 보내는 중인 취소가 없을 때만 SENDING·시도 +1.
     * 다른 서버와 같은 결제의 줄을 동시에 집으면 늦게 커밋하는 쪽이 {@link org.springframework.dao.DuplicateKeyException} 을 받는다 — 부르는 쪽이 「못 집음」으로 다룬다.
     *
     * <p>Native SQL 인 이유: 클래스 설명 — 집은 줄을 같은 문장의 {@code RETURNING} 으로 받고 {@code DuplicateKeyException} 계약을 지킨다.
     */
    public Optional<CancelRow> claimOne(long id, int nodeId, Instant now) {
        return jdbc.query("UPDATE payment_cancels c SET status = 'SENDING', locked_by = ?, locked_at = ?, attempts = attempts + 1 "
                + "WHERE c.id = ? AND c.status = 'PENDING' AND c.next_attempt_at <= ? AND " + NO_SENDING_SIBLING + " RETURNING " + COLUMNS, PaymentCancelStore::claimed,
                nodeId, Timestamp.from(now), id, Timestamp.from(now)).stream().findFirst();
    }

    /**
     * 차례가 된 줄을 집는다 — {@code FOR UPDATE SKIP LOCKED} 로 다른 워커가 집는 중인 줄은 건너뛰고 SENDING·시도 +1.
     * 한 결제에서는 보내는 중인 취소가 없을 때 차례가 가장 이른 한 줄만 집는다(나머지는 앞의 것이 끝난 다음 바퀴).
     * 서버끼리 같은 결제의 줄을 동시에 집으면 {@link org.springframework.dao.DuplicateKeyException} — 부르는 쪽이 이번 바퀴를 건너뛴다.
     *
     * <p>Native SQL 인 이유: 클래스 설명 — {@code UPDATE … WHERE id IN (SELECT … SKIP LOCKED) RETURNING} 한 문장이라 집은 줄 집합과 돌려주는 줄이 같다.
     */
    public List<CancelRow> claimDue(int limit, int nodeId, Instant now) {
        Timestamp at = Timestamp.from(now);
        return jdbc.query("UPDATE payment_cancels SET status = 'SENDING', locked_by = ?, locked_at = ?, attempts = attempts + 1 "
                + "WHERE id IN (SELECT c.id FROM payment_cancels c WHERE c.status = 'PENDING' AND c.next_attempt_at <= ? AND " + NO_SENDING_SIBLING + " "
                + "AND NOT EXISTS (SELECT 1 FROM payment_cancels e WHERE e.payment_order_id = c.payment_order_id AND e.status = 'PENDING' AND e.next_attempt_at <= ? "
                + "AND (e.next_attempt_at, e.id) < (c.next_attempt_at, c.id)) "
                + "ORDER BY c.next_attempt_at, c.id LIMIT ? FOR UPDATE OF c SKIP LOCKED) "
                + "RETURNING " + COLUMNS, PaymentCancelStore::claimed, nodeId, at, at, at, limit).stream()
                .sorted(Comparator.comparing(CancelRow::nextAttemptAt).thenComparingLong(CancelRow::id)).toList();
    }

    /** SENDING 으로 {@code staleBefore} 전부터 머문 줄(보내던 워커가 죽었다)을 바로 PENDING 으로 되돌린다 — 재시도 전 조회가 이미 취소됐는지 맞춘다. 되돌린 줄 수 */
    public int recoverStale(Instant staleBefore, Instant now) {
        return (int) query.update(C).set(C.status, CancelStatus.PENDING).set(C.nextAttemptAt, now).setNull(C.lockedBy).setNull(C.lockedAt)
                .set(C.lastError, "STALE_LOCK").where(C.status.eq(CancelStatus.SENDING), C.lockedAt.lt(staleBefore)).execute();
    }

    /**
     * 성공으로 끝낸다. {@code nodeId} 가 있으면 그 노드가 집은 SENDING 만(워커·즉시 시도), 없으면 끝나지 않은 줄 어느 것이든(웹훅·재시도 전 조회가 포트원에서 확인한 성공).
     * 취소 내역 아이디는 받았을 때만 바꾼다({@code coalesce(?, pg_cancel_id)} 와 같다).
     *
     * @return 이긴 쪽만 true
     */
    public boolean markSucceeded(long id, Integer nodeId, String pgCancelId, Instant now) {
        JPAUpdateClause update = query.update(C).set(C.status, CancelStatus.SUCCEEDED).set(C.pgStatus, PgCancelStatus.SUCCEEDED).set(C.completedAt, now)
                .setNull(C.lastError).setNull(C.lockedBy).setNull(C.lockedAt);
        if (pgCancelId != null) {
            update.set(C.pgCancelId, pgCancelId);
        }
        if (nodeId != null) {
            return update.where(C.id.eq(id), C.status.eq(CancelStatus.SENDING), C.lockedBy.eq(nodeId)).execute() == 1;
        }
        return update.where(C.id.eq(id), C.status.in(CancelStatus.PENDING, CancelStatus.SENDING, CancelStatus.GAVE_UP)).execute() == 1;
    }

    /** 다시 시도한다 — {@code nextAttemptAt} 부터 다시 집힌다. 포트원 응답(접수만·실패)과 취소 내역 아이디가 있으면 남긴다 */
    public boolean markRetry(long id, int nodeId, PgCancelStatus pgStatus, String pgCancelId, String error, Instant nextAttemptAt) {
        JPAUpdateClause update = query.update(C).set(C.status, CancelStatus.PENDING).set(C.nextAttemptAt, nextAttemptAt).setNull(C.lockedBy).setNull(C.lockedAt);
        keepProviderFields(update, pgStatus, pgCancelId, error);
        return update.where(C.id.eq(id), C.status.eq(CancelStatus.SENDING), C.lockedBy.eq(nodeId)).execute() == 1;
    }

    /** 재시도를 다 썼다 — GAVE_UP(결제 이상 목록) */
    public boolean markGaveUp(long id, int nodeId, PgCancelStatus pgStatus, String pgCancelId, String error, Instant now) {
        JPAUpdateClause update = query.update(C).set(C.status, CancelStatus.GAVE_UP).set(C.completedAt, now).setNull(C.lockedBy).setNull(C.lockedAt);
        keepProviderFields(update, pgStatus, pgCancelId, error);
        return update.where(C.id.eq(id), C.status.eq(CancelStatus.SENDING), C.lockedBy.eq(nodeId)).execute() == 1;
    }

    /** 포트원 응답·취소 내역 아이디는 받았을 때만 바꾸고({@code coalesce(?, 칸)}), 오류 한 줄은 그대로 쓴다 */
    private static void keepProviderFields(JPAUpdateClause update, PgCancelStatus pgStatus, String pgCancelId, String error) {
        if (pgStatus != null) {
            update.set(C.pgStatus, pgStatus);
        }
        if (pgCancelId != null) {
            update.set(C.pgCancelId, pgCancelId);
        }
        if (error == null) {
            update.setNull(C.lastError);
        } else {
            update.set(C.lastError, error);
        }
    }

    /**
     * 멈춘(GAVE_UP) 취소를 대기열로 되돌린다(운영자 「다시 시도」 — 확정한 목업 가정 1): 시도 횟수 0, 다음 시도 지금, 처리자·사유를 남긴다. GAVE_UP 일 때만.
     *
     * @return 되돌렸으면 true
     */
    public boolean reopenGaveUp(long id, long operatorId, String note, Instant now) {
        JPAUpdateClause update = query.update(C).set(C.status, CancelStatus.PENDING).set(C.attempts, 0).set(C.nextAttemptAt, now).setNull(C.completedAt)
                .set(C.resolvedBy, operatorId);
        setNote(update, note);
        return update.where(C.id.eq(id), C.status.eq(CancelStatus.GAVE_UP)).execute() == 1;
    }

    /**
     * 멈춘(GAVE_UP) 취소를 「수동 처리 완료」로 닫는다 — 표시만 바꾸고 결제 주문의 취소 누계는 건드리지 않는다(확정한 목업 가정 1). GAVE_UP 일 때만.
     *
     * @return 닫았으면 true
     */
    public boolean markManual(long id, long operatorId, String note, Instant now) {
        return markManual(id, operatorId, note, null, null, now);
    }

    /**
     * 수동 처리 완료에 실제 처리 방법을 함께 적는다(공구 계약 결정 74) — 콘솔 취소면 그 취소 ID 를 붙인다(취소 누계는 부르는 쪽이 올린다). {@code method} null = 표시만.
     *
     * @return 닫았으면 true(GAVE_UP 일 때만)
     */
    public boolean markManual(long id, long operatorId, String note, ManualCancelMethod method, String pgCancelId, Instant now) {
        JPAUpdateClause update = query.update(C).set(C.status, CancelStatus.MANUAL).set(C.completedAt, now).set(C.resolvedBy, operatorId);
        setNote(update, note);
        if (method == null) {
            update.setNull(C.manualMethod);
        } else {
            update.set(C.manualMethod, method);
        }
        if (pgCancelId != null) {
            update.set(C.pgCancelId, pgCancelId);
        }
        return update.where(C.id.eq(id), C.status.eq(CancelStatus.GAVE_UP)).execute() == 1;
    }

    private static void setNote(JPAUpdateClause update, String note) {
        if (note == null) {
            update.setNull(C.resolveNote);
        } else {
            update.set(C.resolveNote, note);
        }
    }

    /**
     * 잠근다(운영자 처리 — 결제 주문 행 다음 차례, 구현 때 정한 것 4). 잠금은 취소 표만 고르는 id 문장({@code PESSIMISTIC_WRITE} = {@code FOR NO KEY UPDATE} — 취소를 FK 로
     * 가리키는 로보티콘 구매의 환불 취소 번호는 그 취소 줄을 만드는 환불이 넣는다), 칸은 그 뒤 읽는다(잠근 뒤의 값).
     */
    public Optional<CancelRow> lockById(long id) {
        if (query.select(C.id).from(C).where(C.id.eq(id)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne() == null) {
            return Optional.empty();
        }
        return findById(id);
    }

    /** 그 결제 주문의 성공한 취소 금액 합(불변식 — 취소 누계 = SUCCEEDED 합 + 콘솔에서 취소해 수동 처리한 줄, 공구 계약 결정 74) */
    public long succeededAmount(long paymentOrderId) {
        Long sum = query.select(C.amount.sum().coalesce(0L)).from(C)
                .where(C.paymentOrderId.eq(paymentOrderId),
                        C.status.eq(CancelStatus.SUCCEEDED).or(C.status.eq(CancelStatus.MANUAL).and(C.manualMethod.eq(ManualCancelMethod.CONSOLE_CANCEL))))
                .fetchOne();
        return sum == null ? 0 : sum;
    }

    // ── 한 줄 모양 ──────────────────────────────────────────────────────────

    /** 한 줄의 칸(차례는 {@link #row(Tuple)} 와 같다) — 관리자 읽기 저장소가 칸을 더해 쓴다 */
    static final Expression<?>[] FIELDS = {C.id, C.paymentOrderId, C.amount, C.reason, C.kind, C.requestKey, C.status, C.pgStatus, C.pgCancelId, C.attempts,
            C.nextAttemptAt, C.lockedBy, C.lockedAt, C.lastError, C.requestedBy, C.createdAt, C.completedAt};

    private JPAQuery<Tuple> rows() {
        return query.select(FIELDS).from(C);
    }

    private static Optional<CancelRow> one(JPAQuery<Tuple> found) {
        return Optional.ofNullable(found.fetchOne()).map(PaymentCancelStore::row);
    }

    static CancelRow row(Tuple t) {
        return new CancelRow(t.get(C.id), t.get(C.paymentOrderId), t.get(C.amount), t.get(C.reason), t.get(C.kind), t.get(C.requestKey), t.get(C.status),
                t.get(C.pgStatus), t.get(C.pgCancelId), t.get(C.attempts), t.get(C.nextAttemptAt), t.get(C.lockedBy), t.get(C.lockedAt), t.get(C.lastError),
                t.get(C.requestedBy), t.get(C.createdAt), t.get(C.completedAt));
    }

    /** 집기({@code RETURNING}) 결과 한 줄 */
    static CancelRow claimed(ResultSet rs, int i) throws SQLException {
        return new CancelRow(rs.getLong("id"), rs.getLong("payment_order_id"), rs.getLong("amount"), rs.getString("reason"), CancelKind.valueOf(rs.getString("kind")),
                rs.getString("request_key"), CancelStatus.valueOf(rs.getString("status")),
                rs.getString("pg_status") == null ? null : PgCancelStatus.valueOf(rs.getString("pg_status")), rs.getString("pg_cancel_id"), rs.getInt("attempts"),
                instant(rs.getTimestamp("next_attempt_at")), rs.getObject("locked_by") == null ? null : rs.getInt("locked_by"), instant(rs.getTimestamp("locked_at")),
                rs.getString("last_error"), rs.getObject("requested_by") == null ? null : rs.getLong("requested_by"), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("completed_at")));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
