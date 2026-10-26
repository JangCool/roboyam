package com.community.payment.service;

import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PgCancelStatus;
import com.community.payment.exception.PaymentGatewayUnavailableException;
import com.community.payment.exception.PaymentNotConfiguredException;
import com.community.payment.gateway.PaymentGateway;
import com.community.payment.gateway.PgCancellation;
import com.community.payment.gateway.PgPayment;
import com.community.payment.gateway.WebhookVerification;
import com.community.payment.repository.master.PaymentCancelStore;
import com.community.payment.repository.master.PaymentCancelStore.CancelRow;
import com.community.payment.repository.master.PaymentEventStore;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.payment.repository.master.PaymentWebhookLogStore;
import com.community.payment.repository.master.PaymentWebhookLogStore.Result;
import com.community.payment.repository.master.ReconciliationIssueStore;
import com.community.payment.service.PaymentConfirmService.ConfirmResult;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 포트원 웹훅 처리(계약 「웹훅 처리 표」, commerce.md 8-4, 포트원 웹훅 버전 2024-04-25). 받은 웹훅은 결과와 상관없이 수신 기록에 한 줄 남는다(본문은 저장하지 않는다).
 *
 * <table>
 *   <tr><th>경우</th><th>응답</th><th>기록</th></tr>
 *   <tr><td>머리글 없음·서명 틀림·시각 오차 5분 초과</td><td>400 — 아무것도 바꾸지 않고 포트원 조회도 없음</td><td>REJECTED</td></tr>
 *   <tr><td>웹훅 비밀키가 없음(검증할 수 없다)</td><td>503 — 설정한 뒤 포트원 재전송을 받는다</td><td>RETRY</td></tr>
 *   <tr><td>같은 webhook-id 를 이미 처리</td><td>200</td><td>PROCESSED(이력 줄은 하나)</td></tr>
 *   <tr><td>우리 상점이 아님·우리 결제가 아님·모르는 이벤트·읽을 수 없는 본문</td><td>200</td><td>IGNORED</td></tr>
 *   <tr><td>{@code Transaction.Paid}</td><td>조회 후 확정 코드. 포트원이 아직 PAID 아님·포트원 장애 → 503</td><td>PROCESSED / RETRY</td></tr>
 *   <tr><td>{@code Transaction.Cancelled}·{@code Transaction.PartialCancelled}</td><td>조회해 포트원 취소 목록과 취소 대기열을 맞춤(취소 내역 아이디·우리 표시). 우리 것이 아닌 취소(콘솔 직접 취소)는
 *       상태 불변 + 대사 이슈 {@code CANCEL_MISMATCH}(P18)</td><td>PROCESSED</td></tr>
 * </table>
 * 본문은 믿지 않는다 — 결제 ID 와 이벤트 종류로 「무엇을 조회할지」만 정하고 판단은 포트원 조회 결과로 한다. 처리기 예외 같은 뜻밖의 오류는 500(포트원이 재전송한다).
 */
