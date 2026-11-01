package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.audit.service.AuditLabels;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 관리자 핫딜 설정 API(3단계) — 계약 「핫딜 설정」 표의 칸 범위·기본값 · 저장(바뀐 칸만 감사 before/after · 사본 무효화 · 같은 값이면 쓰지 않음) · 포인트 정책 읽기 전용 칸 ·
 * 제휴 방식 전환 영향 셈 · 감사 행위 카탈로그(일반 분류·번들 ko/en).
 */
class HotdealAdminSettingsHttpTest extends HotdealAdminTestBase {

    /** 계약 표 — 칸 → {최소, 최대, 기본} */
    private static final Map<String, long[]> TABLE = new LinkedHashMap<>();

    static {
        TABLE.put("endRequestThreshold", new long[] {1, 20, 3});
        TABLE.put("endGraceHours", new long[] {0, 168, 12});
        TABLE.put("endRequestDailyLimit", new long[] {1, 100, 20});
        TABLE.put("endRequestPerAuthorLimit", new long[] {0, 20, 3});
        TABLE.put("endRequestPerAuthorDays", new long[] {1, 30, 7});
        TABLE.put("keepMaxCount", new long[] {0, 10, 2});
        TABLE.put("memberDailyPostLimit", new long[] {0, 50, 5});
        TABLE.put("sameStoreIntervalMinutes", new long[] {0, 1440, 10});
        TABLE.put("autoExpireDays", new long[] {0, 365, 30});
        TABLE.put("priceMaxWon", new long[] {1_000_000, 1_000_000_000, 100_000_000});
        TABLE.put("discountWarnPercent", new long[] {50, 99, 90});
        TABLE.put("hotLikeThreshold", new long[] {1, 100, 10});
        TABLE.put("suspicionMinPosts", new long[] {0, 50, 3});
        TABLE.put("suspicionWindowDays", new long[] {1, 90, 7});
        TABLE.put("storeSuspicionMembers", new long[] {0, 20, 3});
        TABLE.put("storeSuspicionWindowDays", new long[] {1, 90, 30});
        TABLE.put("storeSuspicionOtherRatioPercent", new long[] {0, 100, 20});
    }

    @Autowired
    private MessageSource messages;

    private static ObjectNode defaults() {
        ObjectNode body = JSON.createObjectNode();
        TABLE.forEach((field, spec) -> body.put(field, spec[2]));
        body.put("ogFetchEnabled", true);
        body.put("affiliateMode", "MEMBER");
        return body;
    }

    @Test
    void 기본값은_계약_표와_같고_포인트_정책은_읽기_전용으로_싣는다() throws Exception {
        JsonNode view = data(mvc.perform(get("/api/admin/hotdeal-settings").cookie(login(operatorWith(Permission.HOTDEAL_READ)))).andExpect(status().isOk()).andReturn());
        TABLE.forEach((field, spec) -> assertThat(view.path(field).asLong()).as(field).isEqualTo(spec[2]));
        assertThat(view.path("ogFetchEnabled").asBoolean()).isTrue();
        assertThat(view.path("affiliateMode").asString()).isEqualTo("MEMBER");
        assertThat(view.path("rewardPoints").asInt()).isEqualTo(jdbc.queryForObject("SELECT deal_end_request_points FROM point_settings WHERE id = 1", Integer.class));
        assertThat(view.path("rewardDailyLimit").asInt())
                .isEqualTo(jdbc.queryForObject("SELECT deal_end_request_daily_limit FROM point_settings WHERE id = 1", Integer.class));
        assertThat(view.has("updatedAt")).isTrue();
        assertThat(view.has("updatedBy")).isTrue();
        mvc.perform(get("/api/admin/hotdeal-settings").cookie(login(operatorWith()))).andExpect(status().isForbidden());
    }

