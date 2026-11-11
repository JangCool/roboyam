package com.community.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.commerce.CommerceAdminTestBase;
import com.community.home.dto.response.GadgetPageFolderView;
import com.community.home.dto.response.GadgetPageView;
import com.community.home.service.GadgetPageAdminService;
import com.community.menu.dto.request.MenuNodeRequest;
import com.community.menu.dto.request.MenuTreeSaveRequest;
import com.community.menu.dto.response.MenuFolderView;
import com.community.menu.dto.response.MenuItemView;
import com.community.menu.dto.response.MenuTreeResponse;
import com.community.permission.dto.AdminActor;
import com.community.testsupport.BoardFixture;
import com.community.zconfiguration.exception.ValidationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 메뉴 바로가기(다른 묶음에도 보이기) — 관리자 트리 저장·조회와 표 규칙(V167), 가젯 페이지 기준 폴더 게시판(menu-shortcut-contract).
 *
 * <ul>
 *   <li>트리 저장이 {@code shortcut} 을 받아 저장하고 돌려준다 — 진짜 자리 + 다른 묶음 바로가기는 같은 그룹에 같은 게시판 두 줄(부분 유일 색인).
 *       감사 기록 트리 요약에 {@code [shortcut]}. 진짜 자리 없는 바로가기는 400(화면이 진짜 자리를 지울 때 바로가기도 같이 빼서 보낸다).</li>
 *   <li>표: 진짜 자리끼리 두 번 · 같은 폴더에 같은 게시판 바로가기 두 번 · 최상위·게시판 아닌·모바일 탭 바로가기는 DB 가 막는다.</li>
 *   <li>가젯 페이지 「기준 폴더의 게시판」은 그 폴더의 바로가기 게시판도 담는다.</li>
 * </ul>
 * 시험 전용 메뉴 그룹을 만들어 보고, main 에 넣은 시험 노드는 끝나면 지운다.
 */
class MenuShortcutAdminTest extends CommerceAdminTestBase {

    @Autowired
    private MenuTreeService trees;
    @Autowired
    private GadgetPageAdminService gadgetPages;
    @Autowired
    private ApplicationContext context;

