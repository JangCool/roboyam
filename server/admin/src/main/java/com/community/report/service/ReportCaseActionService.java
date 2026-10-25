package com.community.report.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardReportValues;
import com.community.board.service.BoardCatalog;
import com.community.comment.entity.CommentEntity;
import com.community.comment.repository.master.CommentRepository;
import com.community.membernotification.service.MemberNotificationMessages;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.permission.dto.AdminActor;
import com.community.point.service.PointService;
import com.community.post.entity.PostEntity;
import com.community.post.entity.PostModerationEventEntity;
import com.community.post.repository.master.PostCounterQueries;
import com.community.post.repository.master.PostRepository;
import com.community.post.service.AdminPostModerationService;
import com.community.post.service.PostModerationLog;
import com.community.report.dto.request.ReportBulkDismissRequest;
import com.community.report.dto.request.ReportDismissRequest;
import com.community.report.dto.request.ReportResolveRequest;
import com.community.report.dto.response.ReportBulkFailureView;
import com.community.report.dto.response.ReportBulkResultView;
import com.community.report.entity.ReportCaseEntity;
import com.community.report.entity.ReportEntity;
import com.community.report.entity.ReportEventEntity;
import com.community.report.entity.ReportReasons;
import com.community.report.exception.ReportActionInvalidException;
import com.community.report.exception.ReportCaseClosedException;
import com.community.report.exception.ReportCaseNotFoundException;
import com.community.report.exception.ReportCaseOpenExistsException;
import com.community.report.repository.master.AdminReportRepositoryQDSL;
import com.community.report.repository.master.ReportCaseRepository;
import com.community.report.repository.master.ReportRepositoryQDSL;
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
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 신고 처리({@code report.manage}) — 조치 · 무시 · 일괄 무시 · 다시 열기. docs/features/report-admin-contract.md.
 * 사건 행을 <b>잠가</b> 읽어 두 운영자가 동시에 처리해도 한 번만 처리된다(나중 것은 {@code 409 REPORT_CASE_CLOSED}). 잠그는 차례는 사건 행 → 대상(글·댓글) 행.
 *
 * <ul>
 *   <li><b>조치</b>({@link #resolve}): 칸 검사 {@code action}(HIDE · DELETE · KEEP_HIDDEN, 400 {@code fields.action}) · {@code reasonCode}(신고 사유 코드, 400 {@code fields.reasonCode})
 *       · {@code note}(1~500자, 400 {@code fields.note}) → 사건(없음 404 REPORT_CASE_NOT_FOUND, 닫힘 409 REPORT_CASE_CLOSED) → 대상 상태(맞지 않으면 409 REPORT_ACTION_INVALID).
 *       HIDE = 공개 대상만 운영자 숨김(STAFF, 댓글이면 글의 댓글 수 −1) · KEEP_HIDDEN = 자동 숨김(REPORTED · ILLEGAL · RIGHTS)인 대상을 확정(임시조치 기한을 지운다)
 *       · DELETE = 지우지 않은 대상 삭제(글은 게시글 관리 삭제와 같은 규칙 — 고정 해제·포인트 회수, 댓글은 보이던 것이면 댓글 수 −1 · 포인트 회수).
 *       글 조치는 {@code post_moderation_events} 에도(덧붙임 사건 ID·사유 코드, 사유 = 글 앞 200자). 사건의 신고들은 ACCEPTED.</li>
 *   <li><b>무시</b>({@link #dismiss}): {@code reasonCode} 고르면 신고 사유 코드 · {@code note} 500자까지 · {@code falseReport}. 대상이 자동 숨김 중이면 <b>복구</b>(글은 조치 기록 UNHIDDEN,
 *       댓글은 댓글 수 +1), 운영자 숨김(STAFF)은 그대로. 신고들은 허위 표시면 REJECTED(신고 남용 차단이 센다), 아니면 DISMISSED.</li>
 *   <li><b>일괄 무시</b>({@link #bulkDismiss}): 사건 ID 1~50개(같은 ID 는 한 번, 400 {@code fields.ids}) · 글(400 {@code fields.note}). 사건마다 따로 트랜잭션 — 없음 REPORT_CASE_NOT_FOUND ·
 *       닫힘 REPORT_CASE_CLOSED 로 실패에 담는다. 감사는 {@code REPORT_BULK_DISMISS} 한 줄.</li>
 *   <li><b>다시 열기</b>({@link #reopen}): 무시한 사건만(조치한 사건 409 REPORT_CASE_CLOSED), 같은 대상에 새 열린 사건이 있으면 409 REPORT_CASE_OPEN_EXISTS. 대상 단위 잠금(공개 서버 신고 접수와 같은 키)
 *       아래에서. 복구했던 자동 숨김은 다시 숨기지 않는다. 허위 표시를 풀고 신고들은 PENDING.</li>
 *   <li><b>알림</b>(사건을 닫을 때): 게시판 신고 정책 {@code notifyReporterOnResult} 면 사건의 신고자 모두에게 {@code REPORT_RESULT}(조치됨 · 위반 아님),
 *       {@code notifyAuthorOnAction} 이면 회원 작성자에게 숨김 {@code POST_HIDDEN}·{@code COMMENT_HIDDEN} / 삭제 {@code POST_REMOVED}·{@code COMMENT_REMOVED}(사유 코드 이름 + 글).</li>
 *   <li><b>감사</b>: {@code REPORT_RESOLVE} · {@code REPORT_DISMISS} · {@code REPORT_BULK_DISMISS} · {@code REPORT_REOPEN}(대상 REPORT_CASE). 사건 기록 {@code report_events} 에도 한 줄.</li>
 * </ul>
 */
@Service
public class ReportCaseActionService {

    private static final Logger log = LoggerFactory.getLogger(ReportCaseActionService.class);

    static final String TARGET = "REPORT_CASE";
    static final int BULK_MAX = 50;
    static final List<String> ACTIONS = List.of(ReportCaseEntity.ACTION_HIDE, ReportCaseEntity.ACTION_DELETE, ReportCaseEntity.ACTION_KEEP_HIDDEN);

    private final ReportCaseRepository cases;
    private final AdminReportRepositoryQDSL caseQueries;
    private final ReportRepositoryQDSL reports;
    private final ReportCaseLog caseLog;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final PostCounterQueries counters;
    private final AdminPostModerationService postModeration;
    private final PostModerationLog moderation;
    private final PointService points;
    private final MemberNotificationPublisher memberNotices;
    private final BoardCatalog catalog;
    private final AuditLogService audit;
    private final TransactionTemplate tx;

    public ReportCaseActionService(ReportCaseRepository cases, AdminReportRepositoryQDSL caseQueries, ReportRepositoryQDSL reports, ReportCaseLog caseLog,
                                   PostRepository posts, CommentRepository comments, PostCounterQueries counters, AdminPostModerationService postModeration,
                                   PostModerationLog moderation, PointService points, MemberNotificationPublisher memberNotices, BoardCatalog catalog,
                                   AuditLogService audit, PlatformTransactionManager txManager) {
        this.cases = cases;
        this.caseQueries = caseQueries;
        this.reports = reports;
        this.caseLog = caseLog;
        this.posts = posts;
        this.comments = comments;
        this.counters = counters;
        this.postModeration = postModeration;
        this.moderation = moderation;
        this.points = points;
        this.memberNotices = memberNotices;
        this.catalog = catalog;
        this.audit = audit;
        this.tx = new TransactionTemplate(txManager);
    }

    // ── 조치 ──────────────────────────────────────────────────────────────

    @Transactional
    public void resolve(AdminActor actor, String idText, ReportResolveRequest body) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        String action = body == null || body.action() == null ? null : body.action().strip().toUpperCase(Locale.ROOT);
        if (action == null || !ACTIONS.contains(action)) {
            invalid.put("action", new MessageRef("report.admin.action.invalid"));
        }
        String reasonCode = reasonCodeOf(body == null ? null : body.reasonCode());
        if (reasonCode == null) {
            invalid.put("reasonCode", new MessageRef("report.admin.reasonCode.invalid"));
        }
        String note = clean(body == null ? null : body.note());
        MessageRef noteError = note == null ? new MessageRef("report.admin.note.required") : noteTooLong(note);
        if (noteError != null) {
            invalid.put("note", noteError);
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        ReportCaseEntity c = lock(idText);
        if (!c.isOpen()) {
            throw new ReportCaseClosedException();
        }
        Instant now = Instant.now();
        BoardReportValues policy = catalog.snapshot().report();
        boolean notifyAuthor = Boolean.TRUE.equals(policy.notifyAuthorOnAction());

        Map<String, Object> link = new LinkedHashMap<>();
        link.put("reportCaseId", Long.toString(c.getId()));
        link.put("reasonCode", reasonCode);
        long revoked = c.isComment() ? actOnComment(c, action, reasonCode, note, notifyAuthor, now)
                : actOnPost(actor, c, action, reasonCode, note, link, notifyAuthor, now);

        c.resolve(action, reasonCode, note, actor.operatorId(), now);
        String snapshot = snapshotOf(c);
        reports.markCase(c.getId(), ReportEntity.ACCEPTED, now, actor.operatorId());
        Map<String, Object> detail = closeDetail(action, reasonCode, note, false);
        if (ReportCaseEntity.ACTION_DELETE.equals(action)) {
            detail.put("revokedPoints", revoked);
        }
        caseLog.operator(c.getId(), ReportEventEntity.RESOLVED, actor.operatorId(), detail, now);
        notifyReporters(c, snapshot, MemberNotificationMessages.REPORT_ACTIONED, policy);

        Map<String, Object> auditDetail = new LinkedHashMap<>(detail);
        auditDetail.putAll(targetFacts(c));
        record(actor, AuditActions.REPORT_RESOLVE, Long.toString(c.getId()), auditDetail);
        log.info("신고 조치 caseId={} action={} by operatorId={}", c.getId(), action, actor.operatorId());
    }

    /** 글 조치 — 글 행 잠금 뒤 상태를 확인하고 게시글 관리의 조치 몸통을 부른다. 회수한 포인트를 돌려준다 */
    private long actOnPost(AdminActor actor, ReportCaseEntity c, String action, String reasonCode, String note, Map<String, Object> link, boolean notifyAuthor,
                           Instant now) {
        PostEntity post = posts.findWithLockById(c.getTargetId()).orElseThrow(ReportActionInvalidException::new);
        String reason = cut(note, PostModerationEventEntity.REASON_MAX);
        long revoked = 0;
        switch (action) {
            case ReportCaseEntity.ACTION_HIDE -> {
                if (post.isGoneAt(now) || post.isHiddenAt(now)) {
                    throw new ReportActionInvalidException();
                }
                postModeration.hideForReport(actor, post, reason, link, now);
                if (notifyAuthor && post.getAuthorId() != null) {
                    memberNotices.hiddenByStaff(post.getAuthorId(), post.getId(), null, post.getTitle(), note, reasonCode);
                }
            }
            case ReportCaseEntity.ACTION_KEEP_HIDDEN -> {
                if (post.isGoneAt(now) || !post.isHiddenAt(now) || !ReportReasons.isAutoHidden(post.getHiddenReason())) {
                    throw new ReportActionInvalidException();
                }
                Map<String, Object> detail = new LinkedHashMap<>(link);
                detail.put("keepHidden", true);
                detail.put("hiddenReason", post.getHiddenReason());
                if (post.getHiddenUntil() != null) {
                    detail.put("clearedHiddenUntil", post.getHiddenUntil().toString());
                }
                post.keepHidden(now);
                moderation.operator(post.getId(), PostModerationEventEntity.HIDDEN, actor.operatorId(), reason, detail, now);
                if (notifyAuthor && post.getAuthorId() != null) {
                    memberNotices.hiddenByStaff(post.getAuthorId(), post.getId(), null, post.getTitle(), note, reasonCode);
                }
            }
            default -> { // DELETE
                if (post.isGoneAt(now)) {
                    throw new ReportActionInvalidException();
                }
                revoked = AdminPostModerationService.revokedOf(postModeration.deleteForReport(actor, post, reason, link, now));
                if (notifyAuthor && post.getAuthorId() != null) {
                    memberNotices.removedByStaff(post.getAuthorId(), post.getId(), null, post.getTitle(), note, reasonCode, revoked);
                }
            }
        }
        return revoked;
    }

    /** 댓글 조치 — 댓글 행 잠금 뒤 상태 확인. 숨김·삭제로 보이던 댓글이 사라지면 글의 댓글 수를 줄인다. 회수한 포인트를 돌려준다 */
    private long actOnComment(ReportCaseEntity c, String action, String reasonCode, String note, boolean notifyAuthor, Instant now) {
        CommentEntity comment = comments.findWithLockByPostIdAndId(c.getPostId(), c.getTargetId()).orElseThrow(ReportActionInvalidException::new);
        String excerpt = AdminReportQueryService.excerpt(AdminReportQueryService.plain(comment.getContentFormat(), comment.getContent()));
        long revoked = 0;
        switch (action) {
            case ReportCaseEntity.ACTION_HIDE -> {
                if (comment.isDeleted() || comment.isHiddenAt(now) || !comment.hideByStaff(now)) {
                    throw new ReportActionInvalidException();
                }
                counters.addCommentCount(comment.getPostId(), -1);
                if (notifyAuthor && comment.getAuthorId() != null) {
                    memberNotices.hiddenByStaff(comment.getAuthorId(), comment.getPostId(), comment.getId(), excerpt, note, reasonCode);
                }
            }
            case ReportCaseEntity.ACTION_KEEP_HIDDEN -> {
                if (comment.isDeleted() || !comment.isHiddenAt(now) || !ReportReasons.isAutoHidden(comment.getHiddenReason())) {
                    throw new ReportActionInvalidException();
                }
                comment.keepHidden(now);
                if (notifyAuthor && comment.getAuthorId() != null) {
                    memberNotices.hiddenByStaff(comment.getAuthorId(), comment.getPostId(), comment.getId(), excerpt, note, reasonCode);
                }
            }
            default -> { // DELETE
                if (comment.isDeleted()) {
                    throw new ReportActionInvalidException();
                }
                boolean wasVisible = !comment.isHiddenAt(now);
                comment.delete(now);
                if (wasVisible) {
                    counters.addCommentCount(comment.getPostId(), -1);
                }
                // 운영자 삭제 — 본인 삭제와 같은 규칙으로 댓글 포인트·추천 보너스를 0P 까지 회수한다(이미 회수했으면 0)
                revoked = comment.getAuthorId() == null ? 0 : points.revokeContent(comment.getAuthorId(), PointService.REF_COMMENT, comment.getId());
                if (notifyAuthor && comment.getAuthorId() != null) {
                    memberNotices.removedByStaff(comment.getAuthorId(), comment.getPostId(), comment.getId(), excerpt, note, reasonCode, revoked);
                }
            }
        }
        comments.flush();
        return revoked;
    }

    // ── 무시 ──────────────────────────────────────────────────────────────

    @Transactional
    public void dismiss(AdminActor actor, String idText, ReportDismissRequest body) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        String rawCode = body == null ? null : AdminReportQueryService.trim(body.reasonCode());
        String reasonCode = reasonCodeOf(rawCode);
        if (rawCode != null && reasonCode == null) {
            invalid.put("reasonCode", new MessageRef("report.admin.reasonCode.invalid"));
        }
        String note = clean(body == null ? null : body.note());
        MessageRef noteError = note == null ? null : noteTooLong(note);
        if (noteError != null) {
            invalid.put("note", noteError);
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        boolean falseReport = body != null && Boolean.TRUE.equals(body.falseReport());
        ReportCaseEntity c = lock(idText);
        if (!c.isOpen()) {
            throw new ReportCaseClosedException();
        }
        Map<String, Object> detail = dismissOne(actor, c, reasonCode, note, falseReport, false, Instant.now());
        Map<String, Object> auditDetail = new LinkedHashMap<>(detail);
        auditDetail.putAll(targetFacts(c));
        record(actor, AuditActions.REPORT_DISMISS, Long.toString(c.getId()), auditDetail);
        log.info("신고 무시 caseId={} falseReport={} by operatorId={}", c.getId(), falseReport, actor.operatorId());
    }

    /**
     * 일괄 무시 — 클래스 트랜잭션 없이 사건마다 따로 커밋한다. 검사: {@code ids} 1~50개(400 {@code fields.ids}) · {@code note} 500자(400 {@code fields.note})를 모아 한 번에.
     * 형식이 틀린 ID 는 실패 REPORT_CASE_NOT_FOUND.
     */
    public ReportBulkResultView bulkDismiss(AdminActor actor, ReportBulkDismissRequest body) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        Set<String> ids = new LinkedHashSet<>();
        if (body != null && body.ids() != null) {
            body.ids().stream().filter(Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty()).forEach(ids::add);
        }
        if (ids.isEmpty() || ids.size() > BULK_MAX) {
            invalid.put("ids", new MessageRef("report.admin.ids.size", String.valueOf(BULK_MAX)));
        }
        String note = clean(body == null ? null : body.note());
        MessageRef noteError = note == null ? null : noteTooLong(note);
        if (noteError != null) {
            invalid.put("note", noteError);
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        boolean falseReport = Boolean.TRUE.equals(body.falseReport());

        List<String> done = new ArrayList<>();
        List<ReportBulkFailureView> failed = new ArrayList<>();
        for (String raw : ids) {
            Long id = AdminReportQueryService.parseId(raw);
            if (id == null) {
                failed.add(new ReportBulkFailureView(raw, ReportCaseNotFoundException.CODE));
                continue;
            }
            try {
                tx.executeWithoutResult(status -> {
                    ReportCaseEntity c = cases.findWithLockById(id).orElseThrow(ReportCaseNotFoundException::new);
                    if (!c.isOpen()) {
                        throw new ReportCaseClosedException();
                    }
                    dismissOne(actor, c, null, note, falseReport, true, Instant.now());
                });
                done.add(Long.toString(id));
            } catch (CommonException e) {
                failed.add(new ReportBulkFailureView(Long.toString(id), e.getErrorCode()));
            } catch (RuntimeException e) {
                log.error("일괄 무시 중 사건 하나를 처리하지 못했다 caseId={} 원인={}", id, e.getClass().getSimpleName(), e);
                failed.add(new ReportBulkFailureView(Long.toString(id), "INTERNAL"));
            }
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("falseReport", falseReport);
        if (note != null) {
            detail.put("note", note);
        }
        detail.put("done", done);
        detail.put("failed", failed.stream().map(ReportBulkFailureView::id).toList());
        tx.executeWithoutResult(status -> record(actor, AuditActions.REPORT_BULK_DISMISS, "bulk", detail));
        log.info("신고 일괄 무시 done={} failed={} falseReport={} by operatorId={}", done.size(), failed.size(), falseReport, actor.operatorId());
        return new ReportBulkResultView(done, failed);
    }

    /** 무시 몸통(사건 행 잠금 뒤, 열린 사건) — 자동 숨김 복구 · 닫기 · 신고 상태 · 기록 · 신고자 알림. 기록 덧붙임을 돌려준다 */
    private Map<String, Object> dismissOne(AdminActor actor, ReportCaseEntity c, String reasonCode, String note, boolean falseReport, boolean bulk, Instant now) {
        BoardReportValues policy = catalog.snapshot().report();
        boolean restored = restoreAutoHidden(actor, c, note, now);
        c.dismiss(reasonCode, note, falseReport, actor.operatorId(), now);
        String snapshot = snapshotOf(c);
        reports.markCase(c.getId(), falseReport ? ReportEntity.REJECTED : ReportEntity.DISMISSED, now, actor.operatorId());
        Map<String, Object> detail = closeDetail(ReportCaseEntity.ACTION_DISMISS, reasonCode, note, falseReport);
        if (restored) {
            detail.put("restored", true);
        }
        if (bulk) {
            detail.put("bulk", true);
        }
        caseLog.operator(c.getId(), ReportEventEntity.DISMISSED, actor.operatorId(), detail, now);
        notifyReporters(c, snapshot, MemberNotificationMessages.REPORT_NO_VIOLATION, policy);
        return detail;
    }

    /** 무시하면 신고 자동 숨김(REPORTED · ILLEGAL · RIGHTS)을 푼다 — 운영자 숨김(STAFF)·지운 대상은 그대로. 풀었으면 true */
    private boolean restoreAutoHidden(AdminActor actor, ReportCaseEntity c, String note, Instant now) {
        if (c.isComment()) {
            CommentEntity comment = comments.findWithLockByPostIdAndId(c.getPostId(), c.getTargetId()).orElse(null);
            if (comment == null || comment.isDeleted() || !comment.unhideReported(now)) {
                return false;
            }
            counters.addCommentCount(comment.getPostId(), 1);
            comments.flush();
            return true;
        }
        PostEntity post = posts.findWithLockById(c.getTargetId()).orElse(null);
        if (post == null || post.isGoneAt(now) || !post.isHiddenAt(now) || !ReportReasons.isAutoHidden(post.getHiddenReason())) {
            return false;
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("reportCaseId", Long.toString(c.getId()));
        extra.put("restoredByDismiss", true);
        postModeration.unhideForReport(actor, post, note == null ? null : cut(note, PostModerationEventEntity.REASON_MAX), extra, now);
        return true;
    }

    // ── 다시 열기 ──────────────────────────────────────────────────────────

    @Transactional
    public void reopen(AdminActor actor, String idText) {
        Long id = AdminReportQueryService.parseId(idText);
        AdminReportRepositoryQDSL.CaseTarget target = id == null ? null : caseQueries.targetOf(id);
        if (target == null) {
            throw new ReportCaseNotFoundException();
        }
        // 공개 서버 신고 접수와 같은 대상 단위 잠금 — 다시 여는 사이에 새 사건이 열리지 않게(차례: 이 잠금 → 사건 행)
        counters.lockWriter(ReportCaseEntity.lockKey(target.targetType(), target.targetId()));
        ReportCaseEntity c = cases.findWithLockById(id).orElseThrow(ReportCaseNotFoundException::new);
        if (!ReportCaseEntity.DISMISSED.equals(c.getStatus())) {
            throw new ReportCaseClosedException();
        }
        if (cases.existsByTargetTypeAndTargetIdAndStatusIn(c.getTargetType(), c.getTargetId(), ReportCaseEntity.OPEN)) {
            throw new ReportCaseOpenExistsException();
        }
        Instant now = Instant.now();
        boolean wasFalse = c.isFalseReport();
        c.reopen(now);
        cases.flush();
        reports.markCase(c.getId(), ReportEntity.PENDING, null, null);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("falseReportCleared", wasFalse);
        caseLog.operator(c.getId(), ReportEventEntity.REOPENED, actor.operatorId(), detail, now);
        Map<String, Object> auditDetail = new LinkedHashMap<>(detail);
        auditDetail.putAll(targetFacts(c));
        record(actor, AuditActions.REPORT_REOPEN, Long.toString(c.getId()), auditDetail);
        log.info("신고 다시 열기 caseId={} by operatorId={}", c.getId(), actor.operatorId());
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    private ReportCaseEntity lock(String idText) {
        Long id = AdminReportQueryService.parseId(idText);
        return (id == null ? java.util.Optional.<ReportCaseEntity>empty() : cases.findWithLockById(id)).orElseThrow(ReportCaseNotFoundException::new);
    }

    /** 신고자 결과 알림 — 게시판 신고 정책 {@code notifyReporterOnResult} 일 때 사건의 신고자 모두에게(같은 회원은 한 번) */
    private void notifyReporters(ReportCaseEntity c, String snapshot, String result, BoardReportValues policy) {
        if (!Boolean.TRUE.equals(policy.notifyReporterOnResult())) {
            return;
        }
        Long commentId = c.isComment() ? c.getTargetId() : null;
        for (Long reporterId : reports.reporterIds(c.getId())) {
            memberNotices.reportResult(reporterId, c.getPostId(), commentId, c.getTargetType(), snapshot, result);
        }
    }

    /** 신고 당시 대상 — 첫 신고의 제목(댓글이면 본문 앞부분). 신고 기록이 지워졌으면 null */
    private String snapshotOf(ReportCaseEntity c) {
        ReportEntity first = reports.firstOfCase(c.getId());
        if (first == null) {
            return null;
        }
        return first.getSnapshotTitle() != null && !first.getSnapshotTitle().isBlank() ? first.getSnapshotTitle() : first.getSnapshotContent();
    }

    private static Map<String, Object> closeDetail(String action, String reasonCode, String note, boolean falseReport) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("action", action);
        if (reasonCode != null) {
            detail.put("reasonCode", reasonCode);
        }
        if (note != null) {
            detail.put("note", note);
        }
        detail.put("falseReport", falseReport);
        return detail;
    }

    private static Map<String, Object> targetFacts(ReportCaseEntity c) {
        Map<String, Object> facts = new LinkedHashMap<>();
        if (c.getCaseNo() != null) {
            facts.put("caseNo", c.getCaseNo());
        }
        facts.put("targetType", c.getTargetType());
        facts.put("targetId", Long.toString(c.getTargetId()));
        facts.put("postId", Long.toString(c.getPostId()));
        facts.put("reportCount", c.getReportCount());
        return facts;
    }

    /** 감사 한 줄 — 대상 REPORT_CASE, 값이 null 인 칸은 뺀다 */
    private void record(AdminActor actor, String action, String targetId, Map<String, Object> detail) {
        AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target(TARGET, targetId);
        for (Map.Entry<String, Object> e : detail.entrySet()) {
            if (e.getValue() != null) {
                event = event.with(e.getKey(), e.getValue());
            }
        }
        audit.record(event);
    }

    private static String reasonCodeOf(String raw) {
        String code = AdminReportQueryService.trim(raw);
        if (code == null) {
            return null;
        }
        code = code.toUpperCase(Locale.ROOT);
        return ReportReasons.ALL.contains(code) ? code : null;
    }

    /** 앞뒤 공백·NUL 을 뗀 글 — 비었으면 null */
    private static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.replace("\u0000", "").strip();
        return s.isEmpty() ? null : s;
    }

    private static MessageRef noteTooLong(String note) {
        return note.codePointCount(0, note.length()) > ReportCaseEntity.NOTE_MAX
                ? new MessageRef("report.admin.note.tooLong", String.valueOf(ReportCaseEntity.NOTE_MAX)) : null;
    }

    private static String cut(String text, int max) {
        if (text == null || text.codePointCount(0, text.length()) <= max) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, max));
    }
}
