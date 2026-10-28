package com.community.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.menu.service.AdminScreenRegistry;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 공구 API(커머스 2b-4) — 실제 관리자 앱 문맥 + PostgreSQL. 결정 35 권한(조회·관리·중단·수량 조정·환불·내보내기)·{@code featureReady}·메뉴 · 판매 켜기는 최고 관리자만(20절) ·
 * 사업자 정보 검증 · 대시보드 칸은 볼 권한만 · 상품 설명 정화(결정 2) · 결제 「수동 처리 방법」(결정 74).
 */
class GroupBuyAdminHttpTest extends Commerce04AdminTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @AfterEach
    void restore() {
        jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = false WHERE id = 1");
        jdbc.update("UPDATE commerce_sales_checklist SET checked = false, checked_by = NULL, checked_at = NULL");
    }

    @Test
    void 결정35_권한_코드는_기능이_생겼고_메뉴_묶음은_서버와_화면이_같다() throws Exception {
        for (Permission p : List.of(Permission.COMMERCE_PRODUCT_READ, Permission.COMMERCE_PRODUCT_MANAGE, Permission.COMMERCE_PRODUCT_PRICE, Permission.COMMERCE_STOCK_ADJUST,
                Permission.COMMERCE_ORDER_READ, Permission.COMMERCE_ORDER_MANAGE, Permission.COMMERCE_REFUND_APPROVE, Permission.COMMERCE_REFUND_LARGE,
                Permission.COMMERCE_ADDRESS_REVEAL, Permission.COMMERCE_GROUPBUY_CANCEL, Permission.COMMERCE_EXPORT, Permission.COMMERCE_SETTINGS)) {
            assertThat(p.featureReady()).as(p.code()).isTrue();
            assertThat(jdbc.queryForObject("SELECT feature_ready FROM permissions WHERE code = ?", Boolean.class, p.code())).as(p.code()).isTrue();
        }
        String menu = Files.readString(Path.of("..", "..", "apps", "admin-web", "app", "utils", "util-admin-menu.ts"));
        for (String key : List.of("groupBuyProducts", "groupBuyOptionTypes", "groupBuyCategories", "groupBuys", "groupBuyStock", "groupBuyOrders", "groupBuyRequests")) {
            AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen(key).orElseThrow(() -> new AssertionError("서버 메뉴에 없다: " + key));
            assertThat(menu).contains("{ key: '" + key + "', to: '" + screen.path() + "', icon: '" + screen.icon() + "', labelKey: '" + screen.labelKey() + "', permission: '"
                    + screen.permission().code() + "' }");
        }
    }

    @Test
    void 결정35_조회_관리_중단_수량_조정_환불_내보내기는_각_권한이_있어야_한다() throws Exception {
        Cookie none = login(operatorWith());
        Cookie reader = login(operatorWith(Permission.COMMERCE_PRODUCT_READ, Permission.COMMERCE_ORDER_READ));
        Cookie exporter = login(operatorWith(Permission.COMMERCE_EXPORT, Permission.COMMERCE_ORDER_READ));

        mvc.perform(get("/api/admin/group-buys").cookie(none)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        mvc.perform(get("/api/admin/group-buys").param("page", "0").cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.counts.RECRUITING").exists());
        mvc.perform(withCsrf(post("/api/admin/group-buys/1/cancel"), reader).content("{\"reason\":\"중단합니다 시험\"}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/group-buys/1/stock/adjust"), reader).content("{\"direction\":\"ADD\",\"qty\":1,\"reason\":\"추가 입고 시험\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/group-buys"), reader).content("{}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/group-buy-orders/1/refunds"), reader).content("{\"reason\":\"운영 환불 시험\"}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/group-buy-orders/prepare"), reader).content("{\"orderIds\":[]}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/group-buy-orders/1/refunds/pending/2/approve"), login(operatorWith(Permission.COMMERCE_REFUND_APPROVE))))
                .andExpect(status().isForbidden()); // 고액 승인 대기는 commerce.refund.large(결정 70)
        mvc.perform(withCsrf(post("/api/admin/group-buy-orders/export"), exporter).content("{\"groupBuyId\":\"1\",\"reason\":\"택배 접수용 명단\"}"))
                .andExpect(status().isForbidden()); // 내보내기는 commerce.export + commerce.address.reveal 둘 다(10-9)
        mvc.perform(withCsrf(put("/api/admin/commerce/business-info"), reader).content("{\"values\":{}}")).andExpect(status().isForbidden());
    }

    @Test
    void 판매_켜기는_최고_관리자만_끄기는_설정_권한자도_사업자등록번호는_체크섬까지_본다() throws Exception {
        Cookie settings = login(operatorWith(Permission.COMMERCE_SETTINGS));
        mvc.perform(withCsrf(post("/api/admin/commerce/sales"), settings).content("{\"enabled\":true,\"reason\":\"판매를 시작합니다\"}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMERCE_SALES_ROOT_ONLY"));
        mvc.perform(withCsrf(post("/api/admin/commerce/sales"), settings).content("{\"enabled\":false,\"reason\":\"문제가 있어 멈춥니다\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false)).andExpect(jsonPath("$.data.autoChecks.length()").value(4))
                .andExpect(jsonPath("$.data.checklist.length()").value(6));
        mvc.perform(withCsrf(post("/api/admin/commerce/sales/checklist/TERMS"), settings).content("{\"checked\":true}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checklist[2].key").value("TERMS")).andExpect(jsonPath("$.data.checklist[2].checked").value(true));

        mvc.perform(withCsrf(put("/api/admin/commerce/business-info"), settings).content("{\"values\":{\"companyName\":\"시험상회\",\"businessNo\":\"123-45-67890\"}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields['values.businessNo']").exists());
        mvc.perform(withCsrf(put("/api/admin/commerce/business-info"), settings).content("{\"values\":{\"companyName\":\"시험상회\",\"businessNo\":\"123-45-67891\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.values.businessNo").value("123-45-67891")).andExpect(jsonPath("$.data.ready").value(false));

        mvc.perform(get("/api/admin/commerce/group-buy-settings").cookie(settings)).andExpect(status().isOk()).andExpect(jsonPath("$.data.ranges.holdMinutes.min").value(3))
                .andExpect(jsonPath("$.data.defaults.largeRefundThreshold").value(100000));
    }

    @Test
    void 대시보드_커머스_칸은_볼_권한이_있는_칸만() throws Exception {
        Cookie stockOnly = login(operatorWith(Permission.COMMERCE_PRODUCT_READ));
        mvc.perform(get("/api/admin/commerce/dashboard").cookie(stockOnly)).andExpect(status().isOk()).andExpect(jsonPath("$.data.lowStockGroupBuys").isNumber())
                .andExpect(jsonPath("$.data.preparingWaiting").doesNotExist()).andExpect(jsonPath("$.data.paymentAnomalies").doesNotExist());
        Cookie orders = login(operatorWith(Permission.COMMERCE_ORDER_READ));
        mvc.perform(get("/api/admin/commerce/dashboard").cookie(orders)).andExpect(jsonPath("$.data.preparingWaiting").isNumber())
                .andExpect(jsonPath("$.data.openRequests.total").isNumber()).andExpect(jsonPath("$.data.deliveryStale.days").value(7))
                .andExpect(jsonPath("$.data.lowStockGroupBuys").doesNotExist());
        mvc.perform(get("/api/admin/commerce/dashboard").cookie(login(operatorWith()))).andExpect(status().isForbidden());
    }

    @Test
    void 결정2_상품_설명은_저장할_때_서버가_정화한다() throws Exception {
        Cookie manager = login(operatorWith(Permission.COMMERCE_PRODUCT_READ, Permission.COMMERCE_PRODUCT_MANAGE));
        String categories = mvc.perform(withCsrf(put("/api/admin/group-buys/categories"), manager)
                        .content("[{\"id\":null,\"tempKey\":\"t1\",\"parent\":null,\"name\":\"정화" + (ids.nextId() % 100000) + "\",\"hidden\":false}]"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode created = null;
        for (JsonNode c : MAPPER.readTree(categories).path("data")) {
            if (c.path("name").asString().startsWith("정화")) {
                created = c;
            }
        }
        assertThat(created).isNotNull();
        String body = "{\"categoryId\":\"" + created.path("id").asString() + "\",\"name\":\"정화 상품\",\"summary\":\"\",\"spec\":\"\","
                + "\"description\":\"<p onclick=\\\"x()\\\">설명<script>alert(1)</script></p><iframe src=\\\"https://evil.example\\\"></iframe>\","
                + "\"normalPrice\":10000,\"normalPriceBasis\":\"OFFICIAL_STORE\",\"normalPriceSource\":\"시험\",\"withdrawalRestriction\":\"\",\"optionTypeCodes\":[],"
                + "\"skus\":[{\"skuId\":null,\"name\":\"기본\",\"values\":[],\"defaultSupplyPrice\":5000,\"onSale\":true}],\"images\":[],\"notices\":null,\"version\":null}";
        mvc.perform(withCsrf(post("/api/admin/group-buy-products"), manager).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("<p>설명</p>")).andExpect(jsonPath("$.data.skus[0].defaultSupplyPrice").doesNotExist());
        // 남이 올리지 않은 이미지 키는 받지 않는다
        mvc.perform(withCsrf(post("/api/admin/group-buy-products"), manager).content(body.replace("\"images\":[]", "\"images\":[\"../etc/passwd\"]")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.images").exists());
    }

    @Test
    void 결정74_결제_수동_처리는_방법을_고르고_콘솔_취소면_취소_번호가_필요하다() throws Exception {
        Cookie reconcile = login(operatorWith(Permission.COMMERCE_PAYMENT_READ, Permission.COMMERCE_RECONCILE_MANAGE));
        mvc.perform(withCsrf(post("/api/admin/payment-cancels/" + ids.nextId() + "/manual"), reconcile)
                        .content("{\"reason\":\"콘솔에서 직접 취소함\",\"method\":\"CONSOLE_CANCEL\",\"pgCancelId\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.pgCancelId").exists());
        mvc.perform(withCsrf(post("/api/admin/payment-cancels/" + ids.nextId() + "/manual"), reconcile)
                        .content("{\"reason\":\"콘솔에서 직접 취소함\",\"method\":\"WIRE\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.method").exists());
        mvc.perform(withCsrf(post("/api/admin/payment-cancels/" + ids.nextId() + "/manual"), reconcile)
                        .content("{\"reason\":\"계좌로 돌려줬어요\",\"method\":\"BANK_TRANSFER\"}"))
                .andExpect(status().isNotFound()); // 방법이 맞으면 서비스까지 간다 — 없는 취소는 404
    }
}
