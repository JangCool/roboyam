package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.support.MoneyRequestGuard;
import com.community.membernotification.dto.response.MemberNotificationItemView;
import com.community.membernotification.service.MemberNotificationService;
import com.community.pointshop.service.PointShopRefundService;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 정지 중 환불 신청(commerce-stage1-contract 「1-2 정지 중 환불 신청」) — 회원 API: 같은 환불 주소가 202 + 신청 모양, 같은 키 다시 보내기는 처음 응답 그대로(202), 대기 중 409·장착 409,
 * 내 아이템의 {@code refundNeedsApproval}·{@code refundRequest}, 수락·거절 알림이 알림함에 보인다(제목·본문·링크). 실제 PostgreSQL.
 */
class RefundRequestHttpTest extends PointShopApiTestBase {

    @Autowired
    private PointShopRefundService refunds;

    @Autowired
    private MemberNotificationService notifications;

    private ResultActions refund(long member, long userItemId, String idempotencyKey) throws Exception {
        return mvc.perform(as(member, post("/api/me/items/" + userItemId + "/refund").header(MoneyRequestGuard.HEADER, idempotencyKey)));
    }

    @Test
    void 정지_중_환불은_202_신청이고_같은_키는_처음_응답_그대로_포인트와_아이템은_그대로() throws Exception {
        long member = member();
        grant(member, 1_000);
        long held = buy(member, colorItem(600, "#CC3300"));
        suspend(member);
        String key = key();

        String first = body(refund(member, held, key).andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.status").value("PENDING")).andExpect(jsonPath("$.data.requestId").isString())
                .andExpect(jsonPath("$.data.requestedAt").isString()));
        String again = body(refund(member, held, key).andExpect(status().isAccepted()).andExpect(header().string(MoneyRequestGuard.REPLAYED_HEADER, "true")));
        assertThat((Object) JsonPath.read(again, "$.data")).isEqualTo(JsonPath.read(first, "$.data"));
        assertThat(count("SELECT count(*) FROM point_shop_refund_requests WHERE user_id = ?", member)).isEqualTo(1);
        assertThat(balance(member)).isEqualTo(400);

        refund(member, held, key()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_REFUND_PENDING"));

        String list = body(mvc.perform(as(member, get("/api/me/items"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.refundNeedsApproval").value(true)));
        assertThat(JsonPath.<String>read(list, "$.data.items[0].refundRequest.status")).isEqualTo("PENDING");
        assertThat(JsonPath.<String>read(list, "$.data.items[0].refundRequest.id")).isEqualTo(JsonPath.read(first, "$.data.requestId"));
        assertThat(JsonPath.<Boolean>read(list, "$.data.items[0].refundable")).isFalse();
        assertInvariants(member);
    }

    @Test
    void 정지가_풀려도_대기_중이면_장착은_409_이고_수락하면_알림함에_수락_알림() throws Exception {
        long member = member();
        grant(member, 1_000);
        long held = buy(member, colorItem(600, "#CC3311"));
        suspend(member);
        String requestId = JsonPath.read(body(refund(member, held, key()).andExpect(status().isAccepted())), "$.data.requestId");
        jdbc.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", member);

        mvc.perform(as(member, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/me/equipped/NICKNAME_COLOR")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"userItemId\":\"" + held + "\"}")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_REFUND_PENDING"));
        assertThat(body(mvc.perform(as(member, get("/api/me/items"))))).contains("\"refundNeedsApproval\":false");

        tx.executeWithoutResult(status -> refunds.approveRequest(Long.parseLong(requestId), operator, null));

        assertThat(balance(member)).isEqualTo(1_000);
        List<MemberNotificationItemView> items = notifications.list(member, null, 10, null).items();
        MemberNotificationItemView notice = items.stream().filter(i -> i.type().equals("POINT_SHOP_REFUND_APPROVED")).findFirst().orElseThrow();
        assertThat(notice.category()).isEqualTo("system");
        assertThat(notice.title()).contains("수락");
        assertThat(notice.body()).contains("600");
        assertThat(notice.link()).isEqualTo("/me/items");
        assertInvariants(member);
    }

    @Test
    void 거절하면_알림함에_사유가_보이고_정지_중_다시_신청은_409_REJECTED() throws Exception {
        long member = member();
        grant(member, 1_000);
        long held = buy(member, colorItem(300, "#CC3322"));
        suspend(member);
        String requestId = JsonPath.read(body(refund(member, held, key()).andExpect(status().isAccepted())), "$.data.requestId");

        tx.executeWithoutResult(status -> refunds.rejectRequest(Long.parseLong(requestId), "사용 흔적이 확인됐어요", operator, null));

        MemberNotificationItemView notice = notifications.list(member, null, 10, null).items().stream().filter(i -> i.type().equals("POINT_SHOP_REFUND_REJECTED"))
                .findFirst().orElseThrow();
        assertThat(notice.title()).contains("거절");
        assertThat(notice.body()).isEqualTo("사용 흔적이 확인됐어요");
        assertThat(notice.link()).isEqualTo("/me/items");
        refund(member, held, key()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_REFUND_REJECTED"));
        String list = body(mvc.perform(as(member, get("/api/me/items"))));
        assertThat(JsonPath.<String>read(list, "$.data.items[0].refundRequest.rejectReason")).isEqualTo("사용 흔적이 확인됐어요");
        assertThat(balance(member)).isEqualTo(700);
    }
}
