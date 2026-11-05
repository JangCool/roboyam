package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.batch.core.BatchCatalog;
import com.community.emoticon.dto.request.EmoticonPayoutAccountRequest;
import com.community.emoticon.dto.request.EmoticonSettlementConfirmRequest;
import com.community.emoticon.dto.request.EmoticonSettlementPaidRequest;
import com.community.emoticon.dto.response.EmoticonPublicView;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.exception.EmoticonNotOwnedException;
import com.community.emoticon.handler.EmoticonPackPaymentHandler;
import com.community.emoticon.repository.master.EmoticonPurchaseStore;
import com.community.emoticon.service.EmoticonAcquireService;
import com.community.emoticon.service.EmoticonPurchaseNotices;
import com.community.emoticon.service.EmoticonSettlementCalculator.Rules;
import com.community.holiday.service.BusinessDayCalendar;
import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.exception.IdentityUnderageException;
import com.community.identity.service.IdentityVerificationService;
import com.community.payment.dto.CancelKind;
import com.community.payment.dto.CanceledPayment;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.ManualCancelResolution;
import com.community.payment.dto.PaidPayment;
import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentDecision;
import com.community.payment.dto.PaymentOrderType;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.time.ServiceTime;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * 로보티콘 후속 결정(2026-11-05 — emoticon-contract 「후속 결정 반영(2026-11-05)」) — 실제 PostgreSQL 위에서 규칙마다 막는다.
 *
 * <ol>
 *   <li>작가 자기 팩 = 파생 규칙(사지 않아도 가짐 · 받기 줄·받은 수·보상 없음 · 빼기 409 · 결제 확정 거절 · 판매 중단·주인 변경에 저절로 맞음 · 상점에 나오기 전엔 아님)</li>
 *   <li>구매 알림 {@code EMOTICON_PURCHASE}(구매 완료·자동 취소 — 처리기, 한 번만 · 되돌리면 없음 · 멈춘 취소는 수동 처리 때). 셀프·예외 환불 알림은 결제 모듈을 지나는 api·admin HTTP 시험</li>
 *   <li>만 19세 미만 작가 — 정산 정보 등록·유료 희망 {@code 403 IDENTITY_UNDERAGE}, 무료 팩은 된다</li>
 *   <li>업무일 — 공휴일 시드(2026·2027, 2028 없음) · 다음 업무일 · 지급 예정일</li>
 *   <li>탈퇴 작가 정산 정보 파기 DB 배치({@code R__batch_41})</li>
 * </ol>
 */
class EmoticonFollowupDecisionsTest extends EmoticonTestBase {

    private static final int PRICE = 2500;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

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

