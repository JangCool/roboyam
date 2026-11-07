package com.community.tips.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.highlight.service.HighlightPicks;
import com.community.highlight.service.HighlightPlaces;
import com.community.post.dto.PostRowData;
import com.community.post.dto.response.PostBoardRefView;
import com.community.post.dto.response.PostFeedRowView;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.post.service.AdultContentGate;
import com.community.post.service.PostFeedRows;
import com.community.tips.dto.response.TipsOverviewView;
import com.community.tips.dto.response.TipsPostPageView;
import com.community.tips.dto.response.TipsPostView;
import com.community.tips.repository.master.TipsPostQueries;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팁·강좌·사용기 묶음 화면({@code /tips}) — docs/features/tips-contract.md 「결정」·「서버 구현 결과」. 비회원도 본다(로그인했으면 줄에 차단·메모).
 *
 * <ul>
 *   <li><b>묶음 게시판</b>: {@link #BUNDLE}(팁과강좌 {@code tip} · 사용기 {@code review}) 고정 — 켜진 것만, 게시판 차례. {@code board} 가 그중 하나면 그 게시판만, 아니면(비었거나 모르는 값) 묶음 전체.</li>
 *   <li><b>헤드라인</b>(최대 {@value #HEADLINE_SIZE}): {@code tips} 곳 하이라이트({@link HighlightPicks} — 노출 중·글이 보임·19금 아님, 관리자 순서, 노출 제목·요약·대표 이미지 반영)를 먼저.
 *       게시판을 고르지 않았으면 운영자가 고른 글은 묶음 밖 게시판이어도 둔다(고르면 그 게시판 글만). 공지(고정) 글은 뺀다.
 *       모자라면 최근 {@value #HEADLINE_DAYS}일 묶음 글 추천순, 그래도 모자라면 더 오래된 글 추천순. 같은 글은 한 번만.</li>
 *   <li><b>많이 본 글</b>(최대 {@value #MOST_VIEWED_SIZE}): 최근 {@value #MOST_VIEWED_DAYS}일 조회순, 모자라면 더 오래된 글 조회순. 헤드라인과 겹쳐도 된다.</li>
 *   <li>헤드라인·많이 본 글은 공지·신고 숨긴 글·19금 글·비멤버에게 안 보이는 소모임 글을 뺀다(섞인 목록과 같은 판정 — 공개 게시판은 읽기 제한이 없다).</li>
 *   <li><b>최신 글</b>: 묶음(또는 고른 게시판) 글을 최신순으로 합쳐 한 쪽 {@value #PAGE_SIZE}개. 거름은 게시판 글 목록과 같다(공지는 빼고, 신고 숨긴 글은 자리만, 19금 글은 성인에게만).
 *       헤드라인 글도 그대로 둔다.</li>
 *   <li><b>요약</b> {@code excerpt}: 본문 평문({@link PostEntity#plainContent()}) 앞 {@value #EXCERPT_MAX}자(공백을 한 칸으로, 넘으면 「…」). 가려진 글·19금 글은 null.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class TipsService {

    /** 묶음 게시판 slug(계약 「결정」 — 운영자 설정은 나중) */
    public static final List<String> BUNDLE = List.of("tip", "review");
    static final int HEADLINE_SIZE = 5;
    static final int HEADLINE_DAYS = 7;
    static final int MOST_VIEWED_SIZE = 5;
    static final int MOST_VIEWED_DAYS = 30;
    static final int PAGE_SIZE = 20;
    static final int EXCERPT_MAX = 120;

    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private final BoardCatalog catalog;
    private final TipsPostQueries queries;
    private final PostRepository posts;
    private final PostFeedRows rows;
    private final HighlightPicks highlightPicks;
    private final AdultContentGate adultGate;

    public TipsService(BoardCatalog catalog, TipsPostQueries queries, PostRepository posts, PostFeedRows rows, HighlightPicks highlightPicks,
                       AdultContentGate adultGate) {
        this.catalog = catalog;
        this.queries = queries;
        this.posts = posts;
        this.rows = rows;
        this.highlightPicks = highlightPicks;
        this.adultGate = adultGate;
    }

    /** 묶음 머리 몫 — 게시판 칩 · 헤드라인 · 많이 본 글 */
    public TipsOverviewView overview(String board, Long viewerId) {
        BoardCatalogSnapshot s = catalog.snapshot();
        Instant now = Instant.now();
        List<EffectiveBoard> bundle = bundle(s);
        List<PostBoardRefView> chips = bundle.stream().map(TipsService::ref).toList();
        EffectiveBoard picked = picked(bundle, board);
        List<Long> scope = picked != null ? List.of(picked.id()) : bundle.stream().map(EffectiveBoard::id).toList();
        if (scope.isEmpty()) {
            return new TipsOverviewView(chips, List.of(), List.of());
        }

        // 헤드라인 — 운영자가 tips 곳에 고른 글 먼저
        List<HighlightPicks.Pick> picks = highlightPicks.showing(HighlightPlaces.TIPS, s, now, viewerId,
                r -> r.pinnedAt() == null && (picked == null || Long.valueOf(picked.id()).equals(r.boardId())), HEADLINE_SIZE);
        Set<Long> taken = new HashSet<>();
        picks.forEach(p -> taken.add(p.highlight().getPostId()));
        List<PostRowData> fill = new ArrayList<>();
        Instant weekAgo = now.minus(Duration.ofDays(HEADLINE_DAYS));
        fill.addAll(queries.top(scope, TipsPostQueries.Rank.LIKES, weekAgo, true, taken, HEADLINE_SIZE - picks.size(), now));
        fill.forEach(r -> taken.add(r.id()));
        fill.addAll(queries.top(scope, TipsPostQueries.Rank.LIKES, weekAgo, false, taken, HEADLINE_SIZE - picks.size() - fill.size(), now));

        // 많이 본 글 — 최근 30일 조회순, 모자라면 기간 밖
        Instant monthAgo = now.minus(Duration.ofDays(MOST_VIEWED_DAYS));
        List<PostRowData> viewed = new ArrayList<>(queries.top(scope, TipsPostQueries.Rank.VIEWS, monthAgo, true, List.of(), MOST_VIEWED_SIZE, now));
        if (viewed.size() < MOST_VIEWED_SIZE) {
            viewed.addAll(queries.top(scope, TipsPostQueries.Rank.VIEWS, monthAgo, false, List.of(), MOST_VIEWED_SIZE - viewed.size(), now));
        }

        List<PostFeedRowView> fillRows = rows.of(fill, s, now, viewerId);
        List<PostFeedRowView> viewedRows = rows.of(viewed, s, now, viewerId);
        List<PostFeedRowView> all = new ArrayList<>(fillRows);
        all.addAll(viewedRows);
        picks.forEach(p -> all.add(p.post()));
        Map<String, String> excerpts = excerpts(all);

        List<TipsPostView> headline = new ArrayList<>();
        for (HighlightPicks.Pick p : picks) {
            headline.add(TipsPostView.highlighted(p.post(), excerpts.get(p.post().id()), p.highlight().getTitle(), p.highlight().getSummary(), p.imageUrl()));
        }
        fillRows.forEach(r -> headline.add(TipsPostView.of(r, excerpts.get(r.id()))));
        List<TipsPostView> mostViewed = viewedRows.stream().map(r -> TipsPostView.of(r, excerpts.get(r.id()))).toList();
        return new TipsOverviewView(chips, headline.size() > HEADLINE_SIZE ? headline.subList(0, HEADLINE_SIZE) : headline, mostViewed);
    }

    /** 최신 글 한 쪽 — 묶음(또는 고른 게시판) 글 최신순, 한 쪽 {@value #PAGE_SIZE}개 */
    public TipsPostPageView latest(String board, int page, Long viewerId) {
        BoardCatalogSnapshot s = catalog.snapshot();
        Instant now = Instant.now();
        int current = Math.max(1, page);
        List<EffectiveBoard> bundle = bundle(s);
        EffectiveBoard picked = picked(bundle, board);
        List<Long> scope = picked != null ? List.of(picked.id()) : bundle.stream().map(EffectiveBoard::id).toList();
        PostRepositoryQDSL.Page result = queries.latest(scope, adultGate.adult(viewerId), (long) (current - 1) * PAGE_SIZE, PAGE_SIZE, now);
        List<PostFeedRowView> found = rows.of(result.items(), s, now, viewerId);
        Map<String, String> excerpts = excerpts(found);
        int totalPages = (int) ((result.total() + PAGE_SIZE - 1) / PAGE_SIZE);
        return new TipsPostPageView(found.stream().map(r -> TipsPostView.of(r, excerpts.get(r.id()))).toList(), current, totalPages, result.total());
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    /** 묶음에 든 켜진 게시판(게시판 차례) */
    static List<EffectiveBoard> bundle(BoardCatalogSnapshot s) {
        return s.boards().stream().filter(b -> b.active() && BUNDLE.contains(b.slug())).toList();
    }

    /** 고른 게시판 — 묶음에 없으면 null(전체) */
    private static EffectiveBoard picked(List<EffectiveBoard> bundle, String board) {
        String slug = board == null ? "" : board.strip();
        return bundle.stream().filter(b -> b.slug().equals(slug)).findFirst().orElse(null);
    }

    private static PostBoardRefView ref(EffectiveBoard b) {
        return new PostBoardRefView(Long.toString(b.id()), b.slug(), b.name(), b.nameEn(), b.color());
    }

    /** 글 ID(문자열) → 요약 — 가려진 글·19금 글은 넣지 않는다(본문을 읽지 않는다) */
    private Map<String, String> excerpts(Collection<PostFeedRowView> list) {
        Set<Long> ids = new HashSet<>();
        for (PostFeedRowView r : list) {
            if (r.hiddenReason() == null && !r.adult()) {
                ids.add(Long.parseLong(r.id()));
            }
        }
        Map<String, String> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (PostEntity p : posts.findAllById(ids)) {
            String text = excerpt(p.plainContent());
            if (text != null) {
                out.put(Long.toString(p.getId()), text);
            }
        }
        return out;
    }

    /** 본문 평문 → 요약(공백을 한 칸으로, {@value #EXCERPT_MAX}자 넘으면 자르고 「…」). 비면 null */
    static String excerpt(String plain) {
        if (plain == null) {
            return null;
        }
        String text = SPACES.matcher(plain).replaceAll(" ").strip();
        if (text.isEmpty()) {
            return null;
        }
        if (text.codePointCount(0, text.length()) <= EXCERPT_MAX) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, EXCERPT_MAX)).stripTrailing() + "…";
    }
}
