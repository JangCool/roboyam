package com.community.home.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.entity.BoardGadget;
import com.community.board.service.BoardCatalog;
import com.community.post.dto.PostRowData;
import com.community.post.dto.response.PostFeedPageView;
import com.community.post.dto.response.PostFeedRowView;
import com.community.post.repository.master.PostFeedRepositoryQDSL;
import com.community.post.service.FeedParams;
import com.community.post.service.PostFeedRows;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최신글 전체({@code GET /api/home/latest}) — 켜진 모든 게시판의 글을 최신순으로, 커서(마지막 글 ID)로 이어 받는다. 비회원도 본다.
 *
 * <ul>
 *   <li>나오는 글: 켜진 게시판(공개 게시판은 누구나 읽는다 — 읽기 제한이 없다)의 보이는 글(지움·임시저장·예약 삭제 시각 지남 제외)이고 신고로 숨기지 않은 글.
 *       19금 글은 게시판 목록과 같은 표시({@code adult = true})로 나온다. 운영진 전용 게시판의 글도 나온다(읽기는 누구나).
 *       게시판 설정 「가젯 노출 금지」에 최신글(LATEST)을 고른 게시판의 글은 빠진다(게시판 사본 기준 — 관리자 저장 뒤 최대 사본 주기만큼 늦게 반영).
 *       소모임 글은 비멤버에게 보이는 것(공개 소모임의 글·개별 공개된 글)만 쓴 시각 자리에 섞이고 줄의 {@code group} 이 소모임을 말한다(소모임 6단계 — community api-groups.md 8절).</li>
 *   <li>틀린 값은 모아서 {@code 400 VALIDATION} — {@code fields.size}(1~50) · {@code fields.cursor}(글 ID 형식이 아님).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class LatestFeedService {

    private final BoardCatalog catalog;
    private final PostFeedRepositoryQDSL feeds;
    private final PostFeedRows rows;

    public LatestFeedService(BoardCatalog catalog, PostFeedRepositoryQDSL feeds, PostFeedRows rows) {
        this.catalog = catalog;
        this.feeds = feeds;
        this.rows = rows;
    }

    /** @param viewerId 로그인한 회원(비로그인 null) — 줄의 {@code blocked}·작성자 {@code memo} 를 게시판 목록과 같게 싣는다(빼지 않고 표시만) */
    public PostFeedPageView latest(String sizeRaw, String cursorRaw, Long viewerId) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        int size = FeedParams.size(sizeRaw, fields);
        Long cursor = FeedParams.cursor(cursorRaw, fields);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        BoardCatalogSnapshot s = catalog.snapshot();
        Instant now = Instant.now();
        // 소모임 게시판도 넣는다 — 비멤버에게 보이는 소모임 글만 줄에 소모임 표시(group)를 달고 섞인다(소모임 6단계, 거름은 PostRepositoryQDSL#feedVisible)
        List<Long> boardIds = s.boards().stream().filter(b -> b.active() && !b.excludedFrom(BoardGadget.LATEST)).map(EffectiveBoard::id).toList();
        List<PostRowData> found = feeds.latest(boardIds, cursor, size + 1, now);
        boolean hasNext = found.size() > size;
        List<PostRowData> page = hasNext ? found.subList(0, size) : found;
        List<PostFeedRowView> items = rows.of(page, s, now, viewerId);
        String next = hasNext ? Long.toString(page.getLast().id()) : null;
        return new PostFeedPageView(items, hasNext, next);
    }
}
