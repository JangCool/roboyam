package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.grade.service.XpService;
import com.community.point.entity.PointReason;
import com.community.point.exception.PointFrozenException;
import com.community.point.exception.PointInsufficientException;
import com.community.point.service.PointService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/**
 * 포인트 게임의 포인트 규칙(docs/features/point-games-contract.md 「쓰는 포인트」·「룰렛 당첨」, V235) — {@link PointService#useFree}·{@link PointService#gameReward} 를
 * 실제 PostgreSQL 위에서 본다(커머스 18절 — 규칙마다 시험).
 *
 * <ul>
 *   <li>적립(무상) 묶음에서만 뺀다 — 충전(유상) 포인트는 무상이 모자라도 절대 빠지지 않는다(잔액 부족 = 무상 기준).</li>
 *   <li>잠금 뒤 조건({@code underLock})이 던지면 아무것도 쓰지 않는다 · 동결이면 거절.</li>
 *   <li>당첨은 새 무상 묶음(보통 적립과 같은 소멸) · 하루 활동 한도에 들지 않고 한도가 차도 받는다 · 경험치 사유가 아니다 · 같은 기록에 한 번.</li>
 *   <li>DB 가 새 사유·종류 짝과 대상을 받는다(사용 2종은 USE 만).</li>
 * </ul>
 */
class PointGameLedgerTest extends PointTestBase {

    private long useFree(long userId, long amount, PointReason reason, String refType, long refId, Runnable underLock) {
        return kit.inTx(() -> kit.points.useFree(userId, amount, reason, refType, refId, underLock));
    }

    @Test
    void 적립_포인트만_빠지고_충전_포인트는_그대로다() {
        long member = kit.newMember();
        kit.adjust(member, 30);
        long paidLot = kit.chargePaid(member, 500);
        long spin = kit.ids.nextId();

        long line = useFree(member, 20, PointReason.ROULETTE_SPIN, PointService.REF_ROULETTE_SPIN, spin, null);

        assertThat(kit.free(member)).isEqualTo(10);
        assertThat(kit.paid(member)).isEqualTo(500);
        assertThat(kit.remaining(paidLot)).isEqualTo(500);
        Map<String, Object> row = kit.jdbc.queryForMap("SELECT kind, reason, delta, free_delta, paid_delta, ref_type, ref_id FROM point_ledger WHERE id = ?", line);
        assertThat(row.get("kind")).isEqualTo("USE");
        assertThat(row.get("reason")).isEqualTo("ROULETTE_SPIN");
        assertThat(((Number) row.get("delta")).longValue()).isEqualTo(-20);
        assertThat(((Number) row.get("paid_delta")).longValue()).isZero();
        assertThat(row.get("ref_type")).isEqualTo("ROULETTE_SPIN");
        kit.assertInvariants(member);
    }

    @Test
    void 적립_포인트가_모자라면_충전_포인트가_많아도_409_POINT_INSUFFICIENT_이고_아무것도_쓰지_않는다() {
        long member = kit.newMember();
        kit.adjust(member, 30);
        kit.chargePaid(member, 1_000);
        int lines = kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ?", member);

        assertThatThrownBy(() -> useFree(member, 50, PointReason.ROULETTE_SPIN, PointService.REF_ROULETTE_SPIN, kit.ids.nextId(), null))
                .isInstanceOf(PointInsufficientException.class);
        assertThatThrownBy(() -> useFree(member, 80, PointReason.LOTTO_DRAW, PointService.REF_LOTTO_DRAW, kit.ids.nextId(), null))
                .isInstanceOf(PointInsufficientException.class);

        assertThat(kit.free(member)).isEqualTo(30);
        assertThat(kit.paid(member)).isEqualTo(1_000);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ?", member)).isEqualTo(lines);
        kit.assertInvariants(member);
    }

    @Test
    void 무상이_여러_묶음이면_소멸_임박순으로_빼고_유상은_건드리지_않는다() {
        long member = kit.newMember();
        long first = kit.lotOf(kit.grantLine(member, 15));
        kit.clock.advance(Duration.ofMinutes(1));
        long second = kit.lotOf(kit.grantLine(member, 40));
        long paidLot = kit.chargePaid(member, 100);

        useFree(member, 50, PointReason.LOTTO_DRAW, PointService.REF_LOTTO_DRAW, kit.ids.nextId(), null);

        assertThat(kit.remaining(first)).isZero();
        assertThat(kit.remaining(second)).isEqualTo(5);
        assertThat(kit.remaining(paidLot)).isEqualTo(100);
        kit.assertInvariants(member);
    }

