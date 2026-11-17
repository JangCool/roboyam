package com.community.installation;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.board.service.BoardCatalog;
import com.community.commerce.CommerceApiTestBase;
import com.community.installation.dto.TablePack;
import com.community.installation.service.BoardPresetCatalog;
import com.community.installation.service.TablePackRestorer;
import com.community.trial.service.TrialPostLinker;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 설치 프리셋 구조(irobit.structure.json)의 사용기 게시판(slug review)에 공통 말머리 TRIAL(「체험단」)이 있다 — 설치 직후부터 체험단 말머리로 거를 수 있게. 서버가 첫 체험단 사용기 때
 * 말머리를 만드는 코드({@link TrialPostLinker#ensureTrialPrefix})는 이미 있는 줄을 그대로 쓴다(두 번째 TRIAL 줄을 만들지 않는다).
 */
class TrialPrefixPresetTest extends CommerceApiTestBase {

    @Autowired
    private BoardPresetCatalog presets;
    @Autowired
    private TablePackRestorer restorer;
    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private TrialPostLinker linker;

    private static TablePack.Table table(TablePack pack, String name) {
        return pack.tables().stream().filter(t -> t.table().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void 설치_구조의_사용기_게시판에_공통_체험단_말머리가_있고_첫_체험단_사용기는_그_줄을_쓴다() {
        TablePack structure = presets.structureOf("IROBIT").orElseThrow();
        Map<String, Object> review = table(structure, "boards").rows().stream().filter(r -> "review".equals(r.get("slug"))).findFirst().orElseThrow();
        TablePack.Table prefixes = table(structure, "board_prefixes");
        List<Map<String, Object>> trial = prefixes.rows().stream().filter(r -> "TRIAL".equals(r.get("common_key"))).toList();
        assertThat(trial).singleElement().satisfies(r -> {
            assertThat(r.get("board_id").toString()).isEqualTo(review.get("id").toString());
            assertThat(r).containsEntry("name", "체험단").containsEntry("staff_only", false).containsEntry("active", true).containsEntry("adult_only", false);
        });
        // 구조 안에서 말머리 ID 는 하나씩
        assertThat(prefixes.rows().stream().map(r -> r.get("id").toString()).distinct().count()).isEqualTo(prefixes.rows().size());

        // 구조의 그 줄을 그대로(게시판만 시험 게시판으로) 넣는다 — DB 제약(공통 말머리 키·유일 색인)을 통과하고, 첫 체험단 사용기 말머리 찾기가 그 줄을 쓴다
        long board = ids.nextId();
        String slug = "tp" + Long.toString(board, 36);
        boardFixture.board(board, slug, "말머리 " + slug.substring(slug.length() - 6), 991);
        long prefixId = ids.nextId();
        Map<String, Object> row = new LinkedHashMap<>(trial.getFirst());
        row.put("id", Long.toString(prefixId));
        row.put("board_id", Long.toString(board));
        TablePack one = new TablePack(1, "IROBIT", Instant.now().toString(), Map.of(),
                List.of(new TablePack.Table("board_prefixes", TablePack.Mode.UPSERT.name(), List.of("id"), prefixes.columns(), List.of(row), List.of())));
        tx.executeWithoutResult(status -> restorer.restore(one, true, false, (t, r, columns) -> r));
        catalog.invalidate();

        long used = linker.ensureTrialPrefix(catalog.snapshot().board(board).orElseThrow());
        assertThat(used).isEqualTo(prefixId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM board_prefixes WHERE board_id = ? AND common_key = 'TRIAL'", Integer.class, board)).isEqualTo(1);
    }
}
