package com.community.post.service;

import com.community.block.service.ViewerBlocks;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.AuthorViews;
import com.community.board.service.BoardCatalog;
import com.community.board.service.ContentRules;
import com.community.board.service.PublicBoardService;
import com.community.post.dto.PostNeighborData;
import com.community.post.dto.PostRowData;
import com.community.post.dto.response.PostAuthorView;
import com.community.post.dto.response.PostBoardRefView;
import com.community.post.dto.response.PostDetailView;
import com.community.post.dto.response.PostListResponse;
import com.community.post.dto.response.PostNeighborView;
import com.community.post.dto.response.PostPrefixRefView;
import com.community.post.dto.response.PostProtectionView;
import com.community.post.dto.response.PostRowView;
import com.community.post.entity.PostEntity;
import com.community.post.exception.PostHiddenException;
import com.community.post.exception.PostNotFoundException;
import com.community.post.repository.master.PostCounterQueries;
import com.community.post.repository.master.PostRepository;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.report.entity.ReportReasons;
import com.community.user.entity.ProfileEntity;
import com.community.zconfiguration.crypto.Digests;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 읽기 — 게시판 글 목록({@code GET /api/boards/{slug}/posts})과 글 하나({@code GET /api/posts/{id}}, 조회수 증가).
 *
 * <ul>
 *   <li>목록: 고정 공지는 첫 쪽이고 말머리·검색어가 없을 때만 위에 따로(최대 유효 {@code pinnedMax}), 그때 목록에서는 뺀다. 신고로 숨긴 글은 <b>자리만</b>(제목·작성자 이름 비움,
 *       {@code hiddenReason = REPORTED}) 남고 검색 결과에서는 빠진다. 정렬은 id(= 작성 시각) 순.</li>
 *   <li>글 하나: 숨긴 글 {@code 403 POST_HIDDEN}, 없거나 지운 글(예약 삭제 시각이 지난 글 포함) {@code 404 POST_NOT_FOUND}, 꺼진 게시판 {@code 403 BOARD_INACTIVE}.</li>
 *   <li>조회수: 같은 회원(비회원은 IP 의 해시)이 보안 설정 {@code viewCountDedupHours} 시간 안에 다시 보면 세지 않는다({@code post_views} 기록, 정리는 DB 배치 board-post-upkeep).</li>
 * </ul>
 */
@Service
public class PostQueryService {

    private final BoardCatalog catalog;
    private final PostRepository posts;
    private final PostRepositoryQDSL postQueries;
    private final PostCounterQueries counters;
    private final AuthorViews authors;
    private final ViewerBlocks viewerBlocks;

    public PostQueryService(BoardCatalog catalog, PostRepository posts, PostRepositoryQDSL postQueries, PostCounterQueries counters, AuthorViews authors,
                            ViewerBlocks viewerBlocks) {
        this.catalog = catalog;
        this.posts = posts;
        this.postQueries = postQueries;
        this.counters = counters;
        this.authors = authors;
        this.viewerBlocks = viewerBlocks;
    }

    /**
     * @param prefix 말머리 ID(문자열) — 비면 전체. 이 게시판의 말머리가 아니면 빈 목록
     * @param sort   LATEST(기본) · OLDEST
     * @param q      게시판 안 검색어(제목·본문·작성자 이름) — 비면 검색하지 않는다. {@value PostRepositoryQDSL#QUERY_MAX}자에서 자른다
     * @param page   1부터
     */
    @Transactional(readOnly = true)
    public PostListResponse list(String slug, String prefix, String sort, String q, int page) {
        return list(slug, prefix, sort, q, page, null);
    }

