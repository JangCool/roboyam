package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.emoticon.dto.response.EmoticonPublicView;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.handler.EmoticonPackPaymentHandler;
import com.community.emoticon.repository.master.EmoticonPurchaseStore;
import com.community.emoticon.service.EmoticonSettlementMath;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaidPayment;
import com.community.payment.dto.PaymentDecision;
import com.community.payment.dto.PaymentOrderType;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;

/**
 * 로보티콘 S3 의 공유 규칙(emoticon-contract S3 · commerce.md 7-3·8-6) — 실제 PostgreSQL 위에서 결제 처리기·보유·받기·사용함·집계·구매 기록 표의 지키기를 결제 모듈 없이 본다.
 * (결제 모듈을 지나는 흐름 — 결제 준비·완료 API·자동 취소·셀프 환불·동시 확정 — 은 api {@code EmoticonPurchaseHttpTest})
 */
class EmoticonPurchaseRulesTest extends EmoticonTestBase {

    private static final int PRICE = 2500;

    /** 작가의 유료 팩(가격 단계 2,500) — 끝나면 kit 가 무료로 돌린다 */
    private long paidPack(long creator) {
        long pack = kit.approvedPack(creator);
        kit.jdbc.update("UPDATE emoticon_packs SET price = ? WHERE id = ?", PRICE, pack);
        return pack;
    }

    /** 결제 대기 구매(결제 준비가 만드는 줄과 같다) — 구매 ID */
    private long pending(long user, long pack, int amount) {
        long id = kit.ids.nextId();
        Instant now = kit.clock.instant();
        kit.purchases.insert(new EmoticonPurchaseStore.NewPurchase(id, user, pack, creatorOf(pack), "산 때 이름", amount, now, now.plus(Duration.ofMinutes(30)), now));
        return id;
    }

    private Long creatorOf(long pack) {
        return kit.jdbc.queryForObject("SELECT creator_id FROM emoticon_packs WHERE id = ?", Long.class, pack);
    }

