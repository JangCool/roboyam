package com.community.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 휴일 관리 API(사용자 지시 2026-11-05 — {@code /api/admin/holidays}) — 보기 {@code site.read}, 회사 휴무일 더하기·지우기·지금 받기 {@code site.manage}(새 권한 없음),
 * 달력 읽기는 로그인한 운영자 누구나(회사 휴무일 포함). 공공데이터·기본값 줄은 지울 수 없다(409). 「지금 받기」는 바깥으로 나가지 않게 특일 정보 주소를 닫힌 포트로 돌린다.
 * 2043 년 줄만 쓰고 끝나면 지운다.
 */
class HolidayAdminHttpTest extends Commerce04AdminTestBase {

    private static final int YEAR = 2043;
    private List<Map<String, Object>> savedKeys;

    @BeforeEach
    void keep() {
        savedKeys = jdbc.queryForList("SELECT * FROM public_data_settings");
        jdbc.update("DELETE FROM public_data_settings");
        jdbc.update("DELETE FROM public_data_endpoints");
        jdbc.update("DELETE FROM site_holidays WHERE holiday_date BETWEEN ? AND ?", LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31));
    }

    @AfterEach
    void restore() {
        jdbc.update("DELETE FROM site_holidays WHERE holiday_date BETWEEN ? AND ?", LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31));
        jdbc.update("DELETE FROM public_data_endpoints");
        jdbc.update("DELETE FROM public_data_settings");
        for (Map<String, Object> row : savedKeys) {
            jdbc.update("INSERT INTO public_data_settings (key, value_cipher, updated_at, updated_by, tested_result, tested_at, tested_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    row.get("key"), row.get("value_cipher"), row.get("updated_at"), row.get("updated_by"), row.get("tested_result"), row.get("tested_at"), row.get("tested_by"));
        }
    }

    private void insert(String date, String name, String source) {
        jdbc.update("INSERT INTO site_holidays (holiday_date, name, source) VALUES (?::date, ?, ?)", date, name, source);
    }

    @Test
    void 보기는_site_read_바꾸기와_받기는_site_manage_없으면_403() throws Exception {
        Cookie none = login(operatorWith());
        Cookie reader = login(operatorWith(Permission.SITE_READ, Permission.SITE_SECRET));

        mvc.perform(get("/api/admin/holidays").param("year", String.valueOf(YEAR)).cookie(none)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        mvc.perform(get("/api/admin/holidays").param("year", String.valueOf(YEAR)).cookie(reader)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(withCsrf(post("/api/admin/holidays"), reader).content("{\"date\":\"2043-03-03\",\"name\":\"휴무\"}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/holidays/sync"), reader)).andExpect(status().isForbidden());
        insert("2043-03-04", "회사", "OPERATOR");
        long id = jdbc.queryForObject("SELECT id FROM site_holidays WHERE holiday_date = '2043-03-04'", Long.class);
        mvc.perform(withCsrf(delete("/api/admin/holidays/" + id), reader)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM site_holidays WHERE holiday_date BETWEEN '2043-01-01' AND '2043-12-31'", Integer.class)).isEqualTo(1);
    }

    @Test
    void 회사_휴무일을_더하고_지우며_감사를_남기고_공공데이터_기본값은_409() throws Exception {
        long operator = operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE);
        Cookie session = login(operator);
        insert("2043-05-05", "어린이날", "PUBLIC_DATA");
        insert("2043-05-06", "기본값", "SEED");

        mvc.perform(withCsrf(post("/api/admin/holidays"), session).content("{\"date\":\"2043-05-05\",\"name\":\"  창립기념일  \"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("OPERATOR")).andExpect(jsonPath("$.data.name").value("창립기념일"))
                .andExpect(jsonPath("$.data.date").value("2043-05-05")).andExpect(jsonPath("$.data.createdBy").value(operatorName(operator)));
        mvc.perform(withCsrf(post("/api/admin/holidays"), session).content("{\"date\":\"2043-05-05\",\"name\":\"또\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.date").isNotEmpty());
        mvc.perform(withCsrf(post("/api/admin/holidays"), session).content("{\"date\":\"2043-05-07\",\"name\":\"" + "가".repeat(31) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.name").isNotEmpty());
        mvc.perform(withCsrf(post("/api/admin/holidays"), session).content("{\"name\":\"이름\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.date").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT detail FROM audit_logs WHERE actor_id = ? AND action = 'SITE_HOLIDAY_CREATE' ORDER BY id DESC LIMIT 1", String.class, operator))
                .contains("2043-05-05").contains("창립기념일");

        MvcResult year = mvc.perform(get("/api/admin/holidays").param("year", "2043").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(YEAR)).andExpect(jsonPath("$.data.rows.length()").value(3))
                .andExpect(jsonPath("$.data.rows[0].name").value("어린이날")).andExpect(jsonPath("$.data.rows[1].source").value("OPERATOR"))
                .andExpect(jsonPath("$.data.rows[2].source").value("SEED")).andExpect(jsonPath("$.data.keyConfigured").isBoolean()).andReturn();
        assertThat(year.getResponse().getContentAsString()).contains("\"id\":\"");

        long pub = jdbc.queryForObject("SELECT id FROM site_holidays WHERE source = 'PUBLIC_DATA' AND holiday_date = '2043-05-05'", Long.class);
        long seed = jdbc.queryForObject("SELECT id FROM site_holidays WHERE source = 'SEED' AND holiday_date = '2043-05-06'", Long.class);
        long company = jdbc.queryForObject("SELECT id FROM site_holidays WHERE source = 'OPERATOR' AND holiday_date = '2043-05-05'", Long.class);
        mvc.perform(withCsrf(delete("/api/admin/holidays/" + pub), session)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("HOLIDAY_NOT_EDITABLE"));
        mvc.perform(withCsrf(delete("/api/admin/holidays/" + seed), session)).andExpect(status().isConflict());
        mvc.perform(withCsrf(delete("/api/admin/holidays/" + company), session)).andExpect(status().isNoContent());
        mvc.perform(withCsrf(delete("/api/admin/holidays/" + company), session)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("HOLIDAY_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_id = ? AND action = 'SITE_HOLIDAY_DELETE'", Integer.class, operator)).isEqualTo(1);
    }

    @Test
    void 달력_읽기는_권한_없는_운영자도_되고_회사_휴무일도_보인다_범위는_최대_2년() throws Exception {
        Cookie none = login(operatorWith());
        insert("2043-09-25", "추석", "PUBLIC_DATA");
        insert("2043-09-28", "회사 휴무", "OPERATOR");

        mvc.perform(get("/api/admin/holidays/calendar").param("from", "2043-09-01").param("to", "2043-09-30").cookie(none)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].kind").value("HOLIDAY")).andExpect(jsonPath("$.data[1].kind").value("COMPANY"))
                .andExpect(jsonPath("$.data[1].name").value("회사 휴무"));
        mvc.perform(get("/api/admin/holidays/calendar").param("from", "2043-01-01").param("to", "2046-01-01").cookie(none)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.to").isNotEmpty());
        mvc.perform(get("/api/admin/holidays/calendar").param("from", "2043-09-01").param("to", "2043-09-30")).andExpect(status().isUnauthorized());
    }

    @Test
    void 지금_받기는_실패해도_표를_그대로_두고_결과를_남긴다() throws Exception {
        long operator = operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE, Permission.SITE_SECRET);
        Cookie session = login(operator);
        // 바깥(data.go.kr)으로 나가지 않게 — 키를 넣고 특일 정보 주소를 닫힌 포트로
        mvc.perform(withCsrf(put("/api/admin/public-data-settings"), session).content("{\"serviceKey\":\"holiday-admin-test-key\"}")).andExpect(status().isOk());
        mvc.perform(withCsrf(put("/api/admin/public-data-settings/endpoints/holiday"), session).content("{\"url\":\"https://127.0.0.1:9/getRestDeInfo\"}"))
                .andExpect(status().isOk());
        insert("2043-01-01", "기본값", "SEED");
        int before = jdbc.queryForObject("SELECT count(*) FROM site_holidays", Integer.class);

        mvc.perform(withCsrf(post("/api/admin/holidays/sync"), session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("FAILED")).andExpect(jsonPath("$.data.failedYears.length()").value(2))
                .andExpect(jsonPath("$.data.ranBy").value(operatorName(operator)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM site_holidays", Integer.class)).isEqualTo(before);
        mvc.perform(get("/api/admin/holidays").param("year", "2043").cookie(session)).andExpect(jsonPath("$.data.sync.outcome").value("FAILED"))
                .andExpect(jsonPath("$.data.keyConfigured").value(true));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_id = ? AND action = 'SITE_HOLIDAY_SYNC'", Integer.class, operator)).isEqualTo(1);
    }
}
