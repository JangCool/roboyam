package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.audit.entity.AuditActorType;
import com.community.emoticon.dto.request.EmoticonSettlementManualPaidRequest;
import com.community.emoticon.dto.response.EmoticonSettlementView;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.exception.EmoticonSettlementNotFoundException;
import com.community.emoticon.service.EmoticonSettlementService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.DataAccessException;

/**
 * 수동 지급 기록(사용자 승인 2026-11-05, V149 — 정산 정보 없이 강제 탈퇴된 작가에게 연락이 닿아 은행에서 직접 보낸 뒤 기록만 남긴다. 계좌는 시스템에 넣지 않는다) — 실제 PostgreSQL.
 * 대상 조건(탈퇴 · 정산 정보 없음 · 굳히지 않은 보류 · 확정 가능 시각 지남) · 입력 400(이체일·주말·메모·사유) · 409 · 그 달과 그 앞 줄을 탈퇴 작가 규칙(개인 원천징수)으로 굳혀 지급 완료(지급액 0 줄은 확정) ·
 * 처리 기록 {@code MANUAL_PAID} · 감사 · 파기 배치가 끝난 것으로 봄 · DB 그물. (HTTP · 권한은 admin {@code EmoticonWithdrawalSettlementAdminHttpTest})
 */
class EmoticonManualPaidTest extends EmoticonTestBase {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int PRICE = 2500;
    /** 2,500 → 정산 기준 2,272 → 수수료 30% 뒤 작가 몫 1,590 · 개인 원천징수 50 → 지급액 1,540 */
    private static final long SHARE = 1590;
    private static final long WITHHOLDING = 50;
    private static final long PAYOUT = SHARE - WITHHOLDING;
    private static final String IP = "127.0.0.1";
    private static final String REASON = "작가가 연락해 와 은행에서 직접 보냈어요";

    private Map<String, Object> settingsWas;
    private Instant t0;
    private LocalDate m0;

    @BeforeEach
    void settle() {
        settingsWas = kit.settingsRow();
        kit.jdbc.update("UPDATE emoticon_settings SET fee_rate_percent = 30, payout_minimum = 10000, settlement_day = 10, refund_days = 7 WHERE id = 1");
        // 판매는 석 달쯤 전 달 — 확정 가능 시각 · 파기 여유(30일)가 지난 시각에 처리한다
        t0 = Instant.now().minus(Duration.ofDays(100)).truncatedTo(ChronoUnit.MICROS);
        kit.clock.set(t0);
        m0 = ServiceTime.dateOf(t0).withDayOfMonth(1).minusMonths(2);
    }

