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
import com.community.emoticon.service.EmoticonPayoutService;
import com.community.emoticon.service.EmoticonReviewService;
import com.community.emoticon.service.EmoticonStudioService;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.crypto.DataCipher;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 로보티콘 작가 정산(emoticon-contract S4 — 권한 {@code emoticon.settlement} · 계좌 원문 보기 {@code emoticon.payout.pii}) — 실제 필터 체인·세션·권한 인터셉터·PostgreSQL.
 * 목록·상세 모양 · 확정(건너뛴 까닭) · 판매 줄의 정산 달 · 보류·풀기 · 지급 기록 · 정산 줄 주소의 계좌 원문 보기(처리 기록 · 감사).
 * 시계는 실제 시각이라 판매는 석 달 전 달(확정할 수 있는 달)에 넣는다.
 */
class EmoticonSettlementAdminHttpTest extends Commerce04AdminTestBase {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int PRICE = 2500;

    @Autowired
    private EmoticonStudioService studio;
    @Autowired
    private EmoticonReviewService reviews;
    @Autowired
    private DataCipher cipher;
    @Autowired
    private PlatformTransactionManager transactions;

    private long member() {
        long id = newMember();
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", id);
        return id;
    }

    private long image(long creator, String kind, String format, int frames) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO emoticon_images (id, kind, format, width, height, bytes, frames, loop_forever, content_hash, static_path, uploaded_by_user, created_at) "
                + "VALUES (?, ?, ?, 200, 200, 1000, ?, true, lpad(to_hex(?::bigint), 64, '0'), '/emoticons/test.gif', ?, now())", id, kind, format, frames, id, creator);
        return id;
    }

    private long pack(long creator, long operator) {
        List<EmoticonStudioSaveRequest.Item> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            items.add(new EmoticonStudioSaveRequest.Item(Long.toString(image(creator, "EMOTICON", "GIF", 2)), "표" + i));
        }
        EmoticonStudioSaveRequest body = new EmoticonStudioSaveRequest("정산 시험 팩", "설명", List.of("시험"), Long.toString(image(creator, "COVER", "PNG", 1)), items, "FREE",
                null, true, true, true, null);
        TransactionTemplate tx = new TransactionTemplate(transactions);
        long packId = Long.parseLong(tx.execute(s -> studio.submit(creator, null, body)).id());
        String review = jdbc.queryForObject("SELECT id FROM emoticon_reviews WHERE pack_id = ?", Long.class, packId).toString();
        tx.executeWithoutResult(s -> reviews.approve(operator, "127.0.0.1", review, new EmoticonReviewDecisionRequest(0, null)));
        return packId;
    }

    /** 정산 정보(개인 · 국민 · 홍길동) */
    private void payout(long creator) {
        jdbc.update("INSERT INTO emoticon_payout_accounts (user_id, payee_type, bank_code, account_number_enc, account_last4, holder_name_enc, holder_masked, created_at, "
                + "updated_at) VALUES (?, 'INDIVIDUAL', 'KB', ?, '1234', ?, '홍*동', now(), now())", creator, cipher.encrypt(EmoticonPayoutService.ACCOUNT, "12345678901234"),
                cipher.encrypt(EmoticonPayoutService.HOLDER, "홍길동"));
    }

    private long sale(long pack, long creator, Instant paidAt) {
        long purchase = ids.nextId();
        long order = ids.nextId();
        long buyer = ids.nextId();
        String paymentId = "pay_" + String.format("%032d", order).substring(0, 32);
        jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, status, paid_amount, paid_at, expires_at, confirmed_via, "
                + "created_at, updated_at) VALUES (?, ?, ?, 'ROBOTIKON_PACK', ?, '로보티콘', ?, 'PAID', ?, ?, ?, 'WEBHOOK', ?, ?)", order, paymentId, buyer, purchase, PRICE, PRICE,
                Timestamp.from(paidAt), Timestamp.from(paidAt.plus(Duration.ofMinutes(30))), Timestamp.from(paidAt), Timestamp.from(paidAt));
        jdbc.update("INSERT INTO emoticon_purchases (id, user_id, pack_id, creator_id, pack_name, amount, status, refund_notice_agreed_at, expires_at, created_at, "
                + "payment_order_id, paid_at, refund_until) VALUES (?, ?, ?, ?, '산 때 이름', ?, 'PAID', ?, ?, ?, ?, ?, ?)", purchase, buyer, pack, creator, PRICE,
                Timestamp.from(paidAt), Timestamp.from(paidAt.plus(Duration.ofMinutes(30))), Timestamp.from(paidAt), order, Timestamp.from(paidAt),
                Timestamp.from(paidAt.plus(Duration.ofDays(7))));
        return purchase;
    }

    @Test
    void 정산_목록_상세_확정_보류_지급_기록과_정산_줄의_계좌_원문_보기() throws Exception {
        long setup = operatorWith(Permission.EMOTICON_READ, Permission.EMOTICON_REVIEW);
        long creator = member();
        payout(creator);
        long pack = pack(creator, setup);
        YearMonth month = YearMonth.now(SEOUL).minusMonths(3);
        Instant first = LocalDateTime.of(month.getYear(), month.getMonthValue(), 10, 12, 0).atZone(SEOUL).toInstant();
        long firstSale = 0;
        for (int i = 0; i < 8; i++) {
            long id = sale(pack, creator, first.plus(Duration.ofMinutes(i)));
            firstSale = i == 0 ? id : firstSale;
        }
        String monthText = month.toString();
        String id = monthText.replace("-", "") + "-" + creator;
        String base = "/api/admin/emoticons/settlements";

        mvc.perform(get(base).param("month", monthText).cookie(login(operatorWith(Permission.EMOTICON_SALES)))).andExpect(status().isForbidden());
        long settlementOperator = operatorWith(Permission.EMOTICON_SETTLEMENT);
        Cookie s = login(settlementOperator);
        mvc.perform(get(base).param("month", monthText).cookie(s)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.month").value(monthText)).andExpect(jsonPath("$.data.months[0]").value(YearMonth.now(SEOUL).toString()))
                .andExpect(jsonPath("$.data.rules.feeRatePercent").isNumber())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + id + "')].status").value("ESTIMATED"))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + id + "')].salesAmount").value(20000))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + id + "')].payeeType").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + id + "')].account.maskedNumber").value("****1234"))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + id + "')].creator.memberNo").value(Long.toString(creator)));
        mvc.perform(get(base + "/" + id).cookie(s)).andExpect(status().isOk()).andExpect(jsonPath("$.data.packs[0].packId").value(Long.toString(pack)))
                .andExpect(jsonPath("$.data.packs[0].salesCount").value(8)).andExpect(jsonPath("$.data.events[0].action").value("CREATED"))
                .andExpect(jsonPath("$.data.hold").doesNotExist());
        mvc.perform(get(base + "/202001-" + creator).cookie(s)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("EMOTICON_SETTLEMENT_NOT_FOUND"));

        // 확정
        mvc.perform(withCsrf(post(base + "/confirm"), s).content("{\"ids\":[]}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.ids").exists());
        mvc.perform(withCsrf(post(base + "/confirm"), s).content("{\"ids\":[\"" + id + "\",\"엉터리\"]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmed").value(1)).andExpect(jsonPath("$.data.skipped").value(1))
                .andExpect(jsonPath("$.data.skippedItems[0].reason").value("NOT_FOUND"));
        mvc.perform(get(base + "/" + id).cookie(s)).andExpect(jsonPath("$.data.status").value("CONFIRMED")).andExpect(jsonPath("$.data.payoutAmount").value(12316))
                .andExpect(jsonPath("$.data.withholdingAmount").value(410)).andExpect(jsonPath("$.data.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.events[0].action").value("CONFIRMED")).andExpect(jsonPath("$.data.events[0].by").value(operatorName(settlementOperator)));
        // 판매 내역 줄의 정산 달
        mvc.perform(get("/api/admin/emoticons/sales").param("packId", Long.toString(pack)).cookie(login(operatorWith(Permission.EMOTICON_SALES))))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + firstSale + "')].settlementMonth").value(monthText));

        // 보류 · 풀기
        mvc.perform(withCsrf(post(base + "/" + id + "/hold"), s).content("{\"reason\":\"짧음\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.reason").exists());
        mvc.perform(withCsrf(post(base + "/" + id + "/hold"), s).content("{\"reason\":\"예금주를 확인하고 있어요\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HELD")).andExpect(jsonPath("$.data.hold.reason").value("예금주를 확인하고 있어요"));
        mvc.perform(withCsrf(post(base + "/" + id + "/unhold"), s)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        mvc.perform(withCsrf(post(base + "/" + id + "/unhold"), s)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_SETTLEMENT_NOT_HELD"));

        // 정산 줄의 계좌 원문 보기 — emoticon.payout.pii 만
        String reveal = base + "/" + id + "/account/reveal";
        mvc.perform(withCsrf(post(reveal), s).content("{\"reason\":\"이체하려고 확인해요\"}")).andExpect(status().isForbidden());
        Cookie pii = login(operatorWith(Permission.EMOTICON_PAYOUT_PII));
        mvc.perform(withCsrf(post(reveal), pii).content("{\"reason\":\"이체하려고 확인해요\"}")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.number").value("12345678901234"))
                .andExpect(jsonPath("$.data.holder").value("홍길동")).andExpect(jsonPath("$.data.bank").value("KB"));

        // 지급 완료 기록
        // 이체일은 오늘 이전의 업무일만(후속 결정 4) — 오늘 이전 가장 가까운 업무일로 기록하고, 토요일은 400
        List<LocalDate> holidays = jdbc.queryForList("SELECT holiday_date FROM site_holidays", java.sql.Date.class).stream().map(java.sql.Date::toLocalDate).toList();
        LocalDate business = LocalDate.now(SEOUL);
        while (business.getDayOfWeek().getValue() >= 6 || holidays.contains(business)) {
            business = business.minusDays(1);
        }
        LocalDate saturday = LocalDate.now(SEOUL);
        while (saturday.getDayOfWeek() != java.time.DayOfWeek.SATURDAY) {
            saturday = saturday.minusDays(1);
        }
        String today = business.toString();
        mvc.perform(withCsrf(post(base + "/" + id + "/paid"), s).content("{\"transferDate\":\"" + LocalDate.now(SEOUL).plusDays(1) + "\",\"memo\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.transferDate").exists());
        mvc.perform(withCsrf(post(base + "/" + id + "/paid"), s).content("{\"transferDate\":\"" + saturday + "\",\"memo\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.transferDate").exists());
        mvc.perform(withCsrf(post(base + "/" + id + "/paid"), s).content("{\"transferDate\":\"" + today + "\",\"memo\":\"정기 이체\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID")).andExpect(jsonPath("$.data.paid.transferDate").value(today))
                .andExpect(jsonPath("$.data.paid.memo").value("정기 이체")).andExpect(jsonPath("$.data.events[0].action").value("PAID"))
                .andExpect(jsonPath("$.data.events[1].action").value("ACCOUNT_VIEWED"));
        mvc.perform(withCsrf(post(base + "/" + id + "/paid"), s).content("{\"transferDate\":\"" + today + "\",\"memo\":\"\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMOTICON_SETTLEMENT_NOT_CONFIRMED"));

        // 감사 — 정산 처리 넷(대상 정산 줄) · 계좌 보기(대상 작가, 정산 줄 ID 덧붙임)
        for (String action : List.of("EMOTICON_SETTLEMENT_CONFIRM", "EMOTICON_SETTLEMENT_HOLD", "EMOTICON_SETTLEMENT_UNHOLD", "EMOTICON_SETTLEMENT_PAID")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = ? AND target_type = 'EMOTICON_SETTLEMENT' AND target_id = ?", Integer.class, action, id))
                    .as(action).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'EMOTICON_PAYOUT_REVEAL' AND target_id = ? AND detail::text LIKE ?", Integer.class,
                Long.toString(creator), "%" + id + "%")).isEqualTo(1);
    }
}
