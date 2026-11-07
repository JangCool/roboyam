package com.community.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.analytics.settings.StatsCollectionSettings;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.flyway.SchemaMigration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 「설정 › 통계 수집」 API(site-analytics-contract 「API」) + 대시보드 묶음 응답의 {@code disabled} — 실제 관리자 앱 문맥 + PostgreSQL(보안 필터·권한 인터셉터·CSRF·감사).
 * 설정은 한 행이라 시험이 끝나면 모두 꺼짐 · 90일로 되돌린다. 방문 가젯이 읽는 분석 표는 api 모듈의 analytics 스트림 파일로 만든다(관리자 앱은 그 스트림을 이관하지 않는다).
 */
class AnalyticsSettingsAdminHttpTest extends Commerce04AdminTestBase {

    private static final String URL = "/api/admin/analytics/settings";

    @Autowired
    private StatsCollectionSettings statsSettings;

    @BeforeAll
    static void analyticsTables() {
        new SchemaMigration("filesystem:../api/src/main/resources/db/migration/").migrateStream(CommerceTestDatabase.migrated("admin"), SchemaMigration.ANALYTICS);
    }

    @AfterEach
    void reset() {
        jdbc.update("UPDATE stats_collection_settings SET enabled = false, visits = false, raw_events = false, raw_retention_days = 90, referrers = false, flows = false, "
                + "notification_sends = false, board_activity = false WHERE id = 1");
        statsSettings.invalidate();
    }

    private static String body(boolean enabled, boolean visits, boolean raw, Object days, boolean referrers, boolean flows, boolean sends, boolean board) {
        return "{\"enabled\":" + enabled + ",\"visits\":" + visits + ",\"rawEvents\":" + raw + ",\"rawRetentionDays\":" + days + ",\"referrers\":" + referrers
                + ",\"flows\":" + flows + ",\"notificationSends\":" + sends + ",\"boardActivity\":" + board + "}";
    }

    private long history() {
        return jdbc.queryForObject("SELECT count(*) FROM stats_collection_setting_history", Long.class);
    }

    private long audits(long operator) {
        return (long) auditLogs.where().actor(operator).action("STATS_COLLECTION_UPDATE").count();
    }

    // ── 8. 설정 API ─────────────────────────────────────────────────────────

    @Test
    void 저장하면_바뀐_칸만_이력과_감사에_남고_같은_값을_다시_저장하면_아무것도_남지_않는다() throws Exception {
        long me = operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE);
        Cookie session = login(me);
        mvc.perform(get(URL).cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.rawRetentionDays").value(90));
        long historyBefore = history();

        mvc.perform(withCsrf(put(URL), session).content(body(true, true, false, 180, false, false, true, false))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true)).andExpect(jsonPath("$.data.rawRetentionDays").value(180))
                .andExpect(jsonPath("$.data.updatedBy").value(operatorName(me))).andExpect(jsonPath("$.data.updatedAt").isString());
        assertThat(history()).isEqualTo(historyBefore + 1);
        String changes = jdbc.queryForObject("SELECT changes::text FROM stats_collection_setting_history WHERE operator_id = ? ORDER BY changed_at DESC LIMIT 1",
                String.class, me);
        assertThat(changes).contains("\"enabled\"", "\"visits\"", "\"rawRetentionDays\"", "\"notificationSends\"", "\"180\"")
                .doesNotContain("\"rawEvents\"", "\"referrers\"", "\"flows\"", "\"boardActivity\"");
        assertThat(audits(me)).isEqualTo(1);
        String detail = auditLogs.where().actor(me).action("STATS_COLLECTION_UPDATE").single().detail();
        assertThat(detail).contains("before", "after", "rawRetentionDays").doesNotContain("boardActivity");

        // 같은 값 — 이력·감사 없음
        mvc.perform(withCsrf(put(URL), session).content(body(true, true, false, 180, false, false, true, false))).andExpect(status().isOk());
        assertThat(history()).isEqualTo(historyBefore + 1);
        assertThat(audits(me)).isEqualTo(1);

