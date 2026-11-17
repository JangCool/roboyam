package com.community.grade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 관리자 등급 설정의 레벨 곡선 지수(grade-level-contract 6절) — {@code GET·PUT /api/admin/grades}, {@code POST …/preview}. 실제 필터 체인·권한·PostgreSQL.
 * <ul>
 *   <li>조회: {@code xpCurveExponent}(JSON 숫자)와 {@code pace.typicalDaily}·{@code pace.dailyTotalCap}.</li>
 *   <li>저장: 지수만 바꿔도 전 회원 레벨을 다시 매기고(등급은 내리지 않는다) 감사에 지수를 남긴다. 다섯 값이 아니면 400 {@code fields.xpCurveExponent}(아무것도 저장하지 않음).</li>
 *   <li>미리보기: 저장할 지수의 경계 경험치로 센다.</li>
 * </ul>
 * 시험 DB 의 등급 설정 행은 하나다 — 끝나면 곡선을 처음 값으로 되돌리고 레벨을 다시 매긴다.
 */
class GradeCurveAdminHttpTest extends Commerce04AdminTestBase {

    private Cookie session;
    private int savedBase;
    private BigDecimal savedExponent;

    @BeforeEach
    void signIn() throws Exception {
        savedBase = jdbc.queryForObject("SELECT xp_curve_base FROM grade_settings WHERE id = 1", Integer.class);
        savedExponent = jdbc.queryForObject("SELECT xp_curve_exponent FROM grade_settings WHERE id = 1", BigDecimal.class);
        session = login(operatorWith(Permission.GRADE_READ, Permission.GRADE_MANAGE));
    }

    @AfterEach
    void restoreCurve() {
        jdbc.update("UPDATE grade_settings SET xp_curve_base = ?, xp_curve_exponent = ? WHERE id = 1", savedBase, savedExponent);
        jdbc.update("UPDATE profiles SET level = grade_xp_level(xp, ?, ?) WHERE level <> grade_xp_level(xp, ?, ?)", savedBase, savedExponent, savedBase, savedExponent);
    }

    /** 가입 10일 된 회원 — 경험치와 그에 맞는 레벨(지금 곡선) */
    private long member(long xp) {
        long id = newMember();
        jdbc.update("UPDATE users SET created_at = now() - interval '10 days' WHERE id = ?", id);
        jdbc.update("UPDATE profiles SET xp = ?, level = grade_xp_level(?, ?, ?) WHERE user_id = ?", xp, xp, savedBase, savedExponent, id);
        return id;
    }

