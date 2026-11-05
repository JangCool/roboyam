package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.emoticon.dto.response.EmoticonPublicView;
import com.community.emoticon.exception.EmoticonNotOwnedException;
import com.community.emoticon.exception.EmoticonPackNotFoundException;
import com.community.emoticon.service.EmoticonAcquireService;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 운영자 회원 계정은 판매 중 팩 전부를 쓴다(docs/features/emoticon-contract.md 「운영자 회원 계정 연결(2026-11-05)」 · operator-member-account-contract.md 정책 15) — 실제 PostgreSQL.
 * 쓸 수 있음 ≠ 가짐: 받기 줄 · 받은 수 · 받기 보상 · 「사용함」 · 판매가 생기지 않고, 힘이 멈추면(연결 해제 · 운영자 사용 중지) 바로 일반 규칙. 판매 중단 팩은 일반 규칙 그대로.
 * 일반 회원 · 게시판 매니저 · 스텝은 바뀌지 않는다. 연결은 SQL 로 바로 넣는다(연결 규칙은 관리자 쪽 시험).
 */
class EmoticonOperatorAccountTest extends EmoticonTestBase {

    // ── 재료 ───────────────────────────────────────────────────────────────

    /** 이어진 운영자 회원 계정(회원 ID) — 운영자는 승인 · 사용 중 */
    private long operatorAccount() {
        long user = kit.member();
        kit.jdbc.update("INSERT INTO operator_member_accounts (operator_id, user_id, linked_at) VALUES (?, ?, now())", kit.newOperator(), user);
        return user;
    }

    private long operatorOf(long userId) {
        return kit.jdbc.queryForObject("SELECT operator_id FROM operator_member_accounts WHERE user_id = ?", Long.class, userId);
    }

    private void unlink(long userId) {
        kit.jdbc.update("DELETE FROM operator_member_accounts WHERE user_id = ?", userId);
    }

    private long official(String name) {
        return kit.jdbc.queryForObject("SELECT p.id FROM emoticon_packs p JOIN emoticon_pack_revisions r ON r.id = p.live_revision_id WHERE p.source = 'OFFICIAL' AND r.name = ? "
                + "ORDER BY p.id LIMIT 1", Long.class, name);
    }

    private static String tokens(List<Long> ids) {
        return ids.stream().map(id -> ":emo-" + id + ":").collect(Collectors.joining(" "));
    }

    private int acquisitionRows(long userId) {
        return kit.count("SELECT count(*) FROM emoticon_acquisitions WHERE user_id = ?", userId);
    }