    @AfterEach
    void restore() {
        kit.restoreSettings(settingsWas);
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private static String code(Executable call) {
        try {
            call.execute();
        } catch (CommonException e) {
            return e.getErrorCode();
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("오류가 나지 않았다");
    }

    private static Map<String, MessageRef> fields(Executable call) {
        try {
            call.execute();
        } catch (ValidationException e) {
            return e.getFields();
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("400 이 나지 않았다");
    }

    private Instant soldAt(LocalDate month) {
        return month.plusDays(2).atTime(12, 0).atZone(SEOUL).toInstant();
    }

    /** 결제된 판매 한 건 — 구매 ID */
    private long sale(long creator, long pack, Instant paidAt) {
        long purchase = kit.ids.nextId();
        long order = kit.ids.nextId();
        long buyer = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, status, expires_at, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ROBOTIKON_PACK', ?, '시험', ?, 'READY', ?, ?, ?)", order, "pay_" + String.format("%032d", order).substring(0, 32), buyer, purchase, PRICE,
                Timestamp.from(paidAt.plus(Duration.ofMinutes(30))), Timestamp.from(paidAt), Timestamp.from(paidAt));
        kit.jdbc.update("INSERT INTO emoticon_purchases (id, user_id, pack_id, creator_id, pack_name, amount, status, refund_notice_agreed_at, expires_at, created_at, "
                + "payment_order_id, paid_at, refund_until) VALUES (?, ?, ?, ?, '수동 지급 팩', ?, 'PAID', ?, ?, ?, ?, ?, ?)", purchase, buyer, pack, creator, PRICE,
                Timestamp.from(paidAt), Timestamp.from(paidAt.plus(Duration.ofMinutes(30))), Timestamp.from(paidAt), order, Timestamp.from(paidAt),
                Timestamp.from(paidAt.plus(Duration.ofDays(7))));
        return purchase;
    }

    /** 정산 정보 없이 판매가 있는 작가 — 팩 하나 + 달마다 판매 한 건 */
    private long sellingCreator(LocalDate... months) {
        long creator = kit.member();
        long pack = kit.approvedPack(creator);
        for (LocalDate m : months) {
            sale(creator, pack, soldAt(m));
        }
        return creator;
    }

    /** 운영자 강제 탈퇴와 같은 정산 정리(동의 없음 → 시스템 보류) + 회원 쪽 탈퇴 완료 */
    private void forceWithdraw(long creator) {
        assertThat(kit.inTx(() -> kit.creatorWithdrawal.closeSettlement(creator, null, null)).outcome()).isEqualTo("HELD");
        kit.jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", creator);
        kit.jdbc.update("INSERT INTO member_withdrawals (id, user_id, status, requested_at, completed_at) VALUES (?, ?, 'COMPLETED', ?, ?)", kit.ids.nextId(), creator,
                Timestamp.from(t0), Timestamp.from(t0));
    }

    private static String line(long creator, LocalDate month) {
        return String.format("%04d%02d-%d", month.getYear(), month.getMonthValue(), creator);
    }

    private Map<String, Object> row(long creator, LocalDate month) {
        return kit.jdbc.queryForMap("SELECT status, payout_amount, withholding_amount, carried_in, carried_out, payee_type, confirmed_at, confirmed_by, paid_at, paid_by, "
                + "transfer_date, paid_memo, hold_reason, held_at FROM emoticon_settlements WHERE creator_id = ? AND sales_month = ?", creator, java.sql.Date.valueOf(month));
    }

    private List<Map<String, Object>> events(long creator, String action) {
        return kit.jdbc.queryForList("SELECT sales_month, operator_id, note, amount, consented_at FROM emoticon_settlement_events WHERE creator_id = ? AND action = ? "
                + "ORDER BY sales_month", creator, action);
    }

    private List<AuditEvent> audits(String action, long creator) {
        return kit.audits.stream().filter(e -> e.action().equals(action) && Long.toString(creator).equals(e.detail().get("creator"))).toList();
    }

    /** 처리 시각(t0) 이전의 가장 가까운 업무일 */
    private LocalDate businessDay() {
        LocalDate d = ServiceTime.dateOf(t0);
        while (!kit.holidays.calendar().businessDay(d)) {
            d = d.minusDays(1);
        }
        return d;
    }

    private EmoticonSettlementView.AdminDetail pay(String id, String date, String memo, String reason) {
        return kit.inTx(() -> kit.settlements.markManualPaid(kit.operator, IP, id, new EmoticonSettlementManualPaidRequest(date, memo, reason)));
    }

    // ── 1. 굳힘 · 지급 완료 · 처리 기록 · 감사 · 파기 배치 ─────────────────────

    @Test
    void 그_달과_그_앞의_보류_줄을_개인_원천징수로_굳혀_지급_완료로_기록하고_처리_기록_감사가_남으며_파기_배치가_끝난_것으로_본다() {
        LocalDate m1 = m0.plusMonths(1);
        long creator = sellingCreator(m0, m1);
        forceWithdraw(creator);
        assertThat(kit.creatorWithdrawal.summary(creator).orElseThrow().unpaidEarnings()).isEqualTo(2 * SHARE);
        LocalDate transfer = businessDay();
        kit.audits.clear();

        EmoticonSettlementView.AdminDetail d = pay(line(creator, m1), transfer.toString(), " 국민은행 이체 ", " " + REASON + " ");

        for (LocalDate m : List.of(m0, m1)) {
            Map<String, Object> r = row(creator, m);
            assertThat(r).as(m.toString()).containsEntry("status", "PAID").containsEntry("payout_amount", PAYOUT).containsEntry("withholding_amount", WITHHOLDING)
                    .containsEntry("carried_in", 0L).containsEntry("carried_out", 0L).containsEntry("payee_type", "INDIVIDUAL").containsEntry("confirmed_by", kit.operator)
                    .containsEntry("paid_by", kit.operator).containsEntry("paid_memo", "국민은행 이체").containsEntry("hold_reason", null).containsEntry("held_at", null);
            assertThat(((java.sql.Date) r.get("transfer_date")).toLocalDate()).isEqualTo(transfer);
            assertThat(((Timestamp) r.get("paid_at")).toInstant()).as("굳힌 시각 = 지급 기록 시각").isEqualTo(((Timestamp) r.get("confirmed_at")).toInstant()).isEqualTo(t0);
        }
        // 그 줄에 들어간 판매에 정산 달을 찍었다 · 남은 수익 0
        assertThat(kit.count("SELECT count(*) FROM emoticon_purchases WHERE creator_id = ? AND settlement_month IS NULL", creator)).isZero();
        assertThat(kit.creatorWithdrawal.summary(creator).orElseThrow().unpaidEarnings()).isZero();
        // 처리 기록 — 줄마다 MANUAL_PAID · 사유 · 그 줄의 지급액 · 운영자
        assertThat(events(creator, "MANUAL_PAID")).hasSize(2).allSatisfy(e -> {
            assertThat(e.get("note")).isEqualTo(REASON);
            assertThat(e.get("amount")).isEqualTo(PAYOUT);
            assertThat(e.get("operator_id")).isEqualTo(kit.operator);
            assertThat(e.get("consented_at")).isNull();
        });
        // 감사(돈 관련) — 운영자 · 줄마다 · 계좌 없이 금액·이체일·메모·사유
        assertThat(AuditRetention.declared(AuditActions.EMOTICON_SETTLEMENT_MANUAL_PAID)).contains(AuditRetentionClass.MONEY);
        List<AuditEvent> audits = audits(AuditActions.EMOTICON_SETTLEMENT_MANUAL_PAID, creator);
        assertThat(audits.stream().map(AuditEvent::targetId)).containsExactly(line(creator, m0), line(creator, m1));
        assertThat(audits).allSatisfy(e -> {
            assertThat(e.actorType()).isEqualTo(AuditActorType.OPERATOR);
            assertThat(e.actorId()).isEqualTo(kit.operator);
            assertThat(e.targetType()).isEqualTo(EmoticonSettlementService.TARGET);
            assertThat(e.detail()).containsEntry("from", "HELD").containsEntry("to", "PAID").containsEntry("payoutAmount", PAYOUT)
                    .containsEntry("withholdingAmount", WITHHOLDING).containsEntry("payeeType", "INDIVIDUAL").containsEntry("transferDate", transfer.toString())
                    .containsEntry("memo", "국민은행 이체").containsEntry("reason", REASON);
        });
        // 응답(상세) — 지급 완료 · 지급 기록 · 처리 기록 맨 위가 MANUAL_PAID(금액)
        assertThat(d.status()).isEqualTo("PAID");
        assertThat(d.payoutAmount()).isEqualTo(PAYOUT);
        assertThat(d.paid().transferDate()).isEqualTo(transfer.toString());
        assertThat(d.hold()).isNull();
        assertThat(d.events().get(0).action()).isEqualTo("MANUAL_PAID");
        assertThat(d.events().get(0).amount()).isEqualTo(PAYOUT);
        assertThat(d.events().get(0).note()).isEqualTo(REASON);
        // 다시 하면 409(굳힌 줄) · 포기 처리·보류 풀기도 받지 않는다
        assertThat(code(() -> pay(line(creator, m1), transfer.toString(), null, REASON))).isEqualTo(EmoticonConflictException.SETTLEMENT_NOT_MANUAL_PAYABLE);
        assertThat(code(() -> kit.inTx(() -> kit.settlements.waive(kit.operator, IP, line(creator, m1), "연락이 닿지 않아 포기 처리"))))
                .isEqualTo(EmoticonConflictException.SETTLEMENT_NOT_WAIVABLE);

        // 파기 배치는 끝난 것으로 본다 — 지울 정산 정보는 원래 없지만, 개발 데이터로 붙여 보면 지운다
        kit.jdbc.update("INSERT INTO emoticon_payout_accounts (user_id, payee_type, bank_code, account_number_enc, account_last4, holder_name_enc, holder_masked, created_at, "
                + "updated_at) VALUES (?, 'INDIVIDUAL', 'KB', 'd1:AAAA', '1234', 'd1:AAAA', '홍*동', ?, ?)", creator, Timestamp.from(t0), Timestamp.from(t0));
        kit.jdbc.execute("CALL batch_emoticon_payout_account_cleanup('MANUAL', NULL, NULL)");
        assertThat(kit.count("SELECT count(*) FROM emoticon_payout_accounts WHERE user_id = ?", creator)).isZero();
    }

    @Test
    void 지급액이_0_인_앞_달은_확정으로_굳히고_뒤_달은_그대로_두며_지급할_돈이_없으면_409() {
        // 세 달 모두 처리 시각(t0)의 앞 달까지 — 확정 가능 시각이 지났다(이번 달은 다음 달 1일이 지나야 굳힐 수 있다)
        LocalDate m0 = this.m0.minusMonths(1);
        LocalDate m1 = m0.plusMonths(1);
        LocalDate m2 = m1.plusMonths(1);
        long creator = sellingCreator(m0, m1, m2);
        // m0 의 판매는 같은 달 안에 환불 — 합 0(지급액 0)
        long first = kit.jdbc.queryForObject("SELECT id FROM emoticon_purchases WHERE creator_id = ? ORDER BY paid_at LIMIT 1", Long.class, creator);
        kit.jdbc.update("UPDATE emoticon_purchases SET status = 'REFUNDED', refunded_at = ?, refund_kind = 'EXCEPTION', refund_reason = '같은 달 예외 환불' WHERE id = ?",
                Timestamp.from(soldAt(m0).plus(Duration.ofDays(1))), first);
        forceWithdraw(creator);
        assertThat(row(creator, m0)).containsEntry("status", "HELD");

        // m0 만으로는 지급할 돈이 없다
        assertThat(code(() -> pay(line(creator, m0), businessDay().toString(), null, REASON))).isEqualTo(EmoticonConflictException.SETTLEMENT_NOTHING_TO_PAY);
        assertThat(row(creator, m0).get("confirmed_at")).as("바꾸지 않았다").isNull();

        kit.audits.clear();
        pay(line(creator, m1), businessDay().toString(), null, REASON);
        assertThat(row(creator, m0)).containsEntry("status", "CONFIRMED").containsEntry("payout_amount", 0L).containsEntry("carried_out", 0L).containsEntry("paid_at", null)
                .containsEntry("transfer_date", null).containsEntry("hold_reason", null);
        assertThat(row(creator, m1)).containsEntry("status", "PAID").containsEntry("payout_amount", PAYOUT);
        assertThat(row(creator, m2)).as("뒤 달은 그대로 보류").containsEntry("status", "HELD").containsEntry("confirmed_at", null);
        assertThat(events(creator, "MANUAL_PAID").stream().map(e -> e.get("amount"))).containsExactly(0L, PAYOUT);
        assertThat(audits(AuditActions.EMOTICON_SETTLEMENT_MANUAL_PAID, creator).get(0).detail()).containsEntry("to", "CONFIRMED").containsEntry("payoutAmount", 0L);
        assertThat(kit.creatorWithdrawal.summary(creator).orElseThrow().unpaidEarnings()).as("m2 만 남는다").isEqualTo(SHARE);
        // 뒤 달을 마저 끝낸다 — 앞 달이 굳었으니 그 달 하나
        pay(line(creator, m2), businessDay().toString(), "두 번째 이체", REASON);
        assertThat(row(creator, m2)).containsEntry("status", "PAID").containsEntry("paid_memo", "두 번째 이체");
        assertThat(kit.creatorWithdrawal.summary(creator).orElseThrow().unpaidEarnings()).isZero();
    }

    // ── 2. 입력 400 · 404 · 409 ─────────────────────────────────────────────

    @Test
    void 이체일_주말_공휴일_오늘_이후_메모_사유는_400_을_함께_돌려주고_아무것도_바꾸지_않는다() {
        long creator = sellingCreator(m0);
        forceWithdraw(creator);
        String id = line(creator, m0);

        assertThat(fields(() -> pay(id, null, null, null))).containsOnlyKeys("transferDate", "reason");
        assertThat(fields(() -> pay(id, "2026/01/05", null, REASON)).get("transferDate").key()).isEqualTo("emoticon.settlement.transferDate");
        assertThat(fields(() -> pay(id, ServiceTime.dateOf(t0).plusDays(1).toString(), null, REASON)).get("transferDate").key())
                .isEqualTo("emoticon.settlement.transferDate.future");
        LocalDate sat = ServiceTime.dateOf(t0).minusDays(1);
        while (sat.getDayOfWeek() != DayOfWeek.SATURDAY) {
            sat = sat.minusDays(1);
        }
        LocalDate saturday = sat;
        assertThat(fields(() -> pay(id, saturday.toString(), null, REASON)).get("transferDate").key()).isEqualTo("emoticon.settlement.transferDate.notBusinessDay");
        assertThat(fields(() -> pay(id, saturday.plusDays(1).toString(), null, REASON)).get("transferDate").key()).as("일요일")
                .isEqualTo("emoticon.settlement.transferDate.notBusinessDay");
        // 공휴일(시험용 임시공휴일 — 처리 시각 전 업무일 하나를 막는다)
        LocalDate holiday = businessDay();
        kit.jdbc.update("INSERT INTO public_holidays (holiday_date, name, kind) VALUES (?, '시험 임시공휴일', 'TEMPORARY')", java.sql.Date.valueOf(holiday));
        try {
            assertThat(fields(() -> pay(id, holiday.toString(), null, REASON)).get("transferDate").key()).isEqualTo("emoticon.settlement.transferDate.notBusinessDay");
        } finally {
            kit.jdbc.update("DELETE FROM public_holidays WHERE holiday_date = ? AND name = '시험 임시공휴일'", java.sql.Date.valueOf(holiday));
        }
        assertThat(fields(() -> pay(id, businessDay().toString(), "가".repeat(201), REASON))).containsOnlyKeys("memo");
        assertThat(fields(() -> pay(id, businessDay().toString(), "가".repeat(200), "짧음"))).containsOnlyKeys("reason");
        assertThat(fields(() -> pay(id, businessDay().toString(), null, "가".repeat(201))).get("reason").key()).isEqualTo("emoticon.reason.length");
        assertThat(row(creator, m0)).containsEntry("status", "HELD").containsEntry("confirmed_at", null);
        assertThat(events(creator, "MANUAL_PAID")).isEmpty();
        // 없는 줄 404
        assertThatThrownBy(() -> pay(line(creator, m0.minusYears(3)), businessDay().toString(), null, REASON)).isInstanceOf(EmoticonSettlementNotFoundException.class);
        assertThatThrownBy(() -> pay("nope", businessDay().toString(), null, REASON)).isInstanceOf(EmoticonSettlementNotFoundException.class);
    }

    @Test
    void 탈퇴하지_않은_작가_정산_정보가_있는_작가_보류가_아닌_줄_셀프_환불_기한_전은_409() {
        String date = businessDay().toString();
        // 아직 탈퇴하지 않은 작가(보류만 있음)
        long active = sellingCreator(m0);
        assertThat(kit.inTx(() -> kit.creatorWithdrawal.closeSettlement(active, null, null)).outcome()).isEqualTo("HELD");
        assertThat(code(() -> pay(line(active, m0), date, null, REASON))).as("탈퇴 전").isEqualTo(EmoticonConflictException.SETTLEMENT_NOT_MANUAL_PAYABLE);

        // 정산 정보가 있는 탈퇴 작가의 보류 — 보류를 풀고 확정 뒤 지급 기록으로
        long registered = kit.member();
        kit.registerPayout(registered);
        sale(registered, kit.approvedPack(registered), soldAt(m0));
        kit.jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", registered);
        kit.inTx(() -> kit.settlements.hold(kit.operator, IP, line(registered, m0), "확인할 것이 있어요"));
        assertThat(code(() -> pay(line(registered, m0), date, null, REASON))).as("정산 정보 있음").isEqualTo(EmoticonConflictException.SETTLEMENT_NOT_MANUAL_PAYABLE);

        // 보류가 아닌(예정) 줄
        long estimated = sellingCreator(m0);
        kit.jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", estimated);
        assertThat(code(() -> pay(line(estimated, m0), date, null, REASON))).as("예정 줄").isEqualTo(EmoticonConflictException.SETTLEMENT_NOT_MANUAL_PAYABLE);

        // 셀프 환불 기한(7일) 전 — 처리 시각 바로 전 판매가 든 이번 달
        Instant recentSale = t0.minus(Duration.ofHours(1));
        LocalDate current = ServiceTime.dateOf(recentSale).withDayOfMonth(1);
        long recent = kit.member();
        sale(recent, kit.approvedPack(recent), recentSale);
        forceWithdraw(recent);
        assertThat(code(() -> pay(line(recent, current), date, null, REASON))).as("기한 전").isEqualTo(EmoticonConflictException.SETTLEMENT_NOT_MANUAL_PAYABLE);
        assertThat(row(recent, current)).containsEntry("status", "HELD");
        // 포기 처리로 끝낸 줄
        long waived = sellingCreator(m0);
        forceWithdraw(waived);
        kit.inTx(() -> kit.settlements.waive(kit.operator, IP, line(waived, m0), "연락이 닿지 않아 포기 처리"));
        assertThat(code(() -> pay(line(waived, m0), date, null, REASON))).as("포기한 줄").isEqualTo(EmoticonConflictException.SETTLEMENT_NOT_MANUAL_PAYABLE);
        assertThat(kit.audits.stream().filter(e -> e.action().equals(AuditActions.EMOTICON_SETTLEMENT_MANUAL_PAID))).isEmpty();
    }

    // ── 3. DB 그물 · 문구 ───────────────────────────────────────────────────

    @Test
    void 굳히지_않은_줄은_한_번에_굳히지_않으면_지급_완료가_되지_않고_수동_지급_기록은_사유와_금액이_있어야_한다() {
        long creator = sellingCreator(m0);
        forceWithdraw(creator);
        java.sql.Date month = java.sql.Date.valueOf(m0);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_settlements SET status = 'PAID', paid_at = now(), transfer_date = current_date, hold_reason = NULL, held_at = NULL "
                + "WHERE creator_id = ? AND sales_month = ?", creator, month)).as("금액을 굳히지 않은 지급 완료").isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO emoticon_settlement_events (id, creator_id, sales_month, action, at, note) VALUES (?, ?, ?, 'MANUAL_PAID', now(), ?)",
                kit.ids.nextId(), creator, month, REASON)).as("금액 없음").isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO emoticon_settlement_events (id, creator_id, sales_month, action, at, amount) VALUES (?, ?, ?, 'MANUAL_PAID', now(), 1)",
                kit.ids.nextId(), creator, month)).as("사유 없음").isInstanceOf(DataAccessException.class);
    }

    @Test
    void 새_문구는_두_언어에_있다() throws IOException {
        for (String lang : List.of("ko", "en")) {
            Properties p = new Properties();
            try (InputStream in = getClass().getResourceAsStream("/messages/messages_" + lang + ".properties")) {
                p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            }
            for (String key : List.of("error." + EmoticonConflictException.SETTLEMENT_NOT_MANUAL_PAYABLE, "audit.action." + AuditActions.EMOTICON_SETTLEMENT_MANUAL_PAID)) {
                assertThat(p.getProperty(key)).as(lang + " " + key).isNotBlank();
            }
        }
    }
}
