package com.community.dashboard.repository.master;

import com.community.dashboard.dto.SalesPeriods;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.QGroupBuyEntity;
import com.community.groupbuy.entity.QGroupBuyProductEntity;
import com.community.groupbuy.entity.QGroupBuyQuestionEntity;
import com.community.groupbuy.repository.master.GroupBuyRowNames;
import com.community.payment.entity.QPaymentReconcileDayEntity;
import com.community.payment.entity.QPaymentWebhookLogEntity;
import com.community.payment.entity.QReconciliationIssueEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.community.zconfiguration.time.ServiceTime;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 관리자 대시보드 공구·결제 가젯의 숫자·목록 읽기(admin-dashboard-contract 「서버 부담 원칙」 · commerce.md 16-2 매출 기준). <b>부르는 쪽 트랜잭션에 참여한다.</b>
 *
 * <p>결제·환불 합은 일별 집계 표가 아직 없어 원본 표를 <b>인덱스 시각 범위로만</b> 읽는다 — 결제 {@code ix_payment_orders_paid_at(paid_at)} · 성공한 취소
 * {@code ix_payment_cancels_completed(completed_at) WHERE status = 'SUCCEEDED'}. 범위는 가장 길어야 두 달 남짓(매출 요약)·30일(추이)이다. 양이 커지면 일별 집계 표로 옮긴다.
 *
 * <p>공구 목록·답변 대기 질문·대사일·이슈 수·웹훅 실패 수는 엔티티 QueryDSL 이다. Native SQL 로 남긴 것과 이유: 매출·환불 합({@link #paidSums}·{@link #refundSums} —
 * 부르는 쪽이 정하는 기간 수만큼의 {@code FILTER} 합을 한 번에 세고, 환불은 세 가지 끝난 환불을 {@code UNION ALL} 로 잇는다)과 일별 결제({@link #paidDays} —
 * {@code AT TIME ZONE} 으로 한국 날짜로 묶는다). 질의마다 쓰는 인덱스를 주석으로 적는다.
 */
@Repository
public class DashboardCommerceStore extends RoboyamRepositoryQDSL {

    /**
     * 받아들인 현금 결제(별칭 {@code o}) — 결제된 계열 상태 · 이상 없음(금액 불일치·도메인 거절·늦은 결제는 자동 취소되는 결제라 매출이 아니다) · 결제 시험 제외.
     * 「PAID 는 하나만」 부분 유일 색인({@code ux_payment_orders_accepted})이 세는 결제와 같은 뜻이다(취소된 뒤의 CANCELED 를 더한 것).
     *
     * <p>매출 범위(사용자 결정 2026-11-03 — 지금 그대로): 결제 시험({@code PAYMENT_TEST})만 빼고 모든 결제 종류를 센다 — 공구 주문뿐 아니라 사업자 광고 신청 결제 · 반품 배송비 ·
     * 포인트 충전도 매출에 넣는다. 환불 합({@link #refundSums})도 같은 범위다.
     */
    static final String ACCEPTED_PAYMENT = "o.status IN ('PAID', 'PARTIAL_CANCELED', 'CANCELED') AND o.anomaly IS NULL AND o.order_type <> 'PAYMENT_TEST'";

    /** 금액 합·건수 */
    public record Sum(long amount, long count) {
    }

    /** 하루 결제 합(한국 날짜) */
    public record Day(LocalDate day, long amount, long count) {
    }

    /** 모집 중 공구 한 줄 */
    public record Recruiting(long id, String title, int currentQty, int minQty, int maxQty, Instant endsAt) {
    }

    /** 답변 대기 질문 한 줄 */
    public record OpenQuestion(long id, String groupBuyTitle, String body, boolean secret, Instant askedAt) {
    }

    /** 대사한 날 한 줄 */
    public record ReconcileDay(LocalDate day, String status) {
    }

    private static final QGroupBuyEntity G = QGroupBuyEntity.groupBuyEntity;
    private static final QGroupBuyProductEntity P = QGroupBuyProductEntity.groupBuyProductEntity;
    private static final QGroupBuyQuestionEntity Q = QGroupBuyQuestionEntity.groupBuyQuestionEntity;
    private static final QPaymentReconcileDayEntity RD = QPaymentReconcileDayEntity.paymentReconcileDayEntity;
    private static final QReconciliationIssueEntity RI = QReconciliationIssueEntity.reconciliationIssueEntity;
    private static final QPaymentWebhookLogEntity WH = QPaymentWebhookLogEntity.paymentWebhookLogEntity;

    private final JdbcTemplate jdbc;

    public DashboardCommerceStore(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 공구 ────────────────────────────────────────────────────────────────

    /**
     * 모집 중 공구(마감 임박 차례 — 지금 마감 시각 = 연장 끝 또는 마감 시각) — 모집 중 공구는 {@code ix_group_buys_recruiting_end(end_at) WHERE status = 'RECRUITING'} 부분 인덱스로 고른다
     * (운영자가 연 공구라 수십 줄 — 연장 시각 때문에 정렬은 고른 뒤에 한다).
     */
    public List<Recruiting> recruitingGroupBuys(int limit) {
        return recruiting(null, null, limit);
    }

    /** 모집 중 공구 — {@code [from, to)} 가 있으면 지금 마감 시각이 그 안인 것만(연장 끝은 늘 마감 시각보다 뒤라 {@code end_at < to} 로 먼저 자른다) */
    private List<Recruiting> recruiting(Instant from, Instant to, int limit) {
        DateTimeExpression<Instant> endsAt = Expressions.dateTimeTemplate(Instant.class, "coalesce({0}, {1})", G.extendedUntil, G.endAt); // 지금 마감 시각
        BooleanExpression where = G.status.eq(GroupBuyStatus.RECRUITING);
        if (to != null) {
            where = where.and(G.endAt.lt(to)).and(endsAt.goe(from)).and(endsAt.lt(to));
        }
        return query.select(G.id, G.title, P.name, G.currentQty, G.minQty, G.maxQty, endsAt).from(G).join(P).on(P.id.eq(G.productId)).where(where)
                .orderBy(endsAt.asc(), G.id.asc()).limit(limit).fetch().stream()
                .map(t -> new Recruiting(t.get(G.id), GroupBuyRowNames.title(t.get(G.title), t.get(P.name)), n(t.get(G.currentQty)), n(t.get(G.minQty)), n(t.get(G.maxQty)),
                        t.get(6, Instant.class))).toList();
    }

    /**
     * 범위 {@code [from, to)} 에 마감하는 모집 중 공구(시각 차례, 최대 limit) — 지금 마감 시각(연장 끝 또는 마감 시각)이 범위 안. 연장 끝은 늘 마감 시각보다 뒤라
     * {@code end_at < to} 로 부분 인덱스({@code ix_group_buys_recruiting_end}) 범위를 먼저 자른다.
     */
    public List<Recruiting> closingGroupBuys(Instant from, Instant to, int limit) {
        return recruiting(from, to, limit);
    }

    private static BooleanExpression openQuestion() {
        return Q.status.eq("OPEN").and(Q.hiddenAt.isNull()).and(Q.deletedAt.isNull());
    }

    /** 답변 대기 질문 수(OPEN · 숨기지 않음 · 지우지 않음) — {@code ix_gbq_open(created_at)} 부분 인덱스와 같은 조건 */
    public long openQuestionCount() {
        return n(query.select(Q.count()).from(Q).where(openQuestion()).fetchOne());
    }

    /** 답변 대기 질문 오래된 차례 — 같은 부분 인덱스({@code ix_gbq_open(created_at)}) 차례로 limit 건 */
    public List<OpenQuestion> oldestOpenQuestions(int limit) {
        return query.select(Q.id, G.title, P.name, Q.body, Q.secret, Q.createdAt).from(Q).join(G).on(G.id.eq(Q.groupBuyId)).join(P).on(P.id.eq(G.productId))
                .where(openQuestion()).orderBy(Q.createdAt.asc(), Q.id.asc()).limit(limit).fetch().stream()
                .map(t -> new OpenQuestion(t.get(Q.id), GroupBuyRowNames.title(t.get(G.title), t.get(P.name)), t.get(Q.body), Boolean.TRUE.equals(t.get(Q.secret)),
                        t.get(Q.createdAt))).toList();
    }

    // ── 결제 · 환불 ─────────────────────────────────────────────────────────

    /**
     * 기간마다 받아들인 현금 결제의 결제액({@code paid_amount} — PG 가 알려 준 실제 결제액) 합·건수 — 결제일({@code paid_at}) 기준. 기간들의 OR 를
     * {@code ix_payment_orders_paid_at} 범위(비트맵 OR)로 읽고 기간별 합은 {@code FILTER} 로 한 번에 센다.
     *
     * @return {@code ranges} 와 같은 차례
     */
    public List<Sum> paidSums(List<SalesPeriods.Range> ranges) {
        List<Object> args = new ArrayList<>();
        StringBuilder select = new StringBuilder("SELECT ");
        for (int i = 0; i < ranges.size(); i++) {
            SalesPeriods.Range r = ranges.get(i);
            select.append(i == 0 ? "" : ", ").append("coalesce(sum(o.paid_amount) FILTER (WHERE o.paid_at >= ? AND o.paid_at < ?), 0), "
                    + "count(*) FILTER (WHERE o.paid_at >= ? AND o.paid_at < ?)");
            args.add(ts(r.from()));
            args.add(ts(r.to()));
            args.add(ts(r.from()));
            args.add(ts(r.to()));
        }
        select.append(" FROM payment_orders o WHERE ").append(ACCEPTED_PAYMENT).append(" AND (").append(rangesOn("o.paid_at", ranges, args)).append(")");
        return jdbc.queryForObject(select.toString(), (rs, n) -> sums(rs, ranges.size()), args.toArray());
    }

    /**
     * 기간마다 끝난 현금 환불 합·건수 — 받아들인 결제의 취소만. 끝난 환불 세 가지:
     *
     * <ol>
     *   <li>PG 취소 성공({@code SUCCEEDED}) — 환불 완료일({@code completed_at}) 기준.</li>
     *   <li>포트원 콘솔에서 직접 취소해 수동 처리한 줄({@code MANUAL · CONSOLE_CANCEL}) — 완료일 기준. 1·2 는 결제 주문의 취소 누계({@code canceled_amount})가 세는 것과 같다
     *       (PaymentCancelStore#succeededAmount, 공구 계약 결정 74).</li>
     *   <li><b>계좌 송금 환불</b>({@code MANUAL · BANK_TRANSFER} — PG 가 영구 거절해 계좌로 돌려준 공구 환불, 결정 67) — <b>송금 완료가 기록된 것만</b>
     *       ({@code commerce_bank_transfers.status = 'SENT'}), 그 <b>송금 완료 시각</b>({@code sent_at}) 기준, 송금한 금액({@code amount} — 환불 기록·취소 금액과 같다).
     *       (사용자 결정 2026-11-03). 전환만 하고 아직 송금하지 않은 것(계좌 받기 전 · 계좌 있음)은 넣지 않는다 — 취소 줄의 {@code completed_at} 은 「전환한 시각」이라 쓰지 않는다.
     *       송금 대기 줄 → 환불 기록({@code source_id} = 환불 기록 ID, PK) → 결제 취소({@code payment_cancel_id}, PK) → 결제 주문 순으로 잇는다.
     *       보상액 중 결제액을 넘는 몫({@code GROUP_BUY_COMPENSATION})은 결제를 돌려준 것이 아니라 넣지 않는다.</li>
     * </ol>
     * 표시만 닫은 수동 처리({@code MANUAL}, 방법 없음)는 돈이 나가지 않았으니 넣지 않는다.
     *
     * <p>인덱스: SUCCEEDED 는 {@code ix_payment_cancels_completed(completed_at) WHERE status = 'SUCCEEDED'} 범위, MANUAL · CONSOLE_CANCEL 은 따로 인덱스가 없어 표를 거른다(멈춘 취소를
     * 운영자가 닫은 드문 줄 — 취소 표는 결제 표보다 훨씬 작다). 계좌 송금은 {@code ix_cbt_sent(sent_at) WHERE status = 'SENT'}(admin V6) 범위. 결제 주문은 PK 로 잇는다.
     *
     * @return {@code ranges} 와 같은 차례
     */
    public List<Sum> refundSums(List<SalesPeriods.Range> ranges) {
        List<Object> args = new ArrayList<>();
        StringBuilder select = new StringBuilder("SELECT ");
        for (int i = 0; i < ranges.size(); i++) {
            SalesPeriods.Range r = ranges.get(i);
            select.append(i == 0 ? "" : ", ").append("coalesce(sum(c.amount) FILTER (WHERE c.completed_at >= ? AND c.completed_at < ?), 0), "
                    + "count(*) FILTER (WHERE c.completed_at >= ? AND c.completed_at < ?)");
            args.add(ts(r.from()));
            args.add(ts(r.to()));
            args.add(ts(r.from()));
            args.add(ts(r.to()));
        }
        select.append(" FROM (SELECT x.payment_order_id, x.amount, x.completed_at FROM payment_cancels x WHERE x.status = 'SUCCEEDED' AND (")
                .append(rangesOn("x.completed_at", ranges, args))
                .append(") UNION ALL SELECT y.payment_order_id, y.amount, y.completed_at FROM payment_cancels y WHERE y.status = 'MANUAL' AND y.manual_method = 'CONSOLE_CANCEL' AND (")
                .append(rangesOn("y.completed_at", ranges, args))
                .append(") UNION ALL SELECT z.payment_order_id, t.amount, t.sent_at FROM commerce_bank_transfers t JOIN group_buy_refunds r ON r.id = t.source_id "
                        + "JOIN payment_cancels z ON z.id = r.payment_cancel_id WHERE t.status = 'SENT' AND t.source_kind = 'GROUP_BUY_REFUND' AND z.status = 'MANUAL' "
                        + "AND z.manual_method = 'BANK_TRANSFER' AND (")
                .append(rangesOn("t.sent_at", ranges, args))
                .append(")) c JOIN payment_orders o ON o.id = c.payment_order_id WHERE o.anomaly IS NULL AND o.order_type <> 'PAYMENT_TEST'");
        return jdbc.queryForObject(select.toString(), (rs, n) -> sums(rs, ranges.size()), args.toArray());
    }

    /**
     * 범위 {@code [from, to)} 의 받아들인 현금 결제를 한국 날짜로 묶은 합·건수(날짜 차례, 결제가 없는 날은 빠진다) — {@code ix_payment_orders_paid_at} 범위(최대 30일).
     * 결제 일별 집계 표가 아직 없어 원본을 이 범위로만 묶는다.
     */
    public List<Day> paidDays(Instant from, Instant to) {
        return jdbc.query("SELECT (o.paid_at AT TIME ZONE '" + ServiceTime.ZONE.getId() + "')::date AS day, coalesce(sum(o.paid_amount), 0), count(*) FROM payment_orders o "
                        + "WHERE " + ACCEPTED_PAYMENT + " AND o.paid_at >= ? AND o.paid_at < ? GROUP BY 1 ORDER BY 1",
                (rs, i) -> new Day(rs.getDate(1).toLocalDate(), rs.getLong(2), rs.getLong(3)), ts(from), ts(to));
    }

    // ── 대사 · 웹훅 ─────────────────────────────────────────────────────────

    /** 기록이 있는 가장 최근 대사일 — {@code payment_reconcile_days} PK(day) 를 거꾸로 한 줄 */
    public Optional<ReconcileDay> latestReconcileDay() {
        Tuple t = query.select(RD.reconcileDay, RD.status).from(RD).orderBy(RD.reconcileDay.desc()).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new ReconcileDay(t.get(RD.reconcileDay), t.get(RD.status)));
    }

    /** 열린 대사 이슈 수 — {@code ix_reconciliation_issues_status(status, created_at)} */
    public long openReconcileIssues() {
        return n(query.select(RI.count()).from(RI).where(RI.status.eq("OPEN")).fetchOne());
    }

    /** 그 시각 뒤에 받은 웹훅 중 처리 실패(REJECTED · RETRY) 수 — {@code ix_payment_webhook_logs_result(result, received_at)} 범위 둘 */
    public long webhookFailuresSince(Instant since) {
        return n(query.select(WH.count()).from(WH).where(WH.result.in("REJECTED", "RETRY"), WH.receivedAt.goe(since)).fetchOne());
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    /** {@code (col >= ? AND col < ?) OR …} — 인자를 차례로 더한다 */
    private static String rangesOn(String column, List<SalesPeriods.Range> ranges, List<Object> args) {
        StringBuilder sql = new StringBuilder();
        for (int i = 0; i < ranges.size(); i++) {
            sql.append(i == 0 ? "" : " OR ").append("(").append(column).append(" >= ? AND ").append(column).append(" < ?)");
            args.add(ts(ranges.get(i).from()));
            args.add(ts(ranges.get(i).to()));
        }
        return sql.toString();
    }

    private static List<Sum> sums(ResultSet rs, int size) throws SQLException {
        List<Sum> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(new Sum(rs.getLong(i * 2 + 1), rs.getLong(i * 2 + 2)));
        }
        return out;
    }

    private static Timestamp ts(Instant at) {
        return Timestamp.from(at);
    }

    private static long n(Long value) {
        return value == null ? 0 : value;
    }

    private static int n(Integer value) {
        return value == null ? 0 : value;
    }
}
