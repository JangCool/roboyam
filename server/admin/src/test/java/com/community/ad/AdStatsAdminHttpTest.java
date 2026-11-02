package com.community.ad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.menu.dto.AdminMenuNode;
import com.community.menu.service.AdminScreenRegistry;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 운영자 광고 성과 API(실제 관리자 앱 문맥 + PostgreSQL, ad-performance-contract 규칙 9~12) — 권한 {@code ad.read}(없으면 403) · 400 칸({@code fields.from}·{@code fields.to}·
 * {@code fields.type}·{@code fields.advertiserId}·정렬·쪽) · 합계(노출된 건 수)·날짜별 0 채우기·유형별·자리별·광고주 상위 · 유형·광고주({@code adv-}·{@code biz-}) 거르기 ·
 * 건별(결제 뒤 상태만 · 시작한 건만 · 정렬 노출·클릭·클릭률 — 노출 0 은 뒤 · 쪽) · 한 건(장부 유형 줄이 없으면 404) · {@code /stats} 와 {@code /{id}} 경로 · no-store · 메뉴.
 * 시험 건은 표에 직접 넣는다(먼 과거 날짜 — 다른 시험의 집계와 겹치지 않는다).
 */
class AdStatsAdminHttpTest extends Commerce04AdminTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private JsonNode data(ResultActions result) throws Exception {
        return MAPPER.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private static Instant kst(LocalDate day) {
        return day.atStartOfDay(KST).toInstant();
    }

    private String createAdvertiser(Cookie op) throws Exception {
        return data(mvc.perform(withCsrf(post("/api/admin/ads/advertisers"), op).content("{\"name\":\"성과광고주" + ids.nextId() + "\"}"))
                .andExpect(status().isOk())).get("id").asString();
    }

