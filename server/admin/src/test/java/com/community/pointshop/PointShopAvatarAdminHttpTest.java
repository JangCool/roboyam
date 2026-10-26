package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRetentionClass;
import com.community.permission.entity.Permission;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 아바타 요약·묶음 가격·묶음 판매(커머스 1-2 결정 14 — commerce-stage1-contract 「1-2 화면 목업」 아바타 탭) — 카테고리 14개(무료 2 + 유료 12)·전체 합계, 범위 {@code ALL}·{@code CATEGORIES}·
 * {@code ITEMS}, 무료 카테고리·아바타가 아닌 품목·모르는 범위는 400(아무것도 안 바뀐다), {@code matched}·{@code changed}, 이미 산 구매의 가격은 그대로, 감사(일반 운영).
 */
class PointShopAvatarAdminHttpTest extends PointShopAdminHttpTestBase {

    private ResultActions price(Cookie session, String body) throws Exception {
        return mvc.perform(withCsrf(put(BASE + "/avatars/price"), session).header("Accept-Language", "ko").content(body));
    }

    private ResultActions sale(Cookie session, String body) throws Exception {
        return mvc.perform(withCsrf(post(BASE + "/avatars/sale"), session).header("Accept-Language", "ko").content(body));
    }

    private int avatarPrice(long itemId) {
        return jdbc.queryForObject("SELECT price_point FROM point_shop_items WHERE id = ?", Integer.class, itemId);
    }