    /**
     * 보는 사람의 차단·메모를 싣는 글 목록 — 줄의 {@code blocked}(차단한 작성자 {@code USER} · 제목의 차단 단어 {@code WORD}, 내 글·숨긴 글은 null)와 작성자 {@code memo}.
     * 목록 하나에 차단·단어·메모 질의를 한 번씩만 한다({@link ViewerBlocks}).
     *
     * @param viewerId 로그인한 회원(비로그인 null — 두 칸 모두 null)
     */
    @Transactional(readOnly = true)
    public PostListResponse list(String slug, String prefix, String sort, String q, int page, Long viewerId) {
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = PublicBoardService.requireActive(s, slug);
        Instant now = Instant.now();
        int listSize = b.listSize();
        int current = Math.max(1, page);

        Long prefixId = null;
        boolean impossible = false;
        if (prefix != null && !prefix.isBlank()) {
            prefixId = parseId(prefix);
            impossible = prefixId == null || b.prefix(prefixId).isEmpty();
        }
        String query = ContentRules.normalize(q);
        if (query.length() > PostRepositoryQDSL.QUERY_MAX) {
            query = query.substring(0, PostRepositoryQDSL.QUERY_MAX);
        }
        String search = query.isEmpty() ? null : query;
        boolean plain = prefixId == null && search == null && !impossible;
        boolean oldest = "OLDEST".equals(sort);

        List<PostRowData> pinned = plain && current == 1 ? postQueries.pinned(b.id(), b.pinnedMax(), now) : List.of();
        PostRepositoryQDSL.Page result = impossible ? new PostRepositoryQDSL.Page(List.of(), 0)
                : postQueries.page(b.id(), prefixId, search, oldest, plain, (long) (current - 1) * listSize, listSize, now);

        List<Long> authorIds = Stream.concat(pinned.stream(), result.items().stream()).map(PostRowData::authorId).toList();
        Map<Long, ProfileEntity> members = authors.members(authorIds);
        ViewerBlocks.Lens lens = viewerBlocks.lens(viewerId, authorIds);
        int totalPages = (int) ((result.total() + listSize - 1) / listSize);
        return new PostListResponse(pinned.stream().map(r -> row(r, b, s, members, now, lens)).toList(),
                result.items().stream().map(r -> row(r, b, s, members, now, lens)).toList(), current, totalPages, result.total());
    }

    private PostRowView row(PostRowData r, EffectiveBoard b, BoardCatalogSnapshot s, Map<Long, ProfileEntity> members, Instant now, ViewerBlocks.Lens lens) {
        boolean hidden = r.hiddenAt(now);
        PostAuthorView author = authors.of(r.authorId(), r.guestName(), r.authorIp(), members, s).withMemo(lens.memoOf(r.authorId()));
        return new PostRowView(Long.toString(r.id()), hidden ? null : prefixRef(b, r.prefixId()), hidden ? "" : r.title(), hidden ? 0 : r.commentCount(),
                !hidden && Boolean.TRUE.equals(r.hasImage()), null, hidden ? AuthorViews.blank(author) : author, r.createdAt(), r.viewCount(), r.likeCount(),
                r.pinnedAt() != null, !hidden && Boolean.TRUE.equals(r.adult()), hidden ? ReportReasons.HIDDEN_REPORTED : null,
                hidden ? null : lens.blockedOf(r.authorId(), r.title()));
    }

    /**
     * 글 하나 — 볼 때 조회수가 오른다(중복 방지). 쓰기가 있어 읽기 전용 트랜잭션이 아니다.
     *
     * @param userId 로그인한 회원(비회원 null)
     * @param ip     요청한 사람의 IP(비회원 조회 중복 판정에 해시로만 쓴다)
     */
    @Transactional
    public PostDetailView detail(String idText, Long userId, String ip) {
        Instant now = Instant.now();
        PostEntity post = requireVisible(idText, now);
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = PublicBoardService.requireActive(s, post.getBoardId());
        if (post.isHiddenAt(now)) {
            throw new PostHiddenException();
        }
        int dedupHours = s.security().viewCountDedupHours();
        String viewer = userId != null ? "m:" + userId : "g:" + Digests.sha256Hex(ip == null ? "" : ip);
        int viewCount = counters.recordView(post.getId(), viewer, now.minus(Duration.ofHours(dedupHours))) ? counters.incrementViews(post.getId())
                : post.getViewCount();
        boolean liked = userId != null && counters.postLiked(post.getId(), userId);
        List<Long> authorIds = post.getAuthorId() == null ? List.of() : List.of(post.getAuthorId());
        return view(post, b, s, userId, viewCount, liked, now, viewerBlocks.lens(userId, authorIds));
    }

