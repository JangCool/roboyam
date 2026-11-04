package com.community.dashboard.service.gadget;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardPopularPagesView;
import com.community.dashboard.repository.master.DashboardAnalyticsStore;
import com.community.dashboard.repository.master.DashboardAnalyticsStore.Name;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 인기 페이지(site.read · 수집 항목 {@code rawEvents}) — 오늘의 페이지별 일별 집계({@code analytics_page_daily})에서 조회 많은 차례 limit 건. 이름은 <b>그 상위 limit 건만</b> 붙인다:
 * 글({@code /posts/<id>}) · 공구({@code /group-buys/<id>}) · 소모임({@code /groups/<id>}) · 포인트 상점 품목({@code /point-shop/<id>})은 표마다 PK 로 한 번씩,
 * 게시판({@code /boards/<slug>})은 게시판 사본. 그 밖(홈·검색·목록 화면 등)은 null — 화면이 화면 종류 이름을 보인다.
 * 화면 종류({@code kind})는 브라우저가 보낸 값 그대로다(핫딜 게시판 글은 POST·BOARD 로 온다 — 서버에서 다시 가르지 않는다).
 */
@Component
public class PopularPagesGadget implements DashboardGadgetProvider {

    private static final Pattern POST = Pattern.compile("^/posts/(\\d{1,18})(?:/.*)?$");
    private static final Pattern GROUP_BUY = Pattern.compile("^/group-buys/(\\d{1,18})(?:/.*)?$");
    private static final Pattern GROUP = Pattern.compile("^/groups/(\\d{1,18})(?:/.*)?$");
    private static final Pattern POINT_SHOP = Pattern.compile("^/point-shop/(\\d{1,18})$");
    private static final Pattern BOARD = Pattern.compile("^/boards/([^/]+)/?$");

    private final DashboardAnalyticsStore store;
    private final BoardCatalog catalog;

    public PopularPagesGadget(DashboardAnalyticsStore store, BoardCatalog catalog) {
        this.store = store;
        this.catalog = catalog;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.POPULAR_PAGES;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        List<DashboardAnalyticsStore.Page> pages = store.topPages(query.today(), query.limit());
        Set<Long> posts = new LinkedHashSet<>();
        Set<Long> groupBuys = new LinkedHashSet<>();
        Set<Long> groups = new LinkedHashSet<>();
        Set<Long> items = new LinkedHashSet<>();
        for (DashboardAnalyticsStore.Page p : pages) {
            collect(POST, p.path(), posts);
            collect(GROUP_BUY, p.path(), groupBuys);
            collect(GROUP, p.path(), groups);
            collect(POINT_SHOP, p.path(), items);
        }
        Map<Long, Name> postNames = store.postTitles(posts);
        Map<Long, Name> groupBuyNames = store.groupBuyTitles(groupBuys);
        Map<Long, Name> groupNames = store.groupNames(groups);
        Map<Long, Name> itemNames = store.pointShopNames(items);
        BoardCatalogSnapshot boards = catalog.snapshot();
        List<DashboardPopularPagesView.Item> out = new ArrayList<>(pages.size());
        for (DashboardAnalyticsStore.Page p : pages) {
            Name name = find(POST, p.path(), postNames);
            if (name == null) {
                name = find(GROUP_BUY, p.path(), groupBuyNames);
            }
            if (name == null) {
                name = find(GROUP, p.path(), groupNames);
            }
            if (name == null) {
                name = find(POINT_SHOP, p.path(), itemNames);
            }
            if (name == null) {
                Matcher m = BOARD.matcher(p.path());
                if (m.matches()) {
                    EffectiveBoard b = boards.board(m.group(1)).orElse(null);
                    name = b == null ? null : new Name(b.name(), b.nameEn());
                }
            }
            out.add(new DashboardPopularPagesView.Item(p.path(), p.kind(), name == null ? null : name.name(), name == null ? null : name.nameEn(), p.views()));
        }
        return new DashboardPopularPagesView(out);
    }

    private static void collect(Pattern pattern, String path, Set<Long> into) {
        Long id = id(pattern, path);
        if (id != null) {
            into.add(id);
        }
    }

    private static Name find(Pattern pattern, String path, Map<Long, Name> names) {
        Long id = id(pattern, path);
        return id == null ? null : names.get(id);
    }

    private static Long id(Pattern pattern, String path) {
        Matcher m = pattern.matcher(path);
        return m.matches() ? Long.parseLong(m.group(1)) : null;
    }
}
