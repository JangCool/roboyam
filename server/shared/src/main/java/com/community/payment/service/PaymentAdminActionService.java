package com.community.payment.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchRequestQueue;
import com.community.batch.entity.BatchRequestKind;
import com.community.payment.batch.PaymentReconciliationJob;
import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.ManualCancelMethod;
import com.community.payment.dto.response.PaymentCancelView;
import com.community.payment.dto.response.PaymentRecheckView;
import com.community.payment.dto.response.ReconcileDayView;
import com.community.payment.dto.response.ReconcileIssueView;
import com.community.payment.exception.PaymentCancelNotFoundException;
import com.community.payment.exception.PaymentCancelNotGaveUpException;
import com.community.payment.exception.PaymentOrderNotFoundException;
import com.community.payment.exception.ReconcileAlreadyRunningException;
import com.community.payment.exception.ReconcileIssueNotFoundException;
import com.community.payment.exception.ReconcileIssueNotOpenException;
import com.community.payment.repository.master.PaymentAdminQueries;
import com.community.payment.repository.master.PaymentAdminQueries.IssueRow;
import com.community.payment.repository.master.PaymentCancelStore;
import com.community.payment.repository.master.PaymentCancelStore.CancelRow;
import com.community.payment.repository.master.PaymentEventStore;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.payment.repository.master.ReconciliationIssueStore;
import com.community.payment.service.PaymentConfirmService.ConfirmResult;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 결제 처리(2a-3, 계약 결정 12·확정한 목업 가정 1~3) — 모두 {@code commerce.reconcile.manage}(컨트롤러 선언)이고, 사유 5~200자 + 감사(돈 관련)를 남긴다.
 *
 * <ul>
 *   <li><b>다시 시도</b>: 멈춘(GAVE_UP) 취소 → PENDING(시도 0, 다음 시도 지금 — 1분 워커가 집는다). 이력 {@code CANCEL_REQUESTED}.</li>
 *   <li><b>수동 처리 완료</b>: GAVE_UP → MANUAL 표시만 — <b>결제 주문의 취소 누계는 바꾸지 않는다</b>(실제 취소 여부는 다시 확인·대사가 맞춘다). 이력 {@code CANCEL_MANUAL}.</li>
 *   <li><b>포트원에서 다시 확인</b>: 확정 코드 하나({@link PaymentConfirmService#confirm}, {@code ADMIN_RECHECK})를 태운다 — READY·EXPIRED 면 확정(EXPIRED 는 늦은 결제 + 자동 취소),
 *       이미 확정된 결제면 포트원 취소 목록을 취소 대기열과 맞춘다(우리 취소는 SUCCEEDED 로, 남의 취소는 대사 이슈 — 자동 보정 없음). 이력 {@code RECHECKED}.</li>
 *   <li><b>대사 이슈 처리</b>: 열린 이슈 → RESOLVED(처리자·사유). 데이터를 고치지 않는다(16-1).</li>
 *   <li><b>이 날 다시 대사</b>: 사유 없이(돈을 움직이지 않는다 — 감사는 일반) 배치 요청 표에 그 날 파라미터로 넣는다(배치를 켠 서버가 집는다, 15-2).</li>
 * </ul>
 * 잠그는 차례는 결제 주문 행 → 취소 줄(구현 때 정한 것 4).
 */
@Service
public class PaymentAdminActionService {

    /** 사유 길이 */
    public static final int REASON_MIN = 5;
    public static final int REASON_MAX = 200;
    /** 감사 대상 종류 */
    public static final String AUDIT_CANCEL = "PAYMENT_CANCEL";
    public static final String AUDIT_ISSUE = "RECONCILIATION_ISSUE";
    public static final String AUDIT_DAY = "PAYMENT_RECONCILE_DAY";

    private final PaymentOrderStore orders;
    private final PaymentCancelStore cancels;
    private final PaymentEventStore events;
    private final ReconciliationIssueStore issues;
    private final PaymentAdminQueries queries;
    private final PaymentConfirmService confirm;
    private final PaymentWebhookService webhooks;
    private final PaymentAdminReadService read;
    private final PaymentCancelManualService manual;
    private final BatchRequestQueue requests;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PaymentAdminActionService(PaymentOrderStore orders, PaymentCancelStore cancels, PaymentEventStore events, ReconciliationIssueStore issues,
                                     PaymentAdminQueries queries, PaymentConfirmService confirm, PaymentWebhookService webhooks, PaymentAdminReadService read,
                                     PaymentCancelManualService manual, BatchRequestQueue requests, AuditLogService audit, SnowflakeIdGenerator ids, PlatformTransactionManager transactions,
                                     ObjectProvider<Clock> clocks) {
        this.orders = orders;
        this.cancels = cancels;
        this.events = events;
        this.issues = issues;
        this.queries = queries;
        this.confirm = confirm;
        this.webhooks = webhooks;
        this.read = read;
        this.manual = manual;
        this.requests = requests;
        this.audit = audit;
        this.ids = ids;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 멈춘 취소 다시 시도 — GAVE_UP 이 아니면 409 {@code PAYMENT_CANCEL_NOT_GAVE_UP} */
    @Transactional
    public PaymentCancelView retryCancel(long cancelId, String reason, long operatorId, String ip) {
        String note = reasonOf(reason);
        CancelRow cancel = lockGaveUp(cancelId);
        Instant now = now();
        cancels.reopenGaveUp(cancelId, operatorId, note, now);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("cancelId", Long.toString(cancelId));
        detail.put("kind", cancel.kind().name());
        detail.put("amount", cancel.amount());
        detail.put("by", "ADMIN_RETRY");
        events.insert(ids.nextId(), cancel.paymentOrderId(), PaymentEventStore.Kind.CANCEL_REQUESTED, detail, now);
        auditCancel(AuditActions.PAYMENT_CANCEL_RETRY, cancel, note, operatorId, ip);
        return read.cancel(cancelId);
    }

    /** 멈춘 취소 수동 처리 완료(표시만 — 취소 누계 불변) — GAVE_UP 이 아니면 409 */
    @Transactional
    public PaymentCancelView manualCancel(long cancelId, String reason, long operatorId, String ip) {
        return manualCancel(cancelId, reason, null, null, operatorId, ip);
    }

    /**
     * 멈춘 취소 수동 처리 완료 + 실제 처리 방법(공구 계약 결정 74 — 콘솔 취소면 취소 ID 를 붙이고 취소 누계까지, 계좌 송금이면 누계 그대로). GAVE_UP 이 아니면 409.
     */
    @Transactional
    public PaymentCancelView manualCancel(long cancelId, String reason, ManualCancelMethod method, String pgCancelId, long operatorId, String ip) {
        String note = reasonOf(reason);
        CancelRow cancel = manual.resolve(cancelId, method, pgCancelId, note, operatorId);
        auditCancel(AuditActions.PAYMENT_CANCEL_MANUAL, cancel, note, operatorId, ip);
        return read.cancel(cancelId);
    }

    /**
     * 포트원에서 다시 확인 — 포트원 조회는 트랜잭션 밖(확정 코드·취소 맞춤이 각자 확정), 이력·감사는 그 뒤 한 트랜잭션.
     *
     * @throws com.community.payment.exception.PaymentGatewayUnavailableException 포트원 장애(503) — 아무것도 바꾸지 않았고 감사도 없다
     */
    public PaymentRecheckView recheck(long paymentOrderId, String reason, long operatorId, String ip) {
        String note = reasonOf(reason);
        OrderRow order = orders.findById(paymentOrderId).orElseThrow(PaymentOrderNotFoundException::new);
        ConfirmResult result = confirm.confirm(order.paymentId(), ConfirmedVia.ADMIN_RECHECK);
        String outcome = switch (result.outcome()) {
            case ACCEPTED, REJECTED -> "CONFIRMED";
            case NOT_PAID -> "PG_NOT_PAID";
            case ALREADY_DONE -> {
                webhooks.syncCancellations(result.order(), now());
                yield "UNCHANGED";
            }
            case NOT_FOUND -> throw new PaymentOrderNotFoundException();
        };
        transaction.executeWithoutResult(status -> {
            Instant now = now();
            events.insert(ids.nextId(), paymentOrderId, PaymentEventStore.Kind.RECHECKED, Map.of("outcome", outcome), now);
            audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.PAYMENT_RECHECK, AuditResult.SUCCESS)
                    .target(PaymentAdminReadService.AUDIT_ORDER, Long.toString(paymentOrderId)).with("paymentId", order.paymentId()).with("outcome", outcome)
                    .with("note", note));
        });
        return new PaymentRecheckView(outcome, read.detail(paymentOrderId));
    }

    /** 대사 이슈 처리 — 없으면 404 {@code RECONCILE_ISSUE_NOT_FOUND}, 이미 처리됐으면 409 {@code RECONCILE_ISSUE_NOT_OPEN} */
    @Transactional
    public ReconcileIssueView resolveIssue(long issueId, String reason, long operatorId, String ip) {
        String note = reasonOf(reason);
        IssueRow issue = queries.issue(issueId).orElseThrow(ReconcileIssueNotFoundException::new);
        if (!issues.resolve(issueId, operatorId, note, now())) {
            throw new ReconcileIssueNotOpenException();
        }
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.RECONCILE_ISSUE_RESOLVE, AuditResult.SUCCESS).target(AUDIT_ISSUE, Long.toString(issueId))
                .with("type", issue.type()).with("paymentId", issue.paymentId()).with("note", note));
        return read.issue(issueId);
    }

    /**
     * 그 날 다시 대사 — 배치 요청 표에 {@code day} 파라미터로 넣는다(비동기). 오늘·미래는 400 {@code fields.day}, 대사 배치에 끝나지 않은 요청이 있으면 409 {@code RECONCILE_ALREADY_RUNNING}.
     *
     * @return 그 날 줄(RUNNING)
     */
    @Transactional
    public ReconcileDayView rerunDay(LocalDate day, long operatorId, String ip) {
        if (day.isAfter(ServiceTime.today(clock).minusDays(1))) {
            throw ValidationException.of("day", new MessageRef("payment.reconcile.day.notPast"));
        }
        if (requests.enqueue(BatchCatalog.PAYMENT_RECONCILIATION.key(), BatchRequestKind.NOW, Map.of(PaymentReconciliationJob.PARAM_DAY, day.toString()), null, null,
                operatorId).isEmpty()) {
            throw new ReconcileAlreadyRunningException();
        }
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.PAYMENT_RECONCILE_RERUN, AuditResult.SUCCESS).target(AUDIT_DAY, day.toString()));
        return read.day(day, true);
    }

    private CancelRow lockGaveUp(long cancelId) {
        CancelRow found = cancels.findById(cancelId).orElseThrow(PaymentCancelNotFoundException::new);
        orders.lockById(found.paymentOrderId()).orElseThrow(PaymentCancelNotFoundException::new);
        CancelRow locked = cancels.lockById(cancelId).orElseThrow(PaymentCancelNotFoundException::new);
        if (locked.status() != CancelStatus.GAVE_UP) {
            throw new PaymentCancelNotGaveUpException();
        }
        return locked;
    }

    private void auditCancel(String action, CancelRow cancel, String note, long operatorId, String ip) {
        String paymentId = orders.findById(cancel.paymentOrderId()).map(OrderRow::paymentId).orElse(null);
        audit.record(AuditEvent.byOperator(operatorId, ip, action, AuditResult.SUCCESS).target(AUDIT_CANCEL, Long.toString(cancel.id()))
                .with("paymentId", paymentId).with("amount", cancel.amount()).with("note", note));
    }

    /** 사유 5~200자({@code fields.reason}) — 앞뒤 공백은 뺀다 */
    static String reasonOf(String reason) {
        String text = reason == null ? "" : reason.strip();
        if (text.length() < REASON_MIN || text.length() > REASON_MAX) {
            throw ValidationException.of("reason", new MessageRef("payment.reason.length", REASON_MIN, REASON_MAX));
        }
        return text;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
