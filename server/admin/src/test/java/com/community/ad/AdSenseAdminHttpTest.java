package com.community.ad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.ad.dto.AdSenseSettings;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 구글 애드센스 관리자 설정(docs/features/adsense-contract.md 「서버 구현」, shared V176) — {@code GET·PUT /api/admin/ads/placements} 의 {@code adsense} 묶음.
 *
 * <ul>
 *   <li>저장한 적 없으면 OFF · 게시자 ID null · 시험 표시 false · 단위 모두 null.</li>
 *   <li>저장·조회 왕복(DB {@code adsense_*} 칸) · {@code adsense} 가 빠진 PUT 은 지금 값 그대로.</li>
 *   <li>틀린 칸은 400 {@code fields.adsense.…}(모드·게시자 ID·디스플레이 단위·인피드 한쪽만·형식·64자) — OFF 면 게시자 ID 없어도 저장.</li>
 *   <li>글자 칸은 앞뒤 공백을 자르고 빈 글자는 null · 감사 {@code AD_PLACEMENT_UPDATE} 에 바뀐 {@code adsense.*} 칸만.</li>
 * </ul>
 */
class AdSenseAdminHttpTest extends Commerce04AdminTestBase {

    private static final String URL = "/api/admin/ads/placements";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CLIENT = "ca-pub-1234567890123456";
    /** 네 묶음(자리 설정) — 애드센스 묶음 없이 */
    private static final String BASE = "{\"postInline\":{\"enabled\":true,\"adType\":\"TEXT_LINE\",\"position\":\"BETWEEN_CARDS\"},"
            + "\"wings\":{\"left\":true,\"right\":true,\"screens\":[\"HOME\"]},\"boardList\":{\"enabled\":true,\"after\":5},\"adult\":{\"enabled\":false}";
    private static final String FULL = "{\"mode\":\"SHOW\",\"client\":\"" + CLIENT + "\",\"adtest\":true,\"units\":{\"WIDE_BANNER\":\"1234567890\","
            + "\"WING_BANNER\":\"2234567890\",\"POST_INLINE\":\"3234567890\",\"BOARD_LIST\":{\"slot\":\"4234567890\",\"layoutKey\":\"-fb+5w+4e-db+86\"}}}";

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM ad_placement_settings");
    }

    private static String body(String adsense) {
        return BASE + (adsense == null ? "" : ",\"adsense\":" + adsense) + "}";
    }

    private ResultActions save(Cookie session, String body) throws Exception {
        return mvc.perform(withCsrf(put(URL), session).content(body));
    }

    /** 한국어 문구로 받는 저장 */
    private ResultActions saveKo(Cookie session, String body) throws Exception {
        return mvc.perform(withCsrf(put(URL), session).content(body).header("Accept-Language", "ko"));
    }

    private Map<String, Object> row() {
        return jdbc.queryForMap("SELECT adsense_mode, adsense_client, adsense_adtest, adsense_wide_banner, adsense_strip_banner, adsense_square_banner, "
                + "adsense_small_banner, adsense_wing_banner, adsense_post_inline, adsense_board_list_slot, adsense_board_list_layout_key "
                + "FROM ad_placement_settings WHERE id = 1");
    }

    private static JsonNode adsenseOf(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString()).path("data").path("adsense");
    }

    /** 마지막 감사 상세(before/after 묶음) */
    private JsonNode lastAudit(long operator) {
        return JSON.readTree(auditLogs.where().actor(operator).action("AD_PLACEMENT_UPDATE").latest().orElseThrow().detail());
    }

    @Test
    void 저장한_적_없으면_adsense_는_OFF_게시자_ID_null_시험_표시_false_단위_모두_null() throws Exception {
        Cookie reader = login(operatorWith(Permission.AD_READ));
        JsonNode a = adsenseOf(mvc.perform(get(URL).cookie(reader)).andExpect(status().isOk()));
        assertThat(a.path("mode").asString()).isEqualTo("OFF");
        assertThat(a.has("client")).isTrue();
        assertThat(a.get("client").isNull()).isTrue();
        assertThat(a.get("adtest").asBoolean()).isFalse();
        JsonNode units = a.get("units");
        for (String slot : AdSenseSettings.DISPLAY_SLOTS) {
            assertThat(units.has(slot)).as(slot).isTrue();
            assertThat(units.get(slot).isNull()).as(slot).isTrue();
        }
        assertThat(units.get("BOARD_LIST").get("slot").isNull()).isTrue();
        assertThat(units.get("BOARD_LIST").get("layoutKey").isNull()).isTrue();
    }

    @Test
    void 저장_조회_왕복_DB_칸_감사에_바뀐_adsense_칸() throws Exception {
        long me = operatorWith(Permission.AD_READ, Permission.AD_SETTINGS);
        Cookie session = login(me);
        save(session, body(FULL)).andExpect(status().isOk()).andExpect(jsonPath("$.data.adsense.mode").value("SHOW"))
                .andExpect(jsonPath("$.data.adsense.client").value(CLIENT)).andExpect(jsonPath("$.data.adsense.adtest").value(true))
                .andExpect(jsonPath("$.data.adsense.units.WIDE_BANNER").value("1234567890"))
                .andExpect(jsonPath("$.data.adsense.units.BOARD_LIST.slot").value("4234567890"))
                .andExpect(jsonPath("$.data.adsense.units.BOARD_LIST.layoutKey").value("-fb+5w+4e-db+86"));
        Map<String, Object> row = row();
        assertThat(row).containsEntry("adsense_mode", "SHOW").containsEntry("adsense_client", CLIENT).containsEntry("adsense_adtest", true)
                .containsEntry("adsense_wide_banner", "1234567890").containsEntry("adsense_strip_banner", null).containsEntry("adsense_square_banner", null)
                .containsEntry("adsense_small_banner", null).containsEntry("adsense_wing_banner", "2234567890").containsEntry("adsense_post_inline", "3234567890")
                .containsEntry("adsense_board_list_slot", "4234567890").containsEntry("adsense_board_list_layout_key", "-fb+5w+4e-db+86");

        // 다시 읽어도 같다
        JsonNode a = adsenseOf(mvc.perform(get(URL).cookie(session)).andExpect(status().isOk()));
        assertThat(a.path("mode").asString()).isEqualTo("SHOW");
        assertThat(a.path("client").asString()).isEqualTo(CLIENT);
        assertThat(a.path("adtest").asBoolean()).isTrue();
        assertThat(a.path("units").path("WING_BANNER").asString()).isEqualTo("2234567890");
        assertThat(a.path("units").path("POST_INLINE").asString()).isEqualTo("3234567890");
        assertThat(a.path("units").get("STRIP_BANNER").isNull()).isTrue();

        // 처음 저장 감사 — 기본값에서 바뀐 애드센스 칸만(값 없는 단위는 싣지 않는다)
        JsonNode first = lastAudit(me);
        assertThat(first.path("fromDefaults").asBoolean()).isTrue();
        JsonNode after = first.path("after");
        assertThat(after.path("adsense.mode").asString()).isEqualTo("SHOW");
        assertThat(after.path("adsense.client").asString()).isEqualTo(CLIENT);
        assertThat(after.path("adsense.adtest").asBoolean()).isTrue();
        assertThat(after.path("adsense.units.WIDE_BANNER").asString()).isEqualTo("1234567890");
        assertThat(after.path("adsense.units.BOARD_LIST.slot").asString()).isEqualTo("4234567890");
        assertThat(after.path("adsense.units.BOARD_LIST.layoutKey").asString()).isEqualTo("-fb+5w+4e-db+86");
        assertThat(after.has("adsense.units.STRIP_BANNER")).isFalse();
        assertThat(first.path("before").path("adsense.mode").asString()).isEqualTo("OFF");

        // 게시자 ID 만 바꾸고 넓은 배너 단위를 비우면 — 그 두 칸만 before/after
        String other = "ca-pub-9999999999999999";
        save(session, body(FULL.replace(CLIENT, other).replace("\"WIDE_BANNER\":\"1234567890\",", ""))).andExpect(status().isOk());
        JsonNode second = lastAudit(me);
        assertThat(second.has("fromDefaults")).isFalse();
        assertThat(fieldNames(second.path("before"))).containsExactlyInAnyOrder("adsense.client", "adsense.units.WIDE_BANNER");
        assertThat(fieldNames(second.path("after"))).containsExactlyInAnyOrder("adsense.client", "adsense.units.WIDE_BANNER");
        assertThat(second.path("before").path("adsense.client").asString()).isEqualTo(CLIENT);
        assertThat(second.path("after").path("adsense.client").asString()).isEqualTo(other);
        assertThat(second.path("before").path("adsense.units.WIDE_BANNER").asString()).isEqualTo("1234567890");
        assertThat(second.path("after").get("adsense.units.WIDE_BANNER").isNull()).isTrue();
        assertThat(row()).containsEntry("adsense_client", other).containsEntry("adsense_wide_banner", null);

        // 같은 값 다시 저장 — 감사 늘지 않음
        long audits = auditLogs.where().actor(me).action("AD_PLACEMENT_UPDATE").count();
        save(session, body(FULL.replace(CLIENT, other).replace("\"WIDE_BANNER\":\"1234567890\",", ""))).andExpect(status().isOk());
        assertThat(auditLogs.where().actor(me).action("AD_PLACEMENT_UPDATE").count()).isEqualTo(audits);
    }

    private static java.util.List<String> fieldNames(JsonNode node) {
        java.util.List<String> out = new java.util.ArrayList<>();
        node.propertyNames().forEach(out::add);
        return out;
    }

    @Test
    void adsense_가_빠진_PUT_은_애드센스_값을_그대로_둔다() throws Exception {
        long me = operatorWith(Permission.AD_READ, Permission.AD_SETTINGS);
        Cookie session = login(me);
        save(session, body(FULL)).andExpect(status().isOk());
        Map<String, Object> saved = row();

        // 자리 설정만 바꾸고 adsense 없이
        save(session, body(null).replace("\"after\":5", "\"after\":7")).andExpect(status().isOk()).andExpect(jsonPath("$.data.boardList.after").value(7))
                .andExpect(jsonPath("$.data.adsense.mode").value("SHOW")).andExpect(jsonPath("$.data.adsense.client").value(CLIENT));
        assertThat(row()).isEqualTo(saved);
        JsonNode audit = lastAudit(me);
        assertThat(fieldNames(audit.path("after"))).containsExactly("boardList.after");

        // adsense: null 도 빠진 것과 같다
        save(session, body("null")).andExpect(status().isOk());
        assertThat(row()).isEqualTo(saved);
    }

    @Test
    void adsense_묶음_없이_처음_저장하면_애드센스는_끔() throws Exception {
        Cookie session = login(operatorWith(Permission.AD_READ, Permission.AD_SETTINGS));
        save(session, body(null)).andExpect(status().isOk()).andExpect(jsonPath("$.data.adsense.mode").value("OFF"));
        assertThat(row()).containsEntry("adsense_mode", "OFF").containsEntry("adsense_client", null).containsEntry("adsense_adtest", false)
                .containsEntry("adsense_board_list_slot", null);
    }

    @Test
    void 모드_게시자_ID_칸_400() throws Exception {
        Cookie session = login(operatorWith(Permission.AD_READ, Permission.AD_SETTINGS));
        // 모르는 모드 · 빠진 모드
        save(session, body("{\"mode\":\"ON\",\"client\":\"" + CLIENT + "\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.['adsense.mode']").exists()).andExpect(jsonPath("$.error.fields.length()").value(1));
        save(session, body("{\"client\":\"" + CLIENT + "\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.['adsense.mode']").exists());
        save(session, body("{\"mode\":\"show\",\"client\":\"" + CLIENT + "\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.['adsense.mode']").exists());
        // VERIFY·SHOW 인데 게시자 ID 없음(빠짐 · 빈 글자 · 공백만)
        for (String mode : new String[]{"VERIFY", "SHOW"}) {
            saveKo(session, body("{\"mode\":\"" + mode + "\"}")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.['adsense.client']").value("사이트 확인이나 광고 표시를 쓰려면 게시자 ID를 넣어 주세요."))
                    .andExpect(jsonPath("$.error.fields.length()").value(1));
            save(session, body("{\"mode\":\"" + mode + "\",\"client\":\"\"}")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.['adsense.client']").exists());
            save(session, body("{\"mode\":\"" + mode + "\",\"client\":\"   \"}")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.['adsense.client']").exists());
        }
        // 형식 틀림 — OFF 여도 거절
        for (String bad : new String[]{"ca-pub-123", "pub-1234567890123456", "ca-pub-12345678901234567", "ca-pub-123456789012345a", "CA-PUB-1234567890123456"}) {
            for (String mode : new String[]{"OFF", "VERIFY", "SHOW"}) {
                saveKo(session, body("{\"mode\":\"" + mode + "\",\"client\":\"" + bad + "\"}")).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.error.fields.['adsense.client']").value("게시자 ID는 ca-pub- 뒤에 숫자 16자리예요."));
            }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_placement_settings", Integer.class)).isZero();
    }

    @Test
    void 디스플레이_단위_ID_는_숫자_10자리_자리마다_칸_키() throws Exception {
        Cookie session = login(operatorWith(Permission.AD_READ, Permission.AD_SETTINGS));
        for (String slot : AdSenseSettings.DISPLAY_SLOTS) {
            for (String bad : new String[]{"123456789", "12345678901", "12345abcde", "１２３４５６７８９０"}) {
                save(session, body("{\"mode\":\"SHOW\",\"client\":\"" + CLIENT + "\",\"units\":{\"" + slot + "\":\"" + bad + "\"}}"))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.['adsense.units." + slot + "']").exists())
                        .andExpect(jsonPath("$.error.fields.length()").value(1));
            }
        }
        // 여러 자리가 함께 틀리면 각각
        save(session, body("{\"mode\":\"OFF\",\"units\":{\"WIDE_BANNER\":\"1\",\"WING_BANNER\":\"2\"}}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.['adsense.units.WIDE_BANNER']").exists()).andExpect(jsonPath("$.error.fields.['adsense.units.WING_BANNER']").exists())
                .andExpect(jsonPath("$.error.fields.length()").value(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_placement_settings", Integer.class)).isZero();
    }

    @Test
    void 목록_인피드는_단위_ID_와_레이아웃_키를_함께_형식_64자() throws Exception {
        Cookie session = login(operatorWith(Permission.AD_READ, Permission.AD_SETTINGS));
        String prefix = "{\"mode\":\"SHOW\",\"client\":\"" + CLIENT + "\",\"units\":{\"BOARD_LIST\":";
        // 단위 ID 만 → 레이아웃 키 칸 · 레이아웃 키만 → 단위 ID 칸(문구 inFeedPair)
        saveKo(session, body(prefix + "{\"slot\":\"1234567890\"}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.['adsense.units.BOARD_LIST.layoutKey']").value("목록 한 줄은 광고 단위 ID와 레이아웃 키를 함께 넣어 주세요."))
                .andExpect(jsonPath("$.error.fields.length()").value(1));
        save(session, body(prefix + "{\"slot\":\"1234567890\",\"layoutKey\":\"  \"}}}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.['adsense.units.BOARD_LIST.layoutKey']").exists()).andExpect(jsonPath("$.error.fields.length()").value(1));
        saveKo(session, body(prefix + "{\"layoutKey\":\"-fb+5w+4e-db+86\"}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.['adsense.units.BOARD_LIST.slot']").value("목록 한 줄은 광고 단위 ID와 레이아웃 키를 함께 넣어 주세요."))
                .andExpect(jsonPath("$.error.fields.length()").value(1));
        // 단위 ID 형식 틀림
        save(session, body(prefix + "{\"slot\":\"12345\",\"layoutKey\":\"-fb+5w+4e-db+86\"}}}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.['adsense.units.BOARD_LIST.slot']").exists()).andExpect(jsonPath("$.error.fields.length()").value(1));
        // 레이아웃 키 형식 틀림
        for (String bad : new String[]{"abc", "-fb", "fb 5w", "-fb+5w+4e-db+86!", "+-fb", "-fb++5w"}) {
            saveKo(session, body(prefix + "{\"slot\":\"1234567890\",\"layoutKey\":\"" + bad + "\"}}}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.['adsense.units.BOARD_LIST.layoutKey']").value("레이아웃 키 형식이 올바르지 않아요(예: -fb+5w+4e-db+86)."))
                    .andExpect(jsonPath("$.error.fields.length()").value(1));
        }
        // 64자는 되고 65자는 400
        String key64 = "-fb+" + "a".repeat(60);
        String key65 = "-fb+" + "a".repeat(61);
        assertThat(key64).hasSize(64);
        save(session, body(prefix + "{\"slot\":\"1234567890\",\"layoutKey\":\"" + key65 + "\"}}}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.['adsense.units.BOARD_LIST.layoutKey']").exists()).andExpect(jsonPath("$.error.fields.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_placement_settings", Integer.class)).isZero();
        save(session, body(prefix + "{\"slot\":\"1234567890\",\"layoutKey\":\"" + key64 + "\"}}}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adsense.units.BOARD_LIST.layoutKey").value(key64));
        assertThat(row()).containsEntry("adsense_board_list_layout_key", key64);
        // 대소문자 섞인 레이아웃 키도 된다(대소문자 무시)
        save(session, body(prefix + "{\"slot\":\"1234567890\",\"layoutKey\":\"-FB+5W+4e-db+86\"}}}")).andExpect(status().isOk());
        assertThat(row()).containsEntry("adsense_board_list_layout_key", "-FB+5W+4e-db+86");
    }

    @Test
    void OFF_면_게시자_ID_없어도_저장되고_공백은_자르고_빈_글자는_null() throws Exception {
        Cookie session = login(operatorWith(Permission.AD_READ, Permission.AD_SETTINGS));
        // OFF · 게시자 ID 없음 · units 없음 · adtest 없음
        save(session, body("{\"mode\":\"OFF\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.adsense.mode").value("OFF"));
        assertThat(row()).containsEntry("adsense_mode", "OFF").containsEntry("adsense_client", null).containsEntry("adsense_adtest", false)
                .containsEntry("adsense_wide_banner", null).containsEntry("adsense_board_list_slot", null).containsEntry("adsense_board_list_layout_key", null);

        // 공백 자르기 · 빈 글자·공백만은 null · OFF 여도 입력해 둔 값은 남긴다
        save(session, body("{\"mode\":\" OFF \",\"client\":\"  " + CLIENT + " \",\"adtest\":null,\"units\":{\"WIDE_BANNER\":\" 1234567890 \",\"STRIP_BANNER\":\"\","
                + "\"SQUARE_BANNER\":\"   \",\"SMALL_BANNER\":null,\"WING_BANNER\":\"\\t2234567890\\n\",\"POST_INLINE\":\"\","
                + "\"BOARD_LIST\":{\"slot\":\" \",\"layoutKey\":\"\"}}}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adsense.client").value(CLIENT)).andExpect(jsonPath("$.data.adsense.adtest").value(false));
        assertThat(row()).containsEntry("adsense_mode", "OFF").containsEntry("adsense_client", CLIENT).containsEntry("adsense_adtest", false)
                .containsEntry("adsense_wide_banner", "1234567890").containsEntry("adsense_strip_banner", null).containsEntry("adsense_square_banner", null)
                .containsEntry("adsense_small_banner", null).containsEntry("adsense_wing_banner", "2234567890").containsEntry("adsense_post_inline", null)
                .containsEntry("adsense_board_list_slot", null).containsEntry("adsense_board_list_layout_key", null);

        // 인피드 두 칸 공백 자르기
        save(session, body("{\"mode\":\"VERIFY\",\"client\":\"" + CLIENT + "\",\"units\":{\"BOARD_LIST\":{\"slot\":\" 4234567890 \",\"layoutKey\":\" -fb+5w+4e-db+86 \"}}}"))
                .andExpect(status().isOk());
        assertThat(row()).containsEntry("adsense_mode", "VERIFY").containsEntry("adsense_board_list_slot", "4234567890")
                .containsEntry("adsense_board_list_layout_key", "-fb+5w+4e-db+86").containsEntry("adsense_wide_banner", null);
    }
}
