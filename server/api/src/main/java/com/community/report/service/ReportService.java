package com.community.report.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardReportValues;
import com.community.board.dto.EffectiveBoard;
import com.community.board.exception.BoardWriteBlockedException;
import com.community.board.repository.master.BoardStaffRepository;
import com.community.board.service.BoardAccessPolicy;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.board.service.ContentRules;
import com.community.board.service.RichText;
import com.community.comment.entity.CommentEntity;
import com.community.comment.repository.master.CommentRepository;
import com.community.comment.repository.master.CommentRepositoryQDSL;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupStatus;
import com.community.group.repository.master.GroupRepository;
import com.community.group.service.GroupPostGate;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.permission.entity.MemberPermission;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostCounterQueries;
import com.community.post.repository.master.PostRepository;
import com.community.post.entity.PostModerationEventEntity;
import com.community.post.service.PostModerationLog;
import com.community.post.service.PostQueryService;
import com.community.report.dto.request.ReportRequest;
import com.community.groupbuy.feedback.GroupBuyUgcReportService;
import com.community.report.entity.ReportCaseEntity;
import com.community.report.entity.ReportEntity;
import com.community.report.entity.ReportEventEntity;
import com.community.report.entity.ReportReasons;
import com.community.report.exception.AlreadyReportedException;
import com.community.report.exception.CannotReportOwnException;
import com.community.report.exception.ReportBlockedException;
import com.community.report.exception.ReportDailyLimitException;
import com.community.report.exception.ReportDisabledException;
import com.community.report.exception.ReportTargetNotFoundException;
import com.community.report.exception.ReportTooFastException;
import com.community.report.repository.master.ReportCaseRepository;
import com.community.report.repository.master.ReportRepository;
import com.community.report.repository.master.ReportRepositoryQDSL;
import com.community.report.service.ReportCaseLog;
import com.community.user.entity.ProfileEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 접수 {@code POST /api/reports} — 회원만. 관리자 신고 정책(board_report_settings)을 적용한다. 운영자 처리는 관리자 신고 관리(docs/features/report-admin-contract.md).
 *
 * <p><b>검사 순서</b>: 로그인(401) → 정지 {@code 403 ACCOUNT_RESTRICTED} · 역할 권한 {@code report.create} 없음 {@code 403 BOARD_RESTRICTED} → 신고 기능 꺼짐 {@code 403 REPORT_DISABLED}
 * → 칸({@code 400 VALIDATION}: targetType·targetId·reason·detail — 기타는 설명 필수, 300자) → 대상(없음·지움·숨김·꺼진 게시판 {@code 404 REPORT_TARGET_NOT_FOUND})
 * → 내 것 {@code 400 CANNOT_REPORT_OWN} → 반려 누적 차단 {@code 403 REPORT_BLOCKED} → 간격 {@code 429 REPORT_TOO_FAST} → 하루 한도(한국시간 0시) {@code 429 REPORT_DAILY_LIMIT}
 * → 같은 대상 {@code 409 ALREADY_REPORTED}. 간격·한도·중복 확인과 저장은 신고자 단위 잠금 아래 한 트랜잭션이다. 반려 누적은 관리자가 <b>허위 신고로 표시한 무시</b>(REJECTED)만 센다.
 *
 * <p><b>사건</b>(V82 {@code report_cases}): 대상 단위 잠금 아래 대상의 열린 사건(대기·검토 중)을 잠가 읽어 신고를 붙이고, 없으면 새 사건을 연다(닫힌 사건 뒤의 신고는 새 사건으로 0부터).
 * 사건의 신고 수·마지막 신고 시각을 고치고 기록 {@code REPORTED}(신고자 ID 는 남기지 않는다).
 *
 * <p><b>공구 질문·리뷰 신고</b>({@code GROUP_BUY_QUESTION}·{@code GROUP_BUY_REVIEW}, 2b-6): 회원 상태·신고 켜짐·사유(글·댓글 체계)·설명 검사는 같고, 저장은 공구 신고 표
 * ({@code GroupBuyUgcReportService} — 게시판·사건이 없다). 처리는 공구 Q&A·리뷰 화면의 숨김이다(자동 숨김 없음).
 *
 * <p><b>소모임 신고</b>({@code targetType: GROUP}, V95 — community docs/api-report.md 1절): 대상 = 열린(ACTIVE) 소모임(그 밖·없음 404 REPORT_TARGET_NOT_FOUND),
 * 사유 = PROMOTION · OFFLINE_RISK · STOCK_RECOMMENDATION · OTHER(글·댓글 사유를 보내면 {@code 400 fields.reason}), STOCK_RECOMMENDATION 은 투자 고지 게시판의 소모임만
 * ({@code 400 fields.reason}), 모임장 본인 {@code 400 CANNOT_REPORT_OWN}. 한도·중복·사건·운영자 알림은 글·댓글과 같고 <b>자동 숨김은 없다</b>.
 * 스냅샷: 제목 = 소모임 이름, 본문 = 소개(앞 2,000자), 작성자 = 모임장 닉네임. 사건의 글은 비고(null) 게시판은 그 소모임의 게시판, 대상 작성자 = 신고 당시 모임장.
 *
 * <p><b>자동 숨김</b>(같은 트랜잭션, 대상 행 잠금): 불법촬영물 사유는 1건으로 즉시 숨김(ILLEGAL, 끌 수 없음) · 권리침해 사유는 임시조치 기간 동안 숨김(RIGHTS, 기간이 끝나면 다시 보인다 — 가정)
 * · 그 밖에는 자동 숨김 기준(<b>열린 사건의</b> 서로 다른 회원 수, 신고 당시 최소 등급 이상인 신고만 센다)에 닿으면 숨김(REPORTED). 기준 숨김은 대상 종류(POST·COMMENT·BOTH)를 따르고,
 * 「운영자 작성물·공지 제외」가 켜져 있으면 고정 공지와 그 게시판 매니저·스텝이 쓴 글·댓글은 기준 숨김에서 뺀다. 숨긴 댓글은 글의 댓글 수에서 뺀다.
 *
 * <p><b>조치 기록</b>: 글이 새로 자동 숨김되면 {@code post_moderation_events} 에 {@code REPORT_HIDDEN}(SYSTEM — 숨김 까닭·기간) 한 줄(관리자 게시글 관리의 조치 기록),
 * 사건 기록에 {@code AUTO_HIDDEN}(SYSTEM — 숨김 까닭·기간).
 *
 * <p><b>작성자 알림</b>: 자동 숨김으로 보이던 글·댓글이 새로 숨겨지면 — 정책 {@code notifyAuthorOnAction} 이 켜졌을 때만 — 작성 회원에게 회원 알림 {@code POST_HIDDEN}·{@code COMMENT_HIDDEN}
 * (끌 수 없음 — 신고자·사유 코드는 싣지 않는다).
 *
 * <p><b>운영자 알림</b>: {@code REPORT_RECEIVED}(링크 {@code /reports?report={caseId}}) — 정책 {@code notifyOperatorScope} 가 PER_CASE 면 사건의 첫 신고에만, PER_REPORT 면 신고마다.
 */
