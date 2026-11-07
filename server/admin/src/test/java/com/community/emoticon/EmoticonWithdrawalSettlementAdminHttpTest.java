package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.emoticon.dto.request.EmoticonReviewDecisionRequest;
import com.community.emoticon.dto.request.EmoticonStudioSaveRequest;
import com.community.emoticon.service.EmoticonReviewService;
import com.community.emoticon.service.EmoticonSettlementService;
import com.community.emoticon.service.EmoticonStudioService;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 탈퇴 작가 미확정 정리(V148) — 관리자 쪽: 운영자 강제 탈퇴(포기 동의를 받을 수 없다)는 정산 정보 없이 남은 수익을 시스템 보류로 남기고, 운영자가 사유와 함께 포기로 끝낸다
 * ({@code POST …/settlements/{id}/waive} — 권한 {@code emoticon.settlement} · 사유 400 · 409 · 감사) · 심사 목록 줄의 {@code cause}. 실제 필터 체인·세션·권한·PostgreSQL.
 */
class EmoticonWithdrawalSettlementAdminHttpTest extends Commerce04AdminTestBase {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int PRICE = 2500;
    private static final long SHARE = 1590;
    private static final String MEMO = "운영 정책 위반으로 강제 탈퇴 처리";

    @Autowired
    private EmoticonStudioService studio;
    @Autowired
    private EmoticonReviewService reviews;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private com.community.holiday.service.SiteHolidayService holidays;

    private Map<String, Object> settingsWas;

    @BeforeEach
    void settings() {
        settingsWas = jdbc.queryForMap("SELECT fee_rate_percent, payout_minimum FROM emoticon_settings WHERE id = 1");
        jdbc.update("UPDATE emoticon_settings SET fee_rate_percent = 30, payout_minimum = 10000 WHERE id = 1");
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE emoticon_settings SET fee_rate_percent = ?, payout_minimum = ? WHERE id = 1", settingsWas.get("fee_rate_percent"), settingsWas.get("payout_minimum"));
    }

    private long member() {
        long id = newMember();
        memberFixture.verifyEmail(id);
        return id;
    }

