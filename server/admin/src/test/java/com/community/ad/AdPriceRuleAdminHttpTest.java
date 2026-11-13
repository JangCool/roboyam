package com.community.ad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 상품 가격(요금표 예약 변경) 관리자 API — docs/features/ad-price-rules-contract.md. 읽기 {@code ad.read} · 쓰기 {@code ad.settings}.
 *
 * <ul>
 *   <li>예약 만들기·고치기·취소(한 건·모두) · 여러 상품(단가만 — 기준 지금 단가, 10원 단위 버림, 0원 아래 금지, 비율 −90~300, 전부 아니면 전무) · 받기 바로 적용.</li>
 *   <li>400 칸 오류 키 = 계약 표: {@code effectiveAt}(지남·같은 시각 중복) · {@code unitPrice|capacity|maxDays|maxLeadDays} · {@code products} · {@code adjust.value}. 없는 예약·상품 404.</li>
 *   <li>감사 {@code AD_PRICE_SCHEDULE_CREATE|UPDATE|CANCEL}·{@code AD_PRICE_ACCEPTING_UPDATE}(돈 관련 보관) + 변경 이력(설정 이력 모양, 칸 코드 {@code schedules.<상품>.<칸>}·
 *       {@code rules.<상품>.<칸>}) · 상품 카드의 최근 변경 3건 · 앞날 겹침 분포.</li>
 * </ul>
 * 요금표 줄·예약·이력은 이 클래스만 쓰는 표가 아니라 시험마다 처음 값으로 되돌린다.
 */
class AdPriceRuleAdminHttpTest extends Commerce04AdminTestBase {

    private static final String URL = "/api/admin/ads/price-rules";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private List<Map<String, Object>> snapshot;
    private final List<Long> apps = new ArrayList<>();

    @BeforeEach
    void keep() {
        snapshot = jdbc.queryForList("SELECT product, unit_price, capacity, max_days, max_lead_days, accepting, updated_at, updated_by FROM biz_price_rules");
        jdbc.update("DELETE FROM biz_price_schedules");
        jdbc.update("DELETE FROM biz_price_rule_history");
    }

    @AfterEach
    void restore() {
        jdbc.update("DELETE FROM biz_price_schedules");
        jdbc.update("DELETE FROM biz_price_rule_history");
        for (Map<String, Object> r : snapshot) {
            jdbc.update("UPDATE biz_price_rules SET unit_price = ?, capacity = ?, max_days = ?, max_lead_days = ?, accepting = ?, updated_at = ?, updated_by = ? WHERE product = ?",
                    r.get("unit_price"), r.get("capacity"), r.get("max_days"), r.get("max_lead_days"), r.get("accepting"), r.get("updated_at"), r.get("updated_by"),
                    r.get("product"));
        }
        for (long id : apps) {
            jdbc.update("DELETE FROM biz_application_items WHERE application_id = ?", id);
            jdbc.update("DELETE FROM biz_applications WHERE id = ?", id);
        }
    }

    private Cookie writer() throws Exception {
        return login(operatorWith(Permission.AD_READ, Permission.AD_SETTINGS));
    }

