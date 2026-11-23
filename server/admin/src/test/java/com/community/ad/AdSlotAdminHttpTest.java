package com.community.ad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 광고 자리 관리 API(실제 관리자 앱 문맥 + PostgreSQL, ad-slot-manager-contract 「API」) — 권한(조회 ad.read · 여러 건 내림 ad.takedown · CSRF) · 요약의 진행 중·한도가 일정표 요약과 같다 ·
 * 카드 모양(제목·섬네일·7일 성과·남은 날·예시 주소)·차례·거르기(입금 대기 = 결제 대기 포함)·끝난 건 빠짐 · 여러 건 내림(건마다 따로 — 실패한 건은 문구로, 나머지는 내려지고 감사가 건마다).
 * 목록은 「지금」 자리를 잡은 건이라 시험 건은 오늘 기준으로 표에 직접 넣는다(반 폭 배너 — 다른 시험이 쓰지 않는 종류). 다른 시험의 건이 섞일 수 있어 내 건만 골라 본다.
 */
class AdSlotAdminHttpTest extends Commerce04AdminTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String TYPE = "HALF_BANNER";

    private JsonNode data(ResultActions result) throws Exception {
        return MAPPER.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private static LocalDate today() {
        return LocalDate.now(KST);
    }

    private static Instant kst(LocalDate day) {
        return day.atStartOfDay(KST).toInstant();
    }

    /**
     * 장부 건 한 줄(운영자 등록) + 상품 줄 + 승인된 소재 1. {@code status} AWAITING_PAYMENT 는 온라인(결제 기한), 그 밖은 수동(예약·내림은 계약서 1장 + 입금 확인 — 수동 확정 CHECK).
     */
    private long booking(String status, String cause, Instant start, Instant end, String content, long operatorId) {
        long id = ids.nextId();
        boolean online = status.equals("AWAITING_PAYMENT");
        boolean confirmed = status.equals("SCHEDULED") || status.equals("TAKEN_DOWN");
        // SQL 로 남김: 오늘 기준 과거·미래 시각과 승인 뒤 상태를 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각·심사 흐름으로만 쓴다
        jdbc.update("INSERT INTO biz_applications (id, application_no, kind, status, source, advertiser_name, title, start_at, end_at, preferred_method, payment_method, "
                        + "quote_supply, quote_vat, quote_total, approved_supply, approved_vat, approved_total, approved_at, approved_by, payment_deadline_at, deposit_deadline_at, "
                        + "active_contract_count, deposit_confirmed_at, taken_down_at, takedown_cause, ad_policy_version, refund_policy_version, agreed_at, stop_fee_rate, "
                        + "created_by, created_at, updated_at) "
                        + "VALUES (?, ?, 'AD_BANNER', ?, 'OPERATOR', '자리광고주', '자리 시험', ?, ?, ?, ?, 1000, 100, 1100, 2000, 200, 2200, ?, ?, ?, ?, ?, ?, ?, ?, 'v1', 'v1', "
                        + "now(), 0, ?, now(), now())",
                id, "SL-" + (id % 1_000_000_000_000L), status, Timestamp.from(start), Timestamp.from(end), online ? "ONLINE" : "MANUAL", online ? "ONLINE" : "MANUAL",
                Timestamp.from(start.minus(Duration.ofDays(3))), operatorId, online ? Timestamp.from(start.minus(Duration.ofDays(1))) : null,
                online ? null : Timestamp.from(start.minus(Duration.ofDays(1))), confirmed ? 1 : 0, confirmed ? Timestamp.from(start.minus(Duration.ofDays(2))) : null,
                cause == null ? null : Timestamp.from(Instant.now().minus(Duration.ofHours(1))), cause, operatorId);
        // SQL 로 남김: 과거·미래 시각을 바로 세운다
        jdbc.update("INSERT INTO biz_application_items (id, application_id, product, unit_price, units, supply, start_at, end_at, created_at) "
                + "VALUES (?, ?, ?, 1000, 1, 1000, ?, ?, now())", ids.nextId(), id, TYPE, Timestamp.from(start), Timestamp.from(end));
        // SQL 로 남김: 승인된 소재를 바로 세운다
        jdbc.update("INSERT INTO biz_creative_versions (id, application_id, version, content, created_by_type, created_by, created_at, approved_at) "
                + "VALUES (?, ?, 1, ?::jsonb, 'OPERATOR', ?, now(), ?)", ids.nextId(), id, content, operatorId, Timestamp.from(start.minus(Duration.ofDays(3))));
        return id;
    }

    private void stat(long id, LocalDate day, long impressions, long clicks) {
        jdbc.update("INSERT INTO ad_daily_stats (application_id, version, day, placement, impressions, clicks) VALUES (?, 1, ?, 'HOME', ?, ?)", id, java.sql.Date.valueOf(day),
                impressions, clicks);
    }

    private static JsonNode card(JsonNode list, long id) {
        return list.get("items").valueStream().filter(n -> n.get("id").asString().equals(Long.toString(id))).findFirst().orElse(null);
    }

    private static List<String> mine(JsonNode list, List<Long> ids) {
        List<String> wanted = ids.stream().map(String::valueOf).toList();
        return list.get("items").valueStream().map(n -> n.get("id").asString()).filter(wanted::contains).toList();
    }

    @Test
    void 권한_조회_ad_read_내림_ad_takedown_CSRF_응답은_no_store() throws Exception {
        Cookie none = login(operatorWith());
        Cookie reader = login(operatorWith(Permission.AD_READ));
        mvc.perform(get("/api/admin/ads/slots").cookie(none)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/ads/slots/" + TYPE).cookie(none)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/ads/slots").cookie(reader)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/admin/ads/slots/" + TYPE).cookie(reader)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(withCsrf(post("/api/admin/ads/takedown"), reader).content("{\"ids\":[\"1\"],\"reason\":\"신고 접수\"}")).andExpect(status().isForbidden());
        Cookie takedown = login(operatorWith(Permission.AD_TAKEDOWN));
        mvc.perform(post("/api/admin/ads/takedown").cookie(takedown).contentType("application/json").content("{\"ids\":[\"1\"],\"reason\":\"신고 접수\"}"))
                .andExpect(status().isForbidden()); // CSRF 없음
        // 틀린 값 — 400 칸
        mvc.perform(get("/api/admin/ads/slots/NOPE").cookie(reader)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.type").exists());
        mvc.perform(get("/api/admin/ads/slots/HOTDEAL_POST").cookie(reader)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.type").exists());
        mvc.perform(get("/api/admin/ads/slots/" + TYPE).param("status", "ENDED").cookie(reader)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.status").exists());
        mvc.perform(withCsrf(post("/api/admin/ads/takedown"), takedown).content("{\"ids\":[],\"reason\":\"\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.ids").exists()).andExpect(jsonPath("$.error.fields.reason").exists());
        String many = IntStream.rangeClosed(1, 51).mapToObj(i -> "\"" + i + "\"").collect(Collectors.joining(","));
        mvc.perform(withCsrf(post("/api/admin/ads/takedown"), takedown).content("{\"ids\":[" + many + "],\"reason\":\"신고 접수\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.ids").exists());
    }

    @Test
    void 요약은_일정표_유형_차례이고_진행_중과_한도가_일정표_요약과_같다() throws Exception {
        Cookie reader = login(operatorWith(Permission.AD_READ));
        JsonNode summary = data(mvc.perform(get("/api/admin/ads/slots").cookie(reader)).andExpect(status().isOk()));
        List<String> types = summary.get("types").valueStream().map(n -> n.get("type").asString()).toList();
        assertThat(types).containsExactly("WIDE_BANNER", "STRIP_BANNER", "SQUARE_BANNER", "SMALL_BANNER", "HALF_BANNER", "WING_BANNER", "TEXT_LINE", "HOTDEAL_TOP",
                "HOTDEAL_CATEGORY");
        String month = YearMonth.now(KST).toString();
        for (JsonNode t : summary.get("types")) {
            JsonNode calendar = data(mvc.perform(get("/api/admin/ads/calendar").param("type", t.get("type").asString()).param("month", month).cookie(reader))
                    .andExpect(status().isOk()));
            assertThat(t.get("running").asInt()).as(t.get("type").asString()).isEqualTo(calendar.get("summary").get("running").asInt());
            assertThat(t.get("limit").asInt()).as(t.get("type").asString()).isEqualTo(calendar.get("summary").get("limit").asInt());
            assertThat(t.get("adsense").asString()).isIn("FILLS", "PARTIAL", "EMPTY", "NA");
        }
        JsonNode text = summary.get("types").get(6);
        List<String> textPlaces = text.get("places").valueStream().map(JsonNode::asString).toList();
        assertThat(textPlaces).contains("HOME", "PAGE", "BOARD");
        // 한 줄 — 가젯 자리도 인피드 틀이 있어 NA 가 아니다(2026-11-23 — 한 줄 광고 가젯도 목록 한 줄 인피드로 채운다)
        assertThat(text.get("adsense").asString()).isNotEqualTo("NA");
        assertThat(summary.get("types").get(7).get("adsense").asString()).isEqualTo("NA");
        assertThat(summary.get("types").get(7).get("places").valueStream().map(JsonNode::asString).toList()).containsExactly("HOTDEAL_TOP");
        assertThat(summary.get("types").get(5).get("places").valueStream().map(JsonNode::asString).toList()).isSubsetOf("WING_LEFT", "WING_RIGHT");
    }

    @Test
    void 목록은_진행_예약_대기_내림_차례_입금_대기_거르기는_결제_대기_포함_끝난_건과_심사_대기는_빠진다() throws Exception {
        long op = operatorWith(Permission.AD_READ);
        Cookie reader = login(op);
        JsonNode before = data(mvc.perform(get("/api/admin/ads/slots/" + TYPE).cookie(reader)).andExpect(status().isOk())).get("counts");
        LocalDate today = today();
        String banner = "{\"imageUrl\":\"/media/ad/half.png\",\"altText\":\"반 폭 대체 글\",\"linkUrl\":\"https://shop.example.com/sale\"}";
        long running = booking("SCHEDULED", null, kst(today.minusDays(2)), kst(today.plusDays(5)), banner, op);
        long running2 = booking("SCHEDULED", null, kst(today.minusDays(1)), kst(today.plusDays(9)), "{\"imageUrl\":\"/media/ad/b.png\",\"linkUrl\":\"https://irobit.kr/a\"}",
                op);
        long scheduled = booking("SCHEDULED", null, kst(today.plusDays(20)), kst(today.plusDays(25)), banner, op);
        long deposit = booking("AWAITING_DEPOSIT", null, kst(today.plusDays(30)), kst(today.plusDays(35)), banner, op);
        long payment = booking("AWAITING_PAYMENT", null, kst(today.plusDays(28)), kst(today.plusDays(33)), banner, op);
        long down = booking("TAKEN_DOWN", "EMERGENCY", kst(today.minusDays(1)), kst(today.plusDays(6)), banner, op);
        long ended = booking("SCHEDULED", null, kst(today.minusDays(10)), kst(today.minusDays(3)), banner, op);
        List<Long> all = List.of(running, running2, scheduled, deposit, payment, down, ended);
        // 7일 성과 — 오늘 포함 최근 7일만(8일 전은 뺀다)
        stat(running, today, 10, 2);
        stat(running, today.minusDays(6), 5, 1);
        stat(running, today.minusDays(7), 100, 50);

        JsonNode list = data(mvc.perform(get("/api/admin/ads/slots/" + TYPE).cookie(reader)).andExpect(status().isOk()));
        assertThat(list.get("type").asString()).isEqualTo(TYPE);
        assertThat(mine(list, all)).containsExactly(String.valueOf(running), String.valueOf(running2), String.valueOf(scheduled), String.valueOf(payment),
                String.valueOf(deposit), String.valueOf(down)); // 같은 상태는 시작이 빠른 차례 · 끝난 건 없음
        JsonNode counts = list.get("counts");
        assertThat(counts.get("ALL").asInt() - before.get("ALL").asInt()).isEqualTo(6);
        assertThat(counts.get("RUNNING").asInt() - before.get("RUNNING").asInt()).isEqualTo(2);
        assertThat(counts.get("SCHEDULED").asInt() - before.get("SCHEDULED").asInt()).isEqualTo(1);
        assertThat(counts.get("AWAITING_DEPOSIT").asInt() - before.get("AWAITING_DEPOSIT").asInt()).isEqualTo(2);
        assertThat(counts.get("TAKEN_DOWN").asInt() - before.get("TAKEN_DOWN").asInt()).isEqualTo(1);

        JsonNode c = card(list, running);
        assertThat(c.get("type").asString()).isEqualTo(TYPE);
        assertThat(c.get("status").asString()).isEqualTo("RUNNING");
        assertThat(c.get("title").asString()).isEqualTo("반 폭 대체 글");
        assertThat(c.get("imageUrl").asString()).isEqualTo("/media/ad/half.png");
        assertThat(c.get("text").isNull()).isTrue();
        assertThat(c.get("takedownCause").isNull()).isTrue();
        assertThat(c.get("remainingDays").asInt()).isEqualTo(5); // 오늘 ~ 끝 날짜(오늘+4) 끝 포함
        assertThat(c.get("impressions7d").asLong()).isEqualTo(15);
        assertThat(c.get("clicks7d").asLong()).isEqualTo(3);
        assertThat(c.get("advertiser").get("name").asString()).isEqualTo("자리광고주");
        assertThat(c.get("advertiser").get("kind").asString()).isEqualTo("ADVERTISER");
        assertThat(c.get("paymentMethod").asString()).isEqualTo("MANUAL");
        assertThat(c.get("amount").get("supply").asLong()).isEqualTo(2000);
        assertThat(c.get("amount").get("total").asLong()).isEqualTo(2200);
        assertThat(c.get("domain").asString()).isEqualTo("AD");
        assertThat(c.get("linkUrl").asString()).isEqualTo("https://shop.example.com/sale");
        assertThat(c.get("exampleUrl").asBoolean()).isTrue();
        assertThat(c.get("period").get("startAt").asString()).isEqualTo(kst(today.minusDays(2)).toString());
        JsonNode c2 = card(list, running2);
        assertThat(c2.get("title").asString()).isEqualTo("자리 시험"); // 대체 글이 없으면 건 제목
        assertThat(c2.get("exampleUrl").asBoolean()).isFalse();
        assertThat(c2.get("impressions7d").asLong()).isZero();
        assertThat(card(list, payment).get("status").asString()).isEqualTo("AWAITING_PAYMENT"); // 카드는 실제 상태 그대로
        assertThat(card(list, down).get("takedownCause").asString()).isEqualTo("EMERGENCY");

        JsonNode awaiting = data(mvc.perform(get("/api/admin/ads/slots/" + TYPE).param("status", "AWAITING_DEPOSIT").cookie(reader)).andExpect(status().isOk()));
        assertThat(mine(awaiting, all)).containsExactly(String.valueOf(payment), String.valueOf(deposit));
        assertThat(awaiting.get("counts").get("ALL").asInt()).isEqualTo(counts.get("ALL").asInt()); // 건수는 거르기와 상관없이 전체
        JsonNode runningOnly = data(mvc.perform(get("/api/admin/ads/slots/" + TYPE).param("status", "RUNNING").cookie(reader)).andExpect(status().isOk()));
        assertThat(mine(runningOnly, all)).containsExactly(String.valueOf(running), String.valueOf(running2));
    }

    @Test
    void 여러_건_내림은_건마다_따로_실패한_건은_문구로_나머지는_내려지고_감사가_건마다() throws Exception {
        long op = operatorWith(Permission.AD_READ, Permission.AD_TAKEDOWN);
        Cookie cookie = login(op);
        LocalDate today = today();
        String banner = "{\"imageUrl\":\"/media/ad/half.png\",\"altText\":\"내림 시험\",\"linkUrl\":\"https://irobit.kr/x\"}";
        long running = booking("SCHEDULED", null, kst(today.minusDays(1)), kst(today.plusDays(4)), banner, op);
        long scheduled = booking("SCHEDULED", null, kst(today.plusDays(40)), kst(today.plusDays(42)), banner, op);
        long deposit = booking("AWAITING_DEPOSIT", null, kst(today.plusDays(44)), kst(today.plusDays(46)), banner, op);
        String body = "{\"ids\":[\"" + running + "\",\"" + scheduled + "\",\"" + deposit + "\",\"999\",\"abc\",\"" + running + "\"],\"reason\":\"신고 접수로 내림\","
                + "\"notifyAdvertiser\":false}";
        JsonNode result = data(mvc.perform(withCsrf(post("/api/admin/ads/takedown"), cookie).header("Accept-Language", "ko").content(body))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")));
        assertThat(result.get("takenDown").asInt()).isEqualTo(2); // 같은 ID 는 한 번
        List<String> failedIds = result.get("failed").valueStream().map(n -> n.get("id").asString()).toList();
        assertThat(failedIds).containsExactly(String.valueOf(deposit), "999", "abc");
        assertThat(result.get("failed").get(0).get("message").asString()).isEqualTo("신청 건의 처리 상태가 이미 바뀌었어요. 다시 불러올게요.");
        assertThat(result.get("failed").get(1).get("message").asString()).isEqualTo("신청 건을 찾을 수 없어요.");
        assertThat(result.get("failed").get(2).get("message").asString()).isEqualTo("신청 건을 찾을 수 없어요.");
        // 내린 두 건은 긴급 내림 — 실패한 건은 그대로
        for (long id : List.of(running, scheduled)) {
            assertThat(jdbc.queryForMap("SELECT status, takedown_cause, taken_down_by, takedown_text FROM biz_applications WHERE id = ?", id))
                    .containsEntry("status", "TAKEN_DOWN").containsEntry("takedown_cause", "EMERGENCY").containsEntry("taken_down_by", op)
                    .containsEntry("takedown_text", "신고 접수로 내림");
        }
        assertThat(jdbc.queryForObject("SELECT status FROM biz_applications WHERE id = ?", String.class, deposit)).isEqualTo("AWAITING_DEPOSIT");
        // 감사는 건마다(단건 긴급 내림과 같은 AD_TAKEDOWN)
        Integer audits = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'AD_TAKEDOWN' AND actor_id = ? AND target_id IN (?, ?)", Integer.class, op,
                Long.toString(running), Long.toString(scheduled));
        assertThat(audits).isEqualTo(2);
        // 영어 문구 — 요청 언어
        JsonNode en = data(mvc.perform(withCsrf(post("/api/admin/ads/takedown"), cookie).header("Accept-Language", "en")
                .content("{\"ids\":[\"" + running + "\"],\"reason\":\"again\"}")).andExpect(status().isOk()));
        assertThat(en.get("takenDown").asInt()).isZero();
        assertThat(en.get("failed").get(0).get("message").asString()).doesNotContain("신청");
    }
}