    private String setKey;
    private long boardA;
    private long boardB;
    private final List<Long> mainNodes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        setKey = "sc-" + Long.toString(ids.nextId(), 36);
        jdbc.update("INSERT INTO menu_sets (set_key, name, placement) VALUES (?, '바로가기 시험', 'CUSTOM')", setKey);
        BoardFixture boards = new BoardFixture(context);
        boardA = ids.nextId();
        boardB = ids.nextId();
        boards.board(boardA, "sca" + Long.toString(boardA, 36), "바로가기 A" + (boardA % 1000000), 981);
        boards.board(boardB, "scb" + Long.toString(boardB, 36), "바로가기 B" + (boardB % 1000000), 982);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM menu_sets WHERE set_key = ?", setKey);
        mainNodes.forEach(id -> jdbc.update("DELETE FROM menu_nodes WHERE id = ?", id));
    }

    @Test
    void 트리_저장이_바로가기를_받아_저장하고_돌려준다() {
        long root = rootId();
        AdminActor actor = actor(root);
        String version = trees.tree(actor, setKey).version();

        MenuTreeResponse saved = trees.save(actor, setKey, new MenuTreeSaveRequest(version, List.of(
                folder("커뮤니티", board(boardA, false), board(boardB, false)),
                folder("핫 로토픽", board(boardA, true), board(boardB, true)))));

        assertThat(saved.version()).isNotEqualTo(version);
        assertThat(shortcuts(saved, 0)).containsExactly(false, false);
        assertThat(shortcuts(saved, 1)).containsExactly(true, true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_nodes WHERE set_key = ? AND target_id = ?", Integer.class, setKey, Long.toString(boardA)))
                .as("같은 그룹에 같은 게시판 두 줄(진짜 자리 + 바로가기)").isEqualTo(2);
        assertThat(shortcuts(trees.tree(actor, setKey), 1)).as("다시 읽어도").containsExactly(true, true);
        assertThat(lastAudit(root, AuditActions.MENU_TREE_UPDATE).detail()).contains("[shortcut]");

        // 같은 트리를 다시 보내면 바뀐 것이 없다(버전 그대로) — 응답의 ID 를 그대로 싣는다
        MenuTreeResponse again = trees.save(actor, setKey, new MenuTreeSaveRequest(saved.version(), toRequest(saved)));
        assertThat(again.version()).isEqualTo(saved.version());
    }

    @Test
    void 진짜_자리_없는_바로가기는_400_SHORTCUT_ORIGIN_MISSING() {
        AdminActor actor = actor(rootId());
        String version = trees.tree(actor, setKey).version();

        assertThatThrownBy(() -> trees.save(actor, setKey, new MenuTreeSaveRequest(version, List.of(
                folder("커뮤니티", board(boardB, false)),
                folder("핫 로토픽", board(boardA, true))))))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields().get("nodes[1].children[0].targetId").key())
                        .isEqualTo("menu.shortcut.originMissing"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_nodes WHERE set_key = ?", Integer.class, setKey)).isZero();
    }

    @Test
    void 표는_진짜_자리_한_번과_바로가기_자리를_지킨다() {
        long folderA = node(null, "FOLDER", null, false, false);
        long folderB = node(null, "FOLDER", null, false, false);
        node(folderA, "ITEM", boardA, false, false);
        node(folderB, "ITEM", boardA, true, false); // 진짜 자리 + 바로가기 — 부분 색인이라 들어간다

        assertThatThrownBy(() -> node(folderB, "ITEM", boardA, false, false)).as("진짜 자리 두 번").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> node(folderB, "ITEM", boardA, true, false)).as("같은 폴더에 바로가기 두 번").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> node(null, "ITEM", boardB, true, false)).as("최상위 바로가기").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> node(folderA, "ITEM", boardB, true, true)).as("모바일 탭 바로가기").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, target_type, target_key, shortcut) "
                + "VALUES (?, ?, ?, 'ITEM', 9, 'BUILTIN', 'HOME', true)", ids.nextId(), setKey, folderA))
                .as("게시판 아닌 바로가기").isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT shortcut FROM menu_nodes WHERE id = ?", Boolean.class, folderA)).as("기본값").isFalse();
    }

    @Test
    void 가젯_페이지_기준_폴더의_게시판에_바로가기_게시판도_담는다() {
        // main 에 시험 폴더 둘 — 「원래」 에는 진짜 자리 A, 「바로가기만」 에는 A 의 바로가기만
        long home = mainNode(null, "FOLDER", "원래 " + (boardA % 10000), null, false);
        mainNode(home, "ITEM", null, boardA, false);
        long hot = mainNode(null, "FOLDER", "바로가기만 " + (boardA % 10000), null, false);
        mainNode(hot, "ITEM", null, boardA, true);

        List<GadgetPageFolderView> folders = gadgetPages.folders().folders();
        GadgetPageFolderView hotView = folders.stream().filter(f -> f.id().equals(Long.toString(hot))).findFirst().orElseThrow();
        GadgetPageFolderView homeView = folders.stream().filter(f -> f.id().equals(Long.toString(home))).findFirst().orElseThrow();

        String slugA = "sca" + Long.toString(boardA, 36);
        assertThat(hotView.boards()).extracting(GadgetPageView.Board::slug).containsExactly(slugA);
        assertThat(homeView.boards()).extracting(GadgetPageView.Board::slug).containsExactly(slugA);
    }

    // ── 재료 ─

    private static MenuNodeRequest folder(String label, MenuNodeRequest... children) {
        return new MenuNodeRequest(null, "FOLDER", label, null, true, "ALL", null, false, false, false, Arrays.asList(children), null, null, null, null, false,
                null, null, null, null, null, null, null);
    }

    private static MenuNodeRequest board(long boardId, boolean shortcut) {
        return new MenuNodeRequest(null, "ITEM", null, null, true, "ALL", null, false, false, false, List.of(), "BOARD", Long.toString(boardId), null, null, false,
                null, null, null, shortcut ? Boolean.TRUE : null, null, null, null);
    }

    /** 응답 트리 → 같은 저장 요청(ID 그대로) */
    private static List<MenuNodeRequest> toRequest(MenuTreeResponse res) {
        return res.nodes().stream().map(n -> {
            MenuFolderView f = (MenuFolderView) n;
            List<MenuNodeRequest> children = f.children().stream().map(c -> {
                MenuItemView i = (MenuItemView) c;
                return new MenuNodeRequest(i.id(), "ITEM", i.label(), i.nameEn(), i.visible(), i.audience(), i.minGrade(), false, false, false, List.of(),
                        i.targetType(), i.targetId(), i.targetKey(), i.settings(), i.mobileTab(), i.divider(), i.description(), null, i.shortcut(),
                        i.textColor(), i.bold(), i.badge());
            }).toList();
            return new MenuNodeRequest(f.id(), "FOLDER", f.label(), f.nameEn(), f.visible(), f.audience(), f.minGrade(), f.collapsible(), f.collapsed(),
                    f.autoAdd(), children, null, null, null, null, false, f.divider(), null, null, null, f.textColor(), f.bold(), f.badge());
        }).toList();
    }

    /** 폴더 {@code index} 의 항목들의 바로가기 표시 */
    private static List<Boolean> shortcuts(MenuTreeResponse res, int index) {
        return ((MenuFolderView) res.nodes().get(index)).children().stream().map(c -> ((MenuItemView) c).shortcut()).toList();
    }

    /** 시험 그룹에 노드 하나(표에 바로) */
    private long node(Long parentId, String kind, Long boardId, boolean shortcut, boolean mobileTab) {
        long id = ids.nextId();
        if ("FOLDER".equals(kind)) {
            jdbc.update("INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, label) VALUES (?, ?, ?, 'FOLDER', 0, '폴더')", id, setKey, parentId);
        } else {
            jdbc.update("INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, target_type, target_id, shortcut, mobile_tab) "
                    + "VALUES (?, ?, ?, 'ITEM', 0, 'BOARD', ?, ?, ?)", id, setKey, parentId, Long.toString(boardId), shortcut, mobileTab);
        }
        return id;
    }

    /** main 그룹에 시험 노드 하나 — 끝나면 지운다(맨 뒤 차례) */
    private long mainNode(Long parentId, String kind, String label, Long boardId, boolean shortcut) {
        long id = ids.nextId();
        if ("FOLDER".equals(kind)) {
            jdbc.update("INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, label) VALUES (?, 'main', ?, 'FOLDER', 9000, ?)", id, parentId, label);
        } else {
            jdbc.update("INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, target_type, target_id, shortcut) VALUES (?, 'main', ?, 'ITEM', 0, 'BOARD', ?, ?)",
                    id, parentId, Long.toString(boardId), shortcut);
        }
        mainNodes.addFirst(id);
        return id;
    }
}
