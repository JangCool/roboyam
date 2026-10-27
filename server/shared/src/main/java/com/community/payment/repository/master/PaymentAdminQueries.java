package com.community.payment.repository.master;

import static com.community.payment.repository.master.PaymentOrderStore.instant;
import static com.community.payment.repository.master.PaymentOrderStore.ts;

import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.repository.master.PaymentCancelStore.CancelRow;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 관리자 결제 화면의 읽기(2a-3 — 결제 주문·웹훅 수신 내역·결제 이상·대사). 회원은 번호·닉네임만(이메일 없음 — 결정 17), 운영자는 이름만.
 *
 * <p>Native SQL(JDBC)인 이유: 결제 모듈의 저장소가 모두 JDBC 이고(포인트 상점 관리자 목록 {@code PointShopPurchaseStore} 와 같은 방식), 결제 이상 목록은 두 출처(이상 결제·멈췄던 취소)의
 * {@code UNION ALL}, 이력·이슈는 {@code jsonb} 칸을 읽는다. 정렬·쪽 나누기는 DB 에서 한다.
 */
@Repository
public class PaymentAdminQueries {

    /** 관리자 목록 한 줄 — 결제 주문 + 회원 닉네임·탈퇴 여부 + 끝나지 않은 취소(PENDING·SENDING·GAVE_UP)가 있는가 */
    public record OrderAdminRow(OrderRow order, String nickname, boolean withdrawn, boolean cancelPending) {
    }

    /** 결제 주문 거르기 — 만든 시각 [from, to) */
    public record OrderQuery(PaymentStatus status, PaymentAnomaly anomaly, boolean anyAnomaly, PaymentOrderType orderType, Instant from, Instant to, String q, int page,
                             int size) {
    }

    /** 이력 한 줄 — {@code detail} 은 jsonb 글자 그대로 */
    public record EventRow(long id, String kind, String providerEventId, String detailJson, Instant createdAt) {
    }

    /** 취소 한 줄 + 요청·처리한 운영자 이름과 처리 사유 */
    public record CancelAdminRow(CancelRow cancel, String requestedByName, String resolvedByName, String resolveNote) {
    }

    /** 웹훅 수신 기록 한 줄 — 우리 결제 주문이면 그 번호 */
    public record WebhookRow(long id, String webhookId, String eventType, String paymentId, Long paymentOrderId, String result, int httpStatus, String errorCode,
                             Instant receivedAt) {
    }

    /** 웹훅 거르기 — 받은 시각 [from, to) */
    public record WebhookQuery(String result, Instant from, Instant to, String q, int page, int size) {
    }

    /** 결제 이상 한 줄의 뼈대 — 이상 결제({@code ORDER:}) 또는 멈췄던 취소({@code CANCEL:}) */
    public record AnomalyKey(String key, String kind, long orderId, Long cancelId, Instant detectedAt, boolean resolved) {
    }

    /** 결제 이상 거르기 — 발견 시각 [from, to). {@code kind} 는 이상 종류 또는 {@code CANCEL_GAVE_UP} */
    public record AnomalyQuery(String kind, boolean includeResolved, Instant from, Instant to, int page, int size) {
    }

    /** 대사 이슈 한 줄 + 처리한 운영자 이름 */
    public record IssueRow(long id, String type, String paymentId, Long paymentOrderId, LocalDate detectedDay, String detailJson, String status, String resolveNote,
                           String resolvedByName, Instant resolvedAt, Instant createdAt) {
    }

    /** 대사 이슈 거르기 */
    public record IssueQuery(String type, String status, LocalDate day, int page, int size) {
    }

