package com.community.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.commerce.CommerceAdminTestBase;
import com.community.home.dto.request.HomeLayoutSaveRequest;
import com.community.home.dto.response.HomeLayoutGadgetView;
import com.community.home.dto.response.HomeLayoutView;
import com.community.home.service.HomeGadgetKinds;
import com.community.home.service.HomeLayoutAdminService;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 관리자 홈 구성의 공구 가젯 설정(⚙ — 사용자 결정 2026-10-29) — 보일 개수 1~10 · 정렬 DEADLINE·NEWEST·POPULAR 를 저장하고 다시 준다. 보내지 않으면 그대로,
 * 틀리면 {@code gadgets[i].config.count|sort} 로 400, ⚙ 설정이 없는 종류(하이라이트·소모임 …)의 {@code config} 는 무시. 저장한 적 없으면 처음 값(5개 · 마감 임박 순).
 * 응답의 {@code config} 는 ⚙ 칸만 담은 JSON 이다(가젯 페이지 때 글 목록·갤러리·게시판 카드도 ⚙ 설정을 갖게 됐다 — gadget-page-contract 「가젯 설정」).
 */
class HomeLayoutGroupBuyConfigTest extends CommerceAdminTestBase {

    private static final String THEME = "naver";
    /** ⚙ 설정이 없는 종류 */
    private static final Set<String> PLAIN = Set.of("HIGHLIGHT", "GROUPS", "QUICK_LINKS", "NOTICE_TEXT");

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

    private static HomeLayoutSaveRequest request(List<HomeLayoutGadgetView> gadgets, com.community.home.dto.HomeGridLayout layout,
                                                 Function<HomeLayoutGadgetView, HomeLayoutSaveRequest.Config> config) {
        List<HomeLayoutSaveRequest.Item> items = layout.items().stream().map(it -> new HomeLayoutSaveRequest.Item(it.gadgetId(), it.x(), it.y(), it.w(), it.h())).toList();
        return new HomeLayoutSaveRequest(new HomeLayoutSaveRequest.Layout(layout.columns(), items),
                gadgets.stream().map(g -> new HomeLayoutSaveRequest.Gadget(g.id(), g.type(), g.title(), config.apply(g))).toList());
    }

    private static HomeLayoutGadgetView groupBuy(HomeLayoutView view) {
        return view.gadgets().stream().filter(g -> "GROUP_BUY".equals(g.type())).findFirst().orElseThrow();
    }

    /** 공구 가젯 설정 요청 */
    private static HomeLayoutSaveRequest.Config gb(Integer count, String sort) {
        return new HomeLayoutSaveRequest.Config(count, sort, null, null, null, null, null);
    }

    /** 응답 설정이 공구 칸 {count, sort} 만 그 값인가 */
    private static void assertGroupBuy(JsonNode config, int count, String sort) {
        assertThat(config).isNotNull();
        assertThat(config.propertyNames()).containsExactlyInAnyOrder("count", "sort");
        assertThat(config.path("count").asInt()).isEqualTo(count);
        assertThat(config.path("sort").asString()).isEqualTo(sort);
    }

    @Test
    void 공구_가젯은_처음_값으로_보이고_설정이_없는_종류는_null() {
        HomeLayoutView view = service.get(THEME);

        assertGroupBuy(groupBuy(view).config(), 5, "DEADLINE");
        assertThat(view.gadgets()).filteredOn(g -> PLAIN.contains(g.type())).isNotEmpty().allSatisfy(g -> assertThat(g.config()).isNull());
    }

    @Test
    void 개수와_정렬을_저장하고_보내지_않으면_그대로() {
        HomeLayoutView before = service.get(THEME);
        HomeLayoutView saved = service.save(root, THEME, request(before.gadgets(), before.layout(),
                g -> "GROUP_BUY".equals(g.type()) ? gb(3, "POPULAR") : null));

        assertGroupBuy(groupBuy(saved).config(), 3, "POPULAR");
        assertGroupBuy(groupBuy(service.get(THEME)).config(), 3, "POPULAR");
        String stored = jdbc.queryForObject("SELECT config::text FROM home_gadgets WHERE id = ?", String.class, Long.parseLong(groupBuy(saved).id()));
        assertThat(stored).contains("\"count\"", "\"sort\"", "POPULAR");

        HomeLayoutView again = service.save(root, THEME, request(saved.gadgets(), saved.layout(), g -> null));

        assertGroupBuy(groupBuy(again).config(), 3, "POPULAR");
    }

    @Test
    void 새로_넣은_공구_가젯도_보낸_설정으로_만든다() {
        HomeLayoutView before = service.get(THEME);
        List<HomeLayoutGadgetView> gadgets = new java.util.ArrayList<>(before.gadgets());
        gadgets.add(new HomeLayoutGadgetView("new-1", "GROUP_BUY", "새 공구", true, true, null));
        int bottom = before.layout().items().stream().mapToInt(it -> it.y() + it.h()).max().orElse(0);
        List<com.community.home.dto.HomeLayoutItem> items = new java.util.ArrayList<>(before.layout().items());
        int columns = before.layout().columns();
        items.add(new com.community.home.dto.HomeLayoutItem("new-1", 0, bottom, HomeGadgetKinds.minW("GROUP_BUY", columns), HomeGadgetKinds.minH("GROUP_BUY")));
        HomeLayoutView saved = service.save(root, THEME, request(gadgets, new com.community.home.dto.HomeGridLayout(before.layout().columns(), items),
                g -> "new-1".equals(g.id()) ? gb(10, "NEWEST") : null));

        assertThat(saved.gadgets()).filteredOn(g -> "새 공구".equals(g.title())).singleElement()
                .satisfies(g -> assertGroupBuy(g.config(), 10, "NEWEST"));
    }

    @Test
    void 틀린_개수와_정렬은_칸_경로로_400_설정이_없는_종류의_설정은_무시() {
        HomeLayoutView before = service.get(THEME);
        List<HomeLayoutGadgetView> gadgets = before.gadgets();
        // 기본 배치에 공구 가젯이 둘(본문 모바일 전용 · 오른쪽 열 PC 전용) — 틀린 설정은 첫 공구 가젯에만 보낸다
        HomeLayoutGadgetView target = groupBuy(before);
        int index = gadgets.indexOf(target);

        assertThatThrownBy(() -> service.save(root, THEME, request(gadgets, before.layout(),
                g -> target.id().equals(g.id()) ? gb(0, "BEST") : null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields())
                        .containsKeys("gadgets[" + index + "].config.count", "gadgets[" + index + "].config.sort"));
        assertThatThrownBy(() -> service.save(root, THEME, request(gadgets, before.layout(),
                g -> target.id().equals(g.id()) ? gb(11, "DEADLINE") : null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsOnlyKeys("gadgets[" + index + "].config.count"));
        assertThatThrownBy(() -> service.save(root, THEME, request(gadgets, before.layout(),
                g -> target.id().equals(g.id()) ? gb(null, null) : null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields())
                        .containsOnlyKeys("gadgets[" + index + "].config.count", "gadgets[" + index + "].config.sort"));

        HomeLayoutView ignored = service.save(root, THEME, request(gadgets, before.layout(),
                g -> PLAIN.contains(g.type()) ? gb(0, "BEST") : null));

        assertThat(ignored.gadgets()).filteredOn(g -> PLAIN.contains(g.type())).isNotEmpty().allSatisfy(g -> assertThat(g.config()).isNull());
        assertThat(groupBuy(ignored).config()).isEqualTo(groupBuy(before).config());
    }
}
