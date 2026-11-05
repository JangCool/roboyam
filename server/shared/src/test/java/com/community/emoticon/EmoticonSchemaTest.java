package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.emoticon.service.EmoticonImageValidator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * V140 의 표·제약·트리거·시드(emoticon-contract S1) — 실제 PostgreSQL. 낸 판 고치기 금지 · 상점 판은 그 팩의 판 · 팩마다 대기 건 하나 · 무료만 · 고정 규격 CHECK · 판의 로보티콘은 그 팩의 것 ·
 * 운영 팩 시드(로보얌콘 남·여 32개씩 — 이름표는 robotikon/meta.json, 그림 정보는 두 앱 public 의 파일과 같다).
 */
class EmoticonSchemaTest extends EmoticonTestBase {

    /** robotikon/meta.json items 의 이름표(alt) 차례 */
    private static final List<String> ALTS = List.of("기쁨", "슬픔", "우울", "분노", "짜증", "헉", "무서", "윽", "당황", "부끄", "사랑", "두근", "신남", "평온", "심심", "졸려", "안녕",
            "감사", "굿", "미안", "파이팅", "짝짝", "싫어", "뭐지", "로딩", "에러", "과열", "방전", "충전", "업뎃", "재부팅", "해킹");

    private long officialPack(String name) {
        return kit.jdbc.queryForObject("SELECT p.id FROM emoticon_packs p JOIN emoticon_pack_revisions r ON r.id = p.live_revision_id WHERE p.source = 'OFFICIAL' AND r.name = ? "
                + "ORDER BY p.id LIMIT 1", Long.class, name);
    }

    @Test
    void 시드_로보얌콘_남_여_32개씩_무료_판매_중_이름표는_meta_json_그림은_앱_정적_파일과_같다() {
        for (String[] pack : new String[][] {{"로보얌콘", "m"}, {"로보얌콘핑크", "f"}}) {
            long id = officialPack(pack[0]);
            Map<String, Object> row = kit.jdbc.queryForMap("SELECT status, price, creator_id, published_at FROM emoticon_packs WHERE id = ?", id);
            assertThat(row.get("status")).isEqualTo("APPROVED");
            assertThat(row.get("price")).isEqualTo(0);
            assertThat(row.get("creator_id")).isNull();
            assertThat(row.get("published_at")).isNotNull();
            List<Map<String, Object>> items = kit.jdbc.queryForList("SELECT i.position, i.label, m.static_path, m.content_hash, m.bytes, m.frames, m.loop_forever, m.format "
                    + "FROM emoticon_packs p JOIN emoticon_revision_items i ON i.revision_id = p.live_revision_id JOIN emoticon_images m ON m.id = i.image_id WHERE p.id = ? "
                    + "ORDER BY i.position", id);
            assertThat(items).hasSize(32);
            for (int n = 1; n <= 32; n++) {
                Map<String, Object> item = items.get(n - 1);
                String file = "robotikon-" + pack[1] + "/" + String.format("%02d", n) + ".gif";
                byte[] bytes = TestImages.robotikon(file);
                assertThat(item.get("label")).as(file).isEqualTo(ALTS.get(n - 1));
                assertThat(item.get("static_path")).isEqualTo("/emoticons/" + file);
                assertThat(item.get("content_hash")).as(file).isEqualTo(EmoticonImageValidator.sha256(bytes));
                assertThat(item.get("bytes")).isEqualTo(bytes.length);
                assertThat(item.get("format")).isEqualTo("GIF");
                assertThat(item.get("loop_forever")).isEqualTo(true);
            }
            Map<String, Object> cover = kit.jdbc.queryForMap("SELECT m.static_path, m.content_hash, m.format FROM emoticon_packs p JOIN emoticon_pack_revisions r "
                    + "ON r.id = p.live_revision_id JOIN emoticon_images m ON m.id = r.cover_image_id WHERE p.id = ?", id);
            assertThat(cover.get("static_path")).isEqualTo("/emoticons/robotikon-" + pack[1] + "/cover.png");
            assertThat(cover.get("format")).isEqualTo("PNG");
            assertThat(cover.get("content_hash")).isEqualTo(EmoticonImageValidator.sha256(TestImages.robotikon("robotikon-" + pack[1] + "/cover.png")));
            // 토큰 ID(로보티콘 ID)는 팩마다 겹치지 않는 32개
            assertThat(kit.liveEmoticons(id)).doesNotHaveDuplicates().hasSize(32);
        }
    }

