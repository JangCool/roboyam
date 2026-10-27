package com.community.payment.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchMessageCodec;
import com.community.payment.batch.PaymentReconciliationJob;
import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.response.PaymentAnomalyPageView;
import com.community.payment.dto.response.PaymentAnomalyRowView;
import com.community.payment.dto.response.PaymentCancelView;
import com.community.payment.dto.response.PaymentEventView;
import com.community.payment.dto.response.PaymentMemberView;
import com.community.payment.dto.response.PaymentOrderDetailView;
import com.community.payment.dto.response.PaymentOrderRowView;
import com.community.payment.dto.response.PaymentPageView;
import com.community.payment.dto.response.PaymentWebhookRowView;
import com.community.payment.dto.response.ReconcileDayView;
import com.community.payment.dto.response.ReconcileIssueView;
import com.community.payment.dto.response.ReconcileOverviewView;
import com.community.payment.exception.PaymentOrderNotFoundException;
import com.community.payment.repository.master.PaymentAdminQueries;
import com.community.payment.repository.master.PaymentAdminQueries.AnomalyKey;
import com.community.payment.repository.master.PaymentAdminQueries.AnomalyQuery;
import com.community.payment.repository.master.PaymentAdminQueries.CancelAdminRow;
import com.community.payment.repository.master.PaymentAdminQueries.IssueQuery;
import com.community.payment.repository.master.PaymentAdminQueries.IssueRow;
import com.community.payment.repository.master.PaymentAdminQueries.OrderAdminRow;
import com.community.payment.repository.master.PaymentAdminQueries.OrderQuery;
import com.community.payment.repository.master.PaymentAdminQueries.WebhookQuery;
import com.community.payment.repository.master.PaymentAdminQueries.WebhookRow;
import com.community.payment.repository.master.PaymentCancelStore.CancelRow;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.payment.repository.master.PaymentReconcileDayStore;
import com.community.payment.repository.master.PaymentReconcileDayStore.DayRow;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 결제 화면의 읽기(2a-3) — 결제 주문 목록·상세, 웹훅 수신 내역, 결제 이상, 날짜별 대사·대사 이슈. 모양은 admin-web {@code usePaymentAdminApi.types.ts} 그대로.
 * 회원은 번호·닉네임만(결정 17), 이력·이슈 내용은 요청 언어로 푼 한 줄({@link PaymentDetailText}). 상세를 열면 {@code PAYMENT_ORDER_VIEW}(개인정보 열람) 감사를 남긴다.
 */
@Service
@Transactional(readOnly = true)
public class PaymentAdminReadService {

    /** 상세 서랍에 싣는 웹훅 수(최근 것) */
    static final int DETAIL_WEBHOOKS = 50;
    /** 날짜별 대사 표의 날 수 */
    static final int OVERVIEW_DAYS = 14;

    private final PaymentAdminQueries queries;
    private final PaymentReconcileDayStore days;
    private final PaymentDetailText text;
    private final AuditLogService audit;
    private final Clock clock;

