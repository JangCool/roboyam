package com.community.payment.service;

import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.dto.ManualCancelMethod;
import com.community.payment.dto.request.PaymentManualCancelRequest;
import com.community.payment.dto.request.PaymentReasonRequest;
import com.community.payment.dto.response.PaymentAnomalyPageView;
import com.community.payment.dto.response.PaymentCancelView;
import com.community.payment.dto.response.PaymentOrderDetailView;
import com.community.payment.dto.response.PaymentOrderRowView;
import com.community.payment.dto.response.PaymentPageView;
import com.community.payment.dto.response.PaymentRecheckView;
import com.community.payment.dto.response.PaymentWebhookRowView;
import com.community.payment.dto.response.ReconcileDayView;
import com.community.payment.dto.response.ReconcileIssueView;
import com.community.payment.dto.response.ReconcileOverviewView;
import com.community.payment.exception.PaymentCancelNotFoundException;
import com.community.payment.exception.PaymentOrderNotFoundException;
import com.community.payment.exception.ReconcileIssueNotFoundException;
import com.community.payment.repository.master.PaymentAdminQueries.AnomalyQuery;
import com.community.payment.repository.master.PaymentAdminQueries.IssueQuery;
import com.community.payment.repository.master.PaymentAdminQueries.OrderQuery;
import com.community.payment.repository.master.PaymentAdminQueries.WebhookQuery;
import com.community.payment.repository.master.PaymentWebhookLogStore;
import com.community.payment.repository.master.ReconciliationIssueStore;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * 관리자 결제 API 의 입력 읽기 — 질의 글자를 읽어 거르기로 바꾸고(못 읽으면 400 {@code fields.<이름>}), 경로 ID 를 읽고(못 읽으면 404), shared 의
 * {@link PaymentAdminReadService}·{@link PaymentAdminActionService} 를 부른다. 업무 규칙은 shared 에 있다(포인트 상점 관리자 API 와 같은 모양).
 */
@Service
public class PaymentAdminService {

    /** 한 쪽 기본 크기(화면 첫 쪽과 같다) */
    public static final int DEFAULT_SIZE = 20;
    /** 한 쪽 최대 크기 */
    public static final int SIZE_MAX = 100;

    private final PaymentAdminReadService read;
    private final PaymentAdminActionService actions;

    public PaymentAdminService(PaymentAdminReadService read, PaymentAdminActionService actions) {
        this.read = read;
        this.actions = actions;
    }

    // ── 결제 주문 ───────────────────────────────────────────────────────────

    public PaymentPageView<PaymentOrderRowView> orders(String status, String anomaly, String orderType, String from, String to, String q, String page, String size) {
        Map<String, MessageRef> bad = new LinkedHashMap<>();
        PaymentStatus statusValue = parse("status", status, PaymentStatus::valueOf, bad);
        boolean anyAnomaly = "ANY".equals(anomaly == null ? null : anomaly.strip());
        PaymentAnomaly anomalyValue = anyAnomaly ? null : parse("anomaly", anomaly, PaymentAnomaly::valueOf, bad);
        PaymentOrderType typeValue = parse("orderType", orderType, PaymentOrderType::valueOf, bad);
        Instant fromAt = parse("from", from, Instant::parse, bad);
        Instant toAt = parse("to", to, Instant::parse, bad);
        int[] paging = paging(page, size, bad);
        throwIfBad(bad);
        return read.orders(new OrderQuery(statusValue, anomalyValue, anyAnomaly, typeValue, fromAt, toAt, blankToNull(q), paging[0], paging[1]));
    }

    public PaymentOrderDetailView order(AdminActor actor, String id) {
        return read.open(idOr(id, PaymentOrderNotFoundException::new), actor.operatorId(), actor.ip());
    }

    public PaymentRecheckView recheck(AdminActor actor, String id, PaymentReasonRequest body) {
        return actions.recheck(idOr(id, PaymentOrderNotFoundException::new), reason(body), actor.operatorId(), actor.ip());
    }

    // ── 웹훅 ────────────────────────────────────────────────────────────────

    public PaymentPageView<PaymentWebhookRowView> webhooks(String result, String from, String to, String q, String page, String size) {
        Map<String, MessageRef> bad = new LinkedHashMap<>();
        PaymentWebhookLogStore.Result resultValue = parse("result", result, PaymentWebhookLogStore.Result::valueOf, bad);
        Instant fromAt = parse("from", from, Instant::parse, bad);
        Instant toAt = parse("to", to, Instant::parse, bad);
        int[] paging = paging(page, size, bad);
        throwIfBad(bad);
        return read.webhooks(new WebhookQuery(resultValue == null ? null : resultValue.name(), fromAt, toAt, blankToNull(q), paging[0], paging[1]));
    }

    // ── 결제 이상 ───────────────────────────────────────────────────────────

