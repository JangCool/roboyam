package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.emoticon.dto.request.EmoticonReviewDecisionRequest;
import com.community.emoticon.dto.request.EmoticonSettlementConfirmRequest;
import com.community.emoticon.dto.response.EmoticonAdminView;
import com.community.emoticon.dto.response.EmoticonSettlementView;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.exception.EmoticonSettlementNotFoundException;
import com.community.emoticon.service.EmoticonCreatorWithdrawal;
import com.community.emoticon.service.EmoticonSettlementService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.time.ServiceTime;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
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
 * 탈퇴 작가 미확정 정리(사용자 지시 2026-11-05 「미확정까지 적용해」, V148) — 실제 PostgreSQL 위에서 정산 쪽 규칙을 본다.
 * 탈퇴 화면의 작가 요약(작가·비작가·금액 — 최소 지급액과 상관없이 · 넘긴 금액 포함) · 탈퇴 완료의 정산 정리(정산 정보 있음 = 그대로 · 동의 = 포기로 굳힘 · 동의 없음/금액 늘어남 = 시스템 보류) ·
 * 포기한 줄(지급액 0 · 넘긴 양수는 이어지지 않음 · 처리 기록 · 감사 · 파기 배치가 끝난 것으로 봄 · 그 뒤 환불은 음수 = 끝남) · 운영자 포기 처리(사유 · 차례 · 409) · DB 그물 ·
 * 심사 목록 줄의 처리 까닭. (탈퇴 API 를 지나는 흐름 — 409·400·즉시 완료 — 은 api {@code WithdrawalEmoticonEarningsHttpTest}, 관리자 HTTP 는 admin {@code EmoticonWithdrawalSettlementAdminHttpTest})
 */
class EmoticonWithdrawalEarningsTest extends EmoticonTestBase {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int PRICE = 2500;
    /** 2,500 → 정산 기준 2,272 → 수수료 30% 뒤 작가 몫 1,590(최소 지급액 10,000 미만) */
    private static final long SHARE = 1590;
    private static final String IP = "127.0.0.1";

    private Map<String, Object> settingsWas;
    private Instant t0;
    private LocalDate m0;

    @BeforeEach
    void settle() {
        settingsWas = kit.settingsRow();
        kit.jdbc.update("UPDATE emoticon_settings SET fee_rate_percent = 30, payout_minimum = 10000, settlement_day = 10, refund_days = 7 WHERE id = 1");
        // 판매는 석 달쯤 전 달 — 확정 가능 · 파기 여유(30일)가 지난 시각에 처리한다
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

    private Instant soldAt(LocalDate month) {
        return month.plusDays(2).atTime(12, 0).atZone(SEOUL).toInstant();
    }

    /** 결제된 판매 한 건(결제 주문 + 구매) — 구매 ID */
    private long sale(long creator, long pack, Instant paidAt) {
        long purchase = kit.ids.nextId();
        long order = kit.ids.nextId();
        long buyer = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, status, expires_at, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ROBOTIKON_PACK', ?, '시험', ?, 'READY', ?, ?, ?)", order, "pay_" + String.format("%032d", order).substring(0, 32), buyer, purchase, PRICE,
                Timestamp.from(paidAt.plus(Duration.ofMinutes(30))), Timestamp.from(paidAt), Timestamp.from(paidAt));
        kit.jdbc.update("INSERT INTO emoticon_purchases (id, user_id, pack_id, creator_id, pack_name, amount, status, refund_notice_agreed_at, expires_at, created_at, "
                + "payment_order_id, paid_at, refund_until) VALUES (?, ?, ?, ?, '탈퇴 수익 팩', ?, 'PAID', ?, ?, ?, ?, ?, ?)", purchase, buyer, pack, creator, PRICE,
                Timestamp.from(paidAt), Timestamp.from(paidAt.plus(Duration.ofMinutes(30))), Timestamp.from(paidAt), order, Timestamp.from(paidAt),
                Timestamp.from(paidAt.plus(Duration.ofDays(7))));
        return purchase;
    }

