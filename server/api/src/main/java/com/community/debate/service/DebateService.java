package com.community.debate.service;

import com.community.block.service.ViewerBlocks;
import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.AuthorViews;
import com.community.board.service.BoardAccessPolicy;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.board.service.ContentRules;
import com.community.board.service.PublicBoardService;
import com.community.comment.entity.CommentEntity;
import com.community.comment.repository.master.CommentRepository;
import com.community.debate.dto.request.DebateSettingsRequest;
import com.community.debate.dto.request.StanceRequest;
import com.community.debate.dto.response.CommenterListView;
import com.community.debate.dto.response.CommenterView;
import com.community.debate.dto.response.DebateSummaryView;
import com.community.debate.dto.response.StanceView;
import com.community.debate.entity.DebateStance;
import com.community.debate.entity.PostDebateEntity;
import com.community.debate.exception.DebateHasVotesException;
import com.community.debate.exception.DebateManageDeniedException;
import com.community.debate.exception.DebateNotOpenException;
import com.community.debate.exception.StanceChangeLockedException;
import com.community.debate.repository.master.DebateQueries;
import com.community.post.entity.PostEntity;
import com.community.post.exception.PostHiddenException;
import com.community.post.service.PostQueryService;
import com.community.post.service.PostWriteService;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 찬반 토론의 공개 API — 토론 열기·설정·닫기({@code PUT /api/posts/{postId}/debate}), 입장 고르기·바꾸기({@code PUT /api/posts/{postId}/stance}),
 * @ 지정 후보({@code GET /api/posts/{postId}/commenters?q=}). 판정은 {@link DebateReader}. docs/features/post-debate-contract.md · popular-highlight-contract.md 「서버 구현 결과」.
 *
 * <ul>
 *   <li><b>토론 설정</b>: 로그인 회원만(401). 글 읽기 규칙(404 · 403 POST_HIDDEN · 403 BOARD_INACTIVE) → {@code enabled} 필수(400 {@code fields.enabled})
 *       → 자격({@link DebateReader#manage} — 그 게시판 매니저 또는 글쓴이 규칙, 아니면 403 DEBATE_MANAGE_DENIED) → 글쓴이(매니저 아님)가 투표 있는 토론을 닫으면 409 DEBATE_HAS_VOTES.
 *       글쓴이가 열면 {@code opened_by_kind MEMBER}, 매니저가 열면 {@code MANAGER}(공개 {@code openedBy} 는 OPERATOR — 글쓴이에게 읽기 전용). 저장은 {@link PostDebateWriter}.
 *       응답은 글 상세의 {@code debate} 와 같은 모양. 감사 없음(회원 행위).</li>
 *   <li><b>입장 고르기 = 투표</b>(사용자 결정 2026-10-24): 로그인 회원만(SecurityConfig — 401). 글 읽기 규칙(404 POST_NOT_FOUND · 403 POST_HIDDEN · 403 BOARD_INACTIVE)
 *       → 댓글 쓰기 자격(403 까닭 — 댓글 쓰기와 같다) → {@code stance} PRO·CON·NEUTRAL(아니면 400 {@code fields.stance}) → 토론이 열려 있어야(409 DEBATE_NOT_OPEN)
 *       → 「입장 바꾸기 허용」이 꺼졌는데 이미 고른 입장을 다른 값으로 바꾸면 409 STANCE_CHANGE_LOCKED
 *       → 중립이 꺼진 토론에서 새로 NEUTRAL 을 고르면 400 {@code fields.stance}(이미 중립이면 그대로 — 멱등). 처음이면 입장을 만들고({@code moved 0}), 같은 값이면 그대로(멱등).
 *       바꾸기 잠금은 처음 고르기·같은 값에는 걸리지 않고, 나중에 꺼도 이미 고른 입장은 그대로 두고 그 뒤의 바꾸기만 막는다.
 *       입장 취소는 없다.
 *       <b>소급한다</b>(사용자 결정 2026-10-24 — community 와 다름): 입장이 실제로 바뀌면 같은 트랜잭션에서 그 회원이 그 글에 쓴 입장 있는 댓글(원댓글·답글, 숨긴 것·지운 것 포함)의
 *       배지도 새 입장으로 바꾼다({@link DebateQueries#moveOpinions}). 일반 댓글은 그대로, 고정·숨김 상태도 그대로. 응답 {@code moved} = 옮긴 의견 중 지우지 않은 것의 수.
 *       감사·알림 없음. 횟수 제한 없음.</li>
 *   <li><b>@ 지정 후보</b>(의견에만): 로그인 회원만(401 — 경로는 GET 이라 SecurityConfig 가 열어 두고 서비스가 막는다). 이 글에 지우거나 숨기지 않은 <b>의견</b>(토론 구획의 댓글 —
 *       의견 원댓글과 그 아래 답글)을 쓴 회원 중
 *       나·내가 차단한 회원·탈퇴한 회원·비회원을 뺀 사람, 닉네임에 {@code q}(대소문자 무시, 앞 {@value #QUERY_MAX}자)가 든 사람을 가장 최근에 쓴 순으로 {@value #LIMIT}명.
 *       고른 회원의 {@code parentId}·{@code replyToId} 로 답글을 쓰면 기존 답글 규칙(답글 알림 포함) 그대로다 — 본문 앞 {@code @닉네임} 은 화면이 붙인다. 따로 멘션 알림은 없다.</li>
 * </ul>
 */
@Service
public class DebateService {

    private static final Logger log = LoggerFactory.getLogger(DebateService.class);

    static final int QUERY_MAX = 20;
    static final int LIMIT = 10;

    private final BoardCatalog catalog;
    private final BoardActorResolver actors;
    private final PostQueryService postQueries;
    private final CommentRepository comments;
    private final DebateReader reader;
    private final DebateQueries queries;
    private final AuthorViews authors;
    private final UserRepository users;
    private final ViewerBlocks viewerBlocks;
    private final PostDebateWriter debateWriter;

    public DebateService(BoardCatalog catalog, BoardActorResolver actors, PostQueryService postQueries, CommentRepository comments, DebateReader reader,
                         DebateQueries queries, AuthorViews authors, UserRepository users, ViewerBlocks viewerBlocks, PostDebateWriter debateWriter) {
        this.catalog = catalog;
        this.actors = actors;
        this.postQueries = postQueries;
        this.comments = comments;
        this.reader = reader;
        this.queries = queries;
        this.authors = authors;
        this.users = users;
        this.viewerBlocks = viewerBlocks;
        this.debateWriter = debateWriter;
    }

    @Transactional
    public DebateSummaryView changeDebate(String postIdText, DebateSettingsRequest body, Long userId) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        Instant now = Instant.now();
        PostEntity post = postQueries.requireVisible(postIdText, now);
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = PublicBoardService.requireActive(s, post.getBoardId());
        if (post.isHiddenAt(now)) {
            throw new PostHiddenException();
        }
        if (body == null || body.enabled() == null) {
            throw ValidationException.of("enabled", new MessageRef("debate.enabled.required"));
        }
        DebateReader.Status status = reader.status(post.getId(), now);
        DebateReader.Manage manage = reader.manage(status, post, b, userId);
        if (!manage.canManage()) {
            throw new DebateManageDeniedException();
        }
        if (!body.enabled() && status.open() && !manage.manager() && queries.votesOf(post.getId()).total() > 0) {
            throw new DebateHasVotesException();
        }
        String kind = manage.manager() ? PostDebateEntity.BY_MANAGER : PostDebateEntity.BY_MEMBER;
        PostDebateWriter.Change change = debateWriter.apply(post.getId(), body.enabled(), body.neutralEnabled(), body.stanceChangeAllowed(), kind, userId, now);
        if (change.opened() || change.closed() || change.updated()) {
            log.info("글 토론 {} postId={} by userId={}({})", change.opened() ? "열기" : change.closed() ? "닫기" : "설정 바꾸기", post.getId(), userId, kind);
        }
        return reader.summary(reader.status(post.getId(), now), post, b, userId, now);
    }

    @Transactional
    public StanceView changeStance(String postIdText, StanceRequest body, Long userId) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        Instant now = Instant.now();
        PostEntity post = postQueries.requireVisible(postIdText, now);
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = PublicBoardService.requireActive(s, post.getBoardId());
        if (post.isHiddenAt(now)) {
            throw new PostHiddenException();
        }
        BoardActor actor = actors.resolve(userId, s);
        PostWriteService.requireWritable(BoardAccessPolicy.commentBlocked(b, actor, s));
        DebateStance wanted = parseRequired(body == null ? null : body.stance());
        DebateReader.Status status = reader.status(post.getId(), now);
        if (!status.open()) {
            throw new DebateNotOpenException();
        }
        DebateStance current = queries.lockStance(post.getId(), userId).orElse(null);
        requireChangeAllowed(wanted, current, status);
        requireNeutralAllowed(wanted, current, status);
        if (current == null) {
            if (queries.insertIfAbsent(post.getId(), userId, wanted, now)) {
                return new StanceView(wanted.name(), 0); // 처음 고르기(투표) — 옮길 의견이 없다
            }
            current = queries.lockStance(post.getId(), userId).orElseThrow(); // 같은 회원의 다른 요청이 방금 먼저 골랐다 — 바꾸기로 이어 간다
            requireChangeAllowed(wanted, current, status);
            requireNeutralAllowed(wanted, current, status);
        }
        int moved = 0;
        if (current != wanted) {
            queries.change(post.getId(), userId, wanted, now);
            moved = queries.moveOpinions(post.getId(), userId, wanted); // 소급 — 같은 트랜잭션(사용자 결정 2026-10-24)
        }
        return new StanceView(wanted.name(), moved);
    }

    @Transactional(readOnly = true)
    public CommenterListView commenters(String postIdText, String q, Long userId) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        Instant now = Instant.now();
        PostEntity post = postQueries.requireVisible(postIdText, now);
        BoardCatalogSnapshot s = catalog.snapshot();
        PublicBoardService.requireActive(s, post.getBoardId());
        if (post.isHiddenAt(now)) {
            throw new PostHiddenException();
        }
        String query = ContentRules.normalize(q);
        if (query.length() > QUERY_MAX) {
            query = query.substring(0, QUERY_MAX);
        }
        String needle = query.toLowerCase(Locale.ROOT);

        // 회원마다 가장 최근의(지우거나 숨기지 않은) 의견 — 오래된순 목록을 거꾸로 훑는다. 토론 구획은 원댓글의 입장으로 가르므로 원댓글이 의견인 것만(답글로 쓰면 그 의견 아래로 간다)
        List<CommentEntity> all = comments.findByPostIdOrderByIdAsc(post.getId());
        Set<Long> opinionTops = new HashSet<>();
        all.stream().filter(c -> c.getParentId() == null && c.getStance() != null).forEach(c -> opinionTops.add(c.getId()));
        Map<Long, CommentEntity> latest = new LinkedHashMap<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            CommentEntity c = all.get(i);
            if (c.getAuthorId() == null || c.isDeleted() || c.isHiddenAt(now) || c.getAuthorId().equals(userId)
                    || !opinionTops.contains(c.getParentId() == null ? c.getId() : c.getParentId())) {
                continue;
            }
            latest.putIfAbsent(c.getAuthorId(), c);
        }
        if (latest.isEmpty()) {
            return new CommenterListView(List.of());
        }
        List<Long> memberIds = new ArrayList<>(latest.keySet());
        Map<Long, ProfileEntity> members = authors.members(memberIds);
        Set<Long> gone = new HashSet<>();
        users.findAllById(memberIds).stream().filter(UserEntity::isGone).forEach(u -> gone.add(u.getId()));
        ViewerBlocks.Lens lens = viewerBlocks.lens(userId, memberIds);

        List<CommenterView> out = new ArrayList<>();
        latest.values().stream().sorted(Comparator.comparing(CommentEntity::getId, Comparator.reverseOrder())).forEach(c -> {
            Long authorId = c.getAuthorId();
            ProfileEntity profile = members.get(authorId);
            if (out.size() >= LIMIT || profile == null || gone.contains(authorId) || lens.blocked().contains(authorId)) {
                return;
            }
            if (!needle.isEmpty() && (profile.getNickname() == null || !profile.getNickname().toLowerCase(Locale.ROOT).contains(needle))) {
                return;
            }
            out.add(new CommenterView(authors.of(authorId, null, null, members, s).withMemo(lens.memoOf(authorId)),
                    Long.toString(c.getParentId() == null ? c.getId() : c.getParentId()), Long.toString(c.getId())));
        });
        return new CommenterListView(out);
    }

    /**
     * 「입장 바꾸기 허용」이 꺼진 토론에서 이미 고른 입장을 다른 값으로 바꾸려 하면 409 STANCE_CHANGE_LOCKED — 처음 고르기({@code current} null)·같은 값은 통과.
     * 막히면 소급 이동도 일어나지 않는다. 나중에 꺼도 이미 고른 입장은 그대로다.
     */
    private static void requireChangeAllowed(DebateStance wanted, DebateStance current, DebateReader.Status status) {
        if (current != null && current != wanted && !status.stanceChangeAllowed()) {
            throw new StanceChangeLockedException();
        }
    }

    /** 중립이 꺼진 토론에서 <b>새로</b> 중립을 고르면 400 {@code fields.stance} — 이미 중립이면 그대로 둔다(기존 중립 표·의견은 남긴다) */
    private static void requireNeutralAllowed(DebateStance wanted, DebateStance current, DebateReader.Status status) {
        if (wanted == DebateStance.NEUTRAL && current != DebateStance.NEUTRAL && !status.neutralEnabled()) {
            throw ValidationException.of("stance", new MessageRef("debate.stance.neutralOff"));
        }
    }

    /** 입장 고르기의 {@code stance} — 비었거나 PRO·CON·NEUTRAL 이 아니면 400 */
    private static DebateStance parseRequired(String raw) {
        try {
            DebateStance stance = DebateStance.parse(raw);
            if (stance != null) {
                return stance;
            }
        } catch (IllegalArgumentException ignored) {
            // 아래에서 칸 오류
        }
        throw ValidationException.of("stance", new MessageRef("debate.stance.invalid"));
    }
}
