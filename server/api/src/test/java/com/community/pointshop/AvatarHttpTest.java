package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.pointshop.service.AvatarCatalog;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 프로필 아바타 API(커머스 1-2 결정 14 — commerce-stage1-contract 「1-2 화면 목업」 아바타) — 목록(비로그인·로그인), 고르기(무료는 누구나 · 유료 미보유 409 · 없는 id 404 · 형식 400 ·
 * 이메일 미인증·정지 403 · 유료를 고르면 환불 불가 · 내 프로필·작성자 표시의 {@code avatarUrl}). 실제 PostgreSQL.
 */
class AvatarHttpTest extends PointShopApiTestBase {

    @Autowired
    private AvatarCatalog avatars;

    private String freeAvatar() {
        return avatars.all().stream().filter(AvatarCatalog.Avatar::free).filter(a -> !a.id().equals(AvatarCatalog.DEFAULT_ID)).findFirst().orElseThrow().id();
    }

    private String paidAvatar() {
        return jdbc.queryForObject("SELECT avatar_id FROM point_shop_items WHERE avatar_id IS NOT NULL AND active ORDER BY sort_order, id LIMIT 1", String.class);
    }

    private ResultActions select(long member, String avatarId) throws Exception {
        return mvc.perform(as(member, put("/api/me/profile/avatar").contentType(MediaType.APPLICATION_JSON).content("{\"avatarId\":\"" + avatarId + "\"}")));
    }

    private static String url(String id) {
        return AvatarCatalog.URL_PREFIX + id + ".svg";
    }

    @Test
    void 비로그인도_목록을_읽고_잔액_0_보유_고른_것_없음() throws Exception {
        String body = body(mvc.perform(get("/api/avatars")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.balance").value(0)).andExpect(jsonPath("$.data.selectedId").isEmpty())
                .andExpect(jsonPath("$.data.refundDays").isNumber()));
        List<Object> items = JsonPath.read(body, "$.data.items");
        assertThat(items).hasSize(avatars.all().size());
        assertThat(JsonPath.<List<Boolean>>read(body, "$.data.items[*].owned")).containsOnly(false);
        assertThat(JsonPath.<List<Boolean>>read(body, "$.data.items[*].selected")).containsOnly(false);
        assertThat(JsonPath.<List<Integer>>read(body, "$.data.categories[*].count").stream().mapToInt(Integer::intValue).sum()).isEqualTo(avatars.all().size());
        String paid = paidAvatar();
        assertThat(JsonPath.<List<Object>>read(body, "$.data.items[?(@.id == '" + paid + "')].itemId")).containsExactly(Long.toString(avatarItem(paid)));
        assertThat(JsonPath.<List<Object>>read(body, "$.data.items[?(@.id == '" + paid + "')].free")).containsExactly(false);
    }

    @Test
    void 로그인하면_잔액_보유_고른_것_환불_가능을_싣는다() throws Exception {
        long member = member();
        grant(member, 3_000);
        String paid = paidAvatar();
        buy(member, avatarItem(paid));

        String body = body(mvc.perform(as(member, get("/api/avatars"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.balance").value(3_000 - priceOf(paid))));
        assertThat(JsonPath.<List<Boolean>>read(body, "$.data.items[?(@.id == '" + paid + "')].owned")).containsExactly(true);
        assertThat(JsonPath.<List<Boolean>>read(body, "$.data.items[?(@.id == '" + paid + "')].refundable")).containsExactly(true);
        assertThat(JsonPath.<List<Boolean>>read(body, "$.data.items[?(@.free == true)].owned")).containsOnly(true);
    }

    private int priceOf(String avatarId) {
        return jdbc.queryForObject("SELECT price_point FROM point_shop_items WHERE avatar_id = ?", Integer.class, avatarId);
    }

    @Test
    void 무료_아바타는_누구나_고르고_사업자_회원도_되며_같은_것을_다시_골라도_200() throws Exception {
        String free = freeAvatar();
        long member = member();
        mvc.perform(as(member, get("/api/me/profile"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarUrl").value(url(AvatarCatalog.DEFAULT_ID)));

        select(member, free).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.avatarUrl").value(url(free)))
                .andExpect(jsonPath("$.data.nickname").value(nicknameOf(member)));
        select(member, free).andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarUrl").value(url(free)));
        mvc.perform(as(member, get("/api/avatars"))).andExpect(jsonPath("$.data.selectedId").value(free));
        assertThat(jdbc.queryForObject("SELECT profile_image FROM profiles WHERE user_id = ?", String.class, member)).isEqualTo("avatar:" + free);

        long business = businessMember();
        select(business, free).andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarUrl").value(url(free)));
    }

    @Test
    void 유료는_가진_것만_고르고_고르면_환불할_수_없다() throws Exception {
        long member = member();
        grant(member, 3_000);
        String paid = paidAvatar();

        select(member, paid).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("AVATAR_NOT_OWNED"));
        long held = buy(member, avatarItem(paid));
        select(member, paid).andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarUrl").value(url(paid)));

        mvc.perform(as(member, get("/api/me/items"))).andExpect(jsonPath("$.data.items[0].inUse").value(true))
                .andExpect(jsonPath("$.data.items[0].refundable").value(false)).andExpect(jsonPath("$.data.items[0].avatarId").value(paid));
        mvc.perform(as(member, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/me/items/" + held + "/refund")
                        .header(com.community.commerce.support.MoneyRequestGuard.HEADER, key())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_REFUND_NOT_ALLOWED"))
                .andExpect(jsonPath("$.error.blockedBy").value("USED"));
    }

    @Test
    void 없는_아바타는_404_형식이_틀리면_400_자격이_없으면_403_로그인_안_하면_401() throws Exception {
        long member = member();
        select(member, "no-such-avatar").andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("AVATAR_NOT_FOUND"));
        select(member, "Bad Id!").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.fields.avatarId").exists());

        long unverified = unverifiedMember();
        select(unverified, freeAvatar()).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));
        long suspended = member();
        suspend(suspended);
        select(suspended, freeAvatar()).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_RESTRICTED"));

        mvc.perform(put("/api/me/profile/avatar").contentType(MediaType.APPLICATION_JSON).content("{\"avatarId\":\"basic-default\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT profile_image FROM profiles WHERE user_id = ?", String.class, member)).isNull();
    }
}