    private static CommonException error(Executable call) {
        try {
            call.execute();
        } catch (CommonException e) {
            return e;
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("오류가 나지 않았다");
    }

    private static Timestamp ts(Instant at) {
        return Timestamp.from(at);
    }

    private int acquisitionRows(long user, long pack) {
        return kit.count("SELECT count(*) FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ?", user, pack);
    }

    private EmoticonPublicView.MyPack myPack(long user, long pack) {
        return kit.acquire.mine(user).packs().stream().filter(p -> p.id().equals(Long.toString(pack))).findFirst().orElse(null);
    }

    // ── 1. 작가 자기 팩 ─────────────────────────────────────────────────────

    @Test
    void 작가는_상점에_나온_자기_팩을_사거나_받지_않아도_가지고_받기_줄_받은_수_보상은_없다() {
        long creator = kit.member();
        long pack = kit.approvedPack(creator);
        long other = kit.member();
        String id = Long.toString(pack);
        long token = kit.liveEmoticons(pack).get(0);

        // 토큰 검사 · 팩 상세 · 상점 카드 · 내 로보티콘이 같은 판정
        assertThat(kit.acquisitionStore.creatorOwns(creator, pack)).isTrue();
        assertThat(kit.acquisitionStore.ownedAmong(creator, List.of(pack))).containsExactly(pack);
        kit.guard.requireOwned(creator, "작가 글 :emo-" + token + ":");
        assertThatThrownBy(() -> kit.guard.requireOwned(other, ":emo-" + token + ":")).isInstanceOf(EmoticonNotOwnedException.class);
        assertThat(kit.shop.pack(creator, id).acquired()).isTrue();
        assertThat(kit.shop.pack(other, id).acquired()).isFalse();
        EmoticonPublicView.MyPack mine = myPack(creator, pack);
        assertThat(mine).isNotNull();
        assertThat(mine.mine()).isTrue();
        assertThat(mine.price()).isZero();
        assertThat(mine.purchaseId()).isNull();
        assertThat(mine.emoticons()).isNotEmpty();
        assertThat(myPack(other, pack)).isNull();

        // 받기 — 같은 결과지만 받기 줄·받은 수·보상이 없다
        int before = kit.acquireCount(pack);
        EmoticonAcquireService.Acquired got = kit.inTx(() -> kit.acquire.acquire(creator, id));
        assertThat(got.result().acquired()).isTrue();
        assertThat(got.rewarded()).isZero();
        assertThat(kit.acquireCount(pack)).isEqualTo(before);
        assertThat(acquisitionRows(creator, pack)).isZero();
        assertThat(kit.rewards(creator)).isZero();

        // 빼기 — 409 EMOTICON_PACK_OWNED(reason CREATOR), 그대로 가진다
        CommonException removed = error(() -> kit.inTx(() -> kit.acquire.remove(creator, id)));
        assertThat(removed.getErrorCode()).isEqualTo(EmoticonConflictException.PACK_OWNED);
        assertThat(removed.getExtras()).containsEntry("reason", "CREATOR");
        assertThat(myPack(creator, pack)).isNotNull();

        // 다른 회원이 받으면 받은 수가 오르고(작가 본인 몫은 세지 않았다) 내 로보티콘은 그 회원의 받기 줄
        kit.inTx(() -> kit.acquire.acquire(other, id));
        assertThat(kit.acquireCount(pack)).isEqualTo(before + 1);
        assertThat(myPack(other, pack).mine()).isFalse();
    }

    @Test
    void 상점에_나오기_전에는_가진_것이_아니고_판매_중단_뒤에도_가지며_팩_주인이_바뀌면_새_주인이_가진다() {
        long creator = kit.member();
        long pending = Long.parseLong(kit.submitNew(creator, "심사 중 팩").id());
        assertThat(kit.acquisitionStore.creatorOwns(creator, pending)).isFalse();
        assertThat(myPack(creator, pending)).isNull();

        long pack = kit.approvedPack(creator);
        kit.inTx(() -> kit.packs.suspend(kit.operator, "127.0.0.1", Long.toString(pack), "시험 판매 중단 사유"));
        assertThat(kit.status(pack)).isEqualTo("SUSPENDED");
        assertThat(kit.acquisitionStore.creatorOwns(creator, pack)).isTrue();
        assertThat(myPack(creator, pack).suspended()).isTrue();
        assertThat(kit.shop.pack(creator, Long.toString(pack)).acquired()).isTrue();

        long heir = kit.member();
        kit.jdbc.update("UPDATE emoticon_packs SET creator_id = ? WHERE id = ?", heir, pack);
        assertThat(kit.acquisitionStore.creatorOwns(heir, pack)).isTrue();
        assertThat(kit.acquisitionStore.creatorOwns(creator, pack)).isFalse();
        assertThat(myPack(creator, pack)).isNull();
        assertThat(myPack(heir, pack).mine()).isTrue();
    }

    // ── 결제 처리기(작가 자기 팩 · 구매 알림) ─────────────────────────────────

    private long paidPack(long creator) {
        long pack = kit.approvedPack(creator);
        kit.jdbc.update("UPDATE emoticon_packs SET price = ? WHERE id = ?", PRICE, pack);
        return pack;
    }

    private long pending(long user, long pack) {
        long id = kit.ids.nextId();
        Instant now = kit.clock.instant();
        Long creator = kit.jdbc.queryForObject("SELECT creator_id FROM emoticon_packs WHERE id = ?", Long.class, pack);
        kit.purchases.insert(new EmoticonPurchaseStore.NewPurchase(id, user, pack, creator, "산 때 이름", PRICE, now, now.plus(Duration.ofMinutes(30)), now));
        return id;
    }

    private long paymentOrder(long user, long purchase) {
        long id = kit.ids.nextId();
        String paymentId = "pay_" + String.format("%032d", id).substring(0, 32);
        Instant now = kit.clock.instant();
        kit.jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, status, expires_at, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ROBOTIKON_PACK', ?, '시험', ?, 'READY', ?, ?, ?)", id, paymentId, user, purchase, PRICE, ts(now.plus(Duration.ofMinutes(30))), ts(now),
                ts(now));
        return id;
    }

