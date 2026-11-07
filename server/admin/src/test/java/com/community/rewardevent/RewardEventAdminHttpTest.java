package com.community.rewardevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.audit.dto.AuditActions;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.member.service.MemberMasking;
import com.community.permission.entity.Permission;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 관리자 보상 이벤트 API(docs/features/event-admin-contract.md 「API」) — 실제 필터 체인·권한 인터셉터·PostgreSQL. 입구 권한(읽기·처리·지급) · 만들기 201 → 시작 → 당첨자 → 마감 → 지급
 * 흐름의 응답 모양(번호는 문자열) · 회원 이메일은 {@code member.pii} 가 없으면 가린다 · 일괄 포인트 지급은 고액 권한(없으면 403 + 거부 감사) · 없는 이벤트 404 · 상태 409.
 */
class RewardEventAdminHttpTest extends Commerce04AdminTestBase {

    private static final String ROOT = "/api/admin/reward-events";

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, Cookie session, String body) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r = withCsrf(req, session).header("Accept-Language", "ko");
        return mvc.perform(body == null ? r : r.content(body));
    }

    private static String selectionBody(int points) {
        Instant now = Instant.now();
        return "{\"name\":\"HTTP 당첨 이벤트\",\"memo\":\"\",\"startAt\":\"" + now.minus(Duration.ofHours(1)) + "\",\"endAt\":\"" + now.plus(Duration.ofDays(2))
                + "\",\"noticeUrl\":\"/boards/notice/1\",\"mode\":\"SELECTION\",\"firstCome\":null,\"selection\":{\"sources\":[\"MANUAL\"],\"commentDraw\":null},"
                + "\"bulk\":null,\"excludeOperators\":true,\"tiers\":[{\"id\":\"tmp-1\",\"name\":\"1등\",\"quota\":3,\"rewards\":[{\"id\":\"tmp-l1\",\"kind\":\"POINT\","
                + "\"points\":" + points + "}]}]}";
    }

    @Test
    void 조회_권한만으로는_만들_수_없고_처리_권한만으로는_지급할_수_없다_없는_이벤트는_404() throws Exception {
        Cookie reader = login(operatorWith(Permission.REWARD_EVENT_READ));
        send(post(ROOT), reader, selectionBody(100)).andExpect(status().isForbidden());
        mvc.perform(get(ROOT).cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.counts.DRAFT").exists());
        mvc.perform(get(ROOT + "/999999999999").cookie(reader)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("REWARD_EVENT_NOT_FOUND"));

        Cookie manager = login(operatorWith(Permission.REWARD_EVENT_READ, Permission.REWARD_EVENT_MANAGE));
        String id = JsonPath.read(send(post(ROOT), manager, selectionBody(100)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id");
        send(post(ROOT + "/" + id + "/grants"), manager, "{\"idempotencyKey\":\"k\"}").andExpect(status().isForbidden());
    }

    @Test
    void 만들기_시작_당첨자_마감_지급_흐름과_응답_모양_이메일은_pii_없으면_가린다() throws Exception {
        long member = newMember();
        long op = operatorWith(Permission.REWARD_EVENT_READ, Permission.REWARD_EVENT_MANAGE, Permission.REWARD_EVENT_GRANT);
        Cookie session = login(op);
        String created = send(post(ROOT), session, selectionBody(500)).andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.actions.editScope").value("FULL")).andExpect(jsonPath("$.data.tiers[0].rewards[0].kind").value("POINT"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.data.id");
        String tierId = JsonPath.read(created, "$.data.tiers[0].id");
        assertThat(tierId).matches("[0-9]+");

        String email = "m" + member + "@test.local";
        mvc.perform(get(ROOT + "/member-search").param("q", Long.toString(member)).cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(Long.toString(member))).andExpect(jsonPath("$.data[0].email").value(MemberMasking.email(email)));
        Cookie pii = login(operatorWith(Permission.REWARD_EVENT_READ, Permission.MEMBER_PII));
        mvc.perform(get(ROOT + "/member-search").param("q", Long.toString(member)).cookie(pii)).andExpect(jsonPath("$.data[0].email").value(email));

        send(post(ROOT + "/" + id + "/start"), session, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        send(post(ROOT + "/" + id + "/recipients"), session, "{\"tierId\":\"" + tierId + "\",\"memberIds\":[\"" + member + "\"]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipientsByTier." + tierId).value(1));
        send(post(ROOT + "/" + id + "/start"), session, null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("REWARD_EVENT_STATE"));
        send(post(ROOT + "/" + id + "/close"), session, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.actions.canGrant").value(true));
        mvc.perform(get(ROOT + "/" + id + "/grants/preview").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.targetCount").value(1))
                .andExpect(jsonPath("$.data.totalPoints").value(500)).andExpect(jsonPath("$.data.largeRequired").value(false));

        send(post(ROOT + "/" + id + "/grants"), session, "{\"idempotencyKey\":\"http-1\"}").andExpect(status().isOk()).andExpect(jsonPath("$.data.kind").value("GRANT"))
                .andExpect(jsonPath("$.data.status").value("DONE")).andExpect(jsonPath("$.data.progress.done").value(1));

        String recipients = mvc.perform(get(ROOT + "/" + id + "/recipients").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("GRANTED")).andExpect(jsonPath("$.data.items[0].results[0].status").value("SUCCESS"))
                .andReturn().getResponse().getContentAsString();
        String rid = JsonPath.read(recipients, "$.data.items[0].id");
        send(delete(ROOT + "/" + id + "/recipients/" + rid), session, null).andExpect(status().isConflict());
        mvc.perform(get(ROOT + "/" + id + "/runs").cookie(session)).andExpect(jsonPath("$.data[0].operator.id").value(Long.toString(op)));
        mvc.perform(get(ROOT + "/" + id + "/audit").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].action").value("GRANT"));
        mvc.perform(get(ROOT + "/" + id).cookie(session)).andExpect(jsonPath("$.data.status").value("DONE")).andExpect(jsonPath("$.data.stats.grantedPoints").value(500));
        assertThat(jdbc.queryForObject("SELECT balance FROM point_balances WHERE user_id = ?", Long.class, member)).isEqualTo(500);
    }

    @Test
    void 일괄_포인트_지급은_고액_권한이_없으면_403_POINT_ADJUST_LARGE_REQUIRED_와_거부_감사() throws Exception {
        long op = operatorWith(Permission.REWARD_EVENT_READ, Permission.REWARD_EVENT_MANAGE, Permission.REWARD_EVENT_GRANT);
        Cookie session = login(op);
        Instant now = Instant.now();
        String body = "{\"name\":\"HTTP 일괄\",\"memo\":\"\",\"startAt\":\"" + now.minus(Duration.ofHours(1)) + "\",\"endAt\":\"" + now.plus(Duration.ofDays(2))
                + "\",\"noticeUrl\":\"\",\"mode\":\"BULK\",\"bulk\":{\"scope\":\"CONDITION\",\"joinedFrom\":\"1970-01-02\",\"joinedTo\":\"1970-01-02\"},"
                + "\"excludeOperators\":true,\"tiers\":[{\"id\":\"t\",\"name\":\"전체\",\"quota\":0,\"rewards\":[{\"id\":\"l\",\"kind\":\"POINT\",\"points\":10}]}]}";
        String id = JsonPath.read(send(post(ROOT), session, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id");
        send(post(ROOT + "/" + id + "/start"), session, null).andExpect(status().isOk());

        send(post(ROOT + "/" + id + "/grants"), session, "{\"idempotencyKey\":\"bulk\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POINT_ADJUST_LARGE_REQUIRED"));
        assertThat(auditLogs.where().actor(op).action(AuditActions.PERMISSION_DENIED).targetType("REWARD_EVENT").target(id).result("DENIED").count()).isEqualTo(1);
        mvc.perform(get(ROOT + "/" + id + "/runs").cookie(session)).andExpect(jsonPath("$.data.length()").value(0));
    }
}