    /** 게시판 하나에 매니저·스텝 지정 */
    private void boardStaff(long userId, String role) {
        long board = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO boards (id, slug, name, sort_order) VALUES (?, ?, ?, 962)", board, "eo" + Long.toString(board, 36), "운영자 팩 시험 " + Long.toString(board, 36));
        kit.jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, ?)", board, userId, role);
    }

    // ── 토큰 검사 ───────────────────────────────────────────────────────────

    @Test
    void 운영자_회원_계정은_가지지_않은_판매_중_팩을_쓰고_받기_줄_받은_수_보상_사용함이_생기지_않는다() {
        long creator = kit.member();
        long creatorPack = kit.approvedPack(creator);
        long robot = official("로보얌콘");
        long user = operatorAccount();
        int countBefore = kit.acquireCount(creatorPack);
        long rewardsBefore = kit.rewards(creator);
        String body = tokens(List.of(kit.liveEmoticons(creatorPack).get(0), kit.liveEmoticons(robot).get(1)));
        kit.guard.requireOwned(user, body);
        assertThat(kit.<Integer>inTx(() -> kit.guard.recordUse(user, body))).isZero();
        assertThat(acquisitionRows(user)).isZero();
        assertThat(kit.acquireCount(creatorPack)).isEqualTo(countBefore);
        assertThat(kit.rewards(creator)).isEqualTo(rewardsBefore);
        assertThat(kit.count("SELECT count(*) FROM emoticon_purchases WHERE user_id = ?", user)).isZero();
        // 같은 본문 — 일반 회원은 409
        assertThatThrownBy(() -> kit.guard.requireOwned(kit.member(), body)).isInstanceOf(EmoticonNotOwnedException.class);
    }

    @Test
    void 판매_중단_팩은_일반_규칙_가졌으면_쓰고_아니면_409() {
        long creator = kit.member();
        long held = kit.approvedPack(creator);
        long notHeld = kit.approvedPack(creator);
        long user = operatorAccount();
        unlink(user);
        kit.inTx(() -> kit.acquire.acquire(user, Long.toString(held))); // 연결 전에 받아 둔 팩
        kit.jdbc.update("INSERT INTO operator_member_accounts (operator_id, user_id, linked_at) VALUES (?, ?, now())", kit.newOperator(), user);
        kit.inTx(() -> kit.packs.suspend(kit.operator, null, Long.toString(held), "확인하는 동안 멈춰요"));
        kit.inTx(() -> kit.packs.suspend(kit.operator, null, Long.toString(notHeld), "확인하는 동안 멈춰요"));
        kit.guard.requireOwned(user, tokens(kit.liveEmoticons(held).subList(0, 1)));
        long missed = kit.liveEmoticons(notHeld).get(0);
        assertThatThrownBy(() -> kit.guard.requireOwned(user, tokens(List.of(missed)))).isInstanceOfSatisfying(EmoticonNotOwnedException.class,
                e -> assertThat(e.getExtras()).containsEntry("emoticonId", Long.toString(missed)));
        // 판매 중단 팩의 상세는 가지지 않은 운영자 회원 계정에게도 404(일반 규칙)
        assertThatThrownBy(() -> kit.shop.pack(user, Long.toString(notHeld))).isInstanceOf(EmoticonPackNotFoundException.class);
    }

    @Test
    void 연결을_풀거나_운영자를_사용_중지하면_바로_409() {
        long robot = official("로보얌콘");
        String body = tokens(kit.liveEmoticons(robot).subList(0, 2));
        long unlinked = operatorAccount();
        kit.guard.requireOwned(unlinked, body);
        unlink(unlinked);
        assertThatThrownBy(() -> kit.guard.requireOwned(unlinked, body)).isInstanceOf(EmoticonNotOwnedException.class);
        long disabled = operatorAccount();
        kit.guard.requireOwned(disabled, body);
        kit.jdbc.update("UPDATE operators SET enabled = false WHERE id = ?", operatorOf(disabled));
        assertThatThrownBy(() -> kit.guard.requireOwned(disabled, body)).isInstanceOf(EmoticonNotOwnedException.class);
        // 다시 켜면 다시 쓴다(요청마다 판정 — 캐시 없음)
        kit.jdbc.update("UPDATE operators SET enabled = true WHERE id = ?", operatorOf(disabled));
        kit.guard.requireOwned(disabled, body);
        // 회원이 사이트 정지 중이어도 힘이 멈춘다
        kit.suspend(disabled);
        assertThatThrownBy(() -> kit.guard.requireOwned(disabled, body)).isInstanceOf(EmoticonNotOwnedException.class);
    }

    @Test
    void 게시판_매니저_스텝은_바뀌지_않는다() {
        long robot = official("로보얌콘");
        String body = tokens(kit.liveEmoticons(robot).subList(0, 1));
        long manager = kit.member();
        boardStaff(manager, "MANAGER");
        long staff = kit.member();
        boardStaff(staff, "STAFF");
        for (long who : new long[] {manager, staff}) {
            assertThatThrownBy(() -> kit.guard.requireOwned(who, body)).isInstanceOf(EmoticonNotOwnedException.class);
            assertThat(kit.acquire.mine(who).allPacks()).isFalse();
            assertThat(kit.shop.pack(who, Long.toString(robot)).operatorUsable()).isFalse();
        }
    }

    // ── 받기 · 내 로보티콘 · 팩 상세 ──────────────────────────────────────────

    @Test
    void 받기는_200_이지만_줄을_만들거나_되살리지_않고_연결을_풀면_일반_받기() {
        long creator = kit.member();
        long pack = kit.approvedPack(creator);
        long user = operatorAccount();
        int before = kit.acquireCount(pack);
        EmoticonAcquireService.Acquired got = kit.inTx(() -> kit.acquire.acquire(user, Long.toString(pack)));
        assertThat(got.result().acquired()).isFalse();
        assertThat(got.result().acquireCount()).isEqualTo(before);
        assertThat(got.rewarded()).isZero();
        assertThat(acquisitionRows(user)).isZero();
        assertThat(kit.acquireCount(pack)).isEqualTo(before);
        // 연결을 풀면 보통 회원 받기(받은 수 + 1)
        unlink(user);
        assertThat(kit.inTx(() -> kit.acquire.acquire(user, Long.toString(pack))).result().acquired()).isTrue();
        assertThat(kit.acquireCount(pack)).isEqualTo(before + 1);
        // 다시 이은 뒤: 가진 줄은 그대로 acquired = true, 뺀 줄은 되살리지 않는다
        kit.jdbc.update("INSERT INTO operator_member_accounts (operator_id, user_id, linked_at) VALUES (?, ?, now())", kit.newOperator(), user);
        assertThat(kit.inTx(() -> kit.acquire.acquire(user, Long.toString(pack))).result().acquired()).isTrue();
        kit.inTx(() -> kit.acquire.remove(user, Long.toString(pack)));
        assertThat(kit.inTx(() -> kit.acquire.acquire(user, Long.toString(pack))).result().acquired()).isFalse();
        assertThat(kit.count("SELECT count(*) FROM emoticon_acquisitions WHERE user_id = ? AND removed_at IS NULL", user)).isZero();
        assertThat(kit.acquireCount(pack)).isEqualTo(before + 1);
    }

    @Test
    void 내_로보티콘의_allPacks_는_힘이_살아_있는_운영자_회원_계정만_목록은_가진_팩만() {
        long robot = official("로보얌콘");
        long user = operatorAccount();
        EmoticonPublicView.MyPacks mine = kit.acquire.mine(user);
        assertThat(mine.allPacks()).isTrue();
        assertThat(mine.packs()).isEmpty();
        assertThat(kit.acquire.mine(kit.member()).allPacks()).isFalse();
        kit.jdbc.update("UPDATE operators SET enabled = false WHERE id = ?", operatorOf(user));
        assertThat(kit.acquire.mine(user).allPacks()).isFalse();
        kit.jdbc.update("UPDATE operators SET enabled = true WHERE id = ?", operatorOf(user));
        unlink(user);
        kit.inTx(() -> kit.acquire.acquire(user, Long.toString(robot)));
        kit.jdbc.update("INSERT INTO operator_member_accounts (operator_id, user_id, linked_at) VALUES (?, ?, now())", kit.newOperator(), user);
        mine = kit.acquire.mine(user);
        assertThat(mine.allPacks()).isTrue();
        assertThat(mine.packs()).extracting(EmoticonPublicView.MyPack::id).containsExactly(Long.toString(robot));
    }

    @Test
    void 팩_상세의_operatorUsable_은_판매_중_팩이고_acquired_는_그대로() {
        long robot = official("로보얌콘");
        long user = operatorAccount();
        EmoticonPublicView.PackDetail detail = kit.shop.pack(user, Long.toString(robot));
        assertThat(detail.operatorUsable()).isTrue();
        assertThat(detail.acquired()).isFalse();
        assertThat(kit.shop.pack(kit.member(), Long.toString(robot)).operatorUsable()).isFalse();
        assertThat(kit.shop.pack(null, Long.toString(robot)).operatorUsable()).isFalse();
        // 상점 카드의 acquired 도 그대로
        assertThat(kit.shop.packs(user, null, null, null, null, null).items()).noneMatch(EmoticonPublicView.PackCard::acquired);
        unlink(user);
        assertThat(kit.shop.pack(user, Long.toString(robot)).operatorUsable()).isFalse();
    }
}
