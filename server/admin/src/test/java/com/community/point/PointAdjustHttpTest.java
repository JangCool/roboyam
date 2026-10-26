package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 관리자 수동 조정 {@code POST /api/admin/members/{id}/points}(커머스 1-1 — commerce-stage1-contract 「1-1」 API, commerce.md 5-4·13-2·13-4) — 범위 ±100,000, 큰 금액 문턱과
 * {@code point.adjust.large}(없으면 403 {@code POINT_ADJUST_LARGE_REQUIRED} + 아무것도 바뀌지 않음 + 거부 감사), 문턱 0 이면 모든 조정이 고액, 감사 {@code large}·돈 관련 보관,
 * 무상만(유상을 만들거나 건드리는 길이 없다 — P20). 실제 필터 체인·권한 인터셉터·PostgreSQL.
 */
class PointAdjustHttpTest extends PointAdminHttpTestBase {

    private static final int THRESHOLD = 10_000;

    private static String path(long member) {
        return "/api/admin/members/" + member + "/points";
    }

    private ResultActions adjust(Cookie session, long member, String body) throws Exception {
        return mvc.perform(withCsrf(post(path(member)), session).header("Accept-Language", "ko").content(body));
    }

    private static String body(Object delta) {
        return "{\"delta\":" + delta + ",\"reason\":\"시험 조정 사유\"}";
    }

    /** 이 회원의 원장 줄·묶음·조정 감사 수 — 「아무것도 바뀌지 않았다」를 보려고 */
    private List<Integer> footprint(long member) {
        return List.of(count("SELECT count(*) FROM point_ledger WHERE user_id = ?", member), count("SELECT count(*) FROM point_lots WHERE user_id = ?", member),
                count("SELECT count(*) FROM audit_logs WHERE action = 'POINT_ADJUST' AND target_id = ?", Long.toString(member)),
                count("SELECT count(*) FROM member_notifications WHERE user_id = ?", member));
    }

    private Map<String, Object> lastAdjustAudit(long member) {
        return jdbc.queryForMap("SELECT actor_id, result, retention_class, detail FROM audit_logs WHERE action = 'POINT_ADJUST' AND target_id = ? "
                + "ORDER BY occurred_at DESC, id DESC LIMIT 1", Long.toString(member));
    }

