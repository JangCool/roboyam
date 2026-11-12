package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.emoticon.dto.response.EmoticonPublicView;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.exception.EmoticonPackNotFoundException;
import com.community.emoticon.service.EmoticonAcquireService;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

/**
 * 무료 팩 받기·빼기·내 로보티콘과 받기 보상(emoticon-contract 가정 6·11, S1) — 실제 PostgreSQL. 보상은 {@code PointService} 한 길(원장 사유 {@code EMOTICON_CREATOR_REWARD}, 대상 = 받기 줄).
 * 인정된 받기(본인 아님 · 이메일 인증 정상 회원 · 새싹보다 높은 등급) · 작가 하루 상한(포인트 합 — 남은 만큼만, 동시 받기도 넘지 않는다) · 같은 회원의 같은 팩은 처음 한 번만(빼고 다시 받아도) ·
 * 운영 팩 보상 없음 · 실패해도 받기는 성공(point-contract 가정 1).
 */
class EmoticonAcquireRewardTest extends EmoticonTestBase {

    private long official() {
        return kit.jdbc.queryForObject("SELECT p.id FROM emoticon_packs p JOIN emoticon_pack_revisions r ON r.id = p.live_revision_id WHERE p.source = 'OFFICIAL' AND r.name = '아이로빗콘' "
                + "ORDER BY p.id LIMIT 1", Long.class);
    }

    private EmoticonAcquireService.Acquired acquire(long member, long pack) {
        return kit.inTx(() -> kit.acquire.acquire(member, Long.toString(pack)));
    }

    @Test
    void 받기는_멱등이고_받은_수는_처음_한_번만_오른다() {
        long member = kit.member();
        long pack = official();
        int before = kit.acquireCount(pack);
        EmoticonPublicView.AcquireResult first = acquire(member, pack).result();
        EmoticonPublicView.AcquireResult again = acquire(member, pack).result();
        assertThat(first.acquired()).isTrue();
        assertThat(first.acquireCount()).isEqualTo(before + 1);
        assertThat(again.acquireCount()).isEqualTo(before + 1);
        assertThat(kit.acquireCount(pack)).isEqualTo(before + 1);
        assertThat(kit.shop.pack(member, Long.toString(pack)).acquired()).isTrue();
    }

