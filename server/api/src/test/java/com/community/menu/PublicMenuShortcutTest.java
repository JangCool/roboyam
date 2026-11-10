package com.community.menu;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.board.service.BoardCatalog;
import com.community.commerce.CommerceApiTestBase;
import com.community.home.dto.response.GadgetPageResponse;
import com.community.home.service.GadgetPageFolders;
import com.community.home.service.GadgetPageInitial;
import com.community.home.service.GadgetService;
import com.community.home.service.HomeLayoutJson;
import com.community.menu.dto.response.MenuGroupNodeView;
import com.community.menu.dto.response.MenuItemNodeView;
import com.community.menu.dto.response.MenuResponse;
import com.community.menu.service.PublicMenuService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 메뉴 바로가기(menu-shortcut-contract) — 공개 {@code GET /api/menu} 항목의 {@code shortcut}, 가젯 페이지 「묶음 범위(FOLDER)」 게시판에 그 폴더의 바로가기 게시판 포함.
 * 공개 메뉴는 시험 전용 그룹으로, 가젯 페이지 기준 폴더는 main 에 넣은 시험 폴더(끝나면 지운다)로 본다.
 */
class PublicMenuShortcutTest extends CommerceApiTestBase {

    @Autowired
    private PublicMenuService menus;
    @Autowired
    private GadgetService gadgets;
    @Autowired
    private GadgetPageFolders folders;
    @Autowired
    private BoardCatalog catalog;

    private String setKey;
    private long board;
    private String slug;
    private final List<Long> mainNodes = new ArrayList<>();
    private Long pageId;

    @BeforeEach
    void setUp() {
        setKey = "scp-" + Long.toString(ids.nextId(), 36);
        jdbc.update("INSERT INTO menu_sets (set_key, name, placement) VALUES (?, '바로가기 공개 시험', 'CUSTOM')", setKey);
        board = ids.nextId();
        slug = "scp" + Long.toString(board, 36);
        boardFixture.board(board, slug, "공개 바로가기 " + (board % 1000000), 983);
        catalog.invalidate();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM menu_sets WHERE set_key = ?", setKey);
        mainNodes.forEach(id -> jdbc.update("DELETE FROM menu_nodes WHERE id = ?", id));
        if (pageId != null) {
            jdbc.update("DELETE FROM gadget_pages WHERE id = ?", pageId);
        }
    }

    @Test
    void 공개_메뉴_항목에_shortcut_이_실리고_나머지_모양은_같다() {
        long home = node(setKey, null, "FOLDER", "커뮤니티", false, 0);
        long originId = node(setKey, home, "ITEM", null, false, 0);
        long hot = node(setKey, null, "FOLDER", "핫 로토픽", false, 1);
        long shortcutId = node(setKey, hot, "ITEM", null, true, 0);

        MenuResponse res = menus.menu(setKey, null);
        MenuItemNodeView origin = (MenuItemNodeView) ((MenuGroupNodeView) res.nodes().get(0)).children().get(0);
        MenuItemNodeView copy = (MenuItemNodeView) ((MenuGroupNodeView) res.nodes().get(1)).children().get(0);

        assertThat(origin.id()).isEqualTo(Long.toString(originId));
        assertThat(origin.shortcut()).isFalse();
        assertThat(copy.id()).isEqualTo(Long.toString(shortcutId));
        assertThat(copy.shortcut()).isTrue();
        assertThat(copy.path()).isEqualTo("/boards/" + slug);
        assertThat(copy).usingRecursiveComparison().ignoringFields("id", "shortcut").isEqualTo(origin);
        assertThat(hot).isNotEqualTo(home);
    }

    @Test
    void 가젯_페이지_FOLDER_범위에_그_폴더의_바로가기_게시판도_든다() {
        long home = node("main", null, "FOLDER", "원래 " + (board % 10000), false, 9000);
        node("main", home, "ITEM", null, false, 0);
        long hot = node("main", null, "FOLDER", "바로가기만 " + (board % 10000), false, 9001);
        node("main", hot, "ITEM", null, true, 0);

        assertThat(folders.of(hot).orElseThrow().activeSlugs()).as("바로가기만 있는 폴더").containsExactly(slug);
        assertThat(folders.of(home).orElseThrow().activeSlugs()).as("진짜 자리 폴더").containsExactly(slug);

        pageId = ids.nextId();
        String key = "scp-" + Long.toString(pageId, 36);
        jdbc.update("INSERT INTO gadget_pages (id, page_key, name, folder_id, initial_layout) VALUES (?, ?, '바로가기 페이지', ?, ?::jsonb)", pageId, key, hot,
                HomeLayoutJson.write(GadgetPageInitial.build("바로가기만", List.of())));

        GadgetPageResponse page = gadgets.page(key, null);
        assertThat(page.page().writeBoard()).as("FOLDER 범위 게시판(첫 켜진 게시판)이 바로가기 게시판이다").isEqualTo(slug);
    }

    /** 노드 하나(표에 바로) — 항목은 시험 게시판 */
    private long node(String set, Long parentId, String kind, String label, boolean shortcut, int order) {
        long id = ids.nextId();
        if ("FOLDER".equals(kind)) {
            jdbc.update("INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, label) VALUES (?, ?, ?, 'FOLDER', ?, ?)", id, set, parentId, order, label);
        } else {
            jdbc.update("INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, target_type, target_id, shortcut) VALUES (?, ?, ?, 'ITEM', ?, 'BOARD', ?, ?)",
                    id, set, parentId, order, Long.toString(board), shortcut);
        }
        if ("main".equals(set)) {
            mainNodes.addFirst(id);
        }
        return id;
    }
}
