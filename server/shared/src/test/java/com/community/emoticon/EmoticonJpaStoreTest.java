package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.commerce.sales.CommerceSalesStore;
import com.community.emoticon.repository.master.EmoticonContractStore;
import com.community.emoticon.repository.master.EmoticonPriceEventStore;
import com.community.emoticon.repository.master.EmoticonPurchaseStore;
import com.community.emoticon.repository.master.EmoticonReviewStore;
import com.community.emoticon.repository.master.EmoticonSettingsStore;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaidPayment;
import com.community.payment.dto.PaymentDecision;
import com.community.payment.dto.PaymentOrderType;
import com.community.testsupport.JpaTestContext;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/**
 * 로보티콘 저장소를 JDBC 에서 JPA·QueryDSL 로 옮기며 바뀐 문장의 동작 — 실제 PostgreSQL 위에서 본다(commerce.md 18절).
 *
 * <ul>
 *   <li>행 잠금의 세기: 쓰기 잠금({@code RowLocks}) = 전환 전과 같은 {@code FOR UPDATE}(나눔 잠금도, 외래 키 확인의 {@code FOR KEY SHARE} 도 막는다 —
 *       JPA {@code PESSIMISTIC_WRITE} 의 {@code FOR NO KEY UPDATE} 가 아니다), 나눔 잠금({@code PESSIMISTIC_READ}) = {@code FOR SHARE}(쓰기 잠금을 막는다)
 *       — 다른 연결에서 {@code NOWAIT} 로 확인한다.</li>
 *   <li>{@code DISTINCT ON} 을 바꾼 「마지막 한 줄」 — 정렬 값이 같은 줄에서는 다음 정렬 값(판 · ID)이 큰 줄.</li>
 *   <li>{@code UNION ALL} 을 두 질의로 나눈 「가진 팩」의 차례, 다음 쪽 기준 값, 탭 숫자, 받은 수 올리기.</li>
 * </ul>
 */
class EmoticonJpaStoreTest extends EmoticonTestBase {

    private static final int PRICE = 2500;

    // ── 도우미 ──────────────────────────────────────────────────────────────