        // 방문 수집 전체를 꺼도 하위 칸 값은 그대로 저장한다
        mvc.perform(withCsrf(put(URL), session).content(body(false, true, false, 180, false, false, true, false))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false)).andExpect(jsonPath("$.data.visits").value(true));

        mvc.perform(get(URL + "/history").param("size", "1").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].changes.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].changes[0].field").value("enabled"))
                .andExpect(jsonPath("$.data.items[0].changes[0].labelKey").value("settingHistory.field.analyticsEnabled"))
                .andExpect(jsonPath("$.data.items[0].changes[0].before").value("true"))
                .andExpect(jsonPath("$.data.items[0].changes[0].after").value("false"));
    }

    @Test
    void 보관_일수가_목록_밖이거나_칸이_비면_400() throws Exception {
        Cookie session = login(operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE));
        long historyBefore = history();
        for (Object days : new Object[] {45, 0, 366, -90, "null"}) {
            mvc.perform(withCsrf(put(URL), session).content(body(true, true, true, days, true, true, true, true))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.rawRetentionDays").exists());
        }
        mvc.perform(withCsrf(put(URL), session).content("{\"enabled\":true,\"rawRetentionDays\":90}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.visits").exists()).andExpect(jsonPath("$.error.fields.boardActivity").exists());
        mvc.perform(withCsrf(put(URL), session)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.enabled").exists());
        mvc.perform(get(URL + "/history").param("size", "101").cookie(session)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.size").exists());
        assertThat(history()).isEqualTo(historyBefore);
    }

    @Test
    void 조회는_site_read_저장은_site_manage_와_CSRF() throws Exception {
        Cookie reader = login(operatorWith(Permission.SITE_READ));
        mvc.perform(get(URL).cookie(reader)).andExpect(status().isOk());
        mvc.perform(get(URL + "/history").cookie(reader)).andExpect(status().isOk());
        mvc.perform(withCsrf(put(URL), reader).content(body(true, true, false, 90, false, false, false, false))).andExpect(status().isForbidden());

        Cookie nobody = login(operatorWith(Permission.POST_READ));
        mvc.perform(get(URL).cookie(nobody)).andExpect(status().isForbidden());

        Cookie manager = login(operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE));
        mvc.perform(put(URL).cookie(manager).contentType("application/json").content(body(true, true, false, 90, false, false, false, false)))
                .andExpect(status().isForbidden()); // CSRF 없음
        assertThat(jdbc.queryForObject("SELECT enabled FROM stats_collection_settings WHERE id = 1", Boolean.class)).isFalse();
    }

    // ── 8. 대시보드 disabled ────────────────────────────────────────────────

    @Test
    void 권한이_있고_수집이_꺼진_종류만_disabled_이고_권한이_없는_종류는_disabled_가_아니다() throws Exception {
        String all = "VISITORS,POPULAR_PAGES,REFERRERS,NOTIFICATION_HEALTH,POST_ACTIVITY,BOARD_RANKING,SIGNUP_TREND";
        Cookie full = login(operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE, Permission.NOTIFICATION_READ, Permission.POST_READ, Permission.MEMBER_READ));
        mvc.perform(get("/api/admin/dashboard/gadgets").param("types", all).cookie(full)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disabled.length()").value(7))
                .andExpect(jsonPath("$.data.gadgets.VISITORS").doesNotExist())
                .andExpect(jsonPath("$.data.unavailable.length()").value(0));

        // 방문자 + 알림만 켠다(설정 저장 — 이 서버 사본은 바로 바뀐다)
        mvc.perform(withCsrf(put(URL), full).content(body(true, true, false, 90, false, false, true, false))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/dashboard/gadgets").param("types", all).cookie(full)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disabled.length()").value(5))
                .andExpect(jsonPath("$.data.disabled[0]").value("POPULAR_PAGES"))
                .andExpect(jsonPath("$.data.gadgets.VISITORS.visitorsToday").isNumber())
                .andExpect(jsonPath("$.data.gadgets.VISITORS.hours").isArray())
                .andExpect(jsonPath("$.data.gadgets.NOTIFICATION_HEALTH").exists());

        // 방문 수집 전체를 끄면 방문자 칸이 켜져 있어도 꺼진 것
        mvc.perform(withCsrf(put(URL), full).content(body(false, true, true, 90, true, true, true, true))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/dashboard/gadgets").param("types", "VISITORS,POPULAR_PAGES,REFERRERS,POST_ACTIVITY").cookie(full)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disabled.length()").value(3))
                .andExpect(jsonPath("$.data.gadgets.POST_ACTIVITY").exists());

        // site.read 만 — 알림·글은 권한 없음(null, disabled 아님), 방문은 꺼짐(disabled)
        mvc.perform(withCsrf(put(URL), full).content(body(false, false, false, 90, false, false, false, false))).andExpect(status().isOk());
        Cookie site = login(operatorWith(Permission.SITE_READ));
        mvc.perform(get("/api/admin/dashboard/gadgets").param("types", "VISITORS,NOTIFICATION_HEALTH,POST_ACTIVITY").cookie(site)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disabled.length()").value(1))
                .andExpect(jsonPath("$.data.disabled[0]").value("VISITORS"));
        mvc.perform(get("/api/admin/dashboard/gadgets").param("types", "NOTIFICATION_HEALTH").cookie(site)).andExpect(status().isForbidden());
    }
}
