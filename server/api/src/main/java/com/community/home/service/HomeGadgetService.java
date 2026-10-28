package com.community.home.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.entity.BoardGadget;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.group.service.GroupQueryService;
import com.community.highlight.entity.HighlightEntity;
import com.community.highlight.repository.master.HighlightRepository;
import com.community.home.dto.HomeGadgetSpec;
import com.community.home.dto.HomeGridLayout;
import com.community.home.dto.response.HomeGadgetView;
import com.community.home.dto.response.HomeGadgetsResponse;
import com.community.home.dto.response.gadget.BoardCardView;
import com.community.home.dto.response.gadget.BoardCardsDataView;
import com.community.home.dto.response.gadget.GalleryItemView;
import com.community.home.dto.response.gadget.HighlightDataView;
import com.community.home.dto.response.gadget.HighlightPickView;
import com.community.home.dto.response.gadget.ItemsDataView;
import com.community.home.dto.response.gadget.NoticeTextDataView;
import com.community.home.dto.response.gadget.PostListDataView;
import com.community.home.entity.HomeGadgetEntity;
import com.community.home.entity.HomeLayoutEntity;
import com.community.home.repository.master.HomeGadgetRepository;
import com.community.home.repository.master.HomeLayoutRepository;
import com.community.media.repository.master.FileRepository;
import com.community.media.service.ImageStorage;
import com.community.post.dto.PostRowData;
import com.community.post.dto.response.PostFeedRowView;
import com.community.post.repository.master.PostFeedRepositoryQDSL;
import com.community.post.service.PostFeedRows;
import com.community.site.service.PublicSiteService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
 *   <li>테마는 공개 사이트 사본({@code PublicSiteService}, 20초)의 값이다. 배치·가젯은 요청마다 읽는다(관리자 저장이 바로 보인다).</li>
 *   <li>글 줄은 최신글·인기글 목록과 같은 거름·모양이다({@link PostFeedRows}, 로그인하면 차단·메모 표시). 가젯 설정 {@code boardIds} 는 게시판 slug 목록이고
 *       없거나 꺼진 게시판은 건너뛴다(하나도 남지 않으면 거르지 않는다).</li>
 *   <li>{@code POST_LIST}: {@code sort} LATEST(최신글과 같은 게시판 범위) · POPULAR(인기글 순위 스냅샷, {@code periodTabs} 면 세 기간을 함께) ·
 *       VIEWS·LIKES·COMMENTS(이번 주 인기글 후보를 그 수로 다시 정렬 — 전체 글을 세지 않는다). {@code count} 기본 10.</li>
 *   <li>{@code HIGHLIGHT}: 노출 중인 하이라이트(관리자 순서), 글이 지금 보이는 것만, {@code count} 기본 5. {@code GALLERY}: 사진 있는 최신 글, {@code count} 기본 6.
 *       {@code BOARD_CARDS}: 게시판마다 최신 {@code perBoard}(기본 4)개 + 대표 글, 게시판을 정하지 않으면 켜진 일반 게시판 앞 6개. {@code GROUPS}: 지금 뜨는 소모임 {@code count}(기본 3)개.</li>
 *   <li>{@code GROUP_BUY}: 공개 공동구매 목록 API 가 아직 없어 늘 빈 목록이다. {@code QUICK_LINKS}·{@code NOTICE_TEXT}: 가젯 설정의 {@code items}·{@code text}·{@code format}.</li>
 *   <li>노출 기간·보는 사람(로그인 전용) 설정은 아직 없다 — {@code locked} 는 늘 false.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class HomeGadgetService {

    private static final int POPULAR_POOL = 50;
    private static final int BOARD_CARDS_DEFAULT = 6;
    private static final List<String> PERIODS = List.of("REALTIME", "YESTERDAY", "WEEK");

    private final PublicSiteService site;
    private final HomeLayoutRepository layouts;
    private final HomeGadgetRepository gadgets;
    private final BoardCatalog catalog;
    private final PostFeedRepositoryQDSL feeds;
    private final PostFeedRows rows;
    private final PopularService popular;
    private final HighlightRepository highlights;
    private final FileRepository files;
    private final ImageStorage storage;
    private final GroupQueryService groups;

    public HomeGadgetService(PublicSiteService site, HomeLayoutRepository layouts, HomeGadgetRepository gadgets, BoardCatalog catalog,
                             PostFeedRepositoryQDSL feeds, PostFeedRows rows, PopularService popular, HighlightRepository highlights, FileRepository files,
                             ImageStorage storage, GroupQueryService groups) {
        this.site = site;
        this.layouts = layouts;
        this.gadgets = gadgets;
        this.catalog = catalog;
        this.feeds = feeds;
        this.rows = rows;
        this.popular = popular;
        this.highlights = highlights;
        this.files = files;
        this.storage = storage;
        this.groups = groups;
    }

    /** @param viewerId 로그인한 회원(비로그인 null) — 글 줄의 차단·메모, 소모임 줄의 내 상태 */
    public HomeGadgetsResponse gadgets(Long viewerId) {
        String theme = site.snapshot().info().theme();
        HomeLayoutEntity row = layouts.findById(theme).orElse(null);
        HomeGridLayout layout;
        List<HomeGadgetSpec> specs;
        if (row == null) {
            HomeLayoutDefaults.Home home = HomeLayoutDefaults.of(theme);
            layout = home.layout();
            specs = home.gadgets();
        } else {
            layout = row.toLayout();
            specs = gadgets.findByThemeOrderByIdAsc(theme).stream().map(HomeGadgetEntity::toSpec).toList();
        }
        Ctx ctx = new Ctx(catalog.snapshot(), Instant.now(), viewerId);
        return new HomeGadgetsResponse(theme, layout, specs.stream().map(spec -> view(spec, ctx)).toList());
    }

    private HomeGadgetView view(HomeGadgetSpec g, Ctx ctx) {
        JsonNode config = g.config();
        Object data;
        boolean empty;
        switch (g.type()) {
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
                int count = HomeLayoutJson.number(config, "count", 3, 1, 12);
                List<?> items = groups.trending(ctx.viewerId()).items().stream().limit(count).toList();
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
            // 공개 공동구매 목록 API 가 생기면 그 줄 모양으로 채운다
            case "GROUP_BUY" -> {
                data = new ItemsDataView(List.of());
                empty = true;
            }
            default -> {
                data = null;
                empty = true;
            }
        }
        return new HomeGadgetView(g.id(), g.type(), g.title(), g.desktop(), g.mobile(), empty, false, g.style(), config, g.more(), data);
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
        List<PostRowData> found = feeds.latest(boardIds, null, count, ctx.now());
        return rows.of(found, ctx.s(), ctx.now(), ctx.viewerId());
    }

    // ── HIGHLIGHT · GALLERY · BOARD_CARDS ───────────────────────────────────

    private HighlightDataView highlight(JsonNode config, Ctx ctx) {
        int count = HomeLayoutJson.number(config, "count", 5, 1, 12);
        List<HighlightEntity> showing = highlights.findAllByOrderBySortOrderAscIdAsc().stream().filter(h -> h.isShowingAt(ctx.now())).toList();
        if (showing.isEmpty()) {
            return new HighlightDataView(List.of());
        }
        // 보이는 글 중 켜진 게시판의 것만(하이라이트를 고른 뒤 게시판을 끈 경우)
        List<PostRowData> visible = feeds.visibleRows(showing.stream().map(HighlightEntity::getPostId).toList(), ctx.now()).stream()
                .filter(r -> r.boardId() != null && ctx.s().board(r.boardId()).map(EffectiveBoard::active).orElse(false)).toList();
        Map<Long, PostFeedRowView> posts = rows.of(visible, ctx.s(), ctx.now(), ctx.viewerId()).stream()
                .collect(Collectors.toMap(r -> Long.parseLong(r.id()), Function.identity()));
        List<Long> imageIds = showing.stream().map(HighlightEntity::getImageFileId).filter(Objects::nonNull).toList();
        Map<Long, String> imageUrls = new HashMap<>();
        if (!imageIds.isEmpty()) {
            files.findAllById(imageIds).forEach(f -> imageUrls.put(f.getId(), storage.publicUrl(f.getStorageKey())));
        }
        List<HighlightPickView> picks = new ArrayList<>();
        for (HighlightEntity h : showing) {
            PostFeedRowView post = posts.get(h.getPostId());
            if (post == null || post.adult()) {
                continue;
            }
            picks.add(new HighlightPickView(Long.toString(h.getId()), post, h.getTitle(), h.getSummary(),
                    h.getImageFileId() == null ? null : imageUrls.get(h.getImageFileId())));
            if (picks.size() >= count) {
                break;
            }
        }
        return new HighlightDataView(picks);
    }

    private List<GalleryItemView> gallery(JsonNode config, Ctx ctx) {
        int count = HomeLayoutJson.number(config, "count", 6, 1, 24);
        List<PostRowData> withImage = feeds.latest(boardIds(boardSlugs(config, ctx), ctx), null, Math.min(count * 8, 200), ctx.now()).stream()
                .filter(r -> Boolean.TRUE.equals(r.hasImage()) && !Boolean.TRUE.equals(r.adult())).toList();
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

    // ── 게시판 고르기 ───────────────────────────────────────────────────────

    /** 설정 {@code boardIds}(slug) 중 켜진 게시판 — 정하지 않았거나 하나도 남지 않으면 null(거르지 않음). 차례는 설정 차례 */
    private static Set<String> boardSlugs(JsonNode config, Ctx ctx) {
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

    /** 한 요청 동안 같이 쓰는 값 */
    private record Ctx(BoardCatalogSnapshot s, Instant now, Long viewerId) {
    }
}
