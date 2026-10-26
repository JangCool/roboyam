package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.point.entity.PointReason;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 적립 = 무상 묶음, 회수는 무상만·그 적립 묶음 먼저·0 바닥·모자라면 미회수(P21), 같은 대상 회수는 한 번(빚도 한 번), 미회수 상계(P21b) — commerce.md 5-4, 계약 「1-1 서버 세부」.
 * 글 포인트는 시험 DB 기본 정책(글 20P, 하루 5번).
 */
class PointEarnRevokeTest extends PointTestBase {

    private long post(long member) {
        long postId = kit.ids.nextId();
        assertThat(kit.earnPost(member, postId)).isEqualTo(20);
        return postId;
    }

    @Test
    void 적립_하나는_무상_묶음_하나다_유효기간은_free_valid_days() {
        long member = kit.newMember();
        long postId = post(member);

        long line = kit.entryId(member, "POST", "POST", postId);
        long lot = kit.lotOf(line);
        Map<String, Object> row = kit.jdbc.queryForMap("SELECT kind, original_amount, remaining_amount, expires_at, source_reason, source_ref_type, source_ref_id "
                + "FROM point_lots WHERE id = ?", lot);
        assertThat(row).containsEntry("kind", "FREE").containsEntry("original_amount", 20L).containsEntry("remaining_amount", 20L).containsEntry("source_reason", "POST")
                .containsEntry("source_ref_type", "POST").containsEntry("source_ref_id", postId);
        Instant expires = ((java.sql.Timestamp) row.get("expires_at")).toInstant();
        assertThat(expires).isEqualTo(kit.clock.instant().plus(Duration.ofDays(365)).truncatedTo(ChronoUnit.MICROS));
        assertThat(kit.entryLots(line)).containsExactlyEntriesOf(Map.of(lot, 20L));
        assertThat(kit.jdbc.queryForMap("SELECT free_delta, paid_delta FROM point_ledger WHERE id = ?", line)).containsEntry("free_delta", 20L)
                .containsEntry("paid_delta", 0L);
        kit.assertInvariants(member);
    }

    @Test
    void 회수는_무상에서만_하고_유상은_그대로다_모자란_양은_미회수() {
        long member = kit.newMember();
        long postId = post(member);
        long paidLot = kit.chargePaid(member, 50);
        kit.use(member, 15); // 무상 20 중 15 를 쓴다(무상 먼저) — 무상 5 남음

        long revoked = kit.revokePost(member, postId);

        assertThat(revoked).isEqualTo(5);
        assertThat(kit.balance(member)).containsExactly(0, 50, 15, 50); // 무상 0 · 유상 50 그대로 · 미회수 15
        assertThat(kit.remaining(paidLot)).isEqualTo(50);
        long line = kit.entryId(member, "POST_REVOKE", "POST", postId);
        assertThat(kit.jdbc.queryForMap("SELECT kind, delta, free_delta, paid_delta FROM point_ledger WHERE id = ?", line)).containsEntry("kind", "REVOKE")
                .containsEntry("delta", -5).containsEntry("free_delta", -5L).containsEntry("paid_delta", 0L);
        assertThat(kit.jdbc.queryForMap("SELECT user_id, requested, applied FROM point_revoke_shortfalls WHERE reason = 'POST_REVOKE' AND ref_type = 'POST' AND ref_id = ?",
                postId)).containsEntry("user_id", member).containsEntry("requested", 20L).containsEntry("applied", 5L);
        kit.assertInvariants(member);
    }

    @Test
    void 잔액이_0이면_줄_없이_전부_미회수_P21() {
        long member = kit.newMember();
        long postId = post(member);
        kit.use(member, 20);

        assertThat(kit.revokePost(member, postId)).isZero();

        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'POST_REVOKE'", member)).isZero();
        assertThat(kit.debt(member)).isEqualTo(20);
        assertThat(kit.count("SELECT count(*) FROM point_revoke_shortfalls WHERE user_id = ? AND requested = 20 AND applied = 0", member)).isEqualTo(1);
        assertThat(kit.free(member)).isZero();
        kit.assertInvariants(member);
    }

    @Test
    void 같은_대상을_두_번_회수해도_줄_하나_빚_한_번() {
        long member = kit.newMember();
        long fullPost = post(member);
        assertThat(kit.revokePost(member, fullPost)).isEqualTo(20);
        assertThat(kit.revokePost(member, fullPost)).isZero();
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'POST_REVOKE'", member)).isEqualTo(1);

        long shortPost = post(member);
        kit.use(member, 20);
        kit.revokePost(member, shortPost);
        kit.revokePost(member, shortPost);

        assertThat(kit.debt(member)).isEqualTo(20); // 40 이 아니다
        assertThat(kit.count("SELECT count(*) FROM point_revoke_shortfalls WHERE user_id = ?", member)).isEqualTo(1);
        kit.assertInvariants(member);
    }

