package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.point.entity.PointLotKind;
import com.community.point.exception.PointInsufficientException;
import com.community.point.repository.master.PointLedgerWriter.Lot;
import com.community.point.service.PointDeductionPolicy;
import com.community.point.service.PointDeductionPolicy.Take;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 차감 순서(commerce.md 4절·5-4 — {@link PointDeductionPolicy})와 「소멸일이 지난 묶음은 쓰지 않는다」(계약 「1-1 서버 세부」).
 * 무상: 소멸일이 가까운 순 → 같으면 먼저 만든 순, 그다음 유상: 먼저 만든 순.
 */
class PointDeductionOrderTest extends PointTestBase {

    // ── 정책만(DB 없음) ──────────────────────────────────────────────────────

    private static Lot lot(long id, PointLotKind kind, long remaining, Instant expires, Instant created) {
        return new Lot(id, 1L, kind, remaining, remaining, expires, created);
    }

    @Test
    void 정책_무상은_소멸일_가까운_순_같으면_만든_순_그다음_유상은_ID_순() {
        Instant t = Instant.parse("2026-10-01T00:00:00Z");
        Lot paidOld = lot(10, PointLotKind.PAID, 5, null, t.plusSeconds(1));
        Lot paidNew = lot(20, PointLotKind.PAID, 5, null, t);
        Lot freeLate = lot(30, PointLotKind.FREE, 5, t.plus(Duration.ofDays(300)), t);
        Lot freeSoonNew = lot(40, PointLotKind.FREE, 5, t.plus(Duration.ofDays(10)), t.plusSeconds(9));
        Lot freeSoonOld = lot(50, PointLotKind.FREE, 5, t.plus(Duration.ofDays(10)), t.plusSeconds(1));
        Lot freeSoonOldSmallerId = lot(45, PointLotKind.FREE, 5, t.plus(Duration.ofDays(10)), t.plusSeconds(1));

        List<Lot> ordered = PointDeductionPolicy.order(List.of(paidOld, paidNew, freeLate, freeSoonNew, freeSoonOld, freeSoonOldSmallerId));

        assertThat(ordered).extracting(Lot::id).containsExactly(45L, 50L, 40L, 30L, 10L, 20L);
    }

    @Test
    void 정책_맨_앞_묶음을_먼저_쓰고_모자라면_예외() {
        Instant t = Instant.parse("2026-10-01T00:00:00Z");
        Lot a = lot(1, PointLotKind.FREE, 10, t.plus(Duration.ofDays(1)), t);
        Lot b = lot(2, PointLotKind.FREE, 10, t.plus(Duration.ofDays(2)), t);

        List<Take> takes = PointDeductionPolicy.allocate(List.of(a, b), 15, 2L);

        assertThat(takes).extracting(take -> take.lot().id(), Take::amount).containsExactly(org.assertj.core.groups.Tuple.tuple(2L, 10L),
                org.assertj.core.groups.Tuple.tuple(1L, 5L));
        assertThatThrownBy(() -> PointDeductionPolicy.allocate(List.of(a, b), 21, null)).isInstanceOf(IllegalStateException.class);
    }

    // ── 실제 사용 ────────────────────────────────────────────────────────────

    @Test
    void 사용은_무상_소멸_임박순_만든_순_그다음_유상_만든_순으로_뺀다() {
        long member = kit.newMember();
        long a = kit.lotOf(adjustLine(member, 100)); // 무상 A: 1년
        long[] b = new long[1];
        kit.withSetting("free_valid_days", 10, () -> b[0] = kit.lotOf(adjustLine(member, 50))); // 무상 B: 나중에 만들었지만 10일 뒤 소멸
        long c = kit.lotOf(adjustLine(member, 30)); // 무상 C: A 와 소멸일이 같고 나중에 만듦
        long p1 = kit.chargePaid(member, 40);
        long p2 = kit.chargePaid(member, 60);

        long first = kit.use(member, 170);

        assertThat(kit.entryLots(first)).containsExactlyInAnyOrderEntriesOf(Map.of(b[0], -50L, a, -100L, c, -20L));
        assertThat(kit.remaining(c)).isEqualTo(10);
        assertThat(kit.remaining(p1)).isEqualTo(40);

        long second = kit.use(member, 60);

        assertThat(kit.entryLots(second)).containsExactlyInAnyOrderEntriesOf(Map.of(c, -10L, p1, -40L, p2, -10L));
        Map<String, Object> line = kit.jdbc.queryForMap("SELECT kind, delta, free_delta, paid_delta FROM point_ledger WHERE id = ?", second);
        assertThat(line).containsEntry("kind", "USE").containsEntry("delta", -60).containsEntry("free_delta", -10L).containsEntry("paid_delta", -50L);
        assertThat(kit.balance(member)).containsExactly(0, 50, 0, 50);
        kit.assertInvariants(member);
    }

    @Test
    void 소멸일이_지난_묶음은_사용에_쓰지_않고_먼저_소멸_처리한다() {
        long member = kit.newMember();
        Instant start = kit.clock.instant();
        long old = kit.lotOf(adjustLine(member, 100)); // 1년 뒤 소멸
        kit.clock.advance(Duration.ofDays(200));
        long fresh = kit.lotOf(adjustLine(member, 80));
        kit.clock.set(start.plus(Duration.ofDays(366))); // old 는 소멸일이 지났지만 아직 소멸 줄이 없다
        assertThat(kit.free(member)).isEqualTo(180);

        // 쓸 수 있는 것은 80 뿐 — 100 은 거절(트랜잭션째 되돌아가 소멸 줄도 남지 않는다)
        assertThatThrownBy(() -> kit.use(member, 100)).isInstanceOf(PointInsufficientException.class);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND kind = 'EXPIRE'", member)).isZero();

        long used = kit.use(member, 60);

        assertThat(kit.entryLots(used)).containsExactlyEntriesOf(Map.of(fresh, -60L));
        long expireLine = kit.entryId(member, "EXPIRE", "LOT", old);
        assertThat(kit.entryLots(expireLine)).containsExactlyEntriesOf(Map.of(old, -100L));
        assertThat(expireLine).isLessThan(used); // 소멸이 먼저
        assertThat(kit.remaining(old)).isZero();
        assertThat(kit.balance(member)).containsExactly(20, 0, 0, 20);
        kit.assertInvariants(member);

    }

    /** 운영 지급 + 한 줄 — 그 줄 ID */
    private static long adjustLine(long member, int amount) {
        return kit.grantLine(member, amount);
    }
}
