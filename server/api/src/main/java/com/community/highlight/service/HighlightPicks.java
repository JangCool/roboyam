package com.community.highlight.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.highlight.entity.HighlightEntity;
import com.community.highlight.repository.master.HighlightRepository;
import com.community.home.repository.master.GadgetPageRepository;
import com.community.media.repository.master.FileRepository;
import com.community.media.service.ImageStorage;
import com.community.post.dto.PostRowData;
import com.community.post.dto.response.PostFeedRowView;
import com.community.post.repository.master.PostFeedRepositoryQDSL;
import com.community.post.service.PostFeedRows;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 공개 쪽 하이라이트 읽기 — 한 곳(V161 {@code place})의 노출 중인 하이라이트를 관리자 순서로. 홈·가젯 페이지의 {@code HIGHLIGHT} 가젯({@code GadgetService})과
 * 팁 묶음 「이번 주 추천」({@code TipsService})이 같이 쓴다(tips-contract 「결정」).
 *
 * <ul>
 *   <li>노출 중 = 노출 켬 + 기간 안({@link HighlightEntity#isShowingAt}).</li>
 *   <li>글은 지금 보이는 것만 — 지움·임시저장·예약 삭제 지남·신고 숨김 아님·비멤버에게 보이는 소모임 글({@link PostFeedRepositoryQDSL#visibleRows}), 켜진 게시판,
 *       19금이 아닌 글(성인에게도 빼는 것은 예전 가젯과 같다). 그 위에 부르는 쪽의 거름({@code keep}).</li>
 *   <li>줄 모양은 섞인 목록 줄({@link PostFeedRows} — 보는 사람의 차단·메모 포함)이고, 운영자가 올린 대표 이미지 주소를 함께 준다.</li>
 * </ul>
 */
@Component
public class HighlightPicks {

    private final HighlightRepository highlights;
    private final GadgetPageRepository pages;
    private final PostFeedRepositoryQDSL feeds;
    private final PostFeedRows rows;
    private final FileRepository files;
    private final ImageStorage storage;

    public HighlightPicks(HighlightRepository highlights, GadgetPageRepository pages, PostFeedRepositoryQDSL feeds, PostFeedRows rows, FileRepository files,
                          ImageStorage storage) {
        this.highlights = highlights;
        this.pages = pages;
        this.feeds = feeds;
        this.rows = rows;
        this.files = files;
        this.storage = storage;
    }

    /** 노출 중인 하이라이트 한 칸 — 글 줄과 운영자 대표 이미지 주소(없으면 null) */
    public record Pick(HighlightEntity highlight, PostFeedRowView post, String imageUrl) {
    }

    /** 가젯 설정의 곳 → 읽을 곳. 홈·팁이거나 있는 가젯 페이지 키면 그대로, 아니면(모양 틀림·지운 페이지) home */
    public String resolve(String place) {
        if (!HighlightPlaces.wellFormed(place)) {
            return HighlightPlaces.HOME;
        }
        return HighlightPlaces.builtIn(place) || pages.existsByPageKey(place) ? place : HighlightPlaces.HOME;
    }

    /**
     * 그 곳의 노출 중인 하이라이트(관리자 순서) 최대 {@code limit} 개.
     *
     * @param keep     글 거름(null = 모두) — 예: 팁 묶음은 고른 게시판·공지 아님
     * @param viewerId 로그인한 회원(비로그인 null) — 줄의 차단·메모
     */
    public List<Pick> showing(String place, BoardCatalogSnapshot s, Instant now, Long viewerId, Predicate<PostRowData> keep, int limit) {
        List<HighlightEntity> showing = highlights.findAllByPlaceOrderBySortOrderAscIdAsc(place).stream().filter(h -> h.isShowingAt(now)).toList();
        if (showing.isEmpty() || limit <= 0) {
            return List.of();
        }
        // 보이는 글 중 켜진 게시판의 것만(하이라이트를 고른 뒤 게시판을 끈 경우) — 19금 글은 누구에게나 뺀다(성인 아님 = false)
        List<PostRowData> visible = feeds.visibleRows(showing.stream().map(HighlightEntity::getPostId).toList(), false, now).stream()
                .filter(r -> r.boardId() != null && s.board(r.boardId()).map(EffectiveBoard::active).orElse(false))
                .filter(r -> keep == null || keep.test(r)).toList();
        Map<Long, PostFeedRowView> posts = rows.of(visible, s, now, viewerId).stream()
                .collect(Collectors.toMap(r -> Long.parseLong(r.id()), Function.identity()));
        List<Long> imageIds = showing.stream().map(HighlightEntity::getImageFileId).filter(Objects::nonNull).toList();
        Map<Long, String> imageUrls = new HashMap<>();
        if (!imageIds.isEmpty()) {
            files.findAllById(imageIds).forEach(f -> imageUrls.put(f.getId(), storage.publicUrl(f.getStorageKey())));
        }
        List<Pick> picks = new ArrayList<>();
        for (HighlightEntity h : showing) {
            PostFeedRowView post = posts.get(h.getPostId());
            if (post == null || post.adult()) {
                continue;
            }
            picks.add(new Pick(h, post, h.getImageFileId() == null ? null : imageUrls.get(h.getImageFileId())));
            if (picks.size() >= limit) {
                break;
            }
        }
        return picks;
    }
}
