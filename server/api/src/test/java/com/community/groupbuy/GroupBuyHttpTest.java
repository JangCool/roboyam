package com.community.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.commerce.CommerceApiTestBase;
import com.community.groupbuy.dto.GroupBuyDraft;
import com.community.groupbuy.dto.NoticeDraft;
import com.community.groupbuy.dto.ProductDraft;
import com.community.groupbuy.service.FulfillmentService;
import com.community.groupbuy.service.GroupBuyAdminService;
import com.community.groupbuy.service.GroupBuyNoticeCatalog;
import com.community.groupbuy.service.GroupBuyNoticeService;
import com.community.groupbuy.service.GroupBuyProductService;
import com.community.groupbuy.service.ShippingPolicyService;
import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.repository.master.IdentityStore;
import com.community.installation.service.InstallationState;
import com.community.user.entity.Role;
import jakarta.servlet.Filter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
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
 * 공구 회원 API(커머스 2b-4) — 실제 api 앱 문맥 + PostgreSQL. 주문의 {@code MoneyRequestGuard}(P26 같은 키·다른 본문 409 · 요청 제한 {@code GROUPBUY_JOIN} 429) ·
 * 판매 스위치(꺼지면 403 {@code GROUP_BUY_SALES_CLOSED}, 단추 {@code SALES_CLOSED}) · 공개 정보(G41) · 글자 ID · 남의 주문 404 · 관심 신청.
 */
