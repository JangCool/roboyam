package com.community.payment.gateway.portone;

import com.community.payment.dto.PgCancelStatus;
import com.community.payment.gateway.PgCancelResult;
import com.community.payment.gateway.PgCancellation;
import com.community.payment.gateway.PgPayment;
import com.community.payment.gateway.PgPaymentPage;
import com.community.payment.gateway.PgPaymentStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 포트원 V2 REST 응답 → 결제 모듈 값. 칸 이름은 포트원 V2 OpenAPI(2026-10-26 내려받아 확인 — {@code Payment}·{@code PaymentAmount}·{@code PaymentCancellation}·
 * {@code CancelPaymentResponse}·{@code GetPaymentsResponse})를 따른다. 읽지 못하면 {@link IllegalArgumentException}(부르는 쪽이 503 으로 바꾼다) — 본문은 예외 문구에 싣지 않는다.
 */
public final class PortOnePaymentParser {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private PortOnePaymentParser() {
    }

    /** {@code GET /payments/{paymentId}} 의 200 본문 */
    public static PgPayment payment(String body) {
        return payment(object(read(body)));
    }

    /** {@code POST /payments/{paymentId}/cancel} 의 200 본문 → 취소 내역 상태·아이디 */
    public static PgCancelResult cancel(String body) {
        Map<?, ?> cancellation = object(object(read(body)).get("cancellation"));
        PgCancelStatus status = cancelStatus(cancellation.get("status"));
        String id = text(cancellation.get("id"));
        return switch (status) {
            case SUCCEEDED -> PgCancelResult.succeeded(id);
            case REQUESTED -> PgCancelResult.requested(id);
            case FAILED -> PgCancelResult.failed(id, "CANCELLATION_FAILED");
        };
    }

    /** {@code GET /payments} 의 200 본문 */
    public static PgPaymentPage page(String body) {
        Map<?, ?> root = object(read(body));
        List<PgPayment> items = new ArrayList<>();
        if (root.get("items") instanceof List<?> list) {
            for (Object item : list) {
                items.add(payment(object(item)));
            }
        }
        Map<?, ?> page = root.get("page") instanceof Map<?, ?> p ? p : Map.of();
        return new PgPaymentPage(items, number(page.get("number")).intValue(), number(page.get("size")).intValue(), number(page.get("totalCount")).intValue());
    }

    /** 포트원 오류 본문의 {@code type}(예 {@code PAYMENT_NOT_FOUND}) — 읽지 못하면 "?" */
    public static String errorType(String body) {
        try {
            Object type = MAPPER.readValue(body, Map.class).get("type");
            return type instanceof String s && s.matches("[A-Z_]{1,80}") ? s : "?";
        } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
            return "?";
        }
    }

    static PgPayment payment(Map<?, ?> root) {
        String id = text(root.get("id"));
        if (id == null) {
            throw new IllegalArgumentException("결제 건 아이디가 없다");
        }
        Map<?, ?> amount = root.get("amount") instanceof Map<?, ?> a ? a : Map.of();
        Map<?, ?> method = root.get("method") instanceof Map<?, ?> m ? m : Map.of();
        Map<?, ?> channel = root.get("channel") instanceof Map<?, ?> c ? c : Map.of();
        List<PgCancellation> cancellations = new ArrayList<>();
        if (root.get("cancellations") instanceof List<?> list) {
            for (Object item : list) {
                Map<?, ?> cancellation = object(item);
                cancellations.add(new PgCancellation(text(cancellation.get("id")), cancelStatus(cancellation.get("status")),
                        number(cancellation.get("totalAmount")).longValue(), text(cancellation.get("reason")), time(cancellation.get("cancelledAt"))));
            }
        }
        return new PgPayment(id, text(root.get("storeId")), PgPaymentStatus.parse(text(root.get("status"))), number(amount.get("total")).longValue(),
                number(amount.get("cancelled")).longValue(), text(root.get("currency")), time(root.get("paidAt")), text(root.get("pgTxId")), text(root.get("receiptUrl")),
                text(method.get("type")), text(channel.get("pgProvider")), cancellations, time(root.get("statusChangedAt")));
    }

    private static Object read(String body) {
        try {
            return MAPPER.readValue(body, Object.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("포트원 응답을 읽지 못했다");
        }
    }

    private static Map<?, ?> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException("포트원 응답의 모양이 틀렸다");
    }

    private static PgCancelStatus cancelStatus(Object value) {
        try {
            return PgCancelStatus.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("모르는 취소 상태다");
        }
    }

    private static Number number(Object value) {
        return value instanceof Number n ? n : 0L;
    }

    private static String text(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    private static Instant time(Object value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("포트원 응답의 시각을 읽지 못했다");
        }
    }
}
