package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.support.MoneyRequestGuard;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopUsageType;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 포인트 상점 품목·구매 API(커머스 1-2 — commerce-stage1-contract 「B」 구매·「1-2 화면 목업」 API 후보) — 멱등 키(같은 키 다시 사기는 처음 응답 그대로·포인트 한 번·구매 한 줄,
 * 키 없음 400, 다른 몸 같은 키 409), 요청 제한 429 + {@code Retry-After}, 자격·판매·재고·보유·잔액·고지 동의 오류 코드, 품목 목록·상세. 실제 PostgreSQL.
 */
class PointShopPurchaseHttpTest extends PointShopApiTestBase {

    // ── 멱등 키 ─────────────────────────────────────────────────────────────

    @Test
    void 같은_멱등_키로_다시_사면_처음_응답_그대로이고_포인트는_한_번만_구매도_한_줄() throws Exception {
        long member = member();
        grant(member, 5_000);
        long item = colorItem(1_200, "#E11D48");
        String key = key();

        String first = body(purchase(member, key, item, true).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist(MoneyRequestGuard.REPLAYED_HEADER))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.balance").value(3_800))
                .andExpect(jsonPath("$.data.item.id").value(Long.toString(item)))
                .andExpect(jsonPath("$.data.item.mine.owned").value(true)));
        String again = body(purchase(member, key, item, true).andExpect(status().isOk()).andExpect(header().string(MoneyRequestGuard.REPLAYED_HEADER, "true")));

        assertThat((Object) JsonPath.read(again, "$.data")).as("처음 응답의 data 그대로").isEqualTo(JsonPath.read(first, "$.data"));
        assertThat(balance(member)).as("포인트는 한 번만 빠진다").isEqualTo(3_800);
        assertThat(count("SELECT count(*) FROM point_shop_purchases WHERE user_id = ?", member)).as("구매 한 줄").isEqualTo(1);
        assertThat(count("SELECT count(*) FROM user_items WHERE user_id = ?", member)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'POINT_SHOP_PURCHASE'", member)).isEqualTo(1);
        assertInvariants(member);
    }

