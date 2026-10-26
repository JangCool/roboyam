package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 글 복구 되돌림({@code POST_RESTORE}) — 결정 7, 계약 「1-1 서버 세부」: 회수한 묶음으로 되돌리고(만료면 30일 새 묶음), 그 글의 미회수 기록을 지우며 빚을 줄이고,
 * 이미 상계로 갚은 몫은 새 무상 묶음(1년)으로 준다. 되돌림 줄 하나, 합 0 이면 줄 없음, 같은 글에 한 번.
 */
class PointRestoreTest extends PointTestBase {

    private long post(long member) {
        long postId = kit.ids.nextId();
        assertThat(kit.earnPost(member, postId)).isEqualTo(20);
        return postId;
    }

    private Map<String, Object> restoreLine(long member, long postId) {
        return kit.jdbc.queryForMap("SELECT id, kind, delta, free_delta, paid_delta FROM point_ledger WHERE user_id = ? AND reason = 'POST_RESTORE' AND ref_id = ?", member,
                postId);
    }

    @Test
    void 회수한_묶음으로_되돌린다() {
        long member = kit.newMember();
        long postId = post(member);
        long lot = kit.lotOf(kit.entryId(member, "POST", "POST", postId));
        kit.adjust(member, 100);
        kit.revokePost(member, postId);
        assertThat(kit.remaining(lot)).isZero();

        assertThat(kit.restorePost(member, postId)).isEqualTo(20);

        Map<String, Object> line = restoreLine(member, postId);
        assertThat(line).containsEntry("kind", "EARN").containsEntry("delta", 20).containsEntry("free_delta", 20L);
        assertThat(kit.entryLots(((Number) line.get("id")).longValue())).containsExactlyEntriesOf(Map.of(lot, 20L));
        assertThat(kit.remaining(lot)).isEqualTo(20);
        assertThat(kit.free(member)).isEqualTo(120);
        kit.assertInvariants(member);
    }

    @Test
    void 만료된_묶음이면_30일_새_묶음으로_되돌린다() {
        long member = kit.newMember();
        Instant start = kit.clock.instant();
        long postId = post(member);
        long lot = kit.lotOf(kit.entryId(member, "POST", "POST", postId));
        kit.revokePost(member, postId);
        kit.clock.set(start.plus(Duration.ofDays(366)));

        assertThat(kit.restorePost(member, postId)).isEqualTo(20);

        Map<String, Object> restored = kit.jdbc.queryForMap("SELECT id, original_amount, expires_at, source_reason FROM point_lots WHERE restored_from_lot_id = ?", lot);
        assertThat(restored).containsEntry("original_amount", 20L).containsEntry("source_reason", "POST_RESTORE");
        assertThat(((Timestamp) restored.get("expires_at")).toInstant()).isEqualTo(kit.clock.instant().plus(Duration.ofDays(30)));
        assertThat(kit.remaining(lot)).isZero();
        kit.assertInvariants(member);
    }

    @Test
    void 미회수_기록을_지우고_빚을_줄인다_합이_0이면_줄_없음() {
        long member = kit.newMember();
        long postId = post(member);
        kit.use(member, 20);
        kit.revokePost(member, postId); // 반영 0, 빚 20
        assertThat(kit.debt(member)).isEqualTo(20);

        assertThat(kit.restorePost(member, postId)).isZero();

        assertThat(kit.debt(member)).isZero();
        assertThat(kit.count("SELECT count(*) FROM point_revoke_shortfalls WHERE user_id = ?", member)).isZero();
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'POST_RESTORE'", member)).isZero();
        kit.assertInvariants(member);
    }

    @Test
    void 상계로_이미_갚은_몫은_새_무상_묶음으로_되돌려_준다() {
        long member = kit.newMember();
        long first = post(member);
        long firstLot = kit.lotOf(kit.entryId(member, "POST", "POST", first));
        kit.use(member, 15); // 무상 5 남음
        kit.revokePost(member, first); // 5 회수, 15 미회수
        post(member); // +20, 상계 −15 → 빚 0
        assertThat(kit.debt(member)).isZero();
        assertThat(kit.free(member)).isEqualTo(5);

        assertThat(kit.restorePost(member, first)).isEqualTo(20); // 회수한 5 + 갚은 15

        Map<String, Object> line = restoreLine(member, first);
        long lineId = ((Number) line.get("id")).longValue();
        long fresh = kit.lotOf(lineId);
        assertThat(kit.entryLots(lineId)).containsExactlyInAnyOrderEntriesOf(Map.of(firstLot, 5L, fresh, 15L));
        assertThat(((Timestamp) kit.jdbc.queryForObject("SELECT expires_at FROM point_lots WHERE id = ?", Timestamp.class, fresh)).toInstant())
                .isEqualTo(kit.clock.instant().plus(Duration.ofDays(365)));
        assertThat(kit.balance(member)).containsExactly(25, 0, 0, 25);
        assertThat(kit.count("SELECT count(*) FROM point_revoke_shortfalls WHERE user_id = ?", member)).isZero();
        kit.assertInvariants(member);
    }

    @Test
    void 빚이_남아_있으면_빚만_줄이고_남은_몫만_돌려준다() {
        long member = kit.newMember();
        long first = post(member);
        long second = post(member);
        kit.use(member, 40);
        kit.revokePost(member, first); // 빚 20
        kit.revokePost(member, second); // 빚 40
        post(member); // +20, 상계 −20 → 빚 20

        // first 의 모자란 양 20: 줄일 빚 min(20, 20) = 20, 갚은 몫 0 → 줄 없음
        assertThat(kit.restorePost(member, first)).isZero();

        assertThat(kit.debt(member)).isZero();
        assertThat(kit.count("SELECT count(*) FROM point_revoke_shortfalls WHERE user_id = ?", member)).as("second 의 기록은 남는다").isEqualTo(1);
        kit.assertInvariants(member);
    }

    @Test
    void 같은_글은_한_번만_되돌리고_다시_지워도_두_번째_회수는_없다() {
        long member = kit.newMember();
        long postId = post(member);
        kit.revokePost(member, postId);

        assertThat(kit.restorePost(member, postId)).isEqualTo(20);
        assertThat(kit.restorePost(member, postId)).isZero();
        assertThat(kit.revokePost(member, postId)).isZero();

        assertThat(kit.free(member)).isEqualTo(20);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason IN ('POST_REVOKE', 'POST_RESTORE')", member)).isEqualTo(2);
        kit.assertInvariants(member);
    }
}
