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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 광고 가젯 종류 {@code AD}·{@code AD_TEXT_LINE}(ad-contract 「공개 노출 목업」 확정 2026-11-01) — 관리자 홈 구성 저장이 받고, ⚙ 설정 {@code AD.adType}(배너 넷, 처음 WIDE_BANNER) ·
 * {@code AD_TEXT_LINE.count}(1~5, 처음 3), 범위 밖이면 {@code gadgets[i].config.<칸>} 400. 처음 꾸미기는 테마 기본 위에 머리 숨김 · 비면 접기 · 배너는 평평.
 * 다른 시험(네이버식)과 겹치지 않게 다음식 테마의 홈에서 시험한다.
 */
class HomeLayoutAdGadgetTest extends CommerceAdminTestBase {

    private static final String THEME = "daum";

    @Autowired
    private HomeLayoutAdminService service;

    private AdminActor root;

    @BeforeEach
    void setUp() {
        long rootId = rootId();
        root = actor(rootId);
        // SQL 로 남김: ON CONFLICT upsert — 권한·내장 역할 사본을 없을 때만 넣는다
        jdbc.update("INSERT INTO site_settings (id, site_name, public_url, updated_by) VALUES (1, '시험', 'http://localhost', ?) ON CONFLICT (id) DO NOTHING", rootId);
        var defaults = service.defaults(THEME);
        service.save(root, THEME, request(defaults.gadgets(), defaults.layout(), Map.of()));
    }

    private static HomeLayoutSaveRequest request(List<HomeLayoutGadgetView> gadgets, HomeGridLayout layout, Map<String, JsonNode> configs) {
        List<HomeLayoutSaveRequest.Item> items = layout.items().stream().map(it -> new HomeLayoutSaveRequest.Item(it.gadgetId(), it.x(), it.y(), it.w(), it.h())).toList();
        return new HomeLayoutSaveRequest(new HomeLayoutSaveRequest.Layout(layout.columns(), items), gadgets.stream()
                .map(g -> new HomeLayoutSaveRequest.Gadget(g.id(), g.type(), g.id().startsWith("new-") ? g.title() : null, null, null, null, configs.get(g.id())))
                .toList());
    }

    /** 지금 배치 맨 아래에 광고 가젯 둘(배너 · 한 줄)을 더한 요청 */
    private HomeLayoutSaveRequest withAds(HomeLayoutView before, JsonNode adConfig, JsonNode textConfig) {
        List<HomeLayoutGadgetView> gadgets = new ArrayList<>(before.gadgets());
        gadgets.add(new HomeLayoutGadgetView("new-ad", "AD", "광고 배너", true, true, null, null));
        gadgets.add(new HomeLayoutGadgetView("new-text", "AD_TEXT_LINE", "한 줄 광고", true, true, null, null));
        int columns = before.layout().columns();
        int bottom = before.layout().items().stream().mapToInt(it -> it.y() + it.h()).max().orElse(0);
        List<HomeLayoutItem> items = new ArrayList<>(before.layout().items());
        items.add(new HomeLayoutItem("new-ad", 0, bottom, HomeGadgetKinds.minW("AD", columns), HomeGadgetKinds.minH("AD")));
        items.add(new HomeLayoutItem("new-text", 0, bottom + 10, HomeGadgetKinds.minW("AD_TEXT_LINE", columns), HomeGadgetKinds.minH("AD_TEXT_LINE")));
        Map<String, JsonNode> configs = new java.util.HashMap<>();
        if (adConfig != null) {
            configs.put("new-ad", adConfig);
        }
        if (textConfig != null) {
            configs.put("new-text", textConfig);
        }
        return request(gadgets, new HomeGridLayout(columns, items), configs);
    }

    private static HomeLayoutGadgetView only(HomeLayoutView view, String type) {
        return view.gadgets().stream().filter(g -> type.equals(g.type())).reduce((a, b) -> {
            throw new AssertionError("종류가 둘: " + type);
        }).orElseThrow();
    }

    private static ObjectNode config(String field, Object value) {
        ObjectNode c = HomeLayoutJson.object();
        if (value instanceof Integer n) {
            c.put(field, n);
        } else {
            c.put(field, (String) value);
        }
        return c;
    }