    private String load() throws Exception {
        return mvc.perform(get("/api/admin/grades").cookie(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** 지금 등급 목록 그대로 + 곡선 지수만 바꾼 저장 본문(지수가 null 이면 칸을 싣지 않는다) */
    private String body(String exponentJson) throws Exception {
        String loaded = load();
        List<Map<String, Object>> grades = JsonPath.read(loaded, "$.data.grades");
        String policy = JsonPath.read(loaded, "$.data.manualPolicy");
        List<String> rows = new ArrayList<>();
        for (Map<String, Object> g : grades) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String field : List.of("gradeKey", "label", "minDays", "minActivity", "badgeColor", "badgeIcon", "minLevel", "sprite")) {
                row.put(field, g.get(field));
            }
            rows.add(JsonPath.parse(row).jsonString());
        }
        return "{\"grades\":[" + String.join(",", rows) + "],\"manualPolicy\":\"" + policy + "\"" + (exponentJson == null ? "" : ",\"xpCurveExponent\":" + exponentJson) + "}";
    }

    private int level(long userId) {
        return jdbc.queryForObject("SELECT level FROM profiles WHERE user_id = ?", Integer.class, userId);
    }

    private int rank(long userId) {
        return jdbc.queryForObject("SELECT g.level FROM profiles p JOIN grades g ON g.grade_key = p.grade_key WHERE p.user_id = ?", Integer.class, userId);
    }

    private String gradeAt(int rank) {
        return jdbc.queryForObject("SELECT grade_key FROM grades WHERE level = ?", String.class, rank);
    }

    /** 미리보기에서 그 등급이 될 회원 수 */
    private int previewCount(String exponentJson, String gradeKey) throws Exception {
        String response = mvc.perform(withCsrf(post("/api/admin/grades/preview"), session).content(body(exponentJson))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        List<Integer> counts = JsonPath.read(response, "$.data.items[?(@.gradeKey == '" + gradeKey + "')].count");
        assertThat(counts).hasSize(1);
        return counts.getFirst();
    }

    @Test
    void 조회는_곡선_지수와_하루_적립_기준을_준다() throws Exception {
        Map<String, Object> p = jdbc.queryForMap("SELECT attendance_points, post_points, post_daily_cap, comment_points, comment_daily_cap, daily_total_cap FROM point_settings WHERE id = 1");
        int typical = (int) p.get("attendance_points") + (int) p.get("post_points") * Math.clamp((int) p.get("post_daily_cap"), 0, 1)
                + (int) p.get("comment_points") * Math.clamp((int) p.get("comment_daily_cap"), 0, 3);
        int cap = (int) p.get("daily_total_cap");

        mvc.perform(get("/api/admin/grades").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpCurveExponent").value(savedExponent.doubleValue()))
                .andExpect(jsonPath("$.data.xpCurveExponent").isNumber())
                .andExpect(jsonPath("$.data.xpCurveBase").value(savedBase))
                .andExpect(jsonPath("$.data.pace.typicalDaily").value(cap > 0 ? Math.min(typical, cap) : typical))
                .andExpect(jsonPath("$.data.pace.dailyTotalCap").value(cap))
                .andExpect(jsonPath("$.data.maxGrades").value(11));
    }

    @Test
    void 지수만_바꿔도_전_회원_레벨을_다시_매기고_등급은_내리지_않으며_감사에_지수를_남긴다() throws Exception {
        assertThat(savedExponent).as("시험 DB 의 처음 값").isEqualByComparingTo("2.0");
        long low = member(200);        // 지수 2.0 에서 Lv.5 — 견습(최소 레벨 5 · 3일) 조건을 채웠지만 아직 새싹
        long high = member(98_010);    // 지수 2.0 에서 Lv.100
        memberFixture.grade(high, gradeAt(11));
        assertThat(level(low)).isEqualTo(5);
        assertThat(level(high)).isEqualTo(100);
        int audits = auditLogs.where().action("GRADE_UPDATE").count();

        // 2.0 → 3.0: 레벨이 내려간다(200 XP = Lv.3, 98,010 XP = Lv.22). 등급은 그대로
        mvc.perform(withCsrf(put("/api/admin/grades"), session).content(body("3"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpCurveExponent").value(3.0));
        assertThat(jdbc.queryForObject("SELECT xp_curve_exponent FROM grade_settings WHERE id = 1", BigDecimal.class)).isEqualByComparingTo("3.0");
        assertThat(level(low)).isEqualTo(3);
        assertThat(level(high)).isEqualTo(22);
        assertThat(rank(high)).as("레벨이 내려가도 등급은 내리지 않는다").isEqualTo(11);
        assertThat(rank(low)).isEqualTo(1);
        assertThat(auditLogs.where().action("GRADE_UPDATE").count()).isEqualTo(audits + 1);
        assertThat(auditLogs.where().action("GRADE_UPDATE").detailContains("xpCurveExponent").count()).isGreaterThanOrEqualTo(1);

        // 지수 칸 없이 저장 = 지금 값 그대로(바뀐 것이 없어 감사도 없다)
        mvc.perform(withCsrf(put("/api/admin/grades"), session).content(body(null))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpCurveExponent").value(3.0));
        assertThat(level(low)).isEqualTo(3);
        assertThat(auditLogs.where().action("GRADE_UPDATE").count()).isEqualTo(audits + 1);

        // 3.0 → 2.0: 레벨이 다시 오르고, 조건을 채운 회원은 저장 직후 소급 승급(올리기만, 이력 AUTO)
        mvc.perform(withCsrf(put("/api/admin/grades"), session).content(body("2.0"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpCurveExponent").value(2.0));
        assertThat(level(low)).isEqualTo(5);
        assertThat(level(high)).isEqualTo(100);
        assertThat(rank(low)).as("Lv.5 · 가입 10일 → 견습").isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT kind FROM member_grade_changes WHERE user_id = ?", String.class, low)).containsExactly("AUTO");
        assertThat(auditLogs.where().action("GRADE_UPDATE").count()).isEqualTo(audits + 2);
    }

    @Test
    void 다섯_값이_아닌_지수는_400_이고_아무것도_저장하지_않는다() throws Exception {
        long member = member(200);
        int audits = auditLogs.where().action("GRADE_UPDATE").count();
        for (String wrong : List.of("1.7", "0", "3.5", "-2")) {
            mvc.perform(withCsrf(put("/api/admin/grades"), session).header("Accept-Language", "ko").content(body(wrong))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                    .andExpect(jsonPath("$.error.fields.xpCurveExponent").value("곡선 지수는 1 · 1.5 · 2 · 2.5 · 3 중에서 골라 주세요."));
            mvc.perform(withCsrf(post("/api/admin/grades/preview"), session).content(body(wrong))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.xpCurveExponent").exists());
        }
        assertThat(jdbc.queryForObject("SELECT xp_curve_exponent FROM grade_settings WHERE id = 1", BigDecimal.class)).isEqualByComparingTo(savedExponent);
        assertThat(level(member)).isEqualTo(5);
        assertThat(auditLogs.where().action("GRADE_UPDATE").count()).isEqualTo(audits);
    }

    @Test
    void 미리보기는_저장할_지수의_경계_경험치로_센다() throws Exception {
        String apprentice = gradeAt(2); // 견습: 최소 레벨 5 · 3일
        String regular = gradeAt(3);    // 정식: 최소 레벨 10 · 7일
        assertThat(jdbc.queryForList("SELECT min_level FROM grades WHERE level IN (2, 3, 4) ORDER BY level", Integer.class)).containsExactly(5, 10, 20);
        int apprenticeAtTwo = previewCount("2", apprentice);
        int regularAtOne = previewCount("1", regular);
        int apprenticeAtThree = previewCount("3", apprentice);

        long member = member(200); // 가입 10일 · 새싹 — 지수 2.0: Lv.5(견습) / 1.0: Lv.21(정식 — 숙련은 14일이 모자란다) / 3.0: Lv.3(그대로)

        assertThat(previewCount("2", apprentice)).isEqualTo(apprenticeAtTwo + 1);
        assertThat(previewCount(null, apprentice)).as("지수 칸이 없으면 지금 값").isEqualTo(apprenticeAtTwo + 1);
        assertThat(previewCount("1", regular)).isEqualTo(regularAtOne + 1);
        assertThat(previewCount("3", apprentice)).isEqualTo(apprenticeAtThree);
        assertThat(level(member)).as("미리보기는 저장하지 않는다").isEqualTo(5);
        assertThat(rank(member)).isEqualTo(1);
    }
}
