package com.community.installation;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.board.service.OpsBoardPreset;
import com.community.board.service.OpsBoardSeeder;
import com.community.commerce.CommerceApiTestBase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

/**
 * 운영 게시판 시드(V168)의 두 경로 — 이미 설치된 DB 는 마이그레이션 SQL 블록이, 새 설치는 프리셋 적용의 {@link OpsBoardSeeder} 가 만든다 — 가 같은 결과인지,
 * SQL 은 이미 설치된 DB 에서만 넣고 두 번 돌아도 그대로인지 확인한다. 시험 DB 는 설치 전 상태라 마이그레이션 때 아무것도 넣지 않았다 —
 * 설치 행을 잠깐 「설치됨」으로 바꿔 같은 블록(파일의 {@code -- seed:begin} ~ {@code -- seed:end})을 다시 부르고, 끝나면 되돌린다.
 */
class OpsBoardSeedTest extends CommerceApiTestBase {

    private static final String MIGRATION = "db/migration/shared/V168__post_status_history_and_ops_board_seed.sql";

    @Autowired
    private OpsBoardSeeder seeder;

    private Map<String, Object> savedInstallation;
    private boolean insertedInstallation;
    private long root;

    @BeforeEach
    void setUp() {
        removeSeed();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT status, installed_at, root_operator_id, board_preset_applied_at FROM installation WHERE id = 1");
        insertedInstallation = rows.isEmpty();
        if (insertedInstallation) {
            jdbc.update("INSERT INTO installation (id, status) VALUES (1, 'PENDING')");
            savedInstallation = Map.of("status", "PENDING");
        } else {
            savedInstallation = new LinkedHashMap<>(rows.getFirst());
        }
        root = newOperator();
    }

    @AfterEach
    void restore() {
        removeSeed();
        if (insertedInstallation) {
            jdbc.update("DELETE FROM installation WHERE id = 1");
        } else {
            jdbc.update("UPDATE installation SET status = ?, installed_at = ?, root_operator_id = ?, board_preset_applied_at = ? WHERE id = 1",
                    savedInstallation.get("status"), savedInstallation.get("installed_at"), savedInstallation.get("root_operator_id"),
                    savedInstallation.get("board_preset_applied_at"));
        }
    }

    // ── 재료 ───────────────────────────────────────────────────────────────

    private void removeSeed() {
        jdbc.update("DELETE FROM menu_sets WHERE set_key = ?", OpsBoardPreset.FOOTER_SET);
        for (OpsBoardPreset.Board b : OpsBoardPreset.BOARDS) {
            jdbc.update("DELETE FROM menu_nodes WHERE target_type = 'BOARD' AND target_id IN (SELECT id::text FROM boards WHERE slug = ?)", b.slug());
            jdbc.update("DELETE FROM boards WHERE slug = ?", b.slug());
        }
    }

    private void installed(boolean presetApplied) {
        jdbc.update("UPDATE installation SET status = 'COMPLETED', installed_at = now(), root_operator_id = ?, board_preset_applied_at = ? WHERE id = 1", root,
                presetApplied ? ts(Instant.now()) : null);
    }