    @Test
    void 칸마다_범위_밖이면_그_칸_400_끝값은_받는다() throws Exception {
        Cookie cookie = login(operatorWith(Permission.HOTDEAL_READ, Permission.HOTDEAL_MANAGE));
        for (Map.Entry<String, long[]> e : TABLE.entrySet()) {
            String field = e.getKey();
            for (long bad : new long[] {e.getValue()[0] - 1, e.getValue()[1] + 1}) {
                ObjectNode body = defaults().put(field, bad);
                mvc.perform(withCsrf(put("/api/admin/hotdeal-settings"), cookie).content(body.toString())).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.error.fields." + field).exists());
            }
            ObjectNode missing = defaults();
            missing.remove(field);
            mvc.perform(withCsrf(put("/api/admin/hotdeal-settings"), cookie).content(missing.toString())).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields." + field).exists());
        }
        // 여러 칸이 틀리면 한 번에 모은다 · 방식 · 켬/끔
        ObjectNode many = defaults().put("endRequestThreshold", 0).put("priceMaxWon", 5).put("affiliateMode", "BOTH");
        many.remove("ogFetchEnabled");
        mvc.perform(withCsrf(put("/api/admin/hotdeal-settings"), cookie).content(many.toString())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.endRequestThreshold").exists()).andExpect(jsonPath("$.error.fields.priceMaxWon").exists())
                .andExpect(jsonPath("$.error.fields.affiliateMode").exists()).andExpect(jsonPath("$.error.fields.ogFetchEnabled").exists());
        // 끝값은 받는다
        ObjectNode edges = defaults();
        TABLE.forEach((field, spec) -> edges.put(field, spec[1]));
        mvc.perform(withCsrf(put("/api/admin/hotdeal-settings"), cookie).content(edges.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceMaxWon").value(1_000_000_000L));
        TABLE.forEach((field, spec) -> edges.put(field, spec[0]));
        mvc.perform(withCsrf(put("/api/admin/hotdeal-settings"), cookie).content(edges.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endGraceHours").value(0));
        // 읽기 권한만으로는 저장 못 한다
        mvc.perform(withCsrf(put("/api/admin/hotdeal-settings"), login(operatorWith(Permission.HOTDEAL_READ))).content(defaults().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 저장은_바뀐_칸만_감사하고_사본을_무효화하며_같은_값이면_쓰지_않는다() throws Exception {
        long operator = operatorWith(Permission.HOTDEAL_MANAGE);
        Cookie cookie = login(operator);
        assertThat(dealSettings.current().endRequestThreshold()).isEqualTo(3); // 사본을 먼저 채운다
        ObjectNode body = defaults().put("endRequestThreshold", 5).put("affiliateMode", "SITE");
        JsonNode saved = data(mvc.perform(withCsrf(put("/api/admin/hotdeal-settings"), cookie).content(body.toString())).andExpect(status().isOk()).andReturn());
        assertThat(saved.path("endRequestThreshold").asInt()).isEqualTo(5);
        assertThat(saved.path("affiliateMode").asString()).isEqualTo("SITE");
        assertThat(saved.path("updatedBy").asString()).isEqualTo(operatorName(operator));
        assertThat(dealSettings.current().endRequestThreshold()).isEqualTo(5); // 커밋 뒤 사본이 버려졌다
        assertThat(dealSettings.current().siteMode()).isTrue();

        Map<String, Object> audit = lastAudit(operator, AuditActions.HOTDEAL_SETTINGS_UPDATE);
        JsonNode detail = JSON.readTree((String) audit.get("detail"));
        assertThat(detail.path("before").path("endRequestThreshold").asInt()).isEqualTo(3);
        assertThat(detail.path("after").path("endRequestThreshold").asInt()).isEqualTo(5);
        assertThat(detail.path("before").path("affiliateMode").asString()).isEqualTo("MEMBER");
        assertThat(detail.path("after").path("affiliateMode").asString()).isEqualTo("SITE");
        assertThat(detail.path("after").has("keepMaxCount")).isFalse(); // 바뀐 칸만
        assertThat(audit.get("target_type")).isEqualTo("HOTDEAL_SETTINGS");

        mvc.perform(withCsrf(put("/api/admin/hotdeal-settings"), cookie).content(body.toString())).andExpect(status().isOk());
        assertThat(audits(operator, AuditActions.HOTDEAL_SETTINGS_UPDATE)).isEqualTo(1); // 같은 값이면 감사도 쓰기도 없다
    }

    @Test
    void 제휴_방식_전환_영향은_진행_중_공개_글의_개인_제휴_수와_켠_몰로_가는_수를_센다() throws Exception {
        Cookie cookie = login(operatorWith(Permission.HOTDEAL_READ));
        String store = domain("af");
        jdbc.update("INSERT INTO deal_site_affiliates (id, domain, program_name, transform_type, query, enabled, created_at, updated_at) "
                + "VALUES (?, ?, '시험 제휴', 'QUERY', 'a=1', true, now(), now())", ids.nextId(), store);
        JsonNode before = data(mvc.perform(get("/api/admin/hotdeal-settings/affiliate-impact").param("mode", "SITE").cookie(cookie)).andExpect(status().isOk()).andReturn());

        long board = dealBoard();
        long author = newMember();
        long memberOnStore = deal(board, author, "m." + store);
        jdbc.update("UPDATE post_deals SET affiliate = true WHERE post_id = ?", memberOnStore);
        long memberElsewhere = deal(board, author, "e" + uniq() + ".shop.example");
        jdbc.update("UPDATE post_deals SET affiliate = true WHERE post_id = ?", memberElsewhere);
        long plainViaFinal = deal(board, author, "p" + uniq() + ".shop.example");
        jdbc.update("UPDATE post_deals SET final_host = ? WHERE post_id = ?", store, plainViaFinal); // 최종 호스트가 몰
        long endedOnStore = deal(board, author, store);
        jdbc.update("UPDATE post_deals SET affiliate = true WHERE post_id = ?", endedOnStore);
        endByAuthor(endedOnStore); // 끝난 글은 세지 않는다

        JsonNode after = data(mvc.perform(get("/api/admin/hotdeal-settings/affiliate-impact").param("mode", "SITE").cookie(cookie)).andReturn());
        assertThat(after.path("mode").asString()).isEqualTo("SITE");
        assertThat(after.path("memberAffiliateActive").asLong() - before.path("memberAffiliateActive").asLong()).isEqualTo(2);
        assertThat(after.path("siteStoreMatched").asLong() - before.path("siteStoreMatched").asLong()).isEqualTo(1);
        assertThat(after.path("siteStoreActive").asLong() - before.path("siteStoreActive").asLong()).isEqualTo(2);

        jdbc.update("UPDATE deal_site_affiliates SET enabled = false WHERE domain = ?", store); // 끈 몰은 세지 않는다
        JsonNode off = data(mvc.perform(get("/api/admin/hotdeal-settings/affiliate-impact").param("mode", "MEMBER").cookie(cookie)).andReturn());
        assertThat(off.path("mode").asString()).isEqualTo("MEMBER");
        assertThat(off.path("siteStoreActive").asLong()).isEqualTo(before.path("siteStoreActive").asLong());
        mvc.perform(get("/api/admin/hotdeal-settings/affiliate-impact").param("mode", "X").cookie(cookie)).andExpect(status().isBadRequest());
    }

    @Test
    void 감사_행위는_모두_일반_분류이고_두_언어_이름이_있다() {
        List<String> actions = List.of(AuditActions.HOTDEAL_END, AuditActions.HOTDEAL_REOPEN, AuditActions.HOTDEAL_DOMAIN_CREATE, AuditActions.HOTDEAL_DOMAIN_UPDATE,
                AuditActions.HOTDEAL_DOMAIN_DELETE, AuditActions.HOTDEAL_SUSPICION_DISMISS, AuditActions.HOTDEAL_SETTINGS_UPDATE,
                AuditActions.HOTDEAL_SITE_AFFILIATE_CREATE, AuditActions.HOTDEAL_SITE_AFFILIATE_UPDATE, AuditActions.HOTDEAL_SITE_AFFILIATE_DELETE);
        for (String action : actions) {
            assertThat(AuditActions.all()).contains(action);
            assertThat(AuditRetention.declared(action)).as(action).contains(AuditRetentionClass.GENERAL);
            for (java.util.Locale locale : List.of(java.util.Locale.KOREAN, java.util.Locale.ENGLISH)) {
                assertThat(messages.getMessage("audit.action." + action, null, null, locale)).as(action + " " + locale).isNotBlank();
            }
        }
        for (String type : List.of("HOTDEAL_DOMAIN", "HOTDEAL_SUSPICION", "HOTDEAL_SETTINGS", "HOTDEAL_SITE_AFFILIATE")) {
            assertThat(AuditLabels.TARGET_TYPES).contains(type);
            assertThat(messages.getMessage("audit.targetType." + type, null, null, java.util.Locale.ENGLISH)).isNotBlank();
        }
    }
}
