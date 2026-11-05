package com.community.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.commerce.CommerceApiTestBase;
import com.community.emoticon.dto.request.EmoticonReviewDecisionRequest;
import com.community.emoticon.dto.request.EmoticonStudioSaveRequest;
import com.community.emoticon.service.EmoticonReviewService;
import com.community.emoticon.service.EmoticonStudioService;
import com.community.user.entity.Role;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 탈퇴 작가 미확정 정리(사용자 지시 2026-11-05 「미확정까지 적용해」, V148) — 실제 탈퇴 API({@code GET·POST /api/me/withdrawal}) + 앱 문맥 + PostgreSQL.
 * 탈퇴 화면의 작가 요약(비작가 null · 작가 금액) · 정산 정보 없이 수익이 남으면 409(최상위 금액, 회원·신청 줄 그대로) · 포기 동의 금액이 다르면 400 · 동의하면 즉시 완료되고 정산이 포기로 끝남
 * (신청 줄 · 탈퇴 감사) · 정산 정보가 있으면 그대로 진행(정산은 다음 확정에서 지급).
 */
class WithdrawalEmoticonEarningsHttpTest extends CommerceApiTestBase {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int PRICE = 2500;
    /** 2,500 → 정산 기준 2,272 → 수수료 30% 뒤 작가 몫 1,590(최소 지급액 10,000 미만이어도 탈퇴하면 받을 돈) */
    private static final long SHARE = 1590;

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private EmoticonStudioService studio;
    @Autowired
    private EmoticonReviewService reviews;
    @Autowired
    private PasswordEncoder encoder;

    private MockMvc mvc;
    private Map<String, Object> settingsWas;
    private long operator;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        operator = newOperator();
        settingsWas = jdbc.queryForMap("SELECT fee_rate_percent, payout_minimum FROM emoticon_settings WHERE id = 1");
        jdbc.update("UPDATE emoticon_settings SET fee_rate_percent = 30, payout_minimum = 10000 WHERE id = 1");
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE emoticon_settings SET fee_rate_percent = ?, payout_minimum = ? WHERE id = 1", settingsWas.get("fee_rate_percent"), settingsWas.get("payout_minimum"));
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private long member() {
        long id = newMember();
        WithdrawalTestSupport.withPassword(jdbc, encoder, id, clock.instant());
        return id;
    }

