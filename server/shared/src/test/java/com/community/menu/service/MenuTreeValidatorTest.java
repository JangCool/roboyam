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
import com.community.zconfiguration.message.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.databind.json.JsonMapper;

/** 메뉴 트리 검사·정리({@link MenuTreeValidator})와 보는 사람 기준 조립({@link MenuViewComposer}), 주소 규칙({@link MenuRules}) — DB 없이 순수하게 본다 */
class MenuTreeValidatorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<Long, MenuBoardRef> BOARDS = Map.of(
            1L, new MenuBoardRef(1L, "free", "자유게시판", "Free", null, false, true),
            2L, new MenuBoardRef(2L, "game", "게임", null, "BLUE", false, false));
    private static final Map<String, Integer> LEVELS = Map.of("SEEDLING", 1, "REGULAR", 2);

    private final MenuTargetRegistry registry;
    private final MenuTreeValidator validator;
    private final MenuViewComposer composer;

    MenuTreeValidatorTest() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        Messages messages = new Messages(source, new StandardEnvironment(), "messages/messages");
        registry = new MenuTargetRegistry(List.of(new BoardMenuTargetResolver(), new BuiltinMenuTargetResolver(messages), new PathMenuTargetResolver(),
                new UrlMenuTargetResolver()));
        validator = new MenuTreeValidator(registry);
        composer = new MenuViewComposer(registry);
    }

    @Test
    void 주소_규칙은_화면과_같다() {
        assertThat(MenuRules.pathProblem("/boards/notice")).isNull();
        assertThat(MenuRules.pathProblem("boards")).isNotNull();
        assertThat(MenuRules.pathProblem("//evil.com")).isNotNull();
        assertThat(MenuRules.pathProblem("/api/x")).isNotNull();
        assertThat(MenuRules.pathProblem("/a/../b")).isNotNull();
        assertThat(MenuRules.pathProblem("/javascript:alert(1)")).isNotNull();
        assertThat(MenuRules.hrefProblem("https://blog.roboyam.com/a?b=1")).isNull();
        assertThat(MenuRules.hrefProblem("/boards/free")).isNull();
        assertThat(MenuRules.hrefProblem("http://a.com").key()).isEqualTo("menu.href.http");
        assertThat(MenuRules.hrefProblem("https://user@a.com")).isNotNull();
        assertThat(MenuRules.hrefProblem("javascript:alert(1)")).isNotNull();
        assertThat(MenuRules.hrefProblem("https://")).isNotNull();
    }

    @Test
    void 위반을_칸_경로로_모은다() {
        List<MenuNode> tree = List.of(
                folder(null, "", List.of(board(null, "1"), board(null, "1"))),
                builtin(null, "MY_HOME", "ALL"),
                item(null, "PATH", null, null, JSON.createObjectNode().put("path", "nope")));
        assertThatThrownBy(() -> validator.validate(tree, side(), Map.of()))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKeys(
                        "nodes[0].label", "nodes[0].children[1].targetId", "nodes[1].audience", "nodes[2].label", "nodes[2].settings.path"));
    }

    @Test
    void 깊이_한도와_SIDE_전용_칸() {
        MenuNode deep = folder(null, "a", List.of(folder(null, "b", List.of(folder(null, "c", List.of(folder(null, "d", List.of())))))));
        assertThatThrownBy(() -> validator.validate(List.of(deep), side(), Map.of()))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.getFields()).containsKey("nodes[0].children[0].children[0].children[0].kind"));
        MenuNode tab = new MenuNode(null, MenuNode.ITEM, null, null, true, "ALL", null, false, false, false, "BUILTIN", null, "HOME", null, true, List.of());
        assertThatThrownBy(() -> validator.validate(List.of(tab), new MenuValidationContext(BOARDS, LEVELS.keySet(), "MYPAGE"), Map.of()))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("nodes[0].mobileTab"));
    }

    @Test
    void 새로_켠_자동_추가_폴더가_하나면_기존_것을_끈다() {
        MenuNode old = folder(10L, "옛 폴더", List.of()).withAutoAdd(true);
        MenuNode fresh = folder(null, "새 폴더", List.of()).withAutoAdd(true);
        List<MenuNode> out = validator.validate(List.of(old, fresh), side(), Map.of(10L, true));
        assertThat(out.get(0).autoAdd()).isFalse();
        assertThat(out.get(1).autoAdd()).isTrue();

        MenuNode fresh2 = folder(null, "또 새 폴더", List.of()).withAutoAdd(true);
        assertThatThrownBy(() -> validator.validate(List.of(fresh, fresh2), side(), Map.of()))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("nodes"));
    }

    @Test
    void 모르는_유형은_받은_그대로_보존한다() {
        var settings = JSON.createObjectNode().put("pollId", 42).put("showResult", true);
        MenuNode poll = item("이번 주 투표", "POLL", "42", null, settings);
        List<MenuNode> out = validator.validate(List.of(poll), side(), Map.of());
        assertThat(out.get(0).targetType()).isEqualTo("POLL");
        assertThat(out.get(0).targetId()).isEqualTo("42");
        assertThat(out.get(0).settings()).isEqualTo(settings);
    }

    @Test
    void 노드_한도는_80() {
        List<MenuNode> many = new ArrayList<>();
        for (int i = 0; i < MenuLimits.NODES + 1; i++) {
            many.add(item("링크" + i, "URL", null, null, JSON.createObjectNode().put("href", "https://a.com/" + i).put("newTab", true)));
        }
        assertThatThrownBy(() -> validator.validate(many, side(), Map.of()))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("nodes"));
    }

    @Test
    void 보는_사람_기준으로_거르고_빈_폴더를_뺀다() {
        List<MenuNode> tree = List.of(
                builtin(1L, "HOME", "ALL").withId(1L),
                new MenuNode(2L, MenuNode.ITEM, null, null, true, "ALL", null, false, false, false, "BUILTIN", null, "POPULAR", null, true, List.of()),
                folder(3L, "게시판", List.of(board(4L, "1"), board(5L, "2"))),
                folder(6L, "회원", List.of(builtin(7L, "MY_HOME", "MEMBER"))),
                folder(8L, "등급", List.of(new MenuNode(9L, MenuNode.ITEM, "단골", null, true, "GRADE_MIN", "REGULAR", false, false, false, "URL", null, null,
                        JSON.createObjectNode().put("href", "https://a.com").put("newTab", true), false, List.of()))),
                new MenuNode(10L, MenuNode.ITEM, "투표", null, true, "ALL", null, false, false, false, "POLL", "42", null, null, false, List.of()));

        MenuResponse guest = composer.compose(tree, true, context(MenuViewer.guest()), "3");
        assertThat(guest.nodes()).extracting("id").containsExactly("1", "2", "3");
        assertThat(guest.mobileTabs()).containsExactly("2");
        MenuItemNodeView home = (MenuItemNodeView) guest.nodes().get(0);
        assertThat(home.label()).isEqualTo("홈");
        assertThat(home.nameEn()).isEqualTo("Home");
        assertThat(home.key()).isEqualTo("HOME");
        MenuGroupNodeView boards = (MenuGroupNodeView) guest.nodes().get(2);
        assertThat(boards.kind()).isEqualTo("GROUP");
        assertThat(boards.children()).hasSize(1);
        MenuItemNodeView free = (MenuItemNodeView) boards.children().get(0);
        assertThat(free.label()).isEqualTo("자유게시판");
        assertThat(free.path()).isEqualTo("/boards/free");
        assertThat(free.board().slug()).isEqualTo("free");

        MenuResponse seedling = composer.compose(tree, true, context(new MenuViewer(MenuViewer.MEMBER, 1)), "3");
        assertThat(seedling.nodes()).extracting("id").containsExactly("1", "2", "3", "6");
        MenuResponse regular = composer.compose(tree, true, context(new MenuViewer(MenuViewer.MEMBER, 2)), "3");
        assertThat(regular.nodes()).extracting("id").containsExactly("1", "2", "3", "6", "8");
        MenuItemNodeView link = (MenuItemNodeView) ((MenuGroupNodeView) regular.nodes().get(4)).children().get(0);
        assertThat(link.extra()).containsEntry("external", true);

        MenuResponse notSide = composer.compose(tree, false, context(MenuViewer.guest()), "3");
        assertThat(notSide.mobileTabs()).isEmpty();
    }

    // ── 재료 ─

    private static MenuValidationContext side() {
        return new MenuValidationContext(BOARDS, Set.copyOf(LEVELS.keySet()), "SIDE");
    }

    private static MenuResolveContext context(MenuViewer viewer) {
        return new MenuResolveContext(BOARDS, LEVELS, true, viewer);
    }

    private static MenuNode folder(Long id, String label, List<MenuNode> children) {
        return new MenuNode(id, MenuNode.FOLDER, label, null, true, "ALL", null, false, false, false, null, null, null, null, false, children);
    }

    private static MenuNode board(Long id, String boardId) {
        return new MenuNode(id, MenuNode.ITEM, null, null, true, "ALL", null, false, false, false, "BOARD", boardId, null, null, false, List.of());
    }

    private static MenuNode builtin(Long id, String key, String audience) {
        return new MenuNode(id, MenuNode.ITEM, null, null, true, audience, null, false, false, false, "BUILTIN", null, key, null, false, List.of());
    }

    private static MenuNode item(String label, String type, String targetId, String targetKey, tools.jackson.databind.JsonNode settings) {
        return new MenuNode(null, MenuNode.ITEM, label, null, true, "ALL", null, false, false, false, type, targetId, targetKey, settings, false, List.of());
    }
}
