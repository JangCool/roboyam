package com.community.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.commerce.CommerceAdminTestBase;
import com.community.home.dto.HomeGridLayout;
import com.community.home.dto.HomeLayoutItem;
import com.community.home.dto.request.HomeLayoutSaveRequest;
import com.community.home.dto.response.HomeLayoutGadgetView;
import com.community.home.dto.response.HomeLayoutView;
import com.community.home.service.HomeGadgetKinds;
import com.community.home.service.HomeLayoutAdminService;
import com.community.home.service.HomeLayoutJson;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 관리자 홈 구성의 가젯 ⚙ 설정(공구 개수·정렬 — 사용자 결정 2026-10-29, 8종 전부 + 제목·노출·꾸미기 — gadget-page-contract 「가젯 설정」 2026-10-30).
 * 보낸 칸만 저장 값에 덮고(보내지 않은 칸·null 이면 그대로), 틀리면 {@code gadgets[i].config|style.<칸>}·{@code gadgets[i].title|desktop} 로 400.
 * 응답의 {@code config} 는 그 종류의 표 칸 전부(저장 값, 없으면 처음 값), {@code style} 은 꾸미기 칸 전부(없으면 테마 기본 꾸미기)다.
 */
class HomeLayoutGroupBuyConfigTest extends CommerceAdminTestBase {

    private static final String THEME = "naver";

    @Autowired
    private HomeLayoutAdminService service;

    private AdminActor root;

    @BeforeEach
    void setUp() {
        long rootId = rootId();
        root = actor(rootId);
        // 저장은 사이트 설정 행을 잠가 줄 세운다 — 시험 DB 에 없으면 시험용 행을 넣는다(updated_by 는 운영자)
        jdbc.update("INSERT INTO site_settings (id, site_name, public_url, updated_by) VALUES (1, '시험', 'http://localhost', ?) ON CONFLICT (id) DO NOTHING", rootId);
        // 이 시험은 테마 기본 배치에서 시작한다
        var defaults = service.defaults(THEME);
        service.save(root, THEME, request(defaults.gadgets(), defaults.layout(), g -> null));
    }

    /** 배치 그대로 + 가젯마다 {@code edit} 가 준 칸만 보낸다(null 이면 id·type 만 — 제목·노출·꾸미기·설정은 보내지 않는다) */
    private static HomeLayoutSaveRequest request(List<HomeLayoutGadgetView> gadgets, HomeGridLayout layout, Function<HomeLayoutGadgetView, Edit> edit) {
        List<HomeLayoutSaveRequest.Item> items = layout.items().stream().map(it -> new HomeLayoutSaveRequest.Item(it.gadgetId(), it.x(), it.y(), it.w(), it.h())).toList();
        return new HomeLayoutSaveRequest(new HomeLayoutSaveRequest.Layout(layout.columns(), items), gadgets.stream().map(g -> {
            Edit e = edit.apply(g);
            return e == null ? new HomeLayoutSaveRequest.Gadget(g.id(), g.type(), null, null, null, null, null)
                    : new HomeLayoutSaveRequest.Gadget(g.id(), g.type(), e.title(), e.desktop(), e.mobile(), e.style(), e.config());
        }).toList());
    }

    /** 가젯 하나에 보낼 칸 */
    private record Edit(String title, Boolean desktop, Boolean mobile, JsonNode style, JsonNode config) {

        static Edit config(JsonNode config) {
            return new Edit(null, null, null, null, config);
        }
    }

    private static HomeLayoutGadgetView first(HomeLayoutView view, String type) {
        return view.gadgets().stream().filter(g -> type.equals(g.type())).findFirst().orElseThrow();
    }

    private static ObjectNode gb(Integer count, String sort) {
        ObjectNode c = HomeLayoutJson.object();
        if (count != null) {
            c.put("count", count);
        }
        if (sort != null) {
            c.put("sort", sort);
        }
        return c;
    }

    /** 응답 설정이 공구 칸 {count, sort} 만 그 값인가 */
    private static void assertGroupBuy(JsonNode config, int count, String sort) {
        assertThat(config).isNotNull();
        assertThat(config.propertyNames()).containsExactlyInAnyOrder("count", "sort");
        assertThat(config.path("count").asInt()).isEqualTo(count);
        assertThat(config.path("sort").asString()).isEqualTo(sort);
    }

