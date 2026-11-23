package com.community.menu;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.Commerce04AdminTestBase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * admin V13 — 저장한 관리자 메뉴 구성의 「광고 심사」(adReviews) 바로 뒤에 「광고 자리 관리」(adSlots)를 넣는다(docs/features/ad-slot-manager-contract.md 「메뉴」).
 * 테스트 DB 는 메뉴를 저장한 적이 없어(마이그레이션 적용 때 첫 RETURN) 끼워 넣는 길이 돌지 않으므로, 저장한 구성을 SQL 로 세우고 V13 을 손으로 다시 돌린다(선례 V4 —
 * {@code BizApplicationDomainSplitMigrationTest}). 상태 행(version·updated_at)은 테스트 뒤 되돌린다.
 */
class AdminMenuAdSlotsMigrationTest extends Commerce04AdminTestBase {

    private static final String V13 = "db/migration/admin/V13__admin_menu_ad_slots.sql";

    private Map<String, Object> stateBefore;
    private long section;

    @BeforeEach
    void savedMenu() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_menu_nodes WHERE target_key IN ('adReviews', 'adSlots')", Integer.class))
                .as("다른 테스트가 남긴 광고 메뉴 항목이 없어야 한다").isZero();
        stateBefore = jdbc.queryForMap("SELECT version, updated_at FROM admin_menu_state WHERE id = 1");
        jdbc.update("UPDATE admin_menu_state SET updated_at = now() WHERE id = 1"); // 저장한 적 있는 설치
        section = ids.nextId();
        jdbc.update("INSERT INTO admin_menu_nodes (id, mode, parent_id, kind, sort_order, label_key, icon, visible) VALUES (?, 'normal', NULL, 'SECTION', 900, 'menu.ad', "
                + "'pi pi-megaphone', true)", section);
    }

    @AfterEach
    void restore() {
        jdbc.update("DELETE FROM admin_menu_nodes WHERE id = ?", section); // 하위는 CASCADE
        jdbc.update("UPDATE admin_menu_state SET version = ?, updated_at = ? WHERE id = 1", stateBefore.get("version"), stateBefore.get("updated_at"));
    }

    private void run() throws IOException {
        jdbc.execute(new ClassPathResource(V13).getContentAsString(StandardCharsets.UTF_8));
    }

    private void screen(String key, int order, boolean visible) {
        jdbc.update("INSERT INTO admin_menu_nodes (id, mode, parent_id, kind, sort_order, icon, visible, target_type, target_key) VALUES (?, 'normal', ?, 'SCREEN', ?, "
                + "'pi pi-circle', ?, 'ADMIN_SCREEN', ?)", ids.nextId(), section, order, visible, key);
    }

    /** 이 묶음의 화면 키를 차례대로 */
    private List<String> children() {
        return jdbc.queryForList("SELECT target_key FROM admin_menu_nodes WHERE parent_id = ? ORDER BY sort_order", String.class, section);
    }

    private long version() {
        return jdbc.queryForObject("SELECT version FROM admin_menu_state WHERE id = 1", Long.class);
    }

    @Test
    void 광고_심사_바로_뒤에_넣고_뒤_형제를_밀고_구성_판을_올린다_다시_돌면_그대로() throws IOException {
        screen("adReviews", 0, false); // 운영자가 숨겨 둔 광고 심사 — 새 항목은 보이게 넣는다
        screen("adCalendar", 1, true);
        screen("adStats", 2, true);
        long before = version();

        run();

        assertThat(children()).containsExactly("adReviews", "adSlots", "adCalendar", "adStats");
        assertThat(jdbc.queryForList("SELECT sort_order FROM admin_menu_nodes WHERE parent_id = ? ORDER BY sort_order", Integer.class, section)).containsExactly(0, 1, 2,
                3);
        assertThat(jdbc.queryForMap("SELECT mode, kind, icon, visible, target_type, new_tab FROM admin_menu_nodes WHERE target_key = 'adSlots'"))
                .containsEntry("mode", "normal").containsEntry("kind", "SCREEN").containsEntry("icon", "pi pi-th-large").containsEntry("visible", true)
                .containsEntry("target_type", "ADMIN_SCREEN").containsEntry("new_tab", true);
        assertThat(version()).isEqualTo(before + 1);

        run(); // 이미 adSlots 가 있으면 넣지 않는다

        assertThat(children()).containsExactly("adReviews", "adSlots", "adCalendar", "adStats");
        assertThat(version()).isEqualTo(before + 1);
    }

    @Test
    void 저장_구성에_광고_심사가_없으면_아무것도_바꾸지_않는다() throws IOException {
        screen("adCalendar", 0, true);
        screen("adStats", 1, true);
        long before = version();

        run();

        assertThat(children()).containsExactly("adCalendar", "adStats");
        assertThat(version()).isEqualTo(before);
    }

    @Test
    void 저장한_적_없는_설치는_아무것도_바꾸지_않는다() throws IOException {
        jdbc.update("UPDATE admin_menu_state SET updated_at = NULL WHERE id = 1");
        screen("adReviews", 0, true);
        long before = version();

        run();

        assertThat(children()).containsExactly("adReviews");
        assertThat(version()).isEqualTo(before);
    }
}