    /** 마이그레이션 파일의 시드 블록(DO 문 하나)을 그대로 부른다 */
    private void runMigrationSeed() throws IOException {
        String sql = new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8);
        int begin = sql.indexOf("-- seed:begin");
        int end = sql.indexOf("-- seed:end");
        assertThat(begin).isPositive();
        assertThat(end).isGreaterThan(begin);
        String block = sql.substring(sql.indexOf('\n', begin) + 1, end).strip();
        jdbc.execute(block);
    }

    private int boardCount() {
        return jdbc.queryForObject("SELECT count(*) FROM boards WHERE slug IN ('bugs', 'requests')", Integer.class);
    }

    /** 두 경로를 견줄 값 — ID·시각·순서 번호(지금 있는 마지막 다음)를 뺀 모든 칸 */
    private Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (OpsBoardPreset.Board b : OpsBoardPreset.BOARDS) {
            Map<String, Object> board = jdbc.queryForMap("SELECT slug, name, name_en, description, kind, layout, view_toggle, color, allow_adult, investment_notice, "
                    + "active, write_lock, intro_text, write_guide_text, write_template, write_min, comment_min, post_editor, image_upload_allowed, comment_editor, "
                    + "list_size, pinned_max, indexable, searchable, in_home, point_mode, point_min_length_applied, comment_protect_min, comment_protect_edit, "
                    + "comment_protect_delete, comment_protect_delay_minutes, comment_guest_delay_minutes, staff_hidden_notice, comment_dislike_enabled, "
                    + "comment_like_label, comment_dislike_label, pin_request_reviewers::text AS reviewers, debate_enabled, poll_enabled, manager_max, "
                    + "staff_post_daily_limit, staff_comment_daily_limit, staff_sanction_daily_limit, gadget_exclusions::text AS gadgets, ads_hidden, secret_mode, "
                    + "status_enabled, created_by, updated_by FROM boards WHERE slug = ?", b.slug());
            out.put(b.slug(), board);
            out.put(b.slug() + ".prefixes", jdbc.queryForList("SELECT p.common_key, p.name, p.staff_only, p.color, p.active, p.sort_order, p.adult_only "
                    + "FROM board_prefixes p JOIN boards b ON b.id = p.board_id WHERE b.slug = ? ORDER BY p.sort_order, p.id", b.slug()));
        }
        out.put("footer", jdbc.queryForMap("SELECT set_key, name, name_en, placement, enabled, description, is_system, created_by FROM menu_sets WHERE set_key = ?",
                OpsBoardPreset.FOOTER_SET));
        out.put("footer.nodes", jdbc.queryForList("SELECT n.parent_id, n.kind, n.sort_order, n.label, n.name_en, n.visible, n.audience, n.min_grade, n.collapsible, "
                + "n.collapsed, n.auto_add, n.target_type, b.slug AS target_slug, n.target_key, n.settings::text AS settings, n.mobile_tab, n.divider, n.description, "
                + "n.link_click FROM menu_nodes n LEFT JOIN boards b ON b.id::text = n.target_id WHERE n.set_key = ? ORDER BY n.sort_order, n.id",
                OpsBoardPreset.FOOTER_SET));
        return out;
    }

    // ── 시험 ───────────────────────────────────────────────────────────────

    @Test
    void 설치_전이거나_프리셋을_적용하지_않은_DB_에는_넣지_않는다() throws IOException {
        runMigrationSeed(); // 설치 전(PENDING)
        assertThat(boardCount()).isZero();
        installed(false);
        runMigrationSeed();
        assertThat(boardCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_sets WHERE set_key = 'footer'", Integer.class)).isZero();
    }

    @Test
    void 이미_설치된_DB_에는_넣고_두_번_돌아도_그대로이며_새_설치_프리셋과_같은_값이다() throws IOException {
        installed(true);
        runMigrationSeed();
        assertThat(boardCount()).isEqualTo(2);
        Map<String, Object> fromSql = snapshot();
        runMigrationSeed();
        assertThat(boardCount()).as("두 번째는 아무것도 넣지 않는다").isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM board_prefixes p JOIN boards b ON b.id = p.board_id WHERE b.slug IN ('bugs', 'requests')", Integer.class))
                .isEqualTo(18);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_nodes WHERE set_key = 'footer'", Integer.class)).isEqualTo(2);
        assertThat(snapshot()).isEqualTo(fromSql);

        // 값 확인(계약 표) — 버그 신고는 비밀글 OPTIONAL, 기능 요청은 OFF, 둘 다 처리 상태 켬 · 첫 등급 · 공지만 켬 · 메인 메뉴에 없음
        @SuppressWarnings("unchecked")
        Map<String, Object> bugs = (Map<String, Object>) fromSql.get("bugs");
        assertThat(bugs).containsEntry("secret_mode", "OPTIONAL").containsEntry("status_enabled", true).containsEntry("write_min", "SEEDLING")
                .containsEntry("comment_min", "SEEDLING").containsEntry("ads_hidden", true).containsEntry("indexable", false).containsEntry("point_mode", "OFF")
                .containsEntry("gadgets", "{POPULAR,LATEST}").containsEntry("created_by", root);
        assertThat(((Map<?, ?>) fromSql.get("requests")).get("secret_mode")).isEqualTo("OFF");
        List<Object> active = new ArrayList<>();
        for (Map<String, Object> p : jdbcPrefixes("bugs")) {
            if (Boolean.TRUE.equals(p.get("active"))) {
                active.add(p.get("name"));
            }
        }
        assertThat(active).containsExactly("공지", "화면 오류", "기능 오류", "모바일", "결제·주문", "기타");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_nodes n JOIN boards b ON b.id::text = n.target_id WHERE n.set_key = 'main' AND b.slug IN "
                + "('bugs', 'requests')", Integer.class)).isZero();

        // 새 설치 경로(프리셋 적용이 부르는 시더) — 지우고 다시 만들면 같은 값
        removeSeed();
        int created = tx.execute(status -> seeder.seed(root, Instant.now()));
        assertThat(created).isEqualTo(2);
        assertThat(snapshot()).isEqualTo(fromSql);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_nodes n JOIN boards b ON b.id::text = n.target_id WHERE n.set_key = 'main' AND b.slug IN "
                + "('bugs', 'requests')", Integer.class)).as("시더도 메인 메뉴에 넣지 않는다").isZero();
        int again = tx.execute(status -> seeder.seed(root, Instant.now()));
        assertThat(again).as("두 번째는 만들지 않는다").isZero();
        runMigrationSeed();
        assertThat(boardCount()).as("시더가 만든 뒤 마이그레이션이 돌아도 그대로").isEqualTo(2);
    }

    @Test
    void 같은_주소나_이름의_게시판_footer_그룹이_있으면_건너뛴다() throws IOException {
        installed(true);
        long mine = ids.nextId();
        boardFixture.board(mine, "bugs", "우리 버그 게시판", 970);
        jdbc.update("INSERT INTO menu_sets (set_key, name, placement) VALUES ('footer', '내 푸터', 'CUSTOM')");
        runMigrationSeed();
        assertThat(jdbc.queryForObject("SELECT name FROM boards WHERE slug = 'bugs'", String.class)).isEqualTo("우리 버그 게시판");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM boards WHERE slug = 'requests'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name FROM menu_sets WHERE set_key = 'footer'", String.class)).isEqualTo("내 푸터");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_nodes WHERE set_key = 'footer'", Integer.class)).isZero();
    }

    private List<Map<String, Object>> jdbcPrefixes(String slug) {
        return jdbc.queryForList("SELECT p.name, p.active FROM board_prefixes p JOIN boards b ON b.id = p.board_id WHERE b.slug = ? ORDER BY p.sort_order, p.id", slug);
    }
}
