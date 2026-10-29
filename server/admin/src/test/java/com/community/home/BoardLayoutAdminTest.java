package com.community.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.commerce.CommerceAdminTestBase;
import com.community.home.dto.HomeLayoutItem;
import com.community.home.dto.request.BoardLayoutSaveRequest;
import com.community.home.dto.request.HomeLayoutSaveRequest;
import com.community.home.dto.response.BoardLayoutView;
import com.community.home.dto.response.HomeLayoutGadgetView;
import com.community.home.service.BoardLayoutAdminService;
import com.community.home.service.BoardLayoutDefaults;
import com.community.home.service.HomeGadgetKinds;
import com.community.home.service.HomeLayoutJson;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 관리자 게시판 레이아웃(board-layout-contract) — 기본 배치는 본문만 전체 폭 · 두 화면 켬, 본문 {@code BOARD_BODY} 는 꼭 하나(아니면 {@code 400 fields.layout.items}) ·
 * 최소 크기(12칸 6×20, 24칸 폭 ×2) · 본문의 제목·꾸미기·설정은 고정 · 대상 게시판 {@code BOARD} 는 여기서만({@code FOLDER} 400) · 적용 화면은 불리언.
 */
class BoardLayoutAdminTest extends CommerceAdminTestBase {

    /** 24칸 테마 · 12칸 테마(다음식) */
    private static final String THEME = "roboyam";
    private static final String THEME_12 = "daum";

    @Autowired
    private BoardLayoutAdminService service;

    private AdminActor root;

    @BeforeEach
    void setUp() {
        long rootId = rootId();
        root = actor(rootId);
        jdbc.update("INSERT INTO site_settings (id, site_name, public_url, updated_by) VALUES (1, '시험', 'http://localhost', ?) ON CONFLICT (id) DO NOTHING", rootId);
        // 이 시험은 기본 배치(본문만)에서 시작한다
        jdbc.update("DELETE FROM home_gadgets WHERE board_layout");
        jdbc.update("DELETE FROM board_layouts");
    }

    private static HomeLayoutSaveRequest.Gadget body(String id) {
        return new HomeLayoutSaveRequest.Gadget(id, HomeGadgetKinds.BOARD_BODY, "바꾼 제목", null, null, HomeLayoutJson.object().put("icon", "pi-star"),
                HomeLayoutJson.object().put("count", 3));
    }

    private static HomeLayoutSaveRequest.Item item(String id, int x, int y, int w, int h) {
        return new HomeLayoutSaveRequest.Item(id, x, y, w, h);
    }

    private static BoardLayoutSaveRequest request(Boolean list, Boolean post, int columns, List<HomeLayoutSaveRequest.Item> items, List<HomeLayoutSaveRequest.Gadget> gadgets) {
        return new BoardLayoutSaveRequest(new BoardLayoutSaveRequest.Screens(list, post), new HomeLayoutSaveRequest.Layout(columns, items), gadgets);
    }

    /** 본문 17칸 + 오른쪽 「이 게시판 인기글」(대상 BOARD) — 추천 배치처럼 */
    private static BoardLayoutSaveRequest recommended(String bodyId) {
        ObjectNode config = HomeLayoutJson.object().put("boardScope", "BOARD").put("count", 10).put("sort", "POPULAR").put("rowStyle", "RANK");
        config.putArray("boardIds");
        return request(true, false, 24, List.of(item(bodyId, 0, 0, 17, 20), item("new-1", 17, 0, 7, 14)),
                List.of(body(bodyId), new HomeLayoutSaveRequest.Gadget("new-1", "POST_LIST", "이 게시판 인기글", true, false, null, config)));
    }

    @Test
    void 저장한_적_없으면_본문만_전체_폭_두_화면_켬() {
        BoardLayoutView view = service.get(THEME);
        BoardLayoutView daum = service.get(THEME_12);

        assertThat(view.updatedAt()).isNull();
        assertThat(view.screens().list()).isTrue();
        assertThat(view.screens().post()).isTrue();
        assertThat(view.layout().columns()).isEqualTo(24);
        assertThat(view.layout().items()).containsExactly(new HomeLayoutItem(BoardLayoutDefaults.BODY_ID, 0, 0, 24, 20));
        assertThat(view.gadgets()).singleElement().satisfies(g -> {
            assertThat(g.type()).isEqualTo(HomeGadgetKinds.BOARD_BODY);
            assertThat(g.style()).isNull();
            assertThat(g.config()).isNull();
        });
        assertThat(daum.layout().items()).containsExactly(new HomeLayoutItem(BoardLayoutDefaults.BODY_ID, 0, 0, 12, 20));
        assertThat(service.defaults(THEME).layout()).isEqualTo(view.layout());
        assertThatThrownBy(() -> service.get("nope")).isInstanceOf(CommonException.class);
    }

