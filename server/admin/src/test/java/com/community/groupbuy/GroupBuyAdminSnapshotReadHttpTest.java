package com.community.groupbuy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

/**
 * JPA 전환 보강 — 예전 한 문장이 여러 문장이 된 관리자 공구 읽기(수량 화면 · 상품 목록 · 추가 구성상품 고르기)를 감싼 읽기 전용 {@code REPEATABLE READ} 메서드가
 * 실제 관리자 앱 문맥(JPA 트랜잭션 관리자 + 프록시)에서 열리고 끝난다(격리 수준 지정 거절·읽기 전용 쓰기 오류가 없다). 실제 PostgreSQL.
 */
class GroupBuyAdminSnapshotReadHttpTest extends Commerce04AdminTestBase {

    @Test
    void 한_스냅숏으로_읽는_공구_화면이_열린다() throws Exception {
        Cookie reader = login(operatorWith(Permission.COMMERCE_PRODUCT_READ));
        mvc.perform(get("/api/admin/group-buy-products").param("page", "0").param("size", "20").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber());
        mvc.perform(get("/api/admin/group-buy-products/addon-sources").cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        mvc.perform(get("/api/admin/group-buys/" + ids.nextId() + "/stock").cookie(reader)).andExpect(status().isNotFound()); // 트랜잭션 안에서 없는 공구 — 404
    }
}