    public PaymentAnomalyPageView anomalies(String kind, String includeResolved, String from, String to, String page, String size) {
        Map<String, MessageRef> bad = new LinkedHashMap<>();
        String kindValue = parse("kind", kind, value -> {
            if (!"CANCEL_GAVE_UP".equals(value)) {
                PaymentAnomaly.valueOf(value);
            }
            return value;
        }, bad);
        Boolean resolved = parse("includeResolved", includeResolved, value -> switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(value);
        }, bad);
        Instant fromAt = parse("from", from, Instant::parse, bad);
        Instant toAt = parse("to", to, Instant::parse, bad);
        int[] paging = paging(page, size, bad);
        throwIfBad(bad);
        return read.anomalies(new AnomalyQuery(kindValue, Boolean.TRUE.equals(resolved), fromAt, toAt, paging[0], paging[1]));
    }

    public PaymentCancelView retryCancel(AdminActor actor, String id, PaymentReasonRequest body) {
        return actions.retryCancel(idOr(id, PaymentCancelNotFoundException::new), reason(body), actor.operatorId(), actor.ip());
    }

    public PaymentCancelView manualCancel(AdminActor actor, String id, PaymentManualCancelRequest body) {
        long cancelId = idOr(id, PaymentCancelNotFoundException::new);
        String reason = body == null ? null : body.reason();
        String method = body == null ? null : blankToNull(body.method());
        if (method == null) {
            return actions.manualCancel(cancelId, reason, actor.operatorId(), actor.ip());
        }
        ManualCancelMethod m;
        try {
            m = ManualCancelMethod.valueOf(method);
        } catch (IllegalArgumentException e) {
            throw ValidationException.of("method", new MessageRef("payment.manual.method.invalid"));
        }
        String pgCancelId = blankToNull(body.pgCancelId());
        if (m == ManualCancelMethod.CONSOLE_CANCEL && (pgCancelId == null || pgCancelId.length() > 100)) {
            throw ValidationException.of("pgCancelId", new MessageRef("payment.manual.pgCancelId.required"));
        }
        return actions.manualCancel(cancelId, reason, m, m == ManualCancelMethod.CONSOLE_CANCEL ? pgCancelId : null, actor.operatorId(), actor.ip());
    }

    // ── 대사 ────────────────────────────────────────────────────────────────

    public ReconcileOverviewView reconciliation() {
        return read.overview();
    }

    public PaymentPageView<ReconcileIssueView> issues(String type, String status, String day, String page, String size) {
        Map<String, MessageRef> bad = new LinkedHashMap<>();
        ReconciliationIssueStore.Type typeValue = parse("type", type, ReconciliationIssueStore.Type::valueOf, bad);
        String statusValue = parse("status", status, value -> {
            if (!"OPEN".equals(value) && !"RESOLVED".equals(value)) {
                throw new IllegalArgumentException(value);
            }
            return value;
        }, bad);
        LocalDate dayValue = parse("day", day, LocalDate::parse, bad);
        int[] paging = paging(page, size, bad);
        throwIfBad(bad);
        return read.issues(new IssueQuery(typeValue == null ? null : typeValue.name(), statusValue, dayValue, paging[0], paging[1]));
    }

    public ReconcileIssueView resolveIssue(AdminActor actor, String id, PaymentReasonRequest body) {
        return actions.resolveIssue(idOr(id, ReconcileIssueNotFoundException::new), reason(body), actor.operatorId(), actor.ip());
    }

    /** 그 날 다시 대사 — 날짜를 못 읽으면 400 {@code fields.day} */
    public ReconcileDayView rerunDay(AdminActor actor, String day) {
        Map<String, MessageRef> bad = new LinkedHashMap<>();
        LocalDate value = parse("day", day, LocalDate::parse, bad);
        if (value == null && bad.isEmpty()) {
            bad.put("day", new MessageRef("payment.query.invalid"));
        }
        throwIfBad(bad);
        return actions.rerunDay(value, actor.operatorId(), actor.ip());
    }

    // ── 읽기 도우미 ─────────────────────────────────────────────────────────

    private static String reason(PaymentReasonRequest body) {
        return body == null ? null : body.reason();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** 숫자 ID — 못 읽으면 그 404 */
    private static long idOr(String raw, java.util.function.Supplier<? extends RuntimeException> notFound) {
        if (raw == null || !raw.matches("\\d{1,19}")) {
            throw notFound.get();
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw notFound.get();
        }
    }

    /** 쪽 번호(0부터)·크기(1~{@value #SIZE_MAX}, 기본 {@value #DEFAULT_SIZE}) */
    private static int[] paging(String page, String size, Map<String, MessageRef> bad) {
        Integer pageValue = parse("page", page, Integer::valueOf, bad);
        if (pageValue != null && pageValue < 0) {
            bad.put("page", new MessageRef("payment.query.invalid"));
        }
        Integer sizeValue = parse("size", size, Integer::valueOf, bad);
        if (bad.containsKey("size") || sizeValue != null && (sizeValue < 1 || sizeValue > SIZE_MAX)) {
            bad.put("size", new MessageRef("payment.query.size.range", 1, SIZE_MAX));
        }
        return new int[] {pageValue == null || pageValue < 0 ? 0 : pageValue, sizeValue == null ? DEFAULT_SIZE : sizeValue};
    }

    /** 질의 글자 하나 — 비었으면 null, 못 읽으면 {@code bad} 에 담고 null */
    private static <T> T parse(String name, String raw, Function<String, T> reader, Map<String, MessageRef> bad) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return reader.apply(raw.strip());
        } catch (RuntimeException e) {
            bad.put(name, new MessageRef("payment.query.invalid"));
            return null;
        }
    }

    private static void throwIfBad(Map<String, MessageRef> bad) {
        if (!bad.isEmpty()) {
            throw new ValidationException(bad);
        }
    }
}