    /** 한 트랜잭션이 잠근 채로 있는 동안 다른 연결로 {@code probe} 를 해 본다 — 끝나면 잠근 트랜잭션을 놓는다 */
    private static void whileHeld(Runnable lock, Runnable probe) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = pool.submit(() -> kit.inTx(() -> {
                lock.run();
                locked.countDown();
                try {
                    release.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(60, TimeUnit.SECONDS)).as("잠금을 잡았다").isTrue();
            try {
                probe.run();
            } finally {
                release.countDown();
                holder.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** 다른 연결에서 그 행을 {@code mode NOWAIT} 로 잠가 본다(자동 커밋 — 잡으면 바로 놓는다) */
    private static void tryLock(String table, String key, long id, String mode) {
        kit.jdbc.queryForList("SELECT 1 FROM " + table + " WHERE " + key + " = ? FOR " + mode + " NOWAIT", Integer.class, id);
    }

    /** 쓰기 잠금({@code FOR UPDATE} — 전환 전 세기) — 나눔 잠금도, 외래 키 확인의 키 나눔 잠금도 막힌다({@code FOR NO KEY UPDATE} 면 키 나눔 잠금은 된다) */
    private static void assertWriteLocked(String table, String key, long id) {
        assertThatThrownBy(() -> tryLock(table, key, id, "SHARE")).as(table + " 나눔 잠금은 막힌다").isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> tryLock(table, key, id, "KEY SHARE")).as(table + " 외래 키 확인(키 나눔 잠금)도 막힌다 — FOR UPDATE").isInstanceOf(DataAccessException.class);
    }

    /** 나눔 잠금({@code FOR SHARE}) — 나눔 잠금은 되고 쓰기 잠금은 막힌다 */
    private static void assertShareLocked(String table, String key, long id) {
        assertThatCode(() -> tryLock(table, key, id, "SHARE")).as(table + " 나눔 잠금끼리는 된다").doesNotThrowAnyException();
        assertThatThrownBy(() -> tryLock(table, key, id, "NO KEY UPDATE")).as(table + " 쓰기 잠금은 막힌다").isInstanceOf(DataAccessException.class);
    }

    private static long paidPack(long creator) {
        long pack = kit.approvedPack(creator);
        kit.jdbc.update("UPDATE emoticon_packs SET price = ? WHERE id = ?", PRICE, pack);
        return pack;
    }

    private static long pending(long user, long pack) {
        long id = kit.ids.nextId();
        Instant now = kit.clock.instant();
        Long creator = kit.jdbc.queryForObject("SELECT creator_id FROM emoticon_packs WHERE id = ?", Long.class, pack);
        kit.purchases.insert(new EmoticonPurchaseStore.NewPurchase(id, user, pack, creator, "산 때 이름", PRICE, now, now.plus(Duration.ofMinutes(30)), now));
        return id;
    }

    private static long bought(long user, long pack) {
        long purchase = pending(user, pack);
        long order = kit.ids.nextId();
        Instant now = kit.clock.instant();
        kit.jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, status, expires_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ROBOTIKON_PACK', ?, '시험', ?, 'READY', ?, ?, ?)", order, "pay_" + String.format("%032d", order).substring(0, 32), user, purchase,
                PRICE, Timestamp.from(now.plus(Duration.ofMinutes(30))), Timestamp.from(now), Timestamp.from(now));
        PaymentDecision decision = kit.inTx(() -> kit.handler.onPaid(new PaidPayment(order, "pay_x", user, PaymentOrderType.ROBOTIKON_PACK, purchase, PRICE, now,
                ConfirmedVia.WEBHOOK)));
        assertThat(decision).isEqualTo(PaymentDecision.accept());
        return purchase;
    }

    // ── 잠금 세기 ───────────────────────────────────────────────────────────

    @Test
    void 팩_행_쓰기_잠금은_FOR_UPDATE_결제_준비의_공유_잠금은_FOR_SHARE() throws Exception {
        long pack = kit.approvedPack(kit.member());
        whileHeld(() -> assertThat(kit.packStore.lock(pack)).isPresent(), () -> assertWriteLocked("emoticon_packs", "id", pack));
        whileHeld(() -> assertThat(kit.packStore.lockShare(pack)).isPresent(), () -> assertShareLocked("emoticon_packs", "id", pack));
        // 작가 탈퇴의 팩 멈추기도 팩 행을 쓰기 잠금한 채 바꾼다
        long creator = kit.member();
        long own = kit.approvedPack(creator);
        whileHeld(() -> assertThat(kit.packStore.stopForCreatorWithdrawal(creator, "작가 탈퇴 시험", kit.clock.instant())).extracting(r -> r.id()).containsExactly(own),
                () -> assertWriteLocked("emoticon_packs", "id", own));
        assertThat(kit.status(own)).isEqualTo("SUSPENDED");
    }

    @Test
    void 심사_건_잠금은_쓰기_잠금이고_없는_건은_비어_있다() throws Exception {
        long pack = Long.parseLong(kit.submitNew(kit.member(), "잠금 시험").id());
        long review = kit.pendingReview(pack);
        EmoticonReviewStore reviews = JpaTestContext.create(EmoticonReviewStore.class);
        whileHeld(() -> assertThat(reviews.lock(review)).hasValueSatisfying(r -> assertThat(r.packId()).isEqualTo(pack)),
                () -> assertWriteLocked("emoticon_reviews", "id", review));
        assertThat(kit.inTx(() -> reviews.lock(kit.ids.nextId()))).isEmpty();
    }

    @Test
    void 설정_저장_전_잠금은_쓰기_가격을_정하기_전과_운영_주체_바꾸기_전은_나눔() throws Exception {
        EmoticonSettingsStore settings = JpaTestContext.create(EmoticonSettingsStore.class);
        CommerceSalesStore sales = new CommerceSalesStore(JpaTestContext.query(), JpaTestContext.entityManager());
        whileHeld(settings::readForUpdate, () -> assertWriteLocked("emoticon_settings", "id", 1));
        whileHeld(settings::readShared, () -> assertShareLocked("emoticon_settings", "id", 1));
        whileHeld(sales::lockEmoticonPaidSales, () -> assertShareLocked("emoticon_settings", "id", 1));
        boolean enabled = kit.jdbc.queryForObject("SELECT paid_sales_enabled FROM emoticon_settings WHERE id = 1", Boolean.class);
        assertThat(kit.inTx(sales::lockEmoticonPaidSales)).isEqualTo(enabled);
        assertThat(kit.inTx(settings::read).priceTiers()).isEqualTo(kit.jdbc.queryForList("SELECT unnest(price_tiers) FROM emoticon_settings WHERE id = 1", Integer.class));
    }

    @Test
    void 구매_행_잠금과_정산_확정의_미정산_구매_잠금은_쓰기_잠금이고_ID_차례() throws Exception {
        kit.withPaid(() -> {
            long creator = kit.member();
            long pack = paidPack(creator);
            long first = bought(kit.member(), pack);
            long second = bought(kit.member(), pack);
            try {
                whileHeld(() -> assertThat(kit.purchases.lock(first)).hasValueSatisfying(p -> assertThat(p.status().name()).isEqualTo("PAID")),
                        () -> assertWriteLocked("emoticon_purchases", "id", first));
                whileHeld(() -> assertThat(kit.settlementStore.unsettled(creator, true)).extracting(r -> r.purchaseId()).containsExactly(first, second), () -> {
                    assertWriteLocked("emoticon_purchases", "id", first);
                    assertWriteLocked("emoticon_purchases", "id", second);
                });
                assertThat(kit.settlementStore.unsettled(creator, false)).extracting(r -> r.purchaseId()).containsExactlyInAnyOrder(first, second);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    // ── 마지막 한 줄(DISTINCT ON 대신) ──────────────────────────────────────

    @Test
    void 작가마다_가장_최근_동의는_같은_시각이면_큰_판() {
        EmoticonContractStore store = JpaTestContext.create(EmoticonContractStore.class);
        long a = kit.member();
        long b = kit.member();
        Instant at = kit.clock.instant();
        kit.inTx(() -> {
            assertThat(store.insert(kit.ids.nextId(), a, 901, at, null)).isTrue();
            assertThat(store.insert(kit.ids.nextId(), a, 902, at, null)).isTrue();
            assertThat(store.insert(kit.ids.nextId(), a, 903, at.minusSeconds(60), null)).isTrue();
            assertThat(store.insert(kit.ids.nextId(), b, 901, at.minusSeconds(5), null)).isTrue();
            assertThat(store.insert(kit.ids.nextId(), b, 902, at.minusSeconds(10), null)).isTrue();
            assertThat(store.insert(kit.ids.nextId(), b, 902, at, null)).as("같은 판은 다시 넣지 않는다(ON CONFLICT)").isFalse();
        });
        Map<Long, EmoticonContractStore.Consent> latest = kit.inTx(() -> store.latest(List.of(a, b, kit.ids.nextId())));
        assertThat(latest).containsOnlyKeys(a, b);
        assertThat(latest.get(a)).isEqualTo(new EmoticonContractStore.Consent(902, at));
        assertThat(latest.get(b)).isEqualTo(new EmoticonContractStore.Consent(901, at.minusSeconds(5)));
        assertThat(kit.inTx(() -> store.consents(a))).extracting(EmoticonContractStore.Consent::version).containsExactly(903, 901, 902);
    }

    @Test
    void 팩마다_가장_최근_가격_전환은_같은_시각이면_큰_ID() {
        EmoticonPriceEventStore store = JpaTestContext.create(EmoticonPriceEventStore.class);
        long pack = kit.approvedPack(kit.member());
        long other = kit.approvedPack(kit.member());
        Instant at = kit.clock.instant();
        long first = kit.ids.nextId();
        long second = kit.ids.nextId();
        long older = kit.ids.nextId();
        kit.inTx(() -> {
            store.insert(second, pack, 1500, 2500, "같은 시각 뒤 ID", null, at, kit.operator);
            store.insert(first, pack, 0, 1500, "같은 시각 앞 ID", null, at, kit.operator);
            store.insert(older, other, 0, 1500, "다른 팩 하나뿐", null, at.minusSeconds(30), kit.operator);
        });
        Map<Long, EmoticonPriceEventStore.PriceEventRow> latest = kit.inTx(() -> store.latest(List.of(pack, other)));
        assertThat(latest.get(pack).id()).isEqualTo(second);
        assertThat(latest.get(pack).changedByName()).isNotNull();
        assertThat(latest.get(other).id()).isEqualTo(older);
        assertThat(kit.inTx(() -> store.byPack(pack))).extracting(EmoticonPriceEventStore.PriceEventRow::id).containsExactly(second, first);
    }

    @Test
    void 팩마다_마지막으로_낸_판과_마지막_심사_건() {
        long creator = kit.member();
        long pack = kit.approvedPack(creator);
        long pendingOnly = Long.parseLong(kit.submitNew(creator, "대기만").id());
        var sealed = kit.inTx(() -> kit.packStore.latestSealed(List.of(pack, pendingOnly)));
        assertThat(sealed.keySet()).containsExactly(Math.min(pack, pendingOnly), Math.max(pack, pendingOnly));
        assertThat(sealed.get(pack).version()).isEqualTo(kit.inTx(() -> kit.packStore.maxVersion(pack)));
        assertThat(sealed.get(pack)).isEqualTo(kit.inTx(() -> kit.packStore.latestSealed(pack)).orElseThrow());
        EmoticonReviewStore reviews = JpaTestContext.create(EmoticonReviewStore.class);
        var latest = kit.inTx(() -> reviews.latestOf(List.of(pack, pendingOnly)));
        assertThat(latest.get(pendingOnly).status().name()).isEqualTo("PENDING");
        assertThat(latest.get(pack).status().name()).isEqualTo("APPROVED");
    }

    // ── 두 질의로 나눈 것 · 계산으로 바꾼 것 ───────────────────────────────

    @Test
    void 가진_팩은_받기_줄과_작가_팩을_합쳐_가진_시각_내림차순_같으면_큰_ID_먼저() {
        long creator = kit.member();
        long own = kit.approvedPack(creator); // 처음 나온 시각 = 지금
        long freeA = kit.approvedPack(kit.member());
        long freeB = kit.approvedPack(kit.member());
        kit.inTx(() -> kit.acquire.acquire(creator, Long.toString(freeA))); // 같은 시각 — 받기 줄 ID 가 팩 ID 보다 크다
        kit.clock.advance(Duration.ofMinutes(1));
        kit.inTx(() -> kit.acquire.acquire(creator, Long.toString(freeB)));
        Map<Long, com.community.emoticon.repository.master.EmoticonAcquisitionStore.OwnedPack> owned = kit.inTx(() -> kit.acquisitionStore.owned(creator));
        assertThat(owned.keySet()).containsExactly(freeB, freeA, own);
        assertThat(owned.get(own).creator()).isTrue();
        assertThat(owned.get(freeA).creator()).isFalse();
        assertThat(owned.get(freeA).purchaseAmount()).isZero();
        assertThat(kit.inTx(() -> kit.acquisitionStore.ownedAmong(creator, List.of(own, freeA, freeB, kit.ids.nextId())))).containsExactlyInAnyOrder(own, freeA, freeB);
    }

    @Test
    void 다음_쪽_기준_값은_상점_목록의_정렬_값과_같다() {
        long pack = kit.approvedPack(kit.member());
        Long micros = kit.jdbc.queryForObject("SELECT (floor(extract(epoch FROM published_at) * 1000000))::bigint FROM emoticon_packs WHERE id = ?", Long.class, pack);
        assertThat(kit.inTx(() -> kit.reads.sortValue(pack, true))).isEqualTo(micros);
        assertThat(kit.inTx(() -> kit.reads.sortValue(pack, false))).isEqualTo(kit.acquireCount(pack));
        assertThat(kit.inTx(() -> kit.reads.sortValue(kit.ids.nextId(), true))).isZero();
    }

    @Test
    void 받은_수_올리기는_올린_뒤의_값이고_심사_탭_숫자는_상태_종류로_센다() {
        long pack = kit.approvedPack(kit.member());
        int before = kit.acquireCount(pack);
        int after = kit.inTx(() -> {
            kit.packStore.lock(pack);
            return kit.packStore.incrementAcquire(pack);
        });
        assertThat(after).isEqualTo(before + 1).isEqualTo(kit.acquireCount(pack));
        EmoticonReviewStore reviews = JpaTestContext.create(EmoticonReviewStore.class);
        Map<String, Integer> expected = new java.util.HashMap<>(Map.of("PENDING", 0, "UPDATE", 0, "REJECTED", 0));
        kit.jdbc.query("SELECT CASE WHEN status = 'REJECTED' THEN 'REJECTED' WHEN kind = 'UPDATE' THEN 'UPDATE' ELSE 'PENDING' END, count(*) FROM emoticon_reviews "
                + "WHERE status IN ('PENDING', 'REJECTED') GROUP BY 1", rs -> {
                    expected.put(rs.getString(1), rs.getInt(2));
                });
        Map<String, Integer> counts = kit.inTx(reviews::counts);
        assertThat(counts.keySet()).containsExactly("PENDING", "UPDATE", "REJECTED");
        assertThat(counts).isEqualTo(expected);
    }
}