    private PaidPayment paid(long user, long purchase, long order, Instant paidAt) {
        return new PaidPayment(order, "pay_x", user, PaymentOrderType.ROBOTIKON_PACK, purchase, PRICE, paidAt, ConfirmedVia.WEBHOOK);
    }

    private CanceledPayment autoCancel(long user, long purchase, long order, PaymentAnomaly anomaly) {
        return new CanceledPayment(order, "pay_x", user, PaymentOrderType.ROBOTIKON_PACK, purchase, kit.ids.nextId(), CancelKind.AUTO_REJECT, PRICE, PRICE, true, anomaly,
                kit.clock.instant());
    }

    /** 그 회원의 구매 알림 — {kind, purchase_id, pack_id, pack_name, amount, path}(오래된 것 먼저) */
    private List<Map<String, Object>> notices(long user) {
        return kit.jdbc.queryForList("SELECT payload->>'kind' AS kind, payload->>'purchaseId' AS purchase_id, payload->>'packId' AS pack_id, payload->>'packName' AS pack_name, "
                + "(payload->>'amount')::bigint AS amount, payload->>'path' AS path FROM member_notifications WHERE user_id = ? AND type = 'EMOTICON_PURCHASE' ORDER BY id", user);
    }

    @Test
    void 작가_자기_유료_팩의_결제는_처리기가_이미_가짐으로_거절하고_자동_취소만_알린다() {
        kit.withPaid(() -> {
            long creator = kit.member();
            long pack = paidPack(creator);
            long purchase = pending(creator, pack);
            long order = paymentOrder(creator, purchase);
            PaymentDecision decision = kit.inTx(() -> kit.handler.onPaid(paid(creator, purchase, order, kit.clock.instant())));
            assertThat(decision).isEqualTo(PaymentDecision.reject(EmoticonPackPaymentHandler.REJECT_OWNED));
            assertThat(kit.jdbc.queryForObject("SELECT status FROM emoticon_purchases WHERE id = ?", String.class, purchase)).isEqualTo("CLOSED");
            assertThat(acquisitionRows(creator, pack)).isZero();
            assertThat(notices(creator)).isEmpty();
            kit.inTx(() -> kit.handler.onCanceled(autoCancel(creator, purchase, order, PaymentAnomaly.DOMAIN_REJECTED)));
            assertThat(notices(creator)).extracting(n -> n.get("kind")).containsExactly(EmoticonPurchaseNotices.AUTO_CANCELED);
        });
    }

    @Test
    void 구매_완료_알림은_받아들인_확정에서_한_번만_나가고_확정이_되돌려지면_알림도_없다() {
        kit.withPaid(() -> {
            long creator = kit.member();
            long pack = paidPack(creator);
            long buyer = kit.member();
            // 되돌려진 확정 — 알림 행도 없다(커밋돼야 보인다)
            long rolled = pending(buyer, pack);
            long rolledOrder = paymentOrder(buyer, rolled);
            kit.tx.executeWithoutResult(status -> {
                assertThat(kit.handler.onPaid(paid(buyer, rolled, rolledOrder, kit.clock.instant()))).isEqualTo(PaymentDecision.accept());
                status.setRollbackOnly();
            });
            assertThat(notices(buyer)).isEmpty();
            assertThat(kit.jdbc.queryForObject("SELECT status FROM emoticon_purchases WHERE id = ?", String.class, rolled)).isEqualTo("PENDING");
            kit.purchases.close(rolled, kit.clock.instant()); // 다시 준비하면 앞 결제 대기를 닫는 것과 같게

            long purchase = pending(buyer, pack);
            long order = paymentOrder(buyer, purchase);
            assertThat(kit.inTx(() -> kit.handler.onPaid(paid(buyer, purchase, order, kit.clock.instant())))).isEqualTo(PaymentDecision.accept());
            assertThat(kit.inTx(() -> kit.handler.onPaid(paid(buyer, purchase, order, kit.clock.instant()))))
                    .isEqualTo(PaymentDecision.reject(EmoticonPackPaymentHandler.REJECT_NOT_PENDING));
            List<Map<String, Object>> made = notices(buyer);
            assertThat(made).hasSize(1);
            assertThat(made.get(0)).containsEntry("kind", EmoticonPurchaseNotices.PAID).containsEntry("purchase_id", Long.toString(purchase))
                    .containsEntry("pack_id", Long.toString(pack)).containsEntry("pack_name", "산 때 이름").containsEntry("amount", (long) PRICE)
                    .containsEntry("path", "/me/robotikon");
            // 환불 PG 취소 완료(이상 아님)는 알리지 않는다 — 환불 알림은 환불을 넣을 때 한 번(api·admin 시험)
            kit.inTx(() -> kit.handler.onCanceled(new CanceledPayment(order, "pay_x", buyer, PaymentOrderType.ROBOTIKON_PACK, purchase, kit.ids.nextId(), CancelKind.REFUND,
                    PRICE, PRICE, true, null, kit.clock.instant())));
            assertThat(notices(buyer)).hasSize(1);
        });
    }