    private static final String ORDER_COLUMNS = "o.id, o.payment_id, o.user_id, o.order_type, o.order_ref_id, o.order_name, o.amount, o.currency, o.status, o.paid_amount, "
            + "o.canceled_amount, o.anomaly, o.pay_method, o.pg_provider, o.pg_tx_id, o.receipt_url, o.paid_at, o.expires_at, o.confirmed_via, o.created_at, o.updated_at";
    private static final String ORDER_SELECT = "SELECT " + ORDER_COLUMNS + ", pr.nickname AS member_nickname, "
            + "(u.id IS NULL OR u.status = 'WITHDRAWN' OR u.deleted_at IS NOT NULL) AS member_withdrawn, "
            + "EXISTS (SELECT 1 FROM payment_cancels pc WHERE pc.payment_order_id = o.id AND pc.status IN ('PENDING', 'SENDING', 'GAVE_UP')) AS cancel_pending "
            + "FROM payment_orders o LEFT JOIN users u ON u.id = o.user_id LEFT JOIN profiles pr ON pr.user_id = o.user_id ";
    private static final String CANCEL_SELECT = "SELECT c.id, c.payment_order_id, c.amount, c.reason, c.kind, c.request_key, c.status, c.pg_status, c.pg_cancel_id, c.attempts, "
            + "c.next_attempt_at, c.locked_by, c.locked_at, c.last_error, c.requested_by, c.created_at, c.completed_at, c.resolve_note, "
            + "rq.operator_name AS requested_name, rs.operator_name AS resolved_name "
            + "FROM payment_cancels c LEFT JOIN operators rq ON rq.id = c.requested_by LEFT JOIN operators rs ON rs.id = c.resolved_by ";
    private static final String WEBHOOK_SELECT = "SELECT w.id, w.webhook_id, w.event_type, w.payment_id, o.id AS order_id, w.result, w.http_status, w.error_code, w.received_at "
            + "FROM payment_webhook_logs w LEFT JOIN payment_orders o ON o.payment_id = w.payment_id ";
    private static final String ISSUE_SELECT = "SELECT i.id, i.type, i.payment_id, i.payment_order_id, i.detected_day, i.detail::text, i.status, i.resolve_note, "
            + "op.operator_name, i.resolved_at, i.created_at FROM reconciliation_issues i LEFT JOIN operators op ON op.id = i.resolved_by ";
    /**
     * 결제 이상의 두 출처 — 이상 결제(자동 취소 줄과 함께, 발견 = 이상 이력 시각)와 이상이 아닌 결제의 「한 번이라도 멈췄던」 취소(발견 = 첫 GAVE_UP 이력 시각).
     * 처리됨 = 그 취소가 SUCCEEDED·MANUAL(확정한 목업 가정 4).
     */
    private static final String ANOMALY_ROWS = "SELECT 'ORDER:' || o.id AS row_key, o.anomaly AS kind, o.id AS order_id, c.id AS cancel_id, "
            + "coalesce((SELECT min(e.created_at) FROM payment_events e WHERE e.payment_order_id = o.id AND e.kind = 'ANOMALY'), o.updated_at) AS detected_at, "
            + "coalesce(c.status IN ('SUCCEEDED', 'MANUAL'), false) AS resolved "
            + "FROM payment_orders o LEFT JOIN payment_cancels c ON c.request_key = 'AUTO_REJECT:' || o.id WHERE o.anomaly IS NOT NULL "
            + "UNION ALL "
            + "SELECT 'CANCEL:' || c.id, 'CANCEL_GAVE_UP', o.id, c.id, g.first_at, c.status IN ('SUCCEEDED', 'MANUAL') "
            + "FROM (SELECT (e.detail ->> 'cancelId')::bigint AS cancel_id, min(e.created_at) AS first_at FROM payment_events e WHERE e.kind = 'CANCEL_GAVE_UP' "
            + "AND e.detail ->> 'cancelId' ~ '^[0-9]{1,19}$' GROUP BY 1) g "
            + "JOIN payment_cancels c ON c.id = g.cancel_id JOIN payment_orders o ON o.id = c.payment_order_id WHERE o.anomaly IS NULL";

    private final JdbcTemplate jdbc;

    public PaymentAdminQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 결제 주문 ───────────────────────────────────────────────────────────

    /** 만든 시각 내림차순 한 쪽 */
    public List<OrderAdminRow> orders(OrderQuery query) {
        List<Object> args = new ArrayList<>();
        String where = orderWhere(query, args);
        args.add(query.size());
        args.add((long) query.page() * query.size());
        return jdbc.query(ORDER_SELECT + where + " ORDER BY o.created_at DESC, o.id DESC LIMIT ? OFFSET ?", PaymentAdminQueries::orderRow, args.toArray());
    }

