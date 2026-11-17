package com.community.installation;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.CommerceApiTestBase;
import com.community.installation.dto.TablePack;
import com.community.installation.service.BoardPresetCatalog;
import com.community.installation.service.TablePackRestorer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 설치 프리셋 구조(irobit.structure.json)의 레벨 곡선 값(grade-level-contract 6절) — 프리셋은 등급·등급 설정·닉네임 이미지 조건 행을 덮어쓰므로 새 기본값을 담아야 한다:
 * 서열별 최소 레벨 1·5·10·20·30·40·50·60·75·90·100 · 곡선 지수 2.0({@code xp_curve_exponent} — 글자 "2.0" 을 칸 형식 numeric(2,1) 로 읽는다) · 닉네임 이미지 조건 = 서열 5 등급(정예).
 * 그 세 표를 실제로 넣어 본다(실제 PostgreSQL, 칸이 맞지 않으면 실패하는 엄격 방식). 끝나면 값을 그대로 둔다 — 프리셋 값이 곧 V245 뒤의 기본값이다.
 */
class GradeCurvePresetTest extends CommerceApiTestBase {

    @Autowired
    private BoardPresetCatalog presets;
    @Autowired
    private TablePackRestorer restorer;

    private static TablePack.Table table(TablePack pack, String name) {
        return pack.tables().stream().filter(t -> t.table().equals(name)).findFirst().orElseThrow();
    }

    @AfterEach
    void restoreDefaults() {
        // 시험이 중간에 실패해도 다른 시험이 기본값을 보게
        jdbc.update("UPDATE grade_settings SET xp_curve_exponent = 2.0 WHERE id = 1");
        jdbc.update("UPDATE grades g SET min_level = n.min_level FROM (VALUES (1, 1), (2, 5), (3, 10), (4, 20), (5, 30), (6, 40), (7, 50), (8, 60), (9, 75), (10, 90), (11, 100)) "
                + "AS n(level, min_level) WHERE g.level = n.level");
        jdbc.update("UPDATE nickname_image_settings SET mode = 'GRADE', grade_key = (SELECT grade_key FROM grades WHERE level = 5), require_email_verified = true WHERE id = 1");
    }

    @Test
    void 설치_구조는_새_최소_레벨과_곡선_지수와_닉네임_이미지_정예를_담고_그대로_들어간다() {
        TablePack structure = presets.structureOf("IROBIT").orElseThrow();
        TablePack.Table grades = table(structure, "grades");
        TablePack.Table settings = table(structure, "grade_settings");
        TablePack.Table nickname = table(structure, "nickname_image_settings");

        // 원고
        assertThat(grades.rows().stream().sorted((a, b) -> Integer.compare(((Number) a.get("level")).intValue(), ((Number) b.get("level")).intValue()))
                .map(r -> ((Number) r.get("min_level")).intValue()).toList()).containsExactly(1, 5, 10, 20, 30, 40, 50, 60, 75, 90, 100);
        String elite = grades.rows().stream().filter(r -> ((Number) r.get("level")).intValue() == 5).map(r -> (String) r.get("grade_key")).findFirst().orElseThrow();
        assertThat(elite).isEqualTo("GRADE_7");
        assertThat(settings.columns()).containsEntry("xp_curve_exponent", "numeric(2,1)");
        assertThat(settings.rows()).singleElement().satisfies(r -> assertThat(r).containsEntry("xp_curve_base", 10).containsEntry("xp_curve_exponent", "2.0"));
        assertThat(nickname.rows()).singleElement().satisfies(r -> assertThat(r).containsEntry("mode", "GRADE").containsEntry("grade_key", elite));

        // 옛 값으로 흩어 놓고 넣어 본다
        jdbc.update("UPDATE grade_settings SET xp_curve_exponent = 1.5 WHERE id = 1");
        jdbc.update("UPDATE grades SET min_level = 10 WHERE level = 2");
        jdbc.update("UPDATE nickname_image_settings SET grade_key = 'VETERAN' WHERE id = 1");
        TablePack three = new TablePack(structure.format(), structure.preset(), structure.exportedAt(), Map.of(), List.of(grades, settings, nickname));

        tx.executeWithoutResult(status -> restorer.restore(three, true, false, (t, r, columns) -> r));

        assertThat(jdbc.queryForObject("SELECT xp_curve_exponent FROM grade_settings WHERE id = 1", BigDecimal.class)).isEqualByComparingTo("2.0");
        assertThat(jdbc.queryForList("SELECT min_level FROM grades ORDER BY level", Integer.class)).containsExactly(1, 5, 10, 20, 30, 40, 50, 60, 75, 90, 100);
        assertThat(jdbc.queryForObject("SELECT grade_key FROM nickname_image_settings WHERE id = 1", String.class)).isEqualTo(elite);
        assertThat(jdbc.queryForObject("SELECT grade_key FROM grades WHERE level = 5", String.class)).isEqualTo(elite);
    }
}