    @Test
    void 자동_취소_알림은_PG_취소가_끝날_때_한_번이고_멈춘_자동_취소는_운영자_수동_처리_때_한_번() {
        kit.withPaid(() -> {
            long creator = kit.member();
            long pack = paidPack(creator);
            long buyer = kit.member();
            // 늦은 결제(결제 모듈이 처리기를 부르지 않는다) — 자동 취소 PG 취소 완료 때 알림
            long late = pending(buyer, pack);
            long lateOrder = paymentOrder(buyer, late);
            kit.inTx(() -> kit.handler.onCanceled(autoCancel(buyer, late, lateOrder, PaymentAnomaly.LATE_PAYMENT)));
            List<Map<String, Object>> made = notices(buyer);
            assertThat(made).hasSize(1);
            assertThat(made.get(0)).containsEntry("kind", EmoticonPurchaseNotices.AUTO_CANCELED).containsEntry("path", "/robotikon/" + pack)
                    .containsEntry("amount", (long) PRICE).containsEntry("purchase_id", Long.toString(late));
            assertThat(kit.jdbc.queryForObject("SELECT status FROM emoticon_purchases WHERE id = ?", String.class, late)).isEqualTo("CLOSED");

            // 멈춘(GAVE_UP) 자동 취소 — 그때는 알리지 않고, 운영자가 수동 처리하면 한 번
            long stuck = pending(buyer, pack);
            long stuckOrder = paymentOrder(buyer, stuck);
            assertThat(kit.inTx(() -> kit.handler.onPaid(new PaidPayment(stuckOrder, "pay_x", buyer, PaymentOrderType.ROBOTIKON_PACK, stuck, PRICE - 100,
                    kit.clock.instant(), ConfirmedVia.WEBHOOK)))).isEqualTo(PaymentDecision.reject(EmoticonPackPaymentHandler.REJECT_AMOUNT));
            assertThat(notices(buyer)).hasSize(1);
            kit.inTx(() -> kit.handler.onCancelResolvedManually(new ManualCancelResolution(stuckOrder, PaymentOrderType.ROBOTIKON_PACK, stuck, buyer, kit.ids.nextId(),
                    CancelKind.AUTO_REJECT, PRICE, null, null, kit.operator, kit.clock.instant())));
            assertThat(notices(buyer)).extracting(n -> n.get("kind")).containsExactly(EmoticonPurchaseNotices.AUTO_CANCELED, EmoticonPurchaseNotices.AUTO_CANCELED);
            assertThat(notices(buyer).get(1)).containsEntry("purchase_id", Long.toString(stuck));
            // 멈춘 환불의 수동 처리는 알리지 않는다(환불 접수 알림이 이미 갔다)
            kit.inTx(() -> kit.handler.onCancelResolvedManually(new ManualCancelResolution(stuckOrder, PaymentOrderType.ROBOTIKON_PACK, stuck, buyer, kit.ids.nextId(),
                    CancelKind.REFUND, PRICE, null, null, kit.operator, kit.clock.instant())));
            assertThat(notices(buyer)).hasSize(2);
            // 다른 회원의 결제로 온 취소는 그 구매의 회원에게 알리지 않는다(어긋난 입력)
            long stranger = kit.member();
            kit.inTx(() -> kit.handler.onCanceled(autoCancel(stranger, stuck, stuckOrder, PaymentAnomaly.LATE_PAYMENT)));
            assertThat(notices(buyer)).hasSize(2);
            assertThat(notices(stranger)).isEmpty();
        });
    }

