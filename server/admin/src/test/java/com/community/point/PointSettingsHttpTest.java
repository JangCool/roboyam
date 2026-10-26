package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.permission.entity.Permission;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 관리자 포인트 정책 {@code GET·PUT /api/admin/settings/points}(커머스 1-3 — commerce-stage1-contract 「1-3 화면 목업」 API 후보) — 새 칸 9개(켬끔 2칸 포함)와 {@code adjustLargeThreshold}
 * 저장, 범위 400 {@code fields.<칸>}(아무것도 저장하지 않음), 감사 {@code POINT_SETTINGS_UPDATE} 는 바뀐 칸만 before/after, 저장한 문턱을 조정이 쓴다. 실제 필터 체인·권한·PostgreSQL.
 */
class PointSettingsHttpTest extends PointAdminHttpTestBase {

    private static final List<String> COLUMNS = List.of("post_min_length", "comment_min_length", "comment_own_post_excluded", "comment_once_per_post",
            "write_interval_seconds", "daily_total_cap", "attendance_streak_days", "attendance_streak_bonus", "adjust_large_threshold", "signup_points");

    private Map<String, Object> saved;
    private Cookie session;

    @BeforeEach
    void snapshot() throws Exception {
        saved = jdbc.queryForMap("SELECT " + String.join(", ", COLUMNS) + " FROM point_settings WHERE id = 1");
        session = login(operatorWith(Permission.POINT_READ, Permission.POINT_SETTINGS));
    }

    @AfterEach
    void restoreSettings() {
        saved.forEach((column, value) -> jdbc.update("UPDATE point_settings SET " + column + " = ? WHERE id = 1", value));
        policy.invalidate();
    }

