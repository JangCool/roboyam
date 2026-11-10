package com.community.aimember.service;

import com.community.aimember.exception.AiWriteRefusedException;
import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardSecurityValues;
import com.community.board.dto.ContentBody;
import com.community.board.dto.EffectiveBoard;
import com.community.board.exception.BlockedWordException;
import com.community.board.service.BoardAccessPolicy;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.board.service.ContentRules;
import com.community.comment.entity.CommentEntity;
import com.community.comment.repository.master.CommentRepository;
import com.community.comment.service.CommentWriteRules;
import com.community.debate.entity.DebateStance;
import com.community.debate.service.DebateWriteStance;
import com.community.emoticon.service.EmoticonGuard;
import com.community.group.entity.GroupEntity;
import com.community.group.exception.GroupNotFoundException;
import com.community.group.exception.GroupNotMemberException;
import com.community.group.service.GroupAccess;
import com.community.membernotification.dto.CommentNotice;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.point.service.PointService;
import com.community.post.entity.ContentFormat;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostCounterQueries;
import com.community.post.repository.master.PostRepository;
import com.community.post.service.AdultContentGate;
import com.community.post.service.PostContentFields;
import com.community.post.service.PostOpenRules;
import com.community.post.service.PostWriteRules;
import com.community.post.service.SecretPostReaders;
import com.community.post.service.WriterPromotion;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI 회원 생성물 쓰기(ai-members-contract 정책 9 · 「서버 구현 결과」 「쓰기 제한 면제」) — 관리자 서버의 실행기({@link AiDraftPublisher})가 승인 줄을 글 · 소모임 글 · 댓글 · 답글로
 * 등록한다. 공개 사이트 쓰기 경로(api {@code PostWriteService#create} · {@code GroupPostService#create} · {@code CommentService#create})와 <b>같은 판정 코드</b>를 부른다 —
 * 쓰기 막힘·게시판 제재·비밀글·속도·같은 글 반복({@link PostWriteRules}), 답글 대상·댓글 본문·댓글 잠금({@link CommentWriteRules}), 글 칸({@link PostContentFields}),
 * 금칙어·가림({@link ContentRules}), 글 열기({@link PostOpenRules} · {@link AdultContentGate} · {@link SecretPostReaders}), 소모임({@link GroupAccess}), 입장({@link DebateWriteStance}),
 * 쓴 사람({@link BoardActorResolver}). 그래서 금칙어 · 회원 정지 · 게시판 제재 · 운영진만·쓰기 잠금 · 댓글 꺼짐 · 같은 글 반복 · 칸 검사가 사람 회원과 같다.
 *
 * <ul>
 *   <li>AI 생성물은 평문(TEXT) 글·소모임 글·댓글·답글뿐이다 — 비회원 · 자동입력 방지 · 말머리 · 19금 · 투표 · 핫딜 · 찬반 토론 열기 · 이미지가 없다.</li>
 *   <li><b>면제</b>: 사이트 작성 간격 · 하루 한도는 보지 않는다(실행기가 양을 정한다 — 작성자 잠금·같은 글 반복은 본다). {@code gradeExempt}(그 AI 계정의 쓸 곳으로 고른 게시판)면
 *       게시판 최소 쓰기·댓글 등급({@code GRADE_TOO_LOW})도 보지 않는다.</li>
 *   <li><b>「항상 막힘」 게시판</b>({@link AiMemberRules#blockedReason})은 등록할 때도 다시 본다 — 고른 뒤 게시판 설정이 바뀌었으면 {@code WRITE_BLOCKED}.</li>
 *   <li>댓글은 같은 트랜잭션에서 글의 댓글 수 · 댓글 포인트 · 회원 알림(글쓴이 {@code COMMENT} / 답한 댓글 작성자 {@code REPLY})을 낸다 — 사람 댓글과 같다. 글·댓글 모두 커밋 뒤
 *       활동 수 조건 즉시 승급({@link WriterPromotion}).</li>
 *   <li>막히면 공개 API 라면 오류 응답이었을 것을 그대로 던진다 — {@code CommonException}(상태로 사유 코드를 정한다) · {@link ValidationException} · {@link BlockedWordException},
 *       공개 API 의 404·403 이었을 글 열기 판정은 {@link AiWriteRefusedException}({@code TARGET_GONE} · {@code WRITE_BLOCKED}).</li>
 * </ul>
 * 캐시: 공개 목록·가젯 캐시는 글쓰기가 비우지 않고 TTL 로 따라온다(사람 글쓰기도 같다) — 관리자 서버에서 써도 공개 화면에 같은 시점에 보인다.
 */
@Component
public class AiContentWriter {

    private static final Logger log = LoggerFactory.getLogger(AiContentWriter.class);

    static final String TARGET_GONE = "TARGET_GONE";
    static final String WRITE_BLOCKED = "WRITE_BLOCKED";
    private static final String TEXT = ContentFormat.TEXT.name();

    private final BoardCatalog catalog;
    private final BoardActorResolver actors;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final PostCounterQueries counters;
    private final PostWriteRules postRules;
    private final CommentWriteRules commentRules;
    private final PostOpenRules openRules;
    private final AdultContentGate adultGate;
    private final SecretPostReaders secretReaders;
    private final GroupAccess groupAccess;
    private final DebateWriteStance stances;
    private final EmoticonGuard emoticons;
    private final PointService points;
    private final MemberNotificationPublisher notices;
    private final WriterPromotion promotion;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;

    public AiContentWriter(BoardCatalog catalog, BoardActorResolver actors, PostRepository posts, CommentRepository comments, PostCounterQueries counters,
                           PostWriteRules postRules, CommentWriteRules commentRules, PostOpenRules openRules, AdultContentGate adultGate,
                           SecretPostReaders secretReaders, GroupAccess groupAccess, DebateWriteStance stances, EmoticonGuard emoticons, PointService points,
                           MemberNotificationPublisher notices, WriterPromotion promotion, SnowflakeIdGenerator ids, PlatformTransactionManager txManager) {
        this.catalog = catalog;
        this.actors = actors;
        this.posts = posts;
        this.comments = comments;
        this.counters = counters;
        this.postRules = postRules;
        this.commentRules = commentRules;
        this.openRules = openRules;
        this.adultGate = adultGate;
        this.secretReaders = secretReaders;
        this.groupAccess = groupAccess;
        this.stances = stances;
        this.emoticons = emoticons;
        this.points = points;
        this.notices = notices;
        this.promotion = promotion;
        this.ids = ids;
        this.tx = new TransactionTemplate(txManager);
    }

    // ── 글 ────────────────────────────────────────────────────────────────

    /**
     * 게시판 글(공개 사이트 글쓰기와 같은 차례) — 게시판(없음·소모임 게시판 {@code TARGET_GONE}, 꺼짐·항상 막힘 {@code WRITE_BLOCKED}) → 쓰기 판정 → 게시판 제재 → 칸 →
     * 로보티콘 → 금칙어 → (트랜잭션) 작성자 잠금 · 같은 글 반복 → 저장 · 포인트 → 커밋 뒤 승급.
     *
     * @param gradeExempt 그 AI 계정의 쓸 곳으로 고른 게시판 — 최소 쓰기 등급을 보지 않는다
     * @return 새 글 ID
     */
    public long post(long userId, long boardId, String titleText, String bodyText, boolean gradeExempt, String ip) {
        BoardCatalogSnapshot s = catalog.snapshot();
        Optional<EffectiveBoard> found = s.board(boardId);
        refuseIfDenied(PostOpenRules.publicBoardDenial(found));
        EffectiveBoard b = found.get();
        if (AiMemberRules.blockedReason(b) != null) {
            throw new AiWriteRefusedException(WRITE_BLOCKED);
        }
        BoardActor actor = actors.resolve(userId, s);
        if (actor.guest()) {
            PostWriteRules.requireWritable(BoardAccessPolicy.LOGIN_REQUIRED); // 회원이 없어졌거나 탈퇴 — 비회원으로 쓰지 않는다(401 → WRITE_BLOCKED)
        }
        String blocked = BoardAccessPolicy.writeBlocked(b, actor, s);
        PostWriteRules.requireWritable(gradeExempt && BoardAccessPolicy.GRADE_TOO_LOW.equals(blocked) ? null : blocked, b, s);
        PostWriteRules.requireNotRestricted(actor, b.id()); // 게시판 제재의 쓰기 제한(board-sanction-contract 결정 5)
        BoardSecurityValues sec = s.security();

        Map<String, MessageRef> fields = new LinkedHashMap<>();
        PostContentFields.Cleaned c = PostContentFields.check(b, actor.staffOf(b.id()), memberWriter(b, actor, s), null, null, titleText, TEXT, bodyText, null, sec, fields);
        EmoticonGuard.countInto(c.body().content(), "content", fields);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        emoticons.requireOwned(userId, c.body().content());
        Optional<String> word = ContentRules.blockedWord(sec, c.title(), c.body().wordsHaystack(), null);
        if (word.isPresent()) {
            throw new BlockedWordException(word.get());
        }
        String title = ContentRules.mask(sec, c.title());
        ContentBody content = c.body().masked(sec);
        long id = ids.nextId();
        boolean secret = PostWriteRules.secretOnCreate(b, null);
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now();
            postRules.checkRate(s, userId, ip, title, content.plain(), now, true); // 간격·하루 한도 면제 — 작성자 잠금 · 같은 글 반복은 본다
            PostEntity created = PostEntity.create(id, b.id(), c.prefixId(), userId, null, null, ip, title, content.format(), content.content(), content.plain(), c.adult(),
                    c.hasImage(), now);
            created.initOps(secret, b.statusEnabled());
            posts.persist(created);
            emoticons.recordUse(userId, c.body().content());
            points.earnPost(new PointService.PostEarn(userId, id, content.plain(), b.pointsEnabled(), b.pointMinLengthApplied()));
        });
        promotion.afterWrite(userId); // 커밋 뒤 — 활동 수 조건 즉시 승급(실패해도 글은 그대로)
        return id;
    }

    /**
     * AI 전용 소모임 글(공개 사이트 소모임 글쓰기와 같은 차례) — 트랜잭션 안에서 소모임 행 잠금(없음 {@code TARGET_GONE} 쪽 404 · 비활성 403) → 멤버(모임장 포함) → 쓰기 자격
     * → 칸 → 로보티콘 → 금칙어 → 같은 글 반복 → 저장(비공개 소모임이면 비공개 글) · 포인트 → 소모임 최근 활동 시각. 게시판 쓰기 정책(등급·잠금·운영진만)은 원래 받지 않는다.
     *
     * @return 새 글 ID
     */
    public long groupPost(long groupId, long userId, String titleText, String bodyText, String ip) {
        Long written = tx.execute(status -> {
            GroupEntity group = groupAccess.lockActive(groupId);
            if (!groupAccess.isMember(group, userId)) {
                throw new GroupNotMemberException();
            }
            BoardActor actor = groupAccess.actor(userId);
            PostWriteRules.requireWritable(GroupAccess.postBlocked(actor));
            BoardCatalogSnapshot s = catalog.snapshot();
            // 꺼진 게시판도 쓴다 — 소모임은 멈추지 않는다(공개 사이트와 같다)
            EffectiveBoard b = s.board(group.getBoardId()).orElseThrow(GroupNotFoundException::new);
            BoardSecurityValues sec = s.security();
            Map<String, MessageRef> fields = new LinkedHashMap<>();
            PostContentFields.Cleaned c = PostContentFields.check(b, actor.staffOf(b.id()), memberWriter(b, actor, s), null, null, titleText, TEXT, bodyText, null, sec,
                    fields);
            EmoticonGuard.countInto(c.body().content(), "content", fields);
            if (!fields.isEmpty()) {
                throw new ValidationException(fields);
            }
            emoticons.requireOwned(userId, c.body().content());
            Optional<String> word = ContentRules.blockedWord(sec, c.title(), c.body().wordsHaystack());
            if (word.isPresent()) {
                throw new BlockedWordException(word.get());
            }
            String title = ContentRules.mask(sec, c.title());
            ContentBody content = c.body().masked(sec);
            long id = ids.nextId();
            Instant now = Instant.now();
            postRules.checkRate(s, userId, ip, title, content.plain(), now, true);
            PostEntity created = PostEntity.createInGroup(id, group.getId(), b.id(), c.prefixId(), userId, ip, title, content.format(), content.content(), content.plain(),
                    c.hasImage(), group.isPublicGroup(), now);
            created.initOps(PostWriteRules.secretOnCreate(b, null), b.statusEnabled());
            posts.persist(created);
            emoticons.recordUse(userId, c.body().content());
            points.earnPost(new PointService.PostEarn(userId, id, content.plain(), b.pointsEnabled(), b.pointMinLengthApplied()));
            group.touchActivity(Instant.now());
            return id;
        });
        promotion.afterWrite(userId);
        log.info("소모임 글을 썼다(AI 회원) groupId={} postId={} userId={}", groupId, written, userId);
        return written;
    }

    // ── 댓글 · 답글 ────────────────────────────────────────────────────────

    /**
     * 댓글·답글(공개 사이트 댓글 쓰기와 같은 차례) — 글(없음·지움 {@code TARGET_GONE}) → 글 열기(게시판·소모임 · 19금 · 비밀글) → 숨김 → (일반 글) 항상 막힘 → 댓글 판정 →
     * 게시판 제재(일반 글) → 답글 대상 → 입장(의견에 단 답글이면 투표한 입장이 있어야 한다) → 본문 칸 → 로보티콘 → 금칙어 → (트랜잭션) 작성자 잠금 → 저장 · 댓글 수 · 포인트 · 알림.
     *
     * @param parentId    답글이면 원댓글(댓글이면 null)
     * @param replyToId   답글이 가리키는 댓글(원댓글 그 자체면 null)
     * @param gradeExempt 그 AI 계정의 쓸 곳으로 고른 게시판의 글 — 최소 댓글 등급을 보지 않는다
     * @return 새 댓글 ID
     */
    public long comment(long postId, long userId, Long parentId, Long replyToId, String bodyText, boolean gradeExempt, String ip) {
        Instant start = Instant.now();
        PostEntity post = posts.findById(postId).filter(p -> !p.isGoneAt(start)).orElseThrow(() -> new AiWriteRefusedException(TARGET_GONE));
        BoardCatalogSnapshot s = catalog.snapshot();
        PostOpenRules.Opened opened = openRules.open(s, post, userId);
        refuseIfDenied(opened.denial());
        adultGate.requireViewable(post, userId); // 19금 글은 성인만(403 POST_ADULT_ONLY)
        if (!secretReaders.memberCanRead(s, post, userId, opened.group(), opened.membership())) {
            throw new AiWriteRefusedException(WRITE_BLOCKED); // 읽을 수 없는 비밀글(공개 API 403 SECRET_POST)
        }
        EffectiveBoard b = opened.board();
        if (post.isHiddenAt(start)) {
            throw new AiWriteRefusedException(WRITE_BLOCKED); // 숨긴 글(공개 API 403 POST_HIDDEN)
        }
        if (!post.isGroupPost() && (AiMemberRules.blockedReason(b) != null || post.isAnonymous())) {
            throw new AiWriteRefusedException(WRITE_BLOCKED); // 익명 글(게시판을 나중에 되돌렸어도)에도 쓰지 않는다 — anonymous-board-contract 정책 7
        }
        BoardActor actor = actors.resolve(userId, s);
        if (actor.guest()) {
            PostWriteRules.requireWritable(BoardAccessPolicy.LOGIN_REQUIRED); // 회원이 없어졌거나 탈퇴 — 비회원으로 쓰지 않는다(401 → WRITE_BLOCKED)
        }
        // 소모임 글의 댓글은 게시판 댓글 정책(최소 등급·잠금·끔)을 받지 않는다 — 계정 자격만
        String blocked = post.isGroupPost() ? GroupAccess.commentBlocked(actor) : BoardAccessPolicy.commentBlocked(b, actor, s);
        PostWriteRules.requireWritable(gradeExempt && BoardAccessPolicy.GRADE_TOO_LOW.equals(blocked) ? null : blocked);
        if (!post.isGroupPost()) {
            PostWriteRules.requireNotRestricted(actor, b.id());
        }
        BoardSecurityValues sec = s.security();

        Map<String, MessageRef> fields = new LinkedHashMap<>();
        CommentWriteRules.ReplyTarget reply = commentRules.replyTarget(post.getId(), parentId == null ? null : Long.toString(parentId),
                replyToId == null ? null : Long.toString(replyToId), fields);
        // 의견에 단 답글이면 의견 쓰기다(공개 사이트와 같다 — 투표한 입장이 없으면 409 STANCE_REQUIRED). AI 는 원댓글을 의견으로 쓰지 않는다
        boolean opinion = reply.parent() != null && reply.parent().getStance() != null;
        DebateStance stance = fields.containsKey("parentId") ? null : stances.resolve(post.getId(), userId, opinion, null);
        int max = stance == null ? ContentRules.COMMENT_MAX : DebateWriteStance.OPINION_MAX;
        ContentBody content = CommentWriteRules.checkContent(b, TEXT, bodyText, CommentWriteRules.writer(actor, b, s), sec, max, fields);
        EmoticonGuard.countInto(content.content(), "content", fields);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        emoticons.requireOwned(userId, content.content());
        Optional<String> word = ContentRules.blockedWord(sec, content.wordsHaystack(), null);
        if (word.isPresent()) {
            throw new BlockedWordException(word.get());
        }
        ContentBody masked = content.masked(sec);
        long id = ids.nextId();
        Long parent = reply.parentId();
        Long replyTo = reply.replyToId();
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now();
            commentRules.lockWithoutLimits(userId); // 간격·하루 한도 면제 — 같은 작성자 잠금으로 줄만 선다
            CommentEntity comment = CommentEntity.create(id, post.getId(), b.id(), parent, replyTo, userId, null, null, ip, masked.format(), masked.content(),
                    stance == null ? null : stance.name(), now);
            comments.persist(comment);
            emoticons.recordUse(userId, content.content());
            counters.addCommentCount(post.getId(), 1);
            // 같은 트랜잭션의 마지막에: 댓글 포인트 · 회원 알림 — 둘 다 실패해도 댓글은 그대로다(세이브포인트로 격리)
            points.earnComment(new PointService.CommentEarn(userId, id, post.getId(), post.getAuthorId(), masked.plain(), b.pointsEnabled()));
            Long replyTargetAuthor = null;
            if (parent != null) {
                CommentEntity target = comments.findByPostIdAndId(post.getId(), replyTo != null ? replyTo : parent).orElse(null);
                replyTargetAuthor = target == null ? null : target.getAuthorId();
            }
            notices.commentWritten(new CommentNotice(post.getId(), post.getAuthorId(), id, userId, null, replyTargetAuthor, parent != null, post.getTitle(),
                    masked.plain()));
        });
        promotion.afterWrite(userId);
        return id;
    }

    // ── 도우미 ────────────────────────────────────────────────────────────

    /** 링크·이미지 규칙의 작성자 구분 — AI 회원은 비회원이 아니다(새 회원 규칙 대상이면 그 규칙) */
    private static ContentRules.Writer memberWriter(EffectiveBoard b, BoardActor actor, BoardCatalogSnapshot s) {
        return CommentWriteRules.writer(actor, b, s);
    }

    /** 글 열기 막힘 → 사유 코드(없는 글·게시판 {@code TARGET_GONE}, 꺼짐·비활성·비공개 {@code WRITE_BLOCKED}) */
    private static void refuseIfDenied(PostOpenRules.Denial denial) {
        if (denial == null) {
            return;
        }
        throw new AiWriteRefusedException(switch (denial) {
            case POST_NOT_FOUND, BOARD_NOT_FOUND -> TARGET_GONE;
            case BOARD_INACTIVE, GROUP_INACTIVE, GROUP_POST_PRIVATE -> WRITE_BLOCKED;
        });
    }
}