    /** 처리기가 가리킬 결제 주문 줄(결제 모듈 없이 — 상태는 READY 로 둔다, 처리기는 결제 주문 ID 만 남긴다) — 결제 주문 ID */
    private long paymentOrder(long user, long purchase, int amount) {
        long id = kit.ids.nextId();
        String paymentId = "pay_" + String.format("%032d", id).substring(0, 32);
        Instant now = kit.clock.instant();
        kit.jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, status, expires_at, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ROBOTIKON_PACK', ?, '시험', ?, 'READY', ?, ?, ?)", id, paymentId, user, purchase, amount, Timestamp.from(now.plus(Duration.ofMinutes(30))),
                Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private PaymentDecision onPaid(long user, long purchase, long amount, Instant paidAt) {
        long order = paymentOrder(user, purchase, (int) amount);
        return kit.inTx(() -> kit.handler.onPaid(new PaidPayment(order, "pay_x", user, PaymentOrderType.ROBOTIKON_PACK, purchase, amount, paidAt, ConfirmedVia.WEBHOOK)));
    }

    /** 결제 완료(받아들임)까지 — 구매 ID */
    private long bought(long user, long pack) {
        long purchase = pending(user, pack, PRICE);
        assertThat(onPaid(user, purchase, PRICE, kit.clock.instant())).isEqualTo(PaymentDecision.accept());
        return purchase;
    }

    private String status(long purchase) {
        return kit.jdbc.queryForObject("SELECT status FROM emoticon_purchases WHERE id = ?", String.class, purchase);
    }

    private int acquisitionRows(long user, long pack) {
        return kit.count("SELECT count(*) FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ?", user, pack);
    }

    // ── 결제 완료 판정 ──────────────────────────────────────────────────────

    @Test
    void 판정_차례는_금액_유효_시각_그사이_이미_가짐이고_거절한_구매는_닫힌다() {
        kit.withPaid(() -> {
            long creator = kit.member();
            long pack = paidPack(creator);
            long buyer = kit.member();
            // 금액이 다르고 늦기도 하면 금액이 먼저
            long a = pending(buyer, pack, PRICE);
            assertThat(onPaid(buyer, a, PRICE - 100, kit.clock.instant().plus(Duration.ofHours(1))))
                    .isEqualTo(PaymentDecision.reject(EmoticonPackPaymentHandler.REJECT_AMOUNT));
            assertThat(status(a)).isEqualTo("CLOSED");
            long b = pending(buyer, pack, PRICE);
            assertThat(onPaid(buyer, b, PRICE, kit.clock.instant().plus(Duration.ofMinutes(31)))).isEqualTo(PaymentDecision.reject(EmoticonPackPaymentHandler.REJECT_LATE));
            assertThat(status(b)).isEqualTo("CLOSED");
            int before = kit.acquireCount(pack);
            long c = bought(buyer, pack);
            assertThat(status(c)).isEqualTo("PAID");
            assertThat(kit.acquireCount(pack)).isEqualTo(before + 1);
            // 그사이 다른 결제로 이미 샀다 — 거절(결제 모듈이 전액 자동 취소), 보유·받은 수는 그대로
            long d = pending(buyer, pack, PRICE);
            assertThat(onPaid(buyer, d, PRICE, kit.clock.instant())).isEqualTo(PaymentDecision.reject(EmoticonPackPaymentHandler.REJECT_OWNED));
            assertThat(acquisitionRows(buyer, pack)).isEqualTo(1);
            assertThat(kit.acquireCount(pack)).isEqualTo(before + 1);
            // 같은 구매를 다시 확정하려 하면(결제 대기 아님) 거절
            assertThat(onPaid(buyer, c, PRICE, kit.clock.instant())).isEqualTo(PaymentDecision.reject(EmoticonPackPaymentHandler.REJECT_NOT_PENDING));
            long other = kit.member();
            assertThat(onPaid(other, pending(other, pack, PRICE), PRICE, kit.clock.instant())).isEqualTo(PaymentDecision.accept()); // 다른 회원은 산다
            // 처리기에 다른 회원의 결제가 오면 거절
            long third = kit.member();
            assertThat(onPaid(other, pending(third, pack, PRICE), PRICE, kit.clock.instant())).isEqualTo(PaymentDecision.reject(EmoticonPackPaymentHandler.REJECT_OTHER_MEMBER));
        });
    }

    @Test
    void 구매_기록에는_산_때의_이름_금액_작가_결제_시각_환불_기한이_남는다() {
        kit.withPaid(() -> {
            long creator = kit.member();
            long pack = paidPack(creator);
            long buyer = kit.member();
            Instant paidAt = kit.clock.instant();
            long purchase = bought(buyer, pack);
            Map<String, Object> row = kit.jdbc.queryForMap("SELECT pack_name, amount, creator_id, paid_at, refund_until, used_at, payment_order_id FROM emoticon_purchases WHERE id = ?",
                    purchase);
            assertThat(row.get("pack_name")).isEqualTo("산 때 이름");
            assertThat(row.get("amount")).isEqualTo(PRICE);
            assertThat(row.get("creator_id")).isEqualTo(creator);
            assertThat(((Timestamp) row.get("paid_at")).toInstant()).isEqualTo(paidAt);
            assertThat(((Timestamp) row.get("refund_until")).toInstant()).isEqualTo(paidAt.plus(Duration.ofDays(7)));
            assertThat(row.get("used_at")).isNull();
            assertThat(row.get("payment_order_id")).isNotNull();
            // 가격을 바꿔도 산 구매는 그대로
            kit.jdbc.update("UPDATE emoticon_packs SET price = 3500 WHERE id = ?", pack);
            assertThat(kit.jdbc.queryForObject("SELECT amount FROM emoticon_purchases WHERE id = ?", Integer.class, purchase)).isEqualTo(PRICE);
        });
    }

    @Test
    void 판매_중이_아니거나_그새_무료가_된_팩의_결제는_거절한다() {
        kit.withPaid(() -> {
            long pack = paidPack(kit.member());
            long buyer = kit.member();
            long a = pending(buyer, pack, PRICE);
            kit.inTx(() -> kit.packs.suspend(kit.operator, "127.0.0.1", Long.toString(pack), "판매를 멈춰 봅니다"));
            assertThat(onPaid(buyer, a, PRICE, kit.clock.instant())).isEqualTo(PaymentDecision.reject(EmoticonPackPaymentHandler.REJECT_NOT_ON_SALE));
            kit.inTx(() -> kit.packs.resume(kit.operator, "127.0.0.1", Long.toString(pack)));
            long b = pending(buyer, pack, PRICE);
            kit.jdbc.update("UPDATE emoticon_packs SET price = 0 WHERE id = ?", pack);
            assertThat(onPaid(buyer, b, PRICE, kit.clock.instant())).isEqualTo(PaymentDecision.reject(EmoticonPackPaymentHandler.REJECT_NOW_FREE));
            assertThat(acquisitionRows(buyer, pack)).isZero();
        });
    }

    // ── 보유 모델 ───────────────────────────────────────────────────────────

    @Test
    void 무료일_때_받은_회원은_뺐어도_이미_가진_팩이라_결제를_받지_않는다() {
        kit.withPaid(() -> {
            long pack = kit.approvedPack(kit.member());
            long early = kit.member();
            kit.inTx(() -> kit.acquire.acquire(early, Long.toString(pack)));
            kit.inTx(() -> kit.acquire.remove(early, Long.toString(pack)));
            kit.jdbc.update("UPDATE emoticon_packs SET price = ? WHERE id = ?", PRICE, pack);
            assertThat(onPaid(early, pending(early, pack, PRICE), PRICE, kit.clock.instant())).isEqualTo(PaymentDecision.reject(EmoticonPackPaymentHandler.REJECT_OWNED));
            // 받기로 다시 넣는다(S2) — 줄은 하나
            kit.inTx(() -> kit.acquire.acquire(early, Long.toString(pack)));
            assertThat(acquisitionRows(early, pack)).isEqualTo(1);
        });
    }

    @Test
    void 산_팩이_무료가_된_뒤_받기를_눌러도_받기_줄_받은_수_작가_보상이_새로_생기지_않는다() {
        kit.withPaid(() -> {
            long creator = kit.member();
            long pack = paidPack(creator);
            long buyer = kit.member();
            bought(buyer, pack);
            int count = kit.acquireCount(pack);
            long rewards = kit.rewards(creator);
            kit.jdbc.update("UPDATE emoticon_packs SET price = 0 WHERE id = ?", pack);
            var acquired = kit.inTx(() -> kit.acquire.acquire(buyer, Long.toString(pack)));
            assertThat(acquired.rewarded()).isZero();
            assertThat(acquired.result().acquired()).isTrue();
            assertThat(kit.acquireCount(pack)).isEqualTo(count);
            assertThat(kit.rewards(creator)).isEqualTo(rewards);
            assertThat(kit.jdbc.queryForObject("SELECT kind FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ?", String.class, buyer, pack)).isEqualTo("PURCHASE");
            // 다른 회원의 무료 받기에는 보상이 생긴다(대조)
            long other = kit.member();
            assertThat(kit.inTx(() -> kit.acquire.acquire(other, Long.toString(pack))).rewarded()).isPositive();
        });
    }

    @Test
    void 환불한_산_팩은_유료면_다시_받을_수_없고_무료가_되면_받기로_돌아가되_받은_수_보상은_없다() {
        kit.withPaid(() -> {
            long creator = kit.member();
            long pack = paidPack(creator);
            long buyer = kit.member();
            long purchase = bought(buyer, pack);
            kit.acquisitionStore.revokePurchase(purchase, kit.clock.instant()); // 환불이 보유를 뺀 상태
            assertThatThrownBy(() -> kit.inTx(() -> kit.acquire.acquire(buyer, Long.toString(pack)))).isInstanceOfSatisfying(EmoticonConflictException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo("EMOTICON_PACK_PAID"));
            int count = kit.acquireCount(pack);
            long rewards = kit.rewards(creator);
            kit.jdbc.update("UPDATE emoticon_packs SET price = 0 WHERE id = ?", pack);
            kit.inTx(() -> kit.acquire.acquire(buyer, Long.toString(pack)));
            assertThat(kit.jdbc.queryForMap("SELECT kind, purchase_id, removed_at FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ?", buyer, pack))
                    .containsEntry("kind", "FREE").containsEntry("purchase_id", null).containsEntry("removed_at", null);
            assertThat(kit.acquireCount(pack)).isEqualTo(count);
            assertThat(kit.rewards(creator)).isEqualTo(rewards);
        });
    }

    @Test
    void 환불한_뒤_다시_사면_같은_보유_줄을_다시_쓰고_받은_수는_사람마다_한_번() {
        kit.withPaid(() -> {
            long pack = paidPack(kit.member());
            long buyer = kit.member();
            long first = bought(buyer, pack);
            kit.acquisitionStore.revokePurchase(first, kit.clock.instant());
            kit.jdbc.update("UPDATE emoticon_purchases SET status = 'REFUNDED', refunded_at = ?, refund_kind = 'EXCEPTION', refund_reason = '시험 예외 환불' WHERE id = ?",
                    Timestamp.from(kit.clock.instant()), first);
            int count = kit.acquireCount(pack);
            long second = bought(buyer, pack);
            assertThat(acquisitionRows(buyer, pack)).isEqualTo(1);
            assertThat(kit.jdbc.queryForObject("SELECT purchase_id FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ?", Long.class, buyer, pack)).isEqualTo(second);
            assertThat(kit.acquireCount(pack)).isEqualTo(count);
        });
    }

    @Test
    void 산_팩은_목록에서_빼지_않고_내_로보티콘에_결제_금액_구매_번호_토큰_검사도_통과() {
        kit.withPaid(() -> {
            long pack = paidPack(kit.member());
            long buyer = kit.member();
            long purchase = bought(buyer, pack);
            assertThatThrownBy(() -> kit.inTx(() -> kit.acquire.remove(buyer, Long.toString(pack)))).isInstanceOfSatisfying(EmoticonConflictException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo("EMOTICON_PACK_PAID"));
            EmoticonPublicView.MyPack mine = kit.acquire.mine(buyer).packs().stream().filter(p -> p.id().equals(Long.toString(pack))).findFirst().orElseThrow();
            assertThat(mine.price()).isEqualTo(PRICE);
            assertThat(mine.purchaseId()).isEqualTo(Long.toString(purchase));
            kit.guard.requireOwned(buyer, ":emo-" + kit.liveEmoticons(pack).get(0) + ":"); // 산 보유도 토큰 검사가 본다
            assertThat(kit.shop.pack(buyer, Long.toString(pack)).acquired()).isTrue();
        });
    }

    // ── 사용함 ──────────────────────────────────────────────────────────────

    @Test
    void 사용함은_본문_토큰의_산_팩에_처음_한_번만_찍는다() {
        kit.withPaid(() -> {
            long pack = paidPack(kit.member());
            long freePack = kit.approvedPack(kit.member());
            long buyer = kit.member();
            long purchase = bought(buyer, pack);
            kit.inTx(() -> kit.acquire.acquire(buyer, Long.toString(freePack)));
            assertThat(kit.inTx(() -> kit.guard.recordUse(buyer, "토큰 없는 글"))).isZero();
            assertThat(kit.inTx(() -> kit.guard.recordUse(buyer, "무료 팩 :emo-" + kit.liveEmoticons(freePack).get(0) + ":"))).isZero();
            assertThat(kit.inTx(() -> kit.guard.recordUse(null, ":emo-" + kit.liveEmoticons(pack).get(0) + ":"))).isZero();
            Instant first = kit.clock.instant();
            assertThat(kit.inTx(() -> kit.guard.recordUse(buyer, "산 팩 :emo-" + kit.liveEmoticons(pack).get(1) + ":"))).isEqualTo(1);
            kit.clock.set(first.plus(Duration.ofHours(1)));
            assertThat(kit.inTx(() -> kit.guard.recordUse(buyer, "또 :emo-" + kit.liveEmoticons(pack).get(2) + ":"))).isZero();
            assertThat(kit.jdbc.queryForObject("SELECT used_at FROM emoticon_purchases WHERE id = ?", Timestamp.class, purchase).toInstant()).isEqualTo(first);
        });
    }

    @Test
    void 저장_트랜잭션이_되돌아가면_사용함도_되돌아가고_그사이_보유가_빠졌으면_409() {
        kit.withPaid(() -> {
            long pack = paidPack(kit.member());
            long buyer = kit.member();
            long purchase = bought(buyer, pack);
            String body = ":emo-" + kit.liveEmoticons(pack).get(0) + ":";
            kit.tx.executeWithoutResult(status -> {
                kit.guard.recordUse(buyer, body);
                status.setRollbackOnly(); // 저장이 실패했다
            });
            assertThat(kit.jdbc.queryForObject("SELECT used_at FROM emoticon_purchases WHERE id = ?", Timestamp.class, purchase)).isNull();
            kit.acquisitionStore.revokePurchase(purchase, kit.clock.instant()); // 검사 뒤 저장 전에 보유가 빠졌다
            assertThatThrownBy(() -> kit.inTx(() -> kit.guard.recordUse(buyer, body))).isInstanceOf(com.community.emoticon.exception.EmoticonNotOwnedException.class);
        });
    }

    // ── 집계 ────────────────────────────────────────────────────────────────

    @Test
    void 작가_몫_계산식은_화면과_같다() {
        EmoticonSettlementMath.Breakdown b = EmoticonSettlementMath.of(2500, 0, 30);
        assertThat(b.base()).isEqualTo(2272);
        assertThat(b.vat()).isEqualTo(228);
        assertThat(b.share()).isEqualTo(1590);
        assertThat(b.fee()).isEqualTo(682);
        assertThat(EmoticonSettlementMath.of(2200, 0, 30).base()).isEqualTo(2000); // 2,200 ÷ 1.1 의 부동소수 오차 없이
        assertThat(EmoticonSettlementMath.of(1000, 3000, 30).net()).isZero();
    }

    @Test
    void 스튜디오_판매_요약과_팩_판매액_관리자_팩_줄_작가_요약이_실제_값() {
        kit.withPaid(() -> {
            long creator = kit.member();
            long pack = paidPack(creator);
            long a = kit.member();
            long b = kit.member();
            bought(a, pack);
            long refunded = bought(b, pack);
            kit.acquisitionStore.revokePurchase(refunded, kit.clock.instant());
            kit.jdbc.update("UPDATE emoticon_purchases SET status = 'REFUNDED', refunded_at = ?, refund_kind = 'EXCEPTION', refund_reason = '시험 예외 환불' WHERE id = ?",
                    Timestamp.from(kit.clock.instant()), refunded);
            var overview = kit.studio.overview(creator);
            assertThat(overview.sales().salesCount()).isEqualTo(2);
            assertThat(overview.sales().salesAmount()).isEqualTo(2L * PRICE);
            assertThat(overview.sales().refundAmount()).isEqualTo(PRICE);
            assertThat(overview.sales().baseAmount()).isEqualTo(2272);
            assertThat(overview.sales().expectedPayout()).isEqualTo(1590);
            assertThat(overview.packs().get(0).salesTotal()).isEqualTo(PRICE);
            var row = kit.packs.list(null, null, null, Long.toString(creator)).items().get(0);
            assertThat(row.salesCount()).isEqualTo(1);
            assertThat(row.salesAmount()).isEqualTo(PRICE);
            assertThat(kit.packs.creator(Long.toString(creator)).salesAmount()).isEqualTo(PRICE);
            // 무료로만 나온 작가 팩은 판매 칸이 없다
            long freeCreator = kit.member();
            kit.approvedPack(freeCreator);
            assertThat(kit.packs.list(null, null, null, Long.toString(freeCreator)).items().get(0).salesCount()).isNull();
        });
    }

    // ── 표 지키기 ───────────────────────────────────────────────────────────

    @Test
    void 구매_기록은_지우거나_사본_금액을_고치거나_되돌릴_수_없고_보유_중_구매는_회원_팩마다_하나() {
        kit.withPaid(() -> {
            long pack = paidPack(kit.member());
            long buyer = kit.member();
            long purchase = bought(buyer, pack);
            assertThatThrownBy(() -> kit.jdbc.update("DELETE FROM emoticon_purchases WHERE id = ?", purchase)).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_purchases SET amount = 1500 WHERE id = ?", purchase)).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_purchases SET pack_name = '다른 이름' WHERE id = ?", purchase)).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_purchases SET status = 'PENDING', paid_at = NULL, payment_order_id = NULL, refund_until = NULL WHERE id = ?",
                    purchase)).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_purchases SET paid_at = paid_at + interval '1 day' WHERE id = ?", purchase))
                    .isInstanceOf(DataAccessException.class);
            kit.jdbc.update("UPDATE emoticon_purchases SET used_at = now() WHERE id = ?", purchase);
            assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_purchases SET used_at = NULL WHERE id = ?", purchase)).isInstanceOf(DataAccessException.class);
            // 쓴 구매의 셀프 환불은 표가 받지 않는다(예외 환불은 사유와 함께 된다)
            assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_purchases SET status = 'REFUNDED', refunded_at = now(), refund_kind = 'SELF' WHERE id = ?", purchase))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_purchases SET status = 'REFUNDED', refunded_at = now(), refund_kind = 'EXCEPTION', refund_reason = '짧음' WHERE id = ?",
                    purchase)).isInstanceOf(DataIntegrityViolationException.class);
            kit.jdbc.update("UPDATE emoticon_purchases SET status = 'REFUNDED', refunded_at = now(), refund_kind = 'EXCEPTION', refund_reason = '운영자 예외 환불' WHERE id = ?",
                    purchase);
            assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_purchases SET status = 'PAID', refunded_at = NULL, refund_kind = NULL, refund_reason = NULL WHERE id = ?",
                    purchase)).isInstanceOf(DataAccessException.class);
            // 닫힌 결제 대기는 다시 결제되지 않는다
            long closed = pending(buyer, pack, PRICE);
            kit.purchases.close(closed, kit.clock.instant());
            assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_purchases SET status = 'PENDING', closed_at = NULL WHERE id = ?", closed))
                    .isInstanceOf(DataAccessException.class);
            // 결제 대기는 회원·팩마다 하나 · 보유 중 구매도 하나
            pending(buyer, pack, PRICE);
            assertThatThrownBy(() -> pending(buyer, pack, PRICE)).isInstanceOf(DataIntegrityViolationException.class);
        });
    }

    @Test
    void 보유_줄의_출처는_FREE_PURCHASE_이고_산_줄만_구매를_가리킨다_결제_주문_종류에_ROBOTIKON_PACK() {
        long pack = kit.approvedPack(kit.member());
        long user = kit.member();
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO emoticon_acquisitions (id, user_id, pack_id, kind, first_acquired_at, acquired_at) VALUES (?, ?, ?, 'PURCHASE', now(), now())",
                kit.ids.nextId(), user, pack)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO emoticon_acquisitions (id, user_id, pack_id, kind, first_acquired_at, acquired_at) VALUES (?, ?, ?, 'GIFT', now(), now())",
                kit.ids.nextId(), user, pack)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(kit.jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_payment_orders_type'", String.class))
                .contains("ROBOTIKON_PACK").contains("BIZ_APPLICATION").contains("GROUP_BUY_RETURN_FEE");
        assertThat(List.of(PaymentOrderType.values())).contains(PaymentOrderType.ROBOTIKON_PACK);
    }
}
