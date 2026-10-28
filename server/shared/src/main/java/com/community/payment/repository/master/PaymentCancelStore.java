package com.community.payment.repository.master;

import static com.community.payment.repository.master.PaymentOrderStore.instant;
import static com.community.payment.repository.master.PaymentOrderStore.ts;

import com.community.payment.dto.CancelKind;
import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.ManualCancelMethod;
import com.community.payment.dto.PgCancelStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 취소 대기열({@code payment_cancels}) 읽기·쓰기. 상태 전이는 한 문장 조건부 UPDATE — 워커·즉시 시도는 {@code PENDING → SENDING}(+{@code locked_by}) 으로 한쪽만 집고,
 * 끝낼 때는 {@code … AND status = 'SENDING' AND locked_by = ?} 로 집은 쪽만 바꾼다(계약 「확정 흐름」 4).
 *
 * <p>Native SQL(JDBC)인 이유: {@code INSERT … ON CONFLICT DO NOTHING}(같은 취소를 두 번 넣지 않게), {@code FOR UPDATE SKIP LOCKED}(서버 여럿의 워커가 같은 줄을 두 번 집지 않게),
 * {@code RETURNING} 을 쓴다.
 */
@Repository
public class PaymentCancelStore {

    private static final String COLUMNS = "id, payment_order_id, amount, reason, kind, request_key, status, pg_status, pg_cancel_id, attempts, next_attempt_at, "
            + "locked_by, locked_at, last_error, requested_by, created_at, completed_at";

    /** 취소 한 줄 */
    public record CancelRow(long id, long paymentOrderId, long amount, String reason, CancelKind kind, String requestKey, CancelStatus status, PgCancelStatus pgStatus,
                            String pgCancelId, int attempts, Instant nextAttemptAt, Integer lockedBy, Instant lockedAt, String lastError, Long requestedBy,
                            Instant createdAt, Instant completedAt) {
    }

    private final JdbcTemplate jdbc;

    public PaymentCancelStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 넣는다 — 같은 요청 키가 이미 있으면 넣지 않는다. 넣었으면 true */
    public boolean insert(long id, long paymentOrderId, long amount, String reason, CancelKind kind, String requestKey, Long requestedBy, Instant now) {
        return jdbc.update("INSERT INTO payment_cancels (id, payment_order_id, amount, reason, kind, request_key, status, attempts, next_attempt_at, requested_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?) ON CONFLICT (request_key) DO NOTHING", id, paymentOrderId, amount, reason, kind.name(),
                requestKey, ts(now), requestedBy, ts(now)) == 1;
    }

