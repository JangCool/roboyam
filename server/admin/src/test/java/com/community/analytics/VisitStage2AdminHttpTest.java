package com.community.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/**
 * 「방문자 통계」 2단계 관리자 API 5개(environment · search · bots · health · requests)와 「설정 › 통계 수집」의 2단계 세 칸 — 실제 관리자 앱 문맥 + PostgreSQL.
 * 계산은 {@code VisitStage2AdminServiceTest}·{@code VisitDiagnosticsAdminServiceTest}(고정 시계). 여기서는 기간 400 · 권한 403 · no-store · 꺼짐 모양 · 설정 PUT 의 새 칸.
 * 설정은 한 행이라 시험 앞뒤로 모두 꺼짐 · 90일로 둔다(새 칸 포함).
 */
class VisitStage2AdminHttpTest extends Commerce04AdminTestBase {

    private static final String BASE = "/api/admin/analytics/visits";
    private static final String SETTINGS = "/api/admin/analytics/settings";
    private static final List<String> PERIOD_APIS = List.of("/environment", "/search", "/bots");

    @Autowired
    private StatsCollectionSettings statsSettings;

    @BeforeAll
    static void analyticsTables() {
        new SchemaMigration("filesystem:../api/src/main/resources/db/migration/").migrateStream(CommerceTestDatabase.migrated("admin"), SchemaMigration.ANALYTICS);
    }

    @BeforeEach
    @AfterEach
    void allOff() {
        jdbc.update("UPDATE stats_collection_settings SET enabled = false, visits = false, raw_events = false, raw_retention_days = 90, referrers = false, flows = false, "
                + "environment = false, search_terms = false, bots = false, notification_sends = false, board_activity = false WHERE id = 1");
        statsSettings.invalidate();
    }

    private static LocalDate today() {
        return ServiceTime.today(Clock.systemUTC());
    }