    private long image(long creator, String kind, String format, int frames) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO emoticon_images (id, kind, format, width, height, bytes, frames, loop_forever, content_hash, static_path, uploaded_by_user, created_at) "
                + "VALUES (?, ?, ?, 200, 200, 1000, ?, true, lpad(to_hex(?::bigint), 64, '0'), '/emoticons/test.gif', ?, now())", id, kind, format, frames, id, creator);
        return id;
    }

    private long submitted(long creator) {
        List<EmoticonStudioSaveRequest.Item> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            items.add(new EmoticonStudioSaveRequest.Item(Long.toString(image(creator, "EMOTICON", "GIF", 2)), "표" + i));
        }
        EmoticonStudioSaveRequest body = new EmoticonStudioSaveRequest("강제 탈퇴 팩", "설명", List.of("시험"), Long.toString(image(creator, "COVER", "PNG", 1)), items, "FREE",
                null, true, true, true, null);
        return Long.parseLong(new TransactionTemplate(transactions).execute(s -> studio.submit(creator, null, body)).id());
    }

    private String reviewOf(long pack) {
        return jdbc.queryForObject("SELECT id FROM emoticon_reviews WHERE pack_id = ? ORDER BY submitted_at DESC LIMIT 1", Long.class, pack).toString();
    }

    private long approved(long creator, long operator) {
        long pack = submitted(creator);
        new TransactionTemplate(transactions).executeWithoutResult(s -> reviews.approve(operator, "127.0.0.1", reviewOf(pack), new EmoticonReviewDecisionRequest(0, null)));
        return pack;
    }

    private void sale(long creator, long pack, Instant paidAt) {
        long purchase = ids.nextId();
        long order = ids.nextId();
        long buyer = ids.nextId();
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, status, expires_at, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ROBOTIKON_PACK', ?, '시험', ?, 'READY', ?, ?, ?)", order, "pay_" + String.format("%032d", order).substring(0, 32), buyer, purchase, PRICE,
                Timestamp.from(paidAt.plus(Duration.ofMinutes(30))), Timestamp.from(paidAt), Timestamp.from(paidAt));
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("INSERT INTO emoticon_purchases (id, user_id, pack_id, creator_id, pack_name, amount, status, refund_notice_agreed_at, expires_at, created_at, "
                + "payment_order_id, paid_at, refund_until) VALUES (?, ?, ?, ?, '산 때 이름', ?, 'PAID', ?, ?, ?, ?, ?, ?)", purchase, buyer, pack, creator, PRICE,
                Timestamp.from(paidAt), Timestamp.from(paidAt.plus(Duration.ofMinutes(30))), Timestamp.from(paidAt), order, Timestamp.from(paidAt),
                Timestamp.from(paidAt.plus(Duration.ofDays(7))));
    }

    @Test
    void 운영자_강제_탈퇴는_남은_수익을_시스템_보류로_남기고_운영자가_사유와_함께_포기로_끝낸다() throws Exception {
        long setup = operatorWith(Permission.EMOTICON_READ, Permission.EMOTICON_REVIEW);
        long creator = member();
        long pack = approved(creator, setup);
        YearMonth month = YearMonth.now(SEOUL).minusMonths(3);
        sale(creator, pack, LocalDateTime.of(month.getYear(), month.getMonthValue(), 10, 12, 0).atZone(SEOUL).toInstant());
        String id = month.toString().replace("-", "") + "-" + creator;
        String base = "/api/admin/emoticons/settlements";

        // 운영자 강제 탈퇴(실제 API) — 포기 동의를 받을 수 없으니 보류로 남는다
        Cookie force = login(operatorWith(Permission.MEMBER_WITHDRAW));
        mvc.perform(withCsrf(post("/api/admin/members/" + creator + "/withdrawal"), force).content("{\"reasonKind\":\"POLICY_VIOLATION\",\"memo\":\"" + MEMO + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertThat(memberFixture.status(creator)).isEqualTo("WITHDRAWN");
        Map<String, Object> row = jdbc.queryForMap("SELECT status, hold_reason, held_by FROM emoticon_settlements WHERE creator_id = ?", creator);
        assertThat(row).containsEntry("status", "HELD").containsEntry("hold_reason", EmoticonSettlementService.WITHDRAWN_HOLD_REASON);
        assertThat(row.get("held_by")).isNull();
        // 운영자가 연락할 곳 — 판매 수익이 있던 작가는 연락용 이메일을 분리 보관한다(직접 결제한 적이 없어도)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM withdrawn_member_contacts WHERE user_id = ?", Integer.class, creator)).isEqualTo(1);
        String completed = auditLogs.where().action("MEMBER_WITHDRAW").target(Long.toString(creator)).lastById().orElseThrow().detail();
        assertThat(completed).contains("\"emoticonSettlement\":\"HELD\"").contains("\"emoticonSettlementAmount\":" + SHARE).contains("\"forfeitEmoticonEarnings\":0");

        Cookie s = login(operatorWith(Permission.EMOTICON_SETTLEMENT));
        mvc.perform(get(base + "/" + id).cookie(s)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("HELD"))
                .andExpect(jsonPath("$.data.hold.reason").value(EmoticonSettlementService.WITHDRAWN_HOLD_REASON)).andExpect(jsonPath("$.data.hold.by").doesNotExist())
                .andExpect(jsonPath("$.data.creator.withdrawn").value(true)).andExpect(jsonPath("$.data.payoutAmount").value(SHARE - 50))
                .andExpect(jsonPath("$.data.waivedAmount").value(0));

        // 포기 처리 — 권한 403 → 사유 400 → 404 → 처리 → 다시 하면 409
        mvc.perform(withCsrf(post(base + "/" + id + "/waive"), login(operatorWith(Permission.EMOTICON_SALES))).content("{\"reason\":\"연락이 닿지 않아 포기 처리\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf(post(base + "/" + id + "/waive"), s).content("{\"reason\":\"짧음\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.reason").exists());
        mvc.perform(withCsrf(post(base + "/202001-" + creator + "/waive"), s).content("{\"reason\":\"연락이 닿지 않아 포기 처리\"}")).andExpect(status().isNotFound());
        mvc.perform(withCsrf(post(base + "/" + id + "/waive"), s).content("{\"reason\":\"연락이 닿지 않아 포기 처리\"}")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.status").value("WAIVED"))
                .andExpect(jsonPath("$.data.waivedAmount").value(SHARE)).andExpect(jsonPath("$.data.payoutAmount").value(0))
                .andExpect(jsonPath("$.data.carriedOut").value(0)).andExpect(jsonPath("$.data.events[0].action").value("WAIVED"))
                .andExpect(jsonPath("$.data.events[0].amount").value(SHARE)).andExpect(jsonPath("$.data.events[0].note").value("연락이 닿지 않아 포기 처리"))
                .andExpect(jsonPath("$.data.events[0].consentedAt").doesNotExist()).andExpect(jsonPath("$.data.events[0].by").isNotEmpty());
        mvc.perform(withCsrf(post(base + "/" + id + "/waive"), s).header("Accept-Language", "ko").content("{\"reason\":\"연락이 닿지 않아 포기 처리\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_SETTLEMENT_NOT_WAIVABLE"))
                .andExpect(jsonPath("$.error.message").value("정산 정보 없이 탈퇴한 작가의 보류 정산만 포기로 끝낼 수 있어요."));
        Map<String, Object> audit = auditLogs.where().action("EMOTICON_SETTLEMENT_WAIVE").target(id).lastById().orElseThrow().asMap();
        assertThat(audit).containsEntry("actor_type", "OPERATOR").containsEntry("retention_class", "MONEY");
        assertThat((String) audit.get("detail")).contains("\"basis\":\"OPERATOR\"").contains("연락이 닿지 않아 포기 처리");
        // 목록 — 포기한 금액 합계
        mvc.perform(get(base).param("month", month.toString()).cookie(s)).andExpect(jsonPath("$.data.items[?(@.id == '" + id + "')].status").value("WAIVED"))
                .andExpect(jsonPath("$.data.totals.waivedAmount").isNumber());
    }

    /** 오늘(서비스 시간대) 이전의 가장 가까운 업무일 */
    private java.time.LocalDate businessDay() {
        java.time.LocalDate d = java.time.LocalDate.now(SEOUL);
        while (!holidays.calendar().businessDay(d)) {
            d = d.minusDays(1);
        }
        return d;
    }

    private static String manualPaid(String date, String memo, String reason) {
        StringBuilder b = new StringBuilder("{");
        if (date != null) {
            b.append("\"transferDate\":\"").append(date).append("\",");
        }
        if (memo != null) {
            b.append("\"memo\":\"").append(memo).append("\",");
        }
        if (reason != null) {
            b.append("\"reason\":\"").append(reason).append("\",");
        }
        if (b.length() > 1) {
            b.setLength(b.length() - 1);
        }
        return b.append("}").toString();
    }

    @Test
    void 강제_탈퇴_미리_보기에_작가_요약이_있고_보류로_남은_수익을_수동_지급_기록으로_끝낸다() throws Exception {
        long setup = operatorWith(Permission.EMOTICON_READ, Permission.EMOTICON_REVIEW);
        long creator = member();
        long pack = approved(creator, setup);
        submitted(creator); // 심사 대기 1건
        YearMonth month = YearMonth.now(SEOUL).minusMonths(3);
        sale(creator, pack, LocalDateTime.of(month.getYear(), month.getMonthValue(), 10, 12, 0).atZone(SEOUL).toInstant());
        String id = month.toString().replace("-", "") + "-" + creator;
        String base = "/api/admin/emoticons/settlements";

        // 강제 탈퇴 미리 보기 — 사이트 탈퇴 미리 보기와 같은 작가 요약(원천징수 전 · 최소 지급액과 상관없이) · 작가가 아니면 없다
        Cookie force = login(operatorWith(Permission.MEMBER_WITHDRAW));
        mvc.perform(get("/api/admin/members/" + creator + "/withdrawal/preview").cookie(force)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emoticonCreator.onSalePacks").value(1)).andExpect(jsonPath("$.data.emoticonCreator.pendingReviews").value(1))
                .andExpect(jsonPath("$.data.emoticonCreator.unpaidEarnings").value(SHARE))
                .andExpect(jsonPath("$.data.emoticonCreator.payoutAccountRegistered").value(false));
        String plain = mvc.perform(get("/api/admin/members/" + member() + "/withdrawal/preview").cookie(force)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        Map<String, Object> plainData = com.jayway.jsonpath.JsonPath.read(plain, "$.data");
        assertThat(plainData.get("emoticonCreator")).as("작가가 아님(빠지거나 null)").isNull();

        mvc.perform(withCsrf(post("/api/admin/members/" + creator + "/withdrawal"), force).content("{\"reasonKind\":\"POLICY_VIOLATION\",\"memo\":\"" + MEMO + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertThat(jdbc.queryForObject("SELECT status FROM emoticon_settlements WHERE creator_id = ?", String.class, creator)).isEqualTo("HELD");

        // 수동 지급 기록 — 권한 403 → 입력 400(이체일·메모·사유 함께) → 토요일 400 → 404 → 처리 → 다시 하면 409
        String date = businessDay().toString();
        mvc.perform(withCsrf(post(base + "/" + id + "/manual-paid"), login(operatorWith(Permission.EMOTICON_SALES))).content(manualPaid(date, null, "은행에서 직접 보냈어요")))
                .andExpect(status().isForbidden());
        Cookie s = login(operatorWith(Permission.EMOTICON_SETTLEMENT));
        mvc.perform(withCsrf(post(base + "/" + id + "/manual-paid"), s).content(manualPaid(null, "가".repeat(201), "짧음"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.transferDate").exists()).andExpect(jsonPath("$.error.fields.memo").exists())
                .andExpect(jsonPath("$.error.fields.reason").exists());
        java.time.LocalDate saturday = java.time.LocalDate.now(SEOUL).minusDays(1);
        while (saturday.getDayOfWeek() != java.time.DayOfWeek.SATURDAY) {
            saturday = saturday.minusDays(1);
        }
        String saturdayText = saturday.toString();
        mvc.perform(withCsrf(post(base + "/" + id + "/manual-paid"), s).content(manualPaid(saturdayText, null, "은행에서 직접 보냈어요")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.transferDate").exists());
        mvc.perform(withCsrf(post(base + "/202001-" + creator + "/manual-paid"), s).content(manualPaid(date, null, "은행에서 직접 보냈어요")))
                .andExpect(status().isNotFound());
        mvc.perform(withCsrf(post(base + "/" + id + "/manual-paid"), s).content(manualPaid(date, "국민은행 이체", "은행에서 직접 보냈어요"))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.payoutAmount").value(SHARE - 50)).andExpect(jsonPath("$.data.withholdingAmount").value(50))
                .andExpect(jsonPath("$.data.payeeType").value("INDIVIDUAL")).andExpect(jsonPath("$.data.paid.transferDate").value(date))
                .andExpect(jsonPath("$.data.paid.memo").value("국민은행 이체")).andExpect(jsonPath("$.data.hold").doesNotExist())
                .andExpect(jsonPath("$.data.account").doesNotExist()).andExpect(jsonPath("$.data.events[0].action").value("MANUAL_PAID"))
                .andExpect(jsonPath("$.data.events[0].amount").value(SHARE - 50)).andExpect(jsonPath("$.data.events[0].note").value("은행에서 직접 보냈어요"))
                .andExpect(jsonPath("$.data.events[0].by").isNotEmpty());
        mvc.perform(withCsrf(post(base + "/" + id + "/manual-paid"), s).header("Accept-Language", "ko").content(manualPaid(date, null, "은행에서 직접 보냈어요")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_SETTLEMENT_NOT_MANUAL_PAYABLE"))
                .andExpect(jsonPath("$.error.message").value("정산 정보 없이 탈퇴한 작가의 보류 정산만, 셀프 환불 기간이 지난 뒤 수동 지급으로 기록할 수 있어요."));
        mvc.perform(withCsrf(post(base + "/" + id + "/manual-paid"), s).header("Accept-Language", "en").content(manualPaid(date, null, "은행에서 직접 보냈어요")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.startsWith("Only held settlements")));
        // 계좌는 저장하지 않았다 · 감사(운영자 · 돈 관련 5년)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM emoticon_payout_accounts WHERE user_id = ?", Integer.class, creator)).isZero();
        Map<String, Object> audit = auditLogs.where().action("EMOTICON_SETTLEMENT_MANUAL_PAID").target(id).lastById().orElseThrow().asMap();
        assertThat(audit).containsEntry("actor_type", "OPERATOR").containsEntry("retention_class", "MONEY");
        assertThat((String) audit.get("detail")).contains("\"to\":\"PAID\"").contains("\"transferDate\":\"" + date + "\"").contains("은행에서 직접 보냈어요");
    }

    @Test
    void 심사_목록_줄에_처리_까닭이_있다_대기는_null() throws Exception {
        Cookie reviewer = login(operatorWith(Permission.EMOTICON_READ, Permission.EMOTICON_REVIEW));
        long creator = member();
        long pending = submitted(creator);
        long rejected = submitted(member());
        mvc.perform(withCsrf(post("/api/admin/emoticons/reviews/" + reviewOf(rejected) + "/reject"), reviewer).content("{\"reason\":\"그림 규격이 맞지 않아요\"}"))
                .andExpect(status().isNoContent());
        String pendingReview = reviewOf(pending);
        String pendingList = mvc.perform(get("/api/admin/emoticons/reviews").cookie(reviewer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + pendingReview + "')].status").value("PENDING")).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        List<Object> causes = com.jayway.jsonpath.JsonPath.read(pendingList, "$.data.items[?(@.id == '" + pendingReview + "')].cause");
        assertThat(causes).as("대기 중이면 null(빠지거나 null)").allMatch(java.util.Objects::isNull);
        mvc.perform(get("/api/admin/emoticons/reviews").param("status", "REJECTED").cookie(reviewer))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + reviewOf(rejected) + "')].cause").value("OPERATOR"));
        // 작가 탈퇴(강제) — 대기 건이 시스템 반려로 닫힌다
        mvc.perform(withCsrf(post("/api/admin/members/" + creator + "/withdrawal"), login(operatorWith(Permission.MEMBER_WITHDRAW)))
                .content("{\"reasonKind\":\"POLICY_VIOLATION\",\"memo\":\"" + MEMO + "\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/emoticons/reviews").param("status", "REJECTED").cookie(reviewer))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + pendingReview + "')].cause").value("CREATOR_WITHDRAWN"));
    }
}