    public Optional<CancelRow> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM payment_cancels WHERE id = ?", PaymentCancelStore::row, id).stream().findFirst();
    }

    public Optional<CancelRow> findByRequestKey(String requestKey) {
        return jdbc.query("SELECT " + COLUMNS + " FROM payment_cancels WHERE request_key = ?", PaymentCancelStore::row, requestKey).stream().findFirst();
    }

    public Optional<CancelRow> findByPgCancelId(String pgCancelId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM payment_cancels WHERE pg_cancel_id = ?", PaymentCancelStore::row, pgCancelId).stream().findFirst();
    }

    public List<CancelRow> findByOrder(long paymentOrderId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM payment_cancels WHERE payment_order_id = ? ORDER BY id", PaymentCancelStore::row, paymentOrderId);
    }

    /** 아직 끝나지 않은 취소(PENDING·SENDING·GAVE_UP — 멈춘 것도 운영자가 다시 시도할 수 있어 남은 금액을 잡아 둔다)의 금액 합 */
    public long openAmount(long paymentOrderId) {
        Long sum = jdbc.queryForObject("SELECT coalesce(sum(amount), 0) FROM payment_cancels WHERE payment_order_id = ? AND status IN ('PENDING', 'SENDING', 'GAVE_UP')",
                Long.class, paymentOrderId);
        return sum == null ? 0 : sum;
    }

    /** 같은 결제 주문에 보내는 중(SENDING)인 취소가 없다 — 한 결제의 취소는 하나씩 나간다(V100 의 부분 유일 색인이 마지막 방어선) */
    private static final String NO_SENDING_SIBLING = "NOT EXISTS (SELECT 1 FROM payment_cancels s WHERE s.payment_order_id = c.payment_order_id AND s.status = 'SENDING')";

    /**
     * 그 한 줄을 집는다(즉시 시도) — PENDING 이고 차례가 됐고 같은 결제에 보내는 중인 취소가 없을 때만 SENDING·시도 +1.
     * 다른 서버와 같은 결제의 줄을 동시에 집으면 늦게 커밋하는 쪽이 {@link org.springframework.dao.DuplicateKeyException} 을 받는다 — 부르는 쪽이 「못 집음」으로 다룬다.
     */
    public Optional<CancelRow> claimOne(long id, int nodeId, Instant now) {
        return jdbc.query("UPDATE payment_cancels c SET status = 'SENDING', locked_by = ?, locked_at = ?, attempts = attempts + 1 "
                + "WHERE c.id = ? AND c.status = 'PENDING' AND c.next_attempt_at <= ? AND " + NO_SENDING_SIBLING + " RETURNING " + COLUMNS, PaymentCancelStore::row,
                nodeId, ts(now), id, ts(now)).stream().findFirst();
    }

    /**
     * 차례가 된 줄을 집는다 — {@code FOR UPDATE SKIP LOCKED} 로 다른 워커가 집는 중인 줄은 건너뛰고 SENDING·시도 +1.
     * 한 결제에서는 보내는 중인 취소가 없을 때 차례가 가장 이른 한 줄만 집는다(나머지는 앞의 것이 끝난 다음 바퀴).
     * 서버끼리 같은 결제의 줄을 동시에 집으면 {@link org.springframework.dao.DuplicateKeyException} — 부르는 쪽이 이번 바퀴를 건너뛴다.
     */
    public List<CancelRow> claimDue(int limit, int nodeId, Instant now) {
        return jdbc.query("UPDATE payment_cancels SET status = 'SENDING', locked_by = ?, locked_at = ?, attempts = attempts + 1 "
                + "WHERE id IN (SELECT c.id FROM payment_cancels c WHERE c.status = 'PENDING' AND c.next_attempt_at <= ? AND " + NO_SENDING_SIBLING + " "
                + "AND NOT EXISTS (SELECT 1 FROM payment_cancels e WHERE e.payment_order_id = c.payment_order_id AND e.status = 'PENDING' AND e.next_attempt_at <= ? "
                + "AND (e.next_attempt_at, e.id) < (c.next_attempt_at, c.id)) "
                + "ORDER BY c.next_attempt_at, c.id LIMIT ? FOR UPDATE OF c SKIP LOCKED) "
                + "RETURNING " + COLUMNS, PaymentCancelStore::row, nodeId, ts(now), ts(now), ts(now), limit).stream()
                .sorted(java.util.Comparator.comparing(CancelRow::nextAttemptAt).thenComparingLong(CancelRow::id)).toList();
    }

    /** SENDING 으로 {@code staleBefore} 전부터 머문 줄(보내던 워커가 죽었다)을 바로 PENDING 으로 되돌린다 — 재시도 전 조회가 이미 취소됐는지 맞춘다. 되돌린 줄 수 */
    public int recoverStale(Instant staleBefore, Instant now) {
        return jdbc.update("UPDATE payment_cancels SET status = 'PENDING', next_attempt_at = ?, locked_by = NULL, locked_at = NULL, last_error = 'STALE_LOCK' "
                + "WHERE status = 'SENDING' AND locked_at < ?", ts(now), ts(staleBefore));
    }

    /**
     * 성공으로 끝낸다. {@code nodeId} 가 있으면 그 노드가 집은 SENDING 만(워커·즉시 시도), 없으면 끝나지 않은 줄 어느 것이든(웹훅·재시도 전 조회가 포트원에서 확인한 성공).
     *
     * @return 이긴 쪽만 true
     */
    public boolean markSucceeded(long id, Integer nodeId, String pgCancelId, Instant now) {
        if (nodeId != null) {
            return jdbc.update("UPDATE payment_cancels SET status = 'SUCCEEDED', pg_status = 'SUCCEEDED', pg_cancel_id = coalesce(?, pg_cancel_id), completed_at = ?, "
                    + "last_error = NULL, locked_by = NULL, locked_at = NULL WHERE id = ? AND status = 'SENDING' AND locked_by = ?", pgCancelId, ts(now), id, nodeId) == 1;
        }
        return jdbc.update("UPDATE payment_cancels SET status = 'SUCCEEDED', pg_status = 'SUCCEEDED', pg_cancel_id = coalesce(?, pg_cancel_id), completed_at = ?, "
                + "last_error = NULL, locked_by = NULL, locked_at = NULL WHERE id = ? AND status IN ('PENDING', 'SENDING', 'GAVE_UP')", pgCancelId, ts(now), id) == 1;
    }

    /** 다시 시도한다 — {@code nextAttemptAt} 부터 다시 집힌다. 포트원 응답(접수만·실패)과 취소 내역 아이디가 있으면 남긴다 */
    public boolean markRetry(long id, int nodeId, PgCancelStatus pgStatus, String pgCancelId, String error, Instant nextAttemptAt) {
        return jdbc.update("UPDATE payment_cancels SET status = 'PENDING', pg_status = coalesce(?, pg_status), pg_cancel_id = coalesce(?, pg_cancel_id), last_error = ?, "
                        + "next_attempt_at = ?, locked_by = NULL, locked_at = NULL WHERE id = ? AND status = 'SENDING' AND locked_by = ?",
                pgStatus == null ? null : pgStatus.name(), pgCancelId, error, ts(nextAttemptAt), id, nodeId) == 1;
    }

    /** 재시도를 다 썼다 — GAVE_UP(결제 이상 목록) */
    public boolean markGaveUp(long id, int nodeId, PgCancelStatus pgStatus, String pgCancelId, String error, Instant now) {
        return jdbc.update("UPDATE payment_cancels SET status = 'GAVE_UP', pg_status = coalesce(?, pg_status), pg_cancel_id = coalesce(?, pg_cancel_id), last_error = ?, "
                        + "completed_at = ?, locked_by = NULL, locked_at = NULL WHERE id = ? AND status = 'SENDING' AND locked_by = ?",
                pgStatus == null ? null : pgStatus.name(), pgCancelId, error, ts(now), id, nodeId) == 1;
    }

    /**
     * 멈춘(GAVE_UP) 취소를 대기열로 되돌린다(운영자 「다시 시도」 — 확정한 목업 가정 1): 시도 횟수 0, 다음 시도 지금, 처리자·사유를 남긴다. GAVE_UP 일 때만.
     *
     * @return 되돌렸으면 true
     */
    public boolean reopenGaveUp(long id, long operatorId, String note, Instant now) {
        return jdbc.update("UPDATE payment_cancels SET status = 'PENDING', attempts = 0, next_attempt_at = ?, completed_at = NULL, resolved_by = ?, resolve_note = ? "
                + "WHERE id = ? AND status = 'GAVE_UP'", ts(now), operatorId, note, id) == 1;
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
        return jdbc.update("UPDATE payment_cancels SET status = 'MANUAL', completed_at = ?, resolved_by = ?, resolve_note = ?, manual_method = ?, "
                        + "pg_cancel_id = coalesce(?, pg_cancel_id) WHERE id = ? AND status = 'GAVE_UP'", ts(now), operatorId, note, method == null ? null : method.name(),
                pgCancelId, id) == 1;
    }

    /** 잠근다(운영자 처리 — 결제 주문 행 다음 차례, 구현 때 정한 것 4) */
    public Optional<CancelRow> lockById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM payment_cancels WHERE id = ? FOR UPDATE", PaymentCancelStore::row, id).stream().findFirst();
    }

    /** 그 결제 주문의 성공한 취소 금액 합(불변식 — 취소 누계 = SUCCEEDED 합 + 콘솔에서 취소해 수동 처리한 줄, 공구 계약 결정 74) */
    public long succeededAmount(long paymentOrderId) {
        Long sum = jdbc.queryForObject("SELECT coalesce(sum(amount), 0) FROM payment_cancels WHERE payment_order_id = ? AND (status = 'SUCCEEDED' "
                + "OR (status = 'MANUAL' AND manual_method = 'CONSOLE_CANCEL'))", Long.class, paymentOrderId);
        return sum == null ? 0 : sum;
    }

    static CancelRow row(ResultSet rs, int i) throws SQLException {
        return new CancelRow(rs.getLong("id"), rs.getLong("payment_order_id"), rs.getLong("amount"), rs.getString("reason"), CancelKind.valueOf(rs.getString("kind")),
                rs.getString("request_key"), CancelStatus.valueOf(rs.getString("status")),
                rs.getString("pg_status") == null ? null : PgCancelStatus.valueOf(rs.getString("pg_status")), rs.getString("pg_cancel_id"), rs.getInt("attempts"),
                instant(rs.getTimestamp("next_attempt_at")), rs.getObject("locked_by") == null ? null : rs.getInt("locked_by"), instant(rs.getTimestamp("locked_at")),
                rs.getString("last_error"), rs.getObject("requested_by") == null ? null : rs.getLong("requested_by"), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("completed_at")));
    }
}