    @Test
    void 회수는_그_적립이_만든_묶음에서_먼저_뺀다() {
        long member = kit.newMember();
        long older = kit.lotOf(kit.grantLine(member, 100)); // 차감 순서로는 이 묶음이 먼저(소멸이 더 이르다)
        kit.clock.advance(Duration.ofDays(1));
        long postId = post(member);
        long earned = kit.lotOf(kit.entryId(member, "POST", "POST", postId));

        kit.revokePost(member, postId);

        assertThat(kit.remaining(earned)).isZero();
        assertThat(kit.remaining(older)).isEqualTo(100);
        kit.assertInvariants(member);
    }

    @Test
    void 그_묶음이_모자라면_다른_무상_묶음에서_소멸_임박순으로_뺀다() {
        long member = kit.newMember();
        long postId = post(member);
        long earned = kit.lotOf(kit.entryId(member, "POST", "POST", postId));
        kit.use(member, 15); // earned 20 → 5
        kit.clock.advance(Duration.ofDays(2));
        long later = kit.lotOf(kit.grantLine(member, 100)); // 소멸이 더 늦다
        kit.clock.advance(Duration.ofDays(-1));
        long sooner = kit.lotOf(kit.grantLine(member, 100)); // 나중에 만들었지만 later 보다 하루 먼저 소멸
        long paidLot = kit.chargePaid(member, 30);

        assertThat(kit.revokePost(member, postId)).isEqualTo(20);

        assertThat(kit.remaining(earned)).isZero();
        assertThat(kit.remaining(sooner)).isEqualTo(85);
        assertThat(kit.remaining(later)).isEqualTo(100);
        assertThat(kit.remaining(paidLot)).isEqualTo(30);
        assertThat(kit.debt(member)).isZero();
        kit.assertInvariants(member);
    }

    @Test
    void 미회수가_있으면_적립_직후_상계하고_하루_한도는_1회로_센다_P21b() {
        long member = kit.newMember();
        long first = post(member);
        kit.use(member, 20);
        kit.revokePost(member, first); // 빚 20
        assertThat(kit.debt(member)).isEqualTo(20);
        int before = kit.inTx(() -> kit.points.countToday(member, PointReason.POST));

        long second = post(member); // 적립 +20 → 상계 −20

        long earnLine = kit.entryId(member, "POST", "POST", second);
        long offsetLine = kit.entryId(member, "REVOKE_DEBT_OFFSET", "LEDGER", earnLine);
        assertThat(offsetLine).isGreaterThan(earnLine);
        assertThat(kit.jdbc.queryForMap("SELECT kind, delta, free_delta, paid_delta FROM point_ledger WHERE id = ?", offsetLine)).containsEntry("kind", "REVOKE")
                .containsEntry("delta", -20).containsEntry("free_delta", -20L).containsEntry("paid_delta", 0L);
        assertThat(kit.entryLots(offsetLine)).containsExactlyEntriesOf(Map.of(kit.lotOf(earnLine), -20L)); // 방금 만든 묶음에서
        assertThat(kit.balance(member)).containsExactly(0, 0, 0, 0);
        assertThat(kit.inTx(() -> kit.points.countToday(member, PointReason.POST))).isEqualTo(before + 1);
        kit.assertInvariants(member);
    }

    @Test
    void 빚이_적립보다_크면_적립만큼만_상계하고_빚이_남는다() {
        long member = kit.newMember();
        long[] posts = {post(member), post(member)};
        kit.use(member, 40);
        kit.revokePost(member, posts[0]);
        kit.revokePost(member, posts[1]); // 빚 40

        post(member);

        assertThat(kit.balance(member)).containsExactly(0, 0, 20, 0);
        kit.assertInvariants(member);
    }

    @Test
    void 미회수_상계는_유상을_건드리지_않는다() {
        long member = kit.newMember();
        long first = post(member);
        kit.use(member, 20);
        kit.revokePost(member, first); // 빚 20
        long paidLot = kit.chargePaid(member, 100);

        post(member);

        assertThat(kit.remaining(paidLot)).isEqualTo(100);
        assertThat(kit.balance(member)).containsExactly(0, 100, 0, 100);
        kit.assertInvariants(member);
    }

    @Test
    void 동결_중이면_적립을_건너뛴다() {
        long member = kit.newMember();
        kit.freeze(member);

        assertThat(kit.earnPost(member, kit.ids.nextId())).isZero();
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ?", member)).isZero();
    }
}