    /** 판매가 있는 작가(정산 정보 없음) — 팩 하나 + {@code months} 의 달마다 판매 한 건 */
    private long sellingCreator(LocalDate... months) {
        long creator = kit.member();
        long pack = kit.approvedPack(creator);
        for (LocalDate m : months) {
            sale(creator, pack, soldAt(m));
        }
        return creator;
    }

    private static String line(long creator, LocalDate month) {
        return String.format("%04d%02d-%d", month.getYear(), month.getMonthValue(), creator);
    }

    private Map<String, Object> row(long creator, LocalDate month) {
        return kit.jdbc.queryForMap("SELECT status, payout_amount, withholding_amount, carried_in, carried_out, confirmed_at, confirmed_by, hold_reason, held_by "
                + "FROM emoticon_settlements WHERE creator_id = ? AND sales_month = ?", creator, java.sql.Date.valueOf(month));
    }

    private List<Map<String, Object>> events(long creator, String action) {
        return kit.jdbc.queryForList("SELECT sales_month, operator_id, note, amount, consented_at FROM emoticon_settlement_events WHERE creator_id = ? AND action = ? "
                + "ORDER BY sales_month", creator, action);
    }

    private List<AuditEvent> audits(String action, long creator) {
        return kit.audits.stream().filter(e -> e.action().equals(action) && Long.toString(creator).equals(e.detail().get("creator"))).toList();
    }

    private EmoticonSettlementService.WithdrawalClosure close(long creator, Long consented, Instant consentedAt) {
        return kit.inTx(() -> kit.creatorWithdrawal.closeSettlement(creator, consented, consentedAt));
    }

    /** 탈퇴 완료의 회원 쪽(파기 배치·운영자 포기 처리가 보는 값) */
    private void markWithdrawn(long user, Instant at) {
        kit.jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", user);
        kit.jdbc.update("INSERT INTO member_withdrawals (id, user_id, status, requested_at, completed_at) VALUES (?, ?, 'COMPLETED', ?, ?)", kit.ids.nextId(), user,
                Timestamp.from(at), Timestamp.from(at));
    }

    // ── 1. 탈퇴 화면의 작가 요약 ────────────────────────────────────────────

    @Test
    void 작가_요약은_작가가_아니면_비고_판매_중_팩_대기_심사_남은_수익_정산_정보를_싣는다() {
        assertThat(kit.creatorWithdrawal.summary(kit.member())).as("팩도 정산 정보도 없는 회원").isEmpty();

        // 무료 팩만 — 판매 중 1 · 대기 심사 1 · 수익 0 · 정할 것 없음
        long free = kit.member();
        kit.approvedPack(free);
        kit.submitNew(free, "대기 팩");
        EmoticonCreatorWithdrawal.Summary s = kit.creatorWithdrawal.summary(free).orElseThrow();
        assertThat(s).isEqualTo(new EmoticonCreatorWithdrawal.Summary(1, 1, 0, false));
        assertThat(s.decisionRequired()).isFalse();

        // 정산 정보 없이 판매 — 최소 지급액(10,000) 미만이어도 탈퇴하면 받을 돈으로 센다(원천징수 전) · 정할 것 있음
        long seller = sellingCreator(m0);
        s = kit.creatorWithdrawal.summary(seller).orElseThrow();
        assertThat(s.unpaidEarnings()).isEqualTo(SHARE);
        assertThat(s.payoutAccountRegistered()).isFalse();
        assertThat(s.decisionRequired()).isTrue();

        // 정산 정보 있음 — 확정해 넘긴 금액(굳힌 줄의 carried_out)도 남은 수익이다 · 정할 것 없음
        long registered = kit.member();
        kit.registerPayout(registered);
        sale(registered, kit.approvedPack(registered), soldAt(m0));
        assertThat(kit.inTx(() -> kit.settlements.confirm(kit.operator, IP, new EmoticonSettlementConfirmRequest(List.of(line(registered, m0))))).confirmed()).isEqualTo(1);
        assertThat(row(registered, m0)).containsEntry("carried_out", SHARE);
        s = kit.creatorWithdrawal.summary(registered).orElseThrow();
        assertThat(s.unpaidEarnings()).isEqualTo(SHARE);
        assertThat(s.payoutAccountRegistered()).isTrue();
        assertThat(s.decisionRequired()).isFalse();

        // 정산 정보만 있고 팩이 없어도 작가로 본다
        long accountOnly = kit.member();
        kit.registerPayout(accountOnly);
        assertThat(kit.creatorWithdrawal.summary(accountOnly)).contains(new EmoticonCreatorWithdrawal.Summary(0, 0, 0, true));
    }