    @Test
    void 공구_가젯은_처음_값으로_보이고_8종_모두_설정과_꾸미기_칸을_싣는다() {
        HomeLayoutView view = service.get(THEME);

        assertGroupBuy(first(view, "GROUP_BUY").config(), 5, "DEADLINE");
        assertThat(view.gadgets()).allSatisfy(g -> {
            assertThat(g.config()).isNotNull();
            assertThat(g.style().propertyNames()).contains("icon", "headerStyle", "titleSize", "titleColor", "subtitle", "cardLook", "cardColor", "mobileFold", "empty");
        });
        // 하이라이트 · 소모임도 ⚙ 칸을 싣는다(시드 값)
        assertThat(first(view, "HIGHLIGHT").config().propertyNames()).containsExactlyInAnyOrder("count", "lead", "thumbs");
        assertThat(first(view, "GROUPS").config().path("count").asInt()).isEqualTo(3);
    }

    @Test
    void 개수와_정렬을_저장하고_보내지_않으면_그대로() {
        HomeLayoutView before = service.get(THEME);
        HomeLayoutView saved = service.save(root, THEME, request(before.gadgets(), before.layout(),
                g -> "GROUP_BUY".equals(g.type()) ? Edit.config(gb(3, "POPULAR")) : null));

        assertGroupBuy(first(saved, "GROUP_BUY").config(), 3, "POPULAR");
        assertGroupBuy(first(service.get(THEME), "GROUP_BUY").config(), 3, "POPULAR");
        String stored = jdbc.queryForObject("SELECT config::text FROM home_gadgets WHERE id = ?", String.class, Long.parseLong(first(saved, "GROUP_BUY").id()));
        assertThat(stored).contains("\"count\"", "\"sort\"", "POPULAR");

        HomeLayoutView again = service.save(root, THEME, request(saved.gadgets(), saved.layout(), g -> null));
        assertGroupBuy(first(again, "GROUP_BUY").config(), 3, "POPULAR");

        // 칸 하나만 보내면 그 칸만 바뀐다
        HomeLayoutView partial = service.save(root, THEME, request(again.gadgets(), again.layout(),
                g -> "GROUP_BUY".equals(g.type()) ? Edit.config(gb(7, null)) : null));
        assertGroupBuy(first(partial, "GROUP_BUY").config(), 7, "POPULAR");
    }

    @Test
    void 새로_넣은_공구_가젯도_보낸_설정으로_만든다() {
        HomeLayoutView before = service.get(THEME);
        List<HomeLayoutGadgetView> gadgets = new ArrayList<>(before.gadgets());
        gadgets.add(new HomeLayoutGadgetView("new-1", "GROUP_BUY", "새 공구", true, true, null, null));
        int bottom = before.layout().items().stream().mapToInt(it -> it.y() + it.h()).max().orElse(0);
        List<HomeLayoutItem> items = new ArrayList<>(before.layout().items());
        int columns = before.layout().columns();
        items.add(new HomeLayoutItem("new-1", 0, bottom, HomeGadgetKinds.minW("GROUP_BUY", columns), HomeGadgetKinds.minH("GROUP_BUY")));
        HomeLayoutView saved = service.save(root, THEME, request(gadgets, new HomeGridLayout(columns, items),
                g -> "new-1".equals(g.id()) ? new Edit("새 공구", null, null, null, gb(10, "NEWEST")) : null));

        assertThat(saved.gadgets()).filteredOn(g -> "새 공구".equals(g.title())).singleElement()
                .satisfies(g -> assertGroupBuy(g.config(), 10, "NEWEST"));
    }

