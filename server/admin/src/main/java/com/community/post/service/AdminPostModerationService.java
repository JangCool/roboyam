package com.community.post.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.permission.exception.PermissionDeniedException;
import com.community.permission.service.AccessDeniedRecorder;
import com.community.point.service.PointService;
import com.community.poll.dto.PollData;
import com.community.poll.exception.PollNotFoundException;
import com.community.poll.repository.master.PollQueries;
import com.community.post.dto.request.AdminPostBulkRequest;
import com.community.post.dto.request.AdminPostHideRequest;
import com.community.post.dto.request.AdminPostMoveRequest;
import com.community.post.dto.request.AdminPostReasonRequest;
import com.community.post.dto.response.AdminPostBulkFailureView;
import com.community.post.dto.response.AdminPostBulkResultView;
import com.community.post.entity.PostEntity;
import com.community.post.entity.PostModerationEventEntity;
import com.community.post.exception.AdminPostNotFoundException;
import com.community.post.exception.PinLimitException;
import com.community.post.exception.PostPurgedException;
import com.community.post.exception.PostStateInvalidException;
import com.community.post.repository.master.AdminPostRepositoryQDSL;
import com.community.post.repository.master.PostRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 게시글 관리 조치 — 숨기기 · 숨김 해제 · 삭제 · 복구 · 이동 · 공지 지정 · 고정 · 고정 해제 · 일괄 · 투표 지우기({@link #deletePoll}). docs/features/post-admin-contract.md.
 *
 * <ul>
 *   <li><b>공통</b>: 사유 1~200자 필수(앞뒤 공백을 뗀 글자 수 — 400 {@code fields.reason}) → 글 행 잠금(없는 글 404 POST_NOT_FOUND) → 상태 확인(맞지 않으면 409 POST_STATE_INVALID)
 *       → 바꾸기 + 조치 기록({@code post_moderation_events}) + 감사({@code POST_*}, 대상 POST) 를 한 트랜잭션에서.</li>
 *   <li><b>상태</b>: 공개(지우지 않음 · 숨김 아님) · 숨김(지우지 않음 · 숨김 중 — 까닭 무엇이든) · 지움(지움 · 예약 삭제 시각 지남 · 임시저장).</li>
 *   <li><b>숨기기</b>: 공개 글만, 까닭 STAFF, 고정 해제. 「작성자에게 알림」이면 회원 알림 POST_HIDDEN(사유 포함). <b>숨김 해제</b>: 숨긴 글(신고 숨김도).
 *       <b>삭제</b>: 지우지 않은 글 — 고정 해제, 받은 포인트를 본인 삭제와 같은 규칙으로 회수, 회원 알림 POST_REMOVED(제목 · 사유, 끌 수 없음).
 *       <b>복구</b>({@code post.restore}): 지운 글 — 원문 파기 409 POST_PURGED, 임시저장 409 POST_STATE_INVALID. 예약 삭제도 거두고 숨김은 그대로, 회수한 포인트를 POST_RESTORE 로 되돌린다.</li>
 *   <li><b>이동</b>: 지우지 않은 글(숨긴 글도) → 켜진 게시판(아니면 400 {@code fields.boardId}) · 그 게시판의 켜진 말머리 또는 없음(아니면 400 {@code fields.prefixId},
 *       운영자 전용 말머리도 고를 수 있다) · 지금과 같은 자리 400 {@code fields.prefixId}. 고정 해제, 댓글의 게시판 칸도 함께 옮긴다.</li>
 *   <li><b>공지 지정</b>: 공개 글 → 그 게시판의 켜진 공통 「공지」 말머리(없으면 400 {@code fields.prefixId}) + 고정. 이미 공지이고 고정이면 409 POST_STATE_INVALID.
 *       <b>고정</b>: 공개 글 · 고정 아님. <b>고정 해제</b>: 고정 글. 고정 한도 = 게시판 정책 {@code pinnedMax}(그 게시판의 보이는 고정 글 수, 0 이면 고정 불가 → 409 PIN_LIMIT).</li>
 *   <li><b>일괄</b>({@link #bulk}): HIDE · UNHIDE · DELETE(DELETE 는 {@code post.bulk-delete} 도 — 없으면 403 PERMISSION_DENIED + 거부 감사), 글마다 따로 트랜잭션
 *       (한 글 실패가 다른 글을 막지 않는다). 글마다 조치 기록(덧붙임 {@code bulk true}), 감사는 {@code POST_BULK} 한 줄.</li>
 * </ul>
 */
@Service
public class AdminPostModerationService {

    private static final Logger log = LoggerFactory.getLogger(AdminPostModerationService.class);

    static final String TARGET = "POST";
    static final int BULK_MAX = 50;
    static final String HIDE = "HIDE";
    static final String UNHIDE = "UNHIDE";
    static final String DELETE = "DELETE";

    private final PostRepository posts;
    private final AdminPostRepositoryQDSL queries;
    private final BoardCatalog catalog;
    private final PostModerationLog moderation;
    private final MemberNotificationPublisher memberNotices;
    private final PointService points;
    private final AuditLogService audit;
    private final AccessDeniedRecorder denied;
    private final PollQueries polls;
    private final TransactionTemplate tx;

    public AdminPostModerationService(PostRepository posts, AdminPostRepositoryQDSL queries, BoardCatalog catalog, PostModerationLog moderation,
                                      MemberNotificationPublisher memberNotices, PointService points, AuditLogService audit, AccessDeniedRecorder denied, PollQueries polls,
                                      PlatformTransactionManager txManager) {
        this.posts = posts;
        this.queries = queries;
        this.catalog = catalog;
        this.moderation = moderation;
        this.memberNotices = memberNotices;
        this.points = points;
        this.audit = audit;
        this.denied = denied;
        this.polls = polls;
        this.tx = new TransactionTemplate(txManager);
    }

    // ── 한 글 조치 ─────────────────────────────────────────────────────────

    @Transactional
    public void hide(AdminActor actor, String idText, AdminPostHideRequest body) {
        String reason = reasonOf(body == null ? null : body.reason());
        boolean notify = body != null && Boolean.TRUE.equals(body.notifyAuthor());
        PostEntity post = lock(idText);
        Map<String, Object> detail = applyHide(actor, post, reason, notify, Instant.now(), false);
        record(actor, AuditActions.POST_HIDE, post.getId(), reason, detail);
    }

    @Transactional
    public void unhide(AdminActor actor, String idText, AdminPostReasonRequest body) {
        String reason = reasonOf(body == null ? null : body.reason());
        PostEntity post = lock(idText);
        Map<String, Object> detail = applyUnhide(actor, post, reason, Instant.now(), false);
        record(actor, AuditActions.POST_UNHIDE, post.getId(), reason, detail);
    }

    @Transactional
    public void delete(AdminActor actor, String idText, AdminPostReasonRequest body) {
        String reason = reasonOf(body == null ? null : body.reason());
        PostEntity post = lock(idText);
        Map<String, Object> detail = applyDelete(actor, post, reason, Instant.now(), false);
        record(actor, AuditActions.POST_DELETE, post.getId(), reason, detail);
    }

    @Transactional
    public void restore(AdminActor actor, String idText, AdminPostReasonRequest body) {
        String reason = reasonOf(body == null ? null : body.reason());
        PostEntity post = lock(idText);
        Instant now = Instant.now();
        if (!post.isGoneAt(now)) {
            throw new PostStateInvalidException();
        }
        if (post.isPurged()) {
            throw new PostPurgedException();
        }
        if (!PostEntity.PUBLISHED.equals(post.getStatus())) {
            throw new PostStateInvalidException(); // 임시저장은 지운 글이 아니다
        }
        post.restore(now);
        long restored = post.getAuthorId() == null ? 0 : points.restoreContent(post.getAuthorId(), post.getId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("restoredPoints", restored);
        moderation.operator(post.getId(), PostModerationEventEntity.RESTORED, actor.operatorId(), reason, detail, now);
        record(actor, AuditActions.POST_RESTORE, post.getId(), reason, detail);
        log.info("글 복구 postId={} by operatorId={} restoredPoints={}", post.getId(), actor.operatorId(), restored);
    }

    @Transactional
    public void move(AdminActor actor, String idText, AdminPostMoveRequest body) {
        String reason = reasonOf(body == null ? null : body.reason());
        String boardRaw = body == null ? null : trim(body.boardId());
        if (boardRaw == null) {
            throw ValidationException.of("boardId", new MessageRef("post.admin.boardId.required"));
        }
        Long boardId = AdminPostQueryService.parseId(boardRaw);
        EffectiveBoard board = boardId == null ? null : catalog.snapshot().board(boardId).filter(EffectiveBoard::active).orElse(null);
        if (board == null) {
            throw ValidationException.of("boardId", new MessageRef("post.admin.boardId.invalid"));
        }
        String prefixRaw = trim(body.prefixId());
        Long prefixId = null;
        if (prefixRaw != null) {
            prefixId = AdminPostQueryService.parseId(prefixRaw);
            Long wanted = prefixId;
            if (wanted == null || board.activePrefixes().stream().noneMatch(p -> p.id() == wanted)) {
                throw ValidationException.of("prefixId", new MessageRef("post.admin.prefixId.invalid"));
            }
        }
        PostEntity post = lock(idText);
        Instant now = Instant.now();
        if (post.isGoneAt(now)) {
            throw new PostStateInvalidException();
        }
        if (post.getBoardId() != null && post.getBoardId() == board.id() && Objects.equals(post.getPrefixId(), prefixId)) {
            throw ValidationException.of("prefixId", new MessageRef("post.admin.prefixId.same"));
        }
        Long fromBoard = post.getBoardId();
        Long fromPrefix = post.getPrefixId();
        boolean wasPinned = post.getPinnedAt() != null;
        post.moveTo(board.id(), prefixId, now);
        queries.moveComments(post.getId(), board.id());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("fromBoardId", idString(fromBoard));
        detail.put("fromPrefixId", idString(fromPrefix));
        detail.put("toBoardId", idString(board.id()));
        detail.put("toPrefixId", idString(prefixId));
        if (wasPinned) {
            detail.put("unpinned", true);
        }
        moderation.operator(post.getId(), PostModerationEventEntity.MOVED, actor.operatorId(), reason, detail, now);
        record(actor, AuditActions.POST_MOVE, post.getId(), reason, detail);
    }

    /**
     * 글의 투표 지우기 — 표가 있어도(글 상태 무관). 투표·선택지·표가 함께 사라지고 글쓴이는 새 투표를 다시 붙일 수 있다. 투표가 없으면 404 POLL_NOT_FOUND.
     * 조치 기록 {@code POLL_DELETED}·감사 {@code POST_POLL_DELETE} 에는 질문·참여 수만 남긴다(누가 골랐는지는 남기지 않는다). docs/features/post-poll-contract.md.
     */
    @Transactional
    public void deletePoll(AdminActor actor, String idText, AdminPostReasonRequest body) {
        String reason = reasonOf(body == null ? null : body.reason());
        PostEntity post = lock(idText);
        PollData poll = polls.findForUpdate(post.getId()).orElseThrow(PollNotFoundException::new);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("pollId", Long.toString(poll.id()));
        detail.put("question", poll.question());
        detail.put("voterCount", polls.voterCount(poll.id()));
        polls.delete(poll.id());
        moderation.operator(post.getId(), PostModerationEventEntity.POLL_DELETED, actor.operatorId(), reason, detail, Instant.now());
        record(actor, AuditActions.POST_POLL_DELETE, post.getId(), reason, detail);
    }

    @Transactional
    public void notice(AdminActor actor, String idText, AdminPostReasonRequest body) {
        String reason = reasonOf(body == null ? null : body.reason());
        PostEntity post = lock(idText);
        Instant now = Instant.now();
        requirePublic(post, now);
        EffectiveBoard board = catalog.snapshot().board(post.getBoardId()).orElse(null);
        BoardPrefixInfo noticePrefix = board == null ? null
                : board.activePrefixes().stream().filter(p -> AdminPostQueryService.NOTICE_KEY.equals(p.commonKey())).findFirst().orElse(null);
        if (noticePrefix == null) {
            throw ValidationException.of("prefixId", new MessageRef("post.admin.notice.noPrefix"));
        }
        boolean alreadyNotice = Objects.equals(post.getPrefixId(), noticePrefix.id());
        boolean pinned = post.getPinnedAt() != null;
        if (alreadyNotice && pinned) {
            throw new PostStateInvalidException();
        }
        if (!pinned) {
            requirePinRoom(board, post.getId(), now);
        }
        Long fromPrefix = post.getPrefixId();
        post.changePrefix(noticePrefix.id(), now);
        post.pin(now);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("fromPrefixId", idString(fromPrefix));
        detail.put("toPrefixId", idString(noticePrefix.id()));
        moderation.operator(post.getId(), PostModerationEventEntity.NOTICE, actor.operatorId(), reason, detail, now);
        record(actor, AuditActions.POST_NOTICE, post.getId(), reason, detail);
    }

    @Transactional
    public void pin(AdminActor actor, String idText, AdminPostReasonRequest body) {
        String reason = reasonOf(body == null ? null : body.reason());
        PostEntity post = lock(idText);
        Instant now = Instant.now();
        requirePublic(post, now);
        if (post.getPinnedAt() != null) {
            throw new PostStateInvalidException();
        }
        requirePinRoom(catalog.snapshot().board(post.getBoardId()).orElse(null), post.getId(), now);
        post.pin(now);
        moderation.operator(post.getId(), PostModerationEventEntity.PINNED, actor.operatorId(), reason, null, now);
        record(actor, AuditActions.POST_PIN, post.getId(), reason, Map.of());
    }

    @Transactional
    public void unpin(AdminActor actor, String idText, AdminPostReasonRequest body) {
        String reason = reasonOf(body == null ? null : body.reason());
        PostEntity post = lock(idText);
        Instant now = Instant.now();
        if (post.getPinnedAt() == null) {
            throw new PostStateInvalidException();
        }
        post.unpin(now);
        moderation.operator(post.getId(), PostModerationEventEntity.UNPINNED, actor.operatorId(), reason, null, now);
        record(actor, AuditActions.POST_UNPIN, post.getId(), reason, Map.of());
    }

    // ── 일괄 ──────────────────────────────────────────────────────────────

    /**
     * 일괄 숨김 · 해제 · 삭제 — 클래스 트랜잭션 없이 글마다 따로 커밋한다. 검사: {@code action}(400 {@code fields.action}) · {@code ids} 1~50개(같은 ID 는 한 번, 400 {@code fields.ids})
     * · 사유(400 {@code fields.reason}) 를 모아 한 번에 → DELETE 면 {@code post.bulk-delete}. 형식이 틀린 ID 는 실패 POST_NOT_FOUND.
     */
    public AdminPostBulkResultView bulk(AdminActor actor, AdminPostBulkRequest body) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        String action = body == null || body.action() == null ? null : body.action().strip().toUpperCase(Locale.ROOT);
        if (!HIDE.equals(action) && !UNHIDE.equals(action) && !DELETE.equals(action)) {
            invalid.put("action", new MessageRef("post.admin.bulk.action.invalid"));
        }
        Set<String> ids = new LinkedHashSet<>();
        if (body != null && body.ids() != null) {
            body.ids().stream().filter(Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty()).forEach(ids::add);
        }
        if (ids.isEmpty() || ids.size() > BULK_MAX) {
            invalid.put("ids", new MessageRef("post.admin.bulk.ids.size", String.valueOf(BULK_MAX)));
        }
        String reason = null;
        MessageRef reasonError = reasonError(body == null ? null : body.reason());
        if (reasonError != null) {
            invalid.put("reason", reasonError);
        } else {
            reason = body.reason().strip();
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        if (DELETE.equals(action) && !actor.can(Permission.POST_BULK_DELETE)) {
            denied.record(actor, "MISSING_PERMISSION", TARGET, "bulk", List.of(Permission.POST_BULK_DELETE.code()));
            throw new PermissionDeniedException();
        }
        boolean notify = HIDE.equals(action) && Boolean.TRUE.equals(body.notifyAuthor());

        List<String> done = new ArrayList<>();
        List<AdminPostBulkFailureView> failed = new ArrayList<>();
        String finalReason = reason;
        for (String raw : ids) {
            Long id = AdminPostQueryService.parseId(raw);
            if (id == null) {
                failed.add(new AdminPostBulkFailureView(raw, "POST_NOT_FOUND"));
                continue;
            }
            try {
                tx.executeWithoutResult(status -> {
                    PostEntity post = posts.findWithLockById(id).orElseThrow(AdminPostNotFoundException::new);
                    Instant now = Instant.now();
                    switch (action) {
                        case HIDE -> applyHide(actor, post, finalReason, notify, now, true);
                        case UNHIDE -> applyUnhide(actor, post, finalReason, now, true);
                        default -> applyDelete(actor, post, finalReason, now, true);
                    }
                });
                done.add(Long.toString(id));
            } catch (CommonException e) {
                failed.add(new AdminPostBulkFailureView(Long.toString(id), e.getErrorCode()));
            } catch (RuntimeException e) {
                log.error("일괄 조치 중 글 하나를 처리하지 못했다 postId={} action={} 원인={}", id, action, e.getClass().getSimpleName(), e);
                failed.add(new AdminPostBulkFailureView(Long.toString(id), "INTERNAL"));
            }
        }
        AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.POST_BULK, AuditResult.SUCCESS).target(TARGET, "bulk")
                .with("action", action).with("reason", finalReason).with("done", done)
                .with("failed", failed.stream().map(AdminPostBulkFailureView::id).toList());
        AuditEvent bulkEvent = HIDE.equals(action) ? event.with("notifyAuthor", notify) : event;
        tx.executeWithoutResult(status -> audit.record(bulkEvent));
        log.info("글 일괄 조치 action={} done={} failed={} by operatorId={}", action, done.size(), failed.size(), actor.operatorId());
        return new AdminPostBulkResultView(done, failed);
    }

    // ── 조치 몸통(트랜잭션 안, 글 행 잠금 뒤) ─────────────────────────────

    private Map<String, Object> applyHide(AdminActor actor, PostEntity post, String reason, boolean notify, Instant now, boolean bulk) {
        requirePublic(post, now);
        boolean wasPinned = post.getPinnedAt() != null;
        post.staffHide(now);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("notifyAuthor", notify);
        if (wasPinned) {
            detail.put("unpinned", true);
        }
        if (bulk) {
            detail.put("bulk", true);
        }
        moderation.operator(post.getId(), PostModerationEventEntity.HIDDEN, actor.operatorId(), reason, detail, now);
        if (notify && post.getAuthorId() != null) {
            memberNotices.postHiddenByStaff(post.getAuthorId(), post.getId(), post.getTitle(), reason);
        }
        return detail;
    }

    private Map<String, Object> applyUnhide(AdminActor actor, PostEntity post, String reason, Instant now, boolean bulk) {
        if (post.isGoneAt(now) || !post.isHiddenAt(now)) {
            throw new PostStateInvalidException();
        }
        String before = post.getHiddenReason();
        post.unhide(now);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("hiddenReason", before);
        if (bulk) {
            detail.put("bulk", true);
        }
        moderation.operator(post.getId(), PostModerationEventEntity.UNHIDDEN, actor.operatorId(), reason, detail, now);
        return detail;
    }

    private Map<String, Object> applyDelete(AdminActor actor, PostEntity post, String reason, Instant now, boolean bulk) {
        if (post.isGoneAt(now)) {
            throw new PostStateInvalidException();
        }
        boolean wasPinned = post.getPinnedAt() != null;
        post.staffDelete(now);
        // 운영자 삭제 — 본인 삭제와 같은 규칙으로 글 포인트·추천 보너스를 0P 까지 회수한다(이미 회수했으면 0)
        long revoked = post.getAuthorId() == null ? 0 : points.revokeContent(post.getAuthorId(), PointService.REF_POST, post.getId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("revokedPoints", revoked);
        if (wasPinned) {
            detail.put("unpinned", true);
        }
        if (bulk) {
            detail.put("bulk", true);
        }
        moderation.operator(post.getId(), PostModerationEventEntity.DELETED, actor.operatorId(), reason, detail, now);
        if (post.getAuthorId() != null) {
            memberNotices.postRemoved(post.getAuthorId(), post.getId(), post.getTitle(), reason, revoked);
        }
        return detail;
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    private PostEntity lock(String idText) {
        Long id = AdminPostQueryService.parseId(idText);
        return (id == null ? Optional.<PostEntity>empty() : posts.findWithLockById(id)).orElseThrow(AdminPostNotFoundException::new);
    }

    /** 공개 글(지우지 않음 · 숨김 아님)만 */
    private static void requirePublic(PostEntity post, Instant now) {
        if (post.isGoneAt(now) || post.isHiddenAt(now)) {
            throw new PostStateInvalidException();
        }
    }

    /** 고정 한도 — 그 게시판에 이 글 말고 보이는 고정 글이 한도만큼 있으면(한도 0 포함) 409 PIN_LIMIT */
    private void requirePinRoom(EffectiveBoard board, long postId, Instant now) {
        int max = board == null ? 0 : board.pinnedMax();
        if (max <= 0 || queries.pinnedCount(board.id(), postId, now) >= max) {
            throw new PinLimitException(Math.max(0, max));
        }
    }

    /** 사유 검사 → 앞뒤 공백을 뗀 사유. 틀리면 400 {@code fields.reason} */
    private static String reasonOf(String raw) {
        MessageRef error = reasonError(raw);
        if (error != null) {
            throw ValidationException.of("reason", error);
        }
        return raw.strip();
    }

    private static MessageRef reasonError(String raw) {
        String reason = raw == null ? "" : raw.strip();
        if (reason.isEmpty()) {
            return new MessageRef("post.admin.reason.required");
        }
        if (reason.codePointCount(0, reason.length()) > PostModerationEventEntity.REASON_MAX) {
            return new MessageRef("post.admin.reason.tooLong", String.valueOf(PostModerationEventEntity.REASON_MAX));
        }
        return null;
    }

    /** 감사 한 줄 — 대상 POST + 글 ID, 사유와 덧붙임(값이 null 인 칸은 뺀다) */
    private void record(AdminActor actor, String action, long postId, String reason, Map<String, Object> detail) {
        AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target(TARGET, Long.toString(postId))
                .with("reason", reason);
        for (Map.Entry<String, Object> e : detail.entrySet()) {
            if (e.getValue() != null) {
                event = event.with(e.getKey(), e.getValue());
            }
        }
        audit.record(event);
        log.info("글 조치 {} postId={} by operatorId={}", action, postId, actor.operatorId());
    }

    private static String idString(Long id) {
        return id == null ? null : Long.toString(id);
    }

    private static String trim(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        return s.isEmpty() ? null : s;
    }
}
