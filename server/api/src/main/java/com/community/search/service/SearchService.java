package com.community.search.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.post.dto.PostRowData;
import com.community.post.dto.response.PostFeedRowView;
import com.community.post.repository.master.PostFeedRepositoryQDSL;
import com.community.post.service.FeedParams;
import com.community.post.service.PostFeedRows;
import com.community.search.dto.response.SearchResponse;
import com.community.search.dto.response.SearchRowView;
import com.community.search.exception.SearchTimeoutException;
import com.community.search.repository.master.PostSearchQueries;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 통합 검색({@code GET /api/search}) — 제목·본문·작성자, 비회원도. 규칙은 {@link PostSearchQueries}·{@link SearchQueryPolicy}.
 *
 * <ul>
 *   <li>검사(모아서 {@code 400 VALIDATION}): {@code fields.q}(검색어 규칙) · {@code scope}(all|title|body|author) · {@code board}(없는·꺼진 게시판 — 공개 게시판은
 *       누구나 읽으므로 「못 읽는 게시판」은 따로 없다) · {@code order}(latest|oldest) · {@code page}(1~10, 커서와 함께 주면 오류) · {@code cursor}(글 ID 형식) · {@code size}(1~50).</li>
 *   <li>쪽 번호(기본)면 {@code page}·{@code totalPages}(최대 10)를, 커서면 둘 다 null. {@code nextCursor} 는 다음이 있을 때 이 응답의 마지막 글 ID.</li>
 *   <li><b>시간 제한 {@value #TIME_LIMIT_MILLIS}ms</b>(서버 상수 — 사용자 결정 2026-10-24): 트랜잭션 안의 문장마다 남은 시간을 {@code statement_timeout} 으로 걸고, 넘으면
 *       DB 가 문장을 취소해 {@code 503 SEARCH_TIMEOUT}. 그래서 클래스 단위 트랜잭션 대신 {@link TransactionTemplate} 으로 열고 그 바깥에서 취소를 알아본다.</li>
 *   <li>차단한 회원의 글 빼기·게시판별 「검색에 포함」 끄기는 아직 없다(계약 결정).</li>
 * </ul>
 */
@Service
public class SearchService {

    /** 검색 한 번의 시간 제한 */
    static final long TIME_LIMIT_MILLIS = 3000;
    /** 쪽 번호의 끝 — 그 너머는 커서 더보기 */
    static final int MAX_PAGE = 10;
    private static final Set<String> SCOPES = Set.of(PostSearchQueries.ALL, PostSearchQueries.TITLE, PostSearchQueries.BODY, PostSearchQueries.AUTHOR);
    private static final String LATEST = "latest";
    private static final String OLDEST = "oldest";
    /** DB 가 시간 제한으로 문장을 취소했다(query_canceled) */
    private static final String SQLSTATE_CANCELED = "57014";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private final BoardCatalog catalog;
    private final PostSearchQueries searches;
    private final PostFeedRepositoryQDSL feeds;
    private final PostFeedRows rows;
    private final TransactionTemplate tx;

    public SearchService(BoardCatalog catalog, PostSearchQueries searches, PostFeedRepositoryQDSL feeds, PostFeedRows rows, PlatformTransactionManager txManager) {
        this.catalog = catalog;
        this.searches = searches;
        this.feeds = feeds;
        this.rows = rows;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setReadOnly(true);
    }

    /** @param viewerId 로그인한 회원(비로그인 null) — 줄의 {@code blocked}·작성자 {@code memo} 를 게시판 목록과 같게 싣는다(결과에서 빼지 않고 표시만, 개수·쪽 수도 그대로) */
    public SearchResponse search(String qRaw, String scopeRaw, String boardRaw, String orderRaw, String pageRaw, String cursorRaw, String sizeRaw, Long viewerId) {
        BoardCatalogSnapshot s = catalog.snapshot();
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        SearchQueryPolicy.Problem problem = SearchQueryPolicy.check(qRaw);
        if (problem != null) {
            fields.put("q", switch (problem) {
                case TOO_SHORT -> new MessageRef("search.q.tooShort", SearchQueryPolicy.MIN_TOKEN);
                case TOO_LONG -> new MessageRef("search.q.tooLong", SearchQueryPolicy.MAX_QUERY);
                case SHORT_WORD -> new MessageRef("search.q.shortWord", SearchQueryPolicy.MIN_TOKEN);
            });
        }
        String scope = blank(scopeRaw) ? PostSearchQueries.ALL : scopeRaw.strip().toLowerCase(Locale.ROOT);
        if (!SCOPES.contains(scope)) {
            fields.put("scope", new MessageRef("search.scope.invalid"));
        }
        EffectiveBoard board = null;
        if (!blank(boardRaw)) {
            board = s.board(boardRaw.strip()).filter(EffectiveBoard::active).orElse(null);
            if (board == null) {
                fields.put("board", new MessageRef("search.board.invalid"));
            }
        }
        String order = blank(orderRaw) ? LATEST : orderRaw.strip().toLowerCase(Locale.ROOT);
        if (!LATEST.equals(order) && !OLDEST.equals(order)) {
            fields.put("order", new MessageRef("search.order.invalid"));
        }
        boolean cursorMode = !blank(cursorRaw);
        int page = 1;
        if (cursorMode && !blank(pageRaw)) {
            fields.put("page", new MessageRef("search.page.withCursor"));
        } else {
            page = FeedParams.page(pageRaw, MAX_PAGE, fields);
        }
        Long cursor = FeedParams.cursor(cursorRaw, fields);
        int size = FeedParams.size(sizeRaw, fields);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }

        String query = SearchQueryPolicy.normalize(qRaw);
        List<String> tokens = SearchQueryPolicy.distinctWords(qRaw);
        List<Long> boardIds = board != null ? List.of(board.id())
                : s.boards().stream().filter(EffectiveBoard::active).map(EffectiveBoard::id).toList();
        PostSearchQueries.Criteria criteria = new PostSearchQueries.Criteria(boardIds, tokens, scope, OLDEST.equals(order), cursor, Instant.now());
        String boardSlug = board == null ? null : board.slug();
        int currentPage = page;
        long deadline = System.nanoTime() + Duration.ofMillis(TIME_LIMIT_MILLIS).toNanos();
        try {
            return tx.execute(status -> run(s, criteria, query, boardSlug, cursorMode, currentPage, size, deadline, viewerId));
        } catch (RuntimeException e) {
            if (canceled(e)) {
                throw new SearchTimeoutException();
            }
            throw e;
        }
    }

    private SearchResponse run(BoardCatalogSnapshot s, PostSearchQueries.Criteria c, String query, String boardSlug, boolean cursorMode, int page, int size,
                               long deadline, Long viewerId) {
        if (c.boardIds().isEmpty()) {
            return new SearchResponse(query, c.tokens(), c.scope(), boardSlug, List.of(), cursorMode ? null : page, cursorMode ? null : 0, false, null);
        }
        Integer totalPages = null;
        if (!cursorMode) {
            searches.limitTime(remaining(deadline));
            long total = searches.countUpTo(c, MAX_PAGE * size);
            totalPages = (int) Math.min(MAX_PAGE, (total + size - 1) / size);
        }
        boolean snippet = PostSearchQueries.ALL.equals(c.scope()) || PostSearchQueries.BODY.equals(c.scope());
        searches.limitTime(remaining(deadline));
        List<PostSearchQueries.Hit> hits = searches.find(c, cursorMode ? 0 : (long) (page - 1) * size, size + 1, snippet);
        boolean hasNext = hits.size() > size;
        List<PostSearchQueries.Hit> shown = hasNext ? hits.subList(0, size) : hits;

        searches.limitTime(remaining(deadline));
        Map<Long, PostRowData> found = feeds.visibleRows(shown.stream().map(PostSearchQueries.Hit::id).toList(), c.now()).stream()
                .collect(Collectors.toMap(PostRowData::id, Function.identity()));
        List<PostRowData> ordered = new ArrayList<>(shown.size());
        Map<String, String> snippets = new HashMap<>();
        for (PostSearchQueries.Hit hit : shown) {
            PostRowData r = found.get(hit.id());
            if (r != null) {
                ordered.add(r);
                snippets.put(Long.toString(hit.id()), snippetOf(hit));
            }
        }
        List<SearchRowView> items = new ArrayList<>(ordered.size());
        for (PostFeedRowView row : rows.of(ordered, s, c.now(), viewerId)) {
            items.add(SearchRowView.of(row, snippets.get(row.id())));
        }
        String next = hasNext && !shown.isEmpty() ? Long.toString(shown.getLast().id()) : null;
        return new SearchResponse(query, c.tokens(), c.scope(), boardSlug, items, cursorMode ? null : page, totalPages, hasNext, next);
    }

    /** 조각 — 이어진 공백을 한 칸으로, 앞·뒤가 잘렸으면 「…」 */
    static String snippetOf(PostSearchQueries.Hit hit) {
        if (hit.window() == null || hit.start() <= 0) {
            return null;
        }
        String text = WHITESPACE.matcher(hit.window()).replaceAll(" ").strip();
        if (text.isEmpty()) {
            return null;
        }
        boolean cutBefore = hit.start() > 1;
        boolean cutAfter = hit.start() - 1 + hit.window().codePointCount(0, hit.window().length()) < hit.bodyLength(); // DB 글자 수(char_length)는 코드 포인트
        return (cutBefore ? "…" : "") + text + (cutAfter ? "…" : "");
    }

    /** 남은 시간(밀리초) — 다 썼으면 DB 에 가지 않고 시간 초과로 끝낸다 */
    private static long remaining(long deadline) {
        long millis = Duration.ofNanos(deadline - System.nanoTime()).toMillis();
        if (millis <= 0) {
            throw new SearchTimeoutException();
        }
        return millis;
    }

    /** DB 가 시간 제한으로 문장을 취소했는가 — 예외 사슬 어딘가의 SQLSTATE 57014 */
    private static boolean canceled(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SearchTimeoutException) {
                return true;
            }
            if (t instanceof SQLException sql && SQLSTATE_CANCELED.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