    private static String at(long hours) {
        return Instant.now().plus(hours, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static String body(String effectiveAt, Object unitPrice, Object capacity, Object maxDays, Object maxLeadDays) {
        return "{\"effectiveAt\":" + (effectiveAt == null ? "null" : "\"" + effectiveAt + "\"") + ",\"unitPrice\":" + unitPrice + ",\"capacity\":" + capacity
                + ",\"maxDays\":" + maxDays + ",\"maxLeadDays\":" + maxLeadDays + "}";
    }

    private ResultActions create(Cookie session, String product, String body) throws Exception {
        return mvc.perform(withCsrf(post(URL + "/" + product + "/schedules"), session).content(body));
    }

    private static JsonNode json(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static JsonNode rule(JsonNode view, String product) {
        for (JsonNode r : view.path("data").path("rules")) {
            if (r.path("product").asString().equals(product)) {
                return r;
            }
        }
        throw new AssertionError("상품이 없다: " + product);
    }

    private long schedules() {
        return jdbc.queryForObject("SELECT count(*) FROM biz_price_schedules", Long.class);
    }

    private long historyRows() {
        return jdbc.queryForObject("SELECT count(*) FROM biz_price_rule_history", Long.class);
    }

    // ── 권한 ───────────────────────────────────────────────────────────────

    @Test
    void 권한_읽기는_ad_read_쓰기는_ad_settings_ad_read_만으로_쓰면_403() throws Exception {
        Cookie none = login(operatorWith());
        Cookie reader = login(operatorWith(Permission.AD_READ));
        Cookie settingsOnly = login(operatorWith(Permission.AD_SETTINGS));
        mvc.perform(get(URL).cookie(none)).andExpect(status().isForbidden());
        mvc.perform(get(URL).cookie(settingsOnly)).andExpect(status().isForbidden());
        mvc.perform(get(URL + "/history").cookie(none)).andExpect(status().isForbidden());
        mvc.perform(get(URL).cookie(reader)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.rules.length()").value(9))
                .andExpect(jsonPath("$.data.rules[0].product").value("WIDE_BANNER"))
                .andExpect(jsonPath("$.data.rules[6].product").value("HOTDEAL_POST")).andExpect(jsonPath("$.data.rules[6].unit").value("POST"))
                .andExpect(jsonPath("$.data.rules[6].capacity").doesNotExist()).andExpect(jsonPath("$.data.rules[6].occupancy.length()").value(0))
                .andExpect(jsonPath("$.data.rules[0].unit").value("DAY")).andExpect(jsonPath("$.data.rules[0].schedules.length()").value(0))
                .andExpect(jsonPath("$.data.rules[0].recentChanges.length()").value(0));
        mvc.perform(get(URL + "/history").cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));

        String ok = body(at(24), 45000, 2, 14, 60);
        create(reader, "WIDE_BANNER", ok).andExpect(status().isForbidden());
        mvc.perform(withCsrf(put(URL + "/WIDE_BANNER/schedules/1"), reader).content(ok)).andExpect(status().isForbidden());
        mvc.perform(withCsrf(delete(URL + "/WIDE_BANNER/schedules/1"), reader)).andExpect(status().isForbidden());
        mvc.perform(withCsrf(delete(URL + "/schedules"), reader)).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post(URL + "/schedules/bulk"), reader)
                .content("{\"effectiveAt\":\"" + at(24) + "\",\"products\":[\"WIDE_BANNER\"],\"adjust\":{\"kind\":\"PERCENT\",\"value\":10}}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(patch(URL + "/WIDE_BANNER/accepting"), reader).content("{\"accepting\":false}")).andExpect(status().isForbidden());
        assertThat(schedules()).isZero();
    }

    // ── 예약 한 건 ─────────────────────────────────────────────────────────

    @Test
    void 예약_만들기_고치기_취소_감사와_이력() throws Exception {
        long me = operatorWith(Permission.AD_READ, Permission.AD_SETTINGS);
        Cookie session = login(me);
        String first = at(24);
        JsonNode view = json(create(session, "WIDE_BANNER", body(first, 45000, 2, 14, 60)).andExpect(status().isOk()));
        JsonNode wide = rule(view, "WIDE_BANNER");
        assertThat(wide.path("unitPrice").asLong()).isEqualTo(50_000); // 지금 값은 그대로
        assertThat(wide.path("schedules")).hasSize(1);
        JsonNode s = wide.path("schedules").get(0);
        String id = s.path("id").asString();
        assertThat(Instant.parse(s.path("effectiveAt").asString())).isEqualTo(Instant.parse(first));
        assertThat(s.path("unitPrice").asLong()).isEqualTo(45_000);
        assertThat(s.path("createdBy").path("id").asString()).isEqualTo(Long.toString(me));
        assertThat(s.path("createdBy").path("name").asString()).isEqualTo(operatorName(me));
        assertThat(s.path("updatedAt").isNull()).isTrue();

        // 같은 시각을 하나 더 → 400 fields.effectiveAt
        create(session, "WIDE_BANNER", body(first, 40000, 2, 14, 60)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.effectiveAt").exists());
        // 다른 상품에는 같은 시각이 된다
        create(session, "STRIP_BANNER", body(first, 25000, 3, 14, 60)).andExpect(status().isOk());

        // 이력 — 만들기 한 줄: 적용 시각 + 직전 값과 다른 칸(단가)만
        mvc.perform(get(URL + "/history").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[1].action").value("CREATE")).andExpect(jsonPath("$.data.items[1].source").value("ADMIN"))
                .andExpect(jsonPath("$.data.items[1].operatorId").value(Long.toString(me)))
                .andExpect(jsonPath("$.data.items[1].changes.length()").value(2))
                .andExpect(jsonPath("$.data.items[1].changes[0].field").value("schedules.WIDE_BANNER.effectiveAt"))
                .andExpect(jsonPath("$.data.items[1].changes[0].before").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].changes[1].field").value("schedules.WIDE_BANNER.unitPrice"))
                .andExpect(jsonPath("$.data.items[1].changes[1].labelKey").value("settingHistory.field.schedules.WIDE_BANNER.unitPrice"))
                .andExpect(jsonPath("$.data.items[1].changes[1].before").value("50000")).andExpect(jsonPath("$.data.items[1].changes[1].after").value("45000"));
        String created = auditLogs.where().actor(me).action("AD_PRICE_SCHEDULE_CREATE").latest().orElseThrow().detail();
        assertThat(created).contains("\"product\":\"STRIP_BANNER\"");
        assertThat(auditLogs.where().actor(me).action("AD_PRICE_SCHEDULE_CREATE").count()).isEqualTo(2);

        // 고치기 — 적용 시각·단가·최대 일수
        String moved = at(48);
        view = json(mvc.perform(withCsrf(put(URL + "/WIDE_BANNER/schedules/" + id), session).content(body(moved, 42000, 2, 10, 60))).andExpect(status().isOk()));
        s = rule(view, "WIDE_BANNER").path("schedules").get(0);
        assertThat(Instant.parse(s.path("effectiveAt").asString())).isEqualTo(Instant.parse(moved));
        assertThat(s.path("unitPrice").asLong()).isEqualTo(42_000);
        assertThat(s.path("maxDays").asInt()).isEqualTo(10);
        assertThat(s.path("updatedAt").isNull()).isFalse();
        String updated = auditLogs.where().actor(me).action("AD_PRICE_SCHEDULE_UPDATE").single().detail();
        assertThat(updated).contains("\"unitPrice\":45000").contains("\"unitPrice\":42000");
        mvc.perform(get(URL + "/history").cookie(session)).andExpect(jsonPath("$.data.items[0].action").value("UPDATE"))
                .andExpect(jsonPath("$.data.items[0].changes.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].changes[0].field").value("schedules.WIDE_BANNER.effectiveAt"))
                .andExpect(jsonPath("$.data.items[0].changes[1].field").value("schedules.WIDE_BANNER.unitPrice"))
                .andExpect(jsonPath("$.data.items[0].changes[2].field").value("schedules.WIDE_BANNER.maxDays"));
        // 같은 값으로 다시 고치면 기록 없음
        long rows = historyRows();
        mvc.perform(withCsrf(put(URL + "/WIDE_BANNER/schedules/" + id), session).content(body(moved, 42000, 2, 10, 60))).andExpect(status().isOk());
        assertThat(historyRows()).isEqualTo(rows);

        // 다른 상품 경로 · 없는 ID · 모르는 상품 → 404
        mvc.perform(withCsrf(put(URL + "/STRIP_BANNER/schedules/" + id), session).content(body(moved, 1, 1, 1, 1))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("AD_PRICE_SCHEDULE_NOT_FOUND"));
        mvc.perform(withCsrf(delete(URL + "/WIDE_BANNER/schedules/123"), session)).andExpect(status().isNotFound());
        create(session, "NO_SUCH", body(at(24), 1, 1, 1, 1)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("AD_PRICE_PRODUCT_NOT_FOUND"));

        // 취소 한 건
        view = json(mvc.perform(withCsrf(delete(URL + "/WIDE_BANNER/schedules/" + id), session)).andExpect(status().isOk()));
        assertThat(rule(view, "WIDE_BANNER").path("schedules")).isEmpty();
        assertThat(auditLogs.where().actor(me).action("AD_PRICE_SCHEDULE_CANCEL").single().targetId()).isEqualTo("WIDE_BANNER");
        mvc.perform(get(URL + "/history").cookie(session)).andExpect(jsonPath("$.data.items[0].action").value("DELETE"))
                .andExpect(jsonPath("$.data.items[0].changes[0].field").value("schedules.WIDE_BANNER.effectiveAt"))
                .andExpect(jsonPath("$.data.items[0].changes[0].after").doesNotExist());

        // 모두 취소 — 남은 한 건(띠 배너), 감사 한 건 all
        create(session, "TEXT_LINE", body(at(30), 6000, 5, 30, 60)).andExpect(status().isOk());
        view = json(mvc.perform(withCsrf(delete(URL + "/schedules"), session)).andExpect(status().isOk()));
        assertThat(schedules()).isZero();
        assertThat(auditLogs.where().actor(me).action("AD_PRICE_SCHEDULE_CANCEL").latest().orElseThrow().targetId()).isEqualTo("all");
        assertThat(auditLogs.where().actor(me).action("AD_PRICE_SCHEDULE_CANCEL").count()).isEqualTo(2);
        // 걸린 예약이 없으면 모두 취소도 기록 없음
        rows = historyRows();
        mvc.perform(withCsrf(delete(URL + "/schedules"), session)).andExpect(status().isOk());
        assertThat(historyRows()).isEqualTo(rows);
        assertThat(AuditRetention.declared(AuditActions.AD_PRICE_SCHEDULE_CREATE)).contains(AuditRetentionClass.MONEY);
        assertThat(AuditRetention.declared(AuditActions.AD_PRICE_ACCEPTING_UPDATE)).contains(AuditRetentionClass.MONEY);
    }

    @Test
    void 칸_검사_400_지난_시각_범위_밖_건당_상품_한도() throws Exception {
        Cookie session = writer();
        String past = Instant.now().minusSeconds(60).toString();
        create(session, "WIDE_BANNER", body(past, 45000, 2, 14, 60)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.effectiveAt").exists());
        create(session, "WIDE_BANNER", body(null, 45000, 2, 14, 60)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.effectiveAt").exists());
        create(session, "WIDE_BANNER", body(at(24), -1, 0, 0, 0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.unitPrice").exists()).andExpect(jsonPath("$.error.fields.capacity").exists())
                .andExpect(jsonPath("$.error.fields.maxDays").exists()).andExpect(jsonPath("$.error.fields.maxLeadDays").exists())
                .andExpect(jsonPath("$.error.fields.effectiveAt").doesNotExist());
        create(session, "WIDE_BANNER", body(at(24), 100.5, null, 14, 60)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.unitPrice").exists()).andExpect(jsonPath("$.error.fields.capacity").exists());
        // 건당(핫딜 글) — 한도는 null 만, 0원(무료)은 된다
        create(session, "HOTDEAL_POST", body(at(24), 30000, 1, 30, 60)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.capacity").exists());
        JsonNode view = json(create(session, "HOTDEAL_POST", body(at(24), 0, null, 30, 60)).andExpect(status().isOk()));
        assertThat(rule(view, "HOTDEAL_POST").path("schedules").get(0).path("unitPrice").asLong()).isZero();
        assertThat(rule(view, "HOTDEAL_POST").path("schedules").get(0).path("capacity").isNull()).isTrue();
        assertThat(schedules()).isOne();
    }

    // ── 여러 상품 ──────────────────────────────────────────────────────────

    @Test
    void 여러_상품_단가만_지금_단가_기준_10원_버림_나머지_칸은_직전_값() throws Exception {
        long me = operatorWith(Permission.AD_READ, Permission.AD_SETTINGS);
        Cookie session = login(me);
        String when = at(72);
        // 한 줄 광고에 앞선 예약(최대 일수 14) — 일괄 예약의 나머지 칸은 그 시각 직전 값(최대 일수 14)
        create(session, "TEXT_LINE", body(at(24), 5500, 5, 14, 60)).andExpect(status().isOk());
        JsonNode view = json(mvc.perform(withCsrf(post(URL + "/schedules/bulk"), session)
                .content("{\"effectiveAt\":\"" + when + "\",\"products\":[\"TEXT_LINE\",\"WIDE_BANNER\",\"TEXT_LINE\"],\"adjust\":{\"kind\":\"AMOUNT\",\"value\":1234}}"))
                .andExpect(status().isOk()));
        JsonNode line = rule(view, "TEXT_LINE").path("schedules");
        assertThat(line).hasSize(2);
        assertThat(line.get(1).path("unitPrice").asLong()).isEqualTo(6_230); // 지금 5,000 + 1,234 = 6,234 → 버림(앞선 예약 5,500 이 아니라 지금 단가 기준)
        assertThat(line.get(1).path("maxDays").asInt()).isEqualTo(14);      // 직전 값
        assertThat(rule(view, "WIDE_BANNER").path("schedules").get(0).path("unitPrice").asLong()).isEqualTo(51_230);
        assertThat(rule(view, "WIDE_BANNER").path("schedules").get(0).path("maxDays").asInt()).isEqualTo(14);
        assertThat(auditLogs.where().actor(me).action("AD_PRICE_SCHEDULE_CREATE").latest().orElseThrow().targetId()).isEqualTo("bulk");
        // 이력은 한 줄(두 상품)
        mvc.perform(get(URL + "/history").cookie(session)).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].changes.length()").value(4));

        // 비율 −15% — 50,000 → 42,500 · 30,000 → 25,500
        view = json(mvc.perform(withCsrf(post(URL + "/schedules/bulk"), session)
                .content("{\"effectiveAt\":\"" + at(96) + "\",\"products\":[\"STRIP_BANNER\",\"SQUARE_BANNER\"],\"adjust\":{\"kind\":\"PERCENT\",\"value\":-15}}"))
                .andExpect(status().isOk()));
        assertThat(rule(view, "STRIP_BANNER").path("schedules").get(0).path("unitPrice").asLong()).isEqualTo(25_500);
        assertThat(rule(view, "SQUARE_BANNER").path("schedules").get(0).path("unitPrice").asLong()).isEqualTo(17_000);
    }

    @Test
    void 여러_상품은_하나라도_안_되면_전부_안_된다() throws Exception {
        Cookie session = writer();
        String when = at(72);
        create(session, "SMALL_BANNER", body(when, 9000, 6, 30, 60)).andExpect(status().isOk());
        long before = schedules();
        String bulk = URL + "/schedules/bulk";
        // 0원 아래(한 줄 광고 5,000 − 6,000) — 넓은 배너는 되지만 아무것도 저장하지 않는다
        mvc.perform(withCsrf(post(bulk), session).content("{\"effectiveAt\":\"" + at(80) + "\",\"products\":[\"WIDE_BANNER\",\"TEXT_LINE\"],\"adjust\":{\"kind\":\"AMOUNT\",\"value\":-6000}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.['adjust.value']").exists());
        // 같은 시각 예약이 이미 있는 상품이 하나라도 있으면 400 fields.effectiveAt
        mvc.perform(withCsrf(post(bulk), session).content("{\"effectiveAt\":\"" + when + "\",\"products\":[\"WIDE_BANNER\",\"SMALL_BANNER\"],\"adjust\":{\"kind\":\"PERCENT\",\"value\":10}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.effectiveAt").exists());
        // 비율 범위 −90~300 · 0 · 빈 상품 · 모르는 상품
        mvc.perform(withCsrf(post(bulk), session).content("{\"effectiveAt\":\"" + at(80) + "\",\"products\":[\"WIDE_BANNER\"],\"adjust\":{\"kind\":\"PERCENT\",\"value\":301}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.['adjust.value']").exists());
        mvc.perform(withCsrf(post(bulk), session).content("{\"effectiveAt\":\"" + at(80) + "\",\"products\":[\"WIDE_BANNER\"],\"adjust\":{\"kind\":\"PERCENT\",\"value\":-91}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.['adjust.value']").exists());
        mvc.perform(withCsrf(post(bulk), session).content("{\"effectiveAt\":\"" + at(80) + "\",\"products\":[\"WIDE_BANNER\"],\"adjust\":{\"kind\":\"AMOUNT\",\"value\":0}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.['adjust.value']").exists());
        mvc.perform(withCsrf(post(bulk), session).content("{\"effectiveAt\":\"" + at(80) + "\",\"products\":[],\"adjust\":{\"kind\":\"AMOUNT\",\"value\":100}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.products").exists());
        mvc.perform(withCsrf(post(bulk), session).content("{\"effectiveAt\":\"" + at(80) + "\",\"products\":[\"NOPE\"],\"adjust\":{\"kind\":\"AMOUNT\",\"value\":100}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.products").exists());
        mvc.perform(withCsrf(post(bulk), session).content("{\"effectiveAt\":\"" + Instant.now().minusSeconds(5) + "\",\"products\":[\"WIDE_BANNER\"],\"adjust\":{\"kind\":\"AMOUNT\",\"value\":100}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.effectiveAt").exists());
        assertThat(schedules()).isEqualTo(before);
        // −90% 는 된다(5,000 → 500)
        JsonNode view = json(mvc.perform(withCsrf(post(bulk), session)
                .content("{\"effectiveAt\":\"" + at(80) + "\",\"products\":[\"TEXT_LINE\"],\"adjust\":{\"kind\":\"PERCENT\",\"value\":-90}}")).andExpect(status().isOk()));
        assertThat(rule(view, "TEXT_LINE").path("schedules").get(0).path("unitPrice").asLong()).isEqualTo(500);
    }

    // ── 받기 · 최근 변경 · 겹침 ────────────────────────────────────────────

    @Test
    void 신청_받기는_바로_바뀌고_최근_변경과_이력에_남는다() throws Exception {
        long me = operatorWith(Permission.AD_READ, Permission.AD_SETTINGS);
        Cookie session = login(me);
        JsonNode view = json(mvc.perform(withCsrf(patch(URL + "/WING_BANNER/accepting"), session).content("{\"accepting\":false}")).andExpect(status().isOk()));
        JsonNode wing = rule(view, "WING_BANNER");
        assertThat(wing.path("accepting").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT accepting FROM biz_price_rules WHERE product = 'WING_BANNER'", Boolean.class)).isFalse();
        assertThat(wing.path("recentChanges")).hasSize(1);
        JsonNode recent = wing.path("recentChanges").get(0);
        assertThat(recent.has("system")).isFalse();
        assertThat(recent.path("operatorName").asString()).isEqualTo(operatorName(me));
        assertThat(recent.path("changes").get(0).path("field").asString()).isEqualTo("accepting");
        assertThat(recent.path("changes").get(0).path("before").asString()).isEqualTo("true");
        assertThat(recent.path("changes").get(0).path("after").asString()).isEqualTo("false");
        assertThat(view.path("data").path("updatedBy").path("id").asString()).isEqualTo(Long.toString(me));
        assertThat(view.path("data").path("updatedAt").isNull()).isFalse();
        assertThat(auditLogs.where().actor(me).action("AD_PRICE_ACCEPTING_UPDATE").single().targetType()).isEqualTo("AD_PRICE_RULES");
        // 같은 값 — 기록 없음 · 빈 본문 400
        mvc.perform(withCsrf(patch(URL + "/WING_BANNER/accepting"), session).content("{\"accepting\":false}")).andExpect(status().isOk());
        assertThat(auditLogs.where().actor(me).action("AD_PRICE_ACCEPTING_UPDATE").count()).isOne();
        mvc.perform(withCsrf(patch(URL + "/WING_BANNER/accepting"), session).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.accepting").exists());

        // 최근 변경은 3건까지(최신 먼저) — 받기 이력과 적용 시각이 지난 기간표 줄(배치 없이)을 합친다
        mvc.perform(withCsrf(patch(URL + "/WING_BANNER/accepting"), session).content("{\"accepting\":true}")).andExpect(status().isOk());
        mvc.perform(withCsrf(patch(URL + "/WING_BANNER/accepting"), session).content("{\"accepting\":false}")).andExpect(status().isOk());
        long price = jdbc.queryForObject("SELECT unit_price FROM biz_price_rules WHERE product = 'WING_BANNER'", Long.class);
        Instant applied = Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MILLIS);
        jdbc.update("INSERT INTO biz_price_schedules (id, product, effective_at, unit_price, capacity, max_days, max_lead_days, created_by, created_at) "
                + "SELECT ?, product, ?, unit_price + 2000, capacity, max_days, max_lead_days, ?, now() FROM biz_price_rules WHERE product = 'WING_BANNER'",
                ids.nextId(), Timestamp.from(applied), me);
        Thread.sleep(1_100); // 적용 시각이 지나게
        view = json(mvc.perform(get(URL).cookie(session)).andExpect(status().isOk()));
        wing = rule(view, "WING_BANNER");
        assertThat(wing.path("unitPrice").asLong()).isEqualTo(price + 2000);
        assertThat(wing.path("recentChanges")).hasSize(3);
        JsonNode first = wing.path("recentChanges").get(0);
        assertThat(Instant.parse(first.path("changedAt").asString())).isEqualTo(applied);
        assertThat(first.path("operatorName").asString()).isEqualTo(operatorName(me));
        assertThat(first.path("changes")).hasSize(1); // 바뀐 칸만
        assertThat(first.path("changes").get(0).path("field").asString()).isEqualTo("unitPrice");
        assertThat(first.path("changes").get(0).path("before").asString()).isEqualTo(Long.toString(price));
        assertThat(first.path("changes").get(0).path("after").asString()).isEqualTo(Long.toString(price + 2000));
        assertThat(wing.path("recentChanges").get(1).path("changes").get(0).path("field").asString()).isEqualTo("accepting");
        assertThat(Instant.parse(view.path("data").path("updatedAt").asString())).isEqualTo(applied);
        assertThat(view.path("data").path("updatedBy").path("id").asString()).isEqualTo(Long.toString(me));
        // 적용은 이력 줄을 만들지 않는다(관리자가 한 일만) · 기간표 줄은 지워지지 않는다
        mvc.perform(get(URL + "/history").cookie(session)).andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.items[0].source").value("ADMIN"));
        assertThat(schedules()).isOne();
    }

    @Test
    void 적용_시각이_지난_예약은_지금_값으로_보이고_고칠_수_없다() throws Exception {
        Cookie session = writer();
        // 지난 줄 둘 — 가장 늦은 줄이 지금 값(앞 줄 값은 덮인다) · 직전 값과 같은 줄은 최근 변경에 없다
        long older = ids.nextId();
        long id = ids.nextId();
        long same = ids.nextId();
        jdbc.update("INSERT INTO biz_price_schedules (id, product, effective_at, unit_price, capacity, max_days, max_lead_days, created_by, created_at) "
                + "VALUES (?, 'SQUARE_BANNER', ?, 99000, 9, 9, 9, 1, now())", older, Timestamp.from(Instant.now().minusSeconds(180)));
        jdbc.update("INSERT INTO biz_price_schedules (id, product, effective_at, unit_price, capacity, max_days, max_lead_days, created_by, created_at) "
                + "VALUES (?, 'SQUARE_BANNER', ?, 23000, 3, 20, 45, 1, now())", id, Timestamp.from(Instant.now().minusSeconds(120)));
        jdbc.update("INSERT INTO biz_price_schedules (id, product, effective_at, unit_price, capacity, max_days, max_lead_days, created_by, created_at) "
                + "VALUES (?, 'SQUARE_BANNER', ?, 23000, 3, 20, 45, 1, now())", same, Timestamp.from(Instant.now().minusSeconds(60)));
        JsonNode square = rule(json(mvc.perform(get(URL).cookie(session)).andExpect(status().isOk())), "SQUARE_BANNER");
        assertThat(square.path("unitPrice").asLong()).isEqualTo(23_000);
        assertThat(square.path("capacity").asInt()).isEqualTo(3);
        assertThat(square.path("maxDays").asInt()).isEqualTo(20);
        assertThat(square.path("maxLeadDays").asInt()).isEqualTo(45);
        assertThat(square.path("schedules")).isEmpty();
        assertThat(square.path("recentChanges")).hasSize(2); // older · id (same 은 바뀐 칸이 없다)
        assertThat(square.path("recentChanges").get(0).path("id").asString()).isEqualTo(Long.toString(id));
        assertThat(square.path("recentChanges").get(0).path("changes").get(0).path("before").asString()).isEqualTo("99000"); // 직전 = 앞 줄 값
        for (long gone : List.of(id, same)) {
            mvc.perform(withCsrf(put(URL + "/SQUARE_BANNER/schedules/" + gone), session).content(body(at(24), 1, 1, 1, 1))).andExpect(status().isNotFound());
            mvc.perform(withCsrf(delete(URL + "/SQUARE_BANNER/schedules/" + gone), session)).andExpect(status().isNotFound());
        }
        mvc.perform(withCsrf(delete(URL + "/schedules"), session)).andExpect(status().isOk()); // 모두 취소도 지난 줄은 건드리지 않는다
        assertThat(schedules()).isEqualTo(3);
        // 여러 상품의 기준은 지금 값(23,000) — +10% = 25,300
        JsonNode view = json(mvc.perform(withCsrf(post(URL + "/schedules/bulk"), session)
                .content("{\"effectiveAt\":\"" + at(24) + "\",\"products\":[\"SQUARE_BANNER\"],\"adjust\":{\"kind\":\"PERCENT\",\"value\":10}}")).andExpect(status().isOk()));
        JsonNode s = rule(view, "SQUARE_BANNER").path("schedules").get(0);
        assertThat(s.path("unitPrice").asLong()).isEqualTo(25_300);
        assertThat(s.path("maxDays").asInt()).isEqualTo(20);
    }

    @Test
    void 겹침_분포는_오늘부터_시작일_선택_범위_안_날짜별_최대_동시_겹침() throws Exception {
        Cookie session = writer();
        LocalDate today = LocalDate.now(KST);
        LocalDate d1 = today.plusDays(50);
        LocalDate d2 = today.plusDays(51);
        Instant s1 = d1.atStartOfDay(KST).toInstant();
        Instant s2 = d2.atStartOfDay(KST).toInstant();
        Instant e2 = d2.plusDays(1).atStartOfDay(KST).toInstant();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_application_items WHERE product = 'WING_BANNER' AND start_at < ? AND end_at > ?", Integer.class,
                Timestamp.from(e2), Timestamp.from(s1))).isZero();
        Map<Integer, Integer> before = occupancy(rule(json(mvc.perform(get(URL).cookie(session))), "WING_BANNER"));

        booking("WING_BANNER", s1, e2); // 50·51일째
        booking("WING_BANNER", s2, e2); // 51일째

        Map<Integer, Integer> after = occupancy(rule(json(mvc.perform(get(URL).cookie(session))), "WING_BANNER"));
        Map<Integer, Integer> expected = new HashMap<>(before);
        expected.merge(1, 1, Integer::sum);
        expected.merge(2, 1, Integer::sum);
        assertThat(after).isEqualTo(expected);
    }

    private static Map<Integer, Integer> occupancy(JsonNode rule) {
        Map<Integer, Integer> out = new HashMap<>();
        int last = Integer.MAX_VALUE;
        for (JsonNode o : rule.path("occupancy")) {
            assertThat(o.path("peak").asInt()).isLessThan(last).isPositive(); // 겹침 큰 차례 · 0 은 없다
            last = o.path("peak").asInt();
            out.put(o.path("peak").asInt(), o.path("days").asInt());
        }
        return out;
    }

    /** 자리를 차지하는 운영자 등록 건(입금 대기) 한 줄 + 자리 줄 */
    private void booking(String product, Instant start, Instant end) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO biz_applications (id, application_no, kind, status, source, advertiser_name, title, start_at, end_at, preferred_method, payment_method, "
                        + "quote_supply, quote_vat, quote_total, approved_supply, approved_vat, approved_total, approved_at, deposit_deadline_at, stop_fee_rate, created_by, "
                        + "created_at, updated_at) VALUES (?, ?, 'AD_BANNER', 'AWAITING_DEPOSIT', 'OPERATOR', '겹침시험', '겹침 시험', ?, ?, 'MANUAL', 'MANUAL', "
                        + "1000, 100, 1100, 1000, 100, 1100, now(), now() + interval '3 days', 0, 1, now(), now())",
                id, "PR-" + (id % 1_000_000_000_000L), Timestamp.from(start), Timestamp.from(end));
        long units = (end.getEpochSecond() - start.getEpochSecond()) / 86_400;
        jdbc.update("INSERT INTO biz_application_items (id, application_id, product, unit_price, units, supply, start_at, end_at, created_at) VALUES (?, ?, ?, 1000, ?, ?, ?, ?, now())",
                ids.nextId(), id, product, units, 1000 * units, Timestamp.from(start), Timestamp.from(end));
        apps.add(id);
    }
}
