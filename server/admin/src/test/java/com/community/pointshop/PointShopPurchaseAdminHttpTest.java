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
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.permission.entity.Permission;
import com.community.pointshop.dto.response.PointShopPurchaseResultView;
import com.community.pointshop.entity.EquipSlot;
import com.community.pointshop.service.PointShopRefundService;
import com.community.pointshop.service.UserItemService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 구매 내역·예외 환불·포인트 상점 설정·회원 상세 변경권 수(커머스 1-2 — commerce-stage1-contract 「B」 환불·「1-2 화면 목업」 구매 내역·설정, commerce.md 7-3 D6·13-4).
 * 구매 줄의 {@code selfRefund}(완료 줄 — 지금 설정 기한·{@code USED}|{@code EXPIRED})·{@code refund}(환불 줄 — {@code SELF}|{@code EXCEPTION}), 거르기·쪽, 예외 환불은 셀프 환불 조건 밖도 되고
 * 포인트·재고가 돌아오며 감사는 돈 관련(5년), 두 번째는 409, 사유 5~200자. 실제 필터 체인·권한 인터셉터·PostgreSQL.
 */
class PointShopPurchaseAdminHttpTest extends PointShopAdminHttpTestBase {

    @Autowired
    private UserItemService userItems;
    @Autowired
    private PointShopRefundService refunds;