    @Test
    void 범위는_0_이_아닌_정수_마이너스_100000_에서_100000_이고_넘으면_400_과_최대값_문구() throws Exception {
        long member = newMember();
        Cookie large = login(operatorWith(Permission.POINT_ADJUST, Permission.POINT_ADJUST_LARGE));

        for (String delta : List.of("100001", "-100001", "0", "1.5", "null")) {
            String response = adjust(large, member, body(delta)).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
            assertThat(response).as("delta " + delta).contains("\"code\":\"VALIDATION\"").contains("100,000");
        }
        // 숫자가 아닌 값은 본문을 읽지 못한다(기존 규칙 — 400 MALFORMED_REQUEST)
        adjust(large, member, body("\"abc\"")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
        assertThat(balance(member)).containsExactly(0, 0, 0, 0);

        adjust(large, member, body(100000)).andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(100000))
                .andExpect(jsonPath("$.data.balance").value(100000));
        adjust(large, member, body(-100000)).andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(-100000))
                .andExpect(jsonPath("$.data.balance").value(0));
        assertInvariants(member);
    }

    @Test
    void 문턱을_넘는데_고액_권한이_없으면_403_POINT_ADJUST_LARGE_REQUIRED_이고_아무것도_바뀌지_않고_거부_감사가_남는다() throws Exception {
        long member = newMember();
        grant(member, 500, now().minusSeconds(5));
        long operator = operatorWith(Permission.POINT_ADJUST);
        Cookie session = login(operator);
        withSetting("adjust_large_threshold", THRESHOLD, () -> {
            long[] before = balance(member);
            List<Integer> footprint = footprint(member);

            for (int delta : new int[] {THRESHOLD + 1, -(THRESHOLD + 1), 100000}) {
                adjust(session, member, body(delta)).andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.error.code").value("POINT_ADJUST_LARGE_REQUIRED"))
                        .andExpect(jsonPath("$.error.message").value("10,000P를 넘는 조정은 고액 조정 권한이 필요해요."));
            }
            assertThat(balance(member)).containsExactly(before);
            assertThat(footprint(member)).isEqualTo(footprint);
            assertThat(count("SELECT count(*) FROM audit_logs WHERE actor_id = ? AND action = ? AND target_id = ? AND result = 'DENIED'", operator,
                    AuditActions.PERMISSION_DENIED, Long.toString(member))).isEqualTo(3);

            // 문턱과 같은 크기는 고액이 아니다
            adjust(session, member, body(THRESHOLD)).andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(THRESHOLD));
            assertThat(lastAdjustAudit(member).get("detail").toString()).contains("\"large\":false");
        });
        assertInvariants(member);
    }

    @Test
    void 고액_권한이_있으면_문턱을_넘어도_성공하고_감사에_large_true_돈_관련_보관() throws Exception {
        long member = newMember();
        long operator = operatorWith(Permission.POINT_ADJUST, Permission.POINT_ADJUST_LARGE);
        Cookie session = login(operator);
        withSetting("adjust_large_threshold", THRESHOLD, () -> {
            adjust(session, member, body(THRESHOLD + 1)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.applied").value(THRESHOLD + 1))
                    .andExpect(jsonPath("$.data.balance").value(THRESHOLD + 1));
            Map<String, Object> audit = lastAdjustAudit(member);
            assertThat(((Number) audit.get("actor_id")).longValue()).isEqualTo(operator);
            assertThat(audit.get("result")).isEqualTo("SUCCESS");
            assertThat(audit.get("detail").toString()).contains("\"large\":true", "\"requested\":10001", "\"applied\":10001");
            // 큰 금액 조정 = 돈 관련(5년) — 행위를 나누지 않고 POINT_ADJUST 의 분류로(계약 「1-1」·패키지 「감사 행위와 보관 분류」)
            assertThat(audit.get("retention_class")).isEqualTo(AuditRetentionClass.MONEY.name());
            assertThat(AuditRetention.classify(AuditActions.POINT_ADJUST)).isEqualTo(AuditRetentionClass.MONEY);
        });
        assertThat(balance(member)).containsExactly(THRESHOLD + 1, 0, 0, THRESHOLD + 1);
        assertInvariants(member);
    }

    @Test
    void 정책_조회는_문턱을_읽기_전용으로_준다() throws Exception {
        Cookie reader = login(operatorWith(Permission.POINT_READ));
        withSetting("adjust_large_threshold", 2500, () -> mvc.perform(get("/api/admin/settings/points").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adjustLargeThreshold").value(2500)));
    }

    @Test
    void 문턱이_0_이면_작은_조정도_고액이다() throws Exception {
        long member = newMember();
        Cookie plain = login(operatorWith(Permission.POINT_ADJUST));
        Cookie large = login(operatorWith(Permission.POINT_ADJUST, Permission.POINT_ADJUST_LARGE));
        withSetting("adjust_large_threshold", 0, () -> {
            adjust(plain, member, body(1)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("POINT_ADJUST_LARGE_REQUIRED"));
            adjust(plain, member, body(-1)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("POINT_ADJUST_LARGE_REQUIRED"));
            assertThat(balance(member)).containsExactly(0, 0, 0, 0);
            adjust(large, member, body(1)).andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(1));
            assertThat(lastAdjustAudit(member).get("detail").toString()).contains("\"large\":true");
        });
        assertInvariants(member);
    }

    @Test
    void 칸_검사가_먼저고_그다음_회원_그다음_고액_권한이다() throws Exception {
        Cookie plain = login(operatorWith(Permission.POINT_ADJUST));
        long member = newMember();
        withSetting("adjust_large_threshold", THRESHOLD, () -> {
            // 범위 밖 + 빈 사유 → 400(고액 판정 전에)
            adjust(plain, member, "{\"delta\":200000,\"reason\":\"  \"}").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.delta").exists()).andExpect(jsonPath("$.error.fields.reason").exists());
            // 없는 회원 → 404(고액이어도)
            adjust(plain, ids.nextId(), body(THRESHOLD + 1)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
            long withdrawn = withdrawnMember(Instant.now());
            adjust(plain, withdrawn, body(1)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
        });
    }

    @Test
    void 유상을_지급할_길이_없다_유상_칸을_보내도_무상만_늘고_묶음은_무상이다() throws Exception {
        long member = newMember();
        chargePaid(member, 1000, now().minusSeconds(5));
        Cookie session = login(operatorWith(Permission.POINT_ADJUST));

        adjust(session, member, "{\"delta\":500,\"reason\":\"유상으로 주세요\",\"kind\":\"PAID\",\"paid\":true,\"paidDelta\":500,\"lotKind\":\"PAID\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(500)).andExpect(jsonPath("$.data.balance").value(1500));

        assertThat(balance(member)).containsExactly(500, 1000, 0, 1500);
        Map<String, Object> line = jdbc.queryForMap("SELECT kind, free_delta, paid_delta FROM point_ledger WHERE user_id = ? AND reason = 'ADMIN_ADJUST'", member);
        assertThat(line.get("kind")).isEqualTo("EARN");
        assertThat(((Number) line.get("free_delta")).longValue()).isEqualTo(500);
        assertThat(((Number) line.get("paid_delta")).longValue()).isZero();
        assertThat(jdbc.queryForList("SELECT kind FROM point_lots WHERE user_id = ? AND source_reason = 'ADMIN_ADJUST'", String.class, member)).containsExactly("FREE");
        assertInvariants(member);
    }

    @Test
    void 차감은_무상_잔액까지만이고_유상은_그대로며_미회수를_만들지_않는다() throws Exception {
        long member = newMember();
        chargePaid(member, 1000, now().minusSeconds(10));
        grant(member, 300, now().minusSeconds(5));
        Cookie session = login(operatorWith(Permission.POINT_ADJUST));

        adjust(session, member, body(-5000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applied").value(-300))
                .andExpect(jsonPath("$.data.balance").value(1000));
        assertThat(balance(member)).containsExactly(0, 1000, 0, 1000);
        assertThat(count("SELECT count(*) FROM point_revoke_shortfalls WHERE user_id = ?", member)).isZero();

        // 무상이 0 이면 반영 0 — 원장 줄 없음, 감사는 남는다
        int lines = count("SELECT count(*) FROM point_ledger WHERE user_id = ?", member);
        adjust(session, member, body(-10)).andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(0)).andExpect(jsonPath("$.data.balance").value(1000));
        assertThat(count("SELECT count(*) FROM point_ledger WHERE user_id = ?", member)).isEqualTo(lines);
        assertThat(balance(member)).containsExactly(0, 1000, 0, 1000);
        assertThat(lastAdjustAudit(member).get("detail").toString()).contains("\"applied\":0");
        assertInvariants(member);
    }
}
