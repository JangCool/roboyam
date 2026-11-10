package com.community.home.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.CommerceApiTestBase;
import com.community.home.dto.response.GadgetPageResponse;
import com.community.home.dto.response.GadgetView;
import com.community.site.service.SiteThemes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 공개 응답의 광고 가젯({@code AD}·{@code AD_TEXT_LINE}) — {@code data: null}·{@code empty: false}(화면이 {@code GET /api/ads} 로 채운다), 꾸미기·설정은 저장 값 그대로.
 * 다른 시험의 홈 배치를 건드리지 않게 이 시험만의 가젯 페이지(테마 넷 모두에 배치)를 만들어 읽는다.
 */
class GadgetAdTest extends CommerceApiTestBase {

    @Autowired
    private GadgetService service;

    @Test
    void 광고_가젯은_data_null_empty_false_로_설정과_꾸미기를_그대로_싣는다() {
        long page = ids.nextId();
        String key = "adtest" + Long.toString(page, 36).substring(Long.toString(page, 36).length() - 8);
        jdbc.update("INSERT INTO gadget_pages (id, page_key, name) VALUES (?, ?, '광고 시험')", page, key);
        for (String theme : SiteThemes.IDS) {
            long ad = ids.nextId();
            long text = ids.nextId();
            jdbc.update("INSERT INTO home_gadgets (id, theme, type, title, style, config, page_id) VALUES (?, ?, 'AD', '광고 배너', "
                    + "'{\"headerStyle\":\"HIDDEN\",\"cardLook\":\"FLAT\",\"empty\":{\"keep\":false}}'::jsonb, '{\"adType\":\"STRIP_BANNER\"}'::jsonb, ?)", ad, theme, page);
            jdbc.update("INSERT INTO home_gadgets (id, theme, type, title, style, config, page_id) VALUES (?, ?, 'AD_TEXT_LINE', '한 줄 광고', "
                    + "'{\"headerStyle\":\"HIDDEN\"}'::jsonb, '{\"count\":4}'::jsonb, ?)", text, theme, page);
            jdbc.update("INSERT INTO gadget_page_layouts (page_id, theme, columns, items) VALUES (?, ?, 12, ?::jsonb)", page, theme,
                    "[{\"gadgetId\":\"" + ad + "\",\"x\":0,\"y\":0,\"w\":12,\"h\":4},{\"gadgetId\":\"" + text + "\",\"x\":0,\"y\":4,\"w\":6,\"h\":6}]");
        }

        GadgetPageResponse res = service.page(key, null);

        assertThat(res.gadgets()).extracting(GadgetView::type).containsExactly("AD", "AD_TEXT_LINE");
        assertThat(res.gadgets()).allSatisfy(g -> {
            assertThat(g.data()).isNull();
            assertThat(g.empty()).isFalse();
            assertThat(g.style().path("headerStyle").asString()).isEqualTo("HIDDEN");
        });
        assertThat(res.gadgets().get(0).config().path("adType").asString()).isEqualTo("STRIP_BANNER");
        assertThat(res.gadgets().get(0).style().path("cardLook").asString()).isEqualTo("FLAT");
        assertThat(res.gadgets().get(1).config().path("count").asInt()).isEqualTo(4);
    }
}
