package com.community.payment.repository.master;

import static com.community.payment.repository.master.PaymentCancelStore.C;
import static com.community.payment.repository.master.PaymentEventStore.E;
import static com.community.payment.repository.master.PaymentOrderStore.O;
import static com.community.payment.repository.master.PaymentOrderStore.ts;
import static com.community.payment.repository.master.ReconciliationIssueStore.I;

import com.community.batch.entity.BatchRequestStatus;
import com.community.batch.entity.QBatchJobEntity;
import com.community.batch.entity.QBatchRunRequestEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.entity.QPaymentCancelEntity;
import com.community.payment.entity.QPaymentWebhookLogEntity;
import com.community.payment.repository.master.PaymentCancelStore.CancelRow;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

/**
 * 관리자 결제 화면의 읽기(2a-3 — 결제 주문·웹훅 수신 내역·결제 이상·대사). 회원은 번호·닉네임만(이메일 없음 — 결정 17), 운영자는 이름만. 정렬·쪽 나누기는 DB 에서 한다.
 *
 * <p>QueryDSL 이 기본이다 — 동적 거르기는 {@link BooleanBuilder}, 회원·운영자·결제 주문은 {@code leftJoin}, 한 줄 모양은 프로젝션, 쪽은 {@code offset/limit}.
 * Native SQL 로 남긴 것과 이유: 결제 이상 목록({@link #anomalies}·{@link #anomalyCount}·{@link #openAnomalyCount}) — 두 출처(이상 결제 · 한 번이라도 멈췄던 취소)의
 * {@code UNION ALL} 위에서 거르고 정렬·쪽 나누기를 하고, 멈췄던 취소는 이력 {@code detail ->> 'cancelId'} 를 정규식({@code ~})으로 걸러 묶는다 — JPQL·QueryDSL 에
 * {@code UNION}·jsonb 연산·정규식 비교가 없다(따로 읽어 메모리에서 합치면 쪽 나누기를 DB 에서 하지 못한다).
 */
@Repository
public class PaymentAdminQueries extends IrobitRepositoryQDSL {

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

    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QProfileEntity P = QProfileEntity.profileEntity;
    private static final QPaymentWebhookLogEntity W = QPaymentWebhookLogEntity.paymentWebhookLogEntity;
    private static final QOperatorEntity REQUESTED = new QOperatorEntity("requestedOperator");
    private static final QOperatorEntity RESOLVED = new QOperatorEntity("resolvedOperator");
    private static final QOperatorEntity ISSUE_RESOLVER = new QOperatorEntity("issueResolver");
    private static final QBatchRunRequestEntity REQUEST = QBatchRunRequestEntity.batchRunRequestEntity;
    private static final QBatchJobEntity JOB = QBatchJobEntity.batchJobEntity;
    /** 하위 질의용 별칭 — 끝나지 않은 취소(바깥 질의의 취소 별칭과 겹치지 않게) */
    private static final QPaymentCancelEntity OPEN_CANCEL = new QPaymentCancelEntity("openCancel");

    /** 끝나지 않은 취소(PENDING·SENDING·GAVE_UP)가 있는가 — {@code EXISTS} 를 값으로 */
    private static final Expression<Boolean> CANCEL_PENDING = new CaseBuilder()
            .when(JPAExpressions.selectOne().from(OPEN_CANCEL)
                    .where(OPEN_CANCEL.paymentOrderId.eq(O.id), OPEN_CANCEL.status.in(CancelStatus.PENDING, CancelStatus.SENDING, CancelStatus.GAVE_UP)).exists())
            .then((Expression<Boolean>) Expressions.booleanTemplate("true")).otherwise(Expressions.booleanTemplate("false"));

    /** 대사 이슈: 열린 것(1) 먼저 — {@code (i.status = 'OPEN') DESC} 와 같은 차례 */
    private static final NumberExpression<Integer> ISSUE_OPEN_FIRST = new CaseBuilder().when(I.status.eq("OPEN"))
            .then(Expressions.numberTemplate(Integer.class, "1")).otherwise(Expressions.numberTemplate(Integer.class, "0"));

