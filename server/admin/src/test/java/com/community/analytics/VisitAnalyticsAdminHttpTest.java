package com.community.analytics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.analytics.settings.StatsCollectionSettings;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.flyway.SchemaMigration;
import com.community.zconfiguration.time.ServiceTime;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 「방문자 통계」 관리자 API 7개의 연결(site-analytics-contract 「「방문자 통계」 화면」) — 실제 관리자 앱 문맥 + PostgreSQL(보안 필터·권한 인터셉터·오류 응답 모양).
 * 계산 자체는 {@code VisitAnalyticsAdminServiceTest}(고정 시계). 여기서는 실제 시계의 오늘로 기간 검증 400·권한 403·{@code no-store}·수집 꺼짐 응답 모양만 본다.
 * 설정은 한 행이라 시험 앞뒤로 모두 꺼짐 · 90일로 둔다.
 */
class VisitAnalyticsAdminHttpTest extends Commerce04AdminTestBase {

    private static final String BASE = "/api/admin/analytics/visits";
    private static final List<String> PERIOD_APIS = List.of("/summary", "/pages", "/pages/detail", "/referrers", "/flows", "/time");

    @Autowired
    private StatsCollectionSettings statsSettings;

    @BeforeAll
    static void analyticsTables() {
        new SchemaMigration("filesystem:../api/src/main/resources/db/migration/").migrateStream(CommerceTestDatabase.migrated("admin"), SchemaMigration.ANALYTICS);
    }

    @BeforeEach
    @AfterEach
    void allOff() {
        jdbc.update("UPDATE stats_collection_settings SET enabled = false, visits = false, raw_events = false, raw_retention_months = 2, referrers = false, flows = false, "
                + "notification_sends = false, board_activity = false WHERE id = 1");
        statsSettings.invalidate();
    }

    private static LocalDate today() {
        return ServiceTime.today(Clock.systemUTC());
    }

    private static MockHttpServletRequestBuilder call(String api, String from, String to, Cookie session) {
        MockHttpServletRequestBuilder r = get(BASE + api).cookie(session);
        if (from != null) {
            r.param("from", from);
        }
        if (to != null) {
            r.param("to", to);
        }
        if (api.equals("/pages/detail")) {
            r.param("path", "/");
        }
        return r;
    }

    @Test
    void 기간이_틀리면_여섯_API_모두_400_fields_from_오늘_뒤면_fields_to() throws Exception {
        Cookie session = login(operatorWith(Permission.SITE_READ));
        LocalDate t = today();
        String[][] fromCases = {{null, t.toString()}, {t.toString(), null}, {"2026/11/01", t.toString()}, {"abc", t.toString()},
                {t.toString(), t.minusDays(1).toString()}, {t.minusDays(90).toString(), t.toString()}};
        for (String api : PERIOD_APIS) {
            for (String[] c : fromCases) {
                mvc.perform(call(api, c[0], c[1], session)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.from").isString())
                        .andExpect(jsonPath("$.error.fields.to").doesNotExist());
            }
            mvc.perform(call(api, t.toString(), t.plusDays(1).toString(), session)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.to").isString()).andExpect(jsonPath("$.error.fields.from").doesNotExist());
            mvc.perform(call(api, t.minusDays(89).toString(), t.toString(), session)).andExpect(status().isOk()); // 90일
        }
    }

    @Test
    void 칸별_입력이_틀리면_400_그_칸() throws Exception {
        Cookie session = login(operatorWith(Permission.SITE_READ));
        String d = today().toString();
        mvc.perform(get(BASE + "/pages").cookie(session).param("from", d).param("to", d).param("q", "a".repeat(101))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.q").isString());
        mvc.perform(get(BASE + "/pages").cookie(session).param("from", d).param("to", d).param("size", "101")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.size").isString());
        mvc.perform(get(BASE + "/pages").cookie(session).param("from", d).param("to", d).param("page", "x")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.page").isString());
        mvc.perform(get(BASE + "/pages").cookie(session).param("from", d).param("to", d).param("kind", "nope")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.kind").isString());
        mvc.perform(get(BASE + "/pages/detail").cookie(session).param("from", d).param("to", d)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.path").isString());
        mvc.perform(get(BASE + "/flows").cookie(session).param("from", d).param("to", d).param("path", "//evil")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.path").isString());
        mvc.perform(get(BASE + "/referrers").cookie(session).param("from", d).param("to", d).param("detail", "maybe")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.detail").isString());
    }

    @Test
    void site_read_가_없으면_일곱_API_모두_403() throws Exception {
        Cookie nobody = login(operatorWith(Permission.POST_READ));
        String d = today().toString();
        for (String api : PERIOD_APIS) {
            mvc.perform(call(api, d, d, nobody)).andExpect(status().isForbidden());
        }
        mvc.perform(get(BASE + "/realtime").cookie(nobody)).andExpect(status().isForbidden());
    }

    @Test
    void 수집이_꺼져_있으면_200_no_store_와_0_빈_배열_모양() throws Exception {
        Cookie session = login(operatorWith(Permission.SITE_READ));
        String d = today().toString();
        mvc.perform(call("/summary", d, d, session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.granularity").value("HOUR")).andExpect(jsonPath("$.data.totals.visitors").value(0))
                .andExpect(jsonPath("$.data.series.length()").value(0)).andExpect(jsonPath("$.data.collect.enabled").value(false))
                .andExpect(jsonPath("$.data.previous.range.to").value(today().minusDays(1).toString())).andExpect(jsonPath("$.data.activeNow").value(0));
        mvc.perform(call("/time", d, d, session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.cells.length()").value(168))
                .andExpect(jsonPath("$.data.cells[0].weekday").value(1)).andExpect(jsonPath("$.data.cells[0].hour").value(0));
        mvc.perform(call("/pages", d, d, session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(call("/pages/detail", d, d, session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.kind").value("OTHER"));
        mvc.perform(call("/referrers", d, d, session).param("detail", "false")).andExpect(status().isOk()).andExpect(jsonPath("$.data.campaigns").isArray())
                .andExpect(jsonPath("$.data.series").isArray());
        mvc.perform(call("/flows", d, d, session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.path").value("/"))
                .andExpect(jsonPath("$.data.top.length()").value(0));
        mvc.perform(get(BASE + "/realtime").cookie(session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.perMinute.length()").value(0)).andExpect(jsonPath("$.data.collect.rawEvents").value(false));
    }
}
