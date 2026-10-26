package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.support.MoneyRequestGuard;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 내 아이템 API(커머스 1-2 — commerce-stage1-contract 「1-2 화면 목업」 API 후보) — 목록, 장착·해제·만료 장착 거절, 셀프 환불(성공·장착 뒤 409·기한 지남 409·같은 키 재요청).
 * 실제 PostgreSQL, 시계는 옮길 수 있다(기간제 만료·환불 기한).
 */
class MyItemHttpTest extends PointShopApiTestBase {

    private ResultActions equip(long member, String slot, String userItemId) throws Exception {
        return mvc.perform(as(member, put("/api/me/equipped/" + slot).contentType(MediaType.APPLICATION_JSON).content("{\"userItemId\":\"" + userItemId + "\"}")));
    }

    private ResultActions refund(long member, long userItemId, String idempotencyKey) throws Exception {
        return mvc.perform(as(member, post("/api/me/items/" + userItemId + "/refund").header(MoneyRequestGuard.HEADER, idempotencyKey)));
    }

    // ── 목록 ────────────────────────────────────────────────────────────────

    @Test
    void 내_아이템_목록은_보유_한_줄씩_환불_가능과_기한을_싣는다() throws Exception {
        long member = member();
        grant(member, 3_000);
        long color = colorItem(500, "#E11D48");
        long held = buy(member, color);

        String list = body(mvc.perform(as(member, get("/api/me/items"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.refundDays").isNumber()));
        assertThat(JsonPath.<List<String>>read(list, "$.data.items[*].id")).containsExactly(Long.toString(held));
        assertThat(JsonPath.<String>read(list, "$.data.items[0].itemId")).isEqualTo(Long.toString(color));
        assertThat(JsonPath.<String>read(list, "$.data.items[0].kind")).isEqualTo("NICKNAME_COLOR");
        assertThat(JsonPath.<String>read(list, "$.data.items[0].color")).isEqualTo("#E11D48");
        assertThat(JsonPath.<String>read(list, "$.data.items[0].status")).isEqualTo("OWNED");
        assertThat(JsonPath.<Boolean>read(list, "$.data.items[0].refundable")).isTrue();
        assertThat(JsonPath.<Boolean>read(list, "$.data.items[0].equipped")).isFalse();
        assertThat(JsonPath.<Boolean>read(list, "$.data.items[0].inUse")).isFalse();
        assertThat(JsonPath.<Integer>read(list, "$.data.items[0].pricePaid")).isEqualTo(500);
        assertThat(JsonPath.<String>read(list, "$.data.items[0].refundUntil")).isNotNull();

        mvc.perform(get("/api/me/items")).andExpect(status().isUnauthorized());
    }

    // ── 장착 ────────────────────────────────────────────────────────────────

    @Test
    void 정지_중이면_장착도_해제도_403_ACCOUNT_RESTRICTED_이고_자리는_그대로다() throws Exception {
        long member = member();
        grant(member, 2_000);
        long color = buy(member, colorItem(500, "#22C55E"));
        long border = buy(member, borderItem(500, "FOREST", null));
        equip(member, "NICKNAME_COLOR", Long.toString(color)).andExpect(status().isOk());
        suspend(member);

        equip(member, "PROFILE_BORDER", Long.toString(border)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_RESTRICTED"));
        mvc.perform(as(member, delete("/api/me/equipped/NICKNAME_COLOR"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_RESTRICTED"));
        mvc.perform(as(member, get("/api/me/items"))).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_equipped WHERE user_id = ? AND slot = 'NICKNAME_COLOR'", Integer.class, member)).as("해제되지 않았다").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_equipped WHERE user_id = ? AND slot = 'PROFILE_BORDER'", Integer.class, member)).isZero();
        assertThat(jdbc.queryForObject("SELECT first_used_at FROM user_items WHERE id = ?", java.sql.Timestamp.class, border)).as("막힌 장착은 사용이 아니다").isNull();
    }


    @Test
    void 장착하면_자리에_보이고_해제하면_비며_장착은_사용이라_환불할_수_없다() throws Exception {
        long member = member();
        grant(member, 3_000);
        long color = buy(member, colorItem(500, "#0EA5E9"));
        long border = buy(member, borderItem(500, "OCEAN", null));

        equip(member, "NICKNAME_COLOR", Long.toString(color)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.nicknameColor.userItemId").value(Long.toString(color)))
                .andExpect(jsonPath("$.data.nicknameColor.color").value("#0EA5E9"))
                .andExpect(jsonPath("$.data.profileBorder").isEmpty());
        equip(member, "PROFILE_BORDER", Long.toString(border)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileBorder.borderStyle").value("OCEAN"))
                .andExpect(jsonPath("$.data.nicknameColor.color").value("#0EA5E9"));

        mvc.perform(as(member, delete("/api/me/equipped/NICKNAME_COLOR"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nicknameColor").isEmpty()).andExpect(jsonPath("$.data.profileBorder.borderStyle").value("OCEAN"));
        mvc.perform(as(member, delete("/api/me/equipped/NICKNAME_COLOR"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.nicknameColor").isEmpty());

        // 풀어도 사용 기록(first_used_at)은 남는다 — 환불 불가
        refund(member, color, key()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_REFUND_NOT_ALLOWED"))
                .andExpect(jsonPath("$.error.blockedBy").value("USED"));
        assertThat(balance(member)).isEqualTo(2_000);
    }

    @Test
    void 자리와_종류가_다르거나_남의_줄이면_거절() throws Exception {
        long member = member();
        long other = member();
        grant(member, 3_000);
        grant(other, 3_000);
        long color = buy(member, colorItem(500, "#22C55E"));
        long theirs = buy(other, colorItem(500, "#A855F7"));
        long ticket = buy(member, seedTicket());

        equip(member, "PROFILE_BORDER", Long.toString(color)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("USER_ITEM_NOT_EQUIPPABLE"));
        equip(member, "NICKNAME_COLOR", Long.toString(ticket)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("USER_ITEM_NOT_EQUIPPABLE"));
        equip(member, "NICKNAME_COLOR", Long.toString(theirs)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("USER_ITEM_NOT_FOUND"));
        equip(member, "NICKNAME_COLOR", "abc").andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("USER_ITEM_NOT_FOUND"));
        equip(member, "HAT", Long.toString(color)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
        assertThat(count("SELECT count(*) FROM user_equipped WHERE user_id = ?", member)).isZero();
    }

    @Test
    void 기간이_끝난_기간제는_장착을_거절하고_장착해_둔_것은_해제로_보인다() throws Exception {
        long member = member();
        grant(member, 3_000);
        long item = periodColorItem(300, "#EA580C", 1);
        long first = buy(member, item);
        equip(member, "NICKNAME_COLOR", Long.toString(first)).andExpect(status().isOk()).andExpect(jsonPath("$.data.nicknameColor.expiresAt").isNotEmpty());

        clock.advance(Duration.ofDays(2));
        mvc.perform(as(member, get("/api/me/items"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].expired").value(true))
                .andExpect(jsonPath("$.data.items[0].equipped").value(false));
        equip(member, "NICKNAME_COLOR", Long.toString(first)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("USER_ITEM_EXPIRED"));
        mvc.perform(as(member, delete("/api/me/equipped/NICKNAME_COLOR"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.nicknameColor").isEmpty());
    }

    // ── 셀프 환불 ───────────────────────────────────────────────────────────

    @Test
    void 안_쓴_아이템은_환불되고_같은_키로_다시_보내면_처음_응답_그대로_포인트는_한_번() throws Exception {
        long member = member();
        grant(member, 3_000);
        long item = borderItem(800, "GOLD", 5);
        long held = buy(member, item);
        assertThat(jdbc.queryForObject("SELECT stock FROM point_shop_items WHERE id = ?", Integer.class, item)).isEqualTo(4);
        String key = key();

        String first = body(refund(member, held, key).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.refundedPoints").value(800)).andExpect(jsonPath("$.data.balance").value(3_000)));
        String again = body(refund(member, held, key).andExpect(status().isOk()).andExpect(header().string(MoneyRequestGuard.REPLAYED_HEADER, "true")));
        assertThat((Object) JsonPath.read(again, "$.data")).isEqualTo(JsonPath.read(first, "$.data"));

        assertThat(balance(member)).isEqualTo(3_000);
        assertThat(count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'POINT_SHOP_REFUND'", member)).as("사용 취소 한 줄").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM user_items WHERE id = ?", String.class, held)).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("SELECT stock FROM point_shop_items WHERE id = ?", Integer.class, item)).as("재고 복원").isEqualTo(5);

        // 다른 키로 또 — 이미 환불함
        refund(member, held, key()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_ALREADY_REFUNDED"));
        assertInvariants(member);
    }

    @Test
    void 환불_기한이_지나면_409_EXPIRED() throws Exception {
        long member = member();
        grant(member, 3_000);
        long held = buy(member, colorItem(400, "#F43F5E"));
        int refundDays = jdbc.queryForObject("SELECT refund_days FROM point_shop_settings WHERE id = 1", Integer.class);

        clock.advance(Duration.ofDays(refundDays).plusMinutes(1));
        refund(member, held, key()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_REFUND_NOT_ALLOWED"))
                .andExpect(jsonPath("$.error.blockedBy").value("EXPIRED"));
        mvc.perform(as(member, get("/api/me/items"))).andExpect(jsonPath("$.data.items[0].refundable").value(false));
        assertThat(balance(member)).isEqualTo(2_600);
    }

    @Test
    void 환불은_멱등_키가_필요하고_남의_줄_없는_줄은_404() throws Exception {
        long member = member();
        long other = member();
        grant(other, 1_000);
        long theirs = buy(other, colorItem(400, "#FB7185"));

        mvc.perform(as(member, post("/api/me/items/" + theirs + "/refund"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        refund(member, theirs, key()).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("USER_ITEM_NOT_FOUND"));
        mvc.perform(as(member, post("/api/me/items/abc/refund").header(MoneyRequestGuard.HEADER, key()))).andExpect(status().isNotFound());
        assertThat(balance(other)).isEqualTo(600);
    }
}
