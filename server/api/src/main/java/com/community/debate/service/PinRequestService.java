package com.community.debate.service;

import com.community.block.service.ViewerBlocks;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.exception.NotAuthorException;
import com.community.board.service.AuthorMembers;
import com.community.board.service.AuthorViews;
import com.community.board.service.BoardCatalog;
import com.community.board.service.PublicBoardService;
import com.community.board.service.RichText;
import com.community.comment.entity.CommentEntity;
import com.community.comment.exception.CommentNotFoundException;
import com.community.comment.repository.master.CommentRepository;
import com.community.comment.repository.master.CommentRepositoryQDSL;
import com.community.debate.dto.request.PinRequestRejectRequest;
import com.community.debate.dto.response.PinRequestListView;
import com.community.debate.dto.response.PinRequestReviewItemView;
import com.community.debate.dto.response.PinRequestView;
import com.community.debate.entity.DebatePinRequestEntity;
import com.community.debate.exception.DebateNotOpenException;
import com.community.debate.exception.PinRequestException;
import com.community.debate.repository.master.DebatePinRequestRepository;
import com.community.debate.repository.master.DebateQueries;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.post.entity.PostEntity;
import com.community.post.exception.PostHiddenException;
import com.community.post.repository.master.PostRepository;
import com.community.post.service.AnonymousAuthors;
import com.community.post.service.PostQueryService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 의견 고정 요청의 공개 API(사용자 결정 2026-10-24 — docs/features/popular-highlight-contract.md 「서버 구현 결과」). 모두 로그인 회원만(401).
 *
 * <ul>
 *   <li><b>보내기</b>({@code POST /api/comments/{commentId}/pin-request}): 내가 쓴 의견(입장 있는 원댓글)만. 차례: 댓글 404 → 글·게시판 읽기 규칙 → 내 댓글(403) → 의견인가(409
 *       {@code PIN_REQUEST_NOT_OPINION}) → 숨김(409 {@code OPINION_UNAVAILABLE}) → 토론 열림(409 {@code DEBATE_NOT_OPEN}) → 게시판 고정 요청 켬(409 {@code PIN_REQUEST_OFF})
 *       → 추천 수가 기본 설정 {@code pinRequestMinLikes} 이상(409 {@code PIN_REQUEST_NOT_ENOUGH_LIKES}, V200 — 0 이면 검사 없음)
 *       → 이미 고정(409 {@code OPINION_ALREADY_PINNED}) → 대기 요청 있음(409 {@code PIN_REQUEST_EXISTS}). 거절된 뒤에는 다시 보낼 수 있다. 확인할 수 있는 회원(글쓴이·게시판 매니저 —
 *       나 제외)에게 회원 알림 {@code OPINION_PIN_REQUESTED}, 정책에 OPERATOR 가 있으면 관리자 알림 {@code OPINION_PIN_REQUEST_PENDING}.</li>
 *   <li><b>취소</b>({@code DELETE /api/comments/{commentId}/pin-request}): 내 대기 요청을 지운다(없으면 그대로 204).</li>
 *   <li><b>확인할 목록</b>({@code GET /api/posts/{postId}/pin-requests}): 그 글의 확인 권한자(정책의 글쓴이·게시판 매니저)만 — 아니면 403 {@code PIN_REQUEST_FORBIDDEN}.</li>
 *   <li><b>승인·거절</b>({@code POST /api/pin-requests/{id}/approve|reject}): 확인 권한자만(자기 요청은 403), 규칙은 {@link com.community.debate.service.DebatePinDecisions}.
 *       거절 사유 100자 초과 400 {@code fields.reason}.</li>
 * </ul>
 */
@Service
public class PinRequestService {

    private static final Logger log = LoggerFactory.getLogger(PinRequestService.class);
    static final int EXCERPT_MAX = 200;

    private final BoardCatalog catalog;
    private final PostQueryService postQueries;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final CommentRepositoryQDSL commentQueries;
    private final DebatePinRequestRepository requests;
    private final DebateQueries queries;
    private final DebateReader reader;
    private final PinReviewers reviewers;
    private final DebatePinDecisions decisions;
    private final MemberNotificationPublisher memberNotices;
    private final NotificationPublisher operatorNotices;
    private final AuthorViews authors;
    private final ViewerBlocks viewerBlocks;
    private final SnowflakeIdGenerator ids;
    private final AnonymousAuthors anonymousAuthors;

