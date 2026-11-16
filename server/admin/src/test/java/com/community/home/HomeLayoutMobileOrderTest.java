package com.community.home;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.CommerceAdminTestBase;
import com.community.home.dto.HomeGridLayout;
import com.community.home.dto.HomeLayoutItem;
import com.community.home.dto.request.HomeLayoutSaveRequest;
import com.community.home.dto.response.HomeLayoutGadgetView;
import com.community.home.dto.response.HomeLayoutView;
import com.community.home.service.GadgetKinds;
import com.community.home.service.HomeLayoutAdminService;
import com.community.home.service.HomeLayoutDefaults;
import com.community.permission.dto.AdminActor;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 배치의 모바일 순서 {@code layout.mobileOrder}(home-news-contract 「모바일 순서」 2026-11-15) — 관리자 홈 구성 저장이 배치에 없는 ID·겹친 ID 를 버리고(오류 아님) 빈 목록은 null,
 * 시드·새 가젯 ID 는 저장 뒤 실제 가젯 ID 로 바꾼다. 아이로빗 테마 기본 배치에는 모바일 순서가 있고 다른 테마는 null 이다.
 * 다른 홈 시험(다음식·네이버식)과 겹치지 않게 아이로빗 테마의 홈에서 시험한다.
 */
class HomeLayoutMobileOrderTest extends CommerceAdminTestBase {

    private static final String THEME = "irobit";

    @Autowired
    private HomeLayoutAdminService service;

    private AdminActor root;

    @BeforeEach
    void setUp() {
        long rootId = rootId();
        root = actor(rootId);
        // SQL 로 남김: ON CONFLICT upsert — 사이트 설정 행(저장이 잠그는 행)을 없을 때만 넣는다
        jdbc.update("INSERT INTO site_settings (id, site_name, public_url, updated_by) VALUES (1, '시험', 'http://localhost', ?) ON CONFLICT (id) DO NOTHING", rootId);
        var defaults = service.defaults(THEME);
        service.save(root, THEME, request(defaults.gadgets(), defaults.layout(), defaults.layout().mobileOrder()));
    }

    private static HomeLayoutSaveRequest request(List<HomeLayoutGadgetView> gadgets, HomeGridLayout layout, List<String> mobileOrder) {
        List<HomeLayoutSaveRequest.Item> items = layout.items().stream().map(it -> new HomeLayoutSaveRequest.Item(it.gadgetId(), it.x(), it.y(), it.w(), it.h())).toList();
        return new HomeLayoutSaveRequest(new HomeLayoutSaveRequest.Layout(layout.columns(), items, mobileOrder), gadgets.stream()
                .map(g -> new HomeLayoutSaveRequest.Gadget(g.id(), g.type(), g.id().startsWith("new-") ? g.title() : null, null, null, null, null))
                .toList());
    }

    private static String idOf(HomeLayoutView view, String title, boolean mobileOnly) {
        return view.gadgets().stream().filter(g -> title.equals(g.title()) && (!mobileOnly || !g.desktop())).map(HomeLayoutGadgetView::id).findFirst().orElseThrow();
    }

    @Test
    void 아이로빗_기본_배치에는_모바일_순서가_있고_다른_테마는_null() {
        HomeLayoutDefaults.Home irobit = HomeLayoutDefaults.of(THEME);
        List<String> order = irobit.layout().mobileOrder();
        assertThat(order).hasSize(12).doesNotHaveDuplicates();
        assertThat(order.stream().map(id -> irobit.gadget(id).orElseThrow().title()).toList())
                .containsExactly("뉴스", "상단 띠 배너", "인기글", "함께 보는 글", "최신글", "광고", "포인트 게임", "팁과강좌", "사용기", "인기 소모임", "영상·짤", "띠 배너");
        // 함께 보는 글·최신글은 모바일 전용 가젯
        assertThat(irobit.gadget(order.get(3)).orElseThrow().desktop()).isFalse();
        assertThat(irobit.gadget(order.get(4)).orElseThrow().desktop()).isFalse();
        for (String other : List.of("default", "daum", "naver")) {
            assertThat(HomeLayoutDefaults.of(other).layout().mobileOrder()).as(other).isNull();
        }
    }

