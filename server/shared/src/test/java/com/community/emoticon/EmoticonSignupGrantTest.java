package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.emoticon.dto.response.EmoticonPublicView;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 가입 때 무료 운영 로보티콘(사용자 결정 2026-11-12, {@code EmoticonAcquireService#grantSignupPacks}) — 실제 PostgreSQL. 판매 중인 무료 운영 팩만 · 상점 받기와 같은 받기 줄(FREE)·받은 수 ·
 * 다시 불러도 한 번만 · 스스로 뺀 팩은 되살리지 않는다 · 작가 무료 팩은 주지 않는다 · 운영자 회원 계정은 받기 줄을 만들지 않는다.
 */
class EmoticonSignupGrantTest extends EmoticonTestBase {

    private List<Long> officialFree() {
        return kit.jdbc.queryForList("SELECT id FROM emoticon_packs WHERE source = 'OFFICIAL' AND status = 'APPROVED' AND price = 0 ORDER BY id", Long.class);
    }

    private List<String> mine(long member) {
        return kit.acquire.mine(member).packs().stream().map(EmoticonPublicView.MyPack::id).toList();
    }

    private Map<Long, Integer> counts(List<Long> packs) {
        return packs.stream().collect(Collectors.toMap(p -> p, kit::acquireCount));
    }

    @Test
    void 새_회원은_무료_운영_팩을_상점_받기와_같은_줄로_한_번만_받는다() {
        List<Long> packs = officialFree();
        assertThat(packs).as("시드 운영 팩(아이로빗콘 남·여)").isNotEmpty();
        Map<Long, Integer> before = counts(packs);
        long member = kit.member();

        assertThat(kit.acquire.grantSignupPacks(member)).isEqualTo(packs.size());
        assertThat(mine(member)).containsAll(packs.stream().map(String::valueOf).toList());
        assertThat(kit.count("SELECT count(*) FROM emoticon_acquisitions WHERE user_id = ? AND kind = 'FREE' AND removed_at IS NULL", member)).isEqualTo(packs.size());
        packs.forEach(p -> {
            assertThat(kit.acquireCount(p)).as("받은 수 + 1").isEqualTo(before.get(p) + 1);
            assertThat(kit.shop.pack(member, Long.toString(p)).acquired()).isTrue();
        });
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE reason = 'EMOTICON_CREATOR_REWARD' AND ref_id IN "
                + "(SELECT id FROM emoticon_acquisitions WHERE user_id = ?)", member)).as("운영 팩은 받기 보상이 없다").isZero();

        // 다시 불러도(가입 흐름이 두 번 돌아도) 새로 주지 않는다 — 상점의 받기도 같은 결과(멱등)
        assertThat(kit.acquire.grantSignupPacks(member)).isZero();
        assertThat(kit.inTx(() -> kit.acquire.acquire(member, Long.toString(packs.get(0)))).result().acquireCount()).isEqualTo(before.get(packs.get(0)) + 1);
        packs.forEach(p -> assertThat(kit.acquireCount(p)).isEqualTo(before.get(p) + 1));
        assertThat(kit.count("SELECT count(*) FROM emoticon_acquisitions WHERE user_id = ?", member)).isEqualTo(packs.size());
    }

    @Test
    void 스스로_뺀_팩은_다시_불러도_되살리지_않고_상점_받기로는_다시_넣는다() {
        List<Long> packs = officialFree();
        long member = kit.member();
        kit.acquire.grantSignupPacks(member);
        long removed = packs.get(0);
        kit.inTx(() -> kit.acquire.remove(member, Long.toString(removed)));
        int count = kit.acquireCount(removed);

        assertThat(kit.acquire.grantSignupPacks(member)).isZero();
        assertThat(mine(member)).doesNotContain(Long.toString(removed));

        assertThat(kit.inTx(() -> kit.acquire.acquire(member, Long.toString(removed))).result().acquired()).isTrue();
        assertThat(mine(member)).contains(Long.toString(removed));
        assertThat(kit.acquireCount(removed)).as("다시 넣어도 받은 수는 처음 한 번만").isEqualTo(count);
    }

    @Test
    void 작가_무료_팩은_가입_때_주지_않는다() {
        long creator = kit.member();
        long creatorPack = kit.approvedPack(creator);
        long member = kit.member();

        kit.acquire.grantSignupPacks(member);

        assertThat(mine(member)).doesNotContain(Long.toString(creatorPack));
        assertThat(kit.acquireCount(creatorPack)).isZero();
        assertThat(kit.rewards(creator)).isZero();
    }

    @Test
    void 운영자_회원_계정은_받기_줄을_만들지_않는다() {
        long member = kit.member();
        kit.jdbc.update("INSERT INTO operator_member_accounts (operator_id, user_id, linked_at) VALUES (?, ?, now())", kit.newOperator(), member);
        Map<Long, Integer> before = counts(officialFree());

        assertThat(kit.acquire.grantSignupPacks(member)).isZero();

        assertThat(kit.count("SELECT count(*) FROM emoticon_acquisitions WHERE user_id = ?", member)).isZero();
        before.forEach((p, c) -> assertThat(kit.acquireCount(p)).isEqualTo(c));
    }
}