    /**
     * 결제 이상의 두 출처 — 이상 결제(자동 취소 줄과 함께, 발견 = 이상 이력 시각)와 이상이 아닌 결제의 「한 번이라도 멈췄던」 취소(발견 = 첫 GAVE_UP 이력 시각).
     * 처리됨 = 그 취소가 SUCCEEDED·MANUAL(확정한 목업 가정 4). 발견 시각은 마이크로초 정수로 꺼낸다(네이티브 결과의 시각 형을 드라이버·세션 시간대에 기대지 않는다).
     * 취소 ID 글자 검사 정규식은 인자({@code ?1})로 넘긴다(네이티브 SQL 의 중괄호는 Hibernate 가 별칭 자리로 읽는다).
     */
    private static final String ANOMALY_ROWS = "SELECT 'ORDER:' || o.id AS row_key, o.anomaly AS kind, o.id AS order_id, c.id AS cancel_id, "
            + "coalesce((SELECT min(e.created_at) FROM payment_events e WHERE e.payment_order_id = o.id AND e.kind = 'ANOMALY'), o.updated_at) AS detected_at, "
            + "coalesce(c.status IN ('SUCCEEDED', 'MANUAL'), false) AS resolved "
            + "FROM payment_orders o LEFT JOIN payment_cancels c ON c.request_key = 'AUTO_REJECT:' || o.id WHERE o.anomaly IS NOT NULL "
            + "UNION ALL "
            + "SELECT 'CANCEL:' || c.id, 'CANCEL_GAVE_UP', o.id, c.id, g.first_at, c.status IN ('SUCCEEDED', 'MANUAL') "
            + "FROM (SELECT CAST(e.detail ->> 'cancelId' AS bigint) AS cancel_id, min(e.created_at) AS first_at FROM payment_events e WHERE e.kind = 'CANCEL_GAVE_UP' "
            + "AND e.detail ->> 'cancelId' ~ ?1 GROUP BY 1) g "
            + "JOIN payment_cancels c ON c.id = g.cancel_id JOIN payment_orders o ON o.id = c.payment_order_id WHERE o.anomaly IS NULL";
    /** 이력의 취소 ID 글자(숫자 1~19자) */
    private static final String CANCEL_ID_PATTERN = "^[0-9]{1,19}$";

    private final EntityManager entityManager;

    public PaymentAdminQueries(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    // ── 결제 주문 ───────────────────────────────────────────────────────────

    /** 만든 시각 내림차순 한 쪽 */
    public List<OrderAdminRow> orders(OrderQuery q) {
        return orderRows().where(orderWhere(q)).orderBy(O.createdAt.desc(), O.id.desc()).offset((long) q.page() * q.size()).limit(q.size()).fetch().stream()
                .map(PaymentAdminQueries::orderRow).toList();
    }

    /** 거르기에 맞는 결제 주문 수(회원·프로필 잇기는 한 줄에 하나뿐이라 수를 바꾸지 않는다) */
    public long orderCount(OrderQuery q) {
        Long count = query.select(O.count()).from(O).where(orderWhere(q)).fetchOne();
        return count == null ? 0 : count;
    }

    public Optional<OrderAdminRow> order(long id) {
        return Optional.ofNullable(orderRows().where(O.id.eq(id)).fetchOne()).map(PaymentAdminQueries::orderRow);
    }

    public Map<Long, OrderAdminRow> ordersByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return orderRows().where(O.id.in(ids)).fetch().stream().map(PaymentAdminQueries::orderRow).collect(Collectors.toMap(row -> row.order().id(), row -> row));
    }

    private JPAQuery<Tuple> orderRows() {
        Expression<?>[] columns = Stream.concat(Stream.of(PaymentOrderStore.COLUMNS), Stream.of(P.nickname, U.id, U.status, U.deletedAt, CANCEL_PENDING))
                .toArray(Expression<?>[]::new);
        return query.select(columns).from(O).leftJoin(U).on(U.id.eq(O.userId)).leftJoin(P).on(P.userId.eq(O.userId));
    }

    private static OrderAdminRow orderRow(Tuple t) {
        boolean withdrawn = t.get(U.id) == null || t.get(U.status) == UserStatus.WITHDRAWN || t.get(U.deletedAt) != null;
        return new OrderAdminRow(PaymentOrderStore.row(t), t.get(P.nickname), withdrawn, Boolean.TRUE.equals(t.get(CANCEL_PENDING)));
    }

