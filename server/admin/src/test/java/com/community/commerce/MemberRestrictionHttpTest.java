package com.community.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 관리자 회원 상세 「기능 제한」 API(계약 「0-4」 — commerce.md 12-3, 화면 확정 결정 1·2·3) — 실제 필터 체인·권한 인터셉터·PostgreSQL.
 */
class MemberRestrictionHttpTest extends Commerce04AdminTestBase {

    private static final String UNTIL_TOMORROW = "\"" + Instant.now().plus(Duration.ofDays(1)) + "\"";

    private static String path(long memberId) {
        return "/api/admin/members/" + memberId + "/restrictions";
    }

    private static String grantBody(String type, String until, String reason, String memo) {
        return "{\"type\":" + (type == null ? "null" : "\"" + type + "\"") + ",\"until\":" + until + ",\"reason\":" + (reason == null ? "null" : "\"" + reason + "\"")
                + ",\"adminMemo\":" + (memo == null ? "null" : "\"" + memo + "\"") + "}";
    }

    private String grant(Cookie session, long memberId, String body) throws Exception {
        MvcResult result = mvc.perform(withCsrf(post(path(memberId)), session).content(body)).andExpect(status().isCreated()).andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    @Nested
    class 권한 {

        @Test
        void 조회는_member_read_걸기와_해제는_sanction_manage_가_없으면_403() throws Exception {
            long member = newMember();
            Cookie none = login(operatorWith());
            Cookie reader = login(operatorWith(Permission.MEMBER_READ));

            mvc.perform(get(path(member)).cookie(none)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
            mvc.perform(get(path(member)).cookie(reader)).andExpect(status().isOk());
            mvc.perform(withCsrf(post(path(member)), reader).content(grantBody("POINT_FREEZE", "null", "포인트 부정 적립 의심", null)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
            mvc.perform(withCsrf(post(path(member) + "/1/lift"), reader).content("{\"reason\":\"확인 결과 문제 없음\"}"))
                    .andExpect(status().isForbidden());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM member_restrictions WHERE user_id = ?", Integer.class, member)).isZero();
        }

        @Test
        void 로그인하지_않으면_401() throws Exception {
            mvc.perform(get(path(newMember()))).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class 걸기 {

        @Test
        void 걸면_201_과_만든_한_건_진행_중이고_감사와_회원_알림이_남는다() throws Exception {
            long member = newMember();
            long operator = operatorWith(Permission.SANCTION_MANAGE, Permission.MEMBER_READ);
            Cookie session = login(operator);

            MvcResult result = mvc.perform(withCsrf(post(path(member)), session).content(grantBody("GROUP_BUY_BAN", UNTIL_TOMORROW, "결제하지 않은 선점이 반복됨", "운영 메모 — 내부용")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.type").value("GROUP_BUY_BAN"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.source").value("OPERATOR"))
                    .andExpect(jsonPath("$.data.reason").value("결제하지 않은 선점이 반복됨"))
                    .andExpect(jsonPath("$.data.adminMemo").value("운영 메모 — 내부용"))
                    .andExpect(jsonPath("$.data.createdBy.id").value(Long.toString(operator)))
                    .andExpect(jsonPath("$.data.createdBy.name").value(operatorName(operator)))
                    .andExpect(jsonPath("$.data.until").isNotEmpty())
                    .andReturn();
            String id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

            mvc.perform(get(path(member)).cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(id))
                    .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
            String audit = jdbc.queryForObject("SELECT detail FROM audit_logs WHERE action = 'MEMBER_RESTRICTION_GRANT' AND target_id = ? ORDER BY id DESC LIMIT 1",
                    String.class, Long.toString(member));
            assertThat(audit).contains("\"source\":\"OPERATOR\"", "\"type\":\"GROUP_BUY_BAN\"").doesNotContain("운영 메모");
            List<Map<String, Object>> notices = jdbc.queryForList("SELECT * FROM member_notifications WHERE user_id = ? AND type = 'FEATURE_RESTRICTED'", member);
            assertThat(notices).hasSize(1);
            assertThat(notices.get(0).toString()).doesNotContain("운영 메모");
        }

        @Test
        void 같은_종류가_겹쳐도_막지_않고_무기한도_된다() throws Exception {
            long member = newMember();
            Cookie session = login(operatorWith(Permission.SANCTION_MANAGE, Permission.MEMBER_READ));
            grant(session, member, grantBody("POINT_FREEZE", UNTIL_TOMORROW, "포인트 부정 적립 의심", null));
            mvc.perform(withCsrf(post(path(member)), session).content(grantBody("POINT_FREEZE", "null", "포인트 부정 적립 확인", null)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.data.until").isEmpty());

            mvc.perform(get(path(member)).cookie(session)).andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        void 입력이_틀리면_400_VALIDATION_칸별() throws Exception {
            long member = newMember();
            Cookie session = login(operatorWith(Permission.SANCTION_MANAGE));
            String past = "\"" + Instant.now().minus(Duration.ofHours(1)) + "\"";

            mvc.perform(withCsrf(post(path(member)), session).content(grantBody(null, past, "짧음", "x".repeat(1001))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                    .andExpect(jsonPath("$.error.fields.type").isNotEmpty())
                    .andExpect(jsonPath("$.error.fields.until").isNotEmpty())
                    .andExpect(jsonPath("$.error.fields.reason").isNotEmpty())
                    .andExpect(jsonPath("$.error.fields.adminMemo").isNotEmpty());
            mvc.perform(withCsrf(post(path(member)), session).content(grantBody("CHARGE_BAN", "null", "가".repeat(201), null)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").isNotEmpty());
            // 경계: 사유 5자·200자, 메모 1000자는 된다
            mvc.perform(withCsrf(post(path(member)), session).content(grantBody("CHARGE_BAN", "null", "가".repeat(5), "m".repeat(1000)))).andExpect(status().isCreated());
            mvc.perform(withCsrf(post(path(member)), session).content(grantBody("CHARGE_BAN", "null", "가".repeat(200), null))).andExpect(status().isCreated());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM member_restrictions WHERE user_id = ?", Integer.class, member)).isEqualTo(2);
        }

        @Test
        void 탈퇴_회원에게는_409_MEMBER_WITHDRAWN_없는_회원은_404() throws Exception {
            Cookie session = login(operatorWith(Permission.SANCTION_MANAGE, Permission.MEMBER_READ));
            long gone = withdrawnMember(Instant.now().minus(Duration.ofDays(3)));

            mvc.perform(withCsrf(post(path(gone)), session).content(grantBody("POINT_FREEZE", "null", "포인트 부정 적립 의심", null)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MEMBER_WITHDRAWN"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM member_restrictions WHERE user_id = ?", Integer.class, gone)).isZero();

            long missing = ids.nextId();
            mvc.perform(withCsrf(post(path(missing)), session).content(grantBody("POINT_FREEZE", "null", "포인트 부정 적립 의심", null)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
            mvc.perform(get(path(missing)).cookie(session)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
            mvc.perform(get("/api/admin/members/abc/restrictions").cookie(session)).andExpect(status().isNotFound());
        }
    }

    @Nested
    class 해제 {

        @Test
        void 해제하면_LIFTED_와_사유_해제한_사람_감사() throws Exception {
            long member = newMember();
            long operator = operatorWith(Permission.SANCTION_MANAGE, Permission.MEMBER_READ);
            Cookie session = login(operator);
            String id = grant(session, member, grantBody("POINT_FREEZE", "null", "포인트 부정 적립 의심", null));

            mvc.perform(withCsrf(post(path(member) + "/" + id + "/lift"), session).content("{\"reason\":\"확인 결과 문제 없음\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("LIFTED"))
                    .andExpect(jsonPath("$.data.liftReason").value("확인 결과 문제 없음"))
                    .andExpect(jsonPath("$.data.liftedBy.id").value(Long.toString(operator)))
                    .andExpect(jsonPath("$.data.liftedAt").isNotEmpty());
            assertThat(jdbc.queryForObject("SELECT detail FROM audit_logs WHERE action = 'MEMBER_RESTRICTION_LIFT' AND target_id = ? ORDER BY id DESC LIMIT 1",
                    String.class, Long.toString(member))).contains("\"restrictionId\":\"" + id + "\"", "확인 결과 문제 없음");

            // 이미 해제한 것은 409
            mvc.perform(withCsrf(post(path(member) + "/" + id + "/lift"), session).content("{\"reason\":\"다시 해제 시도\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RESTRICTION_NOT_ACTIVE"));
        }

        @Test
        void 사유가_없거나_짧으면_400() throws Exception {
            long member = newMember();
            Cookie session = login(operatorWith(Permission.SANCTION_MANAGE));
            String id = grant(session, member, grantBody("POINT_FREEZE", "null", "포인트 부정 적립 의심", null));

            mvc.perform(withCsrf(post(path(member) + "/" + id + "/lift"), session).content("{\"reason\":\"짧음\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").isNotEmpty());
            mvc.perform(withCsrf(post(path(member) + "/" + id + "/lift"), session).content("{}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").isNotEmpty());
            assertThat(jdbc.queryForObject("SELECT lifted_at IS NULL FROM member_restrictions WHERE id = ?", Boolean.class, Long.parseLong(id))).isTrue();
        }

        @Test
        void 없거나_남의_것이면_404_기간이_끝났으면_409() throws Exception {
            long member = newMember();
            long other = newMember();
            long operator = operatorWith(Permission.SANCTION_MANAGE);
            Cookie session = login(operator);
            String id = grant(session, member, grantBody("POINT_FREEZE", "null", "포인트 부정 적립 의심", null));

            mvc.perform(withCsrf(post(path(other) + "/" + id + "/lift"), session).content("{\"reason\":\"남의 회원 경로로 해제\"}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESTRICTION_NOT_FOUND"));
            mvc.perform(withCsrf(post(path(member) + "/" + ids.nextId() + "/lift"), session).content("{\"reason\":\"없는 제한 해제 시도\"}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESTRICTION_NOT_FOUND"));
            mvc.perform(withCsrf(post(path(member) + "/xyz/lift"), session).content("{\"reason\":\"형식이 틀린 번호\"}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESTRICTION_NOT_FOUND"));

            long expired = ids.nextId();
            jdbc.update("INSERT INTO member_restrictions (id, user_id, type, reason, starts_at, until, source, created_by, created_at) "
                    + "VALUES (?, ?, 'CHARGE_BAN', '지난 제한', now() - interval '3 days', now() - interval '1 day', 'OPERATOR', ?, now() - interval '3 days')", expired, member, operator);
            mvc.perform(withCsrf(post(path(member) + "/" + expired + "/lift"), session).content("{\"reason\":\"기간이 끝난 제한\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RESTRICTION_NOT_ACTIVE"));
            mvc.perform(get(path(member)).cookie(login(operatorWith(Permission.MEMBER_READ))))
                    .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data[1].status").value("EXPIRED"));
        }
    }
}