    @Test
    void 기간이_틀리면_세_API_모두_400_fields_from_오늘_뒤면_fields_to() throws Exception {
        Cookie session = login(operatorWith(Permission.SITE_READ));
        LocalDate t = today();
        String[][] fromCases = {{null, t.toString()}, {t.toString(), null}, {"abc", t.toString()}, {t.toString(), t.minusDays(1).toString()},
                {t.minusDays(90).toString(), t.toString()}};
        for (String api : PERIOD_APIS) {
            for (String[] c : fromCases) {
                var r = get(BASE + api).cookie(session);
                if (c[0] != null) {
                    r.param("from", c[0]);
                }
                if (c[1] != null) {
                    r.param("to", c[1]);
                }
                mvc.perform(r).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.from").isString())
                        .andExpect(jsonPath("$.error.fields.to").doesNotExist());
            }
            mvc.perform(get(BASE + api).cookie(session).param("from", t.toString()).param("to", t.plusDays(1).toString())).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.to").isString());
            mvc.perform(get(BASE + api).cookie(session).param("from", t.minusDays(89).toString()).param("to", t.toString())).andExpect(status().isOk());
        }
        mvc.perform(get(BASE + "/requests").cookie(session).param("size", "101")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.size").isString());
        mvc.perform(get(BASE + "/requests").cookie(session).param("page", "-1")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.page").isString());
    }

    @Test
    void site_read_가_없으면_다섯_API_모두_403() throws Exception {
        Cookie nobody = login(operatorWith(Permission.POST_READ));
        String d = today().toString();
        for (String api : PERIOD_APIS) {
            mvc.perform(get(BASE + api).cookie(nobody).param("from", d).param("to", d)).andExpect(status().isForbidden());
        }
        mvc.perform(get(BASE + "/health").cookie(nobody)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/requests").cookie(nobody)).andExpect(status().isForbidden());
    }

    @Test
    void 수집이_꺼져_있으면_200_no_store_모든_키_0_series_빈_배열() throws Exception {
        Cookie session = login(operatorWith(Permission.SITE_READ));
        String d = today().toString();
        mvc.perform(get(BASE + "/environment").cookie(session).param("from", d).param("to", d)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.devices.length()").value(3)).andExpect(jsonPath("$.data.browsers.length()").value(7))
                .andExpect(jsonPath("$.data.oses.length()").value(6)).andExpect(jsonPath("$.data.screens.length()").value(5))
                .andExpect(jsonPath("$.data.screens[0].key").value("W480")).andExpect(jsonPath("$.data.series.length()").value(0))
                .andExpect(jsonPath("$.data.collect.enabled").value(false)).andExpect(jsonPath("$.data.collect.environment").value(false));
        mvc.perform(get(BASE + "/search").cookie(session).param("from", d).param("to", d)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.totals.searches").value(0))
                .andExpect(jsonPath("$.data.totals.filtered").value(0)).andExpect(jsonPath("$.data.terms.length()").value(0))
                .andExpect(jsonPath("$.data.noResultTerms.length()").value(0)).andExpect(jsonPath("$.data.collect.searchTerms").value(false));
        mvc.perform(get(BASE + "/bots").cookie(session).param("from", d).param("to", d)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.totals.bots").value(0))
                .andExpect(jsonPath("$.data.totals.humans").doesNotExist()).andExpect(jsonPath("$.data.today.bots").value(0))
                .andExpect(jsonPath("$.data.kinds.length()").value(6)).andExpect(jsonPath("$.data.series.length()").value(0))
                .andExpect(jsonPath("$.data.collect.bots").value(false)).andExpect(jsonPath("$.data.collect.visits").value(false));
        mvc.perform(get(BASE + "/health").cookie(session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.status").value("OFF")).andExpect(jsonPath("$.data.issues[*].code", org.hamcrest.Matchers.hasItem("COLLECT_OFF")))
                .andExpect(jsonPath("$.data.crons.length()").value(7)).andExpect(jsonPath("$.data.raw.retentionDays").value(90));
        mvc.perform(get(BASE + "/requests").cookie(session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items").isArray()).andExpect(jsonPath("$.data.total").isNumber()).andExpect(jsonPath("$.data.pending").isNumber())
                .andExpect(jsonPath("$.data.last30Days").isNumber());
    }

    // ── 설정의 2단계 세 칸 ──────────────────────────────────────────────────

    private static String body(boolean enabled, String more) {
        return "{\"enabled\":" + enabled + ",\"visits\":true,\"rawEvents\":false,\"rawRetentionDays\":90,\"referrers\":false,\"flows\":false,"
                + "\"notificationSends\":false,\"boardActivity\":false" + more + "}";
    }

    private String lastChanges(long operator) {
        return jdbc.queryForObject("SELECT changes::text FROM stats_collection_setting_history WHERE operator_id = ? ORDER BY changed_at DESC, id DESC LIMIT 1",
                String.class, operator);
    }

    @Test
    void 새_칸은_기본_false_이고_PUT_에서_빠지면_저장값_유지_넣으면_바뀐다() throws Exception {
        long me = operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE);
        Cookie session = login(me);
        mvc.perform(get(SETTINGS).cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.environment").value(false))
                .andExpect(jsonPath("$.data.searchTerms").value(false)).andExpect(jsonPath("$.data.bots").value(false));

        // 세 칸을 켠다 — 이력·감사의 칸 차례: … flows · environment · searchTerms · bots · notificationSends …
        mvc.perform(withCsrf(put(SETTINGS), session).content(body(true, ",\"environment\":true,\"searchTerms\":true,\"bots\":true"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.environment").value(true)).andExpect(jsonPath("$.data.searchTerms").value(true)).andExpect(jsonPath("$.data.bots").value(true));
        String changes = lastChanges(me);
        assertThat(changes).contains("\"environment\"", "\"searchTerms\"", "\"bots\"");
        assertThat(changes.indexOf("\"enabled\"")).isLessThan(changes.indexOf("\"visits\""));
        assertThat(changes.indexOf("\"visits\"")).isLessThan(changes.indexOf("\"environment\""));
        assertThat(changes.indexOf("\"environment\"")).isLessThan(changes.indexOf("\"searchTerms\""));
        assertThat(changes.indexOf("\"searchTerms\"")).isLessThan(changes.indexOf("\"bots\""));
        String detail = jdbc.queryForObject("SELECT detail::text FROM audit_logs WHERE actor_id = ? AND action = 'STATS_COLLECTION_UPDATE' ORDER BY id DESC LIMIT 1",
                String.class, me);
        assertThat(detail).contains("environment", "searchTerms", "bots");
        mvc.perform(get(SETTINGS + "/history").param("size", "1").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].changes.length()").value(5))
                .andExpect(jsonPath("$.data.items[0].changes[2].field").value("environment"))
                .andExpect(jsonPath("$.data.items[0].changes[2].labelKey").value("settingHistory.field.environment"))
                .andExpect(jsonPath("$.data.items[0].changes[3].field").value("searchTerms"))
                .andExpect(jsonPath("$.data.items[0].changes[4].field").value("bots"));

        // 세 칸을 모르는 화면이 저장 — 빠진 칸은 저장값 그대로(꺼지지 않는다), 바뀐 칸만 이력
        long historyBefore = jdbc.queryForObject("SELECT count(*) FROM stats_collection_setting_history", Long.class);
        mvc.perform(withCsrf(put(SETTINGS), session).content(body(false, ""))).andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.environment").value(true)).andExpect(jsonPath("$.data.searchTerms").value(true)).andExpect(jsonPath("$.data.bots").value(true));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stats_collection_setting_history", Long.class)).isEqualTo(historyBefore + 1);
        assertThat(lastChanges(me)).contains("\"enabled\"").doesNotContain("\"environment\"", "\"searchTerms\"", "\"bots\"");

        // null 로 보내도 빠진 것과 같다 · 하나만 끈다
        mvc.perform(withCsrf(put(SETTINGS), session).content(body(false, ",\"environment\":null,\"searchTerms\":false"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.environment").value(true)).andExpect(jsonPath("$.data.searchTerms").value(false)).andExpect(jsonPath("$.data.bots").value(true));
        assertThat(lastChanges(me)).contains("\"searchTerms\"").doesNotContain("\"environment\"", "\"bots\"");
        assertThat(jdbc.queryForObject("SELECT environment::text || search_terms::text || bots::text FROM stats_collection_settings WHERE id = 1", String.class))
                .isEqualTo("truefalsetrue");

        // 같은 값 — 아무것도 남기지 않는다
        long h = jdbc.queryForObject("SELECT count(*) FROM stats_collection_setting_history", Long.class);
        mvc.perform(withCsrf(put(SETTINGS), session).content(body(false, ""))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stats_collection_setting_history", Long.class)).isEqualTo(h);
    }

    @Test
    void 설정을_저장하면_이_서버의_화면_API_가_새_칸을_바로_따른다() throws Exception {
        long me = operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE);
        Cookie session = login(me);
        String d = today().toString();
        mvc.perform(withCsrf(put(SETTINGS), session).content(body(true, ",\"environment\":true,\"bots\":true"))).andExpect(status().isOk());
        mvc.perform(get(BASE + "/environment").cookie(session).param("from", d).param("to", d)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collect.environment").value(true)).andExpect(jsonPath("$.data.series.length()").value(1));
        mvc.perform(get(BASE + "/bots").cookie(session).param("from", d).param("to", d)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collect.bots").value(true)).andExpect(jsonPath("$.data.collect.visits").value(true))
                .andExpect(jsonPath("$.data.totals.humans").isNumber()).andExpect(jsonPath("$.data.series.length()").value(1));
        mvc.perform(get(BASE + "/search").cookie(session).param("from", d).param("to", d)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collect.searchTerms").value(false));
        mvc.perform(get(BASE + "/health").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(org.hamcrest.Matchers.not("OFF")));
    }
}
