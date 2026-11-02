package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.bizapplication.dto.request.BizAdApplyRequest;
import com.community.bizapplication.service.BizApplicationMemberService;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 관리자 광고주 대신 정지·다시 켜기·중단(V127 — biz-pause-contract 규칙 11)과 「광고 기본 설정」 중도 해지 수수료율(규칙 7) — 실제 관리자 앱 문맥 + PostgreSQL.
 * 권한 = 건의 도메인 처리 권한(광고 {@code ad.manage}), 다른 도메인 403 · 요청 경로·메모 400 · 응답 칸({@code takedownCause}·{@code pause}·{@code takedown.channel/memo/by}·
 * 환불 내역) · 일정표 줄·상세 · 감사 · 수수료율 0~30 검사·저장·이력·감사.
 */
class BizPauseAdminHttpTest extends Commerce04AdminTestBase {

    private static final String PLACEMENTS = "{\"postInline\":{\"enabled\":true,\"adType\":\"TEXT_LINE\",\"position\":\"BETWEEN_CARDS\"},"
            + "\"wings\":{\"left\":true,\"right\":true,\"screens\":[\"HOME\",\"PAGE\",\"BOARD\",\"POST\"]},\"boardList\":{\"enabled\":true,\"after\":5},"
            + "\"adult\":{\"enabled\":false}";

    @Autowired
    private BizApplicationMemberService members;

    @AfterEach
    void resetSettings() {
        jdbc.update("DELETE FROM ad_placement_settings");
    }

