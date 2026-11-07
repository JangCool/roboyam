package com.community.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.menu.dto.MenuBoardRef;
import com.community.menu.dto.MenuNode;
import com.community.menu.dto.MenuViewer;
import com.community.menu.dto.response.MenuGroupNodeView;
import com.community.menu.dto.response.MenuItemNodeView;
import com.community.menu.dto.response.MenuResponse;
import com.community.menu.target.BoardMenuTargetResolver;
import com.community.menu.target.BuiltinMenuTargetResolver;
import com.community.menu.target.MenuResolveContext;
import com.community.menu.target.MenuTargetRegistry;
import com.community.menu.target.MenuValidationContext;
import com.community.menu.target.PathMenuTargetResolver;
import com.community.menu.target.UrlMenuTargetResolver;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.env.StandardEnvironment;

/**
 * 메뉴 바로가기(다른 묶음에도 보이기) 규칙 — menu-shortcut-contract 「규칙」, admin-web {@code UtilMenu.validate} 와 같은 경로·차례. DB 없이 순수하게 본다.
 *
 * <ul>
 *   <li>바로가기는 BOARD 항목만 · 폴더 안에만 · 모바일 탭 없음.</li>
 *   <li>같은 그룹에 진짜 자리가 있어야 한다({@code SHORTCUT_ORIGIN_MISSING}), 진짜 자리와 같은 폴더·같은 폴더에 두 번째 바로가기는 불가({@code SHORTCUT_SAME_FOLDER}).</li>
 *   <li>「한 그룹에 게시판 한 번」은 진짜 자리끼리만.</li>
 *   <li>공개 응답 항목에 {@code shortcut} 이 실리고 나머지 모양은 일반 게시판 항목과 같다.</li>
 * </ul>
 */
class MenuShortcutRulesTest {

    private static final Map<Long, MenuBoardRef> BOARDS = Map.of(
            1L, new MenuBoardRef(1L, "ai", "AI", "AI", "BLUE", false, true),
            2L, new MenuBoardRef(2L, "game", "게임", null, null, false, true));

    private final MenuTreeValidator validator;
    private final MenuViewComposer composer;