    private ResultActions refund(Cookie session, Object purchaseId, String reason) throws Exception {
        String body = reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}";
        return mvc.perform(withCsrf(post(BASE + "/purchases/" + purchaseId + "/refund"), session).header("Accept-Language", "ko").content(body));
    }

    private String purchasesOf(Cookie session, long member, String... params) throws Exception {
        var request = get(BASE + "/purchases").param("q", Long.toString(member)).cookie(session);
        for (int i = 0; i + 1 < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString();
    }

    @Test
    void 구매_줄은_산_때_사본과_셀프_환불_판정이고_거르기와_쪽이_된다() throws Exception {
        Cookie reader = login(operatorWith(Permission.POINTSHOP_READ));
        jdbc.update("UPDATE point_shop_settings SET refund_days = 7 WHERE id = 1");
        Instant longAgo = now().minus(10, ChronoUnit.DAYS);
        long member = buyerAt(10_000, longAgo.minusSeconds(60));
        long color = colorItem(800, null);
        long ticket = ticketItem(300);
        PointShopPurchaseResultView old = buyAt(member, ticket, longAgo);
        PointShopPurchaseResultView fresh = buy(member, color);
        PointShopPurchaseResultView used = buy(member, colorItem(900, null));
        userItems.equip(member, EquipSlot.NICKNAME_COLOR, Long.parseLong(used.userItemId()));
        // 산 뒤 품목 가격을 바꿔도 줄은 산 때 값
        jdbc.update("UPDATE point_shop_items SET price_point = 5000, name = '바뀐 이름' WHERE id = ?", color);

        String all = purchasesOf(reader, member);
        assertThat(JsonPath.<Integer>read(all, "$.data.total")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(all, "$.data.page")).isZero();
        assertThat(JsonPath.<Integer>read(all, "$.data.size")).isEqualTo(20);
        List<Map<String, Object>> rows = JsonPath.read(all, "$.data.items");
        Map<String, Object> freshRow = rows.stream().filter(r -> fresh.purchaseId().equals(r.get("id"))).findFirst().orElseThrow();
        assertThat(freshRow).containsEntry("itemId", Long.toString(color)).containsEntry("itemName", "시험 색").containsEntry("pricePoint", 800)
                .containsEntry("kind", "NICKNAME_COLOR").containsEntry("usageType", "PERMANENT").containsEntry("status", "COMPLETED").containsEntry("firstUsedAt", null)
                .containsEntry("refund", null).containsEntry("avatarId", null);
        assertThat(JsonPath.<List<String>>read(all, "$.data.items[?(@.id == '" + fresh.purchaseId() + "')].member.id")).containsExactly(Long.toString(member));
        assertThat(JsonPath.<List<Boolean>>read(all, "$.data.items[?(@.id == '" + fresh.purchaseId() + "')].selfRefund.allowed")).containsExactly(true);
        assertThat(JsonPath.<List<Object>>read(all, "$.data.items[?(@.id == '" + fresh.purchaseId() + "')].selfRefund.blockedBy")).containsExactly((Object) null);
        assertThat(JsonPath.<List<String>>read(all, "$.data.items[?(@.id == '" + old.purchaseId() + "')].selfRefund.blockedBy")).containsExactly("EXPIRED");
        assertThat(JsonPath.<List<String>>read(all, "$.data.items[?(@.id == '" + used.purchaseId() + "')].selfRefund.blockedBy")).containsExactly("USED");
        assertThat(JsonPath.<List<String>>read(all, "$.data.items[?(@.id == '" + used.purchaseId() + "')].firstUsedAt").get(0)).isNotNull();
        // 셀프 환불 기한 = 구매 시각 + 지금 설정 일수
        String until = JsonPath.<List<String>>read(all, "$.data.items[?(@.id == '" + old.purchaseId() + "')].selfRefund.until").get(0);
        assertThat(Instant.parse(until)).isEqualTo(longAgo.plus(7, ChronoUnit.DAYS));
        // 최신이 위
        List<String> order = JsonPath.read(all, "$.data.items[*].id");
        assertThat(order).containsExactly(used.purchaseId(), fresh.purchaseId(), old.purchaseId());

        // 쪽
        String page0 = purchasesOf(reader, member, "page", "0", "size", "2");
        assertThat(JsonPath.<List<String>>read(page0, "$.data.items[*].id")).containsExactly(used.purchaseId(), fresh.purchaseId());
        assertThat(JsonPath.<Integer>read(page0, "$.data.total")).isEqualTo(3);
        String page1 = purchasesOf(reader, member, "page", "1", "size", "2");
        assertThat(JsonPath.<List<String>>read(page1, "$.data.items[*].id")).containsExactly(old.purchaseId());
        // 종류 · 상태 · 기간
        assertThat(JsonPath.<List<String>>read(purchasesOf(reader, member, "kind", "NICKNAME_CHANGE_TICKET"), "$.data.items[*].id")).containsExactly(old.purchaseId());
        assertThat(JsonPath.<Integer>read(purchasesOf(reader, member, "status", "REFUNDED"), "$.data.total")).isZero();
        String recent = purchasesOf(reader, member, "from", Instant.now().minus(1, ChronoUnit.DAYS).toString(), "to", Instant.now().plus(1, ChronoUnit.DAYS).toString());
        assertThat(JsonPath.<List<String>>read(recent, "$.data.items[*].id")).containsExactlyInAnyOrder(used.purchaseId(), fresh.purchaseId());
        // 셀프 환불하면 refund.kind = SELF
        refunds.refundSelf(member, Long.parseLong(fresh.userItemId()));
        String refunded = purchasesOf(reader, member, "status", "REFUNDED");
        assertThat(JsonPath.<List<String>>read(refunded, "$.data.items[*].id")).containsExactly(fresh.purchaseId());
        assertThat(JsonPath.<String>read(refunded, "$.data.items[0].refund.kind")).isEqualTo("SELF");
        assertThat(JsonPath.<Object>read(refunded, "$.data.items[0].refund.operatorName")).isNull();
        assertThat(JsonPath.<Integer>read(refunded, "$.data.items[0].refund.refundedPoints")).isEqualTo(800);
        assertThat(JsonPath.<Object>read(refunded, "$.data.items[0].selfRefund")).isNull();
        assertInvariants(member);
    }

    @Test
    void 구매_내역_조건이_틀리면_400_fields() throws Exception {
        Cookie reader = login(operatorWith(Permission.POINTSHOP_READ));
        mvc.perform(get(BASE + "/purchases").param("status", "LOST").param("kind", "HAT").param("from", "어제").param("page", "x").cookie(reader)
                        .header("Accept-Language", "ko"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.fields.status").value("모르는 구매 상태로 거를 수 없어요."))
                .andExpect(jsonPath("$.error.fields.kind").value("모르는 품목 종류로 거를 수 없어요."))
                .andExpect(jsonPath("$.error.fields.from").value("날짜·시각 형식이 맞지 않아요.")).andExpect(jsonPath("$.error.fields.page").exists());
        mvc.perform(get(BASE + "/purchases").param("size", "101").cookie(reader)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.size").exists());
        mvc.perform(get(BASE + "/purchases").param("page", "-1").cookie(reader)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.page").exists());
        mvc.perform(get(BASE + "/purchases").param("from", "2026-10-02T00:00:00Z").param("to", "2026-10-01T00:00:00Z").cookie(reader))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.to").exists());
    }

    @Test
    void 예외_환불은_써서_셀프_환불이_안_되는_구매도_되고_포인트_재고가_돌아오고_감사는_돈_관련_두_번째는_409() throws Exception {
        long operator = operatorWith(Permission.POINTSHOP_REFUND, Permission.POINTSHOP_READ);
        Cookie session = login(operator);
        Instant at = now().minus(40, ChronoUnit.DAYS);
        long member = buyerAt(3000, at.minusSeconds(60));
        long limited = colorItem(1000, 3);
        PointShopPurchaseResultView bought = buyAt(member, limited, at);
        long purchaseId = Long.parseLong(bought.purchaseId());
        userItems.equip(member, EquipSlot.NICKNAME_COLOR, Long.parseLong(bought.userItemId()));
        assertThat(balance(member)).isEqualTo(2000);
        assertThat(stock(limited)).isEqualTo(2);

        // 사유 검사(5~200자) — 아무것도 바뀌지 않는다
        refund(session, purchaseId, "짧음").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").value("사유는 5~200자로 적어 주세요."));
        refund(session, purchaseId, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").exists());
        refund(session, purchaseId, "가".repeat(201)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").exists());
        assertThat(balance(member)).isEqualTo(2000);

        refund(session, purchaseId, "회원 요청 예외 처리").andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.purchaseId").value(Long.toString(purchaseId))).andExpect(jsonPath("$.data.refundedPoints").value(1000));
        assertThat(balance(member)).isEqualTo(3000);
        assertThat(stock(limited)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT status FROM point_shop_purchases WHERE id = ?", String.class, purchaseId)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT status FROM user_items WHERE purchase_id = ?", String.class, purchaseId)).isEqualTo("REVOKED");
        assertThat(count("SELECT count(*) FROM user_equipped WHERE user_id = ?", member)).as("장착도 풀린다").isZero();

        Map<String, Object> audit = lastAudit(AuditActions.POINT_SHOP_REFUND, Long.toString(purchaseId));
        assertThat(((Number) audit.get("actor_id")).longValue()).isEqualTo(operator);
        assertThat(audit.get("result")).isEqualTo("SUCCESS");
        assertThat(audit.get("target_type")).isEqualTo("POINT_SHOP_PURCHASE");
        assertThat(audit.get("retention_class")).isEqualTo(AuditRetentionClass.MONEY.name());
        assertThat(AuditRetention.classify(AuditActions.POINT_SHOP_REFUND)).isEqualTo(AuditRetentionClass.MONEY);
        assertThat(audit.get("detail").toString()).contains("회원 요청 예외 처리", "\"refundedPoints\":1000", "\"selfRefundable\":false");

        // 구매 줄의 환불 기록
        String row = purchasesOf(session, member);
        assertThat(JsonPath.<String>read(row, "$.data.items[0].refund.kind")).isEqualTo("EXCEPTION");
        assertThat(JsonPath.<String>read(row, "$.data.items[0].refund.operatorName")).isEqualTo(operatorName(operator));
        assertThat(JsonPath.<String>read(row, "$.data.items[0].refund.reason")).isEqualTo("회원 요청 예외 처리");
        assertThat(JsonPath.<String>read(row, "$.data.items[0].status")).isEqualTo("REFUNDED");

        // 두 번째 — 409, 포인트는 그대로
        refund(session, purchaseId, "회원 요청 예외 처리").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_ALREADY_REFUNDED"));
        assertThat(balance(member)).isEqualTo(3000);
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = ? AND target_id = ?", AuditActions.POINT_SHOP_REFUND, Long.toString(purchaseId))).isEqualTo(1);
        // 없는 구매
        refund(session, 999_999_999L, "없는 구매 환불").andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_PURCHASE_NOT_FOUND"));
        refund(session, "abc", "없는 구매 환불").andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_PURCHASE_NOT_FOUND"));
        assertInvariants(member);
    }

    @Test
    void 설정_조회_저장은_1_30_이고_감사가_남고_셀프_환불_판정이_지금_값을_따른다() throws Exception {
        long operator = operatorWith(Permission.POINTSHOP_MANAGE, Permission.POINTSHOP_READ);
        Cookie session = login(operator);
        jdbc.update("UPDATE point_shop_settings SET refund_days = 7 WHERE id = 1");

        mvc.perform(get(BASE + "/settings").cookie(session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.refundDays").value(7)).andExpect(jsonPath("$.data.defaultRefundDays").value(7));
        for (String bad : List.of("0", "31", "null")) {
            mvc.perform(withCsrf(put(BASE + "/settings"), session).header("Accept-Language", "ko").content("{\"refundDays\":" + bad + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.refundDays").value("환불 기한은 1~30일로 적어 주세요."));
        }
        assertThat(jdbc.queryForObject("SELECT refund_days FROM point_shop_settings WHERE id = 1", Integer.class)).isEqualTo(7);

        int before = count("SELECT count(*) FROM audit_logs WHERE action = ? AND actor_id = ?", AuditActions.POINT_SHOP_SETTINGS_UPDATE, operator);
        mvc.perform(withCsrf(put(BASE + "/settings"), session).content("{\"refundDays\":30}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundDays").value(30)).andExpect(jsonPath("$.data.updatedBy").value(operatorName(operator)))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
        Map<String, Object> audit = jdbc.queryForMap("SELECT target_type, retention_class, detail FROM audit_logs WHERE action = ? AND actor_id = ? ORDER BY occurred_at DESC LIMIT 1",
                AuditActions.POINT_SHOP_SETTINGS_UPDATE, operator);
        assertThat(audit.get("target_type")).isEqualTo("POINT_SHOP_SETTINGS");
        assertThat(audit.get("retention_class")).isEqualTo(AuditRetentionClass.GENERAL.name());
        assertThat(audit.get("detail").toString()).contains("\"refundDays\":7", "\"refundDays\":30");
        // 같은 값은 감사 없이
        mvc.perform(withCsrf(put(BASE + "/settings"), session).content("{\"refundDays\":30}")).andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = ? AND actor_id = ?", AuditActions.POINT_SHOP_SETTINGS_UPDATE, operator)).isEqualTo(before + 1);

        // 10일 전에 산 구매가 새 기한(30일)으로는 셀프 환불 가능
        Instant at = now().minus(10, ChronoUnit.DAYS);
        long member = buyerAt(2000, at.minusSeconds(60));
        buyAt(member, colorItem(500, null), at);
        String rows = purchasesOf(session, member);
        assertThat(JsonPath.<Boolean>read(rows, "$.data.items[0].selfRefund.allowed")).isTrue();
        assertThat(Instant.parse(JsonPath.<String>read(rows, "$.data.items[0].selfRefund.until"))).isEqualTo(at.plus(30, ChronoUnit.DAYS));
    }

    @Test
    void 회원_상세의_닉네임_변경권_수는_안_쓴_변경권_장수() throws Exception {
        long member = buyer(5000);
        long ticket = ticketItem(500);
        Cookie reader = login(operatorWith(Permission.MEMBER_READ));
        Cookie refunder = login(operatorWith(Permission.POINTSHOP_REFUND));

        mvc.perform(get("/api/admin/members/" + member).cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.gradePoint.nicknameTicketCount").value(0));
        buy(member, ticket);
        String second = buy(member, ticket).purchaseId();
        mvc.perform(get("/api/admin/members/" + member).cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.gradePoint.nicknameTicketCount").value(2));
        refund(refunder, second, "변경권 하나 돌려줌").andExpect(status().isOk());
        mvc.perform(get("/api/admin/members/" + member).cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.gradePoint.nicknameTicketCount").value(1));
        // 탈퇴 회원은 구획이 없다(기존 규칙)
        long gone = withdrawnMember(Instant.now());
        mvc.perform(get("/api/admin/members/" + gone).cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.gradePoint").value(nullValue()));
        assertInvariants(member);
    }
}
