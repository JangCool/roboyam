package com.community.payment.gateway.portone;

import com.community.payment.exception.PaymentGatewayUnavailableException;
import com.community.payment.exception.PaymentNotConfiguredException;
import com.community.payment.gateway.EscrowLogistics;
import com.community.payment.gateway.EscrowResult;
import com.community.payment.gateway.PaymentCheckoutInfo;
import com.community.payment.gateway.PaymentGateway;
import com.community.payment.gateway.PaymentGatewayKind;
import com.community.payment.gateway.PgCancelResult;
import com.community.payment.gateway.PgPayment;
import com.community.payment.gateway.PgPaymentPage;
import com.community.payment.gateway.WebhookVerification;
import com.community.portone.dto.PortOnePaymentCredentials;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 포트원 V2 결제 게이트웨이 — 요청 모양은 포트원 V2 OpenAPI(2026-10-26 확인)를 따른다. 주소 {@value #BASE_URL}, 인증 머리글 {@code Authorization: PortOne <API 비밀키>}.
 *
 * <ul>
 *   <li>결제 단건 조회 {@code GET /payments/{paymentId}?storeId=<상점 ID>} — 404 {@code PAYMENT_NOT_FOUND} 는 「없음」, 그 밖의 오류·연결 실패는 503.
 *       응답의 결제 ID·상점 ID 가 우리 것이 아니면 503(설정이 섞였다).</li>
 *   <li>결제 취소 {@code POST /payments/{paymentId}/cancel} {@code {storeId, amount, currentCancellableAmount, reason}} — 200 이면 {@code cancellation.status}
 *       (SUCCEEDED·REQUESTED·FAILED), 4xx 는 거절(오류 {@code type} 만 남김), 5xx·연결 실패는 503.</li>
 *   <li>결제 다건 조회 {@code GET /payments?requestBody=<JSON>} — 본문을 쿼리 {@code requestBody} 에 JSON 으로 싣는다(OpenAPI 의 쿼리 매개변수 정의).
 *       {@code {page:{number,size}, filter:{storeId, timestampType:"STATUS_CHANGED_AT", from, until}}}.</li>
 *   <li>웹훅 서명은 {@link StandardWebhookVerifier}(웹훅 비밀키).</li>
 * </ul>
 * 설정(상점 ID·API 비밀키·결제 채널 키·웹훅 비밀키)은 요청마다 DB 에서 읽는다(관리자 화면에서 바꾸면 바로 반영). 비었으면 503 {@code PAYMENT_NOT_CONFIGURED}.
 * 로그에는 HTTP 상태와 포트원 오류 {@code type} 만 — 응답 본문·비밀키를 남기지 않는다.
 */
public class PortOnePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(PortOnePaymentGateway.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 포트원 V2 API 주소 — 코드에 고정(운영자가 바꾸는 값이 아니다) */
    public static final String BASE_URL = "https://api.portone.io";

    private final Supplier<Optional<PortOnePaymentCredentials>> settings;
    private final RestClient http;

    /**
     * @param settings 포트원 결제 설정 값(요청마다 부른다 — 보통 {@code PortOneSettingsService#paymentCredentials})
     * @param http     요청 도구(제한 시간·리다이렉트 안 따라감은 만드는 쪽이 정한다)
     */
    public PortOnePaymentGateway(Supplier<Optional<PortOnePaymentCredentials>> settings, RestClient.Builder http) {
        this.settings = settings;
        this.http = http.baseUrl(BASE_URL).build();
    }

    @Override
    public PaymentGatewayKind kind() {
        return PaymentGatewayKind.PORTONE;
    }

    @Override
    public boolean ready() {
        try {
            return settings.get().isPresent();
        } catch (RuntimeException e) {
            return false; // 암호화 키가 없다 — 준비되지 않음
        }
    }

    @Override
    public PaymentCheckoutInfo checkoutInfo() {
        PortOnePaymentCredentials credentials = credentials();
        return new PaymentCheckoutInfo(PaymentGatewayKind.PORTONE, credentials.storeId(), credentials.channelKey());
    }

    @Override
    public boolean ownsStore(String storeId) {
        return storeId != null && storeId.equals(credentials().storeId());
    }

    @Override
    public PgPayment getPayment(String paymentId) {
        PortOnePaymentCredentials credentials = credentials();
        Response response = exchange("결제 조회", () -> http.get()
                .uri(uri -> uri.path("/payments/{id}").queryParam("storeId", credentials.storeId()).build(paymentId))
                .header(HttpHeaders.AUTHORIZATION, "PortOne " + credentials.apiSecret())
                .accept(MediaType.APPLICATION_JSON)
                .exchange((request, raw) -> new Response(raw.getStatusCode().value(), new String(raw.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        if (response.status() == 404 && "PAYMENT_NOT_FOUND".equals(PortOnePaymentParser.errorType(response.body()))) {
            return PgPayment.notFound(paymentId);
        }
        if (response.status() != 200) {
            log.warn("포트원 결제 조회 실패 status={} type={}", response.status(), PortOnePaymentParser.errorType(response.body()));
            throw new PaymentGatewayUnavailableException();
        }
        PgPayment payment = parse(() -> PortOnePaymentParser.payment(response.body()));
        if (!paymentId.equals(payment.paymentId()) || !credentials.storeId().equals(payment.storeId())) {
            log.error("포트원 결제 조회 응답이 우리 결제가 아니다 — 상점 ID·API 비밀키 설정을 확인");
            throw new PaymentGatewayUnavailableException();
        }
        return payment;
    }

    @Override
    public PgCancelResult cancel(String paymentId, long amount, long currentCancellableAmount, String reason) {
        PortOnePaymentCredentials credentials = credentials();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("storeId", credentials.storeId());
        body.put("amount", amount);
        body.put("currentCancellableAmount", currentCancellableAmount);
        body.put("reason", reason);
        String json = JSON.writeValueAsString(body);
        Response response = exchange("결제 취소", () -> http.post()
                .uri("/payments/{id}/cancel", paymentId)
                .header(HttpHeaders.AUTHORIZATION, "PortOne " + credentials.apiSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange((request, raw) -> new Response(raw.getStatusCode().value(), new String(raw.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        if (response.status() == 200) {
            return parse(() -> PortOnePaymentParser.cancel(response.body()));
        }
        String type = PortOnePaymentParser.errorType(response.body());
        if (response.status() >= 400 && response.status() < 500) {
            log.warn("포트원 결제 취소 거절 status={} type={}", response.status(), type);
            return PgCancelResult.failed(null, type);
        }
        log.warn("포트원 결제 취소 실패 status={} type={}", response.status(), type);
        throw new PaymentGatewayUnavailableException();
    }

    @Override
    public EscrowResult registerEscrowLogistics(String paymentId, EscrowLogistics logistics, boolean update) {
        PortOnePaymentCredentials credentials = credentials();
        if (!escrowPayment(paymentId, credentials)) {
            return EscrowResult.notEscrow();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("storeId", credentials.storeId());
        body.put("logistics", Map.of("company", logistics.carrierCode(), "invoiceNumber", logistics.trackingNo(), "sentAt", logistics.sentAt().toString()));
        String json = JSON.writeValueAsString(body);
        Response response = exchange("에스크로 배송 정보", () -> (update ? http.patch() : http.post())
                .uri("/payments/{id}/escrow/logistics", paymentId)
                .header(HttpHeaders.AUTHORIZATION, "PortOne " + credentials.apiSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange((request, raw) -> new Response(raw.getStatusCode().value(), new String(raw.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        return escrowResult("에스크로 배송 정보", response);
    }

    @Override
    public EscrowResult completeEscrow(String paymentId) {
        PortOnePaymentCredentials credentials = credentials();
        if (!escrowPayment(paymentId, credentials)) {
            return EscrowResult.notEscrow();
        }
        String json = JSON.writeValueAsString(Map.of("storeId", credentials.storeId(), "fromStore", true));
        Response response = exchange("에스크로 구매 확정", () -> http.post()
                .uri("/payments/{id}/escrow/complete", paymentId)
                .header(HttpHeaders.AUTHORIZATION, "PortOne " + credentials.apiSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange((request, raw) -> new Response(raw.getStatusCode().value(), new String(raw.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        return escrowResult("에스크로 구매 확정", response);
    }

    /** 결제 단건 조회 본문에 {@code escrow} 가 있으면 에스크로 결제다(포트원 V2 — 실제 응답 모양은 2b-5 에서 확인) */
    private boolean escrowPayment(String paymentId, PortOnePaymentCredentials credentials) {
        Response response = exchange("결제 조회", () -> http.get()
                .uri(uri -> uri.path("/payments/{id}").queryParam("storeId", credentials.storeId()).build(paymentId))
                .header(HttpHeaders.AUTHORIZATION, "PortOne " + credentials.apiSecret())
                .accept(MediaType.APPLICATION_JSON)
                .exchange((request, raw) -> new Response(raw.getStatusCode().value(), new String(raw.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        if (response.status() != 200) {
            log.warn("포트원 결제 조회 실패 status={} type={}", response.status(), PortOnePaymentParser.errorType(response.body()));
            throw new PaymentGatewayUnavailableException();
        }
        return parse(() -> {
            var escrow = JSON.readTree(response.body()).get("escrow");
            return escrow != null && !escrow.isNull();
        });
    }

    /** 200 → 마침, 4xx 중 「이미」(ALREADY…) → 마침, 그 밖 4xx → 거절(재시도), 5xx → 503 */
    private static EscrowResult escrowResult(String what, Response response) {
        if (response.status() == 200) {
            return EscrowResult.done();
        }
        String type = PortOnePaymentParser.errorType(response.body());
        if (response.status() >= 400 && response.status() < 500) {
            if (type != null && type.contains("ALREADY")) {
                return EscrowResult.done();
            }
            log.warn("포트원 {} 거절 status={} type={}", what, response.status(), type);
            return EscrowResult.failed(type);
        }
        log.warn("포트원 {} 실패 status={} type={}", what, response.status(), type);
        throw new PaymentGatewayUnavailableException();
    }

    @Override
    public PgPaymentPage listPayments(Instant from, Instant until, int pageNumber, int pageSize) {
        PortOnePaymentCredentials credentials = credentials();
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("storeId", credentials.storeId());
        filter.put("timestampType", "STATUS_CHANGED_AT");
        filter.put("from", from.toString());
        filter.put("until", until.toString());
        String requestBody = JSON.writeValueAsString(Map.of("page", Map.of("number", pageNumber, "size", pageSize), "filter", filter));
        Response response = exchange("결제 다건 조회", () -> http.get()
                .uri(uri -> uri.path("/payments").queryParam("requestBody", "{body}").build(requestBody))
                .header(HttpHeaders.AUTHORIZATION, "PortOne " + credentials.apiSecret())
                .accept(MediaType.APPLICATION_JSON)
                .exchange((request, raw) -> new Response(raw.getStatusCode().value(), new String(raw.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        if (response.status() != 200) {
            log.warn("포트원 결제 다건 조회 실패 status={} type={}", response.status(), PortOnePaymentParser.errorType(response.body()));
            throw new PaymentGatewayUnavailableException();
        }
        return parse(() -> PortOnePaymentParser.page(response.body()));
    }

    @Override
    public WebhookVerification verifyWebhook(String webhookId, String webhookTimestamp, String webhookSignature, byte[] body, Instant now) {
        Optional<PortOnePaymentCredentials> credentials;
        try {
            credentials = settings.get();
        } catch (RuntimeException e) {
            return WebhookVerification.NOT_CONFIGURED;
        }
        return StandardWebhookVerifier.verify(credentials.map(PortOnePaymentCredentials::webhookSecret).orElse(null), webhookId, webhookTimestamp, webhookSignature, body,
                now);
    }

    private PortOnePaymentCredentials credentials() {
        return settings.get().orElseThrow(PaymentNotConfiguredException::new);
    }

    private static Response exchange(String what, Supplier<Response> call) {
        try {
            Response response = call.get();
            if (response == null) {
                throw new PaymentGatewayUnavailableException();
            }
            return response;
        } catch (RestClientException e) {
            log.warn("포트원 {} 실패 — 연결 원인={}", what, e.getClass().getSimpleName());
            throw new PaymentGatewayUnavailableException();
        }
    }

    private static <T> T parse(Supplier<T> parse) {
        try {
            return parse.get();
        } catch (IllegalArgumentException e) {
            log.warn("포트원 응답을 해석하지 못했다 — {}", e.getMessage()); // 본문은 싣지 않는다
            throw new PaymentGatewayUnavailableException();
        }
    }

    private record Response(int status, String body) {
    }
}
