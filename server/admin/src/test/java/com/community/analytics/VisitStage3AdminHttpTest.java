package com.community.analytics;

import static org.hamcrest.Matchers.nullValue;
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
import java.time.YearMonth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 방문 통계 3단계 관리자 API 두 개(주·월 순방문자 {@code /uniques} · 월별 보기 {@code /monthly}, site-analytics-contract 「결정 ④ 화면 확정」)의 연결 — 실제 관리자 앱 문맥 +
 * PostgreSQL(보안 필터·권한 인터셉터·오류 응답 모양). 계산은 {@code VisitUniquesMonthlyServiceTest}(고정 시계). 여기서는 실제 시계의 이번 달로 400·403·{@code no-store}·
 * 수집 꺼짐 응답 모양만 본다. 설정은 한 행이라 시험 앞뒤로 모두 꺼짐으로 둔다({@code VisitAnalyticsAdminHttpTest} 와 같다).
 */
class VisitStage3AdminHttpTest extends Commerce04AdminTestBase {

    private static final String BASE = "/api/admin/analytics/visits";

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

    private static YearMonth thisMonth() {
        return YearMonth.from(ServiceTime.today(Clock.systemUTC()));
    }

    private static MockHttpServletRequestBuilder monthly(String from, String to, Cookie session) {
        MockHttpServletRequestBuilder r = get(BASE + "/monthly").cookie(session);
        if (from != null) {
            r.param("from", from);
        }
        if (to != null) {
            r.param("to", to);
        }
        return r;
    }

    @Test
    void site_read_가_없으면_두_API_모두_403() throws Exception {
        Cookie nobody = login(operatorWith(Permission.POST_READ));
        String m = thisMonth().toString();
        mvc.perform(get(BASE + "/uniques").cookie(nobody)).andExpect(status().isForbidden());
        mvc.perform(monthly(m, m, nobody)).andExpect(status().isForbidden());
    }

    @Test
    void 월별_기간이_틀리면_400_fields_from_이번_달_뒤면_fields_to() throws Exception {
        Cookie session = login(operatorWith(Permission.SITE_READ));
        YearMonth t = thisMonth();
        String[][] fromCases = {{null, t.toString()}, {t.toString(), null}, {"2026/01", t.toString()}, {"abc", t.toString()}, {t.toString(), t.minusMonths(1).toString()},
                {t.minusMonths(36).toString(), t.toString()}};
        for (String[] c : fromCases) {
            mvc.perform(monthly(c[0], c[1], session)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.from").isString())
                    .andExpect(jsonPath("$.error.fields.to").doesNotExist());
        }
        mvc.perform(monthly(t.toString(), t.plusMonths(1).toString(), session)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.to").isString())
                .andExpect(jsonPath("$.error.fields.from").doesNotExist());
        mvc.perform(monthly(t.minusMonths(35).toString(), t.toString(), session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.months.length()").value(36));
    }

    @Test
    void 수집이_꺼져_있으면_순방문자는_200_no_store_와_모든_수_null_월별은_쌓인_값_모양() throws Exception {
        Cookie session = login(operatorWith(Permission.SITE_READ));
        mvc.perform(get(BASE + "/uniques").cookie(session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.collect.visits").value(false))
                .andExpect(jsonPath("$.data.audiences.VISITOR.week.current.count").value(nullValue()))
                .andExpect(jsonPath("$.data.audiences.VISITOR.week.current.partial").value(true))
                .andExpect(jsonPath("$.data.audiences.VISITOR.week.previousSamePoint").value(nullValue()))
                .andExpect(jsonPath("$.data.audiences.MEMBER.month.current.count").value(nullValue()))
                .andExpect(jsonPath("$.data.audiences.VISITOR.weeks.length()").value(12))
                .andExpect(jsonPath("$.data.audiences.MEMBER.months.length()").value(12));
        String m = thisMonth().toString();
        mvc.perform(monthly(m, m, session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.range.from").value(m)).andExpect(jsonPath("$.data.collect.visits").value(false))
                .andExpect(jsonPath("$.data.months.length()").value(1)).andExpect(jsonPath("$.data.months[0].month").value(m))
                .andExpect(jsonPath("$.data.months[0].partial").value(true)).andExpect(jsonPath("$.data.totals.visitorsDailySum").isNumber())
                .andExpect(jsonPath("$.data.pages").isArray()).andExpect(jsonPath("$.data.referrers.groups").isArray());
    }
}