    public PinRequestService(BoardCatalog catalog, PostQueryService postQueries, PostRepository posts, CommentRepository comments,
                             CommentRepositoryQDSL commentQueries, DebatePinRequestRepository requests, DebateQueries queries, DebateReader reader,
                             PinReviewers reviewers, DebatePinDecisions decisions, MemberNotificationPublisher memberNotices,
                             NotificationPublisher operatorNotices, AuthorViews authors, ViewerBlocks viewerBlocks, SnowflakeIdGenerator ids,
                             AnonymousAuthors anonymousAuthors) {
        this.anonymousAuthors = anonymousAuthors;
        this.catalog = catalog;
        this.postQueries = postQueries;
        this.posts = posts;
        this.comments = comments;
        this.commentQueries = commentQueries;
        this.requests = requests;
        this.queries = queries;
        this.reader = reader;
        this.reviewers = reviewers;
        this.decisions = decisions;
        this.memberNotices = memberNotices;
        this.operatorNotices = operatorNotices;
        this.authors = authors;
        this.viewerBlocks = viewerBlocks;
        this.ids = ids;
    }

    @Transactional
    public PinRequestView request(String commentIdText, Long userId) {
        requireMember(userId);
        Instant now = Instant.now();
        CommentEntity found = requireComment(commentIdText);
        PostEntity post = postQueries.requireVisible(Long.toString(found.getPostId()), now);
        EffectiveBoard b = readable(post, now, userId);
        CommentEntity c = comments.findWithLockByPostIdAndId(post.getId(), found.getId()).filter(x -> !x.isDeleted()).orElseThrow(CommentNotFoundException::new);
        if (!userId.equals(c.getAuthorId())) {
            throw new NotAuthorException();
        }
        if (c.getParentId() != null || c.getStance() == null) {
            throw PinRequestException.conflict(PinRequestException.NOT_OPINION);
        }
        if (c.isHiddenAt(now)) {
            throw PinRequestException.conflict(PinRequestException.UNAVAILABLE);
        }
        if (!reader.status(post.getId(), now).open()) {
            throw new DebateNotOpenException();
        }
        if (b.pinRequestReviewers().isEmpty()) {
            throw PinRequestException.conflict(PinRequestException.OFF);
        }
        // 추천을 일정 이상 받은 의견만 요청할 수 있다(사용자 2026-11-12 「모두 보내면 너무 많다」) — 이미 대기 중인 요청은 추천이 줄어도 그대로
        if (c.getLikeCount() < catalog.snapshot().defaults().pinRequestMinLikes()) {
            throw PinRequestException.conflict(PinRequestException.NOT_ENOUGH_LIKES);
        }
        if (queries.pinnedOf(post.getId()).contains(c.getId())) {
            throw PinRequestException.conflict(PinRequestException.ALREADY_PINNED);
        }
        if (requests.findFirstByCommentIdAndStatus(c.getId(), DebatePinRequestEntity.PENDING).isPresent()) {
            throw PinRequestException.conflict(PinRequestException.EXISTS); // 댓글 행 잠금 아래라 같은 의견의 동시 요청은 줄을 선다
        }
        DebatePinRequestEntity row = DebatePinRequestEntity.pending(ids.nextId(), c.getId(), post.getId(), userId, now);
        requests.persist(row);
        requests.flush();
        String excerpt = excerpt(c);
        for (Long reviewer : reviewers.memberReviewers(b, post)) {
            memberNotices.opinionPinRequested(reviewer, userId, post.getId(), c.getId(), post.getTitle(), excerpt, c.isAnonymous()); // 익명 의견이면 요청자 「익명」(V184)
        }
        if (reviewers.operatorsReview(b)) {
            operatorNotices.publish(AdminNotificationEvent.OPINION_PIN_REQUEST_PENDING,
                    NotificationContext.empty().dedupe("post:" + post.getId()).link("postId", Long.toString(post.getId()))); // 게시글 관리의 그 글 서랍으로
        }
        log.info("의견 고정 요청 requestId={} postId={} commentId={} by userId={}", row.getId(), post.getId(), c.getId(), userId);
        return view(row);
    }

    @Transactional
    public void cancel(String commentIdText, Long userId) {
        requireMember(userId);
        Long commentId = PostQueryService.parseId(commentIdText);
        if (commentId == null) {
            return;
        }
        requests.findFirstByCommentIdAndStatus(commentId, DebatePinRequestEntity.PENDING).filter(r -> r.getRequesterId().equals(userId))
                .flatMap(r -> requests.findWithLockById(r.getId())).filter(DebatePinRequestEntity::isPending).ifPresent(r -> {
                    requests.delete(r);
                    log.info("의견 고정 요청 취소 requestId={} by userId={}", r.getId(), userId);
                });
    }