    @Test
    void 요약은_카탈로그_차례의_14_카테고리와_합계() throws Exception {
        Cookie reader = login(operatorWith(Permission.POINTSHOP_READ));
        long sportsItem = avatarItem(jdbc.queryForObject("SELECT avatar_id FROM point_shop_items WHERE kind = 'PROFILE_AVATAR' AND avatar_id LIKE 'sports-%' "
                + "ORDER BY sort_order LIMIT 1", String.class));
        jdbc.update("UPDATE point_shop_items SET active = false, price_point = 2500 WHERE id = ?", sportsItem);

        String body = mvc.perform(get(BASE + "/avatars/summary").cookie(reader)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.categories.length()").value(14))
                .andExpect(jsonPath("$.data.categories[0].category").value("basic")).andExpect(jsonPath("$.data.categories[0].free").value(true))
                .andExpect(jsonPath("$.data.categories[0].forSale").value(0)).andExpect(jsonPath("$.data.categories[0].stopped").value(0))
                .andExpect(jsonPath("$.data.categories[0].minPrice").value(nullValue()))
                .andExpect(jsonPath("$.data.categories[1].category").value("hobby"))
                .andExpect(jsonPath("$.data.totals.total").value(328)).andExpect(jsonPath("$.data.totals.freeCount").value(22))
                .andExpect(jsonPath("$.data.totals.paidCount").value(306))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> categories = JsonPath.read(body, "$.data.categories");
        Map<String, Object> sports = categories.stream().filter(c -> "sports".equals(c.get("category"))).findFirst().orElseThrow();
        assertThat(sports).containsEntry("free", false).containsEntry("total", 12).containsEntry("forSale", 11).containsEntry("stopped", 1)
                .containsEntry("maxPrice", 2500);
        int forSale = JsonPath.read(body, "$.data.totals.forSale");
        int stopped = JsonPath.read(body, "$.data.totals.stopped");
        assertThat(forSale + stopped).isEqualTo(306);
        assertThat(stopped).isGreaterThanOrEqualTo(1);
    }

    @Test
    void 카테고리_묶음_가격은_그_카테고리만_바꾸고_matched_changed_와_감사_일반_운영_이미_산_가격은_그대로() throws Exception {
        long operator = operatorWith(Permission.POINTSHOP_MANAGE);
        Cookie session = login(operator);
        List<Long> sportsItems = jdbc.queryForList("SELECT id FROM point_shop_items WHERE avatar_id LIKE 'sports-%'", Long.class);
        long other = avatarItem(jdbc.queryForObject("SELECT avatar_id FROM point_shop_items WHERE avatar_id LIKE 'job-%' ORDER BY sort_order LIMIT 1", String.class));
        int otherBefore = avatarPrice(other);
        // 미리 한 개를 같은 값으로 — changed 는 값이 실제로 바뀐 줄만
        jdbc.update("UPDATE point_shop_items SET price_point = 1700 WHERE id = ?", sportsItems.get(0));
        // 산 구매의 가격 사본
        long member = buyer(5000);
        String purchaseId = buy(member, sportsItems.get(1)).purchaseId();
        int paid = jdbc.queryForObject("SELECT price_point FROM point_shop_purchases WHERE id = ?", Integer.class, Long.parseLong(purchaseId));

        price(session, "{\"scope\":\"CATEGORIES\",\"categories\":[\"sports\"],\"price\":1700}").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.scope").value("CATEGORIES")).andExpect(jsonPath("$.data.matched").value(12))
                .andExpect(jsonPath("$.data.changed").value(11)).andExpect(jsonPath("$.data.price").value(1700))
                .andExpect(jsonPath("$.data.active").value(nullValue()));
        for (long id : sportsItems) {
            assertThat(avatarPrice(id)).isEqualTo(1700);
        }
        assertThat(avatarPrice(other)).isEqualTo(otherBefore);
        assertThat(jdbc.queryForObject("SELECT price_point FROM point_shop_purchases WHERE id = ?", Integer.class, Long.parseLong(purchaseId))).isEqualTo(paid);

        Map<String, Object> audit = lastAudit(AuditActions.POINT_SHOP_AVATAR_PRICE, "CATEGORIES");
        assertThat(((Number) audit.get("actor_id")).longValue()).isEqualTo(operator);
        assertThat(audit.get("target_type")).isEqualTo("POINT_SHOP_AVATARS");
        assertThat(audit.get("retention_class")).isEqualTo(AuditRetentionClass.GENERAL.name());
        assertThat(audit.get("detail").toString()).contains("\"matched\":12", "\"changed\":11", "\"price\":1700", "sports");

        // 같은 값으로 다시 — 하나도 바뀌지 않는다
        price(session, "{\"scope\":\"CATEGORIES\",\"categories\":[\"sports\"],\"price\":1700}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(12)).andExpect(jsonPath("$.data.changed").value(0));
        // 전체 — 유료 306
        price(session, "{\"scope\":\"ALL\",\"price\":1700}").andExpect(status().isOk()).andExpect(jsonPath("$.data.matched").value(306));
        assertInvariants(member);
    }

    @Test
    void 품목_묶음_판매_끄기_켜기와_matched_changed() throws Exception {
        Cookie session = login(operatorWith(Permission.POINTSHOP_MANAGE));
        List<Long> picked = jdbc.queryForList("SELECT id FROM point_shop_items WHERE kind = 'PROFILE_AVATAR' ORDER BY sort_order LIMIT 3", Long.class);
        jdbc.update("UPDATE point_shop_items SET active = true WHERE id IN (?, ?, ?)", picked.get(0), picked.get(1), picked.get(2));
        String ids = "[\"" + picked.get(0) + "\",\"" + picked.get(1) + "\",\"" + picked.get(2) + "\",\"" + picked.get(0) + "\"]";

        sale(session, "{\"scope\":\"ITEMS\",\"itemIds\":" + ids + ",\"active\":false}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("ITEMS")).andExpect(jsonPath("$.data.matched").value(3)).andExpect(jsonPath("$.data.changed").value(3))
                .andExpect(jsonPath("$.data.active").value(false)).andExpect(jsonPath("$.data.price").value(nullValue()));
        assertThat(count("SELECT count(*) FROM point_shop_items WHERE id IN (?, ?, ?) AND NOT active", picked.get(0), picked.get(1), picked.get(2))).isEqualTo(3);
        Map<String, Object> audit = lastAudit(AuditActions.POINT_SHOP_AVATAR_SALE, "ITEMS");
        assertThat(audit.get("retention_class")).isEqualTo(AuditRetentionClass.GENERAL.name());
        assertThat(audit.get("detail").toString()).contains("\"itemCount\":4", "\"active\":false");

        sale(session, "{\"scope\":\"ITEMS\",\"itemIds\":[\"" + picked.get(0) + "\"],\"active\":false}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(1)).andExpect(jsonPath("$.data.changed").value(0));
        sale(session, "{\"scope\":\"ITEMS\",\"itemIds\":[\"" + picked.get(0) + "\"],\"active\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(1));
    }

    @Test
    void 무료_카테고리_아바타가_아닌_품목_모르는_범위_빠진_값은_400_이고_아무것도_바뀌지_않는다() throws Exception {
        Cookie session = login(operatorWith(Permission.POINTSHOP_MANAGE));
        long color = colorItem(500, null);
        long avatar = jdbc.queryForObject("SELECT id FROM point_shop_items WHERE kind = 'PROFILE_AVATAR' ORDER BY sort_order LIMIT 1", Long.class);
        int before = avatarPrice(avatar);
        List<Map<String, Object>> snapshot = jdbc.queryForList("SELECT id, price_point, active FROM point_shop_items WHERE kind = 'PROFILE_AVATAR' ORDER BY id");
        int audits = count("SELECT count(*) FROM audit_logs WHERE action IN (?, ?)", AuditActions.POINT_SHOP_AVATAR_PRICE, AuditActions.POINT_SHOP_AVATAR_SALE);

        price(session, "{\"scope\":\"CATEGORIES\",\"categories\":[\"basic\"],\"price\":1200}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.categories").value("무료 카테고리는 품목이 없어 바꿀 수 없어요."));
        price(session, "{\"scope\":\"CATEGORIES\",\"categories\":[\"sports\",\"nope\"],\"price\":1200}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.categories").value("모르는 카테고리가 있어요."));
        price(session, "{\"scope\":\"ITEMS\",\"itemIds\":[\"" + avatar + "\",\"" + color + "\"],\"price\":1200}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.itemIds").value("아바타 품목이 아닌 것이 있어요."));
        // 없는 품목 번호도 같은 400(shared 가 「아바타 품목이 아닌 것」으로 묶는다 — 404 가 아니다)
        // 없는 품목 번호가 섞이면 404(아무것도 안 바뀜) — 계약
        price(session, "{\"scope\":\"ITEMS\",\"itemIds\":[\"" + avatar + "\",\"999999999\"],\"price\":1200}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POINT_SHOP_ITEM_NOT_FOUND"));
        sale(session, "{\"scope\":\"ITEMS\",\"itemIds\":[\"abc\"],\"active\":false}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POINT_SHOP_ITEM_NOT_FOUND"));
        price(session, "{\"scope\":\"ITEMS\",\"itemIds\":[],\"price\":1200}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.itemIds").exists());
        price(session, "{\"scope\":\"ALL\",\"categories\":[\"sports\"],\"price\":1200}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.categories").value("이 범위에서는 보내지 않는 칸이에요."));
        // 모르는 범위 + 가격 범위 밖 — 두 칸이 같이
        price(session, "{\"scope\":\"EVERYTHING\",\"price\":0}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.scope").value("바꿀 범위를 골라 주세요.")).andExpect(jsonPath("$.error.fields.price").value("가격은 1~100,000P로 적어 주세요."));
        price(session, "{\"scope\":\"ALL\",\"price\":100001}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.price").exists());
        sale(session, "{\"scope\":\"ALL\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.active").exists());
        sale(session, "{\"scope\":\"CATEGORIES\",\"categories\":[\"hobby\"],\"active\":false}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.categories").exists());

        assertThat(avatarPrice(avatar)).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT id, price_point, active FROM point_shop_items WHERE kind = 'PROFILE_AVATAR' ORDER BY id")).isEqualTo(snapshot);
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action IN (?, ?)", AuditActions.POINT_SHOP_AVATAR_PRICE, AuditActions.POINT_SHOP_AVATAR_SALE))
                .isEqualTo(audits);
    }
}
