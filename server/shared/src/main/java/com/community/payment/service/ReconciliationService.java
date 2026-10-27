package com.community.payment.service;

import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.payment.gateway.PaymentGateway;
import com.community.payment.gateway.PgPayment;
import com.community.payment.gateway.PgPaymentPage;
import com.community.payment.gateway.PgPaymentStatus;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.payment.repository.master.PaymentReconcileDayStore;
import com.community.payment.repository.master.PaymentReconcileQueries;
import com.community.payment.repository.master.ReconciliationIssueStore;
import com.community.payment.repository.master.ReconciliationIssueStore.Type;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PG 대사(commerce.md 16-1, 계약 결정 16) — 하루(KST 날짜)의 포트원 결제를 다건 조회({@code STATUS_CHANGED_AT} 기준)해 우리 결제 주문과 결제 ID 로 맞춘다.
 *
 * <ul>
 *   <li>포트원 목록의 결제마다 우리 결제 주문과 비교하고, 목록에 없던 우리 쪽 변화(그 날 결제·취소가 끝난 우리 결제)는 단건 조회로 확인한다.</li>
 *   <li>불일치 5종: 포트원 결제 ↔ 우리 READY·EXPIRED({@code PG_PAID_LOCAL_UNPAID}) · 우리 결제 ↔ 포트원 없음·결제 아님({@code LOCAL_PAID_PG_MISSING}) ·
 *       결제 금액({@code AMOUNT_MISMATCH}) · 취소 금액({@code CANCEL_MISMATCH}) · 우리에게 없는 결제({@code PG_ONLY}).</li>
 *   <li><b>자동 보정하지 않는다</b> — 이슈만 남기고 결제 주문·취소 대기열은 그대로다(운영자가 처리, {@code commerce.reconcile.manage}).
 *       열린 이슈는 (종류, 결제) 하나라 다시 돌려도 중복이 없다.</li>
 *   <li>포트원 호출은 트랜잭션 밖이고 쓰기(날짜 상태·이슈)는 각각 새 트랜잭션으로 확정된다 — 배치 덩어리가 실패해 한 건씩 다시 돌아도 이미 남긴 것은 남는다(멱등).</li>
 * </ul>
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    /** 다건 조회 한 쪽 크기 */
    static final int PAGE_SIZE = 100;
    /** 포트원 다건 조회 한도 — {@code (number+1) × size ≤ 60,000} */
    static final int PAGE_LIMIT = 60_000 / PAGE_SIZE;

    /** 하루 대사 결과 — 확인한 결제 수 · 불일치 수(이미 열려 있던 것 포함) · 이번에 새로 연 이슈 수 */
    public record DayResult(LocalDate day, int checked, int mismatches, int opened) {
    }

    /** 불일치 하나 */
    record Finding(Type type, String paymentId, Long paymentOrderId, Map<String, Object> detail) {
    }

    private final PaymentGateway gateway;
    private final PaymentOrderStore orders;
    private final PaymentReconcileQueries queries;
    private final PaymentReconcileDayStore days;
    private final ReconciliationIssueStore issues;
    private final NotificationPublisher publisher;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate separate;
    private final Clock clock;

    public ReconciliationService(PaymentGateway gateway, PaymentOrderStore orders, PaymentReconcileQueries queries, PaymentReconcileDayStore days,
                                 ReconciliationIssueStore issues, NotificationPublisher publisher, SnowflakeIdGenerator ids, PlatformTransactionManager transactions,
                                 ObjectProvider<Clock> clocks) {
        this.gateway = gateway;
        this.orders = orders;
        this.queries = queries;
        this.days = days;
        this.issues = issues;
        this.publisher = publisher;
        this.ids = ids;
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 어제(KST) — 대사할 수 있는 마지막 날 */
    public LocalDate yesterday() {
        return ServiceTime.today(clock).minusDays(1);
    }

    /** 처음 결제가 생긴 날(없으면 어제) — 「아직 안 한 날 전부」의 시작 */
    public LocalDate firstDay() {
        LocalDate yesterday = yesterday();
        return queries.firstOrderAt().map(ServiceTime::dateOf).filter(day -> day.isBefore(yesterday)).orElse(yesterday);
    }

    /**
     * 하루를 대사한다. 포트원 장애·설정 없음은 그 날을 FAILED 로 남기고 예외를 그대로 던진다(배치가 그 날만 실패로 기록 — 다음 실행이 다시 한다).
     *
     * @param runId 배치 실행 번호(없으면 null)
     */
    public DayResult reconcileDay(LocalDate day, Long runId) {
        separate.executeWithoutResult(status -> days.markRunning(day, runId));
        Instant from = day.atStartOfDay(ServiceTime.ZONE).toInstant();
        Instant until = day.plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant();
        Set<String> seen = new LinkedHashSet<>();
        List<Finding> findings = new ArrayList<>();
        try {
            for (int page = 0; page < PAGE_LIMIT; page++) {
                PgPaymentPage result = gateway.listPayments(from, until.minusMillis(1), page, PAGE_SIZE);
                for (PgPayment pg : result.items()) {
                    if (pg.paymentId() != null && seen.add(pg.paymentId())) {
                        findings.addAll(compare(pg.paymentId(), orders.findByPaymentId(pg.paymentId()), pg));
                    }
                }
                if (result.items().size() < PAGE_SIZE) {
                    break;
                }
            }
            for (String paymentId : queries.localChanges(from, until)) {
                if (seen.add(paymentId)) {
                    findings.addAll(compare(paymentId, orders.findByPaymentId(paymentId), gateway.getPayment(paymentId)));
                }
            }
        } catch (RuntimeException e) {
            int checked = seen.size();
            separate.executeWithoutResult(status -> days.finish(day, PaymentReconcileDayStore.Status.FAILED, checked, findings.size(), runId, now()));
            log.warn("대사가 실패했다 day={} 원인={}", day, e.getClass().getSimpleName());
            throw e;
        }
        Instant now = now();
        int opened = 0;
        for (Finding finding : findings) {
            Boolean inserted = separate.execute(status -> issues.open(ids.nextId(), finding.type(), finding.paymentId(), finding.paymentOrderId(), day, finding.detail(), now));
            opened += Boolean.TRUE.equals(inserted) ? 1 : 0;
        }
        separate.executeWithoutResult(status -> days.finish(day, findings.isEmpty() ? PaymentReconcileDayStore.Status.DONE : PaymentReconcileDayStore.Status.MISMATCH,
                seen.size(), findings.size(), runId, now));
        if (!findings.isEmpty()) {
            log.warn("대사 불일치 day={} 불일치={} 새 이슈={}", day, findings.size(), opened);
        }
        return new DayResult(day, seen.size(), findings.size(), opened);
    }

    /** 새로 연 이슈가 있으면 운영자에게 알린다 — 하루 한 번(묶음 키 = 오늘 KST 날짜, 결정 10) */
    public void notifyMismatches(int opened) {
        if (opened <= 0) {
            return;
        }
        separate.executeWithoutResult(status -> publisher.publish(AdminNotificationEvent.COMMERCE_RECONCILE_MISMATCH,
                NotificationContext.empty().dedupe("reconcile:" + ServiceTime.today(clock))));
    }

    /**
     * 포트원 결제 하나와 우리 결제 주문을 비교한다 — 자동 보정 없이 불일치만 돌려준다.
     *
     * @param local 우리 결제 주문(없으면 빈 값)
     * @param pg    포트원 결제(없으면 {@link PgPayment#notFound})
     */
    static List<Finding> compare(String paymentId, Optional<OrderRow> local, PgPayment pg) {
        boolean pgPaid = pg.status() == PgPaymentStatus.PAID || pg.status() == PgPaymentStatus.PARTIAL_CANCELLED || pg.status() == PgPaymentStatus.CANCELLED;
        List<Finding> found = new ArrayList<>();
        if (local.isEmpty()) {
            if (pgPaid) {
                found.add(new Finding(Type.PG_ONLY, paymentId, null, pgDetail(pg)));
            }
            return found;
        }
        OrderRow order = local.get();
        if (!pgPaid) {
            if (order.status().paid()) {
                Map<String, Object> detail = localDetail(order);
                detail.put("pgStatus", pg.status().name());
                found.add(new Finding(Type.LOCAL_PAID_PG_MISSING, paymentId, order.id(), detail));
            }
            return found;
        }
        if (!order.status().paid()) {
            Map<String, Object> detail = pgDetail(pg);
            detail.put("localStatus", order.status().name());
            found.add(new Finding(Type.PG_PAID_LOCAL_UNPAID, paymentId, order.id(), detail));
            return found;
        }
        long paid = order.paidAmount() == null ? 0 : order.paidAmount();
        if (paid != pg.totalAmount()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("pgAmount", pg.totalAmount());
            detail.put("localPaidAmount", paid);
            found.add(new Finding(Type.AMOUNT_MISMATCH, paymentId, order.id(), detail));
        }
        if (order.canceledAmount() != pg.cancelledAmount()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("pgCancelled", pg.cancelledAmount());
            detail.put("localCanceled", order.canceledAmount());
            found.add(new Finding(Type.CANCEL_MISMATCH, paymentId, order.id(), detail));
        }
        return found;
    }

    private static Map<String, Object> pgDetail(PgPayment pg) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("pgStatus", pg.status().name());
        detail.put("pgAmount", pg.totalAmount());
        if (pg.cancelledAmount() > 0) {
            detail.put("pgCancelled", pg.cancelledAmount());
        }
        return detail;
    }

    private static Map<String, Object> localDetail(OrderRow order) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("localStatus", order.status().name());
        detail.put("localPaidAmount", order.paidAmount() == null ? 0 : order.paidAmount());
        return detail;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
