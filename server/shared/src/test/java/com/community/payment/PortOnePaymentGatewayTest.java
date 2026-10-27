package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.community.payment.dto.PgCancelStatus;
import com.community.payment.exception.PaymentGatewayUnavailableException;
import com.community.payment.exception.PaymentNotConfiguredException;
import com.community.payment.gateway.PaymentGatewayKind;
import com.community.payment.gateway.PgCancelResult;
import com.community.payment.gateway.PgPayment;
import com.community.payment.gateway.PgPaymentPage;
import com.community.payment.gateway.PgPaymentStatus;
import com.community.payment.gateway.WebhookVerification;
import com.community.payment.gateway.portone.PortOnePaymentGateway;
import com.community.payment.gateway.portone.StandardWebhookVerifier;
import com.community.portone.dto.PortOnePaymentCredentials;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 포트원 V2 결제 게이트웨이의 요청 모양·응답 해석·오류(계약 「규칙 → 테스트」, 포트원 V2 OpenAPI 2026-10-26 확인) — HTTP 흉내({@link MockRestServiceServer}), 실제 포트원은 부르지 않는다.
 */
class PortOnePaymentGatewayTest {

    static final String STORE = "store-31f0c4a2";
    static final String SECRET = "api-secret-must-not-leak-91";
    static final String WEBHOOK_SECRET = "whsec_c2VjcmV0LXdlYmhvb2stbXVzdC1ub3QtbGVhaw==";
    static final String PAY = "pay_AbCdEfGhIjKlMnOpQrStUvWxYz012345";

    private final AtomicReference<Optional<PortOnePaymentCredentials>> settings = new AtomicReference<>();
    private MockRestServiceServer server;
    private PortOnePaymentGateway gateway;

    @BeforeEach
    void setUp() {
        settings.set(Optional.of(new PortOnePaymentCredentials(STORE, SECRET, "channel-key-pay", WEBHOOK_SECRET)));
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new PortOnePaymentGateway(settings::get, builder);
    }

    static String paid(String id, String store, long total) {
        return "{\"status\":\"PAID\",\"id\":\"" + id + "\",\"transactionId\":\"tx-1\",\"merchantId\":\"m-1\",\"storeId\":\"" + store + "\","
                + "\"method\":{\"type\":\"PaymentMethodCard\",\"card\":{\"name\":\"x\"}},\"channel\":{\"type\":\"LIVE\",\"id\":\"ch\",\"key\":\"k\",\"name\":\"n\",\"pgProvider\":\"INICIS_V2\",\"pgMerchantId\":\"pm\"},"
                + "\"version\":\"V2\",\"requestedAt\":\"2026-10-26T01:00:00Z\",\"updatedAt\":\"2026-10-26T01:00:05Z\",\"statusChangedAt\":\"2026-10-26T01:00:05Z\",\"orderName\":\"시험\","
                + "\"amount\":{\"total\":" + total + ",\"taxFree\":0,\"vat\":909,\"supply\":9091,\"discount\":0,\"paid\":" + total + ",\"cancelled\":0,\"cancelledTaxFree\":0},"
                + "\"currency\":\"KRW\",\"customer\":{\"name\":\"홍길동\",\"email\":\"kim@example.com\"},\"paidAt\":\"2026-10-26T01:00:05.123456Z\",\"pgTxId\":\"pg-tx-77\","
                + "\"receiptUrl\":\"https://receipt.example/77\",\"disputes\":[]}";
    }