    /** 승인된 사업자 신원 한 줄(광고주 연결 없음) — 신원 ID */
    private long businessProfile(String company) {
        long profile = ids.nextId();
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, re_review, company_name, biz_no_enc, biz_no_hash, biz_no_last4, owner_name_enc, phone_enc, email_enc, "
                        + "postcode, address1_enc, terms_version, privacy_version, agreed_at, submitted_at, created_at, updated_at) "
                        + "VALUES (?, ?, 'APPROVED', false, ?, 'x', ?, '6789', 'x', 'x', 'x', '06236', 'x', 'v1', 'v1', now(), now(), now(), now())",
                profile, newMember(), company, String.format("%064x", profile));
        return profile;
    }

    /** 장부 건 한 줄(운영자 등록 · 수동 결제 확정 모양) + 상품 줄 + 승인된 소재 1 — {@code status} SUBMITTED 면 승인 칸 없음 */
    private long booking(String kind, List<String> products, String status, String cause, Instant start, Instant end, Long advertiserId, Long profileId,
                         long operatorId) {
        long id = ids.nextId();
        boolean approved = !status.equals("SUBMITTED");
        Instant takenDown = cause == null ? null : start.plus(Duration.ofDays(4));
        jdbc.update("INSERT INTO biz_applications (id, application_no, kind, status, source, business_profile_id, advertiser_id, advertiser_name, company_name, title, "
                        + "start_at, end_at, preferred_method, payment_method, quote_supply, quote_vat, quote_total, approved_supply, approved_vat, approved_total, approved_at, "
                        + "approved_by, deposit_deadline_at, active_contract_count, deposit_confirmed_at, taken_down_at, takedown_cause, closed_at, ad_policy_version, "
                        + "refund_policy_version, agreed_at, stop_fee_rate, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'OPERATOR', ?, ?, '성과광고주', ?, '성과 시험', ?, ?, 'MANUAL', ?, 1000, 100, 1100, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'v1', 'v1', "
                        + "now(), 0, ?, now(), now())",
                id, "AS-" + (id % 1_000_000_000_000L), kind, status, profileId, advertiserId, profileId == null ? null : "성과상사", Timestamp.from(start), Timestamp.from(end),
                approved ? "MANUAL" : null, approved ? 1000 : null, approved ? 100 : null, approved ? 1100 : null, approved ? Timestamp.from(start.minus(Duration.ofDays(3))) : null,
                approved ? operatorId : null, approved ? Timestamp.from(start.minus(Duration.ofDays(1))) : null, approved ? 1 : 0,
                approved ? Timestamp.from(start.minus(Duration.ofDays(2))) : null, takenDown == null ? null : Timestamp.from(takenDown), cause,
                status.equals("CANCELED") ? Timestamp.from(start.plus(Duration.ofDays(1))) : null, operatorId);
        for (String product : products) {
            jdbc.update("INSERT INTO biz_application_items (id, application_id, product, unit_price, units, supply, start_at, end_at, created_at) "
                    + "VALUES (?, ?, ?, 1000, 1, 1000, ?, ?, now())", ids.nextId(), id, product, Timestamp.from(start), Timestamp.from(end));
        }
        jdbc.update("INSERT INTO biz_creative_versions (id, application_id, version, content, created_by_type, created_by, created_at, approved_at) "
                + "VALUES (?, ?, 1, '{}'::jsonb, 'OPERATOR', ?, now(), ?)", ids.nextId(), id, operatorId, approved ? Timestamp.from(start.minus(Duration.ofDays(3))) : null);
        if (cause != null) {
            jdbc.update("INSERT INTO biz_application_events (id, application_id, action, actor_type, actor_id, note, created_at) VALUES (?, ?, 'TAKEN_DOWN', 'OPERATOR', ?, ?, ?)",
                    ids.nextId(), id, operatorId, cause, Timestamp.from(takenDown));
        }
        return id;
    }

    private void stat(long id, LocalDate day, String placement, long impressions, long clicks) {
        jdbc.update("INSERT INTO ad_daily_stats (application_id, version, day, placement, impressions, clicks) VALUES (?, 1, ?, ?, ?, ?)", id, java.sql.Date.valueOf(day),
                placement, impressions, clicks);
    }

    private List<String> ids(JsonNode page) {
        return page.get("items").valueStream().map(n -> n.get("id").asString()).toList();
    }

    @Test
    void 권한_ad_read_없으면_403_칸이_틀리면_400_응답은_no_store() throws Exception {
        Cookie none = login(operatorWith());
        Cookie reader = login(operatorWith(Permission.AD_READ));
        mvc.perform(get("/api/admin/ads/stats").param("from", "2001-01-01").param("to", "2001-01-02").cookie(none)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/ads/stats/bookings").param("from", "2001-01-01").param("to", "2001-01-02").cookie(none)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/ads/1/stats").cookie(none)).andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/ads/stats").cookie(reader)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.from").exists())
                .andExpect(jsonPath("$.error.fields.to").exists());
        mvc.perform(get("/api/admin/ads/stats").param("from", "2001-01-05").param("to", "2001-01-01").cookie(reader)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.from").exists());
        mvc.perform(get("/api/admin/ads/stats").param("from", "2001-01-01").param("to", "2002-01-02").cookie(reader)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.to").exists()).andExpect(jsonPath("$.error.fields.from").doesNotExist());
        // 366일(끝 포함)까지는 된다
        mvc.perform(get("/api/admin/ads/stats").param("from", "2001-01-01").param("to", "2002-01-01").cookie(reader)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.days.length()").value(366));
        mvc.perform(get("/api/admin/ads/stats").param("from", "2001-01-01").param("to", "2001-01-02").param("type", "HOTDEAL_POST").param("advertiserId", "foo-1")
                .cookie(reader)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.type").exists())
                .andExpect(jsonPath("$.error.fields.advertiserId").exists());
        mvc.perform(get("/api/admin/ads/stats/bookings").param("from", "2001-01-01").param("to", "2001-01-02").param("sort", "views").param("dir", "up")
                .param("page", "-1").param("size", "101").cookie(reader)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.sort").exists())
                .andExpect(jsonPath("$.error.fields.dir").exists()).andExpect(jsonPath("$.error.fields.page").exists()).andExpect(jsonPath("$.error.fields.size").exists());
        mvc.perform(get("/api/admin/ads/stats/bookings").param("from", "2001-01-01").param("to", "2001-01-02").cookie(reader)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.total").value(0));
        // 한 건 — 없는 건·글자 ID 404
        mvc.perform(get("/api/admin/ads/" + ids.nextId() + "/stats").cookie(reader)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BIZ_APPLICATION_NOT_FOUND"));
        mvc.perform(get("/api/admin/ads/abc/stats").cookie(reader)).andExpect(status().isNotFound());
    }

    @Test
    void 합계_유형_광고주_거르기와_건별_정렬_쪽_그리고_한_건() throws Exception {
        long me = operatorWith(Permission.AD_READ, Permission.AD_MANAGE);
        Cookie op = login(me);
        String adv = createAdvertiser(op);
        long advId = Long.parseLong(adv.substring(4));
        long profile = businessProfile("성과상사");
        LocalDate d1 = LocalDate.of(2001, 3, 1);

        long a = booking("AD_TEXT_LINE", List.of("TEXT_LINE"), "SCHEDULED", null, kst(d1), kst(d1.plusDays(6)), advId, null, me);
        long b = booking("AD_BANNER", List.of("WIDE_BANNER"), "SCHEDULED", null, kst(d1.plusDays(2)), kst(d1.plusDays(12)), advId, null, me);
        long c = booking("HOTDEAL_POST", List.of("HOTDEAL_POST", "HOTDEAL_TOP"), "TAKEN_DOWN", "EMERGENCY", kst(d1), kst(d1.plusDays(9)), null, profile, me);
        long d = booking("AD_TEXT_LINE", List.of("TEXT_LINE"), "CANCELED", null, kst(d1.plusDays(1)), kst(d1.plusDays(5)), advId, null, me);
        long e = booking("AD_TEXT_LINE", List.of("TEXT_LINE"), "SUBMITTED", null, kst(d1.plusDays(1)), kst(d1.plusDays(5)), advId, null, me);
        long dealOnly = booking("HOTDEAL_POST", List.of("HOTDEAL_POST"), "SCHEDULED", null, kst(d1), kst(d1.plusDays(3)), null, profile, me);
        stat(a, d1, "HOME", 100, 5);
        stat(a, d1.plusDays(1), "BOARD", 50, 1);
        stat(a, d1.minusDays(1), "HOME", 999, 99); // 범위 밖
        stat(b, d1.plusDays(2), "HOME", 30, 3);
        stat(c, d1.plusDays(3), "HOTDEAL_TOP", 20, 4);
        String from = d1.toString();
        String to = d1.plusDays(9).toString();

        // 합계 — 노출된 건 3 · 날짜 10 · 유형·자리·광고주 노출 차례
        JsonNode all = data(mvc.perform(get("/api/admin/ads/stats").param("from", from).param("to", to).cookie(op)).andExpect(status().isOk()));
        assertThat(all.get("range").get("from").asString()).isEqualTo(from);
        assertThat(all.get("advertiser").isNull()).isTrue();
        assertThat(all.get("totals").get("impressions").asLong()).isEqualTo(200);
        assertThat(all.get("totals").get("clicks").asLong()).isEqualTo(13);
        assertThat(all.get("totals").get("bookings").asLong()).isEqualTo(3);
        assertThat(all.get("days").size()).isEqualTo(10);
        assertThat(all.get("days").get(0).get("impressions").asLong()).isEqualTo(100);
        assertThat(all.get("days").get(9).get("impressions").asLong()).isZero();
        assertThat(all.get("byType").valueStream().map(n -> n.get("type").asString()).toList()).containsExactly("TEXT_LINE", "WIDE_BANNER", "HOTDEAL_TOP");
        assertThat(all.get("byType").get(0).get("bookings").asLong()).isEqualTo(1);
        assertThat(all.get("byPlacement").valueStream().map(n -> n.get("placement").asString()).toList()).containsExactly("HOME", "BOARD", "HOTDEAL_TOP");
        assertThat(all.get("byPlacement").get(0).get("impressions").asLong()).isEqualTo(130);
        JsonNode top = all.get("topAdvertisers");
        assertThat(top.size()).isEqualTo(2);
        assertThat(top.get(0).get("advertiser").get("id").asString()).isEqualTo(adv);
        assertThat(top.get(0).get("impressions").asLong()).isEqualTo(180);
        assertThat(top.get(0).get("bookings").asLong()).isEqualTo(2);
        assertThat(top.get(1).get("advertiser").get("id").asString()).isEqualTo("biz-" + profile);
        assertThat(top.get(1).get("advertiser").get("kind").asString()).isEqualTo("BUSINESS");
        assertThat(top.get(1).get("advertiser").get("name").asString()).isEqualTo("성과상사");

        // 유형 · 광고주 거르기(거른 광고주 이름 · 없는 광고주는 null)
        JsonNode text = data(mvc.perform(get("/api/admin/ads/stats").param("from", from).param("to", to).param("type", "TEXT_LINE").cookie(op)).andExpect(status().isOk()));
        assertThat(text.get("totals").get("impressions").asLong()).isEqualTo(150);
        JsonNode hot = data(mvc.perform(get("/api/admin/ads/stats").param("from", from).param("to", to).param("type", "HOTDEAL_TOP").cookie(op)).andExpect(status().isOk()));
        assertThat(hot.get("totals").get("impressions").asLong()).isEqualTo(20); // 핫딜 노출 자리 건 = 그 신청 건의 집계
        JsonNode byAdv = data(mvc.perform(get("/api/admin/ads/stats").param("from", from).param("to", to).param("advertiserId", adv).cookie(op)).andExpect(status().isOk()));
        assertThat(byAdv.get("totals").get("impressions").asLong()).isEqualTo(180);
        assertThat(byAdv.get("advertiser").get("id").asString()).isEqualTo(adv);
        assertThat(byAdv.get("advertiser").get("name").asString()).startsWith("성과광고주");
        JsonNode byBiz = data(mvc.perform(get("/api/admin/ads/stats").param("from", from).param("to", to).param("advertiserId", "biz-" + profile).cookie(op))
                .andExpect(status().isOk()));
        assertThat(byBiz.get("totals").get("impressions").asLong()).isEqualTo(20);
        assertThat(byBiz.get("advertiser").get("name").asString()).isEqualTo("성과상사");
        JsonNode nobody = data(mvc.perform(get("/api/admin/ads/stats").param("from", from).param("to", to).param("advertiserId", "adv-" + ids.nextId()).cookie(op))
                .andExpect(status().isOk()));
        assertThat(nobody.get("advertiser").isNull()).isTrue();
        assertThat(nobody.get("totals").get("impressions").asLong()).isZero();

        // 건별 — 결제 뒤 상태만(심사 대기 E 빠짐) · 장부 유형 줄이 있는 건만(노출 없는 핫딜 글 빠짐), 노출 0 건(취소 D) 포함, 기본 노출 내림차순
        JsonNode page = data(mvc.perform(get("/api/admin/ads/stats/bookings").param("from", from).param("to", to).cookie(op)).andExpect(status().isOk()));
        assertThat(page.get("total").asLong()).isEqualTo(4);
        assertThat(ids(page)).containsExactly(Long.toString(a), Long.toString(b), Long.toString(c), Long.toString(d)).doesNotContain(Long.toString(e),
                Long.toString(dealOnly));
        JsonNode rowC = page.get("items").get(2);
        assertThat(rowC.get("type").asString()).isEqualTo("HOTDEAL_TOP");
        assertThat(rowC.get("domain").asString()).isEqualTo("HOTDEAL");
        assertThat(rowC.get("status").asString()).isEqualTo("TAKEN_DOWN");
        assertThat(rowC.get("takedownCause").asString()).isEqualTo("EMERGENCY");
        assertThat(rowC.get("advertiser").get("id").asString()).isEqualTo("biz-" + profile);
        assertThat(rowC.get("impressions").asLong()).isEqualTo(20);
        assertThat(rowC.get("period").get("startAt").asString()).isNotBlank();
        assertThat(page.get("items").get(0).get("advertiser").get("id").asString()).isEqualTo(adv);
        assertThat(page.get("items").get(0).get("status").asString()).isEqualTo("ENDED");
        // 클릭률 — 노출 0 은 방향과 상관없이 뒤
        JsonNode ctrDesc = data(mvc.perform(get("/api/admin/ads/stats/bookings").param("from", from).param("to", to).param("type", "TEXT_LINE").param("sort", "ctr")
                .cookie(op)).andExpect(status().isOk()));
        assertThat(ids(ctrDesc)).containsExactly(Long.toString(a), Long.toString(d));
        JsonNode ctrAll = data(mvc.perform(get("/api/admin/ads/stats/bookings").param("from", from).param("to", to).param("advertiserId", adv).param("sort", "ctr")
                .param("dir", "desc").cookie(op)).andExpect(status().isOk()));
        assertThat(ids(ctrAll)).containsExactly(Long.toString(b), Long.toString(a), Long.toString(d)); // 10% · 4% · 노출 0
        JsonNode ctrAsc = data(mvc.perform(get("/api/admin/ads/stats/bookings").param("from", from).param("to", to).param("advertiserId", adv).param("sort", "ctr")
                .param("dir", "asc").cookie(op)).andExpect(status().isOk()));
        assertThat(ids(ctrAsc)).containsExactly(Long.toString(a), Long.toString(b), Long.toString(d));
        JsonNode clicksAsc = data(mvc.perform(get("/api/admin/ads/stats/bookings").param("from", from).param("to", to).param("advertiserId", adv).param("sort", "clicks")
                .param("dir", "asc").cookie(op)).andExpect(status().isOk()));
        assertThat(ids(clicksAsc)).containsExactly(Long.toString(d), Long.toString(b), Long.toString(a));
        JsonNode second = data(mvc.perform(get("/api/admin/ads/stats/bookings").param("from", from).param("to", to).param("advertiserId", adv).param("page", "1")
                .param("size", "2").cookie(op)).andExpect(status().isOk()));
        assertThat(second.get("total").asLong()).isEqualTo(3);
        assertThat(ids(second)).containsExactly(Long.toString(d));

        // 한 건 — 장부 건(끝난 건 = 기간 전체), 핫딜 노출 자리 건(지금 내림 — 상태 칸 보정), 노출 없는 핫딜 글 404
        JsonNode one = data(mvc.perform(get("/api/admin/ads/" + a + "/stats").cookie(op)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")));
        assertThat(one.get("range").get("from").asString()).isEqualTo(from);
        assertThat(one.get("range").get("to").asString()).isEqualTo(d1.plusDays(5).toString());
        assertThat(one.get("days").size()).isEqualTo(6);
        assertThat(one.get("totals").get("impressions").asLong()).isEqualTo(150);
        assertThat(one.get("byVersion").get(0).get("current").asBoolean()).isTrue();
        JsonNode down = data(mvc.perform(get("/api/admin/ads/" + c + "/stats").cookie(op)).andExpect(status().isOk()));
        assertThat(down.get("byPlacement").get(0).get("placement").asString()).isEqualTo("HOTDEAL_TOP");
        assertThat(down.get("days").get(3).get("state").asString()).isEqualTo("SHOWING");
        assertThat(down.get("days").get(4).get("state").asString()).isEqualTo("TAKEN_DOWN");
        assertThat(down.get("downtimes").size()).isEqualTo(1);
        assertThat(down.get("downtimes").get(0).get("to").isNull()).isTrue();
        mvc.perform(get("/api/admin/ads/" + dealOnly + "/stats").cookie(op)).andExpect(status().isNotFound());
        // 승인 뒤 취소된 건은 닫힌 날까지
        JsonNode canceled = data(mvc.perform(get("/api/admin/ads/" + d + "/stats").cookie(op)).andExpect(status().isOk()));
        assertThat(canceled.get("range").get("to").asString()).isEqualTo(d1.plusDays(2).toString());
        // 글자 경로 /stats 는 상세(/{id})로 가지 않는다 — 위에서 200 · 상세는 그대로
        mvc.perform(get("/api/admin/ads/" + a).cookie(op)).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(Long.toString(a)));
    }

    @Test
    void 건별은_시작한_건만_범위는_오늘을_넘어도_된다() throws Exception {
        long me = operatorWith(Permission.AD_READ, Permission.AD_MANAGE);
        Cookie op = login(me);
        long advId = Long.parseLong(createAdvertiser(op).substring(4));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        long started = booking("AD_TEXT_LINE", List.of("TEXT_LINE"), "SCHEDULED", null, now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(2)), advId, null, me);
        long later = booking("AD_TEXT_LINE", List.of("TEXT_LINE"), "SCHEDULED", null, now.plus(Duration.ofHours(2)), now.plus(Duration.ofDays(3)), advId, null, me);
        LocalDate today = LocalDate.ofInstant(now, KST);
        JsonNode page = data(mvc.perform(get("/api/admin/ads/stats/bookings").param("from", today.minusDays(1).toString()).param("to", today.plusDays(3).toString())
                .param("advertiserId", "adv-" + advId).cookie(op)).andExpect(status().isOk()));
        assertThat(ids(page)).containsExactly(Long.toString(started)).doesNotContain(Long.toString(later));
        assertThat(page.get("items").get(0).get("status").asString()).isEqualTo("RUNNING");
        assertThat(page.get("items").get(0).get("impressions").asLong()).isZero();
    }

    @Test
    void 메뉴_광고_묶음에_광고_성과가_일정표_다음이고_ad_read() {
        AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen("adPerformance").orElseThrow();
        assertThat(screen.path()).isEqualTo("/ads/performance");
        assertThat(screen.labelKey()).isEqualTo("menu.adPerformance");
        assertThat(screen.icon()).isEqualTo("pi pi-chart-bar");
        assertThat(screen.permission()).isEqualTo(Permission.AD_READ);
        AdminMenuNode ad = AdminScreenRegistry.defaults().get(AdminScreenRegistry.NORMAL).stream().filter(n -> "menu.ad".equals(n.labelKey())).findFirst().orElseThrow();
        List<String> keys = ad.children().stream().map(AdminMenuNode::targetKey).toList();
        assertThat(keys.indexOf("adPerformance")).isEqualTo(keys.indexOf("ads") + 1);
    }
}