    @Test
    void 멱등_키가_없으면_400_IDEMPOTENCY_KEY_REQUIRED_이고_아무것도_사지_않는다() throws Exception {
        long member = member();
        grant(member, 5_000);
        long item = colorItem(1_000, "#0EA5E9");

        purchase(member, null, item, true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        purchase(member, "bad key!", item, true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_INVALID"));
        assertThat(balance(member)).isEqualTo(5_000);
        assertThat(count("SELECT count(*) FROM point_shop_purchases WHERE user_id = ?", member)).isZero();
    }

    @Test
    void 같은_키에_다른_몸이면_409_IDEMPOTENCY_KEY_REUSED_이고_두_번째는_사지_않는다() throws Exception {
        long member = member();
        grant(member, 5_000);
        long first = colorItem(1_000, "#22C55E");
        long second = colorItem(1_000, "#A855F7");
        String key = key();

        purchase(member, key, first, true).andExpect(status().isOk());
        purchase(member, key, second, true).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(count("SELECT count(*) FROM point_shop_purchases WHERE user_id = ?", member)).isEqualTo(1);
        assertThat(balance(member)).isEqualTo(4_000);
    }

    @Test
    void 실패한_구매는_같은_키로_다시_할_수_있다() throws Exception {
        long member = member();
        long item = colorItem(1_000, "#F59E0B");
        String key = key();

        purchase(member, key, item, true).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_INSUFFICIENT"));
        grant(member, 1_000);
        purchase(member, key, item, true).andExpect(status().isOk()).andExpect(header().doesNotExist(MoneyRequestGuard.REPLAYED_HEADER))
                .andExpect(jsonPath("$.data.balance").value(0));
        assertInvariants(member);
    }

    // ── 요청 제한 ───────────────────────────────────────────────────────────

    @Test
    void 요청_제한을_넘으면_429_REQUEST_RATE_LIMITED_와_Retry_After_이고_사지_않는다() throws Exception {
        long member = member();
        grant(member, 5_000);
        long item = colorItem(1_000, "#14B8A6");
        // 한도(1분 10번)를 이미 다 쓴 것으로 — 이번 창과(경계에 걸릴 때를 위해) 다음 창 모두
        Instant window = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        for (Instant w : new Instant[] {window, window.plus(Duration.ofMinutes(1))}) {
            jdbc.update("INSERT INTO request_rate_counters (user_id, action, window_start, count) VALUES (?, 'POINT_SHOP_PURCHASE', ?, 10)", member, Timestamp.from(w));
        }

        ResultActions limited = purchase(member, key(), item, true).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("REQUEST_RATE_LIMITED")).andExpect(header().exists("Retry-After"));
        assertThat(Long.parseLong(limited.andReturn().getResponse().getHeader("Retry-After"))).isBetween(1L, 60L);
        assertThat(balance(member)).isEqualTo(5_000);
        assertThat(count("SELECT count(*) FROM point_shop_purchases WHERE user_id = ?", member)).isZero();
    }

    // ── 오류 코드 ───────────────────────────────────────────────────────────

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mvc.perform(get("/api/point-shop/items")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/point-shop/purchases").header(MoneyRequestGuard.HEADER, key())
                .contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void 판매_아님_품절_영구_중복_잔액_부족은_409() throws Exception {
        long member = member();
        grant(member, 3_000);
        Instant now = clock.instant();
        long upcoming = createItem(PointShopItemKind.NICKNAME_COLOR, PointShopUsageType.PERMANENT, 100, "#111111", null, null, null, now.plus(Duration.ofDays(1)), null, true);
        long ended = createItem(PointShopItemKind.NICKNAME_COLOR, PointShopUsageType.PERMANENT, 100, "#222222", null, null, null, now.minus(Duration.ofDays(3)),
                now.minus(Duration.ofDays(1)), true);
        long inactive = createItem(PointShopItemKind.NICKNAME_COLOR, PointShopUsageType.PERMANENT, 100, "#333333", null, null, null, null, null, false);
        long soldOut = borderItem(100, "GOLD", 0);
        long owned = colorItem(100, "#444444");
        long expensive = colorItem(5_000, "#555555");

        purchase(member, key(), upcoming, true).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_NOT_ON_SALE"));
        purchase(member, key(), ended, true).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_NOT_ON_SALE"));
        purchase(member, key(), inactive, true).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_NOT_ON_SALE"));
        purchase(member, key(), soldOut, true).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_SOLD_OUT"));
        purchase(member, key(), owned, true).andExpect(status().isOk());
        purchase(member, key(), owned, true).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_ALREADY_OWNED"));
        purchase(member, key(), expensive, true).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_INSUFFICIENT"));

        assertThat(balance(member)).isEqualTo(2_900);
        assertThat(count("SELECT count(*) FROM point_shop_purchases WHERE user_id = ?", member)).isEqualTo(1);
        assertInvariants(member);
    }

    @Test
    void 없는_품목은_404_고지_동의가_없으면_400_fields_agreeRefundNotice() throws Exception {
        long member = member();
        grant(member, 3_000);
        long item = colorItem(100, "#666666");

        purchase(member, key(), 1L, true).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_ITEM_NOT_FOUND"));
        purchaseBody(member, key(), "{\"itemId\":\"abc\",\"agreeRefundNotice\":true}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POINT_SHOP_ITEM_NOT_FOUND"));
        purchase(member, key(), item, false).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.fields.agreeRefundNotice").exists());
        purchaseBody(member, key(), "{\"itemId\":\"" + item + "\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.agreeRefundNotice").exists());
        assertThat(balance(member)).isEqualTo(3_000);
    }