    @Test
    void 구매_알림_문구는_두_언어에_있고_종류마다_금액을_싣는다() throws Exception {
        for (String lang : new String[] {"ko", "en"}) {
            Properties bundle = new Properties();
            try (var in = new InputStreamReader(getClass().getResourceAsStream("/messages/messages_" + lang + ".properties"), StandardCharsets.UTF_8)) {
                bundle.load(in);
            }
            for (String kind : List.of(EmoticonPurchaseNotices.PAID, EmoticonPurchaseNotices.AUTO_CANCELED, EmoticonPurchaseNotices.REFUNDED_SELF,
                    EmoticonPurchaseNotices.REFUNDED_EXCEPTION)) {
                assertThat(bundle.getProperty("member.notification.EMOTICON_PURCHASE.kind." + kind)).as(lang + " " + kind).contains("{0}");
            }
            assertThat(bundle).containsKeys("member.notification.EMOTICON_PURCHASE.title", "member.notification.EMOTICON_PURCHASE.body",
                    "member.event.EMOTICON_PURCHASE.name", "member.event.EMOTICON_PURCHASE.description", "member.event.EMOTICON_PURCHASE.recipients",
                    "notificationTemplate.var.emoticonPurchasePackName.desc", "notificationTemplate.var.emoticonPurchaseDetail.desc", "error.EMOTICON_PACK_OWNED.creator",
                    "error.EMOTICON_PACK_OWNED.creatorRemove", "error.IDENTITY_UNDERAGE.emoticonCreator", "emoticon.settlement.transferDate.notBusinessDay");
        }
    }

    // ── 3. 미성년 작가 ──────────────────────────────────────────────────────

    /** 생년월일 {@code birth} 로 본인인증(이름 홍길동) */
    private void verifyBorn(long user, LocalDate birth) {
        kit.identities.insertVerification(user, String.format("%064x", user), null, birth, IdentityProviderKind.FAKE, "emo-fake:" + user, kit.clock.instant());
        kit.identities.saveNameCipher(user, kit.cipher.encrypt(IdentityVerificationService.NAME, "홍길동"));
    }

    private static EmoticonPayoutAccountRequest account() {
        return new EmoticonPayoutAccountRequest("INDIVIDUAL", null, "KB", "123-456-78901234", "홍길동");
    }

    @Test
    void 만_19세_미만_작가는_정산_정보_등록과_유료_희망_신청이_막히고_무료_팩은_된다() {
        LocalDate today = ServiceTime.today(kit.clock);
        long minor = kit.member();
        verifyBorn(minor, today.minusYears(19).plusDays(1)); // 내일 만 19세
        CommonException e = error(() -> kit.inTx(() -> kit.payouts.save(minor, account())));
        assertThat(e).isInstanceOf(IdentityUnderageException.class);
        assertThat(e.getErrorCode()).isEqualTo("IDENTITY_UNDERAGE");
        assertThat(e.getExtras()).containsEntry("adultFrom", today.plusDays(1).toString());
        assertThat(kit.count("SELECT count(*) FROM emoticon_payout_accounts WHERE user_id = ?", minor)).isZero();

        kit.withPaid(() -> {
            int version = kit.publishContract();
            assertThat(code(() -> kit.submitPaid(minor, "미성년 유료 팩", version))).isEqualTo("IDENTITY_UNDERAGE");
            // 본인인증 전 회원의 유료 희망은 그대로 정산 정보 409(나이를 모른다)
            long unverified = kit.member();
            assertThat(code(() -> kit.submitPaid(unverified, "미인증 유료 팩", version))).isEqualTo(EmoticonConflictException.PAYOUT_REQUIRED);
        });
        // 무료 팩은 신청·승인·판매된다
        long free = kit.approvedPack(minor);
        assertThat(kit.status(free)).isEqualTo("APPROVED");
        assertThat(kit.price(free)).isZero();

        // 오늘 만 19세가 된 작가는 등록된다
        long adult = kit.member();
        verifyBorn(adult, today.minusYears(19));
        assertThat(kit.inTx(() -> kit.payouts.save(adult, account())).registered()).isTrue();
    }