class GroupBuyHttpTest extends CommerceApiTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private GroupBuyProductService products;
    @Autowired
    private GroupBuyAdminService admin;
    @Autowired
    private ShippingPolicyService policies;
    @Autowired
    private GroupBuyNoticeService notices;
    @Autowired
    private GroupBuyNoticeCatalog catalog;
    @Autowired
    private FulfillmentService fulfillment;
    @Autowired
    private InstallationState installation;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilterChain;

    private MockMvc mvc;

    @BeforeEach
    void setUpMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = true WHERE id = 1");
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = false WHERE id = 1");
        jdbc.update("UPDATE commerce_business_info SET company_name = '', phone = '' WHERE id = 1");
    }

    private static MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).contentType(MediaType.APPLICATION_JSON);
    }

    /** 이메일 인증·본인인증(만 19세 이상)을 마친 회원 */
    private long buyer() {
        long id = newMember();
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", id);
        jdbc.update("INSERT INTO profiles (user_id, nickname) VALUES (?, ?) ON CONFLICT DO NOTHING", id, "h" + Long.toString(id, 36));
        new IdentityStore(dataSource).insertVerification(id, String.format("%064x", id), "f" + String.format("%063x", id), LocalDate.of(1990, 1, 1),
                IdentityProviderKind.FAKE, "fake:" + id, Instant.now());
        return id;
    }

    /** 모집 중 공구(선착순·즉시 발송·단품 하나·25,000원·배송비 3,000) */
    private long[] recruiting() {
        long op = newOperator();
        String policy = "H" + Long.toString(ids.nextId(), 36).toUpperCase();
        policies.schedule(new ShippingPolicyService.PolicyInput(policy, "시험 배송", 3_000, null, 3_000, "ONE_WAY", null), op, "ip");
        long category = products.createCategory("HTTP " + ids.nextId(), null, 0, op, "ip");
        long product = products.create(new ProductDraft(category, "HTTP 시험 상품", null, null, "<p>설명</p>", 30_000L, "OFFICIAL_STORE", "시험", null, List.of(),
                List.of(new ProductDraft.SkuDraft(null, "기본", List.of(), 10_000L, true)), List.<GroupBuyDraft.ImageRef>of()), op, "ip");
        List<NoticeDraft.Value> values = catalog.current().category("OTHER_GOODS").orElseThrow().items().stream()
                .map(i -> new NoticeDraft.Value(i.key(), "시험 값", false, null)).toList();
        notices.save(product, List.of(new NoticeDraft("OTHER_GOODS", "본체", List.of(), values)), op, "ip");
        Instant now = clock.instant();
        long gb = admin.create(new GroupBuyDraft(product, "HTTP 시험 공구", 25_000, policy, 0, 100, 5, now.minus(Duration.ofMinutes(1)), now.plus(Duration.ofDays(2)),
                "DEMAND", "IMMEDIATE", null, 3, null, false, List.of(), List.of(), true), op, "ip");
        int version = jdbc.queryForObject("SELECT version FROM group_buys WHERE id = ?", Integer.class, gb);
        admin.publish(gb, version, true, op, "ip");
        long sku = jdbc.queryForObject("SELECT id FROM group_buy_skus WHERE group_buy_id = ? AND kind = 'MAIN'", Long.class, gb);
        return new long[] {gb, sku};
    }

    private static String orderBody(long sku, int qty, long expected) {
        return "{\"items\":[{\"skuId\":\"" + sku + "\",\"qty\":" + qty + "}],\"address\":{\"recipient\":\"홍길동\",\"phone\":\"010-1234-5678\",\"postcode\":\"06236\","
                + "\"address1\":\"서울특별시 강남구 1\",\"address2\":\"101호\",\"memo\":\"\"},\"agreements\":{\"purchaseTerms\":true,\"refundPolicy\":true,\"privacy\":true},"
                + "\"expectedAmount\":" + expected + "}";
    }

    @Test
    void 목록과_상세는_로그인_없이_ID_는_글자_단추는_로그인_필요_no_store() throws Exception {
        long[] g = recruiting();
        mvc.perform(get("/api/group-buys/" + g[0])).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.id").value(Long.toString(g[0])))
                .andExpect(jsonPath("$.data.availability.skus[0].skuId").value(Long.toString(g[1])))
                .andExpect(jsonPath("$.data.purchase.reason").value("LOGIN_REQUIRED"))
                .andExpect(jsonPath("$.data.shipping.fee").value(3000))
                .andExpect(jsonPath("$.data.notices[0].categoryCode").value("OTHER_GOODS"))
                .andExpect(jsonPath("$.data.availability.skus[0].supplyPrice").doesNotExist());
        String list = mvc.perform(get("/api/group-buys").param("phase", "ONGOING").param("page", "0")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(MAPPER.readTree(list).path("data").path("items").toString()).contains("\"id\":\"" + g[0] + "\"");
        mvc.perform(get("/api/group-buys/abc")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("GROUP_BUY_NOT_FOUND"));
        // 보안 필터를 거쳐 로그인 없이 — 상세·문의·리뷰 목록은 200(2b-6 — 문의·리뷰 규칙이 빠져 비회원에게 401 이던 것을 실제 확인에서 잡음), 견적은 로그인 없이 되고 주문은 막힌다
        installation.markCompleted();
        MockMvc secured = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
        secured.perform(get("/api/group-buys/" + g[0])).andExpect(status().isOk());
        secured.perform(get("/api/group-buys/" + g[0] + "/questions")).andExpect(status().isOk());
        secured.perform(get("/api/group-buys/" + g[0] + "/reviews")).andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.count").value(0));
        secured.perform(get("/api/me/group-buy-reviews")).andExpect(status().isUnauthorized());
    }

    @Test
    void P26_주문은_멱등_키가_필수이고_같은_키_같은_본문은_처음_응답_다른_본문은_409() throws Exception {
        long[] g = recruiting();
        long m = buyer();
        mvc.perform(as(m, post("/api/group-buys/" + g[0] + "/orders")).content(orderBody(g[1], 1, 28_000))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        String key = "gb-" + ids.nextId();
        String first = mvc.perform(as(m, post("/api/group-buys/" + g[0] + "/orders")).header("Idempotency-Key", key).content(orderBody(g[1], 1, 28_000)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.amount").value(28000)).andExpect(jsonPath("$.data.payment.orderType").value("GROUP_BUY"))
                .andReturn().getResponse().getContentAsString();
        String orderNo = MAPPER.readTree(first).path("data").path("orderNo").asString();
        mvc.perform(as(m, post("/api/group-buys/" + g[0] + "/orders")).header("Idempotency-Key", key).content(orderBody(g[1], 1, 28_000)))
                .andExpect(status().isOk()).andExpect(header().string("Idempotent-Replayed", "true")).andExpect(jsonPath("$.data.orderNo").value(orderNo));
        mvc.perform(as(m, post("/api/group-buys/" + g[0] + "/orders")).header("Idempotency-Key", key).content(orderBody(g[1], 2, 53_000)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM group_buy_participations WHERE user_id = ?", Integer.class, m)).as("주문은 하나").isEqualTo(1);

        // 내 주문 — 남의 주문 번호는 404
        mvc.perform(as(m, get("/api/me/orders/" + orderNo))).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.actions.payAgain").value(true)).andExpect(jsonPath("$.data.address.masked").value(true));
        mvc.perform(as(buyer(), get("/api/me/orders/" + orderNo))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GROUP_BUY_ORDER_NOT_FOUND"));
    }

    @Test
    void 주문_요청_제한_GROUPBUY_JOIN_1분_5번을_넘으면_429() throws Exception {
        long[] g = recruiting();
        long m = buyer();
        // 한도는 처음 값(1분 5번 — commerce.md 12-4). 서버가 한도를 기억해 두므로 표를 바꾸지 않고 처음 값으로 센다
        for (int i = 0; i < 5; i++) {
            // 진행 중 주문이 생기면 두 번째는 409 ALREADY_JOINED — 그래도 시도는 센다
            mvc.perform(as(m, post("/api/group-buys/" + g[0] + "/orders")).header("Idempotency-Key", "rl-" + ids.nextId()).content(orderBody(g[1], 1, 28_000)));
        }
        mvc.perform(as(m, post("/api/group-buys/" + g[0] + "/orders")).header("Idempotency-Key", "rl-" + ids.nextId()).content(orderBody(g[1], 1, 28_000)))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void 판매가_꺼지면_주문은_403_SALES_CLOSED_상세_단추도_SALES_CLOSED() throws Exception {
        long[] g = recruiting();
        long m = buyer();
        mvc.perform(as(m, get("/api/group-buys/" + g[0]))).andExpect(jsonPath("$.data.purchase.reason").doesNotExist());
        jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = false WHERE id = 1");
        mvc.perform(as(m, get("/api/group-buys/" + g[0]))).andExpect(jsonPath("$.data.purchase.reason").value("SALES_CLOSED"))
                .andExpect(jsonPath("$.data.actions.order").value(false));
        mvc.perform(as(m, post("/api/group-buys/" + g[0] + "/orders")).header("Idempotency-Key", "sc-" + ids.nextId()).content(orderBody(g[1], 1, 28_000)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("GROUP_BUY_SALES_CLOSED"));
    }

    @Test
    void G41_공개_정보는_판매가_켜져_있고_필수_칸이_찼을_때만_사업자_정보() throws Exception {
        jdbc.update("UPDATE commerce_business_info SET company_name = '시험상회', representative = '홍길동', business_no = '123-45-67891', mail_order_no = '2026-서울-0001', "
                + "mail_order_authority = '서울특별시 강남구', address = '서울특별시 강남구 1', phone = '02-000-0000', email = 'help@test.local' WHERE id = 1");
        mvc.perform(get("/api/commerce/info")).andExpect(status().isOk()).andExpect(jsonPath("$.data.salesOpen").value(true))
                .andExpect(jsonPath("$.data.business.companyName").value("시험상회")).andExpect(jsonPath("$.data.business.ceoName").value("홍길동"));
        jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = false WHERE id = 1");
        mvc.perform(get("/api/commerce/info")).andExpect(jsonPath("$.data.salesOpen").value(false)).andExpect(jsonPath("$.data.business").doesNotExist());
    }

    @Test
    void 관심_신청은_서버가_허락한_때만_마감_임박은_되고_품절이_아닌데_수량_풀림은_409() throws Exception {
        long[] g = recruiting();
        long m = buyer();
        mvc.perform(as(m, put("/api/group-buys/" + g[0] + "/watches")).content("{\"kind\":\"CLOSING_SOON\",\"skuId\":null}")).andExpect(status().isNoContent());
        mvc.perform(as(m, put("/api/group-buys/" + g[0] + "/watches")).content("{\"kind\":\"RESTOCK\",\"skuId\":\"" + g[1] + "\"}")).andExpect(status().isConflict());
        String detail = mvc.perform(as(m, get("/api/group-buys/" + g[0]))).andReturn().getResponse().getContentAsString();
        JsonNode watches = MAPPER.readTree(detail).path("data").path("watches");
        assertThat(watches.path("CLOSING_SOON").asBoolean()).isTrue();
        assertThat(watches.path("RESTOCK").asBoolean()).isFalse();
    }

    /** 2b-5 실제 확인에서 잡힌 500 — 막 만든 요청은 처리 시각·회수 송장이 없다(값이 null 인 한 줄을 findFirst 하면 NPE). 응답·내 주문 조회 모두 200 */
    @Test
    void 반품_요청은_처리_시각과_회수_송장이_아직_없어도_만들어지고_내_주문에_보인다() throws Exception {
        long[] g = recruiting();
        long m = buyer();
        String created = mvc.perform(as(m, post("/api/group-buys/" + g[0] + "/orders")).header("Idempotency-Key", "gb-" + ids.nextId()).content(orderBody(g[1], 1, 28_000)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode order = MAPPER.readTree(created).path("data");
        String orderNo = order.path("orderNo").asString();
        String paymentId = order.path("payment").path("checkout").path("paymentId").asString();
        mvc.perform(as(m, post("/api/dev/payments/" + paymentId + "/fake-checkout")).content("{\"outcome\":\"PAID\",\"late\":false}")).andExpect(status().isNoContent());
        mvc.perform(as(m, post("/api/payments/" + paymentId + "/complete"))
                .content("{\"orderType\":\"GROUP_BUY\",\"orderRefId\":\"" + order.path("payment").path("orderRefId").asString() + "\"}")).andExpect(status().isOk());
        long participation = jdbc.queryForObject("SELECT id FROM group_buy_participations WHERE order_no = ?", Long.class, orderNo);
        long op = newOperator();
        fulfillment.ship(List.of(new FulfillmentService.ShipLine(participation, "CJ", "123456789012")), op, "ip");
        fulfillment.deliver(List.of(participation), op, "ip");

        String body = "{\"type\":\"RETURN\",\"items\":[{\"skuId\":\"" + g[1] + "\",\"qty\":1,\"reason\":\"CHANGE_OF_MIND\"}],\"collectionMethod\":\"COMPANY_PICKUP\","
                + "\"resolution\":null,\"reasonText\":\"\",\"photoIds\":[],\"expectedAmount\":null}";
        mvc.perform(as(m, post("/api/me/orders/" + orderNo + "/requests")).header("Idempotency-Key", "rq-" + ids.nextId()).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.request.type").value("RETURN"))
                .andExpect(jsonPath("$.data.request.handledAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.request.pickupTrackingNo").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(as(m, get("/api/me/orders/" + orderNo))).andExpect(status().isOk()).andExpect(jsonPath("$.data.requests[0].type").value("RETURN"));
    }
}
