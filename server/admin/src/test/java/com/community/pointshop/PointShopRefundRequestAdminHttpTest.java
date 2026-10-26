package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.audit.dto.AuditActions;
import com.community.permission.entity.Permission;
import com.community.pointshop.dto.SelfRefundOutcome;
import com.community.pointshop.service.PointShopRefundService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 관리자 환불 신청 처리(commerce-stage1-contract 「1-2 정지 중 환불 신청」) — 구매 내역의 「환불 신청 대기」 거르기·줄의 {@code refundRequest}, 수락(환불 종류 REQUEST·감사 돈 관련)·
 * 거절(사유 400·감사 돈 관련)·이미 결정됨 409·없는 신청 404·권한({@code pointshop.refund} 없으면 403)·신청이 운영자 알림을 낸다. 관리자 앱 문맥 + 실제 PostgreSQL.
 */
class PointShopRefundRequestAdminHttpTest extends PointShopAdminHttpTestBase {

    @Autowired
    private PointShopRefundService refunds;

    private ResultActions decide(Cookie session, Object requestId, String action, String reason) throws Exception {
        String body = reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}";
        return mvc.perform(withCsrf(post(BASE + "/refund-requests/" + requestId + "/" + action), session).header("Accept-Language", "ko").content(body));
    }

    /** 정지 중인 회원의 셀프 환불 → 신청 ID */
    private long requested(long member, int price) {
        long item = colorItem(price, null);
        long userItem = Long.parseLong(buy(member, item).userItemId());
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", member);
        SelfRefundOutcome outcome = refunds.refundSelf(member, userItem);
        assertThat(outcome.isRequest()).isTrue();
        return Long.parseLong(outcome.requested().requestId());
    }

    @Test
    void 대기_거르기와_줄의_신청_칸_수락은_환불_종류_REQUEST_와_돈_관련_감사_신청은_운영자_알림() throws Exception {
        long handler = operatorWith(Permission.POINTSHOP_READ, Permission.POINTSHOP_REFUND);
        Cookie session = login(handler);
        long member = buyer(2_000);
        buy(member, colorItem(100, null)); // 신청 없는 다른 구매(거르기 밖)
        long requestId = requested(member, 700);

        assertThat(count("SELECT count(*) FROM admin_notifications WHERE operator_id = ? AND event_code = 'POINT_SHOP_REFUND_REQUESTED'", handler)).as("운영자 알림")
                .isEqualTo(1);
        String pending = mvc.perform(get(BASE + "/purchases").param("q", Long.toString(member)).param("refundRequest", "PENDING").cookie(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Integer>read(pending, "$.data.total")).isEqualTo(1);
        assertThat(JsonPath.<String>read(pending, "$.data.items[0].refundRequest.id")).isEqualTo(Long.toString(requestId));
        assertThat(JsonPath.<String>read(pending, "$.data.items[0].refundRequest.status")).isEqualTo("PENDING");
        assertThat(JsonPath.<String>read(pending, "$.data.items[0].refundRequest.requestedAt")).isNotNull();
        mvc.perform(get(BASE + "/purchases").param("refundRequest", "LATER").cookie(session)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.refundRequest").value("모르는 환불 신청 상태로 거를 수 없어요."));

        decide(session, requestId, "approve", null).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.status").value("APPROVED")).andExpect(jsonPath("$.data.refundedPoints").value(700))
                .andExpect(jsonPath("$.data.requestId").value(Long.toString(requestId)));

        assertThat(balance(member)).isEqualTo(1_900);
        String refunded = mvc.perform(get(BASE + "/purchases").param("q", Long.toString(member)).param("status", "REFUNDED").cookie(session))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(refunded, "$.data.items[0].refund.kind")).isEqualTo("REQUEST");
        assertThat(JsonPath.<String>read(refunded, "$.data.items[0].refund.operatorName")).isNotNull();
        assertThat(JsonPath.<String>read(refunded, "$.data.items[0].refundRequest.status")).isEqualTo("APPROVED");
        String purchaseId = JsonPath.read(refunded, "$.data.items[0].id");
        Map<String, Object> audit = lastAudit(AuditActions.POINT_SHOP_REFUND, purchaseId);
        assertThat(audit).containsEntry("retention_class", "MONEY").containsEntry("actor_id", handler);
        assertThat((String) audit.get("detail")).contains("\"kind\":\"REQUEST\"").contains(Long.toString(requestId));

        decide(session, requestId, "approve", null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_REFUND_REQUEST_NOT_PENDING"));
        decide(session, requestId, "reject", "다시 거절해 봐요").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("POINT_SHOP_REFUND_REQUEST_NOT_PENDING"));
        assertInvariants(member);
    }

    @Test
    void 거절은_사유_5에서_200자_돈_관련_감사_아이템과_포인트는_그대로() throws Exception {
        long handler = operatorWith(Permission.POINTSHOP_READ, Permission.POINTSHOP_REFUND);
        Cookie session = login(handler);
        long member = buyer(1_000);
        long requestId = requested(member, 400);

        decide(session, requestId, "reject", "짧음").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").exists());
        decide(session, requestId, "reject", "사용 흔적이 확인됐어요").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.refundedPoints").value(0));

        assertThat(balance(member)).isEqualTo(600);
        String row = mvc.perform(get(BASE + "/purchases").param("q", Long.toString(member)).cookie(session)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(row, "$.data.items[0].refundRequest.status")).isEqualTo("REJECTED");
        assertThat(JsonPath.<String>read(row, "$.data.items[0].refundRequest.rejectReason")).isEqualTo("사용 흔적이 확인됐어요");
        assertThat(JsonPath.<String>read(row, "$.data.items[0].status")).isEqualTo("COMPLETED");
        Map<String, Object> audit = lastAudit(AuditActions.POINT_SHOP_REFUND_REJECT, JsonPath.read(row, "$.data.items[0].id"));
        assertThat(audit).containsEntry("retention_class", "MONEY");
        assertThat((String) audit.get("detail")).contains("사용 흔적이 확인됐어요");
        assertThat(count("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = 'POINT_SHOP_REFUND_REJECTED'", member)).isEqualTo(1);
    }

    @Test
    void 권한이_없으면_403_없는_신청은_404_대기_중이면_예외_환불도_409() throws Exception {
        Cookie readerOnly = login(operatorWith(Permission.POINTSHOP_READ));
        Cookie handler = login(operatorWith(Permission.POINTSHOP_REFUND));
        long member = buyer(1_000);
        long requestId = requested(member, 300);

        decide(readerOnly, requestId, "approve", null).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        decide(readerOnly, requestId, "reject", "권한 없이 거절").andExpect(status().isForbidden());
        decide(handler, 999_999_999L, "approve", null).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_REFUND_REQUEST_NOT_FOUND"));
        decide(handler, "abc", "reject", "형식이 틀린 번호").andExpect(status().isNotFound());
        String purchaseId = jdbc.queryForObject("SELECT purchase_id::text FROM point_shop_refund_requests WHERE id = ?", String.class, requestId);
        mvc.perform(withCsrf(post(BASE + "/purchases/" + purchaseId + "/refund"), handler).content("{\"reason\":\"예외로 돌려 드려요\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_REFUND_PENDING"));
        assertThat(count("SELECT count(*) FROM point_shop_refund_requests WHERE id = ? AND status = 'PENDING'", requestId)).isEqualTo(1);
        assertThat(balance(member)).isEqualTo(700);
    }
}
