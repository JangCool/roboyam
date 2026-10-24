package com.community.home.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.entity.BoardGadget;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.home.dto.response.PopularListResponse;
import com.community.home.repository.master.PopularRankQueries;
import com.community.post.dto.PostRowData;
import com.community.post.dto.response.PostFeedRowView;
import com.community.post.repository.master.PostFeedRepositoryQDSL;
import com.community.post.service.FeedParams;
import com.community.post.service.PostFeedRows;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인기글 전체({@code GET /api/home/popular}) — 기간 탭(REALTIME 실시간 · YESTERDAY 어제 · WEEK 이번 주)의 순위. 비회원도 본다.
 *
 * <ul>
 *   <li>순위는 DB 배치 {@code popular-rank}(5분마다, {@code R__batch_24_popular_rank.sql})가 계산해 둔 스냅샷이다 — 점수·조건·채우기·글쓴이 상한은 그 파일의 서버 상수.
 *       pg_cron 이 없으면 스냅샷이 비어 늘 빈 목록이다.</li>
 *   <li>읽을 때 지금 상태로 다시 거른다(스냅샷 순서는 그대로, 쪽은 거른 뒤에 나눈다): 지움·임시저장·예약 삭제 시각 지남·신고 숨김·19금 글, 꺼진 게시판·「가젯 노출 금지」에 인기글(POPULAR)을 고른 게시판(핫딜·직접홍보는 이 값으로 시작)·운영진 전용 게시판
 *       (유효 글쓰기 자격 STAFF)의 글은 빠진다 — 배치의 후보 조건과 같다.</li>
 *   <li>{@code from}·{@code to} 는 이번 주 탭만(DB 시각으로 요청마다 계산 — 배치와 같은 SQL 함수). {@code weekStart} 는 늘 MONDAY. 자정 직후 한 배치 주기 동안은 스냅샷이 앞 기간의 것일 수 있다.</li>
 *   <li>틀린 값은 모아서 {@code 400 VALIDATION} — {@code fields.period} · {@code fields.page}(1 미만) · {@code fields.size}(1~50).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PopularService {

    public static final String REALTIME = "REALTIME";
    public static final String WEEK = "WEEK";
    private static final Set<String> PERIODS = Set.of(REALTIME, "YESTERDAY", WEEK);
    private static final String WEEK_START = "MONDAY";

    private final BoardCatalog catalog;
    private final PopularRankQueries ranks;
    private final PostFeedRepositoryQDSL feeds;
    private final PostFeedRows rows;

    public PopularService(BoardCatalog catalog, PopularRankQueries ranks, PostFeedRepositoryQDSL feeds, PostFeedRows rows) {
        this.catalog = catalog;
        this.ranks = ranks;
        this.feeds = feeds;
        this.rows = rows;
    }

    /**
     * @param viewerId 로그인한 회원(비로그인 null) — 줄의 {@code blocked}·작성자 {@code memo} 를 게시판 목록과 같게 싣는다(빼지 않고 표시만).
     *                 스냅샷(회원 공통)으로 순서·쪽을 정한 뒤 이 쪽의 줄을 만들 때 붙인다 — 스냅샷·순위에는 회원별 값이 들어가지 않는다
     */
    public PopularListResponse popular(String periodRaw, String pageRaw, String sizeRaw, Long viewerId) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        String period = periodRaw == null || periodRaw.isBlank() ? REALTIME : periodRaw.strip().toUpperCase(Locale.ROOT);
        if (!PERIODS.contains(period)) {
            fields.put("period", new MessageRef("popular.period.invalid"));
        }
        int page = FeedParams.pageFrom1(pageRaw, fields);
        int size = FeedParams.size(sizeRaw, fields);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }

        BoardCatalogSnapshot s = catalog.snapshot();
        Instant now = Instant.now();
        List<PopularRankQueries.Entry> entries = ranks.snapshot(period);
        Map<Long, PostRowData> found = feeds.visibleRows(entries.stream().map(PopularRankQueries.Entry::postId).toList(), now).stream()
                .collect(Collectors.toMap(PostRowData::id, Function.identity()));
        List<PostRowData> eligible = new ArrayList<>();
        Map<Long, String> fillOf = new LinkedHashMap<>();
        for (PopularRankQueries.Entry e : entries) {
            PostRowData r = found.get(e.postId());
            if (r != null && !Boolean.TRUE.equals(r.adult()) && rankable(s, r.boardId())) {
                eligible.add(r);
                if (e.fill() != null) {
                    fillOf.put(r.id(), e.fill());
                }
            }
        }

        int total = eligible.size();
        int totalPages = Math.max(1, (total + size - 1) / size);
        int fromIndex = (int) Math.min((long) (page - 1) * size, total);
        List<PostRowData> slice = eligible.subList(fromIndex, Math.min(fromIndex + size, total));
        List<PostFeedRowView> items = rows.of(slice, s, now, viewerId);
        Map<String, String> fills = new LinkedHashMap<>();
        for (PostFeedRowView item : items) {
            String fill = fillOf.get(Long.parseLong(item.id()));
            if (fill != null) {
                fills.put(item.id(), fill);
            }
        }
        PopularRankQueries.Range range = WEEK.equals(period) ? ranks.range(WEEK) : null;
        return new PopularListResponse(List.of(), items, total == 0 ? 1 : page, totalPages, total, page < totalPages, null, period, fills,
                range == null ? null : range.from(), range == null ? null : range.to(), WEEK_START);
    }

    /** 인기글에 오를 수 있는 게시판인가 — 켜져 있고 「가젯 노출 금지」에 인기글이 없으며 운영진 전용(유효 글쓰기 자격 STAFF)이 아니다(배치의 후보 조건과 같다) */
    private static boolean rankable(BoardCatalogSnapshot s, Long boardId) {
        EffectiveBoard b = boardId == null ? null : s.board(boardId).orElse(null);
        return b != null && b.active() && !b.excludedFrom(BoardGadget.POPULAR) && !BoardFields.STAFF.equals(b.writeMin());
    }
}
