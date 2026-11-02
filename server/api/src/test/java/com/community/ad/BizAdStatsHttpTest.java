package com.community.ad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.ad.service.AdOperatorService;
import com.community.auth.dto.AuthUser;
import com.community.bizapplication.BizApplicationApiTestBase;
import com.community.bizapplication.service.BizPauseService;
import com.community.bizapplication.service.BizPeriods;
import com.community.installation.service.InstallationState;
import com.community.user.entity.Role;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 사업자 광고 성과 HTTP(ad-performance-contract 규칙 2~8) — 한 건 {@code GET /api/business/applications/{id}/stats}: 본인 건만(남의 건·없는 건 404), 시작 전 빈 범위, 날짜 0 채우기·
 * 정지 날 PAUSED·긴급 내림 날 TAKEN_DOWN·멈춘 구간(지금 내림이면 {@code to} null)·자리·버전(승인 기록 없는 버전 포함)·범위 자르기·400 {@code fields.from}·no-store, 노출 없는 핫딜 글은 빈 결과.
 * 요약 {@code GET /api/business/ads/stats-summary}: 진행 중·정지 중·긴급 내림·사업자 정지는 넣고 광고주 중단·끝난 건·시작 전·노출 없는 건은 뺀다, 최대 5 · 시작 늦은 차례 · 최근 7일(기간 밖 0).
 */