    @Test
    void 자격_정지_이메일_미인증_권한_없음_동결은_403_이고_사지_않는다() throws Exception {
        long item = colorItem(100, "#777777");

        long suspended = member();
        grant(suspended, 1_000);
        suspend(suspended);
        purchase(suspended, key(), item, true).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_RESTRICTED"));

        long unverified = unverifiedMember();
        grant(unverified, 1_000);
        purchase(unverified, key(), item, true).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));

        long business = businessMember();
        grant(business, 1_000);
        purchase(business, key(), item, true).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

        long frozen = member();
        grant(frozen, 1_000);
        freeze(frozen);
        purchase(frozen, key(), item, true).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("MEMBER_RESTRICTED"));

        for (long m : new long[] {suspended, unverified, business, frozen}) {
            assertThat(balance(m)).isEqualTo(1_000);
            assertThat(count("SELECT count(*) FROM point_shop_purchases WHERE user_id = ?", m)).isZero();
        }
    }

    @Test
    void 자격_검사가_고지_동의보다_먼저다() throws Exception {
        long item = colorItem(100, "#888888");
        long business = businessMember();
        purchase(business, key(), item, false).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }

    // ── 목록·상세 ───────────────────────────────────────────────────────────

    @Test
    void 목록은_잔액_환불_날_수_내_보유를_싣고_판매_끝은_빼며_아바타는_섞지_않는다() throws Exception {
        long member = member();
        grant(member, 2_000);
        long onSale = colorItem(300, "#0F766E");
        long ended = createItem(PointShopItemKind.NICKNAME_COLOR, PointShopUsageType.PERMANENT, 100, "#123456", null, null, null, null, null, false);
        buy(member, onSale);

        String list = body(mvc.perform(as(member, get("/api/point-shop/items"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.balance").value(1_700)).andExpect(jsonPath("$.data.refundDays").isNumber()));
        assertThat(JsonPath.<java.util.List<String>>read(list, "$.data.items[*].id")).contains(Long.toString(onSale)).doesNotContain(Long.toString(ended));
        assertThat(JsonPath.<java.util.List<String>>read(list, "$.data.items[*].kind")).doesNotContain("PROFILE_AVATAR");
        assertThat(JsonPath.<java.util.List<Boolean>>read(list, "$.data.items[?(@.id == '" + onSale + "')].mine.owned")).containsExactly(true);
        assertThat(JsonPath.<java.util.List<String>>read(list, "$.data.items[?(@.id == '" + onSale + "')].saleState")).containsExactly("ON_SALE");

        String avatars = body(mvc.perform(as(member, get("/api/point-shop/items").param("kind", "PROFILE_AVATAR"))).andExpect(status().isOk()));
        assertThat(JsonPath.<java.util.List<String>>read(avatars, "$.data.items[*].kind")).isNotEmpty().containsOnly("PROFILE_AVATAR");
        mvc.perform(as(member, get("/api/point-shop/items").param("kind", "NOPE"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    }

    @Test
    void 상세는_판매가_끝나도_열리고_없거나_형식이_틀리면_404() throws Exception {
        long member = member();
        long ended = createItem(PointShopItemKind.NICKNAME_COLOR, PointShopUsageType.PERMANENT, 100, "#654321", null, null, null, null, null, false);
        mvc.perform(as(member, get("/api/point-shop/items/" + ended))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.id").value(Long.toString(ended))).andExpect(jsonPath("$.data.saleState").value("ENDED"))
                .andExpect(jsonPath("$.data.color").value("#654321")).andExpect(jsonPath("$.data.mine.owned").value(false));
        mvc.perform(as(member, get("/api/point-shop/items/1"))).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_ITEM_NOT_FOUND"));
        mvc.perform(as(member, get("/api/point-shop/items/abc"))).andExpect(status().isNotFound());
    }

    @Test
    void 아바타_품목도_같은_구매로_산다_사도_프로필은_바뀌지_않는다() throws Exception {
        long member = member();
        grant(member, 2_000);
        String avatarId = jdbc.queryForObject("SELECT avatar_id FROM point_shop_items WHERE avatar_id IS NOT NULL AND active ORDER BY sort_order, id LIMIT 1", String.class);
        long item = avatarItem(avatarId);

        purchase(member, key(), item, true).andExpect(status().isOk()).andExpect(jsonPath("$.data.item.avatarId").value(avatarId))
                .andExpect(jsonPath("$.data.item.mine.owned").value(true));
        assertThat(jdbc.queryForObject("SELECT profile_image FROM profiles WHERE user_id = ?", String.class, member)).isNull();
        assertInvariants(member);
    }
}