    @Test
    void 기본_배치를_저장하면_시드_ID_가_실제_가젯_ID_로_바뀐다() {
        HomeLayoutView saved = service.get(THEME);
        List<String> order = saved.layout().mobileOrder();
        assertThat(order).hasSize(12).noneMatch(id -> id.startsWith(HomeLayoutDefaults.SEED_PREFIX));
        assertThat(saved.layout().items().stream().map(HomeLayoutItem::gadgetId).toList()).containsAll(order);
        assertThat(order.get(0)).isEqualTo(idOf(saved, "뉴스", false));
        assertThat(order.get(3)).isEqualTo(idOf(saved, "함께 보는 글", true));
        String stored = jdbc.queryForObject("SELECT mobile_order::text FROM home_layouts WHERE theme = ?", String.class, THEME);
        assertThat(stored).contains(order.get(0), order.get(11));
    }

    @Test
    void 배치에_없는_ID_와_겹친_ID_는_버리고_새_가젯_ID_는_실제_ID_로() {
        HomeLayoutView before = service.get(THEME);
        List<HomeLayoutGadgetView> gadgets = new ArrayList<>(before.gadgets());
        gadgets.add(new HomeLayoutGadgetView("new-ad", "AD", "새 광고", true, true, null, null));
        int columns = before.layout().columns();
        int bottom = before.layout().items().stream().mapToInt(it -> it.y() + it.h()).max().orElse(0);
        List<HomeLayoutItem> items = new ArrayList<>(before.layout().items());
        items.add(new HomeLayoutItem("new-ad", 0, bottom, GadgetKinds.minW("AD", columns), GadgetKinds.minH("AD")));
        String news = idOf(before, "뉴스", false);
        String popular = idOf(before, "인기글", false);

        HomeLayoutView saved = service.save(root, THEME, request(gadgets, new HomeGridLayout(columns, items),
                List.of("new-ad", news, "없는-가젯", news, " " + popular + " ", "999999999999999999")));

        String adId = saved.gadgets().stream().filter(g -> "새 광고".equals(g.title())).map(HomeLayoutGadgetView::id).findFirst().orElseThrow();
        assertThat(adId).doesNotStartWith("new-");
        assertThat(saved.layout().mobileOrder()).containsExactly(adId, news, popular);
        assertThat(service.get(THEME).layout().mobileOrder()).containsExactly(adId, news, popular);
    }

    @Test
    void 빈_목록이나_보내지_않으면_null_PC_배치_따르기() {
        HomeLayoutView before = service.get(THEME);
        HomeLayoutView empty = service.save(root, THEME, request(before.gadgets(), before.layout(), List.of()));
        assertThat(empty.layout().mobileOrder()).isNull();
        assertThat(jdbc.queryForObject("SELECT mobile_order IS NULL FROM home_layouts WHERE theme = ?", Boolean.class, THEME)).isTrue();

        HomeLayoutView onlyUnknown = service.save(root, THEME, request(before.gadgets(), before.layout(), List.of("없는-가젯")));
        assertThat(onlyUnknown.layout().mobileOrder()).isNull();

        HomeLayoutView none = service.save(root, THEME, request(before.gadgets(), before.layout(), null));
        assertThat(none.layout().mobileOrder()).isNull();
    }

    @Test
    void 가젯을_빼면_모바일_순서에서도_빠진다() {
        HomeLayoutView before = service.get(THEME);
        String strip = idOf(before, "띠 배너", false);
        List<HomeLayoutGadgetView> gadgets = before.gadgets().stream().filter(g -> !g.id().equals(strip)).toList();
        List<HomeLayoutItem> items = before.layout().items().stream().filter(it -> !it.gadgetId().equals(strip)).toList();

        HomeLayoutView saved = service.save(root, THEME, request(gadgets, new HomeGridLayout(before.layout().columns(), items), before.layout().mobileOrder()));

        assertThat(saved.layout().mobileOrder()).hasSize(11).doesNotContain(strip);
    }
}
