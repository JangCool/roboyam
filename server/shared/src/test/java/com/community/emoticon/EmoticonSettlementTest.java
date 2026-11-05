package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.emoticon.dto.request.EmoticonSettlementConfirmRequest;
import com.community.emoticon.dto.request.EmoticonSettlementPaidRequest;
import com.community.emoticon.dto.response.EmoticonAdminView;
import com.community.emoticon.dto.response.EmoticonSettlementView;
import com.community.emoticon.dto.response.EmoticonStudioView;
import com.community.emoticon.service.EmoticonPayoutService;
import com.community.emoticon.service.EmoticonSettlementMath;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.DataAccessException;

/**
 * 로보티콘 S4 작가 월 정산(emoticon-contract 「S4」 · 목업 가정 4 · 목업 2차·3차 · 「화면 확정」) — 실제 PostgreSQL 위에서 서비스·DB 그물을 본다.
 * 계산식(부가세 제외 · 수수료 · 원천징수 10원 버림) · 결제 달(KST) · 확정 가능 시각(환불 기한 최댓값) · 확정 굳힘 · 확정 뒤 환불은 환불한 달 · 이월(최소 지급액 · 넘어온 금액만 있는 달 · 음수) ·
 * 확정 조건(달 차례 · 정산 정보) · 보류·풀기 · 지급 기록 · 늦은 결제 · 확정과 환불 동시 · 계좌 원문 보기 · 관리자 목록 · 작가 내역 · 트리거·CHECK.
 * 시계는 2031-05-20 12:00 KST 로 둔다(3·4월은 확정할 수 있고 5월은 진행 중).
 */
class EmoticonSettlementTest extends EmoticonTestBase {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = kst("2031-05-20T12:00");
    private static final int PRICE = 2500;

    private Map<String, Object> settingsBefore;

    @BeforeEach
    void standardSettings() {
        kit.clock.set(NOW);
        settingsBefore = kit.settingsRow();
        kit.jdbc.update("UPDATE emoticon_settings SET fee_rate_percent = 30, payout_minimum = 10000, settlement_day = 10, refund_days = 7 WHERE id = 1");
    }

    @AfterEach
    void restore() {
        kit.restoreSettings(settingsBefore);
    }

    private static Instant kst(String local) {
        return LocalDateTime.parse(local).atZone(SEOUL).toInstant();
    }

    // ── 준비 ────────────────────────────────────────────────────────────────

    /** 정산 정보(개인)를 등록한 작가와 그 작가의 팩 — {작가, 팩} */
    private long[] creatorWithPack(boolean payout) {
        long creator = kit.member();
        if (payout) {
            kit.registerPayout(creator);
        }
        return new long[] {creator, kit.approvedPack(creator)};
    }