    public PaymentAdminReadService(PaymentAdminQueries queries, PaymentReconcileDayStore days, PaymentDetailText text, AuditLogService audit,
                                   ObjectProvider<Clock> clocks) {
        this.queries = queries;
        this.days = days;
        this.text = text;
        this.audit = audit;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 결제 주문 ───────────────────────────────────────────────────────────

    public PaymentPageView<PaymentOrderRowView> orders(OrderQuery query) {
        return new PaymentPageView<>(queries.orders(query).stream().map(this::row).toList(), queries.orderCount(query));
    }

    /** 상세를 연다 — 개인정보 열람 감사(회원 번호·닉네임이 보인다, 결정 17) */
    @Transactional
    public PaymentOrderDetailView open(long id, long operatorId, String ip) {
        PaymentOrderDetailView detail = detail(id);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.PAYMENT_ORDER_VIEW, AuditResult.SUCCESS).target(AUDIT_ORDER, Long.toString(id))
                .with("paymentId", detail.paymentId()));
        return detail;
    }

    /** 상세(감사 없음 — 처리 뒤 새 모양을 돌려줄 때) */
    public PaymentOrderDetailView detail(long id) {
        OrderAdminRow row = queries.order(id).orElseThrow(PaymentOrderNotFoundException::new);
        OrderRow o = row.order();
        List<PaymentEventView> events = queries.events(id).stream()
                .map(e -> new PaymentEventView(Long.toString(e.id()), e.kind(), text.render(e.detailJson()), e.providerEventId(), e.createdAt())).toList();
        List<PaymentCancelView> cancels = queries.cancels(id).stream().map(PaymentAdminReadService::cancel).toList();
        List<PaymentWebhookRowView> webhooks = queries.webhooksOf(o.paymentId(), DETAIL_WEBHOOKS).stream().map(PaymentAdminReadService::webhook).toList();
        return new PaymentOrderDetailView(Long.toString(o.id()), o.paymentId(), member(row), o.orderType(), Long.toString(o.orderRefId()), o.orderName(), o.amount(),
                o.paidAmount(), o.canceledAmount(), o.status(), o.anomaly(), text.payMethod(o.payMethod()), o.paidAt(), o.expiresAt(), o.createdAt(), row.cancelPending(),
                o.currency(), o.pgProvider(), o.pgTxId(), o.receiptUrl(), o.confirmedVia(), o.updatedAt(), events, cancels, webhooks,
                queries.openIssueCount(o.paymentId()));
    }

    /** 취소 한 줄(처리 뒤) */
    public PaymentCancelView cancel(long id) {
        return queries.cancel(id).map(PaymentAdminReadService::cancel).orElseThrow();
    }

    // ── 웹훅 ────────────────────────────────────────────────────────────────

    public PaymentPageView<PaymentWebhookRowView> webhooks(WebhookQuery query) {
        return new PaymentPageView<>(queries.webhooks(query).stream().map(PaymentAdminReadService::webhook).toList(), queries.webhookCount(query));
    }

    // ── 결제 이상 ───────────────────────────────────────────────────────────

    public PaymentAnomalyPageView anomalies(AnomalyQuery query) {
        List<AnomalyKey> keys = queries.anomalies(query);
        Map<Long, OrderAdminRow> orders = queries.ordersByIds(keys.stream().map(AnomalyKey::orderId).collect(Collectors.toSet()));
        Map<Long, CancelAdminRow> cancels = queries.cancelsByIds(keys.stream().map(AnomalyKey::cancelId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        List<PaymentAnomalyRowView> items = keys.stream().map(key -> new PaymentAnomalyRowView(key.key(), key.kind(), row(orders.get(key.orderId())),
                key.cancelId() == null || !cancels.containsKey(key.cancelId()) ? null : cancel(cancels.get(key.cancelId())), key.detectedAt(), key.resolved())).toList();
        return new PaymentAnomalyPageView(items, queries.anomalyCount(query), queries.openAnomalyCount());
    }

    // ── 대사 ────────────────────────────────────────────────────────────────

    /** 날짜별 대사 요약 — 어제부터 14일, 「이 날 다시 대사」 요청이 끝나지 않은 날은 RUNNING */
    public ReconcileOverviewView overview() {
        LocalDate yesterday = ServiceTime.today(clock).minusDays(1);
        LocalDate first = yesterday.minusDays(OVERVIEW_DAYS - 1L);
        Map<LocalDate, DayRow> rows = days.range(first, yesterday).stream().collect(Collectors.toMap(DayRow::day, Function.identity()));
        Map<LocalDate, Integer> open = queries.openIssuesByDay(first, yesterday);
        Set<LocalDate> requested = requestedDays();
        List<ReconcileDayView> list = new ArrayList<>();
        for (LocalDate day = yesterday; !day.isBefore(first); day = day.minusDays(1)) {
            list.add(dayView(day, rows.get(day), open.getOrDefault(day, 0), requested.contains(day)));
        }
        return new ReconcileOverviewView(days.lastChecked().orElse(null), nextRunAt(), list, queries.openIssueTotal());
    }

    /** 날짜 한 줄(다시 대사를 넣은 뒤 — RUNNING) */
    public ReconcileDayView day(LocalDate day, boolean requested) {
        return dayView(day, days.find(day).orElse(null), queries.openIssuesByDay(day, day).getOrDefault(day, 0), requested);
    }

    public PaymentPageView<ReconcileIssueView> issues(IssueQuery query) {
        return new PaymentPageView<>(queries.issues(query).stream().map(this::issue).toList(), queries.issueCount(query));
    }

    /** 이슈 한 줄(처리 뒤) */
    public ReconcileIssueView issue(long id) {
        return queries.issue(id).map(this::issue).orElseThrow();
    }

    // ── 만들기 ──────────────────────────────────────────────────────────────

    /** 감사 대상 종류 — 결제 주문 */
    public static final String AUDIT_ORDER = "PAYMENT_ORDER";

    private PaymentOrderRowView row(OrderAdminRow row) {
        OrderRow o = row.order();
        return new PaymentOrderRowView(Long.toString(o.id()), o.paymentId(), member(row), o.orderType(), Long.toString(o.orderRefId()), o.orderName(), o.amount(),
                o.paidAmount(), o.canceledAmount(), o.status(), o.anomaly(), text.payMethod(o.payMethod()), o.paidAt(), o.expiresAt(), o.createdAt(), row.cancelPending());
    }

    private static PaymentMemberView member(OrderAdminRow row) {
        return new PaymentMemberView(row.withdrawn() ? null : Long.toString(row.order().userId()), row.nickname() == null ? "" : row.nickname(), row.withdrawn());
    }

    private static PaymentCancelView cancel(CancelAdminRow row) {
        CancelRow c = row.cancel();
        return new PaymentCancelView(Long.toString(c.id()), c.amount(), c.kind(), c.status(), c.pgStatus(), c.attempts(),
                c.status() == CancelStatus.PENDING ? c.nextAttemptAt() : null, c.lastError(), c.reason(), row.requestedByName(), row.resolvedByName(), row.resolveNote(),
                c.createdAt(), c.completedAt());
    }

    private static PaymentWebhookRowView webhook(WebhookRow w) {
        return new PaymentWebhookRowView(Long.toString(w.id()), w.webhookId(), w.eventType(), w.paymentId(), w.paymentOrderId() == null ? null : Long.toString(w.paymentOrderId()),
                w.result(), w.httpStatus(), w.errorCode(), w.receivedAt());
    }

    private ReconcileIssueView issue(IssueRow i) {
        String detail = text.render(i.detailJson());
        return new ReconcileIssueView(Long.toString(i.id()), i.type(), i.paymentId(), i.paymentOrderId() == null ? null : Long.toString(i.paymentOrderId()), i.detectedDay(),
                detail == null ? "" : detail, i.status(), i.resolveNote(), i.resolvedByName(), i.resolvedAt(), i.createdAt());
    }

    private static ReconcileDayView dayView(LocalDate day, DayRow row, int openIssues, boolean requested) {
        String status = requested ? "RUNNING" : row == null ? "PENDING" : row.status().name();
        return row == null ? new ReconcileDayView(day, status, 0, 0, openIssues, null)
                : new ReconcileDayView(day, status, row.checkedCount(), row.issueCount(), openIssues, row.finishedAt());
    }

    /** 끝나지 않은 「이 날 다시 대사」 요청의 날 */
    private Set<LocalDate> requestedDays() {
        Set<LocalDate> found = new HashSet<>();
        for (String params : queries.openRequestParams(BatchCatalog.PAYMENT_RECONCILIATION.key())) {
            String day = params == null ? null : BatchMessageCodec.decodeParams(params).get(PaymentReconciliationJob.PARAM_DAY);
            if (day != null) {
                try {
                    found.add(LocalDate.parse(day));
                } catch (java.time.format.DateTimeParseException ignored) {
                    // 검사를 거친 값이라 오지 않는다
                }
            }
        }
        return found;
    }

    /** 다음 자동 실행 — 배치의 지금 cron(없으면 기본값), 서비스 시간대 */
    private Instant nextRunAt() {
        String cron = queries.jobCron(BatchCatalog.PAYMENT_RECONCILIATION.key()).filter(CronExpression::isValidExpression)
                .orElse(BatchCatalog.PAYMENT_RECONCILIATION.defaultCron());
        ZonedDateTime next = CronExpression.parse(cron).next(ZonedDateTime.now(clock.withZone(ServiceTime.ZONE)));
        return next == null ? null : next.toInstant();
    }
}
