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
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 관리자 홈 구성의 공구 가젯 설정(⚙ — 사용자 결정 2026-10-29) — 보일 개수 1~10 · 정렬 DEADLINE·NEWEST·POPULAR 를 저장하고 다시 준다. 보내지 않으면 그대로,
 * 틀리면 {@code gadgets[i].config.count|sort} 로 400, 다른 종류의 {@code config} 는 무시. 저장한 적 없으면 처음 값(5개 · 마감 임박 순).
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

    private static HomeLayoutSaveRequest request(List<HomeLayoutGadgetView> gadgets, com.community.home.dto.HomeGridLayout layout,
                                                 Function<HomeLayoutGadgetView, HomeLayoutSaveRequest.Config> config) {
        List<HomeLayoutSaveRequest.Item> items = layout.items().stream().map(it -> new HomeLayoutSaveRequest.Item(it.gadgetId(), it.x(), it.y(), it.w(), it.h())).toList();
        return new HomeLayoutSaveRequest(new HomeLayoutSaveRequest.Layout(layout.columns(), items),
                gadgets.stream().map(g -> new HomeLayoutSaveRequest.Gadget(g.id(), g.type(), g.title(), config.apply(g))).toList());
    }

    private static HomeLayoutGadgetView groupBuy(HomeLayoutView view) {
        return view.gadgets().stream().filter(g -> "GROUP_BUY".equals(g.type())).findFirst().orElseThrow();
    }

    @Test
    void 공구_가젯은_처음_값으로_보이고_다른_종류는_설정이_없다() {
        HomeLayoutView view = service.get(THEME);

        assertThat(groupBuy(view).config()).isEqualTo(new HomeLayoutGadgetView.Config(5, "DEADLINE"));
        assertThat(view.gadgets()).filteredOn(g -> !"GROUP_BUY".equals(g.type())).allSatisfy(g -> assertThat(g.config()).isNull());
    }

    @Test
    void 개수와_정렬을_저장하고_보내지_않으면_그대로() {
        HomeLayoutView before = service.get(THEME);
        HomeLayoutView saved = service.save(root, THEME, request(before.gadgets(), before.layout(),
                g -> "GROUP_BUY".equals(g.type()) ? new HomeLayoutSaveRequest.Config(3, "POPULAR") : null));

        assertThat(groupBuy(saved).config()).isEqualTo(new HomeLayoutGadgetView.Config(3, "POPULAR"));
        assertThat(groupBuy(service.get(THEME)).config()).isEqualTo(new HomeLayoutGadgetView.Config(3, "POPULAR"));
        String stored = jdbc.queryForObject("SELECT config::text FROM home_gadgets WHERE id = ?", String.class, Long.parseLong(groupBuy(saved).id()));
        assertThat(stored).contains("\"count\"", "\"sort\"", "POPULAR");

        HomeLayoutView again = service.save(root, THEME, request(saved.gadgets(), saved.layout(), g -> null));

        assertThat(groupBuy(again).config()).isEqualTo(new HomeLayoutGadgetView.Config(3, "POPULAR"));
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
                g -> "new-1".equals(g.id()) ? new HomeLayoutSaveRequest.Config(10, "NEWEST") : null));

        assertThat(saved.gadgets()).filteredOn(g -> "새 공구".equals(g.title())).singleElement()
                .satisfies(g -> assertThat(g.config()).isEqualTo(new HomeLayoutGadgetView.Config(10, "NEWEST")));
    }

    @Test
    void 틀린_개수와_정렬은_칸_경로로_400_다른_종류의_설정은_무시() {
        HomeLayoutView before = service.get(THEME);
        List<HomeLayoutGadgetView> gadgets = before.gadgets();
        int index = gadgets.indexOf(groupBuy(before));

        assertThatThrownBy(() -> service.save(root, THEME, request(gadgets, before.layout(),
                g -> "GROUP_BUY".equals(g.type()) ? new HomeLayoutSaveRequest.Config(0, "BEST") : null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields())
                        .containsKeys("gadgets[" + index + "].config.count", "gadgets[" + index + "].config.sort"));
        assertThatThrownBy(() -> service.save(root, THEME, request(gadgets, before.layout(),
                g -> "GROUP_BUY".equals(g.type()) ? new HomeLayoutSaveRequest.Config(11, "DEADLINE") : null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsOnlyKeys("gadgets[" + index + "].config.count"));
        assertThatThrownBy(() -> service.save(root, THEME, request(gadgets, before.layout(),
                g -> "GROUP_BUY".equals(g.type()) ? new HomeLayoutSaveRequest.Config(null, null) : null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields())
                        .containsOnlyKeys("gadgets[" + index + "].config.count", "gadgets[" + index + "].config.sort"));

        HomeLayoutView ignored = service.save(root, THEME, request(gadgets, before.layout(),
                g -> "GROUP_BUY".equals(g.type()) ? null : new HomeLayoutSaveRequest.Config(0, "BEST")));

        assertThat(ignored.gadgets()).filteredOn(g -> !"GROUP_BUY".equals(g.type())).allSatisfy(g -> assertThat(g.config()).isNull());
        assertThat(groupBuy(ignored).config()).isEqualTo(groupBuy(before).config());
    }
}