    // ── 2. 탈퇴 완료의 정산 정리 ────────────────────────────────────────────

    @Test
    void 정산_정보가_있거나_남은_수익이_없으면_정산을_건드리지_않는다() {
        long registered = kit.member();
        kit.registerPayout(registered);
        sale(registered, kit.approvedPack(registered), soldAt(m0));
        assertThat(close(registered, null, null)).isEqualTo(new EmoticonSettlementService.WithdrawalClosure("PAYABLE", SHARE));
        assertThat(kit.count("SELECT count(*) FROM emoticon_settlements WHERE creator_id = ?", registered)).as("줄을 만들지 않는다 — 다음 확정에서 지급").isZero();

        long freeOnly = kit.member();
        kit.approvedPack(freeOnly);
        kit.audits.clear();
        assertThat(close(freeOnly, null, null)).isEqualTo(EmoticonSettlementService.WithdrawalClosure.NONE);
        assertThat(kit.audits).as("정산 감사 없음").isEmpty();
    }

    @Test
    void 동의한_작가는_남은_달을_지급액_0_으로_굳혀_포기로_끝내고_처리_기록_감사가_남으며_넘긴_양수는_이어지지_않는다() {
        LocalDate m1 = m0.plusMonths(1);
        long creator = sellingCreator(m0, m1);
        long purchaseInM1 = kit.jdbc.queryForObject("SELECT id FROM emoticon_purchases WHERE creator_id = ? ORDER BY paid_at DESC LIMIT 1", Long.class, creator);
        long unpaid = kit.creatorWithdrawal.summary(creator).orElseThrow().unpaidEarnings();
        assertThat(unpaid).isEqualTo(2 * SHARE);
        Instant consentedAt = t0.minus(Duration.ofMinutes(5));

        EmoticonSettlementService.WithdrawalClosure closure = close(creator, unpaid, consentedAt);

        assertThat(closure).isEqualTo(new EmoticonSettlementService.WithdrawalClosure("WAIVED", 2 * SHARE));
        for (LocalDate m : List.of(m0, m1)) {
            Map<String, Object> r = row(creator, m);
            assertThat(r).as(m.toString()).containsEntry("status", "WAIVED").containsEntry("payout_amount", 0L).containsEntry("withholding_amount", 0L)
                    .containsEntry("carried_in", 0L).containsEntry("carried_out", SHARE);
            assertThat(r.get("confirmed_at")).as("굳힘").isNotNull();
            assertThat(r.get("confirmed_by")).as("시스템").isNull();
        }
        // 그 달의 판매에 정산 달을 찍었다(아직 정산에 들어가지 않은 판매가 남지 않는다)
        assertThat(kit.count("SELECT count(*) FROM emoticon_purchases WHERE creator_id = ? AND settlement_month IS NULL", creator)).isZero();
        // 처리 기록 — 줄마다 WAIVED · 그 줄에서 포기한 금액 · 동의 시각 · 시스템
        List<Map<String, Object>> waived = events(creator, "WAIVED");
        assertThat(waived).hasSize(2).allSatisfy(e -> {
            assertThat(e.get("amount")).isEqualTo(SHARE);
            assertThat(((Timestamp) e.get("consented_at")).toInstant()).isEqualTo(consentedAt);
            assertThat(e.get("operator_id")).isNull();
            assertThat(e.get("note")).isEqualTo(EmoticonSettlementService.WAIVED_BY_CONSENT_NOTE);
        });
        // 감사(돈 관련) — 시스템 · 근거 MEMBER_CONSENT · 동의 금액·시각
        assertThat(AuditRetention.declared(AuditActions.EMOTICON_SETTLEMENT_WAIVE)).contains(AuditRetentionClass.MONEY);
        List<AuditEvent> audits = audits(AuditActions.EMOTICON_SETTLEMENT_WAIVE, creator);
        assertThat(audits).hasSize(2).allSatisfy(e -> {
            assertThat(e.actorType()).isEqualTo(AuditActorType.SYSTEM);
            assertThat(e.targetType()).isEqualTo(EmoticonSettlementService.TARGET);
            assertThat(e.detail()).containsEntry("basis", "MEMBER_CONSENT").containsEntry("amount", SHARE).containsEntry("consentedAmount", unpaid)
                    .containsEntry("consentedAt", consentedAt.toString()).containsEntry("from", "ESTIMATED");
        });
        assertThat(audits.stream().map(AuditEvent::targetId)).containsExactly(line(creator, m0), line(creator, m1));

        // 관리자 상세 — 포기한 금액 · 넘긴 금액 0 · 처리 기록에 금액·동의 시각
        EmoticonSettlementView.AdminDetail d = kit.settlements.detail(line(creator, m1));
        assertThat(d.status()).isEqualTo("WAIVED");
        assertThat(d.waivedAmount()).isEqualTo(SHARE);
        assertThat(d.carriedOut()).isZero();
        assertThat(d.payoutAmount()).isZero();
        assertThat(d.events().get(0).action()).isEqualTo("WAIVED");
        assertThat(d.events().get(0).amount()).isEqualTo(SHARE);
        assertThat(d.events().get(0).consentedAt()).isEqualTo(consentedAt);
        // 남은 수익은 0 · 다음 달에 이어지지 않는다(목록에 그 작가 줄이 없다) · 두 번 불러도 할 일이 없다
        assertThat(kit.creatorWithdrawal.summary(creator).orElseThrow().unpaidEarnings()).isZero();
        LocalDate m2 = m1.plusMonths(1);
        assertThat(kit.settlements.list(String.format("%04d-%02d", m2.getYear(), m2.getMonthValue())).items())
                .noneSatisfy(r -> assertThat(r.creator().memberNo()).isEqualTo(Long.toString(creator)));
        assertThat(close(creator, unpaid, consentedAt)).isEqualTo(EmoticonSettlementService.WithdrawalClosure.NONE);
        // 확정·보류·지급 기록은 포기한 줄을 받지 않는다
        assertThat(kit.inTx(() -> kit.settlements.confirm(kit.operator, IP, new EmoticonSettlementConfirmRequest(List.of(line(creator, m0))))).skippedItems().get(0).reason())
                .isEqualTo("NOT_ESTIMATED");
        assertThat(code(() -> kit.inTx(() -> kit.settlements.hold(kit.operator, IP, line(creator, m0), "보류해 봅니다")))).isEqualTo(EmoticonConflictException.SETTLEMENT_NOT_HOLDABLE);

        // 파기 배치는 포기한 줄을 끝난 것으로 본다 — 개발 데이터로 정산 정보를 붙여 보면 지운다(넘긴 양수가 있어도)
        markWithdrawn(creator, t0);
        kit.jdbc.update("INSERT INTO emoticon_payout_accounts (user_id, payee_type, bank_code, account_number_enc, account_last4, holder_name_enc, holder_masked, created_at, "
                + "updated_at) VALUES (?, 'INDIVIDUAL', 'KB', 'd1:AAAA', '1234', 'd1:AAAA', '홍*동', ?, ?)", creator, Timestamp.from(t0), Timestamp.from(t0));
        kit.jdbc.execute("CALL batch_emoticon_payout_account_cleanup('MANUAL', NULL, NULL)");
        assertThat(kit.count("SELECT count(*) FROM emoticon_payout_accounts WHERE user_id = ?", creator)).isZero();

        // 탈퇴 뒤 환불(운영자 예외 환불) — 이미 정산에 들어간 판매라 다음 열린 달에서 빼고, 음수 이월 = 끝남(남은 수익 0)
        kit.jdbc.update("UPDATE emoticon_purchases SET status = 'REFUNDED', refunded_at = ?, refund_kind = 'EXCEPTION', refund_reason = '탈퇴 뒤 예외 환불' WHERE id = ?",
                Timestamp.from(t0), purchaseInM1);
        assertThat(kit.creatorWithdrawal.summary(creator).orElseThrow().unpaidEarnings()).isZero();
        LocalDate refundMonth = ServiceTime.dateOf(t0).withDayOfMonth(1);
        EmoticonSettlementView.AdminDetail after = kit.settlements.detail(line(creator, refundMonth));
        assertThat(after.carriedIn()).as("포기한 양수는 이어지지 않는다").isZero();
        assertThat(after.refundAmount()).isEqualTo(PRICE);
        assertThat(after.payoutAmount()).isZero();
        assertThat(after.carriedOut()).isNegative();
    }

