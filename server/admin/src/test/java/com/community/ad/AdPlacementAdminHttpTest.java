package com.community.ad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.ad.dto.AdPlacements;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.audit.service.AuditLabels;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.menu.service.AdminScreenRegistry;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 광고 노출 자리 설정 관리자 API(ad-contract 「공개 노출 목업」 확정 2026-11-01 · 화면·서버 공용 계약 4절) — 조회 {@code ad.read}(저장한 적 없으면 기본값 · {@code updatedAt}·
 * {@code updatedBy} null), 저장 {@code ad.settings}({@code ad.read} 만으로는 403 · CSRF 필요), 칸 검사 400 {@code fields.<묶음>.<칸>}·빠진 묶음 {@code fields.<묶음>},
 * 감사 {@code AD_PLACEMENT_UPDATE}(바뀐 칸 before/after — 같은 값 다시 저장은 기록 없음). 광고 일정표 유형에 날개 배너(한도 4)가 있다.
 */
class AdPlacementAdminHttpTest extends Commerce04AdminTestBase {

    private static final String VALID = "{\"postInline\":{\"enabled\":false,\"adType\":\"SQUARE_BANNER\",\"position\":\"AFTER_BODY\"},"
            + "\"wings\":{\"left\":true,\"right\":false,\"screens\":[\"POST\",\"HOME\",\"HOME\"]},\"boardList\":{\"enabled\":true,\"after\":12},"
            + "\"adult\":{\"enabled\":true}}";

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM ad_placement_settings");
    }

    @Test
    void 저장한_적_없으면_기본값이고_ad_read_로_읽는다() throws Exception {
        Cookie none = login(operatorWith());
        Cookie reader = login(operatorWith(Permission.AD_READ));
        mvc.perform(get("/api/admin/ads/placements").cookie(none)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/ads/placements").cookie(reader)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.postInline.enabled").value(true)).andExpect(jsonPath("$.data.postInline.adType").value("TEXT_LINE"))
                .andExpect(jsonPath("$.data.postInline.position").value("BETWEEN_CARDS")).andExpect(jsonPath("$.data.wings.left").value(true))
                .andExpect(jsonPath("$.data.wings.right").value(true)).andExpect(jsonPath("$.data.wings.screens.length()").value(AdPlacements.WING_SCREENS.size()))
                .andExpect(jsonPath("$.data.boardList.enabled").value(true)).andExpect(jsonPath("$.data.boardList.after").value(5))
                .andExpect(jsonPath("$.data.adult.enabled").value(false)).andExpect(jsonPath("$.data.updatedAt").doesNotExist()).andExpect(jsonPath("$.data.updatedBy").doesNotExist());
    }

    @Test
    void ad_read_로는_저장_403_ad_settings_로_저장하고_감사를_남긴다() throws Exception {
        Cookie reader = login(operatorWith(Permission.AD_READ));
        mvc.perform(withCsrf(put("/api/admin/ads/placements"), reader).content(VALID)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_placement_settings", Integer.class)).isZero();

        long me = operatorWith(Permission.AD_READ, Permission.AD_SETTINGS);
        Cookie settings = login(me);
        // CSRF 토큰이 없으면 403
        mvc.perform(put("/api/admin/ads/placements").cookie(settings).contentType(MediaType.APPLICATION_JSON).content(VALID)).andExpect(status().isForbidden());

        mvc.perform(withCsrf(put("/api/admin/ads/placements"), settings).content(VALID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postInline.enabled").value(false)).andExpect(jsonPath("$.data.postInline.adType").value("SQUARE_BANNER"))
                .andExpect(jsonPath("$.data.postInline.position").value("AFTER_BODY")).andExpect(jsonPath("$.data.wings.left").value(true))
                .andExpect(jsonPath("$.data.wings.right").value(false)).andExpect(jsonPath("$.data.wings.screens.length()").value(2))
                .andExpect(jsonPath("$.data.wings.screens[0]").value("HOME")).andExpect(jsonPath("$.data.wings.screens[1]").value("POST"))
                .andExpect(jsonPath("$.data.boardList.after").value(12)).andExpect(jsonPath("$.data.adult.enabled").value(true))
                .andExpect(jsonPath("$.data.updatedAt").isString())
                .andExpect(jsonPath("$.data.updatedBy.id").value(Long.toString(me))).andExpect(jsonPath("$.data.updatedBy.name").value(operatorName(me)));
        Map<String, Object> row = jdbc.queryForMap("SELECT post_inline_enabled, post_inline_type, array_to_string(wing_screens, ',') AS screens, board_list_after, "
                + "adult_enabled, updated_by FROM ad_placement_settings WHERE id = 1");
        assertThat(row).containsEntry("post_inline_enabled", false).containsEntry("post_inline_type", "SQUARE_BANNER").containsEntry("screens", "HOME,POST")
                .containsEntry("adult_enabled", true).containsEntry("updated_by", me);
        assertThat(((Number) row.get("board_list_after")).intValue()).isEqualTo(12);
        String detail = auditLogs.where().actor(me).action("AD_PLACEMENT_UPDATE").latest().orElseThrow().detail();
        assertThat(detail).contains("\"before\"", "\"after\"", "postInline.adType", "SQUARE_BANNER", "wings.right", "boardList.after", "adult.enabled", "fromDefaults")
                .doesNotContain("boardList.enabled"); // 바뀌지 않은 칸은 싣지 않는다
        assertThat(auditLogs.where().actor(me).action("AD_PLACEMENT_UPDATE").single().targetType())
                .isEqualTo("AD_PLACEMENTS");

        // 같은 값 다시 저장 — 감사 없음
        mvc.perform(withCsrf(put("/api/admin/ads/placements"), settings).content(VALID)).andExpect(status().isOk());
        assertThat(auditLogs.where().actor(me).action("AD_PLACEMENT_UPDATE").count()).isOne();
        // 날개 화면은 비어도 된다
        mvc.perform(withCsrf(put("/api/admin/ads/placements"), settings).content(VALID.replace("[\"POST\",\"HOME\",\"HOME\"]", "[]"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wings.screens.length()").value(0));
        mvc.perform(get("/api/admin/ads/placements").cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.wings.screens.length()").value(0))
                .andExpect(jsonPath("$.data.postInline.adType").value("SQUARE_BANNER")).andExpect(jsonPath("$.data.adult.enabled").value(true));

        // 19금 옵션만 끄기 — 감사에 adult.enabled 의 before true · after false
        mvc.perform(withCsrf(put("/api/admin/ads/placements"), settings).content(VALID.replace("[\"POST\",\"HOME\",\"HOME\"]", "[]")
                .replace("\"adult\":{\"enabled\":true}", "\"adult\":{\"enabled\":false}"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adult.enabled").value(false));
        String last = auditLogs.where().actor(me).action("AD_PLACEMENT_UPDATE").latest().orElseThrow().detail();
        assertThat(last.replace(" ", "")).contains("\"before\":{\"adult.enabled\":true}", "\"after\":{\"adult.enabled\":false}");
        assertThat(jdbc.queryForObject("SELECT adult_enabled FROM ad_placement_settings WHERE id = 1", Boolean.class)).isFalse();
    }

    @Test
    void 틀린_칸은_칸_경로로_빠진_묶음은_묶음_이름으로_400() throws Exception {
        Cookie settings = login(operatorWith(Permission.AD_READ, Permission.AD_SETTINGS));
        String bad = "{\"postInline\":{\"enabled\":true,\"adType\":\"WIDE_BANNER\",\"position\":\"MIDDLE\"},"
                + "\"wings\":{\"left\":true,\"right\":true,\"screens\":[\"HOME\",\"SEARCH\"]},\"boardList\":{\"enabled\":true,\"after\":0}}";
        mvc.perform(withCsrf(put("/api/admin/ads/placements"), settings).content(bad)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.['postInline.adType']").exists()).andExpect(jsonPath("$.error.fields.['postInline.position']").exists())
                .andExpect(jsonPath("$.error.fields.['wings.screens']").exists()).andExpect(jsonPath("$.error.fields.['boardList.after']").exists());
        mvc.perform(withCsrf(put("/api/admin/ads/placements"), settings).content(VALID.replace("\"after\":12", "\"after\":21"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.['boardList.after']").exists());
        mvc.perform(withCsrf(put("/api/admin/ads/placements"), settings).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.postInline").exists()).andExpect(jsonPath("$.error.fields.wings").exists())
                .andExpect(jsonPath("$.error.fields.boardList").exists()).andExpect(jsonPath("$.error.fields.adult").exists());
        mvc.perform(withCsrf(put("/api/admin/ads/placements"), settings).content(VALID.replace("\"right\":false,", ""))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.['wings.right']").exists());
        // 19금 묶음 — 빠지면 fields.adult, enabled 가 비면 fields.adult.enabled(기본값으로 메우지 않는다)
        mvc.perform(withCsrf(put("/api/admin/ads/placements"), settings).content(VALID.replace(",\"adult\":{\"enabled\":true}", ""))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.adult").exists()).andExpect(jsonPath("$.error.fields.length()").value(1));
        mvc.perform(withCsrf(put("/api/admin/ads/placements"), settings).content(VALID.replace("\"adult\":{\"enabled\":true}", "\"adult\":{\"enabled\":null}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.['adult.enabled']").exists())
                .andExpect(jsonPath("$.error.fields.length()").value(1));
        mvc.perform(withCsrf(put("/api/admin/ads/placements"), settings).content(VALID.replace("\"adult\":{\"enabled\":true}", "\"adult\":{}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.['adult.enabled']").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_placement_settings", Integer.class)).isZero();
    }

    @Test
    void 광고_일정표_유형에_날개_배너가_있고_한도는_4() throws Exception {
        Cookie reader = login(operatorWith(Permission.AD_READ));
        mvc.perform(get("/api/admin/ads/calendar").param("type", "WING_BANNER").param("month", "2046-03").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.type").value("WING_BANNER")).andExpect(jsonPath("$.data.summary.limit").value(4));
    }

    @Test
    void 권한_ad_settings_기능_켬_감사_일반_대상_종류_메뉴_셋째() {
        assertThat(Permission.AD_SETTINGS.featureReady()).isTrue();
        assertThat(AuditRetention.declared(AuditActions.AD_PLACEMENT_UPDATE)).contains(AuditRetentionClass.GENERAL);
        assertThat(AuditLabels.TARGET_TYPES).contains("AD_PLACEMENTS");
        AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen("adPlacements").orElseThrow();
        assertThat(screen.path()).isEqualTo("/ads/placements");
        assertThat(screen.labelKey()).isEqualTo("menu.adPlacements");
        assertThat(screen.permission()).isEqualTo(Permission.AD_READ);
        assertThat(screen.icon()).isEqualTo("pi pi-sliders-h");
    }
}
