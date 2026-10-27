package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.commerce.CommerceApiTestBase;
import com.community.installation.service.InstallationState;
import com.community.payment.gateway.fake.FakePaymentGateway;
import com.community.payment.gateway.portone.StandardWebhookVerifier;
import com.community.user.entity.Role;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 결제 HTTP(계약 결정 5·6·7) — 공개 사이트 앱 문맥 전체(local 프로필 = 가짜 게이트웨이 + 시험 주문)를 실제 PostgreSQL 에 띄운다.
 *
 * <ul>
 *   <li>완료 API: 로그인 필요(401), 공통 래퍼·{@code no-store}, 409 {@code PAYMENT_NOT_PAID}·404·409 {@code PAYMENT_ORDER_MISMATCH}·400, <b>속도 제한·멱등 키 없음</b>(P24·12-4)</li>
 *   <li>웹훅: 로그인·CSRF 없이 받는다(보안 필터를 거쳐도 401·403 이 아니다), 서명이 틀리면 400, 맞으면 200, 본문 64KB 넘으면 400</li>
 *   <li>시험 주문 {@code /api/dev/payments}: 만들기 201·가짜 결제 204·상태</li>
 * </ul>
 */
class PaymentCompletionHttpTest extends CommerceApiTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private InstallationState installation;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilterChain;

    private MockMvc mvc;
    private MockMvc secured;

    @BeforeEach
    void setUpMvc() {
        installation.markCompleted();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        secured = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
    }

    private static MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).contentType(MediaType.APPLICATION_JSON);
    }

    /** 시험 주문을 만든다 — data */
    private JsonNode createTestOrder(long member, long amount) throws Exception {
        String body = mvc.perform(as(member, post("/api/dev/payments/test-orders")).content("{\"amount\":" + amount + ",\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.provider").value("FAKE"))
                .andExpect(jsonPath("$.data.amount").value(amount))
                .andReturn().getResponse().getContentAsString();
        return MAPPER.readTree(body).path("data");
    }

    private static String completeBody(JsonNode order) {
        return "{\"orderType\":\"PAYMENT_TEST\",\"orderRefId\":\"" + order.path("orderRefId").asString() + "\"}";
    }

    @Test
    void 완료_API_는_CSRF_와_로그인이_필요하다() throws Exception {
        String path = "/api/payments/pay_" + "a".repeat(32) + "/complete";
        secured.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        secured.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}").cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "csrf-test"))
                        .header("X-XSRF-TOKEN", "csrf-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 시험_주문_가짜_결제_완료가_공통_래퍼와_no_store_로_끝까지_된다() throws Exception {
        long member = newMember();
        JsonNode order = createTestOrder(member, 12_000);
        String paymentId = order.path("paymentId").asString();
        assertThat(paymentId).matches("pay_[A-Za-z0-9]{32}");

        mvc.perform(as(member, post("/api/payments/" + paymentId + "/complete")).content(completeBody(order)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_NOT_PAID"));

        mvc.perform(as(member, post("/api/dev/payments/" + paymentId + "/fake-pay")).content("{\"result\":\"PAID\"}")).andExpect(status().isNoContent());

        mvc.perform(as(member, post("/api/payments/" + paymentId + "/complete")).content(completeBody(order)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentId").value(paymentId))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.accepted").value(true))
                .andExpect(jsonPath("$.data.paidAmount").value(12_000))
                .andExpect(jsonPath("$.meta.requestId").exists());

        mvc.perform(as(member, get("/api/dev/payments/" + paymentId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.handlerCash").value(12_000));
    }

    @Test
    void 남의_결제는_404_다른_주문은_409_본문이_틀리면_400() throws Exception {
        long member = newMember();
        JsonNode order = createTestOrder(member, 5_000);
        String paymentId = order.path("paymentId").asString();
        mvc.perform(as(newMember(), post("/api/payments/" + paymentId + "/complete")).content(completeBody(order)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PAYMENT_ORDER_NOT_FOUND"));
        mvc.perform(as(member, post("/api/payments/" + paymentId + "/complete")).content("{\"orderType\":\"PAYMENT_TEST\",\"orderRefId\":\"123\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PAYMENT_ORDER_MISMATCH"));
        mvc.perform(as(member, post("/api/payments/" + paymentId + "/complete")).content("{\"orderType\":\"PAYMENT_TEST\",\"orderRefId\":\"12a\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
        mvc.perform(as(member, post("/api/dev/payments/test-orders")).content("{\"amount\":50}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.amount").exists());
    }

    @Test
    void 완료_API_에는_속도_제한도_멱등_키도_없다() throws Exception {
        long member = newMember();
        JsonNode order = createTestOrder(member, 3_000);
        String paymentId = order.path("paymentId").asString();
        mvc.perform(as(member, post("/api/dev/payments/" + paymentId + "/fake-pay")).content("{\"result\":\"PAID\"}")).andExpect(status().isNoContent());
        for (int i = 0; i < 15; i++) {
            mvc.perform(as(member, post("/api/payments/" + paymentId + "/complete")).content(completeBody(order))) // Idempotency-Key 없이
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accepted").value(true));
        }
    }

    private MockHttpServletRequestBuilder webhook(String body, String signatureOverride) {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        String id = "msg_" + UUID.randomUUID();
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        String signature = signatureOverride != null ? signatureOverride : StandardWebhookVerifier.signature(FakePaymentGateway.WEBHOOK_SECRET, id, timestamp, raw);
        return post("/api/payments/webhooks/portone").contentType(MediaType.APPLICATION_JSON).content(raw)
                .header("webhook-id", id).header("webhook-timestamp", timestamp).header("webhook-signature", signature);
    }

    @Test
    void 웹훅은_로그인_CSRF_없이_받고_서명이_틀리면_400_맞으면_200() throws Exception {
        long member = newMember();
        JsonNode order = createTestOrder(member, 4_000);
        String paymentId = order.path("paymentId").asString();
        mvc.perform(as(member, post("/api/dev/payments/" + paymentId + "/fake-pay")).content("{\"result\":\"PAID\"}")).andExpect(status().isNoContent());
        String body = "{\"type\":\"Transaction.Paid\",\"timestamp\":\"2026-10-26T00:00:00Z\",\"data\":{\"storeId\":\"" + FakePaymentGateway.STORE_ID
                + "\",\"paymentId\":\"" + paymentId + "\",\"transactionId\":\"tx\"}}";

        secured.perform(webhook(body, "v1,Zm9yZ2Vk")).andExpect(status().isBadRequest());
        secured.perform(webhook(body, null)).andExpect(status().isOk());

        mvc.perform(as(member, get("/api/dev/payments/" + paymentId))).andExpect(jsonPath("$.data.status").value("PAID"));
        assertThat(jdbc.queryForObject("SELECT confirmed_via FROM payment_orders WHERE payment_id = ?", String.class, paymentId)).isEqualTo("WEBHOOK");
    }

    @Test
    void 웹훅_본문이_64KB_를_넘으면_읽지_않고_400() throws Exception {
        String big = "{\"type\":\"Transaction.Paid\",\"pad\":\"" + "x".repeat(64 * 1024) + "\"}";
        secured.perform(webhook(big, null)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_webhook_logs WHERE error_code = 'BODY_TOO_LARGE'", Integer.class)).isPositive();
    }
}