    @Test
    void 동의가_없거나_동의한_금액보다_늘었으면_시스템_보류로_남기고_운영자가_사유와_함께_포기로_끝낸다() {
        // 동의 없음(운영자 강제 탈퇴와 같다)
        LocalDate m1 = m0.plusMonths(1);
        long forced = sellingCreator(m0, m1);
        assertThat(close(forced, null, null)).isEqualTo(new EmoticonSettlementService.WithdrawalClosure("HELD", 2 * SHARE));
        for (LocalDate m : List.of(m0, m1)) {
            assertThat(row(forced, m)).containsEntry("status", "HELD").containsEntry("hold_reason", EmoticonSettlementService.WITHDRAWN_HOLD_REASON);
            assertThat(row(forced, m).get("held_by")).isNull();
            assertThat(row(forced, m).get("confirmed_at")).as("굳히지 않는다").isNull();
        }
        assertThat(events(forced, "HELD")).hasSize(2).allSatisfy(e -> assertThat(e.get("operator_id")).isNull());
        assertThat(audits(AuditActions.EMOTICON_SETTLEMENT_HOLD, forced)).hasSize(2).allSatisfy(e -> {
            assertThat(e.actorType()).isEqualTo(AuditActorType.SYSTEM);
            assertThat(e.detail()).containsEntry("cause", "CREATOR_WITHDRAWN").containsEntry("amount", SHARE);
        });
        // 남은 수익은 그대로 보인다(끝나지 않았다)
        assertThat(kit.creatorWithdrawal.summary(forced).orElseThrow().unpaidEarnings()).isEqualTo(2 * SHARE);

        // 동의한 금액보다 늘었으면(진행 중에 판매) 동의보다 많이 포기시키지 않는다 — 보류
        long grew = sellingCreator(m0, m1);
        assertThat(close(grew, SHARE, t0).outcome()).isEqualTo("HELD");
        assertThat(row(grew, m0)).containsEntry("status", "HELD");
        assertThat(events(grew, "WAIVED")).isEmpty();

        // 운영자 포기 처리 — 사유 400 → 줄 404 → 탈퇴 전 409 → (탈퇴 뒤) 그 달과 그 앞의 굳히지 않은 줄을 포기로
        assertThatThrownBy(() -> kit.inTx(() -> kit.settlements.waive(kit.operator, IP, line(forced, m1), "짧음"))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> kit.inTx(() -> kit.settlements.waive(kit.operator, IP, line(forced, m0.minusYears(3)), "연락이 닿지 않아 포기 처리")))
                .isInstanceOf(EmoticonSettlementNotFoundException.class);
        assertThat(code(() -> kit.inTx(() -> kit.settlements.waive(kit.operator, IP, line(forced, m1), "연락이 닿지 않아 포기 처리"))))
                .as("아직 탈퇴하지 않은 작가").isEqualTo(EmoticonConflictException.SETTLEMENT_NOT_WAIVABLE);
        markWithdrawn(forced, t0);
        kit.audits.clear();
        EmoticonSettlementView.AdminDetail d = kit.inTx(() -> kit.settlements.waive(kit.operator, IP, line(forced, m0), "연락이 닿지 않아 포기 처리"));
        assertThat(d.status()).isEqualTo("WAIVED");
        assertThat(d.waivedAmount()).isEqualTo(SHARE);
        assertThat(row(forced, m0)).containsEntry("confirmed_by", kit.operator).containsEntry("hold_reason", null);
        assertThat(row(forced, m1)).as("뒤 달은 그대로 보류").containsEntry("status", "HELD");
        assertThat(kit.settlements.detail(line(forced, m1)).carriedIn()).as("포기한 앞 달의 양수는 이어지지 않는다").isZero();
        Map<String, Object> event = events(forced, "WAIVED").get(0);
        assertThat(event.get("operator_id")).isEqualTo(kit.operator);
        assertThat(event.get("note")).isEqualTo("연락이 닿지 않아 포기 처리");
        assertThat(event.get("consented_at")).as("회원 동의가 아니다").isNull();
        assertThat(audits(AuditActions.EMOTICON_SETTLEMENT_WAIVE, forced)).singleElement().satisfies(e -> {
            assertThat(e.actorType()).isEqualTo(AuditActorType.OPERATOR);
            assertThat(e.actorId()).isEqualTo(kit.operator);
            assertThat(e.detail()).containsEntry("basis", "OPERATOR").containsEntry("reason", "연락이 닿지 않아 포기 처리").containsEntry("amount", SHARE)
                    .containsEntry("from", "HELD");
        });
        // 포기한 줄은 다시 포기하지 못한다 · 뒤 달을 마저 끝낸다
        assertThat(code(() -> kit.inTx(() -> kit.settlements.waive(kit.operator, IP, line(forced, m0), "다시 포기해 봅니다"))))
                .isEqualTo(EmoticonConflictException.SETTLEMENT_NOT_WAIVABLE);
        kit.inTx(() -> kit.settlements.waive(kit.operator, IP, line(forced, m1), "연락이 닿지 않아 포기 처리"));
        assertThat(row(forced, m1)).containsEntry("status", "WAIVED");
        assertThat(kit.creatorWithdrawal.summary(forced).orElseThrow().unpaidEarnings()).isZero();

        // 정산 정보가 있는 탈퇴 작가 · 보류가 아닌 줄은 포기 처리를 받지 않는다(지급하면 된다)
        long registered = kit.member();
        kit.registerPayout(registered);
        sale(registered, kit.approvedPack(registered), soldAt(m0));
        markWithdrawn(registered, t0);
        kit.inTx(() -> kit.settlements.hold(kit.operator, IP, line(registered, m0), "확인할 것이 있어요"));
        assertThat(code(() -> kit.inTx(() -> kit.settlements.waive(kit.operator, IP, line(registered, m0), "연락이 닿지 않아 포기 처리"))))
                .isEqualTo(EmoticonConflictException.SETTLEMENT_NOT_WAIVABLE);
        long notHeld = sellingCreator(m0);
        markWithdrawn(notHeld, t0);
        assertThat(code(() -> kit.inTx(() -> kit.settlements.waive(kit.operator, IP, line(notHeld, m0), "연락이 닿지 않아 포기 처리"))))
                .as("예정 줄").isEqualTo(EmoticonConflictException.SETTLEMENT_NOT_WAIVABLE);
    }