    @Transactional(readOnly = true)
    public PinRequestListView pending(String postIdText, Long userId) {
        requireMember(userId);
        Instant now = Instant.now();
        PostEntity post = postQueries.requireVisible(postIdText, now);
        EffectiveBoard b = readable(post, now, userId);
        if (!reviewers.canReview(b, post, userId)) {
            throw PinRequestException.forbidden();
        }
        Map<Long, CommentEntity> byId = new HashMap<>();
        comments.findByPostIdOrderByIdAsc(post.getId()).forEach(c -> byId.put(c.getId(), c));
        List<DebatePinRequestEntity> list = reviewers.pending(post.getId(), userId, now, byId);
        List<Long> authorIds = list.stream().map(DebatePinRequestEntity::getRequesterId).distinct().toList();
        AuthorMembers members = authors.members(authorIds);
        ViewerBlocks.Lens lens = authorIds.isEmpty() ? ViewerBlocks.Lens.none() : viewerBlocks.lens(userId, authorIds);
        BoardCatalogSnapshot s = catalog.snapshot();
        // 익명 댓글(V184)은 작성자 대신 익명 표시(글쓴이·글 안 번호) — 번호는 익명 댓글이 있을 때만 읽는다
        Map<Long, Integer> anonymousNumbers = list.stream().anyMatch(r -> byId.get(r.getCommentId()).isAnonymous()) ? anonymousAuthors.numbersOf(post.getId()) : Map.of();
        List<PinRequestReviewItemView> items = new ArrayList<>();
        for (DebatePinRequestEntity r : list) {
            CommentEntity c = byId.get(r.getCommentId());
            items.add(new PinRequestReviewItemView(Long.toString(r.getId()), Long.toString(c.getId()), c.getStance(),
                    c.isAnonymous() ? AuthorViews.anonymous(AnonymousAuthors.commentTag(post, c.getAuthorId(), anonymousNumbers))
                            : authors.of(c.getAuthorId(), c.getGuestName(), c.getAuthorIp(), members, s).withMemo(lens.memoOf(c.getAuthorId())), excerpt(c), c.getLikeCount(),
                    c.getCreatedAt(), r.getCreatedAt()));
        }
        return new PinRequestListView(items);
    }

    @Transactional
    public PinRequestView decide(String requestIdText, boolean approve, PinRequestRejectRequest body, Long userId) {
        requireMember(userId);
        String reason = approve || body == null ? null : body.reason();
        if (reason != null && reason.strip().codePointCount(0, reason.strip().length()) > DebatePinRequestEntity.REASON_MAX) {
            throw ValidationException.of("reason", new MessageRef("debate.pinRequest.reason.tooLong", String.valueOf(DebatePinRequestEntity.REASON_MAX)));
        }
        Long id = PostQueryService.parseId(requestIdText);
        DebatePinRequestEntity row = id == null ? null : requests.findWithLockById(id).orElse(null);
        if (row == null) {
            throw PinRequestException.notFound();
        }
        Instant now = Instant.now();
        PostEntity post = posts.findById(row.getPostId()).filter(p -> !p.isGoneAt(now)).orElseThrow(PinRequestException::notFound);
        EffectiveBoard b = readable(post, now, userId);
        if (Objects.equals(row.getRequesterId(), userId) || !reviewers.canReview(b, post, userId)) {
            throw PinRequestException.forbidden();
        }
        decisions.decide(row, approve, reason, DebatePinRequestEntity.BY_MEMBER, userId, null, now);
        log.info("의견 고정 요청 {} requestId={} by userId={}", approve ? "승인" : "거절", row.getId(), userId);
        return view(row);
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    private static void requireMember(Long userId) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
    }

    /** 글 읽기 규칙 — 게시판 꺼짐 403 BOARD_INACTIVE, 읽을 수 없는 비밀글 403 SECRET_POST(V166), 숨긴 글 403 POST_HIDDEN */
    private EffectiveBoard readable(PostEntity post, Instant now, Long userId) {
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = PublicBoardService.requireActive(s, post.getBoardId());
        postQueries.requireSecretReadable(s, post, userId);
        if (post.isHiddenAt(now)) {
            throw new PostHiddenException();
        }
        return b;
    }

    /** 댓글 — 형식이 틀리거나 없거나 지웠으면 404 */
    private CommentEntity requireComment(String idText) {
        Long id = PostQueryService.parseId(idText);
        Long postId = id == null ? null : commentQueries.postIdOf(id).orElse(null);
        if (postId == null) {
            throw new CommentNotFoundException();
        }
        return comments.findByPostIdAndId(postId, id).filter(c -> !c.isDeleted()).orElseThrow(CommentNotFoundException::new);
    }

    private static String excerpt(CommentEntity c) {
        String text = RichText.plainOf(c.getContentFormat(), c.getContent());
        if (text == null) {
            return "";
        }
        String flat = text.strip().replaceAll("\\s+", " ");
        return flat.codePointCount(0, flat.length()) <= EXCERPT_MAX ? flat : flat.substring(0, flat.offsetByCodePoints(0, EXCERPT_MAX)) + "…";
    }

    static PinRequestView view(DebatePinRequestEntity r) {
        return new PinRequestView(Long.toString(r.getId()), Long.toString(r.getCommentId()), Long.toString(r.getPostId()), r.getStatus(), r.getRejectReason(),
                r.getCreatedAt(), r.getDecidedAt());
    }
}