    /** 결제된 판매 하나(결제 시각 · 셀프 환불 기한 = 결제 + 7일) — 구매 ID. 구매 회원은 판매마다 새 번호 */
    private long sale(long creator, long pack, int amount, Instant paidAt) {
        long purchase = kit.ids.nextId();
        long order = kit.ids.nextId();
        long buyer = kit.ids.nextId();
        String paymentId = "pay_" + String.format("%032d", order).substring(0, 32);
        kit.jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, status, expires_at, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ROBOTIKON_PACK', ?, '시험', ?, 'READY', ?, ?, ?)", order, paymentId, buyer, purchase, amount, ts(paidAt.plus(Duration.ofMinutes(30))),
                ts(paidAt), ts(paidAt));
        kit.jdbc.update("INSERT INTO emoticon_purchases (id, user_id, pack_id, creator_id, pack_name, amount, status, refund_notice_agreed_at, expires_at, created_at, "
                + "payment_order_id, paid_at, refund_until) VALUES (?, ?, ?, ?, '정산 시험 팩', ?, 'PAID', ?, ?, ?, ?, ?, ?)", purchase, buyer, pack, creator, amount, ts(paidAt),
                ts(paidAt.plus(Duration.ofMinutes(30))), ts(paidAt), order, ts(paidAt), ts(paidAt.plus(Duration.ofDays(7))));
        return purchase;
    }

    private List<Long> sales(long creator, long pack, int count, Instant first) {
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(sale(creator, pack, PRICE, first.plus(Duration.ofMinutes(i))));
        }
        return out;
    }

    /** 환불(운영자 예외 환불처럼 — 시각 {@code at}) */
    private void refund(long purchase, Instant at) {
        kit.jdbc.update("UPDATE emoticon_purchases SET status = 'REFUNDED', refunded_at = ?, refund_kind = 'EXCEPTION', refund_reason = '시험 예외 환불' WHERE id = ?", ts(at),
                purchase);
    }

    private static Timestamp ts(Instant at) {
        return Timestamp.from(at);
    }

    private static String id(long creator, String month) {
        return month.replace("-", "") + "-" + creator;
    }

    private EmoticonSettlementView.BulkResult confirm(String... ids) {
        return kit.inTx(() -> kit.settlements.confirm(kit.operator, "127.0.0.1", new EmoticonSettlementConfirmRequest(List.of(ids))));
    }

    private EmoticonSettlementView.AdminDetail detail(long creator, String month) {
        return kit.settlements.detail(id(creator, month));
    }

    /** 관리자 목록에서 그 작가 줄(없으면 null) */
    private EmoticonSettlementView.AdminRow listed(String month, long creator) {
        return kit.settlements.list(month).items().stream().filter(r -> r.creator().memberNo().equals(Long.toString(creator))).findFirst().orElse(null);
    }

    private EmoticonSettlementView.AdminDetail hold(long creator, String month, String reason) {
        return kit.inTx(() -> kit.settlements.hold(kit.operator, "127.0.0.1", id(creator, month), reason));
    }

    private EmoticonSettlementView.AdminDetail unhold(long creator, String month) {
        return kit.inTx(() -> kit.settlements.unhold(kit.operator, "127.0.0.1", id(creator, month)));
    }

    private EmoticonSettlementView.AdminDetail paid(long creator, String month, String date, String memo) {
        return kit.inTx(() -> kit.settlements.markPaid(kit.operator, "127.0.0.1", id(creator, month), new EmoticonSettlementPaidRequest(date, memo)));
    }

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

    private static Map<String, ?> fields(Executable call) {
        try {
            call.execute();
        } catch (ValidationException e) {
            return e.getFields();
        } catch (Throwable e) {
            throw new AssertionError("400 VALIDATION 이 아니다: " + e, e);
        }
        throw new AssertionError("오류가 나지 않았다");
    }

    private LocalDate settlementMonth(long purchase) {
        Date d = kit.jdbc.queryForObject("SELECT settlement_month FROM emoticon_purchases WHERE id = ?", Date.class, purchase);
        return d == null ? null : d.toLocalDate();
    }

    private LocalDate refundSettlementMonth(long purchase) {
        Date d = kit.jdbc.queryForObject("SELECT refund_settlement_month FROM emoticon_purchases WHERE id = ?", Date.class, purchase);
        return d == null ? null : d.toLocalDate();
    }

    private List<String> auditActions(String targetId) {
        return kit.audits.stream().filter(e -> targetId.equals(e.targetId())).map(AuditEvent::action).toList();
    }

    // ── 계산식 ──────────────────────────────────────────────────────────────

    @Test
    void 계산식은_계약_예시와_같고_원천징수는_10원_미만_버림_이월은_원천징수_전_금액() {
        // 2,500 → 정산 기준 2,272 → 작가 몫 1,590 → 원천징수 50 → 지급 1,540(최소 지급액 0)
        EmoticonSettlementMath.Month one = EmoticonSettlementMath.month(2500, 0, 30, 0, true, 0);
        assertThat(one.base()).isEqualTo(2272);
        assertThat(one.vat()).isEqualTo(228);
        assertThat(one.share()).isEqualTo(1590);
        assertThat(one.fee()).isEqualTo(682);
        assertThat(one.withholding()).isEqualTo(50);
        assertThat(one.payout()).isEqualTo(1540);
        assertThat(one.carriedOut()).isZero();
        // 사업자는 원천징수 0
        assertThat(EmoticonSettlementMath.month(2500, 0, 30, 0, false, 0).payout()).isEqualTo(1590);
        // 최소 지급액 미만 — 원천징수 전 금액 전부를 넘긴다
        EmoticonSettlementMath.Month below = EmoticonSettlementMath.month(2500, 0, 30, 0, true, 10000);
        assertThat(below.payout()).isZero();
        assertThat(below.withholding()).isZero();
        assertThat(below.carriedOut()).isEqualTo(1590);
        // 넘어온 금액을 더한 합에서 원천징수(⌊11,135 × 3.3% ÷ 10⌋ × 10 = 360)
        EmoticonSettlementMath.Month carried = EmoticonSettlementMath.month(5000, 0, 30, 7954, true, 10000);
        assertThat(carried.share()).isEqualTo(3181);
        assertThat(carried.withholding()).isEqualTo(360);
        assertThat(carried.payout()).isEqualTo(10775);
        assertThat(EmoticonSettlementMath.withholding(1599)).isEqualTo(50);
        assertThat(EmoticonSettlementMath.withholding(0)).isZero();
        // 순판매 음수(확정 뒤 환불이 판매보다 큼) — 부호만 바꾼 같은 식, 합이 0 이하면 넘긴다
        EmoticonSettlementMath.Month negative = EmoticonSettlementMath.month(2500, 7500, 30, 0, true, 10000);
        assertThat(negative.base()).isEqualTo(-4545);
        assertThat(negative.vat()).isEqualTo(-455);
        assertThat(negative.share()).isEqualTo(-3181);
        assertThat(negative.payout()).isZero();
        assertThat(negative.carriedOut()).isEqualTo(-3181);
        // 최소 지급액 0 이어도 합이 0 이하면 지급하지 않는다
        assertThat(EmoticonSettlementMath.month(0, 2500, 30, 0, true, 0).payout()).isZero();
    }

    // ── 예정 계산 ───────────────────────────────────────────────────────────

    @Test
    void 예정_줄은_조회_때_결제_달_KST로_계산하고_확정_가능_시각은_그_달_판매의_환불_기한_최댓값() {
        long[] cp = creatorWithPack(true);
        long creator = cp[0];
        long pack = cp[1];
        sale(creator, pack, PRICE, kst("2031-03-31T23:30")); // 3월(KST) — UTC 로는 3-31 14:30
        long april = sale(creator, pack, PRICE, kst("2031-04-01T00:10")); // 4월(KST) — UTC 로는 3-31 15:10
        sale(creator, pack, 3500, kst("2031-04-28T09:00")); // 환불 기한 5-05 09:00 → 4월 줄 확정 가능 시각
        EmoticonSettlementView.AdminDetail march = detail(creator, "2031-03");
        assertThat(march.salesCount()).isEqualTo(1);
        assertThat(march.status()).isEqualTo("ESTIMATED");
        assertThat(march.closesAt()).isEqualTo(kst("2031-04-07T23:30")); // 3-31 23:30 판매의 셀프 환불 기한(다음 달 1일보다 늦다)
        assertThat(march.payoutDate()).isEqualTo("2031-04-10");
        EmoticonSettlementView.AdminDetail aprilRow = detail(creator, "2031-04");
        assertThat(aprilRow.salesCount()).isEqualTo(2);
        assertThat(aprilRow.salesAmount()).isEqualTo(6000);
        assertThat(aprilRow.baseAmount()).isEqualTo(5454);
        assertThat(aprilRow.vatAmount()).isEqualTo(546);
        assertThat(aprilRow.shareAmount()).isEqualTo(3817);
        assertThat(aprilRow.feeAmount()).isEqualTo(1637);
        assertThat(aprilRow.feeRatePercent()).isEqualTo(30);
        assertThat(aprilRow.payeeType()).isEqualTo("INDIVIDUAL");
        assertThat(aprilRow.closesAt()).isEqualTo(kst("2031-05-05T09:00"));
        assertThat(aprilRow.packs()).hasSize(1);
        assertThat(aprilRow.packs().get(0).salesCount()).isEqualTo(2);
        assertThat(aprilRow.packs().get(0).price()).isEqualTo(3500); // 그 달 마지막 판매 금액
        assertThat(aprilRow.events()).extracting(EmoticonSettlementView.Event::action).containsExactly("CREATED");
        assertThat(aprilRow.events().get(0).at()).isEqualTo(kst("2031-04-01T00:10"));
        // 예정 줄은 저장하지 않는다 — 지금 설정을 따라 다시 계산된다
        assertThat(kit.count("SELECT count(*) FROM emoticon_settlements WHERE creator_id = ?", creator)).isZero();
        kit.jdbc.update("UPDATE emoticon_settings SET fee_rate_percent = 20 WHERE id = 1");
        assertThat(detail(creator, "2031-04").shareAmount()).isEqualTo(4363);
        // 확정 가능 시각 전이면 건너뛴다
        kit.clock.set(kst("2031-05-05T08:59"));
        assertThat(confirm(id(creator, "2031-03"), id(creator, "2031-04")).skippedItems()).extracting(EmoticonSettlementView.Skipped::reason)
                .containsExactly("NOT_CLOSED"); // 3월은 확정, 4월은 아직
        kit.clock.set(kst("2031-05-05T09:00"));
        assertThat(confirm(id(creator, "2031-04")).confirmed()).isEqualTo(1);
        assertThat(settlementMonth(april)).isEqualTo(LocalDate.of(2031, 4, 1));
    }

    // ── 확정 · 확정 뒤 환불 ────────────────────────────────────────────────

    @Test
    void 확정하면_금액이_굳고_구매에_정산_달이_찍히며_확정_전_환불은_판매_달_확정_뒤_환불은_환불한_달에서_뺀다() {
        long[] cp = creatorWithPack(true);
        long creator = cp[0];
        List<Long> march = sales(creator, cp[1], 8, kst("2031-03-10T12:00"));
        refund(march.get(0), kst("2031-04-03T10:00")); // 예정 ~ 확정 사이 환불 → 3월에서
        EmoticonSettlementView.BulkResult result = confirm(id(creator, "2031-03"));
        assertThat(result.confirmed()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        EmoticonSettlementView.AdminDetail frozen = detail(creator, "2031-03");
        assertThat(frozen.status()).isEqualTo("CONFIRMED");
        assertThat(frozen.salesAmount()).isEqualTo(20000);
        assertThat(frozen.refundAmount()).isEqualTo(2500);
        assertThat(frozen.baseAmount()).isEqualTo(15909);
        assertThat(frozen.vatAmount()).isEqualTo(1591);
        assertThat(frozen.shareAmount()).isEqualTo(11136);
        assertThat(frozen.feeAmount()).isEqualTo(4773);
        assertThat(frozen.withholdingAmount()).isEqualTo(360);
        assertThat(frozen.payoutAmount()).isEqualTo(10776);
        assertThat(frozen.carriedOut()).isZero();
        assertThat(frozen.confirmedAt()).isEqualTo(NOW);
        assertThat(frozen.packs().get(0).salesCount()).isEqualTo(8);
        assertThat(frozen.packs().get(0).refundCount()).isEqualTo(1);
        assertThat(frozen.events()).extracting(EmoticonSettlementView.Event::action).containsExactly("CONFIRMED", "CREATED");
        march.forEach(p -> assertThat(settlementMonth(p)).isEqualTo(LocalDate.of(2031, 3, 1)));
        assertThat(refundSettlementMonth(march.get(0))).isEqualTo(LocalDate.of(2031, 3, 1));
        assertThat(auditActions(id(creator, "2031-03"))).containsExactly(AuditActions.EMOTICON_SETTLEMENT_CONFIRM);

        // 확정 뒤 — 설정을 바꾸고 3월 판매를 환불해도 3월 줄은 그대로, 환불은 환불한 달(5월)에서 뺀다
        kit.jdbc.update("UPDATE emoticon_settings SET fee_rate_percent = 20, payout_minimum = 0 WHERE id = 1");
        refund(march.get(1), kst("2031-05-10T10:00"));
        EmoticonSettlementView.AdminDetail still = detail(creator, "2031-03");
        assertThat(still.refundAmount()).isEqualTo(2500);
        assertThat(still.payoutAmount()).isEqualTo(10776);
        assertThat(still.feeRatePercent()).isEqualTo(30);
        assertThat(listed("2031-04", creator)).isNull(); // 4월은 판매·환불·넘어온 금액이 없다
        kit.jdbc.update("UPDATE emoticon_settings SET fee_rate_percent = 30, payout_minimum = 10000 WHERE id = 1");
        EmoticonSettlementView.AdminDetail may = detail(creator, "2031-05");
        assertThat(may.salesAmount()).isZero();
        assertThat(may.refundAmount()).isEqualTo(2500);
        assertThat(may.baseAmount()).isEqualTo(-2272);
        assertThat(may.shareAmount()).isEqualTo(-1590);
        assertThat(may.payoutAmount()).isZero();
        assertThat(may.carriedOut()).isEqualTo(-1590);
        assertThat(may.packs().get(0).refundCount()).isEqualTo(1);
        assertThat(refundSettlementMonth(march.get(1))).isNull();
        // 작가 스튜디오 이번 달 요약도 같은 규칙(확정 뒤 환불은 이번 달)
        EmoticonStudioView.Sales summary = kit.studio.overview(creator).sales();
        assertThat(summary.month()).isEqualTo("2031-05");
        assertThat(summary.salesCount()).isZero();
        assertThat(summary.refundAmount()).isEqualTo(2500);
        assertThat(summary.expectedPayout()).isEqualTo(-1590);
    }

    // ── 이월 ────────────────────────────────────────────────────────────────

    @Test
    void 최소_지급액_미만은_원천징수_전_금액을_넘기고_다음_달에_넘어온_금액을_더해_원천징수한다() {
        long[] cp = creatorWithPack(true);
        long creator = cp[0];
        sales(creator, cp[1], 5, kst("2031-03-10T12:00"));
        sales(creator, cp[1], 2, kst("2031-04-10T12:00"));
        EmoticonSettlementView.AdminDetail march = detail(creator, "2031-03");
        assertThat(march.shareAmount()).isEqualTo(7954);
        assertThat(march.payoutAmount()).isZero();
        assertThat(march.withholdingAmount()).isZero();
        assertThat(march.carriedOut()).isEqualTo(7954);
        EmoticonSettlementView.AdminDetail april = detail(creator, "2031-04");
        assertThat(april.carriedIn()).isEqualTo(7954); // 예정 줄도 앞 달 예정의 넘김을 받아 보인다
        assertThat(april.withholdingAmount()).isEqualTo(360);
        assertThat(april.payoutAmount()).isEqualTo(10775);
        // 달 차례 — 앞 달이 굳기 전에는 확정하지 않는다
        assertThat(confirm(id(creator, "2031-04")).skippedItems()).extracting(EmoticonSettlementView.Skipped::reason).containsExactly("PREVIOUS_OPEN");
        assertThat(confirm(id(creator, "2031-03")).confirmed()).isEqualTo(1);
        assertThat(code(() -> paid(creator, "2031-03", "2031-05-15", null))).isEqualTo("EMOTICON_SETTLEMENT_NOTHING_TO_PAY");
        // 같은 요청에 두 달 — 앞 달부터 굳는다(이미 굳은 3월은 건너뛴다)
        EmoticonSettlementView.BulkResult both = confirm(id(creator, "2031-04"), id(creator, "2031-03"));
        assertThat(both.confirmed()).isEqualTo(1);
        assertThat(both.skippedItems()).extracting(EmoticonSettlementView.Skipped::reason).containsExactly("NOT_ESTIMATED");
        EmoticonSettlementView.AdminDetail frozenApril = detail(creator, "2031-04");
        assertThat(frozenApril.status()).isEqualTo("CONFIRMED");
        assertThat(frozenApril.carriedIn()).isEqualTo(7954);
        assertThat(frozenApril.payoutAmount()).isEqualTo(10775);
        assertThat(kit.jdbc.queryForObject("SELECT carried_in FROM emoticon_settlements WHERE creator_id = ? AND sales_month = '2031-04-01'", Long.class, creator))
                .isEqualTo(7954);
        // 작가 내역 — 넘긴 달은 지급일 없음 · 최근 달 먼저
        EmoticonSettlementView.StudioList studio = kit.settlements.studio(creator);
        assertThat(studio.items()).extracting(EmoticonSettlementView.StudioRow::month).containsExactly("2031-04", "2031-03");
        EmoticonSettlementView.StudioRow marchRow = studio.items().get(1);
        assertThat(marchRow.carriedOver()).isTrue();
        assertThat(marchRow.carriedOut()).isEqualTo(7954);
        assertThat(marchRow.payDate()).isNull();
        assertThat(marchRow.status()).isEqualTo("CONFIRMED");
        assertThat(studio.items().get(0).payDate()).isEqualTo("2031-05-12"); // 5월 10일이 토요일 → 다음 업무일 월요일(후속 결정 4)
        assertThat(studio.items().get(0).carriedIn()).isEqualTo(7954);
    }

    @Test
    void 넘어온_금액만_있는_달도_줄이_생기고_이번_달까지_이어진다() {
        long[] cp = creatorWithPack(true);
        long creator = cp[0];
        sales(creator, cp[1], 5, kst("2031-03-10T12:00"));
        EmoticonSettlementView.AdminRow april = listed("2031-04", creator);
        assertThat(april).isNotNull();
        assertThat(april.salesCount()).isZero();
        assertThat(april.carriedIn()).isEqualTo(7954);
        assertThat(april.carriedOut()).isEqualTo(7954);
        assertThat(april.closesAt()).isEqualTo(kst("2031-05-01T00:00"));
        assertThat(kit.settlements.studio(creator).items()).extracting(EmoticonSettlementView.StudioRow::month).containsExactly("2031-05", "2031-04", "2031-03");
        // 최소 지급액을 낮추면 넘어온 금액만으로 지급한다(예정 줄은 지금 설정)
        kit.jdbc.update("UPDATE emoticon_settings SET payout_minimum = 5000 WHERE id = 1");
        EmoticonSettlementView.AdminRow paidOut = listed("2031-03", creator);
        assertThat(paidOut.payoutAmount()).isEqualTo(7954 - 260);
        assertThat(listed("2031-04", creator)).isNull();
    }

    @Test
    void 확정_뒤_환불이_그_달_판매보다_크면_음수를_다음_달로_넘겨_뺀다() {
        long[] cp = creatorWithPack(true);
        long creator = cp[0];
        List<Long> march = sales(creator, cp[1], 8, kst("2031-03-10T12:00"));
        assertThat(confirm(id(creator, "2031-03")).confirmed()).isEqualTo(1);
        assertThat(detail(creator, "2031-03").payoutAmount()).isEqualTo(12316);
        sale(creator, cp[1], PRICE, kst("2031-04-10T12:00"));
        for (int i = 0; i < 3; i++) {
            refund(march.get(i), kst("2031-04-12T10:00").plusSeconds(i));
        }
        EmoticonSettlementView.AdminDetail april = detail(creator, "2031-04");
        assertThat(april.salesAmount()).isEqualTo(2500);
        assertThat(april.refundAmount()).isEqualTo(7500);
        assertThat(april.baseAmount()).isEqualTo(-4545);
        assertThat(april.vatAmount()).isEqualTo(-455);
        assertThat(april.shareAmount()).isEqualTo(-3181);
        assertThat(april.feeAmount()).isEqualTo(-1364);
        assertThat(april.payoutAmount()).isZero();
        assertThat(april.carriedOut()).isEqualTo(-3181);
        assertThat(confirm(id(creator, "2031-04")).confirmed()).isEqualTo(1); // DB CHECK 가 음수 식도 받는다
        sales(creator, cp[1], 8, kst("2031-05-05T12:00"));
        EmoticonSettlementView.AdminDetail may = detail(creator, "2031-05");
        assertThat(may.carriedIn()).isEqualTo(-3181);
        assertThat(may.shareAmount()).isEqualTo(12726);
        assertThat(may.payoutAmount()).isZero(); // 12,726 − 3,181 = 9,545 < 10,000 → 넘긴다
        assertThat(may.carriedOut()).isEqualTo(9545);
    }

    // ── 확정 조건 ───────────────────────────────────────────────────────────

    @Test
    void 확정은_맞지_않는_줄을_까닭과_함께_건너뛴다() {
        long[] cp = creatorWithPack(true);
        long creator = cp[0];
        sales(creator, cp[1], 8, kst("2031-03-10T12:00"));
        sales(creator, cp[1], 1, kst("2031-05-02T12:00"));
        long[] noPayout = creatorWithPack(false);
        sales(noPayout[0], noPayout[1], 8, kst("2031-03-10T12:00"));
        EmoticonSettlementView.BulkResult r = confirm("엉터리", id(creator, "2031-01"), id(creator, "2031-05"), id(noPayout[0], "2031-03"), id(creator, "2031-03"),
                id(creator, "2031-03"));
        assertThat(r.confirmed()).isEqualTo(1);
        assertThat(r.skipped()).isEqualTo(4);
        assertThat(r.skippedItems()).extracting(s -> s.id() + ":" + s.reason()).containsExactlyInAnyOrder("엉터리:NOT_FOUND", id(creator, "2031-01") + ":NOT_FOUND",
                id(creator, "2031-05") + ":NOT_CLOSED", id(noPayout[0], "2031-03") + ":PAYOUT_ACCOUNT_MISSING");
        // 정산 정보가 없으면 예정 그대로(보류로 두지 않는다) · 작가 구분 없음 · 원천징수는 개인으로 어림
        EmoticonSettlementView.AdminDetail missing = detail(noPayout[0], "2031-03");
        assertThat(missing.status()).isEqualTo("ESTIMATED");
        assertThat(missing.payeeType()).isNull();
        assertThat(missing.account()).isNull();
        assertThat(missing.withholdingAmount()).isEqualTo(410);
        assertThat(confirm(id(creator, "2031-03")).skippedItems()).extracting(EmoticonSettlementView.Skipped::reason).containsExactly("NOT_ESTIMATED");
        assertThat(fields(() -> confirm()).keySet()).containsExactly("ids");
        List<String> many = Collections.nCopies(201, id(creator, "2031-03"));
        assertThat(fields(() -> kit.inTx(() -> kit.settlements.confirm(kit.operator, "127.0.0.1", new EmoticonSettlementConfirmRequest(many)))).keySet())
                .containsExactly("ids");
        assertThat(code(() -> detail(creator, "2031-02"))).isEqualTo("EMOTICON_SETTLEMENT_NOT_FOUND");
        assertThat(code(() -> kit.settlements.detail("2031-13-" + creator))).isEqualTo("EMOTICON_SETTLEMENT_NOT_FOUND");
    }

    @Test
    void 사업자_작가는_원천징수가_없고_확정_뒤_구분을_바꿔도_굳힌_값은_그대로() {
        long[] cp = creatorWithPack(true);
        long creator = cp[0];
        kit.jdbc.update("UPDATE emoticon_payout_accounts SET payee_type = 'BUSINESS', business_number_enc = ?, business_number_masked = '123-45-*****' WHERE user_id = ?",
                kit.cipher.encrypt(EmoticonPayoutService.BUSINESS_NUMBER, "1234567891"), creator);
        sales(creator, cp[1], 8, kst("2031-03-10T12:00"));
        EmoticonSettlementView.AdminDetail estimated = detail(creator, "2031-03");
        assertThat(estimated.payeeType()).isEqualTo("BUSINESS");
        assertThat(estimated.businessNoMasked()).isEqualTo("123-45-*****");
        assertThat(estimated.withholdingAmount()).isZero();
        assertThat(estimated.payoutAmount()).isEqualTo(12726);
        assertThat(estimated.account().bank()).isEqualTo("KB");
        assertThat(estimated.account().maskedNumber()).isEqualTo("****1234");
        assertThat(estimated.account().holderMasked()).isEqualTo("홍*동");
        confirm(id(creator, "2031-03"));
        kit.jdbc.update("UPDATE emoticon_payout_accounts SET payee_type = 'INDIVIDUAL', business_number_enc = NULL, business_number_masked = NULL WHERE user_id = ?", creator);
        EmoticonSettlementView.AdminDetail frozen = detail(creator, "2031-03");
        assertThat(frozen.payeeType()).isEqualTo("BUSINESS");
        assertThat(frozen.withholdingAmount()).isZero();
        assertThat(frozen.payoutAmount()).isEqualTo(12726);
    }

    // ── 보류 · 지급 기록 ────────────────────────────────────────────────────

    @Test
    void 보류는_예정_확정만_되고_풀면_보류_전_상태로_돌아가며_굳힌_금액은_그대로() {
        long[] cp = creatorWithPack(true);
        long creator = cp[0];
        List<Long> march = sales(creator, cp[1], 8, kst("2031-03-10T12:00"));
        assertThat(fields(() -> hold(creator, "2031-03", "짧음")).keySet()).containsExactly("reason");
        assertThat(code(() -> hold(creator, "2031-02", "예금주를 확인하고 있어요"))).isEqualTo("EMOTICON_SETTLEMENT_NOT_FOUND");
        assertThat(code(() -> unhold(creator, "2031-03"))).isEqualTo("EMOTICON_SETTLEMENT_NOT_HELD");
        // 예정 → 보류: 줄이 생기지만 금액은 굳히지 않는다(그사이 판매도 들어간다)
        EmoticonSettlementView.AdminDetail held = hold(creator, "2031-03", "예금주를 확인하고 있어요");
        assertThat(held.status()).isEqualTo("HELD");
        assertThat(held.hold().reason()).isEqualTo("예금주를 확인하고 있어요");
        assertThat(held.hold().by()).isNotNull();
        assertThat(held.confirmedAt()).isNull();
        sale(creator, cp[1], PRICE, kst("2031-03-20T12:00"));
        assertThat(detail(creator, "2031-03").salesCount()).isEqualTo(9);
        assertThat(kit.settlements.studio(creator).items().get(0).holdReason()).isEqualTo("예금주를 확인하고 있어요");
        assertThat(confirm(id(creator, "2031-03")).skippedItems()).extracting(EmoticonSettlementView.Skipped::reason).containsExactly("NOT_ESTIMATED");
        assertThat(unhold(creator, "2031-03").status()).isEqualTo("ESTIMATED");
        // 확정 → 보류 → 풀기 = 확정(굳힌 금액 그대로 — 그사이 환불은 환불한 달)
        confirm(id(creator, "2031-03"));
        long payout = detail(creator, "2031-03").payoutAmount();
        assertThat(hold(creator, "2031-03", "작가 요청으로 지급을 미뤄요").status()).isEqualTo("HELD");
        refund(march.get(0), kst("2031-05-12T10:00"));
        EmoticonSettlementView.AdminDetail back = unhold(creator, "2031-03");
        assertThat(back.status()).isEqualTo("CONFIRMED");
        assertThat(back.payoutAmount()).isEqualTo(payout);
        assertThat(back.refundAmount()).isZero();
        assertThat(back.hold()).isNull();
        assertThat(detail(creator, "2031-05").refundAmount()).isEqualTo(2500);
        paid(creator, "2031-03", "2031-05-15", "정기 이체");
        assertThat(code(() -> hold(creator, "2031-03", "지급 뒤에 보류할 수 없어요"))).isEqualTo("EMOTICON_SETTLEMENT_NOT_HOLDABLE");
        assertThat(detail(creator, "2031-03").events()).extracting(EmoticonSettlementView.Event::action)
                .containsExactly("PAID", "UNHELD", "HELD", "CONFIRMED", "UNHELD", "HELD", "CREATED");
        assertThat(detail(creator, "2031-03").events().get(5).note()).isEqualTo("예금주를 확인하고 있어요");
        assertThat(auditActions(id(creator, "2031-03"))).containsExactly(AuditActions.EMOTICON_SETTLEMENT_HOLD, AuditActions.EMOTICON_SETTLEMENT_UNHOLD,
                AuditActions.EMOTICON_SETTLEMENT_CONFIRM, AuditActions.EMOTICON_SETTLEMENT_HOLD, AuditActions.EMOTICON_SETTLEMENT_UNHOLD,
                AuditActions.EMOTICON_SETTLEMENT_PAID);
    }

    @Test
    void 지급_완료_기록은_확정한_줄만_이체한_날과_메모를_남긴다() {
        long[] cp = creatorWithPack(true);
        long creator = cp[0];
        sales(creator, cp[1], 8, kst("2031-03-10T12:00"));
        assertThat(fields(() -> paid(creator, "2031-03", null, null)).keySet()).containsExactly("transferDate");
        assertThat(fields(() -> paid(creator, "2031-03", "2031/05/15", null)).keySet()).containsExactly("transferDate");
        assertThat(fields(() -> paid(creator, "2031-03", "2031-02-30", null)).keySet()).containsExactly("transferDate");
        assertThat(fields(() -> paid(creator, "2031-03", "2031-05-21", null)).keySet()).containsExactly("transferDate"); // 오늘(KST 5-20) 뒤
        assertThat(fields(() -> paid(creator, "2031-03", "2031-05-15", "가".repeat(201))).keySet()).containsExactly("memo");
        assertThat(code(() -> paid(creator, "2031-03", "2031-05-15", null))).isEqualTo("EMOTICON_SETTLEMENT_NOT_CONFIRMED");
        confirm(id(creator, "2031-03"));
        EmoticonSettlementView.AdminDetail done = paid(creator, "2031-03", "2031-05-20", "  ");
        assertThat(done.status()).isEqualTo("PAID");
        assertThat(done.paid().transferDate()).isEqualTo("2031-05-20");
        assertThat(done.paid().memo()).isNull();
        assertThat(done.paid().at()).isEqualTo(NOW);
        assertThat(done.events().get(0).action()).isEqualTo("PAID");
        assertThat(code(() -> paid(creator, "2031-03", "2031-05-20", "다시"))).isEqualTo("EMOTICON_SETTLEMENT_NOT_CONFIRMED");
        // 작가 내역 — 3월 줄 하나(4·5월은 판매·넘어온 금액이 없어 줄이 없다)
        assertThat(kit.settlements.studio(creator).items()).extracting(EmoticonSettlementView.StudioRow::status).containsExactly("PAID");
    }

    @Test
    void 이체일은_업무일만_받고_지급_예정일은_토_일_공휴일이면_다음_업무일로_미룬다() {
        long[] cp = creatorWithPack(true);
        long creator = cp[0];
        sales(creator, cp[1], 8, kst("2031-03-10T12:00"));
        confirm(id(creator, "2031-03"));
        // 2031 년은 공휴일 표에 없다 — 시험 동안만 5월 19일(월)을 공휴일로 넣는다
        kit.jdbc.update("INSERT INTO public_holidays (holiday_date, name, kind) VALUES ('2031-05-19', '시험 임시공휴일', 'TEMPORARY')");
        try {
            assertThat(fields(() -> paid(creator, "2031-03", "2031-05-17", null))).containsKey("transferDate"); // 토
            assertThat(fields(() -> paid(creator, "2031-03", "2031-05-18", null))).containsKey("transferDate"); // 일
            assertThat(fields(() -> paid(creator, "2031-03", "2031-05-19", null))).containsKey("transferDate"); // 공휴일
            assertThat(kit.jdbc.queryForObject("SELECT status FROM emoticon_settlements WHERE creator_id = ? AND sales_month = '2031-03-01'", String.class, creator))
                    .isEqualTo("CONFIRMED");
            assertThat(paid(creator, "2031-03", "2031-05-16", null).paid().transferDate()).isEqualTo("2031-05-16"); // 금 — 업무일
            // 4월 판매의 지급 예정일 5월 10일(토) → 12일(월). 5월 판매는 6월 10일(화) 그대로
            sales(creator, cp[1], 8, kst("2031-04-10T12:00"));
            assertThat(listed("2031-04", creator).payoutDate()).isEqualTo("2031-05-12");
            sales(creator, cp[1], 8, kst("2031-05-02T12:00"));
            assertThat(listed("2031-05", creator).payoutDate()).isEqualTo("2031-06-10");
            // 지급 예정일이 공휴일이면 그다음 업무일(6월 10일을 시험 공휴일로)
            kit.jdbc.update("INSERT INTO public_holidays (holiday_date, name, kind) VALUES ('2031-06-10', '시험 임시공휴일', 'TEMPORARY')");
            assertThat(listed("2031-05", creator).payoutDate()).isEqualTo("2031-06-11");
        } finally {
            kit.jdbc.update("DELETE FROM public_holidays WHERE holiday_date IN ('2031-05-19', '2031-06-10')");
        }
    }

    // ── 늦은 결제 · 동시성 ──────────────────────────────────────────────────

    @Test
    void 확정한_달에_늦게_들어온_결제는_다음에_열린_달로_넘긴다() {
        long[] cp = creatorWithPack(true);
        long creator = cp[0];
        sales(creator, cp[1], 8, kst("2031-03-10T12:00"));
        confirm(id(creator, "2031-03"));
        long late = sale(creator, cp[1], PRICE, kst("2031-03-31T23:00"));
        assertThat(detail(creator, "2031-03").salesCount()).isEqualTo(8);
        EmoticonSettlementView.AdminDetail april = detail(creator, "2031-04");
        assertThat(april.salesCount()).isEqualTo(1);
        assertThat(april.salesAmount()).isEqualTo(2500);
        assertThat(confirm(id(creator, "2031-04")).confirmed()).isEqualTo(1);
        assertThat(settlementMonth(late)).isEqualTo(LocalDate.of(2031, 4, 1));
    }

    @Test
    void 확정과_환불이_동시에_와도_환불은_한_달에서_한_번만_뺀다() throws Exception {
        long[] cp = creatorWithPack(true);
        long creator = cp[0];
        List<Long> march = sales(creator, cp[1], 8, kst("2031-03-10T12:00"));
        long target = march.get(3);
        List<Callable<Object>> jobs = List.of(() -> kit.inTx(() -> {
            kit.jdbc.queryForList("SELECT id FROM emoticon_purchases WHERE id = ? FOR UPDATE", Long.class, target);
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            refund(target, kst("2031-05-19T10:00"));
            return "refund";
        }), () -> confirm(id(creator, "2031-03")));
        List<Object> results = EmoticonTestKit.runTogether(jobs);
        assertThat(results).noneMatch(r -> r instanceof Throwable);
        long marchRefund = detail(creator, "2031-03").refundAmount();
        EmoticonSettlementView.AdminRow may = listed("2031-05", creator);
        long mayRefund = may == null ? 0 : may.refundAmount();
        assertThat(marchRefund + mayRefund).isEqualTo(2500);
        assertThat(refundSettlementMonth(target) == null).isEqualTo(mayRefund == 2500);
    }

    // ── 계좌 원문 보기 ──────────────────────────────────────────────────────

    @Test
    void 정산_줄_주소의_계좌_원문_보기는_사유_감사_처리_기록을_남긴다() {
        long[] cp = creatorWithPack(true);
        long creator = cp[0];
        sales(creator, cp[1], 8, kst("2031-03-10T12:00"));
        String id = id(creator, "2031-03");
        assertThat(fields(() -> kit.inTx(() -> kit.settlements.revealAccount(kit.operator, "127.0.0.1", id, "짧음"))).keySet()).containsExactly("reason");
        assertThat(code(() -> kit.inTx(() -> kit.settlements.revealAccount(kit.operator, "127.0.0.1", id(creator, "2031-02"), "이체하려고 확인해요"))))
                .isEqualTo("EMOTICON_SETTLEMENT_NOT_FOUND");
        long[] none = creatorWithPack(false);
        sales(none[0], none[1], 1, kst("2031-03-10T12:00"));
        assertThat(code(() -> kit.inTx(() -> kit.settlements.revealAccount(kit.operator, "127.0.0.1", id(none[0], "2031-03"), "이체하려고 확인해요"))))
                .isEqualTo("EMOTICON_PAYOUT_ACCOUNT_NOT_FOUND");
        EmoticonAdminView.PayoutAccountReveal revealed = kit.inTx(() -> kit.settlements.revealAccount(kit.operator, "127.0.0.1", id, "이체하려고 확인해요"));
        assertThat(revealed.type()).isEqualTo("INDIVIDUAL");
        assertThat(revealed.number()).isEqualTo("12345678901234");
        assertThat(revealed.holder()).isEqualTo("홍길동");
        assertThat(revealed.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        AuditEvent audit = kit.audits.stream().filter(e -> AuditActions.EMOTICON_PAYOUT_REVEAL.equals(e.action())).reduce((a, b) -> b).orElseThrow();
        assertThat(audit.targetId()).isEqualTo(Long.toString(creator));
        assertThat(audit.detail()).containsEntry("settlement", id).containsEntry("reason", "이체하려고 확인해요");
        EmoticonSettlementView.AdminDetail d = detail(creator, "2031-03");
        assertThat(d.status()).isEqualTo("ESTIMATED");
        assertThat(d.events().get(0).action()).isEqualTo("ACCOUNT_VIEWED");
        assertThat(d.events().get(0).note()).isEqualTo("이체하려고 확인해요");
    }

    // ── 목록 ────────────────────────────────────────────────────────────────

    @Test
    void 관리자_목록은_기본_지급을_앞둔_달이고_고를_수_있는_달_합계_정렬_규칙을_준다() {
        long[] a = creatorWithPack(true);
        long[] b = creatorWithPack(true);
        sales(a[0], a[1], 8, kst("2031-04-10T12:00"));
        sales(b[0], b[1], 12, kst("2031-04-10T12:00"));
        EmoticonSettlementView.AdminList list = kit.settlements.list(null);
        assertThat(list.month()).isEqualTo("2031-04");
        assertThat(list.months()).startsWith("2031-05", "2031-04");
        assertThat(kit.settlements.list("2099-01").month()).isEqualTo("2031-04"); // 고를 수 있는 달 밖
        assertThat(kit.settlements.list("엉터리").month()).isEqualTo("2031-04");
        assertThat(list.rules().feeRatePercent()).isEqualTo(30);
        assertThat(list.rules().payoutMinimum()).isEqualTo(10000);
        assertThat(list.rules().settlementDay()).isEqualTo(10);
        assertThat(list.rules().refundDays()).isEqualTo(7);
        List<String> mine = list.items().stream().map(r -> r.creator().memberNo()).filter(m -> m.equals(Long.toString(a[0])) || m.equals(Long.toString(b[0]))).toList();
        assertThat(mine).containsExactly(Long.toString(b[0]), Long.toString(a[0])); // 지급액 큰 것 먼저
        assertThat(list.totals().salesAmount()).isEqualTo(list.items().stream().mapToLong(EmoticonSettlementView.AdminRow::salesAmount).sum());
        assertThat(list.totals().payoutAmount()).isEqualTo(list.items().stream().mapToLong(EmoticonSettlementView.AdminRow::payoutAmount).sum());
        EmoticonSettlementView.AdminRow row = listed("2031-04", a[0]);
        assertThat(row.id()).isEqualTo(id(a[0], "2031-04"));
        assertThat(row.month()).isEqualTo("2031-04");
        assertThat(row.creator().nickname()).isNotNull();
        assertThat(row.payoutDate()).isEqualTo("2031-05-12"); // 5월 10일(토) → 다음 업무일
        assertThat(listed("2031-05", a[0])).isNull();
    }

    // ── DB 그물 ─────────────────────────────────────────────────────────────

    @Test
    void 확정한_줄의_금액과_구매의_정산_달은_DB가_고치지_못하게_막는다() {
        long[] cp = creatorWithPack(true);
        long creator = cp[0];
        List<Long> march = sales(creator, cp[1], 8, kst("2031-03-10T12:00"));
        long may = sale(creator, cp[1], PRICE, kst("2031-05-02T12:00"));
        confirm(id(creator, "2031-03"));
        String where = " WHERE creator_id = " + creator + " AND sales_month = '2031-03-01'";
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_settlements SET payout_amount = payout_amount + 1, carried_out = carried_out" + where))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_settlements SET fee_rate_percent = 20" + where)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_settlements SET status = 'ESTIMATED', confirmed_at = NULL" + where)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("DELETE FROM emoticon_settlements" + where)).isInstanceOf(DataAccessException.class);
        // 금액 칸 사이의 계산이 어긋난 굳힌 줄은 들어가지 않는다(정산 기준이 틀림)
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO emoticon_settlements (creator_id, sales_month, status, sales_count, sales_amount, refund_count, refund_amount, "
                + "vat_amount, base_amount, fee_rate_percent, fee_amount, share_amount, carried_in, withholding_amount, payout_amount, carried_out, payee_type, pay_date, closes_at, "
                + "confirmed_at, created_at, updated_at) VALUES (?, '2031-01-01', 'CONFIRMED', 1, 2500, 0, 0, 228, 2300, 30, 690, 1610, 0, 0, 0, 1610, 'INDIVIDUAL', "
                + "'2031-02-10', now(), now(), now(), now())", creator)).isInstanceOf(DataAccessException.class);
        // 지급하는 줄의 원천징수가 식과 다르면 안 된다
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO emoticon_settlements (creator_id, sales_month, status, sales_count, sales_amount, refund_count, refund_amount, "
                + "vat_amount, base_amount, fee_rate_percent, fee_amount, share_amount, carried_in, withholding_amount, payout_amount, carried_out, payee_type, pay_date, closes_at, "
                + "confirmed_at, created_at, updated_at) VALUES (?, '2031-01-01', 'CONFIRMED', 1, 2500, 0, 0, 228, 2272, 30, 682, 1590, 0, 0, 1590, 0, 'INDIVIDUAL', "
                + "'2031-02-10', now(), now(), now(), now())", creator)).isInstanceOf(DataAccessException.class);
        // 구매의 정산 달은 한 번만, 확정한 줄만 가리킨다
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_purchases SET settlement_month = NULL WHERE id = ?", march.get(0))).isInstanceOf(DataAccessException.class);
        kit.inTx(() -> kit.settlementStore.insertIfAbsent(creator, LocalDate.of(2031, 5, 1), NOW));
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_purchases SET settlement_month = '2031-05-01' WHERE id = ?", may)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_purchases SET settlement_month = '2031-03-01' WHERE id = ?", may))
                .isInstanceOf(DataAccessException.class); // 결제 달(5월)보다 앞
        // 예정 줄의 상태 길 — 굳히지 않고 지급으로 갈 수 없다
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_settlements SET status = 'PAID', paid_at = now(), transfer_date = '2031-05-20' WHERE creator_id = ? "
                + "AND sales_month = '2031-05-01'", creator)).isInstanceOf(DataAccessException.class);
        // 처리 기록은 추가만
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_settlement_events SET note = '고침' WHERE creator_id = ?", creator)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("DELETE FROM emoticon_settlement_events WHERE creator_id = ?", creator)).isInstanceOf(DataAccessException.class);
        // 지급 기록도 한 번
        paid(creator, "2031-03", "2031-05-20", "정기 이체");
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_settlements SET transfer_date = '2031-05-19'" + where)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_settlements SET status = 'HELD', hold_reason = '지급 뒤 보류', held_at = now()" + where))
                .isInstanceOf(DataAccessException.class);
    }
}