    private long approvedBusiness() {
        long user = newMember();
        long profile = ids.nextId();
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, re_review, company_name, biz_no_enc, biz_no_hash, biz_no_last4, owner_name_enc, phone_enc, email_enc, "
                + "postcode, address1_enc, terms_version, privacy_version, agreed_at, submitted_at, created_at, updated_at) "
                + "VALUES (?, ?, 'APPROVED', false, '정지상사', 'x', ?, '1234', 'x', 'x', 'x', '06236', 'x', 'v1', 'v1', now(), now(), now(), now())", profile, user,
                String.format("%064x", profile));
        for (String service : new String[] {"AD", "HOTDEAL"}) {
            jdbc.update("INSERT INTO business_services (id, profile_id, service, status, updated_at) VALUES (?, ?, ?, 'APPROVED', now())", ids.nextId(), profile, service);
        }
        return user;
    }

    /** 진행 중인 수동 결제 한 줄 광고(입금 확인 11,000원 — 시작 하루 전 ~ 이틀 뒤). 승인·확정 흐름은 다른 시험이 막으므로 상태만 맞춘다 */
    private long running() {
        long user = approvedBusiness();
        LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(3);
        long id = members.submitAd(user, new BizAdApplyRequest("TEXT_LINE", start.toString(), start.plusDays(2).toString(), null, "https://a.example.com", null,
                "정지 시험 한 줄", "MANUAL", "v1", BizApplicationMemberService.REFUND_POLICY_VERSION));
        long op = operatorWith();
        jdbc.update("UPDATE biz_applications SET status = 'SCHEDULED', payment_method = 'MANUAL', approved_supply = 10000, approved_vat = 1000, approved_total = 11000, "
                + "approved_at = now(), approved_by = ?, active_contract_count = 1, deposit_confirmed_at = now(), "
                + "start_at = date_trunc('minute', now()) - interval '1 day', end_at = date_trunc('minute', now()) + interval '2 day' WHERE id = ?", op, id);
        jdbc.update("UPDATE biz_application_items SET start_at = date_trunc('minute', now()) - interval '1 day', end_at = date_trunc('minute', now()) + interval '2 day' "
                + "WHERE application_id = ?", id);
        jdbc.update("INSERT INTO biz_deposits (application_id, amount, deposited_on, depositor_name, confirmed_by, confirmed_at) VALUES (?, 11000, current_date, '정지상사', ?, now())",
                id, op);
        return id;
    }

    private static String action(String channel, String memo) {
        return "{\"channel\":" + (channel == null ? "null" : "\"" + channel + "\"") + ",\"memo\":" + (memo == null ? "null" : "\"" + memo + "\"") + "}";
    }

    @Test
    void 권한은_건의_도메인_처리_권한_ad_read_만이나_핫딜_운영자는_403() throws Exception {
        long id = running();
        Cookie reader = login(operatorWith(Permission.AD_READ));
        Cookie dealManager = login(operatorWith(Permission.HOTDEAL_READ, Permission.HOTDEAL_MANAGE));
        for (String path : new String[] {"pause", "resume", "stop"}) {
            mvc.perform(withCsrf(post("/api/admin/biz-applications/" + id + "/" + path), reader).content(action("PHONE", "요청"))).andExpect(status().isForbidden());
            mvc.perform(withCsrf(post("/api/admin/biz-applications/" + id + "/" + path), dealManager).content(action("PHONE", "요청")))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        }
        assertThat(jdbc.queryForObject("SELECT status FROM biz_applications WHERE id = ?", String.class, id)).isEqualTo("SCHEDULED");
    }

    @Test
    void 대신_정지_다시_켜기_중단의_검사와_응답_칸_일정표_감사() throws Exception {
        long id = running();
        long me = operatorWith(Permission.AD_READ, Permission.AD_MANAGE);
        Cookie manager = login(me);
        String base = "/api/admin/biz-applications/" + id;
        mvc.perform(withCsrf(post(base + "/pause"), manager).content(action(null, "메모"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.channel").exists());
        mvc.perform(withCsrf(post(base + "/pause"), manager).content(action("PHONE", ""))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.memo").exists());
        mvc.perform(withCsrf(post(base + "/pause"), manager)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.channel").exists()).andExpect(jsonPath("$.error.fields.memo").exists());

        mvc.perform(withCsrf(post(base + "/pause"), manager).content(action("PHONE", "광고주 전화"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TAKEN_DOWN")).andExpect(jsonPath("$.data.takedownCause").value("ADVERTISER_PAUSE"))
                .andExpect(jsonPath("$.data.takedown.cause").value("ADVERTISER_PAUSE")).andExpect(jsonPath("$.data.takedown.channel").value("PHONE"))
                .andExpect(jsonPath("$.data.takedown.memo").value("광고주 전화")).andExpect(jsonPath("$.data.takedown.by").value(operatorName(me)))
                .andExpect(jsonPath("$.data.pause.used").value(1)).andExpect(jsonPath("$.data.pause.limit").value(3))
                .andExpect(jsonPath("$.data.pause.byOperator").value(true)).andExpect(jsonPath("$.data.pause.actor").value(operatorName(me)))
                .andExpect(jsonPath("$.data.pause.channel").value("PHONE")).andExpect(jsonPath("$.data.pause.feeRate").value(10))
                .andExpect(jsonPath("$.data.pause.proratableTotal").value(11000))
                .andExpect(jsonPath("$.data.history[?(@.action == 'PAUSED')].note").value(org.hamcrest.Matchers.contains("PHONE · 광고주 전화")));
        // 광고 일정표 줄·상세
        mvc.perform(get("/api/admin/ads/" + id).cookie(manager)).andExpect(status().isOk()).andExpect(jsonPath("$.data.takedownCause").value("ADVERTISER_PAUSE"))
                .andExpect(jsonPath("$.data.takedown.channel").value("PHONE")).andExpect(jsonPath("$.data.takedown.pause.used").value(1))
                .andExpect(jsonPath("$.data.pause.byOperator").value(true));
        mvc.perform(get("/api/admin/ads").param("type", "TEXT_LINE").param("q", "").param("size", "100").cookie(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + id + "')].takedownCause").value(org.hamcrest.Matchers.contains("ADVERTISER_PAUSE")));
        // 되돌리기(보상 선택)는 긴급 내림만
        mvc.perform(withCsrf(post("/api/admin/ads/" + id + "/restore"), login(operatorWith(Permission.AD_READ, Permission.AD_RESTORE)))
                .content("{\"compensation\":\"EXTEND\",\"reason\":\"되돌리기\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BIZ_APPLICATION_STATE_CHANGED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE target_id = ? AND action = 'BIZ_APP_PAUSE'", Integer.class, Long.toString(id))).isOne();

        mvc.perform(withCsrf(post(base + "/resume"), manager).content(action("EMAIL", "메일로 재개"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.takedownCause").doesNotExist()).andExpect(jsonPath("$.data.takedown").doesNotExist())
                .andExpect(jsonPath("$.data.pause.used").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE target_id = ? AND action = 'BIZ_APP_RESUME'", Integer.class, Long.toString(id))).isOne();

        mvc.perform(withCsrf(post(base + "/stop"), manager).content("{\"channel\":\"OTHER\",\"memo\":\"방문 요청\",\"reason\":\"행사 취소\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.takedownCause").value("ADVERTISER_STOP")).andExpect(jsonPath("$.data.takedown.text").value("행사 취소"))
                .andExpect(jsonPath("$.data.hasRefundRequest").value(true)).andExpect(jsonPath("$.data.refund.type").value("STOP_PRORATED"))
                .andExpect(jsonPath("$.data.refund.status").value("REQUESTED")).andExpect(jsonPath("$.data.refund.feeRate").value(10))
                .andExpect(jsonPath("$.data.refund.proratedAmount").isNumber()).andExpect(jsonPath("$.data.refund.feeAmount").isNumber())
                .andExpect(jsonPath("$.data.refund.basisAt").isString()).andExpect(jsonPath("$.data.pause.stoppedAt").isString())
                .andExpect(jsonPath("$.data.pause.refundFrom").isString());
        long prorated = jdbc.queryForObject("SELECT prorated_amount FROM biz_refunds WHERE application_id = ? AND status = 'REQUESTED'", Long.class, id);
        long proposed = jdbc.queryForObject("SELECT proposed_amount FROM biz_refunds WHERE application_id = ? AND status = 'REQUESTED'", Long.class, id);
        assertThat(proposed).isEqualTo(prorated - Math.floorDiv(prorated * 10, 100));
        assertThat(jdbc.queryForObject("SELECT retention_class FROM audit_logs WHERE target_id = ? AND action = 'BIZ_APP_STOP'", String.class, Long.toString(id)))
                .isEqualTo("MONEY");
        // 환불 심사 큐에 든다(한 건 한 큐) — 줄에 내림 까닭
        mvc.perform(get("/api/admin/biz-applications").param("domain", "AD").param("queue", "REFUND").cookie(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + id + "')].takedownCause").value(org.hamcrest.Matchers.contains("ADVERTISER_STOP")));
        // 중단 건 환불 승인은 기존 deal-order.refund
        mvc.perform(withCsrf(post(base + "/refund/approve"), manager).content("{\"amount\":" + proposed + ",\"reason\":\"제안대로\"}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post(base + "/refund/approve"), login(operatorWith(Permission.DEAL_ORDER_REFUND))).content("{\"amount\":" + proposed + ",\"reason\":\"제안대로\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELED")).andExpect(jsonPath("$.data.takedownCause").value("ADVERTISER_STOP"));
    }

    private static final tools.jackson.databind.json.JsonMapper MAPPER = tools.jackson.databind.json.JsonMapper.builder().build();

    /** 광고 일정표 합계(그 유형의 끝나지 않은 장부 건) */
    private tools.jackson.databind.JsonNode summary(Cookie cookie) throws Exception {
        String month = java.time.YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
        String body = mvc.perform(get("/api/admin/ads/calendar").param("type", "TEXT_LINE").param("month", month).cookie(cookie)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return MAPPER.readTree(body).get("data").get("summary");
    }

    private static int[] counts(tools.jackson.databind.JsonNode s) {
        return new int[] {s.get("running").asInt(), s.get("paused").asInt(), s.get("takenDown").asInt()};
    }

    @Test
    void 일정표_합계는_자리를_차지한_정지_중과_그_밖의_내림을_따로_세고_환불_승인으로_취소되면_뺀다() throws Exception {
        long toPause = running();
        long toStop = running();
        long toTakedown = running();
        Cookie manager = login(operatorWith(Permission.AD_READ, Permission.AD_MANAGE, Permission.AD_TAKEDOWN));
        int[] base = counts(summary(manager)); // 다른 시험이 남긴 건이 있을 수 있어 차이로 본다

        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + toPause + "/pause"), manager).content(action("PHONE", "정지 요청"))).andExpect(status().isOk());
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + toStop + "/stop"), manager).content(action("EMAIL", "중단 요청"))).andExpect(status().isOk());
        mvc.perform(withCsrf(post("/api/admin/ads/" + toTakedown + "/takedown"), manager).content("{\"reason\":\"링크 사고\",\"notifyMail\":false}")).andExpect(status().isOk());
        int[] after = counts(summary(manager));
        assertThat(after[0]).as("진행 중").isEqualTo(base[0] - 3);
        assertThat(after[1]).as("정지 중 = 광고주 일시 정지").isEqualTo(base[1] + 1);
        assertThat(after[2]).as("그 밖의 내림 = 광고주 중단(환불 심사 중) + 긴급 내림").isEqualTo(base[2] + 2);

        // 중단 건 환불 승인 → CANCELED(자리 반납) → 합계에서 빠진다
        long proposed = jdbc.queryForObject("SELECT proposed_amount FROM biz_refunds WHERE application_id = ? AND status = 'REQUESTED'", Long.class, toStop);
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + toStop + "/refund/approve"), login(operatorWith(Permission.DEAL_ORDER_REFUND)))
                .content("{\"amount\":" + proposed + ",\"reason\":\"제안대로\"}")).andExpect(status().isOk());
        assertThat(counts(summary(manager))[2]).isEqualTo(base[2] + 1);
        // 다시 켜면 진행 중으로 돌아간다
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + toPause + "/resume"), manager).content(action("PHONE", "재개 요청"))).andExpect(status().isOk());
        int[] resumed = counts(summary(manager));
        assertThat(resumed[0]).as("중단·긴급 내림 두 건은 그대로 빠져 있다").isEqualTo(base[0] - 2);
        assertThat(resumed[1]).isEqualTo(base[1]);
    }

    @Test
    void 정지_횟수는_사업자와_운영자가_같이_세고_넘으면_409_BIZ_PAUSE_LIMIT() throws Exception {
        long id = running();
        jdbc.update("UPDATE biz_applications SET pause_count = 3 WHERE id = ?", id);
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + id + "/pause"), login(operatorWith(Permission.AD_READ, Permission.AD_MANAGE)))
                .content(action("PHONE", "네 번째"))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("BIZ_PAUSE_LIMIT"));
    }

    // ── 광고 기본 설정 — 중도 해지 수수료율 ────────────────────────────────

    @Test
    void 수수료율은_0에서_30_정수로_저장하고_빠지면_그대로_바뀐_칸은_이력과_감사에_남는다() throws Exception {
        jdbc.update("DELETE FROM ad_placement_settings");
        jdbc.update("DELETE FROM ad_placement_history");
        Cookie reader = login(operatorWith(Permission.AD_READ));
        long me = operatorWith(Permission.AD_READ, Permission.AD_SETTINGS);
        Cookie settings = login(me);
        mvc.perform(get("/api/admin/ads/placements").cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.refund.stopFeeRate").value(10));
        for (String bad : new String[] {"31", "-1", "null", "\"열\""}) {
            mvc.perform(withCsrf(put("/api/admin/ads/placements"), settings).content(PLACEMENTS + ",\"refund\":{\"stopFeeRate\":" + bad + "}}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(withCsrf(put("/api/admin/ads/placements"), settings).content(PLACEMENTS + ",\"refund\":{\"stopFeeRate\":31}}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.['refund.stopFeeRate']").exists()).andExpect(jsonPath("$.error.fields.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_placement_settings", Integer.class)).isZero();
        mvc.perform(withCsrf(put("/api/admin/ads/placements"), reader).content(PLACEMENTS + ",\"refund\":{\"stopFeeRate\":15}}")).andExpect(status().isForbidden());

        mvc.perform(withCsrf(put("/api/admin/ads/placements"), settings).content(PLACEMENTS + ",\"refund\":{\"stopFeeRate\":15}}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refund.stopFeeRate").value(15));
        assertThat(jdbc.queryForObject("SELECT stop_fee_rate FROM ad_placement_settings WHERE id = 1", Integer.class)).isEqualTo(15);
        // 환불 묶음을 빼면 그대로(화면이 붙기 전 요청)
        mvc.perform(withCsrf(put("/api/admin/ads/placements"), settings).content(PLACEMENTS.replace("\"after\":5", "\"after\":6") + "}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refund.stopFeeRate").value(15));
        mvc.perform(withCsrf(put("/api/admin/ads/placements"), settings).content(PLACEMENTS.replace("\"after\":5", "\"after\":6") + ",\"refund\":{\"stopFeeRate\":0}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.refund.stopFeeRate").value(0));
        String last = jdbc.queryForObject("SELECT detail::text FROM audit_logs WHERE actor_id = ? AND action = 'AD_PLACEMENT_UPDATE' ORDER BY occurred_at DESC, id DESC LIMIT 1",
                String.class, me);
        assertThat(last.replace(" ", "")).contains("\"before\":{\"refund.stopFeeRate\":15}", "\"after\":{\"refund.stopFeeRate\":0}");
        mvc.perform(get("/api/admin/ads/placements/history").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].action").value("UPDATE")).andExpect(jsonPath("$.data.items[0].changes.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].changes[0].field").value("refund.stopFeeRate"))
                .andExpect(jsonPath("$.data.items[0].changes[0].labelKey").value("settingHistory.field.refund.stopFeeRate"))
                .andExpect(jsonPath("$.data.items[0].changes[0].before").value("15")).andExpect(jsonPath("$.data.items[0].changes[0].after").value("0"));
    }
}