    // ── 4. 업무일 ───────────────────────────────────────────────────────────

    @Test
    void 공휴일_표는_2026_2027_만_시드하고_다음_업무일은_토_일_공휴일_대체공휴일을_건너뛴다() {
        assertThat(kit.count("SELECT count(*) FROM public_holidays WHERE holiday_date BETWEEN '2026-01-01' AND '2026-12-31'")).isEqualTo(22);
        assertThat(kit.count("SELECT count(*) FROM public_holidays WHERE holiday_date BETWEEN '2027-01-01' AND '2027-12-31'")).isEqualTo(24);
        assertThat(kit.count("SELECT count(*) FROM public_holidays WHERE holiday_date BETWEEN '2028-01-01' AND '2028-12-31'")).isZero();
        BusinessDayCalendar cal = kit.holidays.calendar();
        assertThat(cal.covers(2026)).isTrue();
        assertThat(cal.covers(2027)).isTrue();
        assertThat(cal.covers(2028)).isFalse();
        // 노동절·지방선거일·제헌절(2026 새 공휴일)·대체공휴일
        for (String day : List.of("2026-05-01", "2026-06-03", "2026-07-17", "2026-10-05", "2027-02-09", "2027-05-03", "2027-07-19", "2027-10-11", "2027-12-27")) {
            assertThat(cal.businessDay(LocalDate.parse(day))).as(day).isFalse();
        }
        assertThat(cal.businessDay(LocalDate.parse("2026-11-05"))).isTrue();
        assertThat(cal.onOrAfter(LocalDate.parse("2026-10-10"))).isEqualTo("2026-10-12"); // 토 → 월
        assertThat(cal.onOrAfter(LocalDate.parse("2027-10-10"))).isEqualTo("2027-10-12"); // 일 → 월(한글날 대체공휴일) → 화
        assertThat(cal.onOrAfter(LocalDate.parse("2027-02-06"))).isEqualTo("2027-02-10"); // 설 연휴 + 대체공휴일
        assertThat(cal.onOrAfter(LocalDate.parse("2026-12-10"))).isEqualTo("2026-12-10"); // 평일 그대로
        assertThat(cal.onOrAfter(LocalDate.parse("2028-06-10"))).isEqualTo("2028-06-12"); // 표에 없는 해 — 주말만
        // 지급 예정일 = 다음 달 정산일 → 다음 업무일
        Rules rules = new Rules(30, 10_000, 10, 7, cal);
        assertThat(rules.payDate(LocalDate.parse("2026-09-01"))).isEqualTo("2026-10-12");
        assertThat(rules.payDate(LocalDate.parse("2026-10-01"))).isEqualTo("2026-11-10");
        assertThat(rules.payDate(LocalDate.parse("2027-09-01"))).isEqualTo("2027-10-12");
        Rules day3 = new Rules(30, 10_000, 3, 7, cal);
        assertThat(day3.payDate(LocalDate.parse("2026-09-01"))).isEqualTo("2026-10-06"); // 개천절(토) 다음 월요일 대체공휴일 → 화
        assertThat(day3.payDate(LocalDate.parse("2026-09-01")).getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        // DB CHECK — 모르는 종류 · 빈 이름
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO public_holidays (holiday_date, name, kind) VALUES ('2099-01-01', '시험', 'OTHER')"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO public_holidays (holiday_date, name, kind) VALUES ('2099-01-01', ' ', 'HOLIDAY')"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    // ── 5. 탈퇴 작가 정산 정보 파기(R__batch_41) ─────────────────────────────

    private static final String JOB = "emoticon-payout-account-cleanup";

    /** 결제된 판매 {@code count} 개(구매 회원은 판매마다 새 번호, 셀프 환불 기한 = 결제 + 7일) */
    private void sales(long creator, long pack, int count, Instant paidAt) {
        for (int i = 0; i < count; i++) {
            long purchase = kit.ids.nextId();
            long order = kit.ids.nextId();
            long buyer = kit.ids.nextId();
            Instant at = paidAt.plus(Duration.ofMinutes(i));
            kit.jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, status, expires_at, created_at, updated_at) "
                    + "VALUES (?, ?, ?, 'ROBOTIKON_PACK', ?, '시험', ?, 'READY', ?, ?, ?)", order, "pay_" + String.format("%032d", order).substring(0, 32), buyer, purchase, PRICE,
                    ts(at.plus(Duration.ofMinutes(30))), ts(at), ts(at));
            kit.jdbc.update("INSERT INTO emoticon_purchases (id, user_id, pack_id, creator_id, pack_name, amount, status, refund_notice_agreed_at, expires_at, created_at, "
                    + "payment_order_id, paid_at, refund_until) VALUES (?, ?, ?, ?, '파기 시험 팩', ?, 'PAID', ?, ?, ?, ?, ?, ?)", purchase, buyer, pack, creator, PRICE, ts(at),
                    ts(at.plus(Duration.ofMinutes(30))), ts(at), order, ts(at), ts(at.plus(Duration.ofDays(7))));
        }
    }

