package com.community.home.service;

import com.community.cache.definition.CacheCatalog;
import com.community.cache.definition.CacheNames;
import com.community.group.dto.response.GroupRowView;
import com.community.zconfiguration.crypto.Digests;
import org.springframework.cache.CacheManager;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.entity.BoardGadget;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.group.service.GroupQueryService;
import com.community.groupbuy.service.GroupBuyMemberService;
import com.community.groupbuy.service.GroupBuySalesSwitch;
import com.community.highlight.service.HighlightPicks;
import com.community.home.dto.HomeGadgetSpec;
import com.community.home.dto.HomeGridLayout;
import com.community.board.service.PublicBoardService;
import com.community.home.dto.response.BoardLayoutResponse;
import com.community.home.dto.response.GadgetPageResponse;
import com.community.home.dto.response.HomeGadgetView;
import com.community.home.dto.response.HomeGadgetsResponse;
import com.community.home.dto.response.gadget.BoardCardView;
import com.community.home.dto.response.gadget.BoardCardsDataView;
import com.community.home.dto.response.gadget.GalleryItemView;
import com.community.home.dto.response.gadget.GroupBuyDataView;
import com.community.home.dto.response.gadget.HighlightDataView;
import com.community.home.dto.response.gadget.HotDealsDataView;
import com.community.hotdeal.repository.master.DealQueries;
import com.community.hotdeal.service.DealSettingsReader;
import com.community.hotdeal.service.DealWriter;
import com.community.home.dto.response.gadget.HighlightPickView;
import com.community.home.dto.response.gadget.ItemsDataView;
import com.community.home.dto.response.gadget.NoticeTextDataView;
import com.community.home.dto.response.gadget.PostListDataView;
import com.community.home.entity.BoardLayoutEntity;
import com.community.home.entity.GadgetPageEntity;
import com.community.home.entity.GadgetPageLayoutEntity;
import com.community.home.entity.HomeGadgetEntity;
import com.community.home.entity.HomeLayoutEntity;
import com.community.home.exception.GadgetPageNotFoundException;
import com.community.home.repository.master.BoardLayoutRepository;
import com.community.home.repository.master.GadgetPageLayoutRepository;
import com.community.home.repository.master.GadgetPageRepository;
import com.community.home.repository.master.HomeGadgetRepository;
import com.community.home.repository.master.HomeLayoutRepository;
import com.community.post.dto.PostRowData;
import com.community.post.dto.response.PostFeedRowView;
import com.community.post.repository.master.PostFeedRepositoryQDSL;
import com.community.post.service.AdultContentGate;
import com.community.post.service.PostFeedRows;
import com.community.site.service.PublicSiteService;
import com.community.site.service.SiteThemes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * 공개 홈 가젯({@code GET /api/home/gadgets}) — 지금 사이트 테마의 홈 구성(저장한 것, 없으면 테마 기본 배치 {@link HomeLayoutDefaults})에 가젯마다 종류별 데이터를 붙인다. 비회원도 본다.
 *
 * <ul>
 *   <li>테마는 공개 사이트 사본({@code PublicSiteService}, 20초)의 값이다. 배치는 캐시 {@code home.layout}·{@code pages.layout}·{@code boards.layout}(기본 300초), 가젯 데이터는
 *       종류별 캐시 {@code gadget.<종류>}(항목 키 = 설정 지문)에 둔다 — 관리자 저장은 세대 번호로 몇 초 안에, 새 글은 TTL 안에 보인다(docs/features/cache-admin-contract.md).
 *       캐시에는 공용 값만 담고 보는 사람 몫은 꺼낸 뒤 덧입힌다. 관리자 테마 미리보기({@code theme})는 캐시를 거치지 않는다. 광고·본문 칸은 캐시하지 않는다.</li>
 *   <li>글 줄은 최신글·인기글 목록과 같은 거름·모양이다({@link PostFeedRows}, 로그인하면 차단·메모 표시). 가젯 설정 {@code boardIds} 는 게시판 slug 목록이고
 *       없거나 꺼진 게시판은 건너뛴다(하나도 남지 않으면 거르지 않는다).</li>
 *   <li>{@code POST_LIST}: {@code sort} LATEST(최신글과 같은 게시판 범위) · POPULAR(인기글 순위 스냅샷, {@code periodTabs} 면 세 기간을 함께) ·
 *       VIEWS·LIKES·COMMENTS(이번 주 인기글 후보를 그 수로 다시 정렬 — 전체 글을 세지 않는다). {@code count} 기본 10.</li>
 *   <li>{@code HIGHLIGHT}: 설정 {@code place}(기본 home — 없는 곳도 home, V161)의 노출 중인 하이라이트(관리자 순서, {@link HighlightPicks}), 글이 지금 보이는 것만, {@code count} 1~6(기본 5). {@code GALLERY}: 사진 있는 최신 글, {@code count} 기본 6.
 *       {@code BOARD_CARDS}: 게시판마다 최신 {@code perBoard}(기본 4)개 + 대표 글, 게시판을 정하지 않으면 켜진 일반 게시판 앞 6개. {@code GROUPS}: 지금 뜨는 소모임 {@code count} 1~6(기본 3)개(범위는 {@link GadgetConfigs#count}).</li>
 *   <li>{@code GROUP_BUY}: 모집 중인 공개 공구 {@code count}(기본 5, 1~10)개를 {@code sort} 차례로(DEADLINE 마감 이른 순 기본 · NEWEST 새로 시작한 순 · POPULAR 참여 많은 순) — 줄은 회원 공구 목록과 같은 모양
 *       ({@link GroupBuyMemberService#recruiting}), 판매 스위치는 거르지 않고 {@code salesOpen} 으로 싣는다(운영 주체가 팔 수 없는 단계면 빈 목록). {@code QUICK_LINKS}·{@code NOTICE_TEXT}: 가젯 설정의 {@code items}·{@code text}·{@code format}.</li>
 *   <li>대상 게시판 {@code boardScope}(gadget-page-contract 「가젯 설정」): {@code ALL} 전체 · {@code PICK} {@code boardIds} · {@code FOLDER} 가젯 페이지의 기준 폴더 켜진 게시판
 *       (폴더가 없거나 게시판이 없으면 전체) · {@code BOARD} 게시판 레이아웃이 그리는 게시판(그 밖에서는 전체). 칸이 없는 옛 설정은 {@code boardIds} 가 있으면 PICK.</li>
 *   <li>가젯 페이지({@code GET /api/pages/{key}}, {@link #page}): 지금 테마의 그 페이지 배치(저장한 것, 없으면 페이지의 처음 배치) + 머리(이름·설명·글쓰기 단추·글쓰기 게시판). 없는 페이지는 404.</li>
 *   <li>게시판 레이아웃({@code GET /api/boards/{slug}/layout}, {@link #boardLayout}): 그 테마의 게시판 레이아웃(저장한 것, 없으면 본문만) + 적용 화면. 본문 가젯은 {@code data: null}·{@code empty: false}.</li>
 *   <li>광고 가젯({@code AD}·{@code AD_TEXT_LINE}): {@code data: null}·{@code empty: false} — 화면이 {@code GET /api/ads} 로 후보를 채운다(ad-contract 「공개 노출」).</li>
 *   <li>노출 기간·보는 사람(로그인 전용) 설정은 아직 없다 — {@code locked} 는 늘 false.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class HomeGadgetService {

    private static final int POPULAR_POOL = 50;
    private static final int BOARD_CARDS_DEFAULT = 6;
    static final int GROUP_BUY_DEFAULT = HomeGadgetKinds.GROUP_BUY_COUNT_DEFAULT;
    static final int GROUP_BUY_MAX = HomeGadgetKinds.GROUP_BUY_COUNT_MAX;
    private static final List<String> PERIODS = List.of("REALTIME", "YESTERDAY", "WEEK");

    private final PublicSiteService site;
    private final HomeLayoutRepository layouts;
    private final HomeGadgetRepository gadgets;
    private final BoardCatalog catalog;
    private final PostFeedRepositoryQDSL feeds;
    private final PostFeedRows rows;
    private final PopularService popular;
    private final HighlightPicks highlightPicks;
    private final GroupQueryService groups;
    private final GroupBuyMemberService groupBuys;
    private final GroupBuySalesSwitch sales;
    private final GadgetPageRepository pages;
    private final GadgetPageLayoutRepository pageLayouts;
    private final GadgetPageFolders folders;
    private final BoardLayoutRepository boardLayouts;
    private final DealQueries dealQueries;
    private final DealSettingsReader dealSettings;
    private final AdultContentGate adultGate;
    private final CacheManager caches;

    public HomeGadgetService(PublicSiteService site, HomeLayoutRepository layouts, HomeGadgetRepository gadgets, BoardCatalog catalog,
                             PostFeedRepositoryQDSL feeds, PostFeedRows rows, PopularService popular, HighlightPicks highlightPicks,
                             GroupQueryService groups, GroupBuyMemberService groupBuys, GroupBuySalesSwitch sales,
                             GadgetPageRepository pages, GadgetPageLayoutRepository pageLayouts, GadgetPageFolders folders,
                             BoardLayoutRepository boardLayouts, DealQueries dealQueries, DealSettingsReader dealSettings, AdultContentGate adultGate,
                             CacheManager caches) {
        this.caches = caches;
        this.adultGate = adultGate;
        this.dealQueries = dealQueries;
        this.dealSettings = dealSettings;
        this.boardLayouts = boardLayouts;
        this.pages = pages;
        this.pageLayouts = pageLayouts;
        this.folders = folders;
        this.groupBuys = groupBuys;
        this.sales = sales;
        this.site = site;
        this.layouts = layouts;
        this.gadgets = gadgets;
        this.catalog = catalog;
        this.feeds = feeds;
        this.rows = rows;
        this.popular = popular;
        this.highlightPicks = highlightPicks;
        this.groups = groups;
    }

    /**
     * @param requestedTheme 관리자 미리보기가 보는 테마(아니면 null) — 아는 테마면 그 테마의 구성, 아니면 사이트 테마
     * @param viewerId 로그인한 회원(비로그인 null) — 글 줄의 차단·메모, 소모임 줄의 내 상태
     */
    public HomeGadgetsResponse gadgets(String requestedTheme, Long viewerId) {
        // 관리자 미리보기(theme)는 캐시를 거치지 않는다 — 저장한 배치를 바로 보여야 하고, 사이트 테마가 아닌 테마의 항목을 캐시에 남기지 않는다
        boolean preview = SiteThemes.isKnown(requestedTheme);
        String theme = preview ? requestedTheme : site.snapshot().info().theme();
        Layout home = preview ? homeLayout(theme) : caches.getCache(CacheNames.HOME_LAYOUT).get(theme, () -> homeLayout(theme));
        Ctx ctx = new Ctx(catalog.snapshot(), Instant.now(), viewerId, adultGate.adult(viewerId), null, null, !preview);
        return new HomeGadgetsResponse(theme, home.layout(), home.specs().stream().map(spec -> view(spec, ctx)).toList());
    }

    /** 홈 배치(가젯 구성·위치·설정) — 저장한 것, 없으면 테마 기본 배치. 캐시 {@code home.layout}(항목 키 = 테마) */
    private Layout homeLayout(String theme) {
        HomeLayoutEntity row = layouts.findById(theme).orElse(null);
        if (row == null) {
            HomeLayoutDefaults.Home home = HomeLayoutDefaults.of(theme);
            return new Layout(home.layout(), List.copyOf(home.gadgets()));
        }
        return new Layout(row.toLayout(), gadgets.findByThemeAndPageIdIsNullAndBoardLayoutFalseOrderByIdAsc(theme).stream().map(HomeGadgetEntity::toSpec).toList());
    }

    /**
     * 가젯 페이지 하나 — 지금 테마의 배치(저장한 것, 없으면 처음 배치) + 가젯마다 데이터 + 머리. 대상 게시판 {@code FOLDER} 는 기준 폴더의 켜진 게시판이다.
     * @param key      영문 주소
     * @param viewerId 로그인한 회원(비로그인 null)
     */
    public GadgetPageResponse page(String key, Long viewerId) {
        if (key == null) {
            throw new GadgetPageNotFoundException();
        }
        String theme = site.snapshot().info().theme();
        String pageKey = key.strip();
        // 캐시 pages.layout(항목 키 = 페이지 주소 · 테마) — 없는 페이지도 「없음」으로 담는다(같은 주소를 거듭 물어도 TTL 안에서는 DB 를 읽지 않는다)
        PageLayout page = caches.getCache(CacheNames.PAGES_LAYOUT).get(pageKey + "|" + theme, () -> pageLayout(pageKey, theme));
        if (page == null) {
            throw new GadgetPageNotFoundException();
        }
        BoardCatalogSnapshot snapshot = catalog.snapshot();
        Set<String> activeFolder = new java.util.LinkedHashSet<>();
        for (String slug : page.folderSlugs()) {
            snapshot.board(slug).filter(EffectiveBoard::active).ifPresent(b -> activeFolder.add(b.slug()));
        }
        Ctx ctx = new Ctx(snapshot, Instant.now(), viewerId, adultGate.adult(viewerId), activeFolder.isEmpty() ? null : activeFolder, null, true);
        String writeBoard = activeFolder.isEmpty() ? null : activeFolder.iterator().next();
        GadgetPageResponse.Page head = new GadgetPageResponse.Page(page.pageKey(), page.name(), page.nameEn(), page.description(), page.writeButton(), writeBoard);
        return new GadgetPageResponse(head, theme, page.layout(), page.specs().stream().map(spec -> view(spec, ctx)).toList());
    }

    /** 가젯 페이지 하나의 배치·머리·기준 폴더 게시판(켜짐 판정 전) — 없는 페이지는 null */
    private PageLayout pageLayout(String pageKey, String theme) {
        GadgetPageEntity page = pages.findByPageKey(pageKey).orElse(null);
        if (page == null) {
            return null;
        }
        GadgetPageLayoutEntity row = pageLayouts.findByPageIdAndTheme(page.getId(), theme).orElse(null);
        HomeGridLayout layout;
        List<HomeGadgetSpec> specs;
        if (row == null) {
            HomeLayoutDefaults.Home initial = GadgetPageInitial.read(page.getInitialLayout(), theme);
            layout = initial.layout();
            specs = List.copyOf(initial.gadgets());
        } else {
            layout = row.toLayout();
            specs = gadgets.findByPageIdAndThemeOrderByIdAsc(page.getId(), theme).stream().map(HomeGadgetEntity::toSpec).toList();
        }
        List<String> folderSlugs = List.copyOf(folders.of(page.getFolderId()).map(GadgetPageFolders.Folder::activeSlugs).orElse(List.of()));
        return new PageLayout(page.getPageKey(), page.getName(), page.getNameEn(), page.getDescription(), page.isWriteButton(), layout, specs, folderSlugs);
    }

    /**
     * 게시판 레이아웃 — 그 테마의 게시판 레이아웃(저장한 것, 없으면 기본 배치 — 본문만) + 가젯마다 데이터 + 적용 화면(board-layout-contract).
     * 본문 가젯({@code BOARD_BODY})은 {@code data: null}·{@code empty: false}, 대상 게시판 {@code BOARD} 는 이 게시판이다. 없는·꺼진 게시판은 게시판 조회와 같은 오류
     * ({@code 404 BOARD_NOT_FOUND}·{@code 403 BOARD_INACTIVE} — 소모임 게시판은 없는 게시판).
     *
     * @param requestedTheme 관리자 미리보기가 보는 테마(아니면 null) — 아는 테마면 그 테마, 아니면 사이트 테마(홈 가젯과 같은 규칙)
     * @param viewerId       로그인한 회원(비로그인 null)
     */
    public BoardLayoutResponse boardLayout(String slug, String requestedTheme, Long viewerId) {
        BoardCatalogSnapshot snapshot = catalog.snapshot();
        EffectiveBoard board = PublicBoardService.requireActive(snapshot, slug == null ? "" : slug.strip());
        // 관리자 미리보기(theme)는 캐시를 거치지 않는다(홈 가젯과 같은 규칙)
        boolean preview = SiteThemes.isKnown(requestedTheme);
        String theme = preview ? requestedTheme : site.snapshot().info().theme();
        BoardLayout cached = preview ? boardLayout(theme) : caches.getCache(CacheNames.BOARDS_LAYOUT).get(theme, () -> boardLayout(theme));
        Ctx ctx = new Ctx(snapshot, Instant.now(), viewerId, adultGate.adult(viewerId), null, board.slug(), !preview);
        return new BoardLayoutResponse(theme, cached.screens(), cached.layout(), cached.specs().stream().map(spec -> view(spec, ctx)).toList());
    }

    /** 그 테마의 게시판 레이아웃(저장한 것, 없으면 기본 배치 — 본문만) + 적용 화면. 캐시 {@code boards.layout}(항목 키 = 테마 — 모든 게시판이 함께 쓴다) */
    private BoardLayout boardLayout(String theme) {
        BoardLayoutEntity row = boardLayouts.findById(theme).orElse(null);
        if (row == null) {
            BoardLayoutDefaults.Board defaults = BoardLayoutDefaults.of(theme);
            return new BoardLayout(new BoardLayoutResponse.Screens(defaults.screens().list(), defaults.screens().post()), defaults.home().layout(),
                    List.copyOf(defaults.home().gadgets()));
        }
        return new BoardLayout(new BoardLayoutResponse.Screens(row.isListEnabled(), row.isPostEnabled()), row.toLayout(),
                gadgets.findByThemeAndBoardLayoutTrueOrderByIdAsc(theme).stream().map(HomeGadgetEntity::toSpec).toList());
    }

    /**
     * 가젯 하나 — 가젯 데이터 캐시를 두는 종류({@code gadget.<종류>}, 항목 키 = 가젯 설정 지문)면 공용 데이터(보는 사람 없이 만든 것)를 캐시에서 꺼내고, 아니면(미리보기 · 캐시하지 않는 종류)
     * 그 자리에서 만든다. 어느 쪽이든 보는 사람 몫(글 줄의 차단·메모·딜의 내 종료 요청, 소모임의 내 상태)은 꺼낸 뒤 덧입히고, 공동구매 판매 스위치는 요청마다 지금 값이다.
     */
    private HomeGadgetView view(HomeGadgetSpec g, Ctx ctx) {
        Ctx shared = ctx.shared();
        GadgetData built = ctx.cached() && CacheCatalog.cachedGadget(g.type())
                ? caches.getCache(CacheNames.gadget(g.type())).get(gadgetKey(g, ctx), () -> compute(g, shared))
                : compute(g, shared);
        Object data = overlay(built, ctx.viewerId());
        return new HomeGadgetView(g.id(), g.type(), g.title(), g.desktop(), g.mobile(), built.empty(), false, g.style(), g.config(), g.more(), data);
    }

    /**
     * 가젯 데이터 캐시의 항목 키 — 설정 지문(같은 설정이면 홈·가젯 페이지·게시판 레이아웃 어디에 있든 한 항목을 함께 쓴다) + 데이터를 바꾸는 요청 값:
     * 성인 여부(19금 글을 넣는가)와 대상 게시판이 쪽에 따라 정해지는 설정의 그 게시판(가젯 페이지 기준 폴더 {@code FOLDER} · 게시판 레이아웃 {@code BOARD}).
     */
    private static String gadgetKey(HomeGadgetSpec g, Ctx ctx) {
        String scope = GadgetConfigs.scope(g.config());
        String place = GadgetConfigs.FOLDER.equals(scope) ? String.valueOf(ctx.folder()) : GadgetConfigs.BOARD.equals(scope) ? String.valueOf(ctx.board()) : "";
        return Digests.sha256Hex((ctx.adult() ? "A" : "N") + "|" + place + "|" + Objects.toString(g.config()));
    }

    /** 보는 사람 몫을 덧입힌다 — 비로그인이면 그대로(공동구매 판매 스위치만 지금 값으로) */
    private Object overlay(GadgetData built, Long viewerId) {
        Object data = built.data();
        if (data instanceof GroupBuyDataView d) {
            return new GroupBuyDataView(d.items(), sales.groupBuyOpen());
        }
        if (viewerId == null) {
            return data;
        }
        return switch (data) {
            case PostListDataView d -> {
                List<PostFeedRowView> all = new ArrayList<>(d.items());
                if (d.periods() != null) {
                    d.periods().values().forEach(all::addAll);
                }
                PostFeedRows.Overlay o = rows.overlay(all, viewerId);
                Map<String, List<PostFeedRowView>> periods = null;
                if (d.periods() != null) {
                    periods = new LinkedHashMap<>();
                    for (Map.Entry<String, List<PostFeedRowView>> e : d.periods().entrySet()) {
                        periods.put(e.getKey(), o.apply(e.getValue()));
                    }
                }
                yield new PostListDataView(o.apply(d.items()), periods);
            }
            case HighlightDataView d -> {
                PostFeedRows.Overlay o = rows.overlay(d.picks().stream().map(HighlightPickView::post).toList(), viewerId);
                yield new HighlightDataView(d.picks().stream().map(p -> new HighlightPickView(p.id(), o.apply(p.post()), p.title(), p.summary(), p.imageUrl())).toList());
            }
            case BoardCardsDataView d -> {
                List<PostFeedRowView> all = new ArrayList<>();
                for (BoardCardView c : d.cards()) {
                    if (c.lead() != null) {
                        all.add(c.lead());
                    }
                    all.addAll(c.items());
                }
                PostFeedRows.Overlay o = rows.overlay(all, viewerId);
                yield new BoardCardsDataView(d.cards().stream().map(c -> new BoardCardView(c.board(), o.apply(c.lead()), o.apply(c.items()))).toList());
            }
            case HotDealsDataView d -> new HotDealsDataView(rows.forViewer(d.items(), viewerId), d.more());
            case ItemsDataView d when built.leaders() != null -> {
                @SuppressWarnings("unchecked")
                List<GroupRowView> items = (List<GroupRowView>) d.items();
                yield new ItemsDataView(groups.withMine(items, built.leaders(), viewerId));
            }
            case null, default -> data;
        };
    }

    /** 가젯 하나의 데이터(보는 사람 없이 — {@code ctx.viewerId()} 는 null) */
    private GadgetData compute(HomeGadgetSpec g, Ctx ctx) {
        JsonNode config = g.config();
        Object data;
        boolean empty;
        List<Long> leaders = null;
        switch (g.type()) {
            case HomeGadgetKinds.BOARD_BODY, HomeGadgetKinds.AD, HomeGadgetKinds.AD_TEXT_LINE -> {
                // 본문: 화면이 그 자리에 게시판 목록·글 보기를 그린다 · 광고: 화면이 GET /api/ads 로 채우고 비었는지도 화면이 정한다
                data = null;
                empty = false;
            }
            case "POST_LIST" -> {
                PostListDataView d = postList(config, ctx);
                data = d;
                // 기간 탭이면 세 기간이 모두 빌 때만 비었다(실시간만 비어도 다른 탭에 글이 있다)
                empty = d.periods() == null ? d.items().isEmpty() : d.periods().values().stream().allMatch(List::isEmpty);
            }
            case "HIGHLIGHT" -> {
                HighlightDataView d = highlight(config, ctx);
                data = d;
                empty = d.picks().isEmpty();
            }
            case "GALLERY" -> {
                List<GalleryItemView> items = gallery(config, ctx);
                data = new ItemsDataView(items);
                empty = items.isEmpty();
            }
            case "BOARD_CARDS" -> {
                BoardCardsDataView d = boardCards(config, ctx);
                data = d;
                empty = d.cards().isEmpty();
            }
            case "GROUPS" -> {
                GadgetConfigs.Count limit = GadgetConfigs.count("GROUPS");
                int count = HomeLayoutJson.number(config, "count", limit.initial(), limit.min(), limit.max());
                // 공용 줄(내 상태 null) + 줄마다 모임장 — 내 상태는 꺼낸 뒤 덧입힌다(overlay)
                GroupQueryService.TrendingShared trending = groups.trendingShared();
                List<GroupRowView> items = trending.rows().stream().limit(count).toList();
                leaders = trending.leaderIds().stream().limit(count).toList();
                data = new ItemsDataView(items);
                empty = items.isEmpty();
            }
            case "QUICK_LINKS" -> {
                List<JsonNode> items = new ArrayList<>();
                JsonNode raw = config == null ? null : config.get("items");
                if (raw != null && raw.isArray()) {
                    raw.forEach(items::add);
                }
                data = new ItemsDataView(items);
                empty = items.isEmpty();
            }
            case "NOTICE_TEXT" -> {
                String text = HomeLayoutJson.text(config, "text", "");
                data = new NoticeTextDataView(text, HomeLayoutJson.text(config, "format", "PLAIN"));
                empty = text.isBlank();
            }
            case "GROUP_BUY" -> {
                GroupBuyDataView d = groupBuy(config);
                data = d;
                empty = d.items().isEmpty();
            }
            case HomeGadgetKinds.HOT_DEALS -> {
                HotDealsDataView d = hotDeals(config, ctx);
                data = d;
                empty = d.items().isEmpty();
            }
            default -> {
                data = null;
                empty = true;
            }
        }
        return new GadgetData(data, empty, leaders);
    }

    // ── POST_LIST ───────────────────────────────────────────────────────────

    private PostListDataView postList(JsonNode config, Ctx ctx) {
        int count = HomeLayoutJson.number(config, "count", 10, 1, 30);
        Set<String> slugs = boardSlugs(config, ctx);
        String sort = HomeLayoutJson.text(config, "sort", "LATEST");
        return switch (sort) {
            case "POPULAR" -> {
                if (HomeLayoutJson.flag(config, "periodTabs")) {
                    Map<String, List<PostFeedRowView>> periods = new LinkedHashMap<>();
                    for (String period : PERIODS) {
                        periods.put(period, ranked(period, slugs, ctx).stream().limit(count).toList());
                    }
                    yield new PostListDataView(periods.get("REALTIME"), periods);
                }
                yield new PostListDataView(ranked("REALTIME", slugs, ctx).stream().limit(count).toList(), null);
            }
            case "VIEWS", "LIKES", "COMMENTS" -> {
                Comparator<PostFeedRowView> by = switch (sort) {
                    case "VIEWS" -> Comparator.comparingInt(PostFeedRowView::viewCount);
                    case "LIKES" -> Comparator.comparingInt(PostFeedRowView::likeCount);
                    default -> Comparator.comparingInt(PostFeedRowView::commentCount);
                };
                List<PostFeedRowView> pool = new ArrayList<>(ranked("WEEK", slugs, ctx));
                pool.sort(by.reversed());
                yield new PostListDataView(pool.stream().limit(count).toList(), null);
            }
            default -> new PostListDataView(latest(boardIds(slugs, ctx), count, ctx), null);
        };
    }

    /** 인기글 스냅샷의 한 기간(최대 {@value #POPULAR_POOL}줄) — 게시판을 정했으면 그 게시판 글만 */
    private List<PostFeedRowView> ranked(String period, Set<String> slugs, Ctx ctx) {
        List<PostFeedRowView> items = popular.popular(period, "1", String.valueOf(POPULAR_POOL), ctx.viewerId()).items();
        return slugs == null ? items : items.stream().filter(r -> r.board() != null && slugs.contains(r.board().slug())).toList();
    }

    /** 최신글 — 최신글 전체와 같은 게시판 범위(켜짐 · 최신글 가젯 노출 금지 아님), 또는 정한 게시판 */
    private List<PostFeedRowView> latest(List<Long> boardIds, int count, Ctx ctx) {
        List<PostRowData> found = feeds.latest(boardIds, null, count, ctx.adult(), ctx.now());
        return rows.of(found, ctx.s(), ctx.now(), ctx.viewerId());
    }

    // ── HIGHLIGHT · GALLERY · BOARD_CARDS ───────────────────────────────────

    private HighlightDataView highlight(JsonNode config, Ctx ctx) {
        GadgetConfigs.Count limit = GadgetConfigs.count("HIGHLIGHT");
        int count = HomeLayoutJson.number(config, "count", limit.initial(), limit.min(), limit.max());
        // 설정의 곳(V161 — 없거나 모르는 곳이면 home)의 노출 중인 하이라이트(tips-contract 「결정」)
        String place = highlightPicks.resolve(GadgetConfigs.highlightPlace(config));
        List<HighlightPickView> picks = highlightPicks.showing(place, ctx.s(), ctx.now(), ctx.viewerId(), null, count).stream()
                .map(p -> new HighlightPickView(Long.toString(p.highlight().getId()), p.post(), p.highlight().getTitle(), p.highlight().getSummary(), p.imageUrl()))
                .toList();
        return new HighlightDataView(picks);
    }

    private List<GalleryItemView> gallery(JsonNode config, Ctx ctx) {
        int count = HomeLayoutJson.number(config, "count", 6, 1, 24);
        // 19금 글은 누구에게나 빼므로 질의에서 거른다(성인 아님 = false). 썸네일 = 첫 이미지, 없으면 YouTube 영상(PostThumbnails) — 영상만 있는 글도 든다
        List<PostRowData> withImage = feeds.latest(boardIds(boardSlugs(config, ctx), ctx), null, Math.min(count * 8, 200), false, ctx.now()).stream()
                .filter(r -> !Boolean.TRUE.equals(r.adult())).toList();
        return rows.of(withImage, ctx.s(), ctx.now(), ctx.viewerId()).stream()
                .filter(r -> r.thumbnailUrl() != null)
                .limit(count)
                .map(r -> new GalleryItemView(r.id(), r.title(), r.thumbnailUrl(), null,
                        new GalleryItemView.BoardRef(r.board().slug(), r.board().name(), r.board().nameEn()), r.likeCount(), r.createdAt()))
                .toList();
    }

    private BoardCardsDataView boardCards(JsonNode config, Ctx ctx) {
        int perBoard = HomeLayoutJson.number(config, "perBoard", 4, 1, 10);
        Set<String> slugs = boardSlugs(config, ctx);
        List<EffectiveBoard> boards = slugs != null
                ? slugs.stream().map(slug -> ctx.s().board(slug).orElse(null)).filter(Objects::nonNull).toList()
                : ctx.s().boards().stream().filter(b -> b.active() && !BoardFields.KIND_GROUP.equals(b.kind())).limit(BOARD_CARDS_DEFAULT).toList();
        List<BoardCardView> cards = new ArrayList<>();
        for (EffectiveBoard b : boards) {
            List<PostFeedRowView> latest = latest(List.of(b.id()), perBoard + 1, ctx);
            PostFeedRowView lead = latest.stream().filter(r -> r.thumbnailUrl() != null).findFirst().orElse(latest.isEmpty() ? null : latest.getFirst());
            List<PostFeedRowView> items = latest.stream().filter(r -> r != lead).limit(perBoard).toList();
            cards.add(new BoardCardView(new BoardCardView.Board(b.slug(), b.name(), b.nameEn(), b.color(), b.investmentNotice()), lead, items));
        }
        return new BoardCardsDataView(cards);
    }

    // ── GROUP_BUY ───────────────────────────────────────────────────────────

    /** 모집 중인 공개 공구(목록 진행 탭과 같은 거름) — 설정 {@code count}·{@code sort}({@link HomeGadgetKinds#groupBuyCount}·{@link HomeGadgetKinds#groupBuySort}) + 판매 스위치 */
    GroupBuyDataView groupBuy(JsonNode config) {
        GroupBuyMemberService.RecruitingOrder order = GroupBuyMemberService.RecruitingOrder.valueOf(HomeGadgetKinds.groupBuySort(config));
        return new GroupBuyDataView(groupBuys.recruiting(HomeGadgetKinds.groupBuyCount(config), order), sales.groupBuyOpen());
    }

    // ── HOT_DEALS ───────────────────────────────────────────────────────────

    /**
     * 진행 중인 핫딜(hotdeal-contract 「가젯 HOT_DEALS 설정」, 규칙 8) — 설정 {@code boards}(핫딜 게시판 slug — 없거나 꺼졌거나 핫딜이 아닌 게시판은 건너뛰고, 비면 켜진 핫딜 게시판 전부)에서
     * ACTIVE(의심·종료·기간 종료 제외)·공개 글을 {@code sort}·{@code count} 대로. 광고 숨김과 무관하다. 줄은 섞인 목록 줄 + {@code deal}.
     * 「더 보기」 = 고른 첫 핫딜 게시판, 고르지 않았으면 켜진 핫딜 게시판의 첫째(게시판 차례), 없으면 null.
     */
    public HotDealsDataView hotDeals(JsonNode config, Long viewerId) {
        return hotDeals(config, new Ctx(catalog.snapshot(), Instant.now(), viewerId, adultGate.adult(viewerId), null, null, false));
    }

    HotDealsDataView hotDeals(JsonNode config, Ctx ctx) {
        List<EffectiveBoard> all = ctx.s().boards().stream().filter(EffectiveBoard::active).filter(DealWriter::dealBoard).toList();
        List<String> picked = HomeGadgetKinds.hotDealsBoards(config);
        List<EffectiveBoard> boards = picked.isEmpty() ? all
                : picked.stream().map(slug -> ctx.s().board(slug).orElse(null)).filter(Objects::nonNull).filter(EffectiveBoard::active).filter(DealWriter::dealBoard)
                .toList();
        if (boards.isEmpty() && !picked.isEmpty()) {
            boards = all; // 고른 게시판이 하나도 남지 않으면 거르지 않는다(글 목록 가젯과 같다)
        }
        HotDealsDataView.More more = boards.isEmpty() ? null : new HotDealsDataView.More("/boards/" + boards.get(0).slug());
        DealQueries.GadgetSort sort = DealQueries.GadgetSort.valueOf(HomeGadgetKinds.hotDealsSort(config));
        List<Long> ids = dealQueries.gadgetDealIds(boards.stream().map(EffectiveBoard::id).toList(), sort, dealSettings.current().autoExpireDays(), ctx.now(),
                HomeGadgetKinds.hotDealsCount(config));
        Map<Long, PostRowData> found = feeds.visibleRows(ids, ctx.adult(), ctx.now()).stream().collect(Collectors.toMap(PostRowData::id, Function.identity()));
        List<PostRowData> ordered = ids.stream().map(found::get).filter(Objects::nonNull).toList();
        return new HotDealsDataView(rows.of(ordered, ctx.s(), ctx.now(), ctx.viewerId()), more);
    }

    // ── 게시판 고르기 ───────────────────────────────────────────────────────

    /**
     * 대상 게시판(slug, 차례) — {@code ALL} 은 null(거르지 않음), {@code FOLDER} 는 페이지 기준 폴더의 켜진 게시판(없으면 null),
     * {@code PICK} 은 설정 {@code boardIds} 중 켜진 게시판(하나도 남지 않으면 null). 차례는 설정·폴더 차례
     */
    private static Set<String> boardSlugs(JsonNode config, Ctx ctx) {
        String scope = GadgetConfigs.scope(config);
        if (GadgetConfigs.ALL.equals(scope)) {
            return null;
        }
        if (GadgetConfigs.FOLDER.equals(scope)) {
            return ctx.folder();
        }
        if (GadgetConfigs.BOARD.equals(scope)) {
            return ctx.board() == null ? null : Set.of(ctx.board());
        }
        Set<String> out = new java.util.LinkedHashSet<>();
        for (String slug : HomeLayoutJson.texts(config, "boardIds")) {
            ctx.s().board(slug).filter(EffectiveBoard::active).ifPresent(b -> out.add(b.slug()));
        }
        return out.isEmpty() ? null : out;
    }

    /** 정한 게시판의 ID, 정하지 않았으면 최신글 전체와 같은 범위(켜짐 · 최신글 가젯 노출 금지 아님) */
    private static List<Long> boardIds(Set<String> slugs, Ctx ctx) {
        if (slugs != null) {
            return slugs.stream().map(slug -> ctx.s().board(slug).map(EffectiveBoard::id).orElse(null)).filter(Objects::nonNull).toList();
        }
        return ctx.s().boards().stream().filter(b -> b.active() && !b.excludedFrom(BoardGadget.LATEST)).map(EffectiveBoard::id).toList();
    }

    /**
     * 한 요청 동안 같이 쓰는 값
     *
     * @param folder 가젯 페이지 기준 폴더의 켜진 게시판 slug — 홈이거나 없으면 null(대상 {@code FOLDER} 는 전체)
     * @param adult  보는 사람이 성인인가({@link AdultContentGate#adult}) — 아니면 글 목록 가젯에서 19금 글을 뺀다
     * @param board  게시판 레이아웃이 그리는 게시판 slug(켜진 게시판) — 게시판 레이아웃이 아니면 null(대상 {@code BOARD} 는 전체)
     */
    private record Ctx(BoardCatalogSnapshot s, Instant now, Long viewerId, boolean adult, Set<String> folder, String board, boolean cached) {

        /** 같은 값에서 보는 사람만 뺀 것 — 가젯 데이터는 이것으로 만든다(성인 여부·대상 게시판은 남는다) */
        Ctx shared() {
            return new Ctx(s, now, null, adult, folder, board, cached);
        }
    }

    /** 캐시에 담는 가젯 데이터 — {@code leaders} 는 소모임 가젯의 줄마다 모임장(내 상태 덧입힘용, 그 밖에는 null) */
    private record GadgetData(Object data, boolean empty, List<Long> leaders) {
    }

    /** 캐시에 담는 홈 배치 */
    private record Layout(HomeGridLayout layout, List<HomeGadgetSpec> specs) {
    }

    /** 캐시에 담는 가젯 페이지 — 머리 · 배치 · 기준 폴더 게시판(켜짐 판정은 요청마다 게시판 사본으로) */
    private record PageLayout(String pageKey, String name, String nameEn, String description, boolean writeButton, HomeGridLayout layout,
                              List<HomeGadgetSpec> specs, List<String> folderSlugs) {
    }

    /** 캐시에 담는 게시판 레이아웃 */
    private record BoardLayout(BoardLayoutResponse.Screens screens, HomeGridLayout layout, List<HomeGadgetSpec> specs) {
    }
}