    private static BooleanBuilder orderWhere(OrderQuery q) {
        BooleanBuilder where = new BooleanBuilder();
        if (q.status() != null) {
            where.and(O.status.eq(q.status()));
        }
        if (q.anyAnomaly()) {
            where.and(O.anomaly.isNotNull());
        } else if (q.anomaly() != null) {
            where.and(O.anomaly.eq(q.anomaly()));
        }
        if (q.orderType() != null) {
            where.and(O.orderType.eq(q.orderType()));
        }
        if (q.from() != null) {
            where.and(O.createdAt.goe(q.from()));
        }
        if (q.to() != null) {
            where.and(O.createdAt.lt(q.to()));
        }
        String text = q.q() == null ? "" : q.q().strip();
        if (!text.isEmpty()) {
            if (text.matches("\\d{1,19}")) {
                where.and(O.userId.eq(Long.parseLong(text)).or(O.paymentId.like(like(text), '\\')));
            } else {
                where.and(O.paymentId.like(like(text), '\\'));
            }
        }
        return where;
    }

    // ── 이력·취소·웹훅(상세) ────────────────────────────────────────────────

    /** 결제 한 건의 이력 — 오래된 것 위 */
    public List<EventRow> events(long paymentOrderId) {
        return query.select(E.id, E.kind, E.providerEventId, E.detail, E.createdAt).from(E).where(E.paymentOrderId.eq(paymentOrderId))
                .orderBy(E.createdAt.asc(), E.id.asc()).fetch().stream()
                .map(t -> new EventRow(t.get(E.id), t.get(E.kind), t.get(E.providerEventId), t.get(E.detail), t.get(E.createdAt))).toList();
    }

    /** 결제 한 건의 취소 대기열 — 넣은 차례 */
    public List<CancelAdminRow> cancels(long paymentOrderId) {
        return cancelRows().where(C.paymentOrderId.eq(paymentOrderId)).orderBy(C.createdAt.asc(), C.id.asc()).fetch().stream().map(PaymentAdminQueries::cancelRow)
                .toList();
    }

    public Optional<CancelAdminRow> cancel(long id) {
        return Optional.ofNullable(cancelRows().where(C.id.eq(id)).fetchOne()).map(PaymentAdminQueries::cancelRow);
    }