    // ── 3. DB 그물 ──────────────────────────────────────────────────────────

    @Test
    void 포기한_줄은_바뀌지_않고_굳힌_확정_줄은_포기가_되지_않으며_포기_기록은_금액이_있어야_한다() {
        long creator = sellingCreator(m0);
        close(creator, SHARE, t0);
        java.sql.Date month = java.sql.Date.valueOf(m0);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_settlements SET status = 'CONFIRMED' WHERE creator_id = ? AND sales_month = ?", creator, month))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_settlements SET status = 'HELD', hold_reason = '다시 보류해요', held_at = now() "
                + "WHERE creator_id = ? AND sales_month = ?", creator, month)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_settlements SET carried_out = 0 WHERE creator_id = ? AND sales_month = ?", creator, month))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_settlement_events SET amount = 0 WHERE creator_id = ? AND action = 'WAIVED'", creator))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO emoticon_settlement_events (id, creator_id, sales_month, action, at, note) VALUES (?, ?, ?, 'WAIVED', now(), "
                + "'금액 없는 포기')", kit.ids.nextId(), creator, month)).as("포기 기록은 금액이 있다").isInstanceOf(DataAccessException.class);

        // 굳힌 확정 줄(정산 정보 있음)은 포기로 바꾸지 못한다
        long registered = kit.member();
        kit.registerPayout(registered);
        sale(registered, kit.approvedPack(registered), soldAt(m0));
        kit.inTx(() -> kit.settlements.confirm(kit.operator, IP, new EmoticonSettlementConfirmRequest(List.of(line(registered, m0)))));
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_settlements SET status = 'WAIVED' WHERE creator_id = ? AND sales_month = ?", registered, month))
                .isInstanceOf(DataAccessException.class);
        // 탈퇴 신청 줄의 포기 동의 금액은 양수이고 동의 시각이 있어야 한다
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO member_withdrawals (id, user_id, status, requested_at, forfeit_emoticon_earnings) VALUES (?, ?, 'SCHEDULED', now(), 100)",
                kit.ids.nextId(), kit.member())).isInstanceOf(DataAccessException.class);
    }

    // ── 4. 심사 목록 줄의 처리 까닭 ─────────────────────────────────────────

    @Test
    void 심사_목록_줄의_cause_는_대기_null_운영자_OPERATOR_작가_탈퇴_CREATOR_WITHDRAWN() {
        kit.clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
        long creator = kit.member();
        long pendingPack = Long.parseLong(kit.submitNew(creator, "대기 팩").id());
        long pendingReview = kit.pendingReview(pendingPack);
        long rejectedPack = Long.parseLong(kit.submitNew(kit.member(), "반려 팩").id());
        long rejectedReview = kit.pendingReview(rejectedPack);
        kit.inTx(() -> kit.reviews.reject(kit.operator, IP, Long.toString(rejectedReview), new EmoticonReviewDecisionRequest(null, "그림 규격이 맞지 않아요")));

        assertThat(cause(kit.reviews.list("PENDING"), pendingReview)).isNull();
        assertThat(cause(kit.reviews.list("REJECTED"), rejectedReview)).isEqualTo("OPERATOR");
        kit.inTx(() -> kit.creatorWithdrawal.withdraw(creator));
        assertThat(cause(kit.reviews.list("REJECTED"), pendingReview)).isEqualTo("CREATOR_WITHDRAWN");
        assertThat(cause(kit.reviews.list("ALL"), rejectedReview)).isEqualTo("OPERATOR");
        // 상세의 처리 기록과 같은 판정
        assertThat(kit.reviews.detail(Long.toString(pendingReview)).decision().cause()).isEqualTo("CREATOR_WITHDRAWN");
    }

    private static String cause(EmoticonAdminView.ReviewList list, long reviewId) {
        return list.items().stream().filter(r -> r.id().equals(Long.toString(reviewId))).findFirst().orElseThrow().cause();
    }

    // ── 5. 문구 ─────────────────────────────────────────────────────────────

    @Test
    void 새_문구는_두_언어에_있다() throws IOException {
        for (String lang : List.of("ko", "en")) {
            Properties p = new Properties();
            try (InputStream in = getClass().getResourceAsStream("/messages/messages_" + lang + ".properties")) {
                p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            }
            for (String key : List.of("error.EMOTICON_SETTLEMENT_NOT_WAIVABLE", "audit.action." + AuditActions.EMOTICON_SETTLEMENT_WAIVE,
                    "error.WITHDRAWAL_EMOTICON_EARNINGS_UNPAID")) {
                assertThat(p.getProperty(key)).as(lang + " " + key).isNotBlank();
            }
            assertThat(p.getProperty("error.WITHDRAWAL_EMOTICON_EARNINGS_UNPAID")).as("금액 자리").contains("{0}");
        }
    }
}
