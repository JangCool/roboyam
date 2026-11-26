package com.community.highlight.service;

import com.community.adultboard.service.AdultBoardGate;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.board.service.PublicBoardService;
import com.community.cache.definition.CacheNames;
import com.community.highlight.dto.response.BoardRecommendItemView;
import com.community.highlight.dto.response.BoardRecommendView;
import com.community.highlight.entity.HighlightBoardSettingEntity;
import com.community.highlight.entity.HighlightEntity;
import com.community.highlight.repository.master.BoardRecommendQueries;
import com.community.highlight.repository.master.HighlightBoardSettingRepository;
import com.community.highlight.repository.master.HighlightRepository;
import com.community.post.dto.PostRowData;
import com.community.post.repository.master.PostFeedRepositoryQDSL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시판 목록 위 「추천 글」 카드 — docs/features/board-recommend-contract.md 「규칙」. 비회원도 본다.
 *
 * <ul>
 *   <li><b>게시판 판정</b>(캐시 앞, 요청마다): 게시판 글 목록과 같다 — 없거나 소모임 게시판 {@code 404 BOARD_NOT_FOUND}, 꺼짐 {@code 403 BOARD_INACTIVE}, 성인 전용 게시판은
 *       {@link AdultBoardGate#requireBoard}(숨김 404 · 핀 필요 403). 일반 게시판({@code GENERAL})이 아니면(핫딜·홍보) 빈 목록.</li>
 *   <li><b>고른 글</b>: 그 게시판 곳({@code board-{boardId}}) 하이라이트 중 노출 중({@link HighlightEntity#isShowingAt})이고 글이 지금 보이는 것(지움·임시저장·예약 삭제 지남·
 *       신고 숨김·비밀글·비멤버에게 안 보이는 소모임 글·19금 아님 — {@link PostFeedRepositoryQDSL#visibleRows}) 중 그 게시판의 글, 관리자 순서.</li>
 *   <li><b>자동 채움</b>(게시판별 스위치 — 행이 없으면 켬): 모자라면 최근 {@value #RECENT_DAYS}일 추천순 → 그래도 모자라면 최근 {@value #FALLBACK_DAYS}일 추천순.
 *       추천 1 이상 · 공지(고정) 아님 · 거름은 위와 같다({@link BoardRecommendQueries}). 추천이 같으면 최신 글 먼저.</li>
 *   <li>최대 {@value HighlightPlaces#BOARD_SHOWN}장, 같은 글은 한 번.</li>
 *   <li><b>캐시</b> {@code boards.recommended}(항목 키 = 게시판 ID, 기본 60초 — 줄에 회원별 칸이 없어 공용). 하이라이트 저장·자동 채우기 스위치·글 숨김/삭제·게시판 저장 때
 *       관리자 저장이 세대 번호를 올려 모든 서버가 비운다(cache-admin-contract). 회원의 새 추천은 TTL 에 맡긴다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class BoardRecommendService {

    static final int RECENT_DAYS = 7;
    static final int FALLBACK_DAYS = 30;

    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private final BoardCatalog catalog;
    private final AdultBoardGate adultBoards;
    private final HighlightRepository highlights;
    private final HighlightBoardSettingRepository settings;
    private final PostFeedRepositoryQDSL feeds;
    private final BoardRecommendQueries queries;
    private final CacheManager caches;

    public BoardRecommendService(BoardCatalog catalog, AdultBoardGate adultBoards, HighlightRepository highlights, HighlightBoardSettingRepository settings,
                                 PostFeedRepositoryQDSL feeds, BoardRecommendQueries queries, CacheManager caches) {
        this.catalog = catalog;
        this.adultBoards = adultBoards;
        this.highlights = highlights;
        this.settings = settings;
        this.feeds = feeds;
        this.queries = queries;
        this.caches = caches;
    }

    /** 그 게시판의 추천 글 카드 — 게시판 판정은 요청마다, 카드는 캐시에서 */
    public BoardRecommendView recommended(String slug, Long viewerId) {
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = adultBoards.requireBoard(PublicBoardService.requireActive(s, slug), viewerId);
        if (!BoardFields.KIND_GENERAL.equals(b.kind())) {
            return BoardRecommendView.EMPTY;
        }
        return caches.getCache(CacheNames.BOARDS_RECOMMENDED).get(Long.toString(b.id()), () -> build(b));
    }

    /** 공용 카드(보는 사람 없이) */
    private BoardRecommendView build(EffectiveBoard b) {
        Instant now = Instant.now();
        int max = HighlightPlaces.BOARD_SHOWN;
        List<BoardRecommendItemView> items = new ArrayList<>();
        Set<Long> taken = new HashSet<>();

        // 고른 글 — 관리자 순서
        List<HighlightEntity> showing = highlights.findAllByPlaceOrderBySortOrderAscIdAsc(HighlightPlaces.boardPlace(b.id())).stream()
                .filter(h -> h.isShowingAt(now)).toList();
        if (!showing.isEmpty()) {
            Map<Long, PostRowData> visible = feeds.visibleRows(showing.stream().map(HighlightEntity::getPostId).toList(), false, now).stream()
                    .filter(r -> Long.valueOf(b.id()).equals(r.boardId()) && !Boolean.TRUE.equals(r.adult()))
                    .collect(Collectors.toMap(PostRowData::id, Function.identity()));
            for (HighlightEntity h : showing) {
                PostRowData r = visible.get(h.getPostId());
                if (r == null || !taken.add(r.id())) {
                    continue;
                }
                String title = h.getTitle() == null || h.getTitle().isBlank() ? r.title() : h.getTitle();
                items.add(new BoardRecommendItemView(Long.toString(r.id()), title, oneLine(h.getSummary()), BoardRecommendItemView.PICKED,
                        r.likeCount() == null ? 0 : r.likeCount(), prefixName(b, r.prefixId())));
                if (items.size() >= max) {
                    return new BoardRecommendView(List.copyOf(items));
                }
            }
        }

        // 자동 채움 — 최근 7일 추천순 → 최근 30일 추천순
        boolean autoFill = settings.findById(b.id()).map(HighlightBoardSettingEntity::isAutoFill).orElse(HighlightBoardSettingEntity.DEFAULT_AUTO_FILL);
        if (autoFill) {
            for (int days : new int[] {RECENT_DAYS, FALLBACK_DAYS}) {
                List<BoardRecommendQueries.Row> rows = queries.mostLiked(b.id(), now.minus(Duration.ofDays(days)), taken, max - items.size(), now);
                for (BoardRecommendQueries.Row r : rows) {
                    if (taken.add(r.id())) {
                        items.add(new BoardRecommendItemView(Long.toString(r.id()), r.title(), null, BoardRecommendItemView.AUTO,
                                r.likeCount() == null ? 0 : r.likeCount(), prefixName(b, r.prefixId())));
                    }
                }
                if (items.size() >= max) {
                    break;
                }
            }
        }
        return items.isEmpty() ? BoardRecommendView.EMPTY : new BoardRecommendView(List.copyOf(items));
    }

    private static String prefixName(EffectiveBoard b, Long prefixId) {
        return b.prefix(prefixId).map(BoardPrefixInfo::name).orElse(null);
    }

    /** 요약을 한 줄로 — 줄바꿈·연속 공백을 한 칸으로, 비면 null */
    static String oneLine(String summary) {
        if (summary == null) {
            return null;
        }
        String text = SPACES.matcher(summary).replaceAll(" ").strip();
        return text.isEmpty() ? null : text;
    }
}
