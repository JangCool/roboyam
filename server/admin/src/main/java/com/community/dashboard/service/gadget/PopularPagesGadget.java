package com.community.dashboard.service.gadget;

import com.community.analytics.service.VisitPageTitles;
import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardPopularPagesView;
import com.community.dashboard.repository.master.DashboardAnalyticsStore;
import com.community.dashboard.repository.master.DashboardAnalyticsStore.Name;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 인기 페이지(site.read · 수집 항목 {@code rawEvents}) — 오늘의 페이지별 일별 집계({@code analytics_page_daily})에서 조회 많은 차례 limit 건. 이름은 <b>그 상위 limit 건만</b> 붙인다
 * ({@link VisitPageTitles} — 글·공구·소모임·포인트 상점 품목은 표마다 PK 로 한 번씩, 게시판은 게시판 사본, 그 밖은 null — 화면이 화면 종류 이름을 보인다).
 * 화면 종류({@code kind})는 브라우저가 보낸 값 그대로다(핫딜 게시판 글은 POST·BOARD 로 온다 — 서버에서 다시 가르지 않는다).
 */
@Component
public class PopularPagesGadget implements DashboardGadgetProvider {

    private final DashboardAnalyticsStore store;
    private final VisitPageTitles titles;

    public PopularPagesGadget(DashboardAnalyticsStore store, VisitPageTitles titles) {
        this.store = store;
        this.titles = titles;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.POPULAR_PAGES;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        List<DashboardAnalyticsStore.Page> pages = store.topPages(query.today(), query.limit());
        Map<String, Name> names = titles.resolve(pages.stream().map(DashboardAnalyticsStore.Page::path).toList());
        List<DashboardPopularPagesView.Item> out = new ArrayList<>(pages.size());
        for (DashboardAnalyticsStore.Page p : pages) {
            Name name = names.get(p.path());
            out.add(new DashboardPopularPagesView.Item(p.path(), p.kind(), name == null ? null : name.name(), name == null ? null : name.nameEn(), p.views()));
        }
        return new DashboardPopularPagesView(out);
    }
}