    @Test
    void 작가_팩을_인정된_회원이_받으면_작가에게_적립_보상_빼고_다시_받아도_처음_한_번만() {
        long creator = kit.member();
        long pack = kit.approvedPack(creator);
        long fan = kit.member();
        EmoticonAcquireService.Acquired got = acquire(fan, pack);
        assertThat(got.rewarded()).isEqualTo(10);
        assertThat(kit.rewards(creator)).isEqualTo(10);
        assertThat(kit.balance(creator)).isEqualTo(10);
        // 원장 줄 — 적립(EARN) · 대상 = 그 받기 줄
        long acquisition = kit.jdbc.queryForObject("SELECT id FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ?", Long.class, fan, pack);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'EMOTICON_CREATOR_REWARD' AND kind = 'EARN' AND ref_type = 'EMOTICON_ACQUIRE' "
                + "AND ref_id = ?", creator, acquisition)).isEqualTo(1);

        kit.inTx(() -> kit.acquire.remove(fan, Long.toString(pack)));
        assertThat(kit.acquire.mine(fan).packs()).extracting(EmoticonPublicView.MyPack::id).doesNotContain(Long.toString(pack));
        assertThat(kit.shop.pack(fan, Long.toString(pack)).acquired()).isFalse();
        EmoticonAcquireService.Acquired back = acquire(fan, pack);
        assertThat(back.rewarded()).isZero();
        assertThat(back.result().acquireCount()).isEqualTo(1);
        assertThat(kit.rewards(creator)).isEqualTo(10);
        assertThat(kit.acquireCount(pack)).isEqualTo(1);
        assertThat(kit.acquire.mine(fan).packs()).extracting(EmoticonPublicView.MyPack::id).contains(Long.toString(pack));
        kit.assertInvariants(creator);
    }

    @Test
    void 운영_팩_본인_새싹_이메일_미인증_정지_회원의_받기는_보상이_없다() {
        long creator = kit.member();
        long pack = kit.approvedPack(creator);
        assertThat(acquire(kit.member(), official()).rewarded()).as("운영 팩").isZero();
        assertThat(acquire(creator, pack).rewarded()).as("본인").isZero();
        assertThat(acquire(kit.member("SEEDLING"), pack).rewarded()).as("새싹").isZero();
        long unverified = kit.member();
        kit.unverify(unverified);
        assertThat(acquire(unverified, pack).rewarded()).as("이메일 인증 전").isZero();
        long suspended = kit.member();
        kit.suspend(suspended);
        assertThat(acquire(suspended, pack).rewarded()).as("정지 회원").isZero();
        assertThat(kit.rewards(creator)).isZero();
        // 보상이 없어도 받은 수는 센다 — 작가 본인 받기는 받기 줄이 없어 세지 않는다(후속 결정 1: 자기 팩은 사지·받지 않아도 가진다)
        assertThat(kit.acquireCount(pack)).as("보상이 없어도 받은 수는 센다(본인 제외)").isEqualTo(3);
    }

    @Test
    void 포인트_동결_작가는_보상을_받지_않는다_받기는_성공() {
        long creator = kit.member();
        long pack = kit.approvedPack(creator);
        kit.jdbc.update("INSERT INTO member_restrictions (id, user_id, type, reason, starts_at, source, auto_rule, created_at) VALUES (?, ?, 'POINT_FREEZE', '시험 동결', ?, 'AUTO', "
                + "'EMOTICON_TEST', ?)", kit.ids.nextId(), creator, Timestamp.from(kit.clock.instant().minusSeconds(60)), Timestamp.from(kit.clock.instant()));
        EmoticonAcquireService.Acquired got = acquire(kit.member(), pack);
        assertThat(got.result().acquired()).isTrue();
        assertThat(got.rewarded()).isZero();
    }

    @Test
    void 작가_하루_상한은_포인트_합이고_걸치면_남은_만큼만_그다음은_없다() {
        long creator = kit.member();
        long pack = kit.approvedPack(creator);
        kit.withSettings(10, 25, 3, () -> {
            assertThat(acquire(kit.member(), pack).rewarded()).isEqualTo(10);
            assertThat(acquire(kit.member(), pack).rewarded()).isEqualTo(10);
            assertThat(acquire(kit.member(), pack).rewarded()).isEqualTo(5);
            assertThat(acquire(kit.member(), pack).rewarded()).isZero();
        });
        assertThat(kit.rewards(creator)).isEqualTo(25);
        kit.assertInvariants(creator);
    }

    @Test
    void 동시에_여러_회원이_작가의_여러_팩을_받아도_하루_상한을_넘지_않는다() throws Exception {
        long creator = kit.member();
        long pack = kit.approvedPack(creator);
        long other = kit.approvedPack(creator); // 팩 행 잠금이 다른 받기끼리도 작가 잔액 행 잠금 아래에서 상한을 센다
        List<Long> fans = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            fans.add(kit.member());
        }
        kit.withSettings(10, 30, 3, () -> {
            List<Callable<Object>> jobs = new ArrayList<>();
            for (int i = 0; i < fans.size(); i++) {
                long fan = fans.get(i);
                long target = i % 2 == 0 ? pack : other;
                jobs.add(() -> acquire(fan, target));
            }
            try {
                List<Object> results = EmoticonTestKit.runTogether(jobs);
                assertThat(results).allMatch(r -> r instanceof EmoticonAcquireService.Acquired);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(kit.rewards(creator)).isEqualTo(30);
        assertThat(kit.acquireCount(pack) + kit.acquireCount(other)).isEqualTo(6);
        kit.assertInvariants(creator);
    }

    @Test
    void 같은_회원이_같은_팩을_동시에_두_번_받아도_한_줄_보상_한_번() throws Exception {
        long creator = kit.member();
        long pack = kit.approvedPack(creator);
        long fan = kit.member();
        List<Object> results = EmoticonTestKit.runTogether(List.of(() -> acquire(fan, pack), () -> acquire(fan, pack), () -> acquire(fan, pack)));
        assertThat(results).allMatch(r -> r instanceof EmoticonAcquireService.Acquired);
        assertThat(kit.count("SELECT count(*) FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ?", fan, pack)).isEqualTo(1);
        assertThat(kit.acquireCount(pack)).isEqualTo(1);
        assertThat(kit.rewards(creator)).isEqualTo(10);
    }

    @Test
    void 보상_적립이_실패해도_받기는_성공한다() {
        long creator = kit.member();
        long pack = kit.approvedPack(creator);
        kit.jdbc.execute("CREATE OR REPLACE FUNCTION emoticon_test_block_reward() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "IF NEW.reason = 'EMOTICON_CREATOR_REWARD' AND NEW.user_id = " + creator + " THEN RAISE EXCEPTION 'blocked for test'; END IF; RETURN NEW; END $$");
        kit.jdbc.execute("CREATE TRIGGER tr_emoticon_test_block_reward BEFORE INSERT ON point_ledger FOR EACH ROW EXECUTE FUNCTION emoticon_test_block_reward()");
        try {
            long fan = kit.member();
            EmoticonAcquireService.Acquired got = acquire(fan, pack);
            assertThat(got.result().acquired()).isTrue();
            assertThat(got.rewarded()).isZero();
            assertThat(kit.count("SELECT count(*) FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ? AND removed_at IS NULL", fan, pack)).isEqualTo(1);
            assertThat(kit.acquireCount(pack)).isEqualTo(1);
        } finally {
            kit.jdbc.execute("DROP TRIGGER IF EXISTS tr_emoticon_test_block_reward ON point_ledger");
            kit.jdbc.execute("DROP FUNCTION IF EXISTS emoticon_test_block_reward()");
        }
        assertThat(kit.rewards(creator)).isZero();
        kit.assertInvariants(creator);
    }

    @Test
    void 판매_중단_팩은_받지_못하고_없는_팩_심사_중_팩_형식이_틀린_ID_는_404() {
        long creator = kit.member();
        long pack = kit.approvedPack(creator);
        long owner = kit.member();
        acquire(owner, pack);
        kit.inTx(() -> kit.packs.suspend(kit.operator, "127.0.0.1", Long.toString(pack), "권리 확인 중이에요"));
        assertThatThrownBy(() -> acquire(kit.member(), pack)).isInstanceOfSatisfying(EmoticonConflictException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo("EMOTICON_PACK_SUSPENDED"));
        long pending = Long.parseLong(kit.submitNew(creator, "심사 중 팩").id());
        assertThatThrownBy(() -> acquire(kit.member(), pending)).isInstanceOf(EmoticonPackNotFoundException.class);
        assertThatThrownBy(() -> kit.inTx(() -> kit.acquire.acquire(kit.member(), "abc"))).isInstanceOf(EmoticonPackNotFoundException.class);
        assertThatThrownBy(() -> acquire(kit.member(), kit.ids.nextId())).isInstanceOf(EmoticonPackNotFoundException.class);
        // 판매 중단 팩도 가진 사람은 계속 쓴다(내 로보티콘에 남고 판매 중단 표시) · 상세는 가진 사람·작가만
        EmoticonPublicView.MyPack mine = kit.acquire.mine(owner).packs().stream().filter(p -> p.id().equals(Long.toString(pack))).findFirst().orElseThrow();
        assertThat(mine.suspended()).isTrue();
        assertThat(mine.emoticons()).hasSize(8);
        assertThat(kit.shop.pack(owner, Long.toString(pack)).saleState()).isEqualTo("SUSPENDED");
        assertThat(kit.shop.pack(creator, Long.toString(pack)).saleState()).isEqualTo("SUSPENDED");
        assertThatThrownBy(() -> kit.shop.pack(kit.member(), Long.toString(pack))).isInstanceOf(EmoticonPackNotFoundException.class);
        assertThatThrownBy(() -> kit.shop.pack(null, Long.toString(pack))).isInstanceOf(EmoticonPackNotFoundException.class);
    }

    @Test
    void 내_로보티콘은_최근_가진_것_먼저() {
        long member = kit.member();
        long creator = kit.member();
        long first = kit.approvedPack(creator);
        long second = kit.approvedPack(creator);
        acquire(member, first);
        kit.clock.advance(java.time.Duration.ofMinutes(1));
        acquire(member, second);
        List<String> ids = kit.acquire.mine(member).packs().stream().map(EmoticonPublicView.MyPack::id).toList();
        assertThat(ids).containsExactly(Long.toString(second), Long.toString(first));
        EmoticonPublicView.MyPack top = kit.acquire.mine(member).packs().get(0);
        assertThat(top.price()).isZero();
        assertThat(top.purchaseId()).isNull();
        assertThat(top.source()).isEqualTo("CREATOR");
        assertThat(top.creatorNickname()).isNotNull();
    }
}
