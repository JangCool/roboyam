package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.emoticon.dto.response.EmoticonPublicView;
import com.community.emoticon.exception.EmoticonNotOwnedException;
import com.community.emoticon.service.EmoticonGuard;
import com.community.emoticon.service.EmoticonShopService;
import com.community.zconfiguration.message.MessageRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 카탈로그·상점·토큰 검사(emoticon-contract S1 · 「목업 5차」) — 실제 PostgreSQL.
 * 카탈로그 = 상점에 나왔던 팩(판매 중단 포함)의 상점 판 · 내용 지문 ETag · 상점 목록(판매 중만 · 거르기 · 정렬 · 찾기 · 키셋 쪽) ·
 * {@code EmoticonGuard}(본문 전체 글자에서 세기 · 21개부터 400 · 그다음 본문 순서로 가지지 않은 팩 409 · 없는 ID·long 범위 밖은 글자 · 운영진 면제 없음).
 */
class EmoticonCatalogGuardTest extends EmoticonTestBase {

    private long official(String name) {
        return kit.jdbc.queryForObject("SELECT p.id FROM emoticon_packs p JOIN emoticon_pack_revisions r ON r.id = p.live_revision_id WHERE p.source = 'OFFICIAL' AND r.name = ? "
                + "ORDER BY p.id LIMIT 1", Long.class, name);
    }

    private static String tokens(List<Long> ids) {
        StringBuilder out = new StringBuilder();
        for (Long id : ids) {
            out.append(":emo-").append(id).append(": ");
        }
        return out.toString();
    }

    // ── 카탈로그 ────────────────────────────────────────────────────────────

    @Test
    void 카탈로그는_판매_중과_판매_중단_팩의_상점_판_로보티콘_임시_심사_중_반려_팩은_없다() {
        long creator = kit.member();
        long onSale = kit.approvedPack(creator);
        long suspended = kit.approvedPack(creator);
        kit.inTx(() -> kit.packs.suspend(kit.operator, null, Long.toString(suspended), "확인하는 동안 멈춰요"));
        long pending = Long.parseLong(kit.submitNew(creator, "심사 중").id());
        List<EmoticonPublicView.CatalogItem> items = kit.shop.catalog().catalog().items();
        assertThat(items).filteredOn(i -> i.packId().equals(Long.toString(onSale))).hasSize(8);
        assertThat(items).filteredOn(i -> i.packId().equals(Long.toString(suspended))).hasSize(8);
        assertThat(items).noneMatch(i -> i.packId().equals(Long.toString(pending)));
        EmoticonPublicView.CatalogItem robot = items.stream().filter(i -> i.packId().equals(Long.toString(official("아이로빗콘")))).findFirst().orElseThrow();
        assertThat(robot.url()).isEqualTo("/emoticons/robotikon-m/01.gif");
        assertThat(robot.label()).isEqualTo("기쁨");
        assertThat(robot.animated()).isTrue();
        // 올린 그림은 저장소 주소
        EmoticonPublicView.CatalogItem uploaded = items.stream().filter(i -> i.packId().equals(Long.toString(onSale))).findFirst().orElseThrow();
        assertThat(uploaded.url()).startsWith("/media/emoticons/").endsWith(".gif");
    }

    @Test
    void ETag_는_내용의_지문_같은_내용이면_같고_판이_바뀌거나_팩이_나오면_달라진다() {
        String first = kit.shop.catalog().etag();
        assertThat(first).matches("^[0-9a-f]{32}$");
        assertThat(kit.shop.catalog().etag()).isEqualTo(first);
        long creator = kit.member();
        long pack = kit.approvedPack(creator);
        String afterNew = kit.shop.catalog().etag();
        assertThat(afterNew).isNotEqualTo(first);
        // 판매 중단은 카탈로그 내용을 바꾸지 않는다(가진 사람이 계속 그린다)
        kit.inTx(() -> kit.packs.suspend(kit.operator, null, Long.toString(pack), "확인하는 동안 멈춰요"));
        assertThat(kit.shop.catalog().etag()).isEqualTo(afterNew);
    }

    // ── 상점 ────────────────────────────────────────────────────────────────