    private void withdraw(long user, Instant at) {
        kit.jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", user);
        kit.jdbc.update("INSERT INTO member_withdrawals (id, user_id, status, requested_at, completed_at) VALUES (?, ?, 'COMPLETED', ?, ?)", kit.ids.nextId(), user, ts(at),
                ts(at));
    }

    private static String id(long creator, LocalDate month) {
        return String.format("%04d%02d-%d", month.getYear(), month.getMonthValue(), creator);
    }

    /**
     * 작가 하나 — {@code at} 시각에 정산 정보를 등록하고, 그 두 달 앞 달에 판매 {@code count} 개를 넣고 {@code at} 에 확정한다. {@code pay} 면 {@code at} 에 지급 기록까지.
     * 확정한 정산 줄 ID.
     */
    private String settledCreator(long creator, Instant at, int count, boolean pay) {
        kit.clock.set(at);
        kit.registerPayout(creator);
        long pack = kit.approvedPack(creator);
        LocalDate month = ServiceTime.dateOf(at).withDayOfMonth(1).minusMonths(2);
        sales(creator, pack, count, month.plusDays(2).atTime(12, 0).atZone(SEOUL).toInstant());
        String line = id(creator, month);
        assertThat(kit.inTx(() -> kit.settlements.confirm(kit.operator, "127.0.0.1", new EmoticonSettlementConfirmRequest(List.of(line)))).confirmed()).isEqualTo(1);
        if (pay) {
            LocalDate transfer = ServiceTime.dateOf(at);
            BusinessDayCalendar cal = kit.holidays.calendar();
            while (!cal.businessDay(transfer)) {
                transfer = transfer.minusDays(1);
            }
            String date = transfer.toString();
            kit.inTx(() -> kit.settlements.markPaid(kit.operator, "127.0.0.1", line, new EmoticonSettlementPaidRequest(date, null)));
        }
        return line;
    }

    private boolean hasPayout(long user) {
        return kit.count("SELECT count(*) FROM emoticon_payout_accounts WHERE user_id = ?", user) == 1;
    }