    @Test
    void 운영_팩_가격은_늘_0_유료_판매가_켜져_있고_열린_단계여도() {
        long id = officialPack("로보얌콘");
        // 유료 판매가 켜져 있고 1,500 이 열린 단계여도 운영 팩은 CHECK 가 막는다(S2 — 작가 팩의 유료 가격은 EmoticonPaidTest)
        kit.withPaid(() -> assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_packs SET price = 1500 WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class));
    }

    @Test
    void 고정_규격은_CHECK_로도_막는다_200x200_24프레임_대표는_PNG_1MB() {
        String insert = "INSERT INTO emoticon_images (id, kind, format, width, height, bytes, frames, loop_forever, content_hash, static_path, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, true, repeat('a', 64), '/x.gif', now())";
        assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), "EMOTICON", "GIF", 220, 220, 100, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), "EMOTICON", "GIF", 200, 200, 100, 25)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), "EMOTICON", "GIF", 200, 200, 2 * 1024 * 1024 + 1, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), "COVER", "GIF", 200, 200, 100, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), "COVER", "PNG", 200, 200, 1024 * 1024 + 1, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), "EMOTICON", "PNG", 200, 200, 100, 2)).isInstanceOf(DataIntegrityViolationException.class);
        kit.jdbc.update(insert, kit.ids.nextId(), "EMOTICON", "GIF", 200, 200, 2 * 1024 * 1024, 24); // 경계는 받는다
    }

    @Test
    void 낸_판은_고치거나_지우거나_차례를_더하지_못한다_임시_판만_고친다() {
        long creator = kit.member();
        long packId = Long.parseLong(kit.submitNew(creator, "판 잠금 시험").id());
        long sealed = kit.jdbc.queryForObject("SELECT id FROM emoticon_pack_revisions WHERE pack_id = ? AND version = 1", Long.class, packId);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_pack_revisions SET name = '바꾼 이름' WHERE id = ?", sealed)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("sealed");
        assertThatThrownBy(() -> kit.jdbc.update("DELETE FROM emoticon_pack_revisions WHERE id = ?", sealed)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_revision_items SET label = '바꿈' WHERE revision_id = ?", sealed)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("sealed");
        assertThatThrownBy(() -> kit.jdbc.update("DELETE FROM emoticon_revision_items WHERE revision_id = ?", sealed)).isInstanceOf(DataAccessException.class);
        long emoticon = kit.jdbc.queryForObject("SELECT emoticon_id FROM emoticon_revision_items WHERE revision_id = ? AND position = 1", Long.class, sealed);
        long image = kit.jdbc.queryForObject("SELECT image_id FROM emoticon_revision_items WHERE revision_id = ? AND position = 1", Long.class, sealed);
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO emoticon_revision_items (revision_id, position, pack_id, emoticon_id, image_id, label) VALUES (?, 60, ?, ?, ?, 'x')",
                sealed, packId, emoticon + 0, image)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void 팩마다_대기_심사_건은_하나_판의_로보티콘은_그_팩의_것_상점_판은_그_팩의_판() {
        long creator = kit.member();
        long a = Long.parseLong(kit.submitNew(creator, "제약 시험 가").id());
        long b = Long.parseLong(kit.submitNew(creator, "제약 시험 나").id());
        long revisionA = kit.jdbc.queryForObject("SELECT revision_id FROM emoticon_reviews WHERE pack_id = ?", Long.class, a);
        long revisionB = kit.jdbc.queryForObject("SELECT revision_id FROM emoticon_reviews WHERE pack_id = ?", Long.class, b);
        // 같은 팩에 대기 건을 하나 더 — 부분 유일 색인
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO emoticon_reviews (id, pack_id, revision_id, creator_id, kind, status, submitted_at, agree_ownership_at, "
                + "agree_license_at, agree_policy_at, consent_version) VALUES (?, ?, ?, ?, 'FIRST', 'PENDING', now(), now(), now(), now(), 'v')", kit.ids.nextId(), a, revisionB,
                creator)).isInstanceOf(DataIntegrityViolationException.class);
        // 다른 팩의 로보티콘을 차례에 끌어오지 못한다
        long foreign = kit.liveEmoticonsOrSubmitted(b).get(0);
        long draft = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO emoticon_pack_revisions (id, pack_id, created_at) VALUES (?, ?, now())", draft, a);
        long image = kit.jdbc.queryForObject("SELECT image_id FROM emoticon_revision_items WHERE revision_id = ? AND position = 1", Long.class, revisionA);
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO emoticon_revision_items (revision_id, position, pack_id, emoticon_id, image_id) VALUES (?, 1, ?, ?, ?)", draft, a,
                foreign, image)).isInstanceOf(DataIntegrityViolationException.class);
        kit.jdbc.update("DELETE FROM emoticon_pack_revisions WHERE id = ?", draft); // 임시 판은 지울 수 있다
        // 상점 판을 다른 팩의 판으로 — 커밋 때 외래 키가 막는다
        assertThatThrownBy(() -> kit.inTx(() -> {
            kit.jdbc.update("UPDATE emoticon_packs SET status = 'APPROVED', live_revision_id = ?, published_at = now() WHERE id = ?", revisionB, a);
        })).isInstanceOf(RuntimeException.class).hasStackTraceContaining("fk_emoticon_packs_live");
        assertThat(kit.status(a)).isEqualTo("PENDING");
    }

    @Test
    void 받기는_회원과_팩_하나에_한_줄() {
        long member = kit.member();
        long pack = officialPack("로보얌콘");
        kit.jdbc.update("INSERT INTO emoticon_acquisitions (id, user_id, pack_id, first_acquired_at, acquired_at) VALUES (?, ?, ?, now(), now())", kit.ids.nextId(), member, pack);
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO emoticon_acquisitions (id, user_id, pack_id, first_acquired_at, acquired_at) VALUES (?, ?, ?, now(), now())",
                kit.ids.nextId(), member, pack)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