    /** 지금 정책(GET 응답의 data) — 저장 본문의 바탕 */
    private DocumentContext current() throws Exception {
        String body = mvc.perform(get("/api/admin/settings/points").cookie(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        DocumentContext data = JsonPath.parse(JsonPath.<Map<String, Object>>read(body, "$.data"));
        data.delete("$.updatedAt").delete("$.updatedBy");
        return data;
    }

    private org.springframework.test.web.servlet.ResultActions save(String json) throws Exception {
        return mvc.perform(withCsrf(put("/api/admin/settings/points"), session).header("Accept-Language", "ko").content(json));
    }

    private int audits() {
        return count("SELECT count(*) FROM audit_logs WHERE action = 'POINT_SETTINGS_UPDATE'");
    }

    @Test
    void 조회는_새_칸_9개와_문턱을_준다() throws Exception {
        mvc.perform(get("/api/admin/settings/points").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postMinLength").value(saved.get("post_min_length")))
                .andExpect(jsonPath("$.data.commentMinLength").value(saved.get("comment_min_length")))
                .andExpect(jsonPath("$.data.commentOwnPostExcluded").value(saved.get("comment_own_post_excluded")))
                .andExpect(jsonPath("$.data.commentOncePerPost").value(saved.get("comment_once_per_post")))
                .andExpect(jsonPath("$.data.writeIntervalSeconds").value(saved.get("write_interval_seconds")))
                .andExpect(jsonPath("$.data.dailyTotalCap").value(saved.get("daily_total_cap")))
                .andExpect(jsonPath("$.data.attendanceStreakDays").value(saved.get("attendance_streak_days")))
                .andExpect(jsonPath("$.data.attendanceStreakBonus").value(saved.get("attendance_streak_bonus")))
                .andExpect(jsonPath("$.data.adjustLargeThreshold").value(saved.get("adjust_large_threshold")));
    }

    @Test
    void 새_칸과_문턱을_저장하고_감사에는_바뀐_칸만_before_after() throws Exception {
        DocumentContext body = current();
        body.set("$.postMinLength", 30).set("$.commentOncePerPost", false).set("$.dailyTotalCap", 0).set("$.adjustLargeThreshold", 5000);
        int before = audits();

        save(body.jsonString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postMinLength").value(30))
                .andExpect(jsonPath("$.data.commentOncePerPost").value(false))
                .andExpect(jsonPath("$.data.dailyTotalCap").value(0))
                .andExpect(jsonPath("$.data.adjustLargeThreshold").value(5000))
                .andExpect(jsonPath("$.data.updatedBy").exists());

        Map<String, Object> row = jdbc.queryForMap("SELECT post_min_length, comment_once_per_post, daily_total_cap, adjust_large_threshold, comment_min_length "
                + "FROM point_settings WHERE id = 1");
        assertThat(row).containsEntry("post_min_length", 30).containsEntry("comment_once_per_post", false).containsEntry("daily_total_cap", 0)
                .containsEntry("adjust_large_threshold", 5000).containsEntry("comment_min_length", saved.get("comment_min_length"));
        assertThat(audits()).isEqualTo(before + 1);
        String detail = jdbc.queryForObject("SELECT detail::text FROM audit_logs WHERE action = 'POINT_SETTINGS_UPDATE' ORDER BY occurred_at DESC, id DESC LIMIT 1",
                String.class);
        Map<String, Object> beforeMap = JsonPath.read(detail, "$.before");
        Map<String, Object> afterMap = JsonPath.read(detail, "$.after");
        assertThat(afterMap).containsOnlyKeys("postMinLength", "commentOncePerPost", "dailyTotalCap", "adjustLargeThreshold").containsEntry("postMinLength", 30)
                .containsEntry("commentOncePerPost", false).containsEntry("adjustLargeThreshold", 5000);
        assertThat(beforeMap).containsOnlyKeys("postMinLength", "commentOncePerPost", "dailyTotalCap", "adjustLargeThreshold")
                .containsEntry("postMinLength", saved.get("post_min_length")).containsEntry("adjustLargeThreshold", saved.get("adjust_large_threshold"));

        // 저장한 값이 이 서버의 정책 사본에 바로 들어온다(커밋 뒤 비움) — 조정 문턱도
        assertThat(policy.current().postMinLength()).isEqualTo(30);
        assertThat(policy.lots().adjustLargeThreshold()).isEqualTo(5000);

        // 같은 값으로 다시 저장하면 감사가 없다
        save(body.jsonString()).andExpect(status().isOk());
        assertThat(audits()).isEqualTo(before + 1);
    }

    @Test
    void 저장한_문턱으로_고액_조정을_판정한다() throws Exception {
        save(current().set("$.adjustLargeThreshold", 100).jsonString()).andExpect(status().isOk());
        long member = newMember();
        Cookie plain = login(operatorWith(Permission.POINT_ADJUST));
        mvc.perform(withCsrf(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/members/" + member + "/points"), plain)
                .content("{\"delta\":101,\"reason\":\"문턱 시험 조정\"}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POINT_ADJUST_LARGE_REQUIRED"));
        assertThat(balance(member)).containsExactly(0, 0, 0, 0);
    }

    @Test
    void 범위_밖이거나_빠진_칸은_400_fields_칸이고_아무것도_저장하지_않는다() throws Exception {
        Map<String, Object> bad = Map.of("postMinLength", 10001, "commentMinLength", 1001, "writeIntervalSeconds", 3601, "dailyTotalCap", 100001,
                "attendanceStreakDays", 366, "attendanceStreakBonus", 10001, "adjustLargeThreshold", 100001);
        int before = audits();
        for (Map.Entry<String, Object> entry : bad.entrySet()) {
            save(current().set("$." + entry.getKey(), entry.getValue()).jsonString()).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION")).andExpect(jsonPath("$.error.fields." + entry.getKey()).exists());
            save(current().set("$." + entry.getKey(), -1).jsonString()).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields." + entry.getKey()).exists());
        }
        for (String field : List.of("commentOwnPostExcluded", "commentOncePerPost", "postMinLength", "adjustLargeThreshold")) {
            save(current().delete("$." + field).jsonString()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields." + field).exists());
        }
        save(current().set("$.postMinLength", 1.5).set("$.commentOncePerPost", null).jsonString()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.postMinLength").value("0 ~ 10,000 사이 정수로 입력해 주세요."))
                .andExpect(jsonPath("$.error.fields.commentOncePerPost").value("켬 또는 끔을 골라 주세요."));
        assertThat(audits()).isEqualTo(before);
        assertThat(jdbc.queryForMap("SELECT " + String.join(", ", COLUMNS) + " FROM point_settings WHERE id = 1")).isEqualTo(saved);
    }

    @Test
    void 경계값은_저장된다() throws Exception {
        save(current().set("$.postMinLength", 0).set("$.commentMinLength", 1000).set("$.writeIntervalSeconds", 3600).set("$.dailyTotalCap", 100000)
                .set("$.attendanceStreakDays", 365).set("$.attendanceStreakBonus", 0).set("$.adjustLargeThreshold", 0).jsonString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentMinLength").value(1000)).andExpect(jsonPath("$.data.adjustLargeThreshold").value(0));
    }

    @Test
    void 저장은_point_settings_권한이_있어야_한다() throws Exception {
        Cookie reader = login(operatorWith(Permission.POINT_READ));
        String body = current().jsonString();
        mvc.perform(withCsrf(put("/api/admin/settings/points"), reader).content(body)).andExpect(status().isForbidden());
    }
}