    @Test
    void 추천_배치를_저장하면_본문은_고정이고_BOARD_범위를_받는다() {
        BoardLayoutView saved = service.save(root, THEME, request(true, false, 24, recommended(BoardLayoutDefaults.BODY_ID).layout().items(),
                recommended(BoardLayoutDefaults.BODY_ID).gadgets()));

        assertThat(saved.updatedAt()).isNotNull();
        assertThat(saved.screens().list()).isTrue();
        assertThat(saved.screens().post()).isFalse();
        HomeLayoutGadgetView body = saved.gadgets().stream().filter(g -> HomeGadgetKinds.BOARD_BODY.equals(g.type())).findFirst().orElseThrow();
        assertThat(body.title()).isEqualTo(BoardLayoutDefaults.BODY_TITLE);
        assertThat(body.style()).isNull();
        assertThat(body.config()).isNull();
        String stored = jdbc.queryForObject("SELECT style::text || config::text FROM home_gadgets WHERE id = ?", String.class, Long.parseLong(body.id()));
        assertThat(stored).doesNotContain("pi-star", "count");
        HomeLayoutGadgetView popular = saved.gadgets().stream().filter(g -> "POST_LIST".equals(g.type())).findFirst().orElseThrow();
        assertThat(popular.config().path("boardScope").asString()).isEqualTo("BOARD");
        assertThat(popular.mobile()).isFalse();
        // 홈 가젯 목록에는 섞이지 않는다
        Integer homeRows = jdbc.queryForObject("SELECT count(*) FROM home_gadgets WHERE theme = ? AND page_id IS NULL AND NOT board_layout AND id = ?", Integer.class,
                THEME, Long.parseLong(popular.id()));
        assertThat(homeRows).isZero();

        // 다시 저장(저장 본문 ID, 인기글은 뺌) — 본문 행은 그대로 남는다
        BoardLayoutView again = service.save(root, THEME, request(true, true, 24, List.of(item(body.id(), 0, 0, 17, 20)), List.of(body(body.id()))));
        assertThat(again.gadgets()).extracting(HomeLayoutGadgetView::id).containsExactly(body.id());
        assertThat(again.layout().items()).containsExactly(new HomeLayoutItem(body.id(), 0, 0, 17, 20));
    }

    @Test
    void 본문이_없거나_둘이면_layout_items_400() {
        ValidationException none = catchValidation(request(true, true, 24, List.of(item("new-1", 0, 0, 24, 10)),
                List.of(new HomeLayoutSaveRequest.Gadget("new-1", "NOTICE_TEXT", "안내", null, null, null, null))));
        assertThat(none.getFields()).containsKey("layout.items");

        ValidationException two = catchValidation(request(true, true, 24, List.of(item("a", 0, 0, 12, 20), item("b", 12, 0, 12, 20)),
                List.of(body("a"), body("b"))));
        assertThat(two.getFields()).containsOnlyKeys("layout.items");
    }

    @Test
    void 본문_최소_크기_밑은_400() {
        ValidationException narrow = catchValidation(request(true, true, 24, List.of(item("b", 0, 0, 11, 20)), List.of(body("b"))));
        assertThat(narrow.getFields()).containsOnlyKeys("layout.items[0]");
        ValidationException low = catchValidation(request(true, true, 12, List.of(item("b", 0, 0, 6, 19)), List.of(body("b"))));
        assertThat(low.getFields()).containsOnlyKeys("layout.items[0]");

        BoardLayoutView ok = service.save(root, THEME_12, request(true, true, 12, List.of(item("b", 0, 0, 6, 20)), List.of(body("b"))));
        assertThat(ok.layout().items()).singleElement().satisfies(it -> assertThat(it.w()).isEqualTo(6));
    }

    @Test
    void 게시판_레이아웃에서_FOLDER_범위와_빠진_적용_화면은_400() {
        ObjectNode folder = HomeLayoutJson.object().put("boardScope", "FOLDER");
        ValidationException e = catchValidation(request(null, true, 24, List.of(item("b", 0, 0, 17, 20), item("new-1", 17, 0, 7, 10)),
                List.of(body("b"), new HomeLayoutSaveRequest.Gadget("new-1", "GALLERY", "사진", null, null, null, folder))));
        assertThat(e.getFields()).containsOnlyKeys("screens.list", "gadgets[1].config.boardScope");

        ValidationException noScreens = catchValidation(new BoardLayoutSaveRequest(null, new HomeLayoutSaveRequest.Layout(24, List.of(item("b", 0, 0, 24, 20))),
                List.of(body("b"))));
        assertThat(noScreens.getFields()).containsOnlyKeys("screens.list", "screens.post");
    }

    @Test
    void 새_게시판_가젯은_설정을_보내지_않으면_지금_보는_게시판이_처음_범위() {
        List<HomeLayoutSaveRequest.Gadget> gadgets = new ArrayList<>(List.of(body("b")));
        gadgets.add(new HomeLayoutSaveRequest.Gadget("new-1", "POST_LIST", "새 글", null, null, null, null));
        BoardLayoutView saved = service.save(root, THEME, request(true, true, 24, List.of(item("b", 0, 0, 17, 20), item("new-1", 17, 0, 7, 10)), gadgets));

        JsonNode config = saved.gadgets().stream().filter(g -> "POST_LIST".equals(g.type())).findFirst().orElseThrow().config();
        assertThat(config.path("boardScope").asString()).isEqualTo("BOARD");
    }

    private ValidationException catchValidation(BoardLayoutSaveRequest request) {
        try {
            service.save(root, THEME, request);
        } catch (ValidationException e) {
            return e;
        }
        throw new AssertionError("400 이 아니다");
    }
}