    @Test
    void 상점_목록은_판매_중만_찾기_정렬_키셋_쪽() {
        String tag = "찾기" + Long.toString(kit.ids.nextId(), 36).substring(4, 9);
        List<Long> made = new ArrayList<>();
        for (int c = 0; c < 6; c++) {
            long creator = kit.member();
            for (int i = 0; i < 5; i++) {
                var body = EmoticonTestKit.body("쪽 시험 " + c + i, kit.uploadCover(creator), kit.uploads(creator, 8));
                var tagged = new com.community.emoticon.dto.request.EmoticonStudioSaveRequest(body.name(), body.description(), List.of(tag), body.coverImageId(),
                        body.emoticons(), "FREE", null, true, true, true, null);
                long id = Long.parseLong(kit.inTx(() -> kit.studio.submit(creator, null, tagged)).id());
                kit.approve(id);
                made.add(id);
                kit.clock.advance(java.time.Duration.ofSeconds(1));
            }
        }
        long suspended = made.get(0);
        kit.inTx(() -> kit.packs.suspend(kit.operator, null, Long.toString(suspended), "확인하는 동안 멈춰요"));
        // 인기순 — 받은 수가 많은 것 먼저
        long popular = made.get(10);
        kit.inTx(() -> kit.acquire.acquire(kit.member(), Long.toString(popular)));
        EmoticonPublicView.PackList page1 = kit.shop.packs(null, "CREATOR", "FREE", "POPULAR", tag, null);
        assertThat(page1.items()).hasSize(EmoticonShopService.PAGE_SIZE);
        assertThat(page1.items().get(0).id()).isEqualTo(Long.toString(popular));
        assertThat(page1.nextCursor()).isNotNull();
        EmoticonPublicView.PackList page2 = kit.shop.packs(null, "CREATOR", "FREE", "POPULAR", tag, page1.nextCursor());
        assertThat(page2.nextCursor()).isNull();
        List<String> all = new ArrayList<>();
        page1.items().forEach(p -> all.add(p.id()));
        page2.items().forEach(p -> all.add(p.id()));
        assertThat(page2.items()).hasSize(5);
        assertThat(all).hasSize(29).doesNotHaveDuplicates().doesNotContain(Long.toString(suspended));
        // 최신순 — 처음 나온 시각이 늦은 것 먼저
        EmoticonPublicView.PackList latest = kit.shop.packs(null, null, null, "LATEST", tag, null);
        assertThat(latest.items().get(0).id()).isEqualTo(Long.toString(made.get(made.size() - 1)));
        // 운영 팩 거르기·유료 거르기에는 없다 · 작가 닉네임으로도 찾는다
        assertThat(kit.shop.packs(null, "OFFICIAL", null, null, tag, null).items()).isEmpty();
        assertThat(kit.shop.packs(null, null, "PAID", null, tag, null).items()).isEmpty();
        String nickname = kit.jdbc.queryForObject("SELECT nickname FROM profiles WHERE user_id = (SELECT creator_id FROM emoticon_packs WHERE id = ?)", String.class, popular);
        assertThat(kit.shop.packs(null, null, null, null, nickname, null).items()).extracting(EmoticonPublicView.PackCard::id).contains(Long.toString(popular));
        assertThat(kit.shop.packs(null, null, null, null, "쪽 시험 21", null).items()).extracting(EmoticonPublicView.PackCard::id).contains(Long.toString(made.get(11)));
    }

    @Test
    void 운영_팩_카드와_상세() {
        long id = official("아이로빗콘핑크");
        EmoticonPublicView.PackDetail detail = kit.shop.pack(null, Long.toString(id));
        assertThat(detail.source()).isEqualTo("OFFICIAL");
        assertThat(detail.creator()).isNull();
        assertThat(detail.count()).isEqualTo(32);
        assertThat(detail.coverUrl()).isEqualTo("/emoticons/robotikon-f/cover.png");
        assertThat(detail.tags()).containsExactly("로봇", "아이로빗", "핑크", "리액션", "귀여움");
        assertThat(detail.saleState()).isEqualTo("ON_SALE");
        assertThat(detail.emoticons().get(18).label()).isEqualTo("굿");
        assertThat(kit.shop.packs(null, "OFFICIAL", null, null, "아이로빗콘핑크", null).items()).extracting(EmoticonPublicView.PackCard::id).contains(Long.toString(id));
    }

    // ── 토큰 검사 ───────────────────────────────────────────────────────────