@Service
public class ReportService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 스냅샷 본문 최대 글자 */
    private static final int SNAPSHOT_MAX = 2000;

    private final BoardCatalog catalog;
    private final BoardActorResolver actors;
    private final PostRepository posts;
    private final PostQueryService postQueries;
    private final CommentRepository comments;
    private final CommentRepositoryQDSL commentQueries;
    private final PostCounterQueries counters;
    private final ReportRepository reports;
    private final ReportRepositoryQDSL reportQueries;
    private final ProfileRepository profiles;
    private final BoardStaffRepository boardStaff;
    private final NotificationPublisher publisher;
    private final MemberNotificationPublisher memberNotices;
    private final SnowflakeIdGenerator ids;
    private final PostModerationLog moderation;
    private final ReportCaseRepository cases;
    private final ReportCaseLog caseLog;
    private final GroupPostGate groupGate;
    private final GroupRepository groups;
    private final GroupBuyUgcReportService groupBuyReports;

    public ReportService(BoardCatalog catalog, BoardActorResolver actors, PostRepository posts, PostQueryService postQueries, CommentRepository comments,
                         CommentRepositoryQDSL commentQueries, PostCounterQueries counters, ReportRepository reports, ReportRepositoryQDSL reportQueries,
                         ProfileRepository profiles, BoardStaffRepository boardStaff, NotificationPublisher publisher, MemberNotificationPublisher memberNotices,
                         SnowflakeIdGenerator ids, PostModerationLog moderation, ReportCaseRepository cases, ReportCaseLog caseLog, GroupPostGate groupGate,
                         GroupRepository groups, GroupBuyUgcReportService groupBuyReports) {
        this.groupBuyReports = groupBuyReports;
        this.groupGate = groupGate;
        this.groups = groups;
        this.cases = cases;
        this.caseLog = caseLog;
        this.memberNotices = memberNotices;
        this.moderation = moderation;
        this.catalog = catalog;
        this.actors = actors;
        this.posts = posts;
        this.postQueries = postQueries;
        this.comments = comments;
        this.commentQueries = commentQueries;
        this.counters = counters;
        this.reports = reports;
        this.reportQueries = reportQueries;
        this.profiles = profiles;
        this.boardStaff = boardStaff;
        this.publisher = publisher;
        this.ids = ids;
    }

    /** 신고 대상 — 글이면 comment 가 null */
    private record Target(PostEntity post, CommentEntity comment, EffectiveBoard board) {

        String type() {
            return comment == null ? ReportEntity.POST : ReportEntity.COMMENT;
        }

        long id() {
            return comment == null ? post.getId() : comment.getId();
        }

        Long authorId() {
            return comment == null ? post.getAuthorId() : comment.getAuthorId();
        }
    }

    @Transactional
    public void report(ReportRequest body, Long userId) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        BoardCatalogSnapshot s = catalog.snapshot();
        BoardActor actor = actors.resolve(userId, s);
        if (actor.guest()) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        if (actor.restricted()) {
            throw new BoardWriteBlockedException(BoardAccessPolicy.ACCOUNT_RESTRICTED);
        }
        if (!actor.can(MemberPermission.REPORT_CREATE.code())) {
            throw new BoardWriteBlockedException(BoardAccessPolicy.BOARD_RESTRICTED);
        }
        BoardReportValues policy = s.report();
        if (!Boolean.TRUE.equals(policy.reportEnabled())) {
            throw new ReportDisabledException();
        }

        Map<String, MessageRef> fields = new LinkedHashMap<>();
        String type = body.targetType();
        if (!ReportEntity.POST.equals(type) && !ReportEntity.COMMENT.equals(type) && !ReportEntity.GROUP.equals(type) && !GroupBuyUgcReportService.TYPES.contains(type)) {
            fields.put("targetType", new MessageRef("board.report.targetType"));
        }
        Long targetId = PostQueryService.parseId(body.targetId());
        if (targetId == null) {
            fields.put("targetId", new MessageRef("board.report.target"));
        }
        String reason = body.reason();
        if (reason == null || !ReportReasons.forTarget(type).contains(reason)) { // 소모임 사유와 글·댓글 사유는 섞지 않는다
            fields.put("reason", new MessageRef("board.report.reason"));
        }
        String detail = ContentRules.normalize(body.detail());
        if (ContentRules.length(detail) > ContentRules.REPORT_DETAIL_MAX) {
            fields.put("detail", new MessageRef("board.report.detail.length", ContentRules.REPORT_DETAIL_MAX));
        } else if (ReportReasons.OTHER.equals(reason) && detail.isEmpty()) {
            fields.put("detail", new MessageRef("board.report.detail.required"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }

        Instant now = Instant.now();
        if (GroupBuyUgcReportService.TYPES.contains(type)) { // 공구 질문·리뷰(2b-6) — 공구 신고 표로(게시판·사건이 없다), 처리는 운영자 숨김
            groupBuyReports.report(type, targetId, userId, reason, detail);
            return;
        }
        if (ReportEntity.GROUP.equals(type)) {
            reportGroup(targetId, reason, detail, userId, actor, s, policy, now);
            return;
        }
        Target target = ReportEntity.POST.equals(type) ? postTarget(targetId, s, now) : commentTarget(targetId, s, now);
        requireGroupReadable(target, s, userId); // 소모임 비공개 글·그 댓글은 멤버만 신고한다(읽을 수 없는 대상은 없는 대상과 같다)
        if (userId.equals(target.authorId())) {
            throw new CannotReportOwnException();
        }

        counters.lockWriter("report:" + userId);
        checkLimits(policy, userId, now);
        if (reports.existsByTargetTypeAndTargetIdAndReporterId(target.type(), target.id(), userId)) {
            throw new AlreadyReportedException();
        }

        Integer minLevel = s.gradeLevel(policy.autoHideMinGrade());
        boolean counted = minLevel == null || actor.gradeLevel() >= minLevel;
        String authorName = authorName(target);
        String snapshotTitle = target.comment() == null ? target.post().getTitle() : null;
        String snapshotContent = truncate(target.comment() == null ? target.post().plainContent()
                : RichText.plainOf(target.comment().getContentFormat(), target.comment().getContent())); // HTML 본문은 보이는 글자로 남긴다
        ReportCaseEntity reportCase = openCase(target.type(), target.id(), target.post().getId(), target.board().id(), target.authorId(), now);
        long reportId = ids.nextId();
        reports.persist(ReportEntity.create(reportId, reportCase.getId(), target.type(), target.id(), target.post().getId(), target.board().id(), userId,
                target.authorId(), reason, detail.isEmpty() ? null : detail, snapshotTitle, snapshotContent, authorName, counted, now));
        reportCase.reportAdded(target.post().getId(), target.board().id(), now);
        caseLog.reported(reportCase.getId(), reportId, reason, now);
        reports.flush();

        autoHide(target, reportCase.getId(), reason, policy, now);
        notifyOperators(policy, reportCase);
    }

    /**
     * 소모임 신고(V95) — 열린 소모임만, 투자 고지 게시판이 아니면 STOCK_RECOMMENDATION 400, 모임장 본인 400. 자동 숨김은 없다.
     * 잠금·한도·중복·사건은 글·댓글 신고와 같은 차례(신고자 잠금 → 대상 잠금 → 사건 행).
     */
    private void reportGroup(long groupId, String reason, String detail, long userId, BoardActor actor, BoardCatalogSnapshot s, BoardReportValues policy,
                             Instant now) {
        GroupEntity group = groups.findById(groupId).filter(g -> g.getStatus() == GroupStatus.ACTIVE).orElseThrow(ReportTargetNotFoundException::new);
        if (ReportReasons.STOCK_RECOMMENDATION.equals(reason) && !s.board(group.getBoardId()).map(EffectiveBoard::investmentNotice).orElse(false)) {
            throw ValidationException.of("reason", new MessageRef("board.report.reason.stockOnly"));
        }
        if (group.getLeaderId() == userId) {
            throw CannotReportOwnException.group();
        }

        counters.lockWriter("report:" + userId);
        checkLimits(policy, userId, now);
        if (reports.existsByTargetTypeAndTargetIdAndReporterId(ReportEntity.GROUP, groupId, userId)) {
            throw new AlreadyReportedException();
        }

        Integer minLevel = s.gradeLevel(policy.autoHideMinGrade());
        boolean counted = minLevel == null || actor.gradeLevel() >= minLevel; // 자동 숨김은 없지만 칸의 뜻(신고 당시 최소 등급 이상)은 같게 남긴다
        String leaderName = profiles.findById(group.getLeaderId()).map(ProfileEntity::getNickname).orElse(null);
        ReportCaseEntity reportCase = openCase(ReportEntity.GROUP, groupId, null, group.getBoardId(), group.getLeaderId(), now);
        long reportId = ids.nextId();
        reports.persist(ReportEntity.create(reportId, reportCase.getId(), ReportEntity.GROUP, groupId, null, group.getBoardId(), userId, group.getLeaderId(), reason,
                detail.isEmpty() ? null : detail, group.getName(), truncate(group.getDescription()), leaderName, counted, now));
        reportCase.reportAdded(null, group.getBoardId(), now);
        caseLog.reported(reportCase.getId(), reportId, reason, now);
        reports.flush();
        notifyOperators(policy, reportCase);
    }

    /** 운영자 알림 REPORT_RECEIVED — PER_CASE 면 사건의 첫 신고에만, PER_REPORT 면 신고마다 */
    private void notifyOperators(BoardReportValues policy, ReportCaseEntity reportCase) {
        if (!"PER_CASE".equals(policy.notifyOperatorScope()) || reportCase.getReportCount() == 1) {
            publisher.publish(AdminNotificationEvent.REPORT_RECEIVED, NotificationContext.empty().link("caseId", Long.toString(reportCase.getId())));
        }
    }

    /**
     * 대상의 열린 사건 — 대상 단위 잠금(관리자 「다시 열기」도 같은 잠금을 잡는다) 아래 열린 사건을 잠가 읽고, 없으면 새로 연다(신고 0건 — 부르는 쪽이 하나 더한다).
     * 운영자가 막 닫은 사건은 잠금이 풀린 뒤 조건을 다시 보므로 붙지 않는다.
     *
     * @param postId 대상 글(소모임 사건이면 null)
     */
    private ReportCaseEntity openCase(String type, long targetId, Long postId, long boardId, Long authorId, Instant now) {
        counters.lockWriter(ReportCaseEntity.lockKey(type, targetId));
        return cases.findWithLockByTargetTypeAndTargetIdAndStatusIn(type, targetId, ReportCaseEntity.OPEN).orElseGet(() -> {
            ReportCaseEntity opened = ReportCaseEntity.open(ids.nextId(), type, targetId, postId, boardId, authorId, now);
            cases.persist(opened);
            return opened;
        });
    }


    /** 반려 누적 차단 → 간격 → 하루 한도 */
    private void checkLimits(BoardReportValues policy, long userId, Instant now) {
        int rejectCount = policy.rejectionLimitCount();
        if (rejectCount > 0) {
            List<Instant> rejected = reportQueries.rejectedSince(userId, now.minus(Duration.ofDays(policy.rejectionLimitDays())));
            if (rejected.size() >= rejectCount) {
                Instant until = rejected.get(0).plus(Duration.ofDays(policy.rejectionBlockDays()));
                if (until.isAfter(now)) {
                    throw new ReportBlockedException(Math.max(1, Duration.between(now, until).toSeconds()));
                }
            }
        }
        int cooldown = policy.reportCooldownSeconds();
        if (cooldown > 0) {
            Instant last = reportQueries.lastAt(userId);
            if (last != null) {
                long elapsedMs = Duration.between(last, now).toMillis();
                if (elapsedMs < cooldown * 1000L) {
                    throw new ReportTooFastException(cooldown, Math.max(1, (cooldown * 1000L - elapsedMs + 999) / 1000));
                }
            }
        }
        int daily = policy.dailyReportLimit();
        if (daily > 0) {
            Instant todayStart = LocalDate.now(KST).atStartOfDay(KST).toInstant();
            if (reportQueries.countSince(userId, todayStart) >= daily) {
                throw new ReportDailyLimitException(daily);
            }
        }
    }

    /** 보이는 글 — 없음·지움·숨김·꺼진 게시판은 404 REPORT_TARGET_NOT_FOUND(존재 여부를 알리지 않게 한 코드로) */
    private Target postTarget(long id, BoardCatalogSnapshot s, Instant now) {
        PostEntity post = posts.findById(id).filter(p -> !p.isGoneAt(now) && !p.isHiddenAt(now)).orElseThrow(ReportTargetNotFoundException::new);
        return new Target(post, null, activeBoard(s, post.getBoardId()));
    }

    private Target commentTarget(long id, BoardCatalogSnapshot s, Instant now) {
        Long postId = commentQueries.postIdOf(id).orElseThrow(ReportTargetNotFoundException::new);
        CommentEntity comment = comments.findByPostIdAndId(postId, id).filter(c -> !c.isDeleted() && !c.isHiddenAt(now))
                .orElseThrow(ReportTargetNotFoundException::new);
        PostEntity post = posts.findById(postId).filter(p -> !p.isGoneAt(now) && !p.isHiddenAt(now)).orElseThrow(ReportTargetNotFoundException::new);
        return new Target(post, comment, activeBoard(s, post.getBoardId()));
    }

    /** 소모임 글이면 신고자가 그 글을 읽을 수 있어야 한다(나가서 가림·비활성·비공개 글의 비멤버) — 아니면 404 REPORT_TARGET_NOT_FOUND */
    private void requireGroupReadable(Target target, BoardCatalogSnapshot s, long userId) {
        if (!target.post().isGroupPost()) {
            return;
        }
        try {
            groupGate.resolve(s, target.post(), userId);
        } catch (CommonException e) {
            throw new ReportTargetNotFoundException();
        }
    }

    private static EffectiveBoard activeBoard(BoardCatalogSnapshot s, long boardId) {
        return s.board(boardId).filter(EffectiveBoard::active).orElseThrow(ReportTargetNotFoundException::new);
    }

    /** 자동 숨김 — 대상 행을 잠그고 판정한다. 기준 숨김은 열린 사건의 신고만 센다 */
    private void autoHide(Target target, long caseId, String reason, BoardReportValues policy, Instant now) {
        String hideReason = null;
        Instant until = null;
        if (ReportReasons.ILLEGAL_FILMING.equals(reason)) {
            hideReason = ReportReasons.HIDDEN_ILLEGAL;
        } else if (ReportReasons.RIGHTS.equals(reason)) {
            hideReason = ReportReasons.HIDDEN_RIGHTS;
            until = now.plus(Duration.ofDays(policy.rightsTempMeasureDays()));
        } else if (thresholdApplies(target, policy) && reportQueries.countedReporters(caseId) >= policy.autoHideThreshold()) {
            hideReason = ReportReasons.HIDDEN_REPORTED;
        }
        if (hideReason == null) {
            return;
        }
        boolean notifyAuthor = Boolean.TRUE.equals(policy.notifyAuthorOnAction());
        Map<String, Object> hidden = new LinkedHashMap<>();
        hidden.put("hiddenReason", hideReason);
        hidden.put("hiddenUntil", until == null ? null : until.toString());
        if (target.comment() == null) {
            PostEntity locked = posts.findWithLockById(target.post().getId()).orElseThrow(ReportTargetNotFoundException::new);
            if (!locked.hide(hideReason, until, now)) {
                return;
            }
            // 관리자 게시글 관리의 조치 기록 — 자동 숨김(SYSTEM, 숨김 까닭·기간)
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("hiddenReason", hideReason);
            detail.put("hiddenUntil", until == null ? null : until.toString());
            moderation.system(locked.getId(), PostModerationEventEntity.REPORT_HIDDEN, detail, now);
            caseLog.system(caseId, ReportEventEntity.AUTO_HIDDEN, hidden, now);
            if (notifyAuthor && locked.getAuthorId() != null) {
                // 작성 회원에게 알림(끌 수 없음, 신고자·사유 코드는 싣지 않는다) — 실패해도 신고는 그대로
                memberNotices.contentHidden(locked.getAuthorId(), locked.getId(), null, locked.getTitle(), until);
            }
            return;
        }
        CommentEntity locked = comments.findWithLockByPostIdAndId(target.comment().getPostId(), target.comment().getId())
                .orElseThrow(ReportTargetNotFoundException::new);
        boolean wasVisible = !locked.isDeleted() && !locked.isHiddenAt(now);
        boolean madeHidden = locked.hide(hideReason, until, now);
        if (madeHidden && wasVisible) {
            counters.addCommentCount(locked.getPostId(), -1);
        }
        if (madeHidden) {
            caseLog.system(caseId, ReportEventEntity.AUTO_HIDDEN, hidden, now);
        }
        if (madeHidden && notifyAuthor && locked.getAuthorId() != null) {
            memberNotices.contentHidden(locked.getAuthorId(), locked.getPostId(), locked.getId(), RichText.plainOf(locked.getContentFormat(), locked.getContent()),
                    until);
        }
    }

    /** 기준 숨김을 이 대상에 거는가 — 기준이 켜져 있고(0 = 끔) 대상 종류가 맞고 제외 대상이 아니다 */
    private boolean thresholdApplies(Target target, BoardReportValues policy) {
        if (policy.autoHideThreshold() <= 0) {
            return false;
        }
        String scope = policy.autoHideTarget();
        if (!"BOTH".equals(scope) && !target.type().equals(scope)) {
            return false;
        }
        if (Boolean.TRUE.equals(policy.autoHideExemptStaffPosts())) {
            if (target.comment() == null && target.post().getPinnedAt() != null) {
                return false; // 공지(고정 글)
            }
            Long author = target.authorId();
            if (author != null && boardStaff.existsByBoardIdAndUserId(target.board().id(), author)) {
                return false; // 그 게시판 매니저·스텝이 쓴 것
            }
        }
        return true;
    }

    private String authorName(Target target) {
        Long authorId = target.authorId();
        if (authorId == null) {
            return target.comment() == null ? target.post().getGuestName() : target.comment().getGuestName();
        }
        return profiles.findById(authorId).map(ProfileEntity::getNickname).orElse(null);
    }

    private static String truncate(String text) {
        if (text == null || text.codePointCount(0, text.length()) <= SNAPSHOT_MAX) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, SNAPSHOT_MAX));
    }
}