    @Test
    void 파기_배치는_정산이_모두_끝나고_30일_지난_탈퇴_작가의_정산_정보만_지우고_정산_기록은_남긴다() throws Exception {
        Map<String, Object> was = kit.settingsRow();
        try {
            kit.jdbc.update("UPDATE emoticon_settings SET fee_rate_percent = 30, payout_minimum = 10000, settlement_day = 10, refund_days = 7 WHERE id = 1");
            Instant now = Instant.now();
            Instant old = now.minus(Duration.ofDays(70));
            Instant recent = now.minus(Duration.ofDays(10));

            long paidOld = kit.member();          // 지급 완료 70일 전 · 탈퇴 → 지운다
            settledCreator(paidOld, old, 8, true);
            withdraw(paidOld, old);
            long noSales = kit.member();          // 판매 없이 정산 정보만 · 탈퇴 70일 전 → 지운다
            kit.clock.set(old);
            kit.registerPayout(noSales);
            withdraw(noSales, old);
            long paidRecent = kit.member();       // 지급 완료 10일 전 → 여유 기간 안이라 그대로
            settledCreator(paidRecent, recent, 8, true);
            withdraw(paidRecent, old);
            long confirmedUnpaid = kit.member();  // 확정했지만 지급 전 → 그대로
            settledCreator(confirmedUnpaid, old, 8, false);
            withdraw(confirmedUnpaid, old);
            long held = kit.member();             // 보류 → 그대로
            String heldLine = settledCreator(held, old, 8, false);
            kit.inTx(() -> kit.settlements.hold(kit.operator, "127.0.0.1", heldLine, "시험 보류 사유"));
            withdraw(held, old);
            long carried = kit.member();          // 최소 지급액 미만을 넘겨 둠 → 그대로
            settledCreator(carried, old, 1, false);
            withdraw(carried, old);
            long unsettled = kit.member();        // 아직 정산에 들어가지 않은 판매 → 그대로
            kit.clock.set(old);
            kit.registerPayout(unsettled);
            sales(unsettled, kit.approvedPack(unsettled), 2, old);
            withdraw(unsettled, old);
            long active = kit.member();           // 탈퇴하지 않음 → 그대로
            kit.clock.set(old);
            kit.registerPayout(active);

            kit.jdbc.execute("CALL batch_emoticon_payout_account_cleanup('MANUAL', NULL, NULL)");

            assertThat(hasPayout(paidOld)).isFalse();
            assertThat(hasPayout(noSales)).isFalse();
            for (long kept : new long[] {paidRecent, confirmedUnpaid, held, carried, unsettled, active}) {
                assertThat(hasPayout(kept)).as("그대로 " + kept).isTrue();
            }
            // 정산 기록(금액·상태·이체일)은 남는다
            assertThat(kit.jdbc.queryForObject("SELECT status FROM emoticon_settlements WHERE creator_id = ?", String.class, paidOld)).isEqualTo("PAID");
            assertThat(kit.count("SELECT count(*) FROM emoticon_purchases WHERE creator_id = ?", paidOld)).isEqualTo(8);
            Map<String, Object> run = kit.jdbc.queryForMap("SELECT status, message_key FROM batch_runs WHERE job_key = ? ORDER BY started_at DESC, id DESC LIMIT 1", JOB);
            assertThat(run.get("status")).isEqualTo("OK");
            assertThat(run.get("message_key")).isEqualTo("batch.result.emoticonPayoutAccountCleanup");
            // 다시 돌리면 남은 줄은 대상이 아니다
            kit.jdbc.execute("CALL batch_emoticon_payout_account_cleanup('MANUAL', NULL, NULL)");
            assertThat(hasPayout(paidRecent)).isTrue();
        } finally {
            kit.restoreSettings(was);
        }
    }

    @Test
    void 파기_배치는_DB_배치이고_매일_04시_35분_여유_30일_번들_두_언어() throws Exception {
        assertThat(BatchCatalog.ofKey(JOB)).isEqualTo(BatchCatalog.EMOTICON_PAYOUT_ACCOUNT_CLEANUP);
        assertThat(BatchCatalog.EMOTICON_PAYOUT_ACCOUNT_CLEANUP.isJava()).isFalse();
        assertThat(BatchCatalog.EMOTICON_PAYOUT_ACCOUNT_CLEANUP.procedureName()).isEqualTo("batch_emoticon_payout_account_cleanup");
        Map<String, Object> defined = kit.jdbc.queryForMap("SELECT kst_hour, kst_minute, cadence FROM batch_db_jobs WHERE job_key = ?", JOB);
        assertThat(((Number) defined.get("kst_hour")).intValue()).isEqualTo(4);
        assertThat(((Number) defined.get("kst_minute")).intValue()).isEqualTo(35);
        assertThat(defined.get("cadence")).isEqualTo("DAILY");
        assertThat(kit.jdbc.queryForObject("SELECT param_value FROM batch_params WHERE job_key = ? AND param_key = 'grace_days'", Long.class, JOB)).isEqualTo(30L);
        for (String lang : new String[] {"ko", "en"}) {
            Properties bundle = new Properties();
            try (var in = new InputStreamReader(getClass().getResourceAsStream("/messages/messages_" + lang + ".properties"), StandardCharsets.UTF_8)) {
                bundle.load(in);
            }
            assertThat(bundle).containsKeys("batch.job." + JOB + ".name", "batch.job." + JOB + ".desc", "batch.result.emoticonPayoutAccountCleanup",
                    "batch.result.emoticonPayoutAccountCleanup.none");
        }
    }
}
