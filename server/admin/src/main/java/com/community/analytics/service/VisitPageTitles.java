package com.community.analytics.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.dashboard.repository.master.DashboardAnalyticsStore;
import com.community.dashboard.repository.master.DashboardAnalyticsStore.Name;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 방문 경로의 이름 붙이기 — 대시보드 「인기 페이지」({@code PopularPagesGadget})와 「방문자 통계」가 같이 쓴다. <b>돌려줄 줄의 경로만</b> 받아 표마다 PK 로 한 번씩 찾는다:
 * 글({@code /posts/<id>}) · 공구({@code /group-buys/<id>}) · 소모임({@code /groups/<id>}) · 포인트 상점 품목({@code /point-shop/<id>}) — 메인 DB,
 * 게시판({@code /boards/<slug>}) — 게시판 사본(메모리). 그 밖(홈·검색·목록 화면 등)은 이름 없음(화면이 경로·화면 종류 이름을 보인다).
 * 3단계부터 수집 주소는 라우트 기준 묶음이라 {@code {id}} 가 든 주소·{@code /{unknown}} 은 찾지 않고 이름 없음이다({@link #grouped}) — 옛 원본(실시간 30분)·옛 집계 줄의 번호 주소만 찾는다.
 */
@Component
public class VisitPageTitles {

    private static final Pattern POST = Pattern.compile("^/posts/(\\d{1,18})(?:/.*)?$");
    private static final Pattern GROUP_BUY = Pattern.compile("^/group-buys/(\\d{1,18})(?:/.*)?$");
    private static final Pattern GROUP = Pattern.compile("^/groups/(\\d{1,18})(?:/.*)?$");
    private static final Pattern POINT_SHOP = Pattern.compile("^/point-shop/(\\d{1,18})$");
    private static final Pattern BOARD = Pattern.compile("^/boards/([^/]+)/?$");

    private final DashboardAnalyticsStore store;
    private final BoardCatalog catalog;

    public VisitPageTitles(DashboardAnalyticsStore store, BoardCatalog catalog) {
        this.store = store;
        this.catalog = catalog;
    }

    /** 경로 → 이름(모르는 경로는 맵에 없다). null·빈 경로는 건너뛴다 */
    public Map<String, Name> resolve(Collection<String> paths) {
        Set<Long> posts = new LinkedHashSet<>();
        Set<Long> groupBuys = new LinkedHashSet<>();
        Set<Long> groups = new LinkedHashSet<>();
        Set<Long> items = new LinkedHashSet<>();
        for (String p : paths) {
            if (p == null || p.isEmpty() || grouped(p)) {
                continue;
            }
            collect(POST, p, posts);
            collect(GROUP_BUY, p, groupBuys);
            collect(GROUP, p, groups);
            collect(POINT_SHOP, p, items);
        }
        Map<Long, Name> postNames = store.postTitles(posts);
        Map<Long, Name> groupBuyNames = store.groupBuyTitles(groupBuys);
        Map<Long, Name> groupNames = store.groupNames(groups);
        Map<Long, Name> itemNames = store.pointShopNames(items);
        BoardCatalogSnapshot boards = null;
        Map<String, Name> out = new HashMap<>();
        for (String p : paths) {
            if (p == null || p.isEmpty() || grouped(p) || out.containsKey(p)) {
                continue;
            }
            Name name = find(POST, p, postNames);
            if (name == null) {
                name = find(GROUP_BUY, p, groupBuyNames);
            }
            if (name == null) {
                name = find(GROUP, p, groupNames);
            }
            if (name == null) {
                name = find(POINT_SHOP, p, itemNames);
            }
            if (name == null) {
                Matcher m = BOARD.matcher(p);
                if (m.matches()) {
                    if (boards == null) {
                        boards = catalog.snapshot();
                    }
                    EffectiveBoard b = boards.board(m.group(1)).orElse(null);
                    name = b == null ? null : new Name(b.name(), b.nameEn());
                }
            }
            if (name != null) {
                out.put(p, name);
            }
        }
        return out;
    }

    /**
     * 이름에 {@code q} 가 든 게시판의 주소({@code /boards/<slug>}) — 페이지 순위의 「경로·제목 일부」 거르기가 경로 일부와 함께 쓴다(게시판 사본, DB 없음).
     * 글·공구 제목으로는 거르지 않는다(메인 DB 의 큰 표를 글자 일부로 훑어야 해서).
     */
    public List<String> boardPathsMatching(String q) {
        String needle = q.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (EffectiveBoard b : catalog.snapshot().boards()) {
            if (contains(b.name(), needle) || contains(b.nameEn(), needle)) {
                out.add("/boards/" + b.slug());
            }
        }
        return out;
    }

    /**
     * 라우트 기준으로 묶은 주소인가(3단계 — {@code /posts/{id}} 처럼 번호 자리가 {@code {id}} 인 주소 · 「알 수 없는 주소」 {@code /{unknown}}) — 한 글·한 품목이 아니라 이름이
     * 없다({@code title}·{@code titleEn} null, 화면이 화면 종류 이름을 보인다). 공개 사이트 수집기 {@code VisitRoutes} 의 {@code ID}·{@code UNKNOWN} 과 같은 글자.
     */
    static boolean grouped(String path) {
        return path.contains("{id}") || path.equals("/{unknown}");
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
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
