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
import com.community.comment.entity.CommentEntity;
import com.community.comment.repository.master.CommentRepository;
import com.community.comment.repository.master.CommentRepositoryQDSL;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.permission.entity.MemberPermission;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostCounterQueries;
import com.community.post.repository.master.PostRepository;
import com.community.post.service.PostQueryService;
import com.community.report.dto.request.ReportRequest;
import com.community.report.entity.ReportEntity;
import com.community.report.entity.ReportReasons;
import com.community.report.exception.AlreadyReportedException;
import com.community.report.exception.CannotReportOwnException;
import com.community.report.exception.ReportBlockedException;
import com.community.report.exception.ReportDailyLimitException;
import com.community.report.exception.ReportDisabledException;
import com.community.report.exception.ReportTargetNotFoundException;
import com.community.report.exception.ReportTooFastException;
import com.community.report.repository.master.ReportRepository;
import com.community.report.repository.master.ReportRepositoryQDSL;
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
 * 신고 접수 {@code POST /api/reports} — 회원만. 관리자 신고 정책(board_report_settings)을 적용한다. 운영자 처리 화면은 후속이다.
 *
 * <p><b>검사 순서</b>: 로그인(401) → 정지 {@code 403 ACCOUNT_RESTRICTED} · 역할 권한 {@code report.create} 없음 {@code 403 BOARD_RESTRICTED} → 신고 기능 꺼짐 {@code 403 REPORT_DISABLED}
 * → 칸({@code 400 VALIDATION}: targetType·targetId·reason·detail — 기타는 설명 필수, 300자) → 대상(없음·지움·숨김·꺼진 게시판 {@code 404 REPORT_TARGET_NOT_FOUND})
 * → 내 것 {@code 400 CANNOT_REPORT_OWN} → 반려 누적 차단 {@code 403 REPORT_BLOCKED} → 간격 {@code 429 REPORT_TOO_FAST} → 하루 한도(한국시간 0시) {@code 429 REPORT_DAILY_LIMIT}
 * → 같은 대상 {@code 409 ALREADY_REPORTED}. 간격·한도·중복 확인과 저장은 신고자 단위 잠금 아래 한 트랜잭션이다.
 *
 * <p><b>자동 숨김</b>(같은 트랜잭션, 대상 행 잠금): 불법촬영물 사유는 1건으로 즉시 숨김(ILLEGAL, 끌 수 없음) · 권리침해 사유는 임시조치 기간 동안 숨김(RIGHTS, 기간이 끝나면 다시 보인다 — 가정)
 * · 그 밖에는 자동 숨김 기준(서로 다른 회원 수, 신고 당시 최소 등급 이상인 신고만 센다)에 닿으면 숨김(REPORTED). 기준 숨김은 대상 종류(POST·COMMENT·BOTH)를 따르고,
 * 「운영자 작성물·공지 제외」가 켜져 있으면 고정 공지와 그 게시판 매니저·스텝이 쓴 글·댓글은 기준 숨김에서 뺀다. 숨긴 댓글은 글의 댓글 수에서 뺀다.
 *
 * <p><b>작성자 알림</b>: 자동 숨김으로 보이던 글·댓글이 새로 숨겨지면 작성 회원에게 회원 알림 {@code POST_HIDDEN}·{@code COMMENT_HIDDEN}(끌 수 없음 — 신고자·사유 코드는 싣지 않는다).
 *
 * <p><b>운영자 알림</b>: {@code REPORT_RECEIVED} — 정책 {@code notifyOperatorScope} 가 PER_CASE 면 대상의 첫 신고에만, PER_REPORT 면 신고마다.
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

    public ReportService(BoardCatalog catalog, BoardActorResolver actors, PostRepository posts, PostQueryService postQueries, CommentRepository comments,
                         CommentRepositoryQDSL commentQueries, PostCounterQueries counters, ReportRepository reports, ReportRepositoryQDSL reportQueries,
                         ProfileRepository profiles, BoardStaffRepository boardStaff, NotificationPublisher publisher, MemberNotificationPublisher memberNotices,
                         SnowflakeIdGenerator ids) {
        this.memberNotices = memberNotices;
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
        if (!ReportEntity.POST.equals(type) && !ReportEntity.COMMENT.equals(type)) {
            fields.put("targetType", new MessageRef("board.report.targetType"));
        }
        Long targetId = PostQueryService.parseId(body.targetId());
        if (targetId == null) {
            fields.put("targetId", new MessageRef("board.report.target"));
        }
        String reason = body.reason();
        if (reason == null || !ReportReasons.ALL.contains(reason)) {
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
        Target target = ReportEntity.POST.equals(type) ? postTarget(targetId, s, now) : commentTarget(targetId, s, now);
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
        String snapshotContent = truncate(target.comment() == null ? target.post().getContent() : target.comment().getContent());
        reports.persist(ReportEntity.create(ids.nextId(), target.type(), target.id(), target.post().getId(), target.board().id(), userId, target.authorId(),
                reason, detail.isEmpty() ? null : detail, snapshotTitle, snapshotContent, authorName, counted, now));
        reports.flush();

        autoHide(target, reason, policy, now);

        long reportsOnTarget = reportQueries.countForTarget(target.type(), target.id());
        if (!"PER_CASE".equals(policy.notifyOperatorScope()) || reportsOnTarget == 1) {
            publisher.publish(AdminNotificationEvent.REPORT_RECEIVED, NotificationContext.empty());
        }
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

    private static EffectiveBoard activeBoard(BoardCatalogSnapshot s, long boardId) {
        return s.board(boardId).filter(EffectiveBoard::active).orElseThrow(ReportTargetNotFoundException::new);
    }

    /** 자동 숨김 — 대상 행을 잠그고 판정한다 */
    private void autoHide(Target target, String reason, BoardReportValues policy, Instant now) {
        String hideReason = null;
        Instant until = null;
        if (ReportReasons.ILLEGAL_FILMING.equals(reason)) {
            hideReason = ReportReasons.HIDDEN_ILLEGAL;
        } else if (ReportReasons.RIGHTS.equals(reason)) {
            hideReason = ReportReasons.HIDDEN_RIGHTS;
            until = now.plus(Duration.ofDays(policy.rightsTempMeasureDays()));
        } else if (thresholdApplies(target, policy) && reportQueries.countedReporters(target.type(), target.id()) >= policy.autoHideThreshold()) {
            hideReason = ReportReasons.HIDDEN_REPORTED;
        }
        if (hideReason == null) {
            return;
        }
        if (target.comment() == null) {
            PostEntity locked = posts.findWithLockById(target.post().getId()).orElseThrow(ReportTargetNotFoundException::new);
            if (locked.hide(hideReason, until, now) && locked.getAuthorId() != null) {
                // 작성 회원에게 알림(끌 수 없음, 신고자·사유 코드는 싣지 않는다) — 실패해도 신고는 그대로
                memberNotices.contentHidden(locked.getAuthorId(), locked.getId(), null, locked.getTitle(), until);
            }
            return;
        }
        CommentEntity locked = comments.findWithLockByPostIdAndId(target.comment().getPostId(), target.comment().getId())
                .orElseThrow(ReportTargetNotFoundException::new);
        boolean wasVisible = !locked.isDeleted() && !locked.isHiddenAt(now);
        boolean hidden = locked.hide(hideReason, until, now);
        if (hidden && wasVisible) {
            counters.addCommentCount(locked.getPostId(), -1);
        }
        if (hidden && locked.getAuthorId() != null) {
            memberNotices.contentHidden(locked.getAuthorId(), locked.getPostId(), locked.getId(), locked.getContent(), until);
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
