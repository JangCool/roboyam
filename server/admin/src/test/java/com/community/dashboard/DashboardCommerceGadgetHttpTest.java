package com.community.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

/**
 * 대시보드 공구·결제 가젯의 권한(admin-dashboard-contract 「가젯 종류」 표 — 공구 = commerce.product.read, 결제 = commerce.payment.read) — 실제 관리자 앱 문맥 + PostgreSQL.
 * 권한이 없는 종류는 그 칸 null, 한 종류만 요청했는데 권한이 없으면 403. 2단계부터 커머스 종류는 {@code unavailable} 이 아니다.
 */
class DashboardCommerceGadgetHttpTest extends Commerce04AdminTestBase {

    @Test
    void 결제_조회_권한자는_결제_가젯만_받고_공구_가젯은_null() throws Exception {
        Cookie payment = login(operatorWith(Permission.COMMERCE_PAYMENT_READ));
        mvc.perform(get("/api/admin/dashboard/gadgets").param("types", "SALES_SUMMARY,ORDER_TREND,RECONCILE,GROUPBUY_PROGRESS,GROUPBUY_QNA").param("days.ORDER_TREND", "30")
                        .cookie(payment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gadgets.SALES_SUMMARY.today.paid").isNumber())
                .andExpect(jsonPath("$.data.gadgets.SALES_SUMMARY.lastMonth.refundedCount").isNumber())
                .andExpect(jsonPath("$.data.gadgets.ORDER_TREND.days.length()").value(30))
                .andExpect(jsonPath("$.data.gadgets.RECONCILE.status").isString())
                .andExpect(jsonPath("$.data.gadgets.RECONCILE.webhookFailures").isNumber())
                .andExpect(jsonPath("$.data.gadgets.GROUPBUY_PROGRESS").doesNotExist())
                .andExpect(jsonPath("$.data.gadgets.GROUPBUY_QNA").doesNotExist())
                .andExpect(jsonPath("$.data.unavailable.length()").value(0));
        mvc.perform(get("/api/admin/dashboard/gadgets").param("types", "GROUPBUY_PROGRESS").cookie(payment)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }

    @Test
    void 상품_조회_권한자는_공구_가젯과_오늘_일정을_받고_결제_가젯은_403() throws Exception {
        Cookie product = login(operatorWith(Permission.COMMERCE_PRODUCT_READ));
        mvc.perform(get("/api/admin/dashboard/gadgets").param("types", "GROUPBUY_PROGRESS,GROUPBUY_QNA,TODAY_SCHEDULE").param("limit.GROUPBUY_QNA", "10").cookie(product))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gadgets.GROUPBUY_PROGRESS.items").isArray())
                .andExpect(jsonPath("$.data.gadgets.GROUPBUY_QNA.pending").isNumber())
                .andExpect(jsonPath("$.data.gadgets.TODAY_SCHEDULE.items").isArray())
                .andExpect(jsonPath("$.data.unavailable.length()").value(0));
        mvc.perform(get("/api/admin/dashboard/gadgets").param("types", "SALES_SUMMARY").cookie(product)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard/gadgets").param("types", "ORDER_TREND").param("days.ORDER_TREND", "14").cookie(product)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['days.ORDER_TREND']").exists());
    }
}