    public Map<Long, CancelAdminRow> cancelsByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return cancelRows().where(C.id.in(ids)).fetch().stream().map(PaymentAdminQueries::cancelRow).collect(Collectors.toMap(row -> row.cancel().id(), row -> row));
    }

    private JPAQuery<Tuple> cancelRows() {
        Expression<?>[] columns = Stream.concat(Stream.of(PaymentCancelStore.FIELDS), Stream.of(C.resolveNote, REQUESTED.operatorName, RESOLVED.operatorName))
                .toArray(Expression<?>[]::new);
        return query.select(columns).from(C).leftJoin(REQUESTED).on(REQUESTED.id.eq(C.requestedBy)).leftJoin(RESOLVED).on(RESOLVED.id.eq(C.resolvedBy));
    }

    private static CancelAdminRow cancelRow(Tuple t) {
        return new CancelAdminRow(PaymentCancelStore.row(t), t.get(REQUESTED.operatorName), t.get(RESOLVED.operatorName), t.get(C.resolveNote));
    }

    /** 그 결제 번호로 받은 웹훅 — 최근 것 위(최대 {@code limit}) */
    public List<WebhookRow> webhooksOf(String paymentId, int limit) {
        return webhookRows().where(W.paymentId.eq(paymentId)).orderBy(W.receivedAt.desc(), W.id.desc()).limit(limit).fetch().stream()
                .map(PaymentAdminQueries::webhookRow).toList();
    }

    /** 그 결제에 걸린 열린 대사 이슈 수 */
    public int openIssueCount(String paymentId) {
        Long count = query.select(I.count()).from(I).where(I.paymentId.eq(paymentId), I.status.eq("OPEN")).fetchOne();
        return count == null ? 0 : count.intValue();
    }

    // ── 웹훅 수신 내역 ──────────────────────────────────────────────────────

    public List<WebhookRow> webhooks(WebhookQuery q) {
        return webhookRows().where(webhookWhere(q)).orderBy(W.receivedAt.desc(), W.id.desc()).offset((long) q.page() * q.size()).limit(q.size()).fetch().stream()
                .map(PaymentAdminQueries::webhookRow).toList();
    }

    public long webhookCount(WebhookQuery q) {
        Long count = query.select(W.count()).from(W).where(webhookWhere(q)).fetchOne();
        return count == null ? 0 : count;
    }

    /** 웹훅 기록 + 우리 결제 주문 번호(결제 번호는 유일 — 잇기가 줄을 늘리지 않는다) */
    private JPAQuery<Tuple> webhookRows() {
        return query.select(W.id, W.webhookId, W.eventType, W.paymentId, O.id, W.result, W.httpStatus, W.errorCode, W.receivedAt).from(W)
                .leftJoin(O).on(O.paymentId.eq(W.paymentId));
    }

    private static WebhookRow webhookRow(Tuple t) {
        return new WebhookRow(t.get(W.id), t.get(W.webhookId), t.get(W.eventType), t.get(W.paymentId), t.get(O.id), t.get(W.result), t.get(W.httpStatus),
                t.get(W.errorCode), t.get(W.receivedAt));
    }

    private static BooleanBuilder webhookWhere(WebhookQuery q) {
        BooleanBuilder where = new BooleanBuilder();
        if (q.result() != null) {
            where.and(W.result.eq(q.result()));
        }
        if (q.from() != null) {
            where.and(W.receivedAt.goe(q.from()));
        }
        if (q.to() != null) {
            where.and(W.receivedAt.lt(q.to()));
        }
        String text = q.q() == null ? "" : q.q().strip();
        if (!text.isEmpty()) {
            where.and(W.paymentId.like(like(text), '\\').or(W.webhookId.like(like(text), '\\')));
        }
        return where;
    }

    // ── 결제 이상 ───────────────────────────────────────────────────────────

    /**
     * 처리 안 된 것 먼저, 그 안에서 발견 시각 내림차순.
     *
     * <p>Native SQL 인 이유: 클래스 설명({@code UNION ALL} + jsonb·정규식).
     */
    public List<AnomalyKey> anomalies(AnomalyQuery q) {
        List<Object> args = new ArrayList<>();
        String where = anomalyWhere(q, args);
        args.add(q.size());
        args.add((long) q.page() * q.size());
        NativeQuery<?> rows = native_("SELECT row_key, kind, order_id, cancel_id, CAST(extract(epoch FROM detected_at) * 1000000 AS bigint) AS detected_micros, resolved "
                + "FROM (" + ANOMALY_ROWS + ") a " + where + " ORDER BY resolved, detected_at DESC, row_key LIMIT ?" + (args.size()) + " OFFSET ?" + (args.size() + 1), args)
                .addScalar("row_key", String.class).addScalar("kind", String.class).addScalar("order_id", Long.class).addScalar("cancel_id", Long.class)
                .addScalar("detected_micros", Long.class).addScalar("resolved", Boolean.class);
        @SuppressWarnings("unchecked")
        List<Object[]> result = (List<Object[]>) rows.getResultList();
        return result.stream().map(r -> new AnomalyKey((String) r[0], (String) r[1], (Long) r[2], (Long) r[3], micros((Long) r[4]), Boolean.TRUE.equals(r[5])))
                .toList();
    }

    public long anomalyCount(AnomalyQuery q) {
        List<Object> args = new ArrayList<>();
        String where = anomalyWhere(q, args);
        return count("SELECT count(*) AS n FROM (" + ANOMALY_ROWS + ") a " + where, args);
    }

    /** 처리 안 된 결제 이상 전체 수(거르기와 무관 — 제목 옆 숫자) */
    public long openAnomalyCount() {
        return count("SELECT count(*) AS n FROM (" + ANOMALY_ROWS + ") a WHERE NOT resolved", new ArrayList<>());
    }

    /** 거르기 — 인자 번호는 정규식(?1) 다음부터 */
    private static String anomalyWhere(AnomalyQuery q, List<Object> args) {
        StringBuilder where = new StringBuilder("WHERE true");
        if (!q.includeResolved()) {
            where.append(" AND NOT resolved");
        }
        if (q.kind() != null) {
            args.add(q.kind());
            where.append(" AND kind = ?").append(args.size() + 1);
        }
        if (q.from() != null) {
            args.add(ts(q.from()));
            where.append(" AND detected_at >= ?").append(args.size() + 1);
        }
        if (q.to() != null) {
            args.add(ts(q.to()));
            where.append(" AND detected_at < ?").append(args.size() + 1);
        }
        return where.toString();
    }

    /** 네이티브 질의 — ?1 은 취소 ID 정규식, 그다음 인자는 차례대로 ?2… (LIMIT·OFFSET 자리는 부르는 쪽이 번호를 붙인다) */
    private NativeQuery<?> native_(String sql, List<Object> args) {
        NativeQuery<?> nativeQuery = entityManager.createNativeQuery(sql).unwrap(NativeQuery.class);
        nativeQuery.setParameter(1, CANCEL_ID_PATTERN);
        for (int i = 0; i < args.size(); i++) {
            nativeQuery.setParameter(i + 2, args.get(i));
        }
        return nativeQuery;
    }

    private long count(String sql, List<Object> args) {
        Object n = native_(sql, args).addScalar("n", Long.class).getSingleResult();
        return n == null ? 0 : (Long) n;
    }

    private static Instant micros(long value) {
        return Instant.ofEpochSecond(Math.floorDiv(value, 1_000_000L), Math.floorMod(value, 1_000_000L) * 1_000L);
    }

    // ── 대사 ────────────────────────────────────────────────────────────────

    /** 열린 것 먼저, 그 안에서 최근 것 위 */
    public List<IssueRow> issues(IssueQuery q) {
        return issueRows().where(issueWhere(q)).orderBy(ISSUE_OPEN_FIRST.desc(), I.createdAt.desc(), I.id.desc()).offset((long) q.page() * q.size()).limit(q.size())
                .fetch().stream().map(PaymentAdminQueries::issueRow).toList();
    }

    public long issueCount(IssueQuery q) {
        Long count = query.select(I.count()).from(I).where(issueWhere(q)).fetchOne();
        return count == null ? 0 : count;
    }

    public Optional<IssueRow> issue(long id) {
        return Optional.ofNullable(issueRows().where(I.id.eq(id)).fetchOne()).map(PaymentAdminQueries::issueRow);
    }

    /** 열린 이슈 전체 수 */
    public long openIssueTotal() {
        Long count = query.select(I.count()).from(I).where(I.status.eq("OPEN")).fetchOne();
        return count == null ? 0 : count;
    }

    /** [from, to] 날짜별 열린 이슈 수(발견한 날 기준) */
    public Map<LocalDate, Integer> openIssuesByDay(LocalDate from, LocalDate to) {
        Map<LocalDate, Integer> counts = new HashMap<>();
        for (Tuple t : query.select(I.detectedDay, I.count()).from(I).where(I.status.eq("OPEN"), I.detectedDay.between(from, to)).groupBy(I.detectedDay).fetch()) {
            counts.put(t.get(I.detectedDay), t.get(I.count()).intValue());
        }
        return counts;
    }

    /** 그 배치의 끝나지 않은 실행 요청의 파라미터(JSON 글자) — 「이 날 다시 대사」가 도는 중인지 */
    public List<String> openRequestParams(String jobKey) {
        return query.select(REQUEST.params).from(REQUEST).where(REQUEST.jobKey.eq(jobKey), REQUEST.status.in(BatchRequestStatus.PENDING, BatchRequestStatus.RUNNING))
                .fetch();
    }

    /** 배치의 지금 cron·사용 여부(줄이 없으면 빈 값) */
    public Optional<String> jobCron(String jobKey) {
        return Optional.ofNullable(query.select(JOB.cron).from(JOB).where(JOB.jobKey.eq(jobKey)).fetchFirst());
    }

    private JPAQuery<Tuple> issueRows() {
        return query.select(I.id, I.issueType, I.paymentId, I.paymentOrderId, I.detectedDay, I.detail, I.status, I.resolveNote, ISSUE_RESOLVER.operatorName,
                        I.resolvedAt, I.createdAt)
                .from(I).leftJoin(ISSUE_RESOLVER).on(ISSUE_RESOLVER.id.eq(I.resolvedBy));
    }

    private static IssueRow issueRow(Tuple t) {
        return new IssueRow(t.get(I.id), t.get(I.issueType), t.get(I.paymentId), t.get(I.paymentOrderId), t.get(I.detectedDay), t.get(I.detail), t.get(I.status),
                t.get(I.resolveNote), t.get(ISSUE_RESOLVER.operatorName), t.get(I.resolvedAt), t.get(I.createdAt));
    }

    private static BooleanBuilder issueWhere(IssueQuery q) {
        BooleanBuilder where = new BooleanBuilder();
        if (q.type() != null) {
            where.and(I.issueType.eq(q.type()));
        }
        if (q.status() != null) {
            where.and(I.status.eq(q.status()));
        }
        if (q.day() != null) {
            where.and(I.detectedDay.eq(q.day()));
        }
        return where;
    }

    private static String like(String q) {
        return "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