    private static <B extends org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<B>> B as(long memberId, B request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).header("Accept-Language", "ko");
    }

    private long image(long creator, String kind, String format, int frames) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO emoticon_images (id, kind, format, width, height, bytes, frames, loop_forever, content_hash, static_path, uploaded_by_user, created_at) "
                + "VALUES (?, ?, ?, 200, 200, 1000, ?, true, lpad(to_hex(?::bigint), 64, '0'), '/emoticons/test.gif', ?, now())", id, kind, format, frames, id, creator);
        return id;
    }

    /** 상점에 나온 작가 팩(심사 승인) — 팩 ID */
    private long pack(long creator) {
        List<EmoticonStudioSaveRequest.Item> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            items.add(new EmoticonStudioSaveRequest.Item(Long.toString(image(creator, "EMOTICON", "GIF", 2)), "표" + i));
        }
        EmoticonStudioSaveRequest body = new EmoticonStudioSaveRequest("탈퇴 수익 팩", "설명", List.of("시험"), Long.toString(image(creator, "COVER", "PNG", 1)), items, "FREE",
                null, true, true, true, null);
        long packId = Long.parseLong(tx.execute(s -> studio.submit(creator, null, body)).id());
        String review = jdbc.queryForObject("SELECT id FROM emoticon_reviews WHERE pack_id = ?", Long.class, packId).toString();
        tx.executeWithoutResult(s -> reviews.approve(operator, "127.0.0.1", review, new EmoticonReviewDecisionRequest(0, null)));
        return packId;
    }

    /** 석 달 전 달의 결제된 판매 한 건 */
    private void sale(long creator, long pack) {
        YearMonth month = YearMonth.now(SEOUL).minusMonths(3);
        Instant paidAt = LocalDateTime.of(month.getYear(), month.getMonthValue(), 10, 12, 0).atZone(SEOUL).toInstant();
        long purchase = ids.nextId();
        long order = ids.nextId();
        long buyer = ids.nextId();
        jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, status, expires_at, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ROBOTIKON_PACK', ?, '시험', ?, 'READY', ?, ?, ?)", order, "pay_" + String.format("%032d", order).substring(0, 32), buyer, purchase, PRICE,
                Timestamp.from(paidAt.plus(Duration.ofMinutes(30))), Timestamp.from(paidAt), Timestamp.from(paidAt));
        jdbc.update("INSERT INTO emoticon_purchases (id, user_id, pack_id, creator_id, pack_name, amount, status, refund_notice_agreed_at, expires_at, created_at, "
                + "payment_order_id, paid_at, refund_until) VALUES (?, ?, ?, ?, '산 때 이름', ?, 'PAID', ?, ?, ?, ?, ?, ?)", purchase, buyer, pack, creator, PRICE,
                Timestamp.from(paidAt), Timestamp.from(paidAt.plus(Duration.ofMinutes(30))), Timestamp.from(paidAt), order, Timestamp.from(paidAt),
                Timestamp.from(paidAt.plus(Duration.ofDays(7))));
    }

    /** 판매가 있는 작가(정산 정보 없음) */
    private long sellingCreator() {
        long creator = member();
        sale(creator, pack(creator));
        return creator;
    }

    private void payout(long creator) {
        jdbc.update("INSERT INTO emoticon_payout_accounts (user_id, payee_type, bank_code, account_number_enc, account_last4, holder_name_enc, holder_masked, created_at, "
                + "updated_at) VALUES (?, 'INDIVIDUAL', 'KB', ?, '1234', ?, '홍*동', now(), now())", creator, WithdrawalTestSupport.CIPHER, WithdrawalTestSupport.CIPHER);
    }

    private ResultActions withdraw(long member, String extra) throws Exception {
        return mvc.perform(as(member, post("/api/me/withdrawal")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + WithdrawalTestSupport.PASSWORD + "\"" + extra + "}"));
    }

    private String userStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, id);
    }

    private List<String> settlementStatuses(long creator) {
        return jdbc.queryForList("SELECT status FROM emoticon_settlements WHERE creator_id = ? ORDER BY sales_month", String.class, creator);
    }

    // ── 시험 ────────────────────────────────────────────────────────────────

    @Test
    void 탈퇴_화면은_작가가_아니면_작가_요약이_없고_작가면_판매_중_팩_남은_수익_정산_정보를_싣는다() throws Exception {
        long plain = member();
        mvc.perform(as(plain, get("/api/me/withdrawal"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.emoticonCreator").doesNotExist());

        long creator = sellingCreator();
        mvc.perform(as(creator, get("/api/me/withdrawal"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.emoticonCreator.onSalePacks").value(1))
                .andExpect(jsonPath("$.data.emoticonCreator.pendingReviews").value(0)).andExpect(jsonPath("$.data.emoticonCreator.unpaidEarnings").value(SHARE))
                .andExpect(jsonPath("$.data.emoticonCreator.payoutAccountRegistered").value(false))
                .andExpect(jsonPath("$.data.emoticonCreator.decisionRequired").value(true));
        payout(creator);
        mvc.perform(as(creator, get("/api/me/withdrawal"))).andExpect(jsonPath("$.data.emoticonCreator.payoutAccountRegistered").value(true))
                .andExpect(jsonPath("$.data.emoticonCreator.decisionRequired").value(false)).andExpect(jsonPath("$.data.emoticonCreator.unpaidEarnings").value(SHARE));
    }

    @Test
    void 정산_정보_없이_수익이_남은_작가는_포기_동의_없이는_409_로_막히고_금액이_다르면_400() throws Exception {
        long creator = sellingCreator();

        withdraw(creator, "").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("WITHDRAWAL_EMOTICON_EARNINGS_UNPAID"))
                .andExpect(jsonPath("$.error.unpaidEarnings").value(SHARE)).andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("1,590")));
        withdraw(creator, ",\"waiveEmoticonEarnings\":false").andExpect(status().isConflict());
        // 409 는 본인 확인 앞 — 비밀번호가 틀려도 같은 409(시도 횟수를 쓰지 않는다)
        mvc.perform(as(creator, post("/api/me/withdrawal")).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("WITHDRAWAL_EMOTICON_EARNINGS_UNPAID"));
        // 포기를 골랐는데 금액이 없거나 다르면 400 fields.forfeitConsent(금액이 바뀌었으니 다시 보라)
        withdraw(creator, ",\"waiveEmoticonEarnings\":true").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.forfeitConsent").exists());
        withdraw(creator, ",\"waiveEmoticonEarnings\":true,\"forfeitConsent\":{\"emoticonEarnings\":" + (SHARE - 1) + "}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.forfeitConsent").exists());

        assertThat(userStatus(creator)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_withdrawals WHERE user_id = ?", Integer.class, creator)).isZero();
        assertThat(settlementStatuses(creator)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM emoticon_packs WHERE creator_id = ?", String.class, creator)).isEqualTo("APPROVED");
    }

    @Test
    void 포기에_동의하면_즉시_완료되고_정산이_포기로_끝나며_신청_줄과_탈퇴_감사에_동의_금액이_남는다() throws Exception {
        long creator = sellingCreator();

        withdraw(creator, ",\"waiveEmoticonEarnings\":true,\"forfeitConsent\":{\"emoticonEarnings\":" + SHARE + "}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("COMPLETED"));

        assertThat(userStatus(creator)).isEqualTo("WITHDRAWN");
        assertThat(settlementStatuses(creator)).containsExactly("WAIVED");
        Map<String, Object> event = jdbc.queryForMap("SELECT amount, consented_at, operator_id FROM emoticon_settlement_events WHERE creator_id = ? AND action = 'WAIVED'",
                creator);
        assertThat(event.get("amount")).isEqualTo(SHARE);
        assertThat(event.get("operator_id")).isNull();
        Map<String, Object> row = jdbc.queryForMap("SELECT forfeit_emoticon_earnings, forfeit_consented_at, requested_at FROM member_withdrawals WHERE user_id = ?", creator);
        assertThat(row.get("forfeit_emoticon_earnings")).isEqualTo(SHARE);
        assertThat(row.get("forfeit_consented_at")).isEqualTo(row.get("requested_at"));
        assertThat(((Timestamp) event.get("consented_at")).toInstant()).isEqualTo(((Timestamp) row.get("forfeit_consented_at")).toInstant());
        // 탈퇴 감사(돈 관련) — 신청 · 완료 모두 동의 금액, 완료는 정리 결과
        String requested = (String) lastAudit("MEMBER", Long.toString(creator), "MEMBER_WITHDRAW_REQUEST").get("detail");
        assertThat(((Number) JsonPath.read(requested, "$.forfeitEmoticonEarnings")).longValue()).isEqualTo(SHARE);
        String completed = (String) lastAudit("MEMBER", Long.toString(creator), "MEMBER_WITHDRAW").get("detail");
        assertThat(((Number) JsonPath.read(completed, "$.forfeitEmoticonEarnings")).longValue()).isEqualTo(SHARE);
        assertThat((String) JsonPath.read(completed, "$.emoticonSettlement")).isEqualTo("WAIVED");
        assertThat(((Number) JsonPath.read(completed, "$.emoticonSettlementAmount")).longValue()).isEqualTo(SHARE);
        String month = YearMonth.now(SEOUL).minusMonths(3).toString().replace("-", "");
        Map<String, Object> waive = lastAudit("EMOTICON_SETTLEMENT", month + "-" + creator, "EMOTICON_SETTLEMENT_WAIVE");
        assertThat(waive).isNotNull().containsEntry("actor_type", "SYSTEM");
        assertThat((String) JsonPath.read((String) waive.get("detail"), "$.basis")).isEqualTo("MEMBER_CONSENT");
        // 판매 수익이 있던 작가는 직접 결제한 적이 없어도 거래 이력이 있다 — 연락용 이메일을 분리 보관한다(분쟁·정정 연락)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM withdrawn_member_contacts WHERE user_id = ?", Integer.class, creator)).isEqualTo(1);
        // 팩은 후속 결정 6 그대로 새 판매만 멈춘다
        assertThat(jdbc.queryForObject("SELECT suspend_kind FROM emoticon_packs WHERE creator_id = ?", String.class, creator)).isEqualTo("CREATOR_WITHDRAWN");
    }

    @Test
    void 정산_정보가_있으면_포기_동의_없이_그대로_진행하고_정산은_다음_확정에서_지급한다() throws Exception {
        long creator = sellingCreator();
        payout(creator);
        // 포기 칸을 보내도 필요 없는 상황이면 무시한다
        withdraw(creator, ",\"waiveEmoticonEarnings\":true").andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("COMPLETED"));

        assertThat(userStatus(creator)).isEqualTo("WITHDRAWN");
        assertThat(settlementStatuses(creator)).as("줄을 만들지 않는다 — 예정 그대로").isEmpty();
        assertThat(jdbc.queryForObject("SELECT forfeit_emoticon_earnings FROM member_withdrawals WHERE user_id = ?", Long.class, creator)).isNull();
        String completed = (String) lastAudit("MEMBER", Long.toString(creator), "MEMBER_WITHDRAW").get("detail");
        assertThat((String) JsonPath.read(completed, "$.emoticonSettlement")).isEqualTo("PAYABLE");
        assertThat(((Number) JsonPath.read(completed, "$.emoticonSettlementAmount")).longValue()).isEqualTo(SHARE);

        // 남은 수익이 없는 작가(무료 팩만)도 그대로
        long free = member();
        pack(free);
        withdraw(free, "").andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("COMPLETED"));
        assertThat((String) JsonPath.read((String) lastAudit("MEMBER", Long.toString(free), "MEMBER_WITHDRAW").get("detail"), "$.emoticonSettlement"))
                .isEqualTo("NONE");
    }
}