class BizAdStatsHttpTest extends BizApplicationApiTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private InstallationState installation;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilterChain;
    @Autowired
    private BizPauseService pauses;
    @Autowired
    private AdOperatorService ads;

    private MockMvc mvc;
    private MockMvc secured;

    @BeforeEach
    void setUpMvc() {
        installation.markCompleted();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        secured = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
    }

    private static MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
    }

    private static JsonNode data(ResultActions result) throws Exception {
        return MAPPER.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private void stat(long id, int version, LocalDate day, String placement, long impressions, long clicks) {
        jdbc.update("INSERT INTO ad_daily_stats (application_id, version, day, placement, impressions, clicks) VALUES (?, ?, ?, ?, ?, ?)", id, version,
                java.sql.Date.valueOf(day), placement, impressions, clicks);
    }

    /** 한 줄 광고를 승인·온라인 결제(예약) */
    private long paidTextLine(long user, BizPeriods.Period p) {
        long id = submitTextLine(user, 0, 2, "ONLINE");
        approve(id, "ONLINE", p);
        pay(user, id);
        return id;
    }

    /** 결제한 건을 시험 기간 {@code [start, end)} 로 옮긴다(자리 판정 없이 — 요약 시험만) */
    private void move(long id, Instant start, Instant end) {
        jdbc.update("UPDATE biz_applications SET start_at = ?, end_at = ? WHERE id = ?", Timestamp.from(start), Timestamp.from(end), id);
    }

    @Test
    void 한_건_성과는_본인_건만이고_날짜_상태_멈춘_구간_자리_버전을_준다() throws Exception {
        long user = approvedBusiness();
        BizPeriods.Period p = freshPeriod(5);
        long id = paidTextLine(user, p);
        LocalDate d1 = BizPeriods.startDate(p.start());
        String url = "/api/business/applications/" + id + "/stats";

        // 시작 전 — 시작 날짜 하루의 빈 범위
        JsonNode before = data(mvc.perform(as(user, get(url))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")));
        assertThat(before.get("range").get("from").asString()).isEqualTo(d1.toString());
        assertThat(before.get("range").get("to").asString()).isEqualTo(d1.toString());
        assertThat(before.get("days").size()).isZero();
        assertThat(before.get("totals").get("impressions").asLong()).isZero();

        // 둘째 날 10시 정지 → 12시 다시 켜기, 셋째 날 9시 긴급 내림
        clock.set(p.start().plus(Duration.ofDays(1)).plus(Duration.ofHours(10)));
        pauses.pause(user, id);
        clock.set(clock.instant().plus(Duration.ofHours(2)));
        pauses.resume(user, id);
        stat(id, 1, d1, "HOME", 10, 1);
        stat(id, 1, d1, "BOARD", 4, 0);
        stat(id, 1, d1.plusDays(1), "HOME", 6, 2);
        stat(id, 2, d1.plusDays(2), "POST_INLINE", 1, 0); // 승인 기록 없는 버전
        clock.set(p.start().plus(Duration.ofDays(2)).plus(Duration.ofHours(9)));
        ads.takedown(operator, id, "긴급 내림 시험", false);
        clock.set(clock.instant().plus(Duration.ofHours(1)));

        JsonNode s = data(mvc.perform(as(user, get(url))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")));
        assertThat(s.get("range").get("from").asString()).isEqualTo(d1.toString());
        assertThat(s.get("range").get("to").asString()).isEqualTo(d1.plusDays(2).toString()); // 오늘까지
        assertThat(s.get("asOf").asString()).isNotBlank();
        assertThat(s.get("totals").get("impressions").asLong()).isEqualTo(21);
        assertThat(s.get("totals").get("clicks").asLong()).isEqualTo(3);
        assertThat(s.get("days").size()).isEqualTo(3);
        assertThat(s.get("days").get(0).get("state").asString()).isEqualTo("SHOWING");
        assertThat(s.get("days").get(0).get("impressions").asLong()).isEqualTo(14);
        assertThat(s.get("days").get(1).get("state").asString()).isEqualTo("PAUSED");
        assertThat(s.get("days").get(2).get("state").asString()).isEqualTo("TAKEN_DOWN");
        assertThat(s.get("byPlacement").valueStream().map(n -> n.get("placement").asString()).toList()).containsExactly("HOME", "BOARD", "POST_INLINE");
        assertThat(s.get("byPlacement").get(0).get("impressions").asLong()).isEqualTo(16);
        assertThat(s.get("byVersion").size()).isEqualTo(2);
        assertThat(s.get("byVersion").get(0).get("version").asInt()).isEqualTo(1);
        assertThat(s.get("byVersion").get(0).get("current").asBoolean()).isTrue();
        assertThat(s.get("byVersion").get(0).get("approvedAt").isNull()).isFalse();
        assertThat(s.get("byVersion").get(1).get("version").asInt()).isEqualTo(2);
        assertThat(s.get("byVersion").get(1).get("approvedAt").isNull()).isTrue();
        assertThat(s.get("byVersion").get(1).get("current").asBoolean()).isFalse();
        JsonNode downtimes = s.get("downtimes");
        assertThat(downtimes.size()).isEqualTo(2);
        assertThat(downtimes.get(0).get("cause").asString()).isEqualTo("ADVERTISER_PAUSE");
        assertThat(Instant.parse(downtimes.get(0).get("from").asString())).isEqualTo(p.start().plus(Duration.ofHours(34)));
        assertThat(Instant.parse(downtimes.get(0).get("to").asString())).isEqualTo(p.start().plus(Duration.ofHours(36)));
        assertThat(downtimes.get(1).get("cause").asString()).isEqualTo("EMERGENCY");
        assertThat(downtimes.get(1).get("to").isNull()).isTrue(); // 지금 내려져 있다

        // 범위 자르기 — 둘째 날만(그 날과 겹치는 구간만), 기간 밖 요청은 기간으로
        JsonNode one = data(mvc.perform(as(user, get(url)).param("from", d1.plusDays(1).toString()).param("to", d1.plusDays(1).toString())).andExpect(status().isOk()));
        assertThat(one.get("days").size()).isEqualTo(1);
        assertThat(one.get("totals").get("impressions").asLong()).isEqualTo(6);
        assertThat(one.get("downtimes").size()).isEqualTo(1);
        JsonNode wide = data(mvc.perform(as(user, get(url)).param("from", d1.minusDays(10).toString()).param("to", d1.plusDays(30).toString()))
                .andExpect(status().isOk()));
        assertThat(wide.get("range").get("from").asString()).isEqualTo(d1.toString());
        assertThat(wide.get("range").get("to").asString()).isEqualTo(d1.plusDays(2).toString());

        // 400 fields.from — 형식 · from > to
        mvc.perform(as(user, get(url)).param("from", "2031-13-01")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.from").exists());
        mvc.perform(as(user, get(url)).param("to", "어제")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.from").exists());
        mvc.perform(as(user, get(url)).param("from", d1.plusDays(2).toString()).param("to", d1.toString())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.from").exists());

        // 남의 건·없는 건 404, 로그인 없으면 401
        long other = approvedBusiness();
        mvc.perform(as(other, get(url))).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("BIZ_APPLICATION_NOT_FOUND"));
        mvc.perform(as(user, get("/api/business/applications/" + ids.nextId() + "/stats"))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BIZ_APPLICATION_NOT_FOUND"));
        mvc.perform(as(user, get("/api/business/applications/abc/stats"))).andExpect(status().isNotFound());
        secured.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void 노출_자리_줄이_없는_본인_핫딜_글은_404_가_아니라_빈_결과() throws Exception {
        long user = approvedBusiness();
        long deal = submitDeal(user, "NONE", null, null, "ONLINE");
        JsonNode s = data(mvc.perform(as(user, get("/api/business/applications/" + deal + "/stats"))).andExpect(status().isOk()));
        assertThat(s.get("days").size()).isZero();
        assertThat(s.get("byPlacement").size()).isZero();
        assertThat(s.get("byVersion").size()).isZero();
        assertThat(s.get("downtimes").size()).isZero();
        assertThat(s.get("range").get("from").asString()).isEqualTo(BizPeriods.startDate(app(deal).startAt()).toString());
    }

    @Test
    void 요약은_진행_정지_긴급_사업자_정지만_넣고_최대_5_시작_늦은_차례_최근_7일() throws Exception {
        long user = approvedBusiness();
        // 결제는 각자 겹치지 않는 먼 미래 기간에 하고, 시험 시각 T 주위로 옮긴다
        long a = paidTextLine(user, freshPeriod(3)); // 진행 중(시작 T-1일)
        long b = paidTextLine(user, freshPeriod(3)); // 정지 중(T-2일)
        long c = paidTextLine(user, freshPeriod(3)); // 긴급 내림(T-3일)
        long d = paidTextLine(user, freshPeriod(3)); // 사업자 정지(T-4일)
        long x = paidTextLine(user, freshPeriod(3)); // 진행 중(T-5일) — 다섯째
        long y = paidTextLine(user, freshPeriod(3)); // 진행 중(T-6일) — 여섯째라 빠진다
        long stopped = paidTextLine(user, freshPeriod(3)); // 광고주 중단 — 빠진다
        long ended = paidTextLine(user, freshPeriod(3)); // 끝남 — 빠진다
        long upcoming = paidTextLine(user, freshPeriod(3)); // 시작 전 — 빠진다
        submitDeal(user, "NONE", null, null, "ONLINE"); // 노출 자리 없음 — 빠진다

        LocalDate today = LocalDate.of(2040, 3, 10);
        Instant t = BizPeriods.startOf(today).plus(Duration.ofHours(12));
        Instant end = t.plus(Duration.ofDays(5));
        move(a, t.minus(Duration.ofDays(1)), end);
        move(b, t.minus(Duration.ofDays(2)), end);
        move(c, t.minus(Duration.ofDays(3)), end);
        move(d, t.minus(Duration.ofDays(4)), end);
        move(x, t.minus(Duration.ofDays(5)), end);
        move(y, t.minus(Duration.ofDays(6)), end);
        move(stopped, t.minus(Duration.ofHours(1)), end);
        move(ended, t.minus(Duration.ofDays(9)), t.minus(Duration.ofHours(1)));
        move(upcoming, t.plus(Duration.ofHours(1)), end);
        clock.set(t);
        pauses.pause(user, b);
        ads.takedown(operator, c, "긴급 내림 시험", false);
        jdbc.update("UPDATE biz_applications SET status = 'TAKEN_DOWN', takedown_cause = 'SUSPENDED', taken_down_at = ? WHERE id = ?", Timestamp.from(t), d);
        pauses.stop(user, stopped, "광고주 중단 시험");

        // a: 오늘·이틀 전 숫자 + 시작 전 날(기간 밖 — 0 으로 보인다)
        stat(a, 1, today, "HOME", 5, 1);
        stat(a, 1, today.minusDays(1), "BOARD", 3, 0);
        stat(a, 1, today.minusDays(3), "HOME", 100, 9);
        stat(ended, 1, today.minusDays(1), "HOME", 50, 5);

        JsonNode s = data(mvc.perform(as(user, get("/api/business/ads/stats-summary"))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")));
        assertThat(s.get("asOf").asString()).isNotBlank();
        List<String> got = s.get("items").valueStream().map(n -> n.get("id").asString()).toList();
        assertThat(got).containsExactly(Long.toString(a), Long.toString(b), Long.toString(c), Long.toString(d), Long.toString(x));
        JsonNode first = s.get("items").get(0);
        assertThat(first.get("kind").asString()).isEqualTo("AD_TEXT_LINE");
        assertThat(first.get("adType").asString()).isEqualTo("TEXT_LINE");
        assertThat(first.get("status").asString()).isEqualTo("SCHEDULED");
        assertThat(first.get("phase").asString()).isEqualTo("RUNNING");
        assertThat(first.get("startDate").asString()).isEqualTo(today.minusDays(1).toString());
        assertThat(first.get("last7").get("impressions").asLong()).isEqualTo(8);
        assertThat(first.get("last7").get("clicks").asLong()).isEqualTo(1);
        JsonNode spark = first.get("spark");
        assertThat(spark.size()).isEqualTo(7);
        assertThat(spark.get(0).get("date").asString()).isEqualTo(today.minusDays(6).toString());
        assertThat(spark.get(6).get("date").asString()).isEqualTo(today.toString());
        assertThat(spark.get(6).get("impressions").asLong()).isEqualTo(5);
        assertThat(spark.get(3).get("impressions").asLong()).isZero(); // 시작 전 날
        assertThat(spark.get(3).get("state").asString()).isEqualTo("SHOWING");
        JsonNode paused = s.get("items").get(1);
        assertThat(paused.get("status").asString()).isEqualTo("TAKEN_DOWN");
        assertThat(paused.get("takedownCause").asString()).isEqualTo("ADVERTISER_PAUSE");
        assertThat(paused.get("spark").get(6).get("state").asString()).isEqualTo("PAUSED");
        assertThat(s.get("items").get(2).get("takedownCause").asString()).isEqualTo("EMERGENCY");
        assertThat(s.get("items").get(2).get("spark").get(6).get("state").asString()).isEqualTo("TAKEN_DOWN");
        assertThat(s.get("items").get(3).get("takedownCause").asString()).isEqualTo("SUSPENDED");

        // 다른 사업자는 비어 있다
        JsonNode none = data(mvc.perform(as(approvedBusiness(), get("/api/business/ads/stats-summary"))).andExpect(status().isOk()));
        assertThat(none.get("items").size()).isZero();
        secured.perform(get("/api/business/ads/stats-summary")).andExpect(status().isUnauthorized());
    }
}
