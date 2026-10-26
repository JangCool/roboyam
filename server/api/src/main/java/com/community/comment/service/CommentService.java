package com.community.comment.service;

import com.community.block.service.ViewerBlocks;
import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardSecurityValues;
import com.community.board.dto.ContentBody;
import com.community.board.dto.EffectiveBoard;
import com.community.board.exception.AlreadyLikedException;
import com.community.board.exception.BlockedWordException;
import com.community.board.exception.CannotLikeOwnException;
import com.community.board.exception.NotAuthorException;
import com.community.board.service.AuthorMembers;
import com.community.board.service.AuthorViews;
import com.community.board.service.BoardAccessPolicy;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.board.service.ContentRules;
import com.community.board.service.GuestPasswords;
import com.community.board.service.PublicBoardService;
import com.community.board.service.RichText;
import com.community.captcha.CaptchaVerifier;
import com.community.comment.dto.request.CommentUpdateRequest;
import com.community.comment.dto.request.CommentWriteRequest;
import com.community.comment.dto.response.CommentLikeResult;
import com.community.comment.dto.response.CommentListResponse;
import com.community.comment.dto.response.CommentReactionsView;
import com.community.comment.dto.response.CommentThreadView;
import com.community.comment.dto.response.CommentView;
import com.community.comment.dto.response.PinRequestStateView;
import com.community.comment.dto.response.ReplyToView;
import com.community.comment.entity.CommentEntity;
import com.community.comment.exception.AlreadyDislikedException;
import com.community.comment.exception.CannotDislikeOwnException;
import com.community.comment.exception.CommentDailyLimitException;
import com.community.comment.exception.CommentDislikeDisabledException;
import com.community.comment.exception.CommentHiddenException;
import com.community.comment.exception.CommentNotFoundException;
import com.community.comment.exception.CommentTooFastException;
import com.community.comment.repository.master.CommentRepository;
import com.community.comment.repository.master.CommentRepositoryQDSL;
import com.community.debate.dto.response.CommentDebateView;
import com.community.debate.dto.response.DebateCountsView;
import com.community.debate.entity.DebatePinRequestEntity;
import com.community.debate.entity.DebateStance;
import com.community.debate.repository.master.DebatePinRequestRepository;
import com.community.debate.repository.master.DebateQueries;
import com.community.debate.service.DebateReader;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupPostQueries;
import com.community.group.service.GroupPostGate;
import com.community.membernotification.dto.CommentNotice;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.point.service.PointService;
import com.community.post.dto.WriteRateData;
import com.community.post.dto.response.PostAuthorView;
import com.community.post.entity.ContentFormat;
import com.community.post.entity.PostEntity;
import com.community.post.exception.PostHiddenException;
import com.community.post.repository.master.PostCounterQueries;
import com.community.post.repository.master.PostRepository;
import com.community.post.service.PostQueryService;
import com.community.post.service.PostWriteService;
import com.community.post.service.WriterPromotion;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 댓글 — 목록·쓰기·고치기·지우기·추천.
 *
 * <ul>
 *   <li><b>목록</b>: 원댓글(정렬 OLDEST·LATEST) + 그 아래 답글(오래된순, 지운 답글은 뺀다). 지운 원댓글은 답글이 있을 때만 자리를 남긴다({@code DELETED}, 본문·작성자 이름 없음).
 *       신고·운영자가 숨긴 댓글은 {@code HIDDEN} — 운영자 숨김은 게시판 옵션 {@code staffHiddenNotice} 가 켜졌을 때만 {@code hiddenBy STAFF} 로 밝힌다(끄면 신고 숨김과 구별되지 않는다). 비회원이 보면 최근 {@code commentGuestDelayMinutes} 분 안의 댓글은 {@code DELAYED}(본문을 보내지 않는다) — 단 같은 IP 가 쓴 댓글은
 *       본인 것으로 보고 바로 보인다(목업의 「이 브라우저가 방금 쓴 댓글」을 IP 로 가늠한다 — 가정). 베스트는 추천 {@code bestCommentMinLikes} 이상·보이는 댓글, 추천 많은 순 최대 3개.</li>
 *   <li><b>쓰기 순서</b>(목업 {@code mockCommentCreate}): 글(404·403 숨김) → 게시판(403 꺼짐) → 댓글 판정(401·403 까닭) → 칸(본문 1~2,000자, 링크·이미지, 비회원, 답글 대상)
 *       → 금지어 → 자동입력 방지 → 작성 간격({@code 429 COMMENT_TOO_FAST}) → 하루 한도({@code 429 COMMENT_DAILY_LIMIT}). 답글은 1단계({@code parentId} = 원댓글).</li>
 *   <li>글의 보이는 댓글 수({@code posts.comment_count})는 쓰기·지우기·숨김이 같은 트랜잭션에서 원자적으로 고친다.</li>
 *   <li><b>하이라이트 토론</b>(docs/features/popular-highlight-contract.md 「서버 구현 결과」): 목록 {@code kind}(ALL · GENERAL · OPINION · PRO · CON · NEUTRAL — 원댓글의 입장으로
 *       가른다. 토론 구획은 OPINION(또는 한 입장), 댓글 구획은 GENERAL)와 {@code debate}(열림·내 입장·중립 받기·투표 수·의견/일반 수), 댓글마다 {@code stance} 배지.
 *       쓰기는 의견 표시({@code opinion}·{@code stance})가 있는 원댓글과 의견에 단 답글만 입장 의견이다({@link DebateReader#resolveForWrite}) — 입장은 쓰기로 정해지지 않는다(투표로만).
 *       입장 의견의 글자 수 한도는 {@link DebateReader#OPINION_MAX}.</li>
 *   <li><b>추천·비추천</b>: 한 회원이 한 댓글에 둘 중 하나만 — 하나를 누르면 다른 하나는 같은 트랜잭션에서 풀린다. 비추천은 게시판 정책 {@code commentDislikeEnabled} 가 켜졌을 때만
 *       (끄면 409 {@code COMMENT_DISLIKE_DISABLED}, 쌓인 수는 보존하되 응답에 싣지 않는다). 비추천은 포인트·알림에 쓰지 않는다. 추천을 비추천이 풀어도 이미 준 추천 보너스는 회수하지 않는다.</li>
 *   <li><b>고정한 의견</b>: 토론 의견 구분(OPINION·PRO·CON·NEUTRAL)에서 운영자가 고정한(또는 고정 요청이 승인된) 보이는 의견이 고정한 시각 순으로 맨 위에 온다.</li>
 *   <li>회원 댓글은 저장이 커밋된 뒤 활동 수 조건 즉시 승급을 판정한다({@link WriterPromotion} — 별도 트랜잭션, 실패해도 댓글쓰기는 성공).</li>
 *   <li>같은 트랜잭션의 마지막에 댓글 포인트와 회원 알림({@code COMMENT}·{@code REPLY})을 낸다 — 둘 다 세이브포인트로 격리돼 실패해도 댓글쓰기는 성공한다. 지우면 받은 포인트를
 *       0P 까지 회수하고, 추천이 인정된 추천 문턱을 채우면 작성자에게 추천 보너스를 준다({@link PointService}).</li>
 * </ul>
 */
@Service
public class CommentService {

    static final String VISIBLE = "VISIBLE";
    static final String DELETED = "DELETED";
    static final String HIDDEN = "HIDDEN";
    static final String DELAYED = "DELAYED";
    /** 공개 응답 {@code hiddenBy} — 운영자 숨김을 밝히는 게시판에서만 */
    static final String HIDDEN_BY_STAFF = "STAFF";
    /** 공개 응답 {@code hiddenBy} — 소모임을 스스로 나간 멤버가 나가기 전에 쓴 댓글(소모임 글만, {@code state} HIDDEN) */
    static final String HIDDEN_BY_GROUP_LEFT = "GROUP_LEFT";
    /**
     * 공개 응답 {@code hiddenBy} — 비공개 소모임의 개별 공개 글에서 공개 시각 이전에 달린 댓글을 비멤버·비회원에게 가린다({@code state} HIDDEN, community api-groups.md 11-7).
     * 가림 사유 판정은 {@link #view} 한 곳({@code GROUP_LEFT} · {@code MEMBERS_ONLY}). 운영진 숨김 {@code STAFF} 는 별개
     */
    static final String HIDDEN_BY_MEMBERS_ONLY = "MEMBERS_ONLY";
    /** 베스트 댓글 최대 수(가정) */
    static final int BEST_MAX = 3;
    /** 목록 구분 — 원댓글의 입장으로 가른다. ALL 두 구획 모두(기본) · GENERAL 일반 댓글(댓글 구획) · OPINION 입장 의견 전체(토론 구획) · PRO · CON · NEUTRAL 그 입장의 의견만 */
    static final String KIND_ALL = "ALL";
    static final String KIND_GENERAL = "GENERAL";
    static final String KIND_OPINION = "OPINION";
    static final List<String> KINDS = List.of(KIND_ALL, KIND_GENERAL, KIND_OPINION, DebateStance.PRO.name(), DebateStance.CON.name(), DebateStance.NEUTRAL.name());

    private final BoardCatalog catalog;
    private final BoardActorResolver actors;
    private final PostRepository posts;
    private final PostQueryService postQueries;
    private final CommentRepository comments;
    private final CommentRepositoryQDSL commentQueries;
    private final PostCounterQueries counters;
    private final AuthorViews authors;
    private final GuestPasswords passwords;
    private final CaptchaVerifier captcha;
    private final SnowflakeIdGenerator ids;
    private final WriterPromotion promotion;
    private final PointService points;
    private final MemberNotificationPublisher notices;
    private final ViewerBlocks viewerBlocks;
    private final DebateReader debate;
    private final DebateQueries debateQueries;
    private final DebatePinRequestRepository pinRequests;
    private final GroupPostGate groupGate;
    private final GroupMemberRepository groupMembers;
    private final GroupPostQueries groupPosts;
    private final Messages messages;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public CommentService(BoardCatalog catalog, BoardActorResolver actors, PostRepository posts, PostQueryService postQueries, CommentRepository comments,
                          CommentRepositoryQDSL commentQueries, PostCounterQueries counters, AuthorViews authors, GuestPasswords passwords,
                          CaptchaVerifier captcha, SnowflakeIdGenerator ids, WriterPromotion promotion, PointService points, MemberNotificationPublisher notices,
                          ViewerBlocks viewerBlocks, DebateReader debate, DebateQueries debateQueries, DebatePinRequestRepository pinRequests,
                          GroupPostGate groupGate, GroupMemberRepository groupMembers, GroupPostQueries groupPosts, Messages messages,
                          PlatformTransactionManager txManager) {
        this.groupGate = groupGate;
        this.groupMembers = groupMembers;
        this.groupPosts = groupPosts;
        this.messages = messages;
        this.viewerBlocks = viewerBlocks;
        this.debate = debate;
        this.debateQueries = debateQueries;
        this.pinRequests = pinRequests;
        this.promotion = promotion;
        this.points = points;
        this.notices = notices;
        this.catalog = catalog;
        this.actors = actors;
        this.posts = posts;
        this.postQueries = postQueries;
        this.comments = comments;
        this.commentQueries = commentQueries;
        this.counters = counters;
        this.authors = authors;
        this.passwords = passwords;
        this.captcha = captcha;
        this.ids = ids;
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    // ── 목록 ──────────────────────────────────────────────────────────────

    /**
     * 보는 사람 — 댓글 상태·내 것·추천 판정.
     *
     * @param staffHiddenNotice 게시판 옵션 「운영자가 숨긴 댓글임을 밝히기」 유효값 — 켜졌을 때만 운영자 숨김에 {@code hiddenBy STAFF} 를 싣는다
     * @param dislikeEnabled    게시판 정책 {@code commentDislikeEnabled} 유효값 — 꺼졌으면 비추천 칸이 null
     * @param disliked          내가 비추천한 댓글
     * @param pinned            고정한 의견(댓글 ID)
     * @param myPinRequests     내 의견의 고정 요청 상태(댓글 ID → 상태, 대기·거절만)
     */
    private record Viewer(Long userId, String ip, boolean guest, int delayMinutes, Set<Long> liked, ViewerBlocks.Lens lens, boolean staffHiddenNotice,
                          boolean dislikeEnabled, Set<Long> disliked, Set<Long> pinned, Map<Long, PinRequestStateView> myPinRequests, Map<Long, Instant> leftAt,
                          Instant membersOnlyBefore) {

        /** 쓰기·고치기 응답용 — 목록 칸(추천·비추천·고정·내 요청)은 부르는 쪽이 정한다. 쓴 사람은 그 글을 열 수 있는 사람이라 공개 시각 가림을 하지 않는다 */
        static Viewer self(Long userId, String ip, Set<Long> liked, EffectiveBoard b, Set<Long> disliked, Set<Long> pinned) {
            return new Viewer(userId, ip, false, 0, liked, ViewerBlocks.Lens.none(), b.staffHiddenNotice(), b.commentDislikeEnabled(), disliked, pinned, Map.of(),
                    Map.of(), null);
        }

        /** 비멤버·비회원이 보는 비공개 소모임의 개별 공개 글에서 공개 시각 이전에 달린 댓글인가(api-groups.md 11-7 — 되돌렸다 다시 공개하면 새 시각 기준) */
        boolean membersOnly(CommentEntity c) {
            return membersOnlyBefore != null && c.getCreatedAt().isBefore(membersOnlyBefore);
        }

        /**
         * 소모임을 스스로 나간 멤버가 <b>나가기 전에</b> 쓴 댓글인가(소모임 글만 — community api-groups.md 8절 「나간 멤버의 글·댓글」). 다시 가입하면 풀린다(나간 시각이 비워진다).
         * 나간 뒤 공개 글에 쓴 댓글은 가리지 않는다.
         */
        boolean groupLeft(CommentEntity c) {
            Instant left = c.getAuthorId() == null ? null : leftAt.get(c.getAuthorId());
            return left != null && !c.getCreatedAt().isAfter(left);
        }
    }

    /**
     * @param kindRaw 구분 ALL(기본 — 지금까지처럼 모든 댓글) · GENERAL · OPINION · PRO · CON · NEUTRAL(대소문자 무시) — 그 밖은 400 {@code fields.kind}.
     *                의견이 없는 글의 OPINION·PRO·CON·NEUTRAL 은 빈 목록이다
     */
    public CommentListResponse list(String postIdText, String sort, String kindRaw, Long userId, String ip) {
        String kind = kindRaw == null || kindRaw.isBlank() ? KIND_ALL : kindRaw.strip().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(kind)) {
            throw ValidationException.of("kind", new MessageRef("debate.kind.invalid"));
        }
        return readTx.execute(status -> {
            Instant now = Instant.now();
            PostEntity post = postQueries.requireVisible(postIdText, now);
            BoardCatalogSnapshot s = catalog.snapshot();
            GroupPostGate.Resolved gate = groupGate.resolve(s, post, userId); // 소모임 비공개 글의 댓글은 멤버만(403 GROUP_POST_PRIVATE)
            EffectiveBoard b = gate.board();
            if (post.isHiddenAt(now)) {
                throw new PostHiddenException();
            }
            Instant membersOnlyBefore = GroupPostGate.membersOnlyBefore(gate, post); // 비멤버에게 공개 시각 이전 댓글을 가린다(11-7)
            List<CommentEntity> all = comments.findByPostIdOrderByIdAsc(post.getId());
            List<Long> pinOrder = all.isEmpty() ? List.of() : debateQueries.pinnedInOrder(post.getId());
            Viewer viewer = new Viewer(userId, ip, userId == null, b.commentGuestDelayMinutes(),
                    userId == null || all.isEmpty() ? Set.of() : counters.likedComments(post.getId(), userId),
                    all.isEmpty() ? ViewerBlocks.Lens.none() : viewerBlocks.lens(userId, all.stream().map(CommentEntity::getAuthorId).toList()),
                    b.staffHiddenNotice(), b.commentDislikeEnabled(),
                    userId == null || all.isEmpty() || !b.commentDislikeEnabled() ? Set.of() : counters.dislikedComments(post.getId(), userId),
                    new HashSet<>(pinOrder), userId == null || all.isEmpty() ? Map.of() : myPinRequests(post.getId(), userId), leftAt(post, all),
                    membersOnlyBefore);
            AuthorMembers members = authors.members(all.stream().map(CommentEntity::getAuthorId).toList());
            Map<Long, CommentEntity> byId = new HashMap<>();
            all.forEach(c -> byId.put(c.getId(), c));

            List<CommentEntity> allTops = all.stream().filter(c -> c.getParentId() == null).toList();
            DebateCountsView counts = counts(all, post.getCommentCount(), now);
            List<CommentEntity> tops = new ArrayList<>(allTops.stream().filter(c -> inKind(c, kind)).toList());
            if ("LATEST".equals(sort)) {
                tops.sort(Comparator.comparing(CommentEntity::getId, Comparator.reverseOrder()));
            }
            if (!KIND_ALL.equals(kind) && !KIND_GENERAL.equals(kind) && !pinOrder.isEmpty()) {
                tops = pinnedFirst(tops, pinOrder, now); // 토론 의견 — 고정한 보이는 의견을 고정한 시각 순으로 맨 위에
            }
            Map<Long, List<CommentEntity>> replies = new HashMap<>();
            all.stream().filter(c -> c.getParentId() != null && !c.isDeleted()).forEach(c -> replies.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c));

            List<CommentThreadView> threads = new ArrayList<>();
            for (CommentEntity top : tops) {
                List<CommentEntity> children = replies.getOrDefault(top.getId(), List.of());
                if (top.isDeleted() && children.isEmpty()) {
                    continue; // 지운 원댓글은 답글이 있을 때만 자리를 남긴다
                }
                threads.add(CommentThreadView.of(view(top, viewer, byId, members, s, now),
                        children.stream().map(c -> view(c, viewer, byId, members, s, now)).toList()));
            }
            int minLikes = s.defaults().bestCommentMinLikes();
            List<CommentView> best = all.stream()
                    .filter(c -> KIND_ALL.equals(kind) || (c.getParentId() == null && inKind(c, kind)))
                    .filter(c -> !c.isDeleted() && !c.isHiddenAt(now) && c.getLikeCount() >= minLikes)
                    .map(c -> view(c, viewer, byId, members, s, now))
                    .filter(v -> VISIBLE.equals(v.state()))
                    .sorted(Comparator.comparingInt(CommentView::likeCount).reversed().thenComparing(CommentView::id))
                    .limit(BEST_MAX)
                    .toList();
            CommentDebateView debateView = debate.forComments(post, b, userId, now, counts, all, byId);
            int membersOnlyCount = membersOnlyBefore == null ? 0 : groupPosts.countCommentsBefore(post.getId(), membersOnlyBefore, now);
            return new CommentListResponse(threads, best, post.getCommentCount(), kind, debateView,
                    new CommentReactionsView(b.commentDislikeEnabled(), b.commentLikeLabel(), b.commentDislikeLabel()), membersOnlyCount);
        });
    }

    /**
     * 소모임 글이면 댓글 쓴 사람 중 그 소모임을 스스로 나가 아직 돌아오지 않은 사람 → 나간 시각(일반 글·댓글 없음은 빈 값). 다시 가입하면 나간 시각이 비워져 빠진다
     * (신청만 했다 취소한 사람은 멤버였던 적이 없어 나간 시각이 없다). 질의 한 번.
     */
    private Map<Long, Instant> leftAt(PostEntity post, List<CommentEntity> all) {
        if (!post.isGroupPost() || all.isEmpty()) {
            return Map.of();
        }
        List<Long> authorIds = all.stream().map(CommentEntity::getAuthorId).filter(java.util.Objects::nonNull).distinct().toList();
        if (authorIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Instant> out = new HashMap<>();
        for (GroupMemberEntity m : groupMembers.findByGroupIdAndUserIdIn(post.getGroupId(), authorIds)) {
            if (!m.active() && m.getLeftAt() != null) {
                out.put(m.getUserId(), m.getLeftAt());
            }
        }
        return out;
    }

    /** 고정한 보이는(지우거나 숨기지 않은) 의견을 고정한 시각 순으로 앞에, 나머지는 받은 차례 그대로 */
    private static List<CommentEntity> pinnedFirst(List<CommentEntity> tops, List<Long> pinOrder, Instant now) {
        Map<Long, CommentEntity> visible = new HashMap<>();
        tops.stream().filter(c -> !c.isDeleted() && !c.isHiddenAt(now)).forEach(c -> visible.put(c.getId(), c));
        List<CommentEntity> out = new ArrayList<>();
        Set<Long> raised = new HashSet<>();
        for (Long id : pinOrder) {
            CommentEntity c = visible.get(id);
            if (c != null) {
                out.add(c);
                raised.add(id);
            }
        }
        tops.stream().filter(c -> !raised.contains(c.getId())).forEach(out::add);
        return out;
    }

    /** 내 의견의 고정 요청 상태 — 의견마다 가장 최근 요청이 대기·거절이면 그 상태 */
    private Map<Long, PinRequestStateView> myPinRequests(long postId, long userId) {
        Map<Long, DebatePinRequestEntity> latest = new HashMap<>();
        for (DebatePinRequestEntity r : pinRequests.findByPostIdAndRequesterId(postId, userId)) {
            latest.merge(r.getCommentId(), r, (a, b) -> a.getId() > b.getId() ? a : b);
        }
        Map<Long, PinRequestStateView> out = new HashMap<>();
        latest.forEach((commentId, r) -> {
            if (!DebatePinRequestEntity.APPROVED.equals(r.getStatus())) {
                out.put(commentId, new PinRequestStateView(Long.toString(r.getId()), r.getStatus(), r.getRejectReason()));
            }
        });
        return out;
    }

    /** 원댓글이 그 구분에 드는가 — GENERAL = 배지 없음, OPINION = 배지 있음, PRO·CON·NEUTRAL = 그 배지. 지운·숨긴 자리도 배지를 간직하므로 같은 구분에 남는다 */
    private static boolean inKind(CommentEntity top, String kind) {
        return switch (kind) {
            case KIND_ALL -> true;
            case KIND_GENERAL -> top.getStance() == null;
            case KIND_OPINION -> top.getStance() != null;
            default -> kind.equals(top.getStance());
        };
    }

    /**
     * 구분별 수 — {@code all} 은 글의 보이는 댓글 수(답글 포함). 나머지는 지우거나 숨기지 않은 것만: 원댓글 수는 그 원댓글의 입장으로, 구획 합계({@code generalTotal}·
     * {@code opinionsTotal})는 답글까지 그 <b>원댓글</b>의 구획으로 센다(답글 자신의 배지가 아니라 — 목록이 답글을 원댓글 아래에 두는 것과 같게).
     */
    private static DebateCountsView counts(List<CommentEntity> all, int visibleAll, Instant now) {
        Map<Long, String> topStance = new HashMap<>();
        for (CommentEntity c : all) {
            if (c.getParentId() == null) {
                topStance.put(c.getId(), c.getStance());
            }
        }
        int general = 0;
        int generalTotal = 0;
        int opinionsTotal = 0;
        int[] byStance = new int[DebateStance.values().length];
        for (CommentEntity c : all) {
            if (c.isDeleted() || c.isHiddenAt(now)) {
                continue;
            }
            boolean top = c.getParentId() == null;
            if (!top && !topStance.containsKey(c.getParentId())) {
                continue; // 원댓글을 모르는 답글(없어야 한다) — 목록에도 나오지 않는다
            }
            String section = top ? c.getStance() : topStance.get(c.getParentId());
            if (section == null) {
                generalTotal++;
                if (top) {
                    general++;
                }
            } else {
                opinionsTotal++;
                if (top) {
                    byStance[DebateStance.valueOf(section).ordinal()]++;
                }
            }
        }
        int pro = byStance[DebateStance.PRO.ordinal()];
        int con = byStance[DebateStance.CON.ordinal()];
        int neutral = byStance[DebateStance.NEUTRAL.ordinal()];
        return new DebateCountsView(visibleAll, general, generalTotal, pro + con + neutral, opinionsTotal, pro, con, neutral);
    }

    private CommentView view(CommentEntity c, Viewer viewer, Map<Long, CommentEntity> byId, AuthorMembers members, BoardCatalogSnapshot s,
                             Instant now) {
        String state;
        boolean groupLeft = false;
        if (c.isDeleted()) {
            state = DELETED;
        } else if (c.isHiddenAt(now)) {
            state = HIDDEN;
        } else if (viewer.groupLeft(c)) {
            state = HIDDEN; // 소모임을 나간 멤버가 나가기 전에 쓴 댓글 — 자리·답글 흐름만 남기고 작성자·본문을 가린다(hiddenBy GROUP_LEFT)
            groupLeft = true;
        } else if (viewer.membersOnly(c)) {
            // 비공개 소모임의 개별 공개 글에서 공개 시각 이전의 댓글 — 비멤버·비회원에게는 자리·답글 흐름만(hiddenBy MEMBERS_ONLY, 추천 수 0, 베스트에서 빠진다)
            return new CommentView(Long.toString(c.getId()), Long.toString(c.getPostId()), c.getParentId() == null ? null : Long.toString(c.getParentId()),
                    replyTo(c, viewer, byId, members, s), groupLeftAuthor(), null, c.getContentFormat().name(), HIDDEN, c.getCreatedAt(), c.getEditedAt(), 0, false,
                    false, null, c.getStance(), HIDDEN_BY_MEMBERS_ONLY, false, viewer.dislikeEnabled() ? 0 : null, viewer.dislikeEnabled() ? false : null, null);
        } else if (delayed(c, viewer, now)) {
            state = DELAYED;
        } else {
            state = VISIBLE;
        }
        if (groupLeft) {
            return new CommentView(Long.toString(c.getId()), Long.toString(c.getPostId()), c.getParentId() == null ? null : Long.toString(c.getParentId()),
                    replyTo(c, viewer, byId, members, s), groupLeftAuthor(), null, c.getContentFormat().name(), state, c.getCreatedAt(), c.getEditedAt(), 0, false,
                    false, null, c.getStance(), HIDDEN_BY_GROUP_LEFT, false, viewer.dislikeEnabled() ? 0 : null, viewer.dislikeEnabled() ? false : null, null);
        }
        PostAuthorView author = authors.of(c.getAuthorId(), c.getGuestName(), c.getAuthorIp(), members, s).withMemo(viewer.lens().memoOf(c.getAuthorId()));
        ReplyToView replyTo = replyTo(c, viewer, byId, members, s);
        return new CommentView(Long.toString(c.getId()), Long.toString(c.getPostId()), c.getParentId() == null ? null : Long.toString(c.getParentId()), replyTo,
                DELETED.equals(state) ? AuthorViews.blank(author) : author, VISIBLE.equals(state) ? c.getContent() : null, c.getContentFormat().name(), state,
                c.getCreatedAt(),
                c.getEditedAt(), c.getLikeCount(), viewer.liked().contains(c.getId()), viewer.userId() != null && viewer.userId().equals(c.getAuthorId()),
                VISIBLE.equals(state) ? viewer.lens().blockedOf(c.getAuthorId(), RichText.plainOf(c.getContentFormat(), c.getContent())) : null, c.getStance(),
                HIDDEN.equals(state) && viewer.staffHiddenNotice() && c.isStaffHiddenAt(now) ? HIDDEN_BY_STAFF : null,
                !DELETED.equals(state) && !HIDDEN.equals(state) && viewer.pinned().contains(c.getId()),
                viewer.dislikeEnabled() ? c.getDislikeCount() : null, viewer.dislikeEnabled() ? viewer.disliked().contains(c.getId()) : null,
                viewer.userId() != null && viewer.userId().equals(c.getAuthorId()) ? viewer.myPinRequests().get(c.getId()) : null);
    }

    /** 답한 댓글 — 지운 댓글·나간 멤버의 가린 댓글이면 이름을 비운다 */
    private ReplyToView replyTo(CommentEntity c, Viewer viewer, Map<Long, CommentEntity> byId, AuthorMembers members, BoardCatalogSnapshot s) {
        if (c.getReplyToId() == null) {
            return null;
        }
        CommentEntity target = byId.get(c.getReplyToId());
        if (target == null) {
            return null;
        }
        String name = target.isDeleted() || viewer.groupLeft(target) ? ""
                : viewer.membersOnly(target) ? messages.get(new MessageRef("comment.membersOnly.replyToName")) // 공개 시각 이전 댓글에 답한 글 — 「멤버」
                : authors.of(target.getAuthorId(), target.getGuestName(), null, members, s).name();
        return new ReplyToView(Long.toString(target.getId()), name);
    }

    /** 나간 멤버의 가린 댓글의 작성자 — 회원이라는 것만(회원 ID·이름·등급·장식 없음). 화면은 「소모임을 나간 멤버의 댓글이에요」 */
    private static PostAuthorView groupLeftAuthor() {
        return new PostAuthorView("MEMBER", null, "", null, null, null, null, null, null, null);
    }

    /** 비회원에게 아직 보이지 않는 댓글인가 — 같은 IP 가 쓴 비회원 댓글은 본인 것으로 본다 */
    private static boolean delayed(CommentEntity c, Viewer viewer, Instant now) {
        if (!viewer.guest() || viewer.delayMinutes() <= 0) {
            return false;
        }
        if (c.isGuest() && viewer.ip() != null && viewer.ip().equals(c.getAuthorIp())) {
            return false;
        }
        return c.getCreatedAt().isAfter(now.minus(Duration.ofMinutes(viewer.delayMinutes())));
    }

    // ── 쓰기 ──────────────────────────────────────────────────────────────

    public CommentView create(String postIdText, CommentWriteRequest body, Long userId, String ip) {
        Instant start = Instant.now();
        PostEntity post = postQueries.requireVisible(postIdText, start);
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = groupGate.resolve(s, post, userId).board(); // 소모임 비공개 글은 멤버만, 공개로 전환된 글은 로그인한 누구나
        if (post.isHiddenAt(start)) {
            throw new PostHiddenException();
        }
        BoardActor actor = actors.resolve(userId, s);
        // 소모임 글의 댓글은 게시판 댓글 정책(최소 등급·잠금·끔)을 받지 않는다 — 계정 자격만(비회원 401)
        PostWriteService.requireWritable(post.isGroupPost() ? GroupPostGate.commentBlocked(actor) : BoardAccessPolicy.commentBlocked(b, actor, s));
        BoardSecurityValues sec = s.security();

        Map<String, MessageRef> fields = new LinkedHashMap<>();
        // 답글 대상 — 답글이 의견인지(원댓글이 의견인지)를 정하므로 입장보다 먼저 본다
        Long parentId = null;
        Long replyToId = null;
        CommentEntity parentRow = null;
        if (body.parentId() != null && !body.parentId().isBlank()) {
            parentId = PostQueryService.parseId(body.parentId());
            Optional<CommentEntity> parent = parentId == null ? Optional.empty() : comments.findByPostIdAndId(post.getId(), parentId);
            if (parent.isEmpty() || parent.get().getParentId() != null || parent.get().isDeleted()) {
                fields.put("parentId", new MessageRef("board.comment.parent"));
            } else {
                parentRow = parent.get();
                if (body.replyToId() != null && !body.replyToId().isBlank()) {
                    replyToId = PostQueryService.parseId(body.replyToId());
                    Long parentKey = parentId;
                    Optional<CommentEntity> target = replyToId == null ? Optional.empty() : comments.findByPostIdAndId(post.getId(), replyToId);
                    if (target.isEmpty() || !(target.get().getId().equals(parentKey) || parentKey.equals(target.get().getParentId()))) {
                        fields.put("parentId", new MessageRef("board.comment.parent"));
                    }
                }
            }
        }
        // 하이라이트 토론 — 의견 쓰기 = 원댓글은 의견 표시(opinion·stance), 답글은 원댓글이 의견일 때(답글의 opinion 은 보지 않고, stance 를 보냈으면 지금 입장과 맞춰 본다). 칸 오류(형식·비회원·답글 대상)면 해석하지 않는다.
        // 해석은 409(DEBATE_NOT_OPEN · STANCE_REQUIRED · STANCE_LOCKED)를 던질 수 있다. 입장은 여기서 정해지지 않는다(PUT …/stance 투표로만)
        DebateStance requested = null;
        try {
            requested = DebateStance.parse(body.stance());
        } catch (IllegalArgumentException e) {
            fields.put("stance", new MessageRef("debate.stance.invalid"));
        }
        boolean opinion = parentRow != null ? parentRow.getStance() != null : (Boolean.TRUE.equals(body.opinion()) || requested != null);
        if (opinion && actor.guest() && !fields.containsKey("stance")) {
            fields.put("stance", new MessageRef("debate.stance.memberOnly"));
        }
        DebateReader.WriteStance stance = fields.containsKey("stance") || fields.containsKey("parentId") || actor.guest() ? DebateReader.WriteStance.GENERAL
                : debate.resolveForWrite(post.getId(), actor.userId(), opinion, requested, start);
        ContentBody content = checkContent(b, body.contentFormat(), body.content(), writer(actor, b, s), sec, stance.bodyMax(), fields);
        String guestName = null;
        if (actor.guest()) {
            ContentRules.checkGuest(body.guest(), sec, fields);
            guestName = body.guest() == null ? null : ContentRules.normalize(body.guest().name());
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        Optional<String> word = ContentRules.blockedWord(sec, content.wordsHaystack(), guestName);
        if (word.isPresent()) {
            throw new BlockedWordException(word.get());
        }
        ContentBody masked = content.masked(sec);
        String name = ContentRules.mask(sec, guestName);
        String passwordHash = null;
        if (actor.guest()) {
            if (Boolean.TRUE.equals(sec.guestCaptchaRequired()) && !captcha.verify(body.guest().captchaToken(), ip)) {
                throw ValidationException.of("guest.captchaToken", new MessageRef("board.guest.captcha.failed"));
            }
            passwordHash = passwords.hash(body.guest().password());
        }

        long id = ids.nextId();
        Long authorId = actor.guest() ? null : actor.userId();
        Long parent = parentId;
        Long replyTo = replyToId;
        String hash = passwordHash;
        CommentView created = tx.execute(status -> {
            Instant now = Instant.now();
            checkRate(s, authorId, ip, now);
            CommentEntity comment = CommentEntity.create(id, post.getId(), b.id(), parent, replyTo, authorId, name, hash, ip, masked.format(), masked.content(),
                    stance.stanceName(), now);
            comments.persist(comment);
            counters.addCommentCount(post.getId(), 1);
            Map<Long, CommentEntity> byId = new HashMap<>();
            if (replyTo != null) {
                comments.findByPostIdAndId(post.getId(), replyTo).ifPresent(t -> byId.put(t.getId(), t));
            }
            List<Long> memberIds = new ArrayList<>();
            memberIds.add(authorId);
            byId.values().forEach(t -> memberIds.add(t.getAuthorId()));
            // 쓴 사람에게는 공개 지연 없이 보인다
            Viewer self = Viewer.self(userId, ip, Set.of(), b, Set.of(), Set.of());
            CommentView view = view(comment, self, byId, authors.members(memberIds), s, now);
            // 같은 트랜잭션의 마지막에: 댓글 포인트(회원만) · 회원 알림 — 둘 다 실패해도 댓글쓰기는 그대로다(세이브포인트로 격리)
            if (authorId != null) {
                points.earnComment(new PointService.CommentEarn(authorId, id, post.getId(), post.getAuthorId(), masked.plain(), b.pointsEnabled()));
            }
            Long replyTargetAuthor = null;
            if (parent != null) {
                CommentEntity target = replyTo != null ? byId.get(replyTo) : comments.findByPostIdAndId(post.getId(), parent).orElse(null);
                replyTargetAuthor = target == null ? null : target.getAuthorId();
            }
            notices.commentWritten(new CommentNotice(post.getId(), post.getAuthorId(), id, authorId, name, replyTargetAuthor, parent != null, post.getTitle(),
                    masked.plain()));
            return view;
        });
        promotion.afterWrite(authorId); // 커밋 뒤 — 회원 댓글이면 활동 수 조건 즉시 승급(실패해도 댓글쓰기는 성공)
        return created;
    }

    /** 작성 간격 → 하루 한도(작성자 단위 잠금 아래) */
    private void checkRate(BoardCatalogSnapshot s, Long authorId, String ip, Instant now) {
        counters.lockWriter("comment:" + (authorId != null ? "m:" + authorId : "g:" + ip));
        int gap = s.defaults().commentGapSeconds();
        int daily = s.defaults().commentDailyLimit();
        Instant since = now.minus(Duration.ofDays(1));
        WriteRateData rate = commentQueries.rate(authorId, ip, since, SnowflakeIdGenerator.floorIdAt(since));
        if (gap > 0 && rate.lastAt() != null) {
            long elapsedMs = Duration.between(rate.lastAt(), now).toMillis();
            if (elapsedMs < gap * 1000L) {
                throw new CommentTooFastException(gap, Math.max(1, (gap * 1000L - elapsedMs + 999) / 1000));
            }
        }
        if (rate.countInWindow() >= daily) {
            throw new CommentDailyLimitException(daily);
        }
    }

    private static ContentRules.Writer writer(BoardActor actor, EffectiveBoard b, BoardCatalogSnapshot s) {
        return actor.guest() ? ContentRules.Writer.GUEST
                : BoardAccessPolicy.newMemberRules(b, actor, s) ? ContentRules.Writer.NEW_MEMBER : ContentRules.Writer.MEMBER;
    }

    /**
     * 본문 형식 + 본문 1~{@code max}자(HTML 은 보이는 글자 — 일반 댓글 {@link ContentRules#COMMENT_MAX}, 입장 배지가 붙는 댓글 {@link DebateReader#OPINION_MAX})
     * + 링크·이미지(댓글은 이미지 불가). 형식이 빠지면 TEXT, 모르는 형식·댓글 에디터 NONE 게시판의 HTML 은
     * {@code fields.contentFormat}. HTML 은 간단 에디터 허용 목록으로 정화한다(이미지 표식은 간단 목록에 없어 사라진다).
     */
    private static ContentBody checkContent(EffectiveBoard b, String formatText, String raw, ContentRules.Writer writer, BoardSecurityValues sec, int max,
                                            Map<String, MessageRef> fields) {
        ContentFormat format = ContentRules.format(formatText);
        if (format == null || (format == ContentFormat.HTML && !b.commentHtml())) {
            fields.put("contentFormat", new MessageRef(format == null ? "board.content.format.unknown" : "board.content.format.notAllowed"));
            return ContentBody.text(raw);
        }
        if (format == ContentFormat.HTML && raw != null && raw.length() > ContentRules.COMMENT_HTML_MAX * 2) {
            fields.put("content", new MessageRef("board.comment.html.tooLong"));
            return ContentBody.text("");
        }
        ContentBody content = format == ContentFormat.HTML ? ContentBody.html(raw, RichText.Level.SIMPLE) : ContentBody.text(raw);
        int length = ContentRules.length(content.plain());
        if (length < 1 || length > max) {
            fields.put("content", new MessageRef("board.comment.length", String.format("%,d", max)));
        } else if (content.isHtml() && content.content().length() > ContentRules.COMMENT_HTML_MAX) {
            fields.put("content", new MessageRef("board.comment.html.tooLong"));
        } else {
            ContentRules.checkCommentLinksImages(content, writer, sec, fields);
        }
        return content;
    }

    // ── 고치기·지우기·추천 ─────────────────────────────────────────────────

    /** 댓글과 그 글 */
    private record Target(CommentEntity comment, PostEntity post) {
    }

    /** 댓글 — 형식이 틀리거나 없거나 지웠거나 글이 사라졌으면 404 */
    private Target requireComment(String idText, Instant now) {
        Long id = PostQueryService.parseId(idText);
        Long postId = id == null ? null : commentQueries.postIdOf(id).orElse(null);
        CommentEntity comment = postId == null ? null : comments.findByPostIdAndId(postId, id).orElse(null);
        if (comment == null || comment.isDeleted()) {
            throw new CommentNotFoundException();
        }
        PostEntity post = posts.findById(postId).orElse(null);
        if (post == null || post.isGoneAt(now)) {
            throw new CommentNotFoundException();
        }
        return new Target(comment, post);
    }

    /** 댓글이 놓인 게시판 — 일반 글은 공개 게시판 판정, 소모임 글은 소모임 판정(비공개 글은 멤버만) */
    private EffectiveBoard boardOf(BoardCatalogSnapshot s, Target t, Long userId) {
        return groupGate.resolve(s, t.post(), userId).board();
    }

    private void requireOwner(CommentEntity c, String password, Long userId, String ip) {
        if (c.isGuest()) {
            passwords.require(c.getGuestPasswordHash(), password, ip, "comment:" + c.getId());
            return;
        }
        if (userId == null || !userId.equals(c.getAuthorId())) {
            throw new NotAuthorException();
        }
    }

    public CommentView update(String idText, CommentUpdateRequest body, Long userId, String ip) {
        Instant start = Instant.now();
        Target target = requireComment(idText, start);
        CommentEntity comment = target.comment();
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = boardOf(s, target, userId);
        if (comment.isHiddenAt(start)) {
            throw new CommentHiddenException();
        }
        requireOwner(comment, body.password(), userId, ip);
        BoardActor actor = actors.resolve(userId, s);
        ContentRules.Writer writer = comment.isGuest() ? ContentRules.Writer.GUEST : writer(actor, b, s);
        BoardSecurityValues sec = s.security();
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        int max = comment.getStance() == null ? ContentRules.COMMENT_MAX : DebateReader.OPINION_MAX; // 의견이면 의견 한도 — 배지는 고칠 때 바뀌지 않는다
        ContentBody content = checkContent(b, body.contentFormat(), body.content(), writer, sec, max, fields);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        Optional<String> word = ContentRules.blockedWord(sec, content.wordsHaystack());
        if (word.isPresent()) {
            throw new BlockedWordException(word.get());
        }
        ContentBody masked = content.masked(sec);
        return tx.execute(status -> {
            Instant now = Instant.now();
            CommentEntity locked = comments.findWithLockByPostIdAndId(comment.getPostId(), comment.getId()).filter(c -> !c.isDeleted())
                    .orElseThrow(CommentNotFoundException::new);
            if (locked.isHiddenAt(now)) {
                throw new CommentHiddenException();
            }
            locked.edit(masked.format(), masked.content(), now);
            Map<Long, CommentEntity> byId = new HashMap<>();
            if (locked.getReplyToId() != null) {
                comments.findByPostIdAndId(locked.getPostId(), locked.getReplyToId()).ifPresent(t -> byId.put(t.getId(), t));
            }
            List<Long> memberIds = new ArrayList<>();
            memberIds.add(locked.getAuthorId());
            byId.values().forEach(t -> memberIds.add(t.getAuthorId()));
            Set<Long> liked = userId == null ? Set.of() : counters.likedComments(locked.getPostId(), userId);
            Set<Long> disliked = userId == null || !b.commentDislikeEnabled() ? Set.of() : counters.dislikedComments(locked.getPostId(), userId);
            Set<Long> pinned = locked.getStance() == null ? Set.of() : new HashSet<>(debateQueries.pinnedInOrder(locked.getPostId()));
            return view(locked, Viewer.self(userId, ip, liked, b, disliked, pinned), byId, authors.members(memberIds), s, now);
        });
    }

    /** 지우기 — 답글이 있는 원댓글은 목록에 자리만 남는다. 보이던 댓글이면 글의 댓글 수를 하나 줄인다 */
    public void delete(String idText, String password, Long userId, String ip) {
        Instant start = Instant.now();
        Target target = requireComment(idText, start);
        CommentEntity comment = target.comment();
        boardOf(catalog.snapshot(), target, userId);
        requireOwner(comment, password, userId, ip);
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now();
            CommentEntity locked = comments.findWithLockByPostIdAndId(comment.getPostId(), comment.getId()).filter(c -> !c.isDeleted())
                    .orElseThrow(CommentNotFoundException::new);
            boolean wasVisible = !locked.isHiddenAt(now);
            locked.delete(now);
            if (wasVisible) {
                counters.addCommentCount(locked.getPostId(), -1);
            }
            if (locked.getAuthorId() != null) {
                points.revokeContent(locked.getAuthorId(), PointService.REF_COMMENT, locked.getId()); // 본인 삭제 — 받은 포인트를 0P 까지 회수(알림 없음)
            }
        });
    }

    /** 추천 — 회원만, 정지 403, 내 댓글 400, 이미 추천 409. 취소 없음. 내 비추천이 있으면 같은 트랜잭션에서 푼다 */
    public CommentLikeResult like(String idText, Long userId) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        BoardCatalogSnapshot s = catalog.snapshot();
        PostWriteService.requireReactable(actors.resolve(userId, s));
        return tx.execute(status -> {
            Instant now = Instant.now();
            Target target = requireComment(idText, now);
            CommentEntity comment = target.comment();
            EffectiveBoard b = boardOf(s, target, userId);
            if (comment.isHiddenAt(now)) {
                throw new CommentHiddenException();
            }
            if (userId.equals(comment.getAuthorId())) {
                throw new CannotLikeOwnException(true);
            }
            if (!counters.insertCommentLike(comment.getId(), comment.getPostId(), userId)) {
                throw new AlreadyLikedException(true);
            }
            counters.incrementCommentLikes(comment.getPostId(), comment.getId()); // 댓글 행 갱신 — 같은 댓글의 추천·비추천이 여기서 줄을 선다
            if (counters.deleteCommentDislike(comment.getId(), userId)) {
                counters.addCommentDislikes(comment.getPostId(), comment.getId(), -1); // 추천과 비추천은 둘 중 하나만
            }
            points.likeBonus(comment.getAuthorId(), PointService.REF_COMMENT, comment.getId(), userId, b.pointsEnabled());
            return reactionResult(comment, b, true, false);
        });
    }

    /**
     * 비추천 누르기({@code on}) · 취소 — 회원만(401), 정지 403(추천과 같은 자격), 비추천이 꺼진 게시판 409 {@code COMMENT_DISLIKE_DISABLED}, 숨긴 댓글 403, 내 댓글 400
     * {@code CANNOT_DISLIKE_OWN}, 이미 비추천 409 {@code ALREADY_DISLIKED}(누르기). 취소는 비추천이 없어도 지금 상태로 200. 누르면 내 추천이 같은 트랜잭션에서 풀린다
     * (이미 준 추천 보너스는 회수하지 않는다). 포인트·알림 없음.
     */
    public CommentLikeResult dislike(String idText, Long userId, boolean on) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        BoardCatalogSnapshot s = catalog.snapshot();
        PostWriteService.requireReactable(actors.resolve(userId, s));
        return tx.execute(status -> {
            Instant now = Instant.now();
            Target target = requireComment(idText, now);
            CommentEntity comment = target.comment();
            EffectiveBoard b = boardOf(s, target, userId);
            if (!b.commentDislikeEnabled()) {
                throw new CommentDislikeDisabledException();
            }
            if (comment.isHiddenAt(now)) {
                throw new CommentHiddenException();
            }
            if (userId.equals(comment.getAuthorId())) {
                throw new CannotDislikeOwnException();
            }
            boolean liked;
            if (on) {
                if (!counters.insertCommentDislike(comment.getId(), comment.getPostId(), userId)) {
                    throw new AlreadyDislikedException();
                }
                counters.addCommentDislikes(comment.getPostId(), comment.getId(), 1);
                if (counters.deleteCommentLike(comment.getId(), userId)) {
                    counters.decrementCommentLikes(comment.getPostId(), comment.getId()); // 추천과 비추천은 둘 중 하나만
                }
                liked = false;
            } else {
                if (counters.deleteCommentDislike(comment.getId(), userId)) {
                    counters.addCommentDislikes(comment.getPostId(), comment.getId(), -1);
                }
                liked = counters.commentLiked(comment.getId(), userId);
            }
            return reactionResult(comment, b, liked, on);
        });
    }

    /** 누른 뒤의 수(댓글 행을 다시 읽는다)와 내 상태 — 비추천이 꺼진 게시판은 비추천 칸이 null */
    private CommentLikeResult reactionResult(CommentEntity comment, EffectiveBoard b, boolean liked, boolean disliked) {
        int[] n = counters.commentReactionCounts(comment.getPostId(), comment.getId());
        return new CommentLikeResult(n[0], liked, b.commentDislikeEnabled() ? n[1] : null, b.commentDislikeEnabled() ? disliked : null);
    }
}
