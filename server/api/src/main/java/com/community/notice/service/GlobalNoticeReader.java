package com.community.notice.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.notice.dto.NoticeGlobalPinRow;
import com.community.notice.dto.response.GlobalNoticeView;
import com.community.notice.entity.NoticeGlobalPinEntity;
import com.community.notice.repository.master.NoticeGlobalPinQueries;
import com.community.site.service.SiteNoticeBars;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 게시판 글 목록 맨 위의 전체 공지(V174) — docs/features/notice-admin-contract.md 「전체 공지」.
 *
 * <ul>
 *   <li>공지 게시판(slug {@value SiteNoticeBars#NOTICE_BOARD_SLUG}, 소모임 게시판 아님)이 없거나 꺼져 있으면 없다.</li>
 *   <li>살아 있는 전체 공지({@link NoticeGlobalPinQueries#live} — 지운 · 숨긴 · 고정이 풀린 공지는 빠진다)를 <b>짧은 메모리 사본</b>({@value #TTL_SECONDS}초, 게시판 사본과 같은 주기)으로
 *       읽는다 — 게시판 목록마다 DB 를 읽지 않는다. 관리자 앱에서 바꾸면 이 서버에는 최대 그만큼 늦게 닿는다.</li>
 *   <li>기간({@code expiresAt})은 사본을 쓸 때마다 지금 시각으로 다시 거른다(배치 없음).</li>
 *   <li>게시판마다: 공지 게시판 자신 · 뺀 게시판 · (소모임 게시판이면) 소모임 게시판 전체 빼기면 없다. 순서는 고정 시각 최근 것이 위, 최대 {@value NoticeGlobalPinEntity#MAX}개.</li>
 *   <li>읽다 실패하면 이전 사본을 쓰고 5초 뒤 다시 시도한다(사본이 없으면 빈 목록 — 글 목록 자체는 깨지 않는다).</li>
 * </ul>
 */
@Component
public class GlobalNoticeReader {

    private static final Logger log = LoggerFactory.getLogger(GlobalNoticeReader.class);

    static final int TTL_SECONDS = 15;
    /** 사본에 넉넉히 받는 수 — 한도는 {@value NoticeGlobalPinEntity#MAX} 이지만 동시에 고쳐 잠깐 넘는 경우를 견딘다 */
    static final int LOAD_LIMIT = 20;
    private static final long TTL_NANOS = Duration.ofSeconds(TTL_SECONDS).toNanos();
    private static final long RETRY_NANOS = Duration.ofSeconds(5).toNanos();

    private final BoardCatalog catalog;
    private final NoticeGlobalPinQueries queries;
    private volatile Cached cached;

    public GlobalNoticeReader(BoardCatalog catalog, NoticeGlobalPinQueries queries) {
        this.catalog = catalog;
        this.queries = queries;
    }

    /** 이 게시판 글 목록 맨 위에 걸 전체 공지(없으면 빈 목록) */
    public List<GlobalNoticeView> forBoard(EffectiveBoard board, Instant now) {
        BoardCatalogSnapshot s = catalog.snapshot();
        Optional<EffectiveBoard> notice = noticeBoard(s);
        if (notice.isEmpty() || board.id() == notice.get().id()) {
            return List.of();
        }
        long noticeId = notice.get().id();
        boolean group = BoardFields.KIND_GROUP.equals(board.kind());
        return rows(noticeId).stream()
                .filter(r -> r.showsOn(board.id(), group, noticeId, now))
                .limit(NoticeGlobalPinEntity.MAX)
                .map(r -> new GlobalNoticeView(Long.toString(r.postId()), r.title(), r.createdAt()))
                .toList();
    }

    /** 공지 게시판 — 켜져 있고 소모임 게시판이 아닌 것(공지 띠와 같은 판정) */
    private static Optional<EffectiveBoard> noticeBoard(BoardCatalogSnapshot s) {
        return s.board(SiteNoticeBars.NOTICE_BOARD_SLUG).filter(b -> b.active() && !BoardFields.KIND_GROUP.equals(b.kind()));
    }

    private List<NoticeGlobalPinRow> rows(long noticeBoardId) {
        Cached current = cached;
        if (fresh(current, noticeBoardId, System.nanoTime())) {
            return current.rows;
        }
        synchronized (this) {
            current = cached;
            long nanos = System.nanoTime();
            if (fresh(current, noticeBoardId, nanos)) {
                return current.rows;
            }
            try {
                List<NoticeGlobalPinRow> loaded = queries.live(noticeBoardId, Instant.now(), LOAD_LIMIT);
                cached = new Cached(noticeBoardId, loaded, nanos);
                return loaded;
            } catch (RuntimeException e) {
                if (current == null || current.boardId != noticeBoardId) {
                    log.warn("전체 공지를 읽지 못해 비운다: {}", e.toString());
                    cached = new Cached(noticeBoardId, List.of(), nanos - TTL_NANOS + RETRY_NANOS);
                    return List.of();
                }
                log.warn("전체 공지 사본을 다시 읽지 못해 이전 값을 쓴다: {}", e.toString());
                cached = new Cached(current.boardId, current.rows, nanos - TTL_NANOS + RETRY_NANOS);
                return current.rows;
            }
        }
    }

    private static boolean fresh(Cached c, long noticeBoardId, long nanos) {
        return c != null && c.boardId == noticeBoardId && nanos - c.loadedAt < TTL_NANOS;
    }

    private record Cached(long boardId, List<NoticeGlobalPinRow> rows, long loadedAt) {
    }
}