    @Test
    void 광고_가젯_둘을_저장하면_처음_설정과_머리_숨김_비면_접기_배너는_평평() {
        HomeLayoutView saved = service.save(root, THEME, withAds(service.get(THEME), null, null));

        HomeLayoutGadgetView ad = only(saved, "AD");
        HomeLayoutGadgetView text = only(saved, "AD_TEXT_LINE");
        assertThat(ad.id()).doesNotStartWith("new-");
        assertThat(ad.config().propertyNames()).containsExactly("adType");
        assertThat(ad.config().path("adType").asString()).isEqualTo("WIDE_BANNER");
        assertThat(text.config().propertyNames()).containsExactly("count");
        assertThat(text.config().path("count").asInt()).isEqualTo(3);
        // 다음식 테마 기본(막대 머리 · 카드)을 광고 종류의 처음 꾸미기가 덮는다
        assertThat(ad.style().path("headerStyle").asString()).isEqualTo("HIDDEN");
        assertThat(ad.style().path("cardLook").asString()).isEqualTo("FLAT");
        assertThat(ad.style().path("empty").path("keep").asBoolean()).isFalse();
        assertThat(text.style().path("headerStyle").asString()).isEqualTo("HIDDEN");
        assertThat(text.style().path("cardLook").asString()).isEqualTo("DEFAULT");
        assertThat(text.style().path("empty").path("keep").asBoolean()).isFalse();
        // 저장 행의 꾸미기도 같은 처음 값(공개 응답이 그대로 싣는다)
        String stored = jdbc.queryForObject("SELECT style::text FROM home_gadgets WHERE id = ?", String.class, Long.parseLong(ad.id()));
        assertThat(stored).contains("\"HIDDEN\"", "\"FLAT\"");
        // 다른 종류의 처음 꾸미기는 그대로 — 다음식 기본은 막대 머리
        assertThat(HomeGadgetKinds.baseStyle(THEME, "POST_LIST").path("headerStyle").asString()).isEqualTo("BAR");
        assertThat(HomeGadgetKinds.baseStyle(THEME, "POST_LIST").path("empty").path("keep").asBoolean()).isTrue();
    }

    @Test
    void 배너_유형과_줄_수를_저장하고_범위_밖이면_칸_경로로_400() {
        HomeLayoutView saved = service.save(root, THEME, withAds(service.get(THEME), config("adType", "SQUARE_BANNER"), config("count", 5)));
        assertThat(only(saved, "AD").config().path("adType").asString()).isEqualTo("SQUARE_BANNER");
        assertThat(only(saved, "AD_TEXT_LINE").config().path("count").asInt()).isEqualTo(5);

        int adIndex = saved.gadgets().indexOf(only(saved, "AD"));
        int textIndex = saved.gadgets().indexOf(only(saved, "AD_TEXT_LINE"));
        String adId = only(saved, "AD").id();
        String textId = only(saved, "AD_TEXT_LINE").id();
        // 날개 배너는 가젯에 없다 · 모르는 유형 · 줄 수 0 / 6
        for (Object bad : List.of("WING_BANNER", "TEXT_LINE", 3)) {
            assertThatThrownBy(() -> service.save(root, THEME, request(saved.gadgets(), saved.layout(), Map.of(adId, config("adType", bad)))))
                    .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsOnlyKeys("gadgets[" + adIndex + "].config.adType"));
        }
        for (int bad : new int[] {0, 6}) {
            assertThatThrownBy(() -> service.save(root, THEME, request(saved.gadgets(), saved.layout(), Map.of(textId, config("count", bad)))))
                    .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsOnlyKeys("gadgets[" + textIndex + "].config.count"));
        }
        assertThat(only(service.get(THEME), "AD").config().path("adType").asString()).isEqualTo("SQUARE_BANNER");
        assertThat(only(service.get(THEME), "AD_TEXT_LINE").config().path("count").asInt()).isEqualTo(5);
    }

    @Test
    void 모르는_종류는_여전히_400_fields_gadgets_type() {
        HomeLayoutView before = service.get(THEME);
        List<HomeLayoutGadgetView> gadgets = new ArrayList<>(before.gadgets());
        gadgets.add(new HomeLayoutGadgetView("new-x", "AD_WING", "날개", true, true, null, null));
        List<HomeLayoutItem> items = new ArrayList<>(before.layout().items());
        items.add(new HomeLayoutItem("new-x", 0, 1500, 4, 4));
        int index = gadgets.size() - 1;
        assertThatThrownBy(() -> service.save(root, THEME, request(gadgets, new HomeGridLayout(before.layout().columns(), items), Map.of())))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("gadgets[" + index + "].type"));
    }
}