    MenuShortcutRulesTest() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        Messages messages = new Messages(source, new StandardEnvironment(), "messages/messages");
        MenuTargetRegistry registry = new MenuTargetRegistry(List.of(new BoardMenuTargetResolver(), new BuiltinMenuTargetResolver(messages),
                new PathMenuTargetResolver(), new UrlMenuTargetResolver()));
        validator = new MenuTreeValidator(registry);
        composer = new MenuViewComposer(registry);
    }

    @Test
    void 진짜_자리와_다른_폴더의_바로가기는_통과하고_칸이_남는다() {
        List<MenuNode> out = validator.validate(List.of(
                folder(10L, "커뮤니티", board(11L, "1"), board(12L, "2")),
                folder(20L, "핫 로토픽", shortcut(null, "1"), shortcut(null, "2"))), side(), Map.of());

        assertThat(out.get(0).children()).extracting(MenuNode::shortcut).containsExactly(false, false);
        assertThat(out.get(1).children()).extracting(MenuNode::shortcut).containsExactly(true, true);
        assertThat(out.get(1).children()).extracting(MenuNode::targetId).containsExactly("1", "2");
    }

    @Test
    void 한_그룹에_게시판_한_번은_진짜_자리끼리만_센다() {
        // 진짜 자리 + 여러 묶음의 바로가기는 중복이 아니다
        validator.validate(List.of(
                folder(10L, "가", board(11L, "1")),
                folder(20L, "나", shortcut(null, "1")),
                folder(30L, "다", shortcut(null, "1"))), side(), Map.of());
        // 진짜 자리 두 개는 여전히 중복
        expectFields(List.of(folder(10L, "가", board(11L, "1")), folder(20L, "나", board(21L, "1"))),
                fields -> assertThat(fields).containsEntry("nodes[1].children[0].targetId", "menu.target.duplicate"));
    }

    @Test
    void 진짜_자리가_없으면_SHORTCUT_ORIGIN_MISSING() {
        expectFields(List.of(folder(10L, "가", board(11L, "2")), folder(20L, "나", shortcut(null, "1"))),
                fields -> assertThat(fields).containsExactly(Map.entry("nodes[1].children[0].targetId", "menu.shortcut.originMissing")));
    }

    @Test
    void 진짜_자리와_같은_폴더면_SHORTCUT_SAME_FOLDER() {
        expectFields(List.of(folder(10L, "가", board(11L, "1"), shortcut(null, "1"))),
                fields -> assertThat(fields).containsExactly(Map.entry("nodes[0].children[1].targetId", "menu.shortcut.sameFolder")));
        // 최상위의 진짜 자리와 최상위 바로가기 — 최상위 규칙이 먼저 걸린다
        expectFields(List.of(board(11L, "1"), shortcut(null, "1")),
                fields -> assertThat(fields).containsExactly(Map.entry("nodes[1].targetId", "menu.shortcut.topLevel")));
    }

    @Test
    void 같은_폴더의_두_번째_바로가기만_SHORTCUT_SAME_FOLDER() {
        expectFields(List.of(folder(10L, "가", board(11L, "1")), folder(20L, "나", shortcut(null, "1"), shortcut(null, "1"))),
                fields -> assertThat(fields).containsExactly(Map.entry("nodes[1].children[1].targetId", "menu.shortcut.sameFolder")));
    }

    @Test
    void 진짜_자리가_뒤에_있어도_찾는다_하위_폴더_안도() {
        validator.validate(List.of(
                folder(20L, "나", shortcut(null, "1")),
                folder(10L, "가", folder(15L, "가-1", board(11L, "1")))), side(), Map.of());
    }

    @Test
    void 바로가기는_폴더_안에만() {
        expectFields(List.of(folder(10L, "가", board(11L, "1")), shortcut(null, "1")),
                fields -> assertThat(fields).containsExactly(Map.entry("nodes[1].targetId", "menu.shortcut.topLevel")));
    }

    @Test
    void 바로가기는_게시판_항목만이고_모바일_탭이_없다() {
        MenuNode builtin = new MenuNode(null, MenuNode.ITEM, null, null, true, "ALL", null, false, false, false, "BUILTIN", null, "HOME", null, false, List.of())
                .withShortcut(true);
        expectFields(List.of(folder(10L, "가", builtin)),
                fields -> assertThat(fields).containsEntry("nodes[0].children[0].targetType", "menu.shortcut.board"));

        MenuNode tab = new MenuNode(null, MenuNode.ITEM, null, null, true, "ALL", null, false, false, false, "BOARD", "1", null, null, true, List.of())
                .withShortcut(true);
        expectFields(List.of(folder(10L, "가", board(11L, "1")), folder(20L, "나", tab)),
                fields -> assertThat(fields).containsExactly(Map.entry("nodes[1].children[0].mobileTab", "menu.shortcut.mobileTab")));
    }

    @Test
    void 폴더의_바로가기_표시는_버린다() {
        List<MenuNode> out = validator.validate(List.of(folder(10L, "가", board(11L, "1")).withShortcut(true)), side(), Map.of());
        assertThat(out.get(0).shortcut()).isFalse();
    }

    @Test
    void 공개_응답에_shortcut_이_실리고_나머지는_일반_게시판_항목과_같다() {
        List<MenuNode> tree = validator.validate(List.of(
                folder(10L, "커뮤니티", board(11L, "1")),
                folder(20L, "핫 로토픽", shortcut(21L, "1"))), side(), Map.of());

        MenuResponse res = composer.compose(tree, true, new MenuResolveContext(BOARDS, Map.of(), true, MenuViewer.guest()), "1");
        MenuItemNodeView origin = (MenuItemNodeView) ((MenuGroupNodeView) res.nodes().get(0)).children().get(0);
        MenuItemNodeView copy = (MenuItemNodeView) ((MenuGroupNodeView) res.nodes().get(1)).children().get(0);

        assertThat(origin.shortcut()).isFalse();
        assertThat(copy.shortcut()).isTrue();
        assertThat(copy.id()).isEqualTo("21").isNotEqualTo(origin.id());
        assertThat(copy).usingRecursiveComparison().ignoringFields("id", "shortcut").isEqualTo(origin);
        assertThat(copy.path()).isEqualTo("/boards/ai");
        assertThat(copy.mobileTab()).isFalse();
    }

    // ── 재료 ─

    private void expectFields(List<MenuNode> tree, Consumer<Map<String, String>> check) {
        assertThatThrownBy(() -> validator.validate(tree, side(), Map.of()))
                .isInstanceOfSatisfying(ValidationException.class, e -> check.accept(keys(e.getFields())));
    }

    /** 칸 → 문구 키(차례 그대로) — {@link MessageRef} 는 인자 배열 때문에 값으로 견줄 수 없다 */
    private static Map<String, String> keys(Map<String, MessageRef> fields) {
        Map<String, String> out = new LinkedHashMap<>();
        fields.forEach((field, message) -> out.put(field, message.key()));
        return out;
    }

    private static MenuValidationContext side() {
        return new MenuValidationContext(BOARDS, Set.of(), "SIDE");
    }

    private static MenuNode folder(Long id, String label, MenuNode... children) {
        return new MenuNode(id, MenuNode.FOLDER, label, null, true, "ALL", null, false, false, false, null, null, null, null, false, List.of(children));
    }

    private static MenuNode board(Long id, String boardId) {
        return new MenuNode(id, MenuNode.ITEM, null, null, true, "ALL", null, false, false, false, "BOARD", boardId, null, null, false, List.of());
    }

    private static MenuNode shortcut(Long id, String boardId) {
        return board(id, boardId).withShortcut(true);
    }
}