    /** 고친 뒤의 글(조회수는 그대로) — {@link PostWriteService#update} 가 같은 트랜잭션에서 부른다. 차단·메모는 싣지 않는다(응답 모양 그대로, 두 칸 null) */
    PostDetailView viewAfterEdit(PostEntity post, EffectiveBoard b, BoardCatalogSnapshot s, Long userId, Instant now) {
        boolean liked = userId != null && counters.postLiked(post.getId(), userId);
        return view(post, b, s, userId, post.getViewCount(), liked, now, ViewerBlocks.Lens.none());
    }

    /** @param lens 보는 사람의 차단·메모 — 글 {@code blocked}(제목 단어 판정, 게시판 글 목록과 같음)와 작성자 {@code memo} */
    private PostDetailView view(PostEntity post, EffectiveBoard b, BoardCatalogSnapshot s, Long userId, int viewCount, boolean liked, Instant now,
                                ViewerBlocks.Lens lens) {
        Map<Long, ProfileEntity> members = authors.members(post.getAuthorId() == null ? List.of() : List.of(post.getAuthorId()));
        Optional<PostNeighborData> older = postQueries.older(b.id(), post.getId(), now);
        Optional<PostNeighborData> newer = postQueries.newer(b.id(), post.getId(), now);
        PostAuthorView author = authors.of(post.getAuthorId(), post.getGuestName(), post.getAuthorIp(), members, s).withMemo(lens.memoOf(post.getAuthorId()));
        return new PostDetailView(Long.toString(post.getId()), new PostBoardRefView(Long.toString(b.id()), b.slug(), b.name(), b.nameEn(), b.color()),
                prefixRef(b, post.getPrefixId()), post.getTitle(), post.getContentFormat().name(), post.getContent(),
                author, post.getCreatedAt(), post.getEditedAt(), viewCount,
                post.getLikeCount(), liked, post.getCommentCount(), post.isAdult(), userId != null && userId.equals(post.getAuthorId()),
                protection(b, post.getCommentCount()), post.getDeleteScheduledAt(), older.map(n -> new PostNeighborView(Long.toString(n.id()), n.title())).orElse(null),
                newer.map(n -> new PostNeighborView(Long.toString(n.id()), n.title())).orElse(null), Boolean.TRUE.equals(s.security().externalLinkNofollow()),
                lens.blockedOf(post.getAuthorId(), post.getTitle()));
    }

    /** 댓글 많은 글 보호 — 보이는 댓글 수가 기준 이상이면 보호 중 */
    static PostProtectionView protection(EffectiveBoard b, int visibleComments) {
        boolean active = b.commentProtectMin() > 0 && visibleComments >= b.commentProtectMin();
        return new PostProtectionView(active, active && b.commentProtectEdit(), b.commentProtectDelete(), b.commentProtectDelayMinutes(),
                b.commentProtectMin());
    }

    /** 보이는 글 — 형식이 틀리거나 없거나 지웠으면(예약 삭제 시각이 지났거나 임시저장 포함) 404 */
    public PostEntity requireVisible(String idText, Instant now) {
        Long id = parseId(idText);
        PostEntity post = id == null ? null : posts.findById(id).orElse(null);
        if (post == null || post.isGoneAt(now)) {
            throw new PostNotFoundException();
        }
        return post;
    }

    static PostPrefixRefView prefixRef(EffectiveBoard b, Long prefixId) {
        Optional<BoardPrefixInfo> prefix = b.prefix(prefixId);
        return prefix.map(p -> new PostPrefixRefView(Long.toString(p.id()), p.name(), p.staffOnly(), p.color())).orElse(null);
    }

    /** 문자열 ID → long. 형식이 틀리면 null */
    public static Long parseId(String raw) {
        if (raw == null || raw.isBlank() || raw.strip().length() > 19) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.strip());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
