package com.community.highlight.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.highlight.entity.HighlightEntity;
import com.community.highlight.repository.master.HighlightPeriodRepositoryQDSL;
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
 * 팁 묶음 「이번 주 추천」({@code TipsService})이 같이 쓴다(tips-contract 「결정」). 공개 「아추!」 페이지는 기간 안에 고른 것({@link #picked})을 읽는다.
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
    private final HighlightPeriodRepositoryQDSL periods;
    private final GadgetPageRepository pages;
    private final PostFeedRepositoryQDSL feeds;
    private final PostFeedRows rows;
    private final FileRepository files;
    private final ImageStorage storage;

    public HighlightPicks(HighlightRepository highlights, HighlightPeriodRepositoryQDSL periods, GadgetPageRepository pages, PostFeedRepositoryQDSL feeds,
                          PostFeedRows rows, FileRepository files, ImageStorage storage) {
        this.highlights = highlights;
        this.periods = periods;
        this.pages = pages;
        this.feeds = feeds;
        this.rows = rows;
        this.files = files;
        this.storage = storage;
    }

    /** 노출 중인 하이라이트 한 칸 — 글 줄과 운영자 대표 이미지 주소(없으면 null) */
    public record Pick(HighlightEntity highlight, PostFeedRowView post, String imageUrl) {
    }

    /** 가젯 설정의 곳 → 읽을 곳. 홈·팁이거나 있는 가젯 페이지 키면 그대로(시스템 페이지 TIPS 의 키는 팁), 아니면(모양 틀림·지운 페이지) home */
    public String resolve(String place) {
        if (!HighlightPlaces.wellFormed(place)) {
            return HighlightPlaces.HOME;
        }
        String key = HighlightPlaces.canonical(place);
        return HighlightPlaces.builtIn(key) || pages.existsByPageKey(key) ? key : HighlightPlaces.HOME;
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
        List<PostRowData> visible = visibleRows(showing, s, now, keep);
        Map<Long, PostFeedRowView> posts = rows.of(visible, s, now, viewerId).stream()
                .collect(Collectors.toMap(r -> Long.parseLong(r.id()), Function.identity()));
        Map<Long, String> imageUrls = imageUrls(showing);
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

    /**
     * 기간 안에 고른 하이라이트 한 쪽 — 공개 「아추!」 페이지({@code GET /api/highlights}, achu-page-contract 「기간 정책」).
     * 노출을 켠 것 중 고른 시각({@link HighlightEntity#pickedAt})이 {@code [from, to)} 이고 지금 이하인 것(노출 기간이 끝난 것도 든다), 고른 시각 최신순(같으면 id 큰 것 먼저).
     * 글 판정은 {@link #showing} 과 같다(지금 보이는 글·켜진 게시판·19금 아님). 거른 뒤에 쪽을 나누고 줄은 그 쪽 것만 만든다.
     *
     * @param offset   거른 목록에서 건너뛸 수
     * @param limit    쪽 크기
     * @param viewerId 로그인한 회원(비로그인 null) — 줄의 차단·메모
     * @return 그 쪽의 칸과 거른 뒤 전체 수
     */
    public PickPage picked(String place, Instant from, Instant to, BoardCatalogSnapshot s, Instant now, Long viewerId, int offset, int limit) {
        List<HighlightEntity> candidates = periods.pickedBetween(place, from, to, now);
        if (candidates.isEmpty()) {
            return new PickPage(List.of(), 0);
        }
        Map<Long, PostRowData> visible = visibleRows(candidates, s, now, null).stream()
                .filter(r -> !Boolean.TRUE.equals(r.adult()))
                .collect(Collectors.toMap(PostRowData::id, Function.identity()));
        List<HighlightEntity> eligible = candidates.stream().filter(h -> visible.containsKey(h.getPostId())).toList();
        int total = eligible.size();
        int fromIndex = Math.min(Math.max(offset, 0), total);
        List<HighlightEntity> slice = eligible.subList(fromIndex, Math.min(fromIndex + Math.max(limit, 0), total));
        if (slice.isEmpty()) {
            return new PickPage(List.of(), total);
        }
        // 한 곳에 같은 글은 한 번이라(곳마다 한 번) 글 줄은 하이라이트마다 하나다
        Map<Long, PostFeedRowView> posts = rows.of(slice.stream().map(h -> visible.get(h.getPostId())).toList(), s, now, viewerId).stream()
                .collect(Collectors.toMap(r -> Long.parseLong(r.id()), Function.identity()));
        Map<Long, String> imageUrls = imageUrls(slice);
        List<Pick> picks = new ArrayList<>();
        for (HighlightEntity h : slice) {
            PostFeedRowView post = posts.get(h.getPostId());
            if (post != null) {
                picks.add(new Pick(h, post, h.getImageFileId() == null ? null : imageUrls.get(h.getImageFileId())));
            }
        }
        return new PickPage(picks, total);
    }

    /** 기간 쪽 — 그 쪽의 칸과 거른 뒤 전체 수 */
    public record PickPage(List<Pick> picks, int total) {
    }

    /** 보이는 글 중 켜진 게시판의 것만(하이라이트를 고른 뒤 게시판을 끈 경우) — 19금 글은 누구에게나 뺀다(성인 아님 = false). 그 위에 부르는 쪽의 거름 */
    private List<PostRowData> visibleRows(List<HighlightEntity> list, BoardCatalogSnapshot s, Instant now, Predicate<PostRowData> keep) {
        return feeds.visibleRows(list.stream().map(HighlightEntity::getPostId).toList(), false, now).stream()
                .filter(r -> r.boardId() != null && s.board(r.boardId()).map(EffectiveBoard::active).orElse(false))
                .filter(r -> keep == null || keep.test(r)).toList();
    }

    /** 운영자 대표 이미지 파일 ID → 공개 주소 */
    private Map<Long, String> imageUrls(List<HighlightEntity> list) {
        List<Long> imageIds = list.stream().map(HighlightEntity::getImageFileId).filter(Objects::nonNull).toList();
        Map<Long, String> urls = new HashMap<>();
        if (!imageIds.isEmpty()) {
            files.findAllById(imageIds).forEach(f -> urls.put(f.getId(), storage.publicUrl(f.getStorageKey())));
        }
        return urls;
    }
}