@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 결제 완료 이벤트 */
    public static final String PAID = "Transaction.Paid";
    /** 취소 이벤트(전액·부분) */
    public static final Set<String> CANCELLED = Set.of("Transaction.Cancelled", "Transaction.PartialCancelled");

    /** 웹훅 요청(머리글 셋 + 원본 본문) */
    public record WebhookRequest(String webhookId, String webhookTimestamp, String webhookSignature, byte[] body) {
    }

    /** 처리 결과 — 컨트롤러가 이 HTTP 상태로 답한다 */
    public record WebhookOutcome(int httpStatus, Result result, String errorCode) {
    }

    private final PaymentGateway gateway;
    private final PaymentConfirmService confirm;
    private final PaymentCancelExecutor executor;
    private final PaymentOrderStore orders;
    private final PaymentCancelStore cancels;
    private final PaymentEventStore events;
    private final PaymentWebhookLogStore logs;
    private final ReconciliationIssueStore issues;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate separate;
    private final Clock clock;

    public PaymentWebhookService(PaymentGateway gateway, PaymentConfirmService confirm, PaymentCancelExecutor executor, PaymentOrderStore orders,
                                 PaymentCancelStore cancels, PaymentEventStore events, PaymentWebhookLogStore logs, ReconciliationIssueStore issues,
                                 SnowflakeIdGenerator ids, PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
        this.gateway = gateway;
        this.confirm = confirm;
        this.executor = executor;
        this.orders = orders;
        this.cancels = cancels;
        this.events = events;
        this.logs = logs;
        this.issues = issues;
        this.ids = ids;
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 본문이 한도({@value #MAX_BODY_BYTES}바이트)를 넘었다 — 읽지 않고 400 REJECTED(결정 5) */
    public WebhookOutcome rejectOversized() {
        return record(null, null, null, Result.REJECTED, 400, "BODY_TOO_LARGE", clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    /** 웹훅 본문 한도(결정 5 — 64KB) */
    public static final int MAX_BODY_BYTES = 64 * 1024;

    /** 웹훅 하나를 처리한다 — 트랜잭션 밖에서 부른다. 던지지 않는다(결과의 HTTP 상태로 답한다) */
    public WebhookOutcome handle(WebhookRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        WebhookVerification verified = gateway.verifyWebhook(request.webhookId(), request.webhookTimestamp(), request.webhookSignature(), request.body(), now);
        if (verified == WebhookVerification.NOT_CONFIGURED) {
            return record(request.webhookId(), null, null, Result.RETRY, 503, "WEBHOOK_SECRET_MISSING", now);
        }
        if (verified != WebhookVerification.OK) {
            log.warn("웹훅 서명 검증 실패 result={}", verified);
            return record(null, null, null, Result.REJECTED, 400, verified.name(), now); // 검증 안 된 머리글 값은 남기지 않는다
        }
        Body body = parse(request.body());
        if (body == null) {
            return record(request.webhookId(), null, null, Result.IGNORED, 200, "UNREADABLE", now);
        }
        if (events.webhookProcessed(request.webhookId())) {
            return record(request.webhookId(), body.type(), body.paymentId(), Result.PROCESSED, 200, "DUPLICATE", now);
        }
        try {
            if (!gateway.ownsStore(body.storeId())) {
                return record(request.webhookId(), body.type(), body.paymentId(), Result.IGNORED, 200, "FOREIGN_STORE", now);
            }
            if (!PAID.equals(body.type()) && !CANCELLED.contains(body.type())) {
                return record(request.webhookId(), body.type(), body.paymentId(), Result.IGNORED, 200, "UNKNOWN_EVENT", now);
            }
            Optional<OrderRow> order = body.paymentId() == null ? Optional.empty() : orders.findByPaymentId(body.paymentId());
            if (order.isEmpty()) {
                return record(request.webhookId(), body.type(), body.paymentId(), Result.IGNORED, 200, "UNKNOWN_PAYMENT", now);
            }
            if (PAID.equals(body.type())) {
                ConfirmResult result = confirm.confirm(body.paymentId(), ConfirmedVia.WEBHOOK);
                if (result.outcome() == PaymentConfirmService.Outcome.NOT_PAID) {
                    return record(request.webhookId(), body.type(), body.paymentId(), Result.RETRY, 503, "NOT_PAID", now);
                }
            } else {
                syncCancellations(order.get(), now);
            }
            events.insertWebhook(ids.nextId(), order.get().id(), request.webhookId(), Map.of("type", body.type()), now);
            return record(request.webhookId(), body.type(), body.paymentId(), Result.PROCESSED, 200, null, now);
        } catch (PaymentGatewayUnavailableException | PaymentNotConfiguredException e) {
            return record(request.webhookId(), body.type(), body.paymentId(), Result.RETRY, 503, e.getErrorCode(), now);
        } catch (RuntimeException e) {
            log.error("웹훅 처리가 실패했다 type={} 원인={}", body.type(), e.getClass().getSimpleName());
            return record(request.webhookId(), body.type(), body.paymentId(), Result.RETRY, 500, "INTERNAL", now);
        }
    }

    /**
     * 포트원 취소 목록을 취소 대기열과 맞춘다 — 성공한 포트원 취소마다: 우리 줄(취소 내역 아이디 또는 사유의 우리 표시)이 있으면 끝나지 않았을 때 성공으로 맞추고,
     * 없으면 우리 것이 아닌 취소(콘솔 직접 취소)라 상태를 바꾸지 않고 이력 + 대사 이슈를 남긴다(P18 — 자동 보정 없음).
     */
    void syncCancellations(OrderRow order, Instant now) {
        PgPayment pg = gateway.getPayment(order.paymentId());
        for (PgCancellation cancellation : pg.cancellations()) {
            if (cancellation.status() != PgCancelStatus.SUCCEEDED) {
                continue;
            }
            Optional<CancelRow> ours = ours(order, cancellation);
            if (ours.isPresent()) {
                CancelRow row = ours.get();
                if (row.status() != CancelStatus.SUCCEEDED && row.status() != CancelStatus.MANUAL) {
                    executor.settleFromProvider(row, cancellation.id());
                }
                continue;
            }
            external(order, cancellation, now);
        }
    }

    private Optional<CancelRow> ours(OrderRow order, PgCancellation cancellation) {
        Optional<CancelRow> byId = cancellation.id() == null ? Optional.empty() : cancels.findByPgCancelId(cancellation.id());
        if (byId.isPresent()) {
            return byId;
        }
        Long tagged = PaymentCancelExecutor.cancelIdOf(cancellation.reason());
        return tagged == null ? Optional.empty() : cancels.findById(tagged).filter(row -> row.paymentOrderId() == order.id());
    }

    private void external(OrderRow order, PgCancellation cancellation, Instant now) {
        String pgCancelId = cancellation.id() == null ? "?" : cancellation.id();
        separate.executeWithoutResult(status -> {
            if (events.externalCancelRecorded(order.id(), pgCancelId)) {
                return;
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("pgCancelId", pgCancelId);
            detail.put("amount", cancellation.amount());
            events.insert(ids.nextId(), order.id(), PaymentEventStore.Kind.EXTERNAL_CANCEL, detail, now);
            issues.open(ids.nextId(), ReconciliationIssueStore.Type.CANCEL_MISMATCH, order.paymentId(), order.id(), ServiceTime.dateOf(now), detail, now);
            log.warn("우리 것이 아닌 취소(콘솔 직접 취소)를 받았다 — 상태는 그대로, 대사 이슈 paymentOrderId={}", order.id());
        });
    }

    private WebhookOutcome record(String webhookId, String type, String paymentId, Result result, int httpStatus, String errorCode, Instant now) {
        try {
            logs.insert(ids.nextId(), webhookId, type, paymentId, result, httpStatus, errorCode, now);
        } catch (RuntimeException e) {
            log.warn("웹훅 수신 기록을 남기지 못했다 원인={}", e.getClass().getSimpleName());
        }
        return new WebhookOutcome(httpStatus, result, errorCode);
    }

    /** 본문에서 쓰는 칸 — {@code type}·{@code data.paymentId}·{@code data.storeId}. 읽을 수 없으면 null */
    static Body parse(byte[] raw) {
        try {
            if (!(JSON.readValue(raw, Object.class) instanceof Map<?, ?> root) || !(root.get("type") instanceof String type)) {
                return null;
            }
            Map<?, ?> data = root.get("data") instanceof Map<?, ?> d ? d : Map.of();
            return new Body(type, data.get("paymentId") instanceof String p ? p : null, data.get("storeId") instanceof String s ? s : null);
        } catch (JacksonException e) {
            return null;
        }
    }

    record Body(String type, String paymentId, String storeId) {
    }
}