    private void expectGet(String id, ResponseCreator response) {
        server.expect(requestTo("https://api.portone.io/payments/" + id + "?storeId=" + STORE)).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "PortOne " + SECRET)).andRespond(response);
    }

    @Test
    void 단건_조회는_상점_ID_와_인증_머리글로_부르고_쓰는_칸만_읽는다() {
        expectGet(PAY, withSuccess(paid(PAY, STORE, 10_000), MediaType.APPLICATION_JSON));
        PgPayment payment = gateway.getPayment(PAY);
        server.verify();
        assertThat(payment.status()).isEqualTo(PgPaymentStatus.PAID);
        assertThat(payment.paid()).isTrue();
        assertThat(payment.totalAmount()).isEqualTo(10_000);
        assertThat(payment.currency()).isEqualTo("KRW");
        assertThat(payment.paidAt()).isEqualTo(Instant.parse("2026-10-26T01:00:05.123456Z"));
        assertThat(payment.pgTxId()).isEqualTo("pg-tx-77");
        assertThat(payment.receiptUrl()).isEqualTo("https://receipt.example/77");
        assertThat(payment.payMethod()).isEqualTo("PaymentMethodCard");
        assertThat(payment.pgProvider()).isEqualTo("INICIS_V2");
        assertThat(payment.storeId()).isEqualTo(STORE);
        assertThat(payment.toString()).as("구매자 정보는 읽지 않는다").doesNotContain("kim@example.com").doesNotContain("홍길동");
    }

    @Test
    void 취소된_결제는_취소_내역을_읽는다() {
        String body = paid(PAY, STORE, 10_000).replace("\"status\":\"PAID\"", "\"status\":\"PARTIAL_CANCELLED\"").replace("\"cancelled\":0,", "\"cancelled\":3000,")
                .replace("\"disputes\":[]", "\"disputes\":[],\"cancellations\":[{\"status\":\"SUCCEEDED\",\"id\":\"c-1\",\"totalAmount\":3000,\"taxFreeAmount\":0,"
                        + "\"vatAmount\":0,\"reason\":\"roboyam REFUND rc:42\",\"requestedAt\":\"2026-10-26T02:00:00Z\",\"cancelledAt\":\"2026-10-26T02:00:01Z\"},"
                        + "{\"status\":\"REQUESTED\",\"id\":\"c-2\",\"totalAmount\":1000,\"taxFreeAmount\":0,\"vatAmount\":0,\"reason\":\"x\",\"requestedAt\":\"2026-10-26T03:00:00Z\"}]");
        expectGet(PAY, withSuccess(body, MediaType.APPLICATION_JSON));
        PgPayment payment = gateway.getPayment(PAY);
        assertThat(payment.status()).isEqualTo(PgPaymentStatus.PARTIAL_CANCELLED);
        assertThat(payment.paid()).as("확정은 PAID 만").isFalse();
        assertThat(payment.cancelledAmount()).isEqualTo(3_000);
        assertThat(payment.cancellations()).hasSize(2);
        assertThat(payment.cancellations().get(0).id()).isEqualTo("c-1");
        assertThat(payment.cancellations().get(0).status()).isEqualTo(PgCancelStatus.SUCCEEDED);
        assertThat(payment.cancellations().get(0).amount()).isEqualTo(3_000);
        assertThat(payment.cancellations().get(0).reason()).isEqualTo("roboyam REFUND rc:42");
        assertThat(payment.cancellations().get(1).status()).isEqualTo(PgCancelStatus.REQUESTED);
        assertThat(payment.cancellations().get(1).cancelledAt()).isNull();
    }

    @Test
    void 없는_결제는_없음이고_그_밖의_오류_연결_실패_모양이_틀림_남의_결제는_503() {
        expectGet(PAY, withStatus(HttpStatus.NOT_FOUND).body("{\"type\":\"PAYMENT_NOT_FOUND\",\"message\":\"x\"}").contentType(MediaType.APPLICATION_JSON));
        assertThat(gateway.getPayment(PAY).status()).isEqualTo(PgPaymentStatus.NOT_FOUND);

        for (ResponseCreator response : new ResponseCreator[] {
                withStatus(HttpStatus.UNAUTHORIZED).body("{\"type\":\"UNAUTHORIZED\"}").contentType(MediaType.APPLICATION_JSON),
                withStatus(HttpStatus.NOT_FOUND).body("<html>gateway</html>"),
                withServerError(),
                withSuccess("not json", MediaType.APPLICATION_JSON),
                withSuccess("{\"status\":\"PAID\"}", MediaType.APPLICATION_JSON),
                withSuccess(paid(PAY, "store-other", 10_000), MediaType.APPLICATION_JSON),
                withSuccess(paid("pay_other", STORE, 10_000), MediaType.APPLICATION_JSON),
                request -> {
                    throw new java.io.IOException("connection reset");
                }}) {
            server.reset();
            expectGet(PAY, response);
            assertThatThrownBy(() -> gateway.getPayment(PAY)).isInstanceOf(PaymentGatewayUnavailableException.class);
        }
    }

    private void expectCancel(ResponseCreator response) {
        server.expect(requestTo("https://api.portone.io/payments/" + PAY + "/cancel")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "PortOne " + SECRET))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.storeId").value(STORE))
                .andExpect(jsonPath("$.amount").value(3000))
                .andExpect(jsonPath("$.currentCancellableAmount").value(10000))
                .andExpect(jsonPath("$.reason").value("roboyam REFUND rc:7"))
                .andRespond(response);
    }

    private static String cancellation(String status, String id) {
        return "{\"cancellation\":{\"status\":\"" + status + "\",\"id\":\"" + id + "\",\"totalAmount\":3000,\"taxFreeAmount\":0,\"vatAmount\":0,\"reason\":\"r\","
                + "\"requestedAt\":\"2026-10-26T02:00:00Z\"}}";
    }

    @Test
    void 취소는_금액_취소_가능_잔액_사유를_보내고_응답의_취소_상태를_읽는다() {
        expectCancel(withSuccess(cancellation("SUCCEEDED", "c-9"), MediaType.APPLICATION_JSON));
        PgCancelResult done = gateway.cancel(PAY, 3_000, 10_000, "roboyam REFUND rc:7");
        server.verify();
        assertThat(done.succeeded()).isTrue();
        assertThat(done.cancellationId()).isEqualTo("c-9");

        server.reset();
        expectCancel(withSuccess(cancellation("REQUESTED", "c-10"), MediaType.APPLICATION_JSON));
        PgCancelResult requested = gateway.cancel(PAY, 3_000, 10_000, "roboyam REFUND rc:7");
        assertThat(requested.status()).as("접수만").isEqualTo(PgCancelStatus.REQUESTED);
        assertThat(requested.cancellationId()).isEqualTo("c-10");

        server.reset();
        expectCancel(withSuccess(cancellation("FAILED", "c-11"), MediaType.APPLICATION_JSON));
        assertThat(gateway.cancel(PAY, 3_000, 10_000, "roboyam REFUND rc:7").status()).isEqualTo(PgCancelStatus.FAILED);
    }

    @Test
    void 포트원이_거절한_취소는_실패와_오류_type_이고_5xx_연결_실패는_503() {
        expectCancel(withStatus(HttpStatus.CONFLICT).body("{\"type\":\"CANCELLABLE_AMOUNT_CONSISTENCY_BROKEN\",\"message\":\"m\"}").contentType(MediaType.APPLICATION_JSON));
        PgCancelResult rejected = gateway.cancel(PAY, 3_000, 10_000, "roboyam REFUND rc:7");
        assertThat(rejected.status()).isEqualTo(PgCancelStatus.FAILED);
        assertThat(rejected.errorType()).isEqualTo("CANCELLABLE_AMOUNT_CONSISTENCY_BROKEN");

        server.reset();
        expectCancel(withStatus(HttpStatus.BAD_GATEWAY).body("{\"type\":\"PG_PROVIDER\"}").contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.cancel(PAY, 3_000, 10_000, "roboyam REFUND rc:7")).isInstanceOf(PaymentGatewayUnavailableException.class);

        server.reset();
        expectCancel(request -> {
            throw new java.io.IOException("timeout");
        });
        assertThatThrownBy(() -> gateway.cancel(PAY, 3_000, 10_000, "roboyam REFUND rc:7")).isInstanceOf(PaymentGatewayUnavailableException.class);
    }

    @Test
    void 다건_조회는_requestBody_쿼리에_쪽과_상태_변경_시각_필터를_싣는다() {
        AtomicReference<String> captured = new AtomicReference<>();
        server.expect(request -> {
            assertThat(request.getURI().getPath()).isEqualTo("/payments");
            String raw = request.getURI().getRawQuery();
            assertThat(raw).startsWith("requestBody=");
            captured.set(URLDecoder.decode(raw.substring("requestBody=".length()), StandardCharsets.UTF_8));
        }).andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "PortOne " + SECRET))
                .andRespond(withSuccess("{\"items\":[" + paid(PAY, STORE, 5_000) + "],\"page\":{\"number\":2,\"size\":50,\"totalCount\":1}}", MediaType.APPLICATION_JSON));

        PgPaymentPage page = gateway.listPayments(Instant.parse("2026-10-25T15:00:00Z"), Instant.parse("2026-10-26T15:00:00Z"), 2, 50);

        Map<?, ?> body = JsonMapper.builder().build().readValue(captured.get(), Map.class);
        assertThat(body.get("page")).isEqualTo(Map.of("number", 2, "size", 50));
        Map<?, ?> filter = (Map<?, ?>) body.get("filter");
        assertThat(filter.get("storeId")).isEqualTo(STORE);
        assertThat(filter.get("timestampType")).isEqualTo("STATUS_CHANGED_AT");
        assertThat(filter.get("from")).isEqualTo("2026-10-25T15:00:00Z");
        assertThat(filter.get("until")).isEqualTo("2026-10-26T15:00:00Z");
        assertThat(page.items()).singleElement().satisfies(p -> assertThat(p.totalAmount()).isEqualTo(5_000));
        assertThat(page.number()).isEqualTo(2);
        assertThat(page.totalCount()).isEqualTo(1);
    }

    @Test
    void 설정이_비면_포트원을_부르지_않고_503_PAYMENT_NOT_CONFIGURED_준비_안_됨() {
        settings.set(Optional.empty());
        assertThat(gateway.ready()).isFalse();
        assertThatThrownBy(() -> gateway.getPayment(PAY)).isInstanceOf(PaymentNotConfiguredException.class);
        assertThatThrownBy(() -> gateway.cancel(PAY, 1, 1, "r")).isInstanceOf(PaymentNotConfiguredException.class);
        assertThatThrownBy(() -> gateway.checkoutInfo()).isInstanceOf(PaymentNotConfiguredException.class);
        assertThat(gateway.verifyWebhook("msg_1", "1", "v1,x", new byte[0], Instant.now())).isEqualTo(WebhookVerification.NOT_CONFIGURED);
        server.verify(); // 요청 없음
    }

    @Test
    void 결제창_정보는_상점_ID_와_결제_채널_키뿐이고_웹훅은_설정의_비밀키로_검증한다() {
        assertThat(gateway.kind()).isEqualTo(PaymentGatewayKind.PORTONE);
        assertThat(gateway.ready()).isTrue();
        assertThat(gateway.checkoutInfo().storeId()).isEqualTo(STORE);
        assertThat(gateway.checkoutInfo().channelKey()).isEqualTo("channel-key-pay");
        assertThat(gateway.checkoutInfo().toString()).doesNotContain(SECRET);
        assertThat(gateway.ownsStore(STORE)).isTrue();
        assertThat(gateway.ownsStore("store-other")).isFalse();
        assertThat(PortOnePaymentGateway.BASE_URL).isEqualTo("https://api.portone.io");
        byte[] body = "{\"type\":\"Transaction.Paid\"}".getBytes(StandardCharsets.UTF_8);
        Instant now = Instant.parse("2026-10-26T00:00:00Z");
        String timestamp = Long.toString(now.getEpochSecond());
        String signature = StandardWebhookVerifier.signature(WEBHOOK_SECRET, "msg_1", timestamp, body);
        assertThat(gateway.verifyWebhook("msg_1", timestamp, signature, body, now)).isEqualTo(WebhookVerification.OK);
        assertThat(gateway.verifyWebhook("msg_1", timestamp, StandardWebhookVerifier.signature("whsec_b3RoZXI=", "msg_1", timestamp, body), body, now))
                .isEqualTo(WebhookVerification.BAD_SIGNATURE);
    }
}