    public long orderCount(OrderQuery query) {
        List<Object> args = new ArrayList<>();
        String where = orderWhere(query, args);
        Long count = jdbc.queryForObject("SELECT count(*) FROM payment_orders o LEFT JOIN users u ON u.id = o.user_id LEFT JOIN profiles pr ON pr.user_id = o.user_id "
                + where, Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    public Optional<OrderAdminRow> order(long id) {
        return jdbc.query(ORDER_SELECT + "WHERE o.id = ?", PaymentAdminQueries::orderRow, id).stream().findFirst();
    }

    public Map<Long, OrderAdminRow> ordersByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jdbc.query(ORDER_SELECT + "WHERE o.id IN (" + marks(ids.size()) + ")", PaymentAdminQueries::orderRow, ids.toArray()).stream()
                .collect(Collectors.toMap(row -> row.order().id(), row -> row));
    }

    private static String orderWhere(OrderQuery query, List<Object> args) {
        StringBuilder where = new StringBuilder("WHERE true");
        if (query.status() != null) {
            where.append(" AND o.status = ?");
            args.add(query.status().name());
        }
        if (query.anyAnomaly()) {
            where.append(" AND o.anomaly IS NOT NULL");
        } else if (query.anomaly() != null) {
            where.append(" AND o.anomaly = ?");
            args.add(query.anomaly().name());
        }
        if (query.orderType() != null) {
            where.append(" AND o.order_type = ?");
            args.add(query.orderType().name());
        }
        if (query.from() != null) {
            where.append(" AND o.created_at >= ?");
            args.add(ts(query.from()));
        }
        if (query.to() != null) {
            where.append(" AND o.created_at < ?");
            args.add(ts(query.to()));
        }
        String q = query.q() == null ? "" : query.q().strip();
        if (!q.isEmpty()) {
            if (q.matches("\\d{1,19}")) {
                where.append(" AND (o.user_id = ? OR o.payment_id LIKE ? ESCAPE '\\')");
                args.add(Long.parseLong(q));
            } else {
                where.append(" AND o.payment_id LIKE ? ESCAPE '\\'");
            }
            args.add(like(q));
        }
        return where.toString();
    }

    // ── 이력·취소·웹훅(상세) ────────────────────────────────────────────────

    /** 결제 한 건의 이력 — 오래된 것 위 */
    public List<EventRow> events(long paymentOrderId) {
        return jdbc.query("SELECT id, kind, provider_event_id, detail::text, created_at FROM payment_events WHERE payment_order_id = ? ORDER BY created_at, id",
                (rs, i) -> new EventRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), instant(rs.getTimestamp(5))), paymentOrderId);
    }

    /** 결제 한 건의 취소 대기열 — 넣은 차례 */
    public List<CancelAdminRow> cancels(long paymentOrderId) {
        return jdbc.query(CANCEL_SELECT + "WHERE c.payment_order_id = ? ORDER BY c.created_at, c.id", PaymentAdminQueries::cancelRow, paymentOrderId);
    }

    public Optional<CancelAdminRow> cancel(long id) {
        return jdbc.query(CANCEL_SELECT + "WHERE c.id = ?", PaymentAdminQueries::cancelRow, id).stream().findFirst();
    }

    public Map<Long, CancelAdminRow> cancelsByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jdbc.query(CANCEL_SELECT + "WHERE c.id IN (" + marks(ids.size()) + ")", PaymentAdminQueries::cancelRow, ids.toArray()).stream()
                .collect(Collectors.toMap(row -> row.cancel().id(), row -> row));
    }

    /** 그 결제 번호로 받은 웹훅 — 최근 것 위(최대 {@code limit}) */
    public List<WebhookRow> webhooksOf(String paymentId, int limit) {
        return jdbc.query(WEBHOOK_SELECT + "WHERE w.payment_id = ? ORDER BY w.received_at DESC, w.id DESC LIMIT ?", PaymentAdminQueries::webhookRow, paymentId, limit);
    }

    /** 그 결제에 걸린 열린 대사 이슈 수 */
    public int openIssueCount(String paymentId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM reconciliation_issues WHERE payment_id = ? AND status = 'OPEN'", Integer.class, paymentId);
        return count == null ? 0 : count;
    }

    // ── 웹훅 수신 내역 ──────────────────────────────────────────────────────

    public List<WebhookRow> webhooks(WebhookQuery query) {
        List<Object> args = new ArrayList<>();
        String where = webhookWhere(query, args);
        args.add(query.size());
        args.add((long) query.page() * query.size());
        return jdbc.query(WEBHOOK_SELECT + where + " ORDER BY w.received_at DESC, w.id DESC LIMIT ? OFFSET ?", PaymentAdminQueries::webhookRow, args.toArray());
    }

    public long webhookCount(WebhookQuery query) {
        List<Object> args = new ArrayList<>();
        String where = webhookWhere(query, args);
        Long count = jdbc.queryForObject("SELECT count(*) FROM payment_webhook_logs w " + where, Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private static String webhookWhere(WebhookQuery query, List<Object> args) {
        StringBuilder where = new StringBuilder("WHERE true");
        if (query.result() != null) {
            where.append(" AND w.result = ?");
            args.add(query.result());
        }
        if (query.from() != null) {
            where.append(" AND w.received_at >= ?");
            args.add(ts(query.from()));
        }
        if (query.to() != null) {
            where.append(" AND w.received_at < ?");
            args.add(ts(query.to()));
        }
        String q = query.q() == null ? "" : query.q().strip();
        if (!q.isEmpty()) {
            where.append(" AND (w.payment_id LIKE ? ESCAPE '\\' OR w.webhook_id LIKE ? ESCAPE '\\')");
            args.add(like(q));
            args.add(like(q));
        }
        return where.toString();
    }

    // ── 결제 이상 ───────────────────────────────────────────────────────────

    /** 처리 안 된 것 먼저, 그 안에서 발견 시각 내림차순 */
    public List<AnomalyKey> anomalies(AnomalyQuery query) {
        List<Object> args = new ArrayList<>();
        String where = anomalyWhere(query, args);
        args.add(query.size());
        args.add((long) query.page() * query.size());
        return jdbc.query("SELECT row_key, kind, order_id, cancel_id, detected_at, resolved FROM (" + ANOMALY_ROWS + ") a " + where
                        + " ORDER BY resolved, detected_at DESC, row_key LIMIT ? OFFSET ?",
                (rs, i) -> new AnomalyKey(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getObject(4) == null ? null : rs.getLong(4), instant(rs.getTimestamp(5)),
                        rs.getBoolean(6)), args.toArray());
    }

    public long anomalyCount(AnomalyQuery query) {
        List<Object> args = new ArrayList<>();
        String where = anomalyWhere(query, args);
        Long count = jdbc.queryForObject("SELECT count(*) FROM (" + ANOMALY_ROWS + ") a " + where, Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    /** 처리 안 된 결제 이상 전체 수(거르기와 무관 — 제목 옆 숫자) */
    public long openAnomalyCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM (" + ANOMALY_ROWS + ") a WHERE NOT resolved", Long.class);
        return count == null ? 0 : count;
    }

    private static String anomalyWhere(AnomalyQuery query, List<Object> args) {
        StringBuilder where = new StringBuilder("WHERE true");
        if (!query.includeResolved()) {
            where.append(" AND NOT resolved");
        }
        if (query.kind() != null) {
            where.append(" AND kind = ?");
            args.add(query.kind());
        }
        if (query.from() != null) {
            where.append(" AND detected_at >= ?");
            args.add(ts(query.from()));
        }
        if (query.to() != null) {
            where.append(" AND detected_at < ?");
            args.add(ts(query.to()));
        }
        return where.toString();
    }

    // ── 대사 ────────────────────────────────────────────────────────────────

    /** 열린 것 먼저, 그 안에서 최근 것 위 */
    public List<IssueRow> issues(IssueQuery query) {
        List<Object> args = new ArrayList<>();
        String where = issueWhere(query, args);
        args.add(query.size());
        args.add((long) query.page() * query.size());
        return jdbc.query(ISSUE_SELECT + where + " ORDER BY (i.status = 'OPEN') DESC, i.created_at DESC, i.id DESC LIMIT ? OFFSET ?", PaymentAdminQueries::issueRow,
                args.toArray());
    }

    public long issueCount(IssueQuery query) {
        List<Object> args = new ArrayList<>();
        String where = issueWhere(query, args);
        Long count = jdbc.queryForObject("SELECT count(*) FROM reconciliation_issues i " + where, Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    public Optional<IssueRow> issue(long id) {
        return jdbc.query(ISSUE_SELECT + "WHERE i.id = ?", PaymentAdminQueries::issueRow, id).stream().findFirst();
    }

    /** 열린 이슈 전체 수 */
    public long openIssueTotal() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM reconciliation_issues WHERE status = 'OPEN'", Long.class);
        return count == null ? 0 : count;
    }

    /** [from, to] 날짜별 열린 이슈 수(발견한 날 기준) */
    public Map<LocalDate, Integer> openIssuesByDay(LocalDate from, LocalDate to) {
        Map<LocalDate, Integer> counts = new HashMap<>();
        jdbc.query("SELECT detected_day, count(*) FROM reconciliation_issues WHERE status = 'OPEN' AND detected_day BETWEEN ? AND ? GROUP BY detected_day",
                rs -> {
                    counts.put(rs.getDate(1).toLocalDate(), rs.getInt(2));
                }, Date.valueOf(from), Date.valueOf(to));
        return counts;
    }

    /** 그 배치의 끝나지 않은 실행 요청의 파라미터(JSON 글자) — 「이 날 다시 대사」가 도는 중인지 */
    public List<String> openRequestParams(String jobKey) {
        return jdbc.queryForList("SELECT params FROM batch_run_requests WHERE job_key = ? AND status IN ('PENDING', 'RUNNING')", String.class, jobKey);
    }

    /** 배치의 지금 cron·사용 여부(줄이 없으면 빈 값) */
    public Optional<String> jobCron(String jobKey) {
        return jdbc.queryForList("SELECT cron FROM batch_jobs WHERE job_key = ?", String.class, jobKey).stream().findFirst();
    }

    private static String issueWhere(IssueQuery query, List<Object> args) {
        StringBuilder where = new StringBuilder("WHERE true");
        if (query.type() != null) {
            where.append(" AND i.type = ?");
            args.add(query.type());
        }
        if (query.status() != null) {
            where.append(" AND i.status = ?");
            args.add(query.status());
        }
        if (query.day() != null) {
            where.append(" AND i.detected_day = ?");
            args.add(Date.valueOf(query.day()));
        }
        return where.toString();
    }

    // ── 행 읽기 ─────────────────────────────────────────────────────────────

    private static OrderAdminRow orderRow(ResultSet rs, int i) throws SQLException {
        return new OrderAdminRow(PaymentOrderStore.row(rs, i), rs.getString("member_nickname"), rs.getBoolean("member_withdrawn"), rs.getBoolean("cancel_pending"));
    }

    private static CancelAdminRow cancelRow(ResultSet rs, int i) throws SQLException {
        return new CancelAdminRow(PaymentCancelStore.row(rs, i), rs.getString("requested_name"), rs.getString("resolved_name"), rs.getString("resolve_note"));
    }

    private static WebhookRow webhookRow(ResultSet rs, int i) throws SQLException {
        return new WebhookRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getObject(5) == null ? null : rs.getLong(5), rs.getString(6),
                rs.getInt(7), rs.getString(8), instant(rs.getTimestamp(9)));
    }

    private static IssueRow issueRow(ResultSet rs, int i) throws SQLException {
        return new IssueRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getObject(4) == null ? null : rs.getLong(4), rs.getDate(5).toLocalDate(), rs.getString(6),
                rs.getString(7), rs.getString(8), rs.getString(9), instant(rs.getTimestamp(10)), instant(rs.getTimestamp(11)));
    }

    private static String like(String q) {
        return "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static String marks(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }
}