    @Test
    void 개수는_본문_전체_글자에서_센다_같은_토큰_없는_ID_범위_밖_ID_HTML_속성_안도() {
        assertThat(EmoticonGuard.count(":emo-1::emo-1: :emo-99999999999999999999: <a href=\":emo-5:\">x</a> <code>:emo-6:</code>")).isEqualTo(4);
        assertThat(EmoticonGuard.count(":emo-: :emo-a: emo-1:")).isZero();
        assertThat(EmoticonGuard.ids(":emo-9223372036854775807: :emo-9223372036854775808: :emo-0000000000000000001:")).containsExactly(Long.MAX_VALUE, 1L);
        String twenty = ":emo-1:".repeat(20);
        Map<String, MessageRef> ok = new LinkedHashMap<>();
        EmoticonGuard.countInto(twenty, "content", ok);
        assertThat(ok).isEmpty();
        Map<String, MessageRef> over = new LinkedHashMap<>();
        EmoticonGuard.countInto(twenty + ":emo-123:", "content", over);
        assertThat(over.get("content").key()).isEqualTo("emoticon.body.tooMany");
        assertThat(over.get("content").args()).containsExactly(20);
        // 그 칸에 다른 오류가 이미 있으면 그 오류가 남는다
        Map<String, MessageRef> earlier = new LinkedHashMap<>();
        earlier.put("content", new MessageRef("board.content.body.length"));
        EmoticonGuard.countInto(twenty + ":emo-1:", "content", earlier);
        assertThat(earlier.get("content").key()).isEqualTo("board.content.body.length");
    }

    @Test
    void 가지지_않은_팩의_로보티콘은_409_처음_걸린_ID_가진_팩은_통과_없는_ID_는_글자() {
        long pink = official("아이로빗콘핑크");
        long robot = official("아이로빗콘");
        List<Long> pinkIds = kit.liveEmoticons(pink);
        List<Long> robotIds = kit.liveEmoticons(robot);
        long member = kit.member();
        kit.inTx(() -> kit.acquire.acquire(member, Long.toString(robot)));
        // 가진 팩 · 카탈로그에 없는 ID · 범위 밖 ID 는 통과
        kit.guard.requireOwned(member, tokens(List.of(robotIds.get(0), robotIds.get(5))) + ":emo-1: :emo-99999999999999999999:");
        // 본문 순서로 처음 걸린 것
        String body = "<p>" + tokens(List.of(robotIds.get(0), pinkIds.get(3), pinkIds.get(1))) + "</p>";
        assertThatThrownBy(() -> kit.guard.requireOwned(member, body)).isInstanceOfSatisfying(EmoticonNotOwnedException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo("EMOTICON_NOT_OWNED");
            assertThat(e.getStatus().value()).isEqualTo(409);
            assertThat(e.getExtras()).containsEntry("emoticonId", Long.toString(pinkIds.get(3)));
        });
        // 비회원은 아무 팩도 가지지 않았다
        assertThatThrownBy(() -> kit.guard.requireOwned(null, tokens(List.of(robotIds.get(0))))).isInstanceOf(EmoticonNotOwnedException.class);
        // 목록에서 빼면 다시 쓸 수 없다
        kit.inTx(() -> kit.acquire.remove(member, Long.toString(robot)));
        assertThatThrownBy(() -> kit.guard.requireOwned(member, tokens(List.of(robotIds.get(0))))).isInstanceOf(EmoticonNotOwnedException.class);
    }

    @Test
    void 판매_중단_팩의_로보티콘은_가진_사람은_쓰고_아닌_사람은_409_심사_중_팩의_ID_는_글자() {
        long creator = kit.member();
        long pack = kit.approvedPack(creator);
        long owner = kit.member();
        kit.inTx(() -> kit.acquire.acquire(owner, Long.toString(pack)));
        kit.inTx(() -> kit.packs.suspend(kit.operator, null, Long.toString(pack), "확인하는 동안 멈춰요"));
        String body = tokens(kit.liveEmoticons(pack).subList(0, 2));
        kit.guard.requireOwned(owner, body);
        assertThatThrownBy(() -> kit.guard.requireOwned(kit.member(), body)).isInstanceOf(EmoticonNotOwnedException.class);
        // 작가 본인은 상점에 나온 자기 팩을 사지 않아도 가진다(후속 결정 1, 2026-11-05 — 판매 중단 뒤에도). 운영진 면제는 여전히 없다
        kit.guard.requireOwned(creator, body);
        long pending = Long.parseLong(kit.submitNew(creator, "심사 중 토큰").id());
        kit.guard.requireOwned(kit.member(), tokens(kit.liveEmoticonsOrSubmitted(pending)));
    }

    @Test
    void 빈_본문_토큰_없는_본문은_검사할_것이_없다() {
        kit.guard.requireOwned(null, null);
        kit.guard.requireOwned(null, "");
        kit.guard.requireOwned(null, "그냥 글자 :emo: emo-12");
        assertThat(EmoticonGuard.count(null)).isZero();
    }
}