    @Test
    void 틀린_개수와_정렬은_칸_경로로_400() {
        HomeLayoutView before = service.get(THEME);
        List<HomeLayoutGadgetView> gadgets = before.gadgets();
        // 기본 배치에 공구 가젯이 둘(본문 모바일 전용 · 오른쪽 열 PC 전용) — 틀린 설정은 첫 공구 가젯에만 보낸다
        HomeLayoutGadgetView target = first(before, "GROUP_BUY");
        int index = gadgets.indexOf(target);

        assertThatThrownBy(() -> service.save(root, THEME, request(gadgets, before.layout(),
                g -> target.id().equals(g.id()) ? Edit.config(gb(0, "BEST")) : null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields())
                        .containsKeys("gadgets[" + index + "].config.count", "gadgets[" + index + "].config.sort"));
        assertThatThrownBy(() -> service.save(root, THEME, request(gadgets, before.layout(),
                g -> target.id().equals(g.id()) ? Edit.config(gb(11, "DEADLINE")) : null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsOnlyKeys("gadgets[" + index + "].config.count"));

        // 하이라이트 · 소모임의 개수는 1~6
        HomeLayoutGadgetView groups = first(before, "GROUPS");
        int groupsIndex = gadgets.indexOf(groups);
        assertThatThrownBy(() -> service.save(root, THEME, request(gadgets, before.layout(),
                g -> groups.id().equals(g.id()) ? Edit.config(gb(7, null)) : null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsOnlyKeys("gadgets[" + groupsIndex + "].config.count"));
    }

    @Test
    void 저장_가젯의_제목_노출_꾸미기를_바꾸고_보낸_칸만_덮는다() {
        HomeLayoutView before = service.get(THEME);
        HomeLayoutGadgetView target = first(before, "GALLERY");
        ObjectNode style = HomeLayoutJson.object().put("icon", "pi-video").put("titleColor", "#AABBCC").put("headerStyle", "FILLED");
        style.putObject("empty").put("text", "  아직 없어요 ");
        HomeLayoutView saved = service.save(root, THEME, request(before.gadgets(), before.layout(),
                g -> target.id().equals(g.id()) ? new Edit("  영상 모음 ", true, false, style, null) : null));

        HomeLayoutGadgetView after = saved.gadgets().stream().filter(g -> "GALLERY".equals(g.type())).findFirst().orElseThrow();
        assertThat(after.title()).isEqualTo("영상 모음");
        assertThat(after.desktop()).isTrue();
        assertThat(after.mobile()).isFalse();
        assertThat(after.style().path("icon").asString()).isEqualTo("pi-video");
        assertThat(after.style().path("titleColor").asString()).isEqualTo("#aabbcc");
        assertThat(after.style().path("headerStyle").asString()).isEqualTo("FILLED");
        // 보내지 않은 칸은 그대로
        assertThat(after.style().path("titleSize").asString()).isEqualTo(target.style().path("titleSize").asString());
        assertThat(after.style().path("empty").path("text").asString()).isEqualTo("아직 없어요");
        assertThat(after.style().path("empty").path("keep").asBoolean()).isEqualTo(target.style().path("empty").path("keep").asBoolean());
        assertThat(after.config()).isEqualTo(target.config());
        // 더보기(화면에서 고치지 않는 칸)는 남는다
        String more = jdbc.queryForObject("SELECT more::text FROM home_gadgets WHERE id = ?", String.class, Long.parseLong(after.id()));
        assertThat(more).contains("/boards/video");
    }

    @Test
    void 틀린_제목_노출_꾸미기는_칸_경로로_400() {
        HomeLayoutView before = service.get(THEME);
        List<HomeLayoutGadgetView> gadgets = before.gadgets();
        HomeLayoutGadgetView target = first(before, "GALLERY");
        int index = gadgets.indexOf(target);
        ObjectNode style = HomeLayoutJson.object().put("icon", "fa-star").put("headerStyle", "BIG").put("titleColor", "red").put("subtitle", "x".repeat(41));
        style.putObject("empty").put("keep", "yes");

        assertThatThrownBy(() -> service.save(root, THEME, request(gadgets, before.layout(),
                g -> target.id().equals(g.id()) ? new Edit(" ", false, false, style, null) : null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsOnlyKeys(
                        "gadgets[" + index + "].title", "gadgets[" + index + "].desktop",
                        "gadgets[" + index + "].style.icon", "gadgets[" + index + "].style.headerStyle", "gadgets[" + index + "].style.titleColor",
                        "gadgets[" + index + "].style.subtitle", "gadgets[" + index + "].style.empty.keep"));
    }

    @Test
    void 홈에서는_대상_게시판_BOARD_와_FOLDER_를_받지_않는다() {
        HomeLayoutView before = service.get(THEME);
        List<HomeLayoutGadgetView> gadgets = before.gadgets();
        HomeLayoutGadgetView target = first(before, "POST_LIST");
        int index = gadgets.indexOf(target);
        for (String scope : List.of("BOARD", "FOLDER")) {
            assertThatThrownBy(() -> service.save(root, THEME, request(gadgets, before.layout(),
                    g -> target.id().equals(g.id()) ? Edit.config(HomeLayoutJson.object().put("boardScope", scope)) : null)))
                    .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsOnlyKeys("gadgets[" + index + "].config.boardScope"));
        }
    }
}
