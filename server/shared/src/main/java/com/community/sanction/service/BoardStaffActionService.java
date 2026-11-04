package com.community.sanction.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.comment.entity.CommentEntity;
import com.community.comment.repository.master.CommentRepository;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.permission.entity.MemberPermission;
import com.community.point.service.PointService;
import com.community.post.entity.ContentFormat;
import com.community.post.entity.PostEntity;
import com.community.post.exception.PostStateInvalidException;
import com.community.post.repository.master.PostCounterQueries;
import com.community.post.repository.master.PostRepository;
import com.community.post.service.PostModerationActions;
import com.community.post.service.PostModerationActions.ModerationActor;
import com.community.sanction.exception.BoardSanctionException;
import com.community.sanction.exception.BoardStaffActionException;
import com.community.sanction.repository.master.BoardStaffActionStore;
import com.community.sanction.repository.master.BoardStaffActionStore.ActionRow;
import com.community.sanction.repository.master.BoardStaffActionStore.Limits;
import com.community.sanction.repository.master.BoardStaffActionStore.PageFilter;
import com.community.sanction.repository.master.BoardStaffActionStore.StaffAccess;
import com.community.sanction.repository.master.BoardStaffActionStore.Usage;
import com.community.sanction.service.BoardSanctionService.Quota;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * 게시판 스텝 처리(docs/features/board-staff-action-contract.md 정책 1~9) — 매니저·스텝의 글 숨기기 · 글 삭제 · 댓글 삭제와 되돌리기(매니저 · 운영자).
 * api(처리 · 내역 · 매니저 되돌리기 · {@code viewer.staffTools})와 admin(스텝 처리 감독 · 운영자 되돌리기)이 같이 쓴다.
 *
 * <ul>
 *   <li><b>효과는 운영자 조치와 같다</b>(정책 2): 글은 shared {@link PostModerationActions}(운영자 게시글 관리와 같은 몸통 — 까닭 STAFF·고정 해제 · 소프트 삭제·포인트 회수 · 조치 기록
 *       {@code post_moderation_events} 행위자 BOARD_STAFF), 댓글은 운영자 댓글 삭제(신고 처리)와 같은 규칙(보이던 댓글이면 글의 댓글 수 −1 · 포인트 회수 · 답글이 있으면 목록에 자리가 남는다).
 *       작성자 알림은 운영자와 같은 종류·같은 문구({@code POST_HIDDEN}·{@code POST_REMOVED}·{@code COMMENT_REMOVED}, 사유 = 사유 코드 이름) — 처리자 없음 · 메일 없음.</li>
 *   <li><b>판정 순서</b>: 404(글·댓글 — 임시저장·없는 글, 그 글의 댓글이 아님) → 403 {@code BOARD_STAFF_FORBIDDEN}(그 게시판 매니저·스텝 · 계정 정상·인증 · 역할 권한) →
 *       400 {@code fields.reasonCode} → <b>글 행 잠금</b>(→ 댓글 행 잠금) → 409 {@code BOARD_STAFF_TARGET_PROTECTED}(공지·고정 글 · 같은 게시판 매니저·스텝의 것 · 소모임 글 · 본인 것) →
 *       409 {@code POST_STATE_INVALID}·{@code COMMENT_STATE_INVALID} → 409 {@code BOARD_STAFF_DAILY_LIMIT}(원자적 증가 — 실패한 시도는 세지 않는다) → 조치 + 처리 기록 + 알림, 한 트랜잭션.
 *       잠금 차례: 글 행 → 댓글 행 → 사용량 행.</li>
 *   <li><b>되돌리기</b>(정책 6): 매니저({@code board.staff-action.restore} + 자기 게시판) · 운영자({@code keeper-action.restore} — 입구가 본다, 감사 {@code BOARD_STAFF_ACTION_RESTORE}).
 *       글 행(→ 댓글 행) 잠금 → 기록 조건부 UPDATE(이미 되돌림 409 {@code BOARD_STAFF_ACTION_RESTORED}) → 숨김 해제(까닭 STAFF 인 숨긴 글만) · 글 복구(파기 409 POST_PURGED, 회수한 포인트 되돌림)
 *       · 댓글 복구(지운 댓글만, 보이는 댓글이면 댓글 수 +1). 작성자에게 알리지 않는다. 한도는 돌려받지 않는다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class BoardStaffActionService {

    private static final Logger log = LoggerFactory.getLogger(BoardStaffActionService.class);

    /** 처리 사유 코드(정책 3 — OTHER 는 제재에만) */
    public static final Set<String> REASONS = Set.of("SPAM", "ABUSE", "INVESTMENT", "OFF_TOPIC");
    /** 공개 내역 한 쪽 */
    public static final int PAGE_SIZE = 20;
    /** 댓글 앞부분 스냅숏 글자 수 */
    public static final int EXCERPT_MAX = 40;
    /** 알림의 댓글 앞부분(운영자 댓글 삭제와 같은 80자 — 발행기가 다시 자른다) */
    static final int NOTICE_EXCERPT_MAX = 80;
    /** 되돌림 사유 최대 글자 */
    public static final int REASON_MAX = 200;
    /** 감사 대상 */
    public static final String AUDIT_TARGET = "POST";

    /** 매니저·스텝 도구({@code viewer.staffTools}) — 할 수 있는 일 + 오늘 한도 셋 */
    public record Tools(boolean manager, boolean postHide, boolean postDelete, boolean commentDelete, boolean sanction, boolean restore, Quota post, Quota comment,
                        Quota sanctionQuota, Instant resetsAt) {
    }

    /** 처리 결과 — 그 종류의 남은 한도 */
    public record Done(Quota quota, long actionId) {
    }

    /** 공개 내역 한 쪽(1부터) — {@code canRestore} 매니저 + {@code board.staff-action.restore} */
    public record Page(boolean manager, boolean canRestore, List<ActionRow> rows, long total, int page, int size) {
    }

    private final BoardStaffActionStore store;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final PostCounterQueries counters;
    private final PostModerationActions actions;
    private final PointService points;
    private final BoardCatalog catalog;
    private final MemberNotificationPublisher memberNotices;
    private final AuditLogService audit;
    private final Messages messages;
    private final SnowflakeIdGenerator ids;
    private final BoardSanctionService sanctions;

    public BoardStaffActionService(BoardStaffActionStore store, PostRepository posts, CommentRepository comments, PostCounterQueries counters,
                                   PostModerationActions actions, PointService points, BoardCatalog catalog, MemberNotificationPublisher memberNotices,
                                   AuditLogService audit, Messages messages, SnowflakeIdGenerator ids, BoardSanctionService sanctions) {
        this.store = store;
        this.posts = posts;
        this.comments = comments;
        this.counters = counters;
        this.actions = actions;
        this.points = points;
        this.catalog = catalog;
        this.memberNotices = memberNotices;
        this.audit = audit;
        this.messages = messages;
        this.ids = ids;
        this.sanctions = sanctions;
    }

    /** 지금(DB 시각 정밀도 — 게시판 제재와 같은 시계) */
    public Instant now() {
        return sanctions.now();
    }

    // ── 도구 ──────────────────────────────────────────────────────────────

    /**
     * 게시판 응답의 {@code viewer.staffTools} — 그 게시판 매니저·스텝이고 계정 정상·인증이며 네 처리 권한(글 숨기기 · 글 삭제 · 댓글 삭제 · 제재) 중 하나라도 있으면(아니면 빈 값).
     * {@code restore} = 매니저 + {@code board.staff-action.restore}.
     */
    public Optional<Tools> tools(long boardId, long userId) {
        StaffAccess access = store.access(boardId, userId);
        boolean hide = access.can(MemberPermission.BOARD_POST_HIDE);
        boolean delete = access.can(MemberPermission.BOARD_POST_DELETE);
        boolean comment = access.can(MemberPermission.BOARD_COMMENT_DELETE);
        boolean sanction = access.can(MemberPermission.BOARD_SANCTION_ISSUE);
        if (!(hide || delete || comment || sanction)) {
            return Optional.empty();
        }
        Instant now = now();
        Instant resetsAt = BoardSanctionService.nextResetAfter(now);
        Limits limits = store.limits(boardId);
        Usage used = store.usage(boardId, userId, ServiceTime.dateOf(now));
        return Optional.of(new Tools(access.manager(), hide, delete, comment, sanction, access.manager() && access.can(MemberPermission.BOARD_STAFF_ACTION_RESTORE),
                new Quota(limits.posts(), used.posts(), resetsAt), new Quota(limits.comments(), used.comments(), resetsAt),
                new Quota(limits.sanctions(), used.sanctions(), resetsAt), resetsAt));
    }

    // ── 글 숨기기 · 글 삭제 ───────────────────────────────────────────────

    @Transactional
    public Done hidePost(long actorId, long postId, String reasonCode) {
        return onPost(actorId, postId, reasonCode, BoardStaffActionStore.ACTION_HIDE);
    }

    @Transactional
    public Done deletePost(long actorId, long postId, String reasonCode) {
        return onPost(actorId, postId, reasonCode, BoardStaffActionStore.ACTION_DELETE);
    }

    private Done onPost(long actorId, long postId, String reasonCode, String action) {
        boolean hide = BoardStaffActionStore.ACTION_HIDE.equals(action);
        MemberPermission permission = hide ? MemberPermission.BOARD_POST_HIDE : MemberPermission.BOARD_POST_DELETE;
        long seenBoard = store.publishedPostBoard(postId).orElseThrow(BoardStaffActionException::postNotFound);
        requireAble(seenBoard, actorId, permission);
        String code = requireReason(reasonCode);

        PostEntity post = posts.findWithLockById(postId).orElseThrow(BoardStaffActionException::postNotFound);
        StaffAccess access = requireAble(post.getBoardId(), actorId, permission); // 그새 옮겨졌을 수 있다 — 잠근 글의 게시판으로 다시
        long boardId = post.getBoardId();
        Instant now = now();
        if (post.isGroupPost() || groupBoard(boardId) || notice(post) || post.getPinnedAt() != null || protectedAuthor(boardId, post.getAuthorId(), actorId)) {
            throw BoardStaffActionException.targetProtected();
        }
        if (hide ? post.isGoneAt(now) || post.isHiddenAt(now) : post.isGoneAt(now)) {
            throw new PostStateInvalidException();
        }
        Quota quota = consume(BoardStaffActionStore.KIND_POST, boardId, actorId, now);

        long actionId = ids.nextId();
        String label = reasonLabel(code);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("boardStaffActionId", Long.toString(actionId));
        extra.put("reasonCode", code);
        ModerationActor actor = ModerationActor.boardStaff(actorId, access.role());
        String authorName = post.getAuthorId() == null ? post.getGuestName() : store.nickname(post.getAuthorId());
        if (hide) {
            actions.hide(actor, post, label, extra, now);
        } else {
            long revoked = PostModerationActions.revokedOf(actions.delete(actor, post, label, extra, now));
            extra.put("revokedPoints", revoked);
        }
        store.insert(actionId, boardId, actorId, access.role(), BoardStaffActionStore.KIND_POST, action, post.getAuthorId(), post.getId(), null, code,
                cut(post.getTitle(), 200), null, cut(authorName, 40), now);
        if (post.getAuthorId() != null) {
            if (hide) {
                memberNotices.postHiddenByBoardStaff(post.getAuthorId(), post.getId(), post.getTitle(), label);
            } else {
                memberNotices.removedByBoardStaff(post.getAuthorId(), post.getId(), null, post.getTitle(), label, PostModerationActions.revokedOf(extra));
            }
        }
        log.info("스텝 글 처리 action={} postId={} boardId={} by userId={} role={} actionId={}", action, post.getId(), boardId, actorId, access.role(), actionId);
        return new Done(quota, actionId);
    }

    // ── 댓글 삭제 ─────────────────────────────────────────────────────────

    @Transactional
    public Done deleteComment(long actorId, Long postId, long commentId, String reasonCode) {
        if (postId == null) {
            throw BoardStaffActionException.commentNotFound();
        }
        long seenBoard = store.publishedPostBoard(postId).orElseThrow(BoardStaffActionException::commentNotFound);
        if (!store.commentExists(postId, commentId)) {
            throw BoardStaffActionException.commentNotFound();
        }
        requireAble(seenBoard, actorId, MemberPermission.BOARD_COMMENT_DELETE);
        String code = requireReason(reasonCode);

        PostEntity post = posts.findWithLockById(postId).orElseThrow(BoardStaffActionException::commentNotFound);
        CommentEntity comment = comments.findWithLockByPostIdAndId(postId, commentId).orElseThrow(BoardStaffActionException::commentNotFound);
        StaffAccess access = requireAble(post.getBoardId(), actorId, MemberPermission.BOARD_COMMENT_DELETE);
        long boardId = post.getBoardId();
        Instant now = now();
        if (post.isGroupPost() || groupBoard(boardId) || protectedAuthor(boardId, comment.getAuthorId(), actorId)) {
            throw BoardStaffActionException.targetProtected();
        }
        if (comment.isDeleted() || post.isGoneAt(now)) {
            throw BoardStaffActionException.commentStateInvalid();
        }
        Quota quota = consume(BoardStaffActionStore.KIND_COMMENT, boardId, actorId, now);

        // 운영자 댓글 삭제(신고 처리)와 같은 규칙 — 보이던 댓글이면 글의 댓글 수 −1, 포인트 회수. 답글이 있으면 목록에 자리가 남는다(목록 규칙)
        boolean wasVisible = !comment.isHiddenAt(now);
        comment.delete(now);
        if (wasVisible) {
            counters.addCommentCount(post.getId(), -1);
        }
        long revoked = comment.getAuthorId() == null ? 0 : points.revokeContent(comment.getAuthorId(), PointService.REF_COMMENT, comment.getId());
        comments.flush();

        long actionId = ids.nextId();
        String text = plain(comment.getContentFormat(), comment.getContent());
        String authorName = comment.getAuthorId() == null ? comment.getGuestName() : store.nickname(comment.getAuthorId());
        store.insert(actionId, boardId, actorId, access.role(), BoardStaffActionStore.KIND_COMMENT, BoardStaffActionStore.ACTION_DELETE, comment.getAuthorId(), post.getId(),
                comment.getId(), code, cut(post.getTitle(), 200), excerpt(text, EXCERPT_MAX), cut(authorName, 40), now);
        if (comment.getAuthorId() != null) {
            memberNotices.removedByBoardStaff(comment.getAuthorId(), post.getId(), comment.getId(), excerpt(text, NOTICE_EXCERPT_MAX), reasonLabel(code), revoked);
        }
        log.info("스텝 댓글 삭제 postId={} commentId={} boardId={} by userId={} role={} actionId={}", post.getId(), comment.getId(), boardId, actorId, access.role(), actionId);
        return new Done(quota, actionId);
    }

    // ── 내역 ──────────────────────────────────────────────────────────────

    public Optional<ActionRow> find(long id) {
        return store.find(id);
    }

    /** 공개 내역 — 매니저·스텝(계정 정상)이 아니면 404 {@code BOARD_STAFF_NOT_FOUND}. 매니저 = 그 게시판 전체, 스텝 = 본인 것. {@code kind} ALL · POST · COMMENT, {@code page} 1부터 */
    public Page page(long actorId, long boardId, String kind, int page) {
        StaffAccess access = store.access(boardId, actorId);
        if (access.role() == null || !access.accountOk()) {
            throw BoardSanctionException.staffNotFound();
        }
        PageFilter filter = new PageFilter(boardId, access.manager() ? null : actorId, kind);
        long total = store.total(filter);
        int pages = (int) Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int p = Math.min(Math.max(1, page), pages);
        List<ActionRow> rows = store.page(filter, (p - 1) * PAGE_SIZE, PAGE_SIZE);
        return new Page(access.manager(), access.manager() && access.can(MemberPermission.BOARD_STAFF_ACTION_RESTORE), rows, total, p, PAGE_SIZE);
    }

    // ── 되돌리기 ──────────────────────────────────────────────────────────

    /** 매니저 되돌리기 — 자기 게시판 + {@code board.staff-action.restore}(아니면 403). 스텝은 되돌리지 못한다(본인 것도) */
    @Transactional
    public ActionRow restoreByManager(long actorId, long actionId, String reasonText) {
        ActionRow row = store.find(actionId).orElseThrow(BoardStaffActionException::actionNotFound);
        StaffAccess access = store.access(row.boardId(), actorId);
        if (!(access.manager() && access.can(MemberPermission.BOARD_STAFF_ACTION_RESTORE))) {
            throw BoardSanctionException.forbidden();
        }
        String reason = requireRestoreReason(reasonText);
        Map<String, Object> detail = restoreLocked(row, BoardStaffActionStore.BY_MANAGER, ModerationActor.boardStaff(actorId, access.role()), reason, now());
        log.info("스텝 처리 되돌리기(매니저) actionId={} kind={} by userId={} detail={}", actionId, row.apiKind(), actorId, detail);
        return store.find(actionId).orElseThrow();
    }

    /** 운영자 되돌리기 — 권한({@code keeper-action.restore})은 입구가 본다. 감사 {@code BOARD_STAFF_ACTION_RESTORE}(대상 POST) */
    @Transactional
    public ActionRow restoreByOperator(long actionId, String reasonText, long operatorId, String ip) {
        ActionRow row = store.find(actionId).orElseThrow(BoardStaffActionException::actionNotFound);
        String reason = requireRestoreReason(reasonText);
        Map<String, Object> detail = restoreLocked(row, BoardStaffActionStore.BY_OPERATOR, ModerationActor.operator(operatorId), reason, now());
        AuditEvent event = AuditEvent.byOperator(operatorId, ip, AuditActions.BOARD_STAFF_ACTION_RESTORE, AuditResult.SUCCESS).target(AUDIT_TARGET, Long.toString(row.postId()))
                .with("boardStaffActionId", Long.toString(actionId)).with("kind", row.apiKind()).with("boardId", Long.toString(row.boardId())).with("reason", reason);
        if (row.commentId() != null) {
            event = event.with("commentId", Long.toString(row.commentId()));
        }
        if (row.actorId() != null) {
            event = event.with("actorUserId", Long.toString(row.actorId()));
        }
        if (detail.get("restoredPoints") != null) {
            event = event.with("restoredPoints", detail.get("restoredPoints"));
        }
        audit.record(event);
        log.info("스텝 처리 되돌리기(운영자) actionId={} kind={} by operatorId={}", actionId, row.apiKind(), operatorId);
        return store.find(actionId).orElseThrow();
    }

    /** 글 행(→ 댓글 행) 잠금 → 기록 조건부 UPDATE → 대상 되살리기. 글 조치 덧붙임(댓글이면 빈 맵)을 돌려준다 */
    private Map<String, Object> restoreLocked(ActionRow row, String byKind, ModerationActor actor, String reason, Instant now) {
        PostEntity post = posts.findWithLockById(row.postId()).orElseThrow(PostStateInvalidException::new);
        CommentEntity comment = null;
        if (BoardStaffActionStore.KIND_COMMENT.equals(row.kind())) {
            comment = comments.findWithLockByPostIdAndId(row.postId(), row.commentId()).orElseThrow(BoardStaffActionException::commentStateInvalid);
        }
        if (!store.markRestored(row.id(), byKind, actor.id(), reason, now)) {
            throw BoardStaffActionException.alreadyRestored();
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("boardStaffActionId", Long.toString(row.id()));
        if (comment != null) {
            if (!comment.isDeleted() || comment.getPurgedAt() != null) {
                throw BoardStaffActionException.commentStateInvalid();
            }
            comment.restore(now);
            if (!comment.isHiddenAt(now) && !post.isGoneAt(now)) {
                counters.addCommentCount(post.getId(), 1);
            }
            comments.flush();
            return Map.of();
        }
        if (BoardStaffActionStore.ACTION_HIDE.equals(row.action())) {
            if (!PostEntity.STAFF_HIDDEN.equals(post.getHiddenReason())) {
                throw new PostStateInvalidException(); // 그새 다른 까닭(신고 숨김 등)으로 바뀌었다 — 그 숨김은 풀지 않는다
            }
            return actions.unhide(actor, post, reason, extra, now);
        }
        return actions.restore(actor, post, reason, extra, now);
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    /** 그 게시판 매니저·스텝 + 계정 정상·인증 + 그 권한 — 아니면 403 */
    private StaffAccess requireAble(Long boardId, long actorId, MemberPermission permission) {
        if (boardId == null) {
            throw BoardSanctionException.forbidden();
        }
        StaffAccess access = store.access(boardId, actorId);
        if (!access.can(permission)) {
            throw BoardSanctionException.forbidden();
        }
        return access;
    }

    private Quota consume(String kind, long boardId, long actorId, Instant now) {
        Limits limits = store.limits(boardId);
        int limit = BoardStaffActionStore.KIND_POST.equals(kind) ? limits.posts() : limits.comments();
        LocalDate today = ServiceTime.dateOf(now);
        Instant resetsAt = BoardSanctionService.nextResetAfter(now);
        Integer used = store.consume(kind, boardId, actorId, today, limit, now).orElse(null);
        if (used == null) {
            Usage usage = store.usage(boardId, actorId, today);
            throw BoardStaffActionException.dailyLimit(kind, limit, BoardStaffActionStore.KIND_POST.equals(kind) ? usage.posts() : usage.comments(), resetsAt);
        }
        return new Quota(limit, used, resetsAt);
    }

    /** 본인 것 · 같은 게시판 매니저·스텝의 것(비회원 것은 처리할 수 있다 — 정책 4) */
    private boolean protectedAuthor(long boardId, Long authorId, long actorId) {
        return authorId != null && (authorId == actorId || store.staffOf(boardId, authorId));
    }

    private boolean groupBoard(long boardId) {
        return catalog.snapshot().board(boardId).map(b -> BoardFields.KIND_GROUP.equals(b.kind())).orElse(false);
    }

    /** 공지 글(공통 「공지」 말머리) */
    private boolean notice(PostEntity post) {
        EffectiveBoard board = catalog.snapshot().board(post.getBoardId()).orElse(null);
        return board != null && board.prefix(post.getPrefixId()).map(BoardPrefixInfo::commonKey).filter("NOTICE"::equals).isPresent();
    }

    private static String requireReason(String raw) {
        String code = raw == null ? null : raw.strip().toUpperCase(Locale.ROOT);
        if (code == null || !REASONS.contains(code)) {
            throw ValidationException.of("reasonCode", new MessageRef("boardStaffAction.reasonCode.invalid"));
        }
        return code;
    }

    /** 되돌림 사유 1~200자 — {@code fields.reason} */
    private static String requireRestoreReason(String raw) {
        String reason = MemberSanctionService.clean(raw);
        if (reason == null || MemberSanctionService.length(reason) > REASON_MAX) {
            throw ValidationException.of("reason", new MessageRef("sanction.reason.length", 1, REASON_MAX));
        }
        return reason;
    }

    /** 사유 코드의 이름(한국어 — 알림·조치 기록의 사유. 운영자가 적은 사유 자리에 들어간다) */
    private String reasonLabel(String code) {
        return messages.get(new MessageRef("member.boardSanction.reason." + code), Locale.KOREAN);
    }

    static String plain(ContentFormat format, String content) {
        if (content == null) {
            return null;
        }
        if (format != ContentFormat.HTML) {
            return content;
        }
        return HtmlUtils.htmlUnescape(content.replaceAll("<[^>]*>", " ")).replace(' ', ' ');
    }

    /** 앞 {@code max}자 — 공백은 한 칸, 비면 null */
    static String excerpt(String text, int max) {
        if (text == null) {
            return null;
        }
        String flat = text.strip().replaceAll("\\s+", " ");
        return flat.isEmpty() ? null : cut(flat, max);
    }

    static String cut(String text, int max) {
        if (text == null || text.codePointCount(0, text.length()) <= max) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, max));
    }
}