    @Test
    void 잠금_뒤_조건이_던지면_아무것도_쓰지_않는다() {
        long member = kit.newMember();
        kit.adjust(member, 100);
        int lines = kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ?", member);

        assertThatThrownBy(() -> useFree(member, 50, PointReason.ROULETTE_SPIN, PointService.REF_ROULETTE_SPIN, kit.ids.nextId(), () -> {
            throw new IllegalStateException("하루 횟수");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(kit.free(member)).isEqualTo(100);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ?", member)).isEqualTo(lines);
    }

    @Test
    void 잠금_뒤_조건이_잔액_확인보다_먼저다() {
        long member = kit.newMember(); // 0P
        assertThatThrownBy(() -> useFree(member, 50, PointReason.ROULETTE_SPIN, PointService.REF_ROULETTE_SPIN, kit.ids.nextId(), () -> {
            throw new IllegalStateException("하루 횟수");
        })).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 동결_회원은_쓸_수_없다() {
        long member = kit.newMember();
        kit.adjust(member, 100);
        kit.freeze(member);
        assertThatThrownBy(() -> useFree(member, 50, PointReason.ROULETTE_SPIN, PointService.REF_ROULETTE_SPIN, kit.ids.nextId(), null))
                .isInstanceOf(PointFrozenException.class);
        assertThat(kit.free(member)).isEqualTo(100);
    }

    @Test
    void 사용_사유가_아니면_거절한다() {
        long member = kit.newMember();
        kit.adjust(member, 100);
        assertThatThrownBy(() -> useFree(member, 10, PointReason.ROULETTE_WIN, PointService.REF_ROULETTE_SPIN, kit.ids.nextId(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 당첨은_새_무상_묶음이고_보통_적립과_같은_소멸이며_같은_기록에_한_번() {
        long member = kit.newMember();
        kit.adjust(member, 50);
        long spin = kit.ids.nextId();
        Instant now = kit.clock.instant();

        long paid = kit.inTx(() -> {
            kit.points.useFree(member, 50, PointReason.ROULETTE_SPIN, PointService.REF_ROULETTE_SPIN, spin, null);
            return kit.points.gameReward(member, spin, 100);
        });
        long again = kit.inTx(() -> kit.points.gameReward(member, spin, 100));

        assertThat(paid).isEqualTo(100);
        assertThat(again).as("같은 기록에 두 번 주지 않는다").isZero();
        assertThat(kit.free(member)).isEqualTo(100);
        long line = kit.entryId(member, "ROULETTE_WIN", PointService.REF_ROULETTE_SPIN, spin);
        Map<String, Object> lot = kit.jdbc.queryForMap("SELECT kind, original_amount, expires_at, source_reason, source_ref_type FROM point_lots WHERE source_ledger_id = ?", line);
        assertThat(lot.get("kind")).isEqualTo("FREE");
        assertThat(((Number) lot.get("original_amount")).longValue()).isEqualTo(100);
        assertThat(lot.get("source_reason")).isEqualTo("ROULETTE_WIN");
        assertThat(lot.get("source_ref_type")).isEqualTo("ROULETTE_SPIN");
        Instant expires = ((java.sql.Timestamp) lot.get("expires_at")).toInstant();
        assertThat(expires).isEqualTo(now.plus(Duration.ofDays(kit.policy.lots().freeValidDays())));
        assertThat(kit.jdbc.queryForObject("SELECT kind FROM point_ledger WHERE id = ?", String.class, line)).isEqualTo("EARN");
        kit.assertInvariants(member);
    }

    @Test
    void 당첨은_하루_활동_한도에_들지_않고_한도가_차도_받는다() {
        long member = kit.newMember();
        kit.withSetting("daily_total_cap", 20, () -> {
            kit.earnPost(member, kit.ids.nextId()); // 활동 적립으로 한도를 채운다
            assertThat(kit.points.earnedToday(member)).isEqualTo(20);
            long spin = kit.ids.nextId();
            long won = kit.inTx(() -> {
                kit.points.useFree(member, 20, PointReason.ROULETTE_SPIN, PointService.REF_ROULETTE_SPIN, spin, null);
                return kit.points.gameReward(member, spin, 500);
            });
            assertThat(won).isEqualTo(500);
            assertThat(kit.points.earnedToday(member)).as("하루 총 한도가 세는 값은 그대로").isEqualTo(20);
        });
        assertThat(PointService.DAILY_TOTAL_REASONS).doesNotContain(PointReason.ROULETTE_WIN);
        kit.assertInvariants(member);
    }

    @Test
    void 당첨은_경험치_사유가_아니다() {
        assertThat(XpService.GAIN).doesNotContain(PointReason.ROULETTE_WIN, PointReason.ROULETTE_SPIN, PointReason.LOTTO_DRAW);
        assertThat(XpService.LOSS).doesNotContain(PointReason.ROULETTE_WIN, PointReason.ROULETTE_SPIN, PointReason.LOTTO_DRAW);
    }

    @Test
    void DB는_게임_사용_사유를_USE_로만_받는다() {
        long member = kit.newMember();
        String insert = "INSERT INTO point_ledger (id, user_id, kind, reason, delta, free_delta, paid_delta, balance_after, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 0, now())";
        for (String reason : List.of("ROULETTE_SPIN", "LOTTO_DRAW")) {
            assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), member, "EARN", reason, 10, 10, 0)).as(reason + " 는 USE 만")
                    .isInstanceOf(DataAccessException.class);
        }
        assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), member, "USE", "ROULETTE_WIN", -10, -10, 0)).as("당첨은 USE 가 아니다")
                .isInstanceOf(DataAccessException.class);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ?", member)).isZero();
    }
}
