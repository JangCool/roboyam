package com.community.sanction.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardWriteRestriction;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.operator.service.OperatorMemberAccountService;
import com.community.operator.service.OperatorMemberAccountService.ActiveOperatorAccount;
import com.community.permission.entity.Permission;
import com.community.sanction.entity.AppealStatus;
import com.community.sanction.entity.BoardSanctionKind;
import com.community.sanction.entity.BoardSanctionReason;
import com.community.sanction.entity.SanctionStatus;
import com.community.sanction.exception.AppealAlreadyException;
import com.community.sanction.exception.AppealClosedException;
import com.community.sanction.exception.AppealExpiredException;
import com.community.sanction.exception.BoardSanctionException;
import com.community.sanction.exception.SanctionNotAppealableException;
import com.community.sanction.repository.master.BoardSanctionStore;
import com.community.sanction.repository.master.BoardSanctionStore.BoardSanctionRow;
import com.community.sanction.repository.master.BoardSanctionStore.Counts;
import com.community.sanction.repository.master.BoardSanctionStore.PostSource;
import com.community.sanction.repository.master.BoardSanctionStore.ReviewRow;
import com.community.sanction.repository.master.BoardSanctionStore.StaffGate;
import com.community.sanction.repository.master.BoardSanctionStore.TargetRow;
import com.community.sanction.repository.master.MemberSanctionStore;
import com.community.sanction.repository.master.MemberSanctionStore.AppealRow;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시판 제재(docs/features/board-sanction-contract.md 결정 1~11 · 규칙) — api(매니저·스텝의 부여·내역·해제 · 회원의 확인·이의제기)와 admin(해제·무효·반복 검토 닫기 ·
 * 이의제기 인용·기각)이 같이 쓴다. 사이트 제재({@link MemberSanctionService})와 단계·횟수를 따로 센다.
 *
 * <ul>
 *   <li><b>동시성</b>: 서버 여러 대 전제 — 메모리 상태 없이 <b>대상 회원 행 잠금</b>({@code users FOR UPDATE}, 사이트 제재와 같은 잠금) 아래에서 진행 중 제한·반복 검토를 다시 본다.
 *       진행 중 쓰기 제한은 (회원, 게시판)당 하나(부분 유일 색인), 하루 한도는 원자적 증가 한 문장, 열린 검토는 (회원, 게시판)당 하나({@code ON CONFLICT DO NOTHING}),
 *       해제·무효·닫기는 조건부 UPDATE. 잠금 차례: 검토 행 → 이의제기 행 → 회원 행 → 제재 행.</li>
 *   <li><b>부여</b>({@link #issue}): 계정 정상·이메일 인증 → 게시판 → 그 게시판 매니저·스텝 + 역할 권한 {@code board.sanction.issue}(403) → 입력(400) → 대상 잠금 →
 *       대상 보호(본인 · 같은 게시판 매니저·스텝 · 탈퇴 · 없는 회원 → 409 하나) → 끝이 지난 제한 정리 → 진행 중 제한이면 쓰기 제한 불가(409) → 하루 한도(409 + limit·used·resetsAt)
 *       → 제재 + 처리 기록 + 반복 검토 판정(7일 · VOIDED 제외 · 마지막 닫힌 검토 이후 · 3건 이상 · 열린 건 없음 → 열고 운영자 알림) + 회원 알림, 한 트랜잭션.</li>
 *   <li><b>해제</b>: 진행 중 쓰기 제한만 → LIFTED. 매니저({@code board.staff-action.restore} + 자기 게시판, 처리 기록) · 운영자(감사 {@code BOARD_SANCTION_LIFT}).</li>
 *   <li><b>무효</b>(운영자): → VOIDED(진행 중 제한은 그 순간 풀림). 대기 이의제기는 같은 트랜잭션에서 인용 + 답변 「운영자가 제재를 무효로 했어요」 + 답변 알림(결정 10).</li>
 *   <li><b>반복 검토 닫기</b>: KEPT · SITE_SANCTIONED(그 회원의 사이트 제재인지 확인) · VOIDED(그 검토가 센 제재인지 확인 — 같은 트랜잭션에서 무효). 자동으로 닫지 않는다(결정 9).</li>
 *   <li><b>이의제기</b>: 사이트 제재와 같은 규칙(1번 · 시작 후 30일 · 10~500자), 인용 → VOIDED. 메일은 보내지 않는다(결정 6).</li>
 * </ul>
 * <b>처리자 신원</b>은 회원 알림·오류 문구에 싣지 않는다(결정 7). 매니저·스텝의 처리 기록은 {@code board_staff_actions}(감사 기록 대신).
 */
@Service
@Transactional(readOnly = true)
public class BoardSanctionService {

    private static final Logger log = LoggerFactory.getLogger(BoardSanctionService.class);

    /** 덧붙임 · 해제 · 무효 사유 · 검토 메모 최대 글자(결정 11) */
    public static final int TEXT_MAX = 200;
    /** 사유 코드 OTHER 의 덧붙임 최소 글자 */
    public static final int OTHER_TEXT_MIN = 5;
    /** 반복 검토가 열리는 건수(결정 3) */
    public static final int REVIEW_THRESHOLD = 3;
    /** 매니저·스텝 내역 한 쪽 */
    public static final int STAFF_PAGE_SIZE = 20;
    /** 게시판 머리에 싣는 확인하지 않은 경고 최대 수 */
    public static final int PENDING_WARNING_MAX = 10;
    /** 감사 대상 종류 */
    public static final String TARGET = "MEMBER";
    /** 처리 기록 action */
    public static final String ACTION_ISSUE = "ISSUE";
    public static final String ACTION_LIFT = "LIFT";
    /** 무효 출처 */
    public static final String VOID_APPEAL = "APPEAL";
    public static final String VOID_OPERATOR = "OPERATOR";
    public static final String VOID_REVIEW = "REVIEW";
    /** 반복 검토 결과 */
    public static final String RESULT_KEPT = "KEPT";
    public static final String RESULT_SITE = "SITE_SANCTIONED";
    public static final String RESULT_VOIDED = "VOIDED";

    /** 하루 사용량 — 한도 · 쓴 수 · 다시 채워지는 시각(다음 한국 시간 0시) */
    public record Quota(int limit, int used, Instant resetsAt) {
        public int remaining() {
            return Math.max(0, limit - used);
        }
    }

    /** 부여 요청 — ID 는 부르는 쪽이 숫자로 바꿨다(모르면 null) */
    public record Issue(long actorId, long boardId, long targetId, String kind, String reasonCode, String reasonText, Long postId, Long commentId, String ip) {
    }

    /** 대화창 현황 — {@code quota} 운영자 회원 계정은 null(하루 한도 없음) */
    public record Preview(TargetRow target, Counts counts, BoardWriteRestriction active, BoardSanctionKind recommended, Quota quota) {
    }

    /** 부여 결과 — 새 줄 + 남은 한도(운영자 회원 계정은 null — 세지 않는다) */
    public record Issued(BoardSanctionRow row, Quota quota) {
    }

    /** 제재를 주는 사람 — 그 게시판 매니저·스텝({@code gate}) 또는 힘이 살아 있는 운영자 회원 계정({@code operator}, operator-member-account-contract 정책 7) */
    private record Issuer(StaffGate gate, ActiveOperatorAccount operator) {

        boolean isOperator() {
            return operator != null;
        }

        String role() {
            return operator != null ? BoardSanctionStore.ROLE_OPERATOR : gate.role();
        }

        Long operatorId() {
            return operator == null ? null : operator.operatorId();
        }
    }

    /** 매니저·스텝 도구(게시판 응답 {@code viewer.staffTools}) */
    public record StaffTools(Quota quota, boolean manager) {
    }

    /**
     * 매니저·스텝 내역 한 쪽(1부터) — 매니저는 전체, 스텝은 내가 준 것만. {@code canLift} 매니저 + {@code board.staff-action.restore}(운영자 회원 계정은 {@code sanction.manage}).
     * {@code operator} 운영자 회원 계정이 보는 내역 — 매니저는 운영자 회원 계정이 준 쓰기 제한을 풀지 못한다.
     */
    public record StaffPage(boolean manager, boolean canLift, boolean operator, List<BoardSanctionRow> rows, long total, int page, int size) {

        /** 이 줄을 지금 보는 사람이 풀 수 있나 */
        public boolean canLift(BoardSanctionRow row, Instant now) {
            return canLift && row.runningAt(now) && (operator || !row.byOperator());
        }
    }

    /** 반복 검토 닫기 요청 — 검사는 여기서 */
    public record ReviewClose(String result, String memo, String siteSanctionId, List<String> voidSanctionIds) {
    }

    private final BoardSanctionStore store;
    private final MemberSanctionStore memberStore;
    private final AuditLogService audit;
    private final MemberNotificationPublisher memberNotices;
    private final NotificationPublisher operatorNotices;
    private final Messages messages;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;
    private final OperatorMemberAccountService operatorAccounts;

    public BoardSanctionService(BoardSanctionStore store, MemberSanctionStore memberStore, AuditLogService audit, MemberNotificationPublisher memberNotices,
                                NotificationPublisher operatorNotices, Messages messages, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks,
                                OperatorMemberAccountService operatorAccounts) {
        this.operatorAccounts = operatorAccounts;
        this.store = store;
        this.memberStore = memberStore;
        this.audit = audit;
        this.memberNotices = memberNotices;
        this.operatorNotices = operatorNotices;
        this.messages = messages;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 지금(DB 시각 정밀도 — 마이크로초) */
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 다음 한국 시간 0시(하루 한도가 다시 채워지는 때) */
    public static Instant nextResetAfter(Instant now) {
        return ServiceTime.dateOf(now).plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant();
    }

    /** 이의제기 마감(시작 + 30일) — 무효면 null */
    public static Instant appealDeadline(BoardSanctionRow row) {
        return row.status() == SanctionStatus.VOIDED ? null : row.startsAt().plus(MemberSanctionService.APPEAL_WINDOW);
    }

    // ── 읽기 ──────────────────────────────────────────────────────────────

    public Optional<BoardSanctionRow> find(long id) {
        return store.find(id);
    }

    /** 그 회원의 게시판 제재(최근 것이 위, 모든 게시판, 무효 포함) */
    public List<BoardSanctionRow> ofUser(long userId, int limit) {
        return store.ofUser(userId, limit);
    }

    /** 그 회원의 게시판 제재 이의제기(게시판 제재 ID → 이의제기) */
    public Map<Long, AppealRow> appealsOf(long userId) {
        return store.appealsOfUser(userId);
    }

    /** 이 회원의 진행 중인 쓰기 제한 전부(게시판 ID → 제한) — 글·댓글 쓰기 판정, 요청당 색인 질의 하나 */
    public Map<Long, BoardWriteRestriction> writeRestrictions(long userId) {
        return store.runningOfUser(userId, now());
    }

    /** 게시판 응답의 {@code viewer.pendingWarnings} — 그 게시판에서 아직 확인하지 않은 경고(최근 것이 위, 최대 {@link #PENDING_WARNING_MAX}) */
    public List<BoardSanctionStore.PendingWarning> pendingWarnings(long userId, long boardId) {
        return store.pendingWarnings(userId, boardId, PENDING_WARNING_MAX);
    }

    /** 게시판 응답의 {@code viewer.staffTools} — 그 게시판에서 제재를 줄 수 있는 매니저·스텝에게만(아니면 빈 값) */
    public Optional<StaffTools> staffTools(long boardId, long userId) {
        StaffGate gate = store.gate(boardId, userId);
        if (!gate.issuer()) {
            return Optional.empty();
        }
        return Optional.of(new StaffTools(quota(boardId, userId, now()), gate.manager()));
    }

    /**
     * 대화창 현황 — 판정은 부여와 같다(계정 · 게시판 · 자리·권한 403, 대상 보호 409). 잠그지 않는다(부여가 다시 본다). 운영자 회원 계정은 한도가 없다(null).
     */
    public Preview preview(long actorId, long boardId, long targetId) {
        Issuer issuer = requireIssuer(boardId, actorId);
        TargetRow target = store.target(targetId).orElseThrow(BoardSanctionException::targetProtected);
        requireTargetable(target, boardId, actorId, issuer);
        Instant now = now();
        Counts counts = store.counts(targetId, boardId);
        BoardWriteRestriction running = store.running(targetId, boardId, now).orElse(null);
        return new Preview(target, counts, running, BoardSanctionKind.recommended(counts.total()), issuer.isOperator() ? null : quota(boardId, actorId, now));
    }

    /**
     * 매니저·스텝 제재 내역 — 매니저·스텝(계정 정상)이 아니면 404 {@code BOARD_STAFF_NOT_FOUND}. {@code scope} ALL · ACTIVE · ENDED, {@code page} 1부터.
     * 운영자 회원 계정은 {@code keeper-action.read} 가 있으면 그 게시판 전체(매니저처럼), 없으면 404(소모임 게시판도 404). 해제는 {@code sanction.manage}.
     */
    public StaffPage staffPage(long actorId, long boardId, String scope, int page) {
        Optional<ActiveOperatorAccount> operator = operatorAccounts.activeOperator(actorId);
        boolean manager;
        boolean canLift;
        Long issuedBy;
        if (operator.isPresent()) {
            String kind = store.boardKind(boardId).orElse(null);
            if (!operator.get().can(Permission.KEEPER_ACTION_READ) || kind == null || "GROUP".equals(kind)) {
                throw BoardSanctionException.staffNotFound();
            }
            manager = true;
            canLift = operator.get().can(Permission.SANCTION_MANAGE);
            issuedBy = null;
        } else {
            StaffGate gate = store.gate(boardId, actorId);
            if (gate.role() == null || !gate.accountOk()) {
                throw BoardSanctionException.staffNotFound();
            }
            manager = gate.manager();
            canLift = gate.manager() && gate.canRestore();
            issuedBy = gate.manager() ? null : actorId;
        }
        Instant now = now();
        long total = store.staffTotal(boardId, issuedBy, scope, now);
        int pages = (int) Math.max(1, (total + STAFF_PAGE_SIZE - 1) / STAFF_PAGE_SIZE);
        int p = Math.min(Math.max(1, page), pages);
        List<BoardSanctionRow> rows = store.staffPage(boardId, issuedBy, scope, now, (p - 1) * STAFF_PAGE_SIZE, STAFF_PAGE_SIZE);
        return new StaffPage(manager, canLift, operator.isPresent(), rows, total, p, STAFF_PAGE_SIZE);
    }

    // ── 부여 ──────────────────────────────────────────────────────────────

    @Transactional
    public Issued issue(Issue request) {
        Issuer issuer = requireIssuer(request.boardId(), request.actorId());

        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        BoardSanctionKind kind = BoardSanctionKind.of(request.kind()).orElse(null);
        if (kind == null) {
            invalid.put("kind", new MessageRef("boardSanction.kind.invalid"));
        }
        BoardSanctionReason reason = BoardSanctionReason.of(request.reasonCode()).orElse(null);
        if (reason == null) {
            invalid.put("reasonCode", new MessageRef("boardSanction.reasonCode.invalid"));
        }
        String text = MemberSanctionService.clean(request.reasonText());
        if (text != null && MemberSanctionService.length(text) > TEXT_MAX) {
            invalid.put("reasonText", new MessageRef("boardSanction.reasonText.length", TEXT_MAX));
        } else if (reason == BoardSanctionReason.OTHER && (text == null || MemberSanctionService.length(text) < OTHER_TEXT_MIN)) {
            invalid.put("reasonText", new MessageRef("boardSanction.reasonText.otherRequired", OTHER_TEXT_MIN));
        }
        String sourceTitle = source(request, invalid);
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }

        TargetRow target = store.lockTarget(request.targetId()).orElseThrow(BoardSanctionException::targetProtected);
        requireTargetable(target, request.boardId(), request.actorId(), issuer);
        Instant now = now();
        store.endDue(target.id(), request.boardId(), now);
        if (kind.restricts() && store.running(target.id(), request.boardId(), now).isPresent()) {
            throw BoardSanctionException.alreadyRestricted();
        }

        Quota quota = null; // 운영자 회원 계정은 하루 한도가 없다(세지도 않는다 — operator-member-account-contract 정책 7)
        if (!issuer.isOperator()) {
            int limit = store.dailyLimit(request.boardId());
            LocalDate today = ServiceTime.dateOf(now);
            Instant resetsAt = nextResetAfter(now);
            Integer used = store.consume(request.boardId(), request.actorId(), today, limit, now).orElse(null);
            if (used == null) {
                throw BoardSanctionException.dailyLimit(limit, store.used(request.boardId(), request.actorId(), today), resetsAt);
            }
            quota = new Quota(limit, used, resetsAt);
        }

        Instant endsAt = kind.restricts() ? now.plus(kind.length()) : null;
        long id = ids.nextId();
        long no = store.insert(id, request.boardId(), target.id(), kind, reason.name(), text, request.actorId(), issuer.role(), issuer.operatorId(), request.postId(),
                request.commentId(), sourceTitle, now, endsAt, now);
        store.insertAction(ids.nextId(), request.boardId(), request.actorId(), issuer.role(), issuer.operatorId(), ACTION_ISSUE, target.id(), id, request.postId(),
                request.commentId(), text, now);
        Long reviewId = openReviewIfRepeated(target.id(), request.boardId(), now); // 운영자 회원 계정의 제재도 합산한다(정책 7)

        BoardSanctionRow row = store.find(id).orElseThrow();
        memberNotices.boardSanctionIssued(target.id(), id, request.boardId(), row.boardName(), kind.name(), endsAt, reason.name(), text);
        if (issuer.isOperator()) {
            AuditEvent event = AuditEvent.byOperator(issuer.operatorId(), request.ip(), AuditActions.SITE_BOARD_SANCTION, AuditResult.SUCCESS)
                    .target(TARGET, Long.toString(target.id())).with("viaUserId", Long.toString(request.actorId())).with("boardId", Long.toString(request.boardId()));
            if (request.postId() != null) {
                event = event.with("postId", Long.toString(request.postId()));
            }
            if (request.commentId() != null) {
                event = event.with("commentId", Long.toString(request.commentId()));
            }
            audit.record(event.with("reasonCode", reason.name()).with("kind", kind.name()).with("boardSanctionId", Long.toString(id)).with("boardSanctionNo", no));
        }
        log.info("게시판 제재 부여 boardSanctionId={} no={} boardId={} userId={} kind={} by userId={} role={} operatorId={} reviewId={}", id, no, request.boardId(),
                target.id(), kind, request.actorId(), issuer.role(), issuer.operatorId(), reviewId);
        return new Issued(row, quota);
    }


    /**
     * 반복 검토 판정(결정 3) — 대상 회원 행을 잠근 뒤. 같은 (회원, 게시판)에 열린 건이 없고 최근 7일 · VOIDED 제외 · 마지막으로 닫힌 검토 이후 제재가 3건 이상이면 연다.
     *
     * @return 연 검토 ID(열지 않았으면 null)
     */
    private Long openReviewIfRepeated(long userId, long boardId, Instant now) {
        if (store.hasOpenReview(userId, boardId)) {
            return null;
        }
        Instant lastClosed = store.lastClosedAt(userId, boardId).orElse(null);
        Instant from = now.minus(BoardSanctionStore.REVIEW_WINDOW);
        if (lastClosed != null && lastClosed.isAfter(from)) {
            from = lastClosed;
        }
        int count = store.countValidAfter(userId, boardId, from);
        if (count < REVIEW_THRESHOLD) {
            return null;
        }
        long reviewId = ids.nextId();
        if (store.openReview(reviewId, boardId, userId, count, lastClosed, now).isEmpty()) {
            return null; // 그새 다른 트랜잭션이 열었다(유일 색인)
        }
        operatorNotices.publish(AdminNotificationEvent.BOARD_SANCTION_REPEAT_REVIEW, NotificationContext.empty().link("reviewId", Long.toString(reviewId))
                .dedupe(userId + ":" + boardId));
        return reviewId;
    }

    /** 계기 글·댓글 — 그 게시판 · 대상의 글(댓글이면 대상의 댓글). 글 제목 스냅숏(없으면 null) */
    private String source(Issue request, Map<String, MessageRef> invalid) {
        if (request.commentId() != null && request.postId() == null) {
            invalid.put("commentId", new MessageRef("boardSanction.source.invalid"));
            return null;
        }
        if (request.postId() == null) {
            return null;
        }
        PostSource post = store.post(request.postId()).orElse(null);
        if (post == null || post.boardId() != request.boardId() || (request.commentId() == null && !Long.valueOf(request.targetId()).equals(post.authorId()))) {
            invalid.put("postId", new MessageRef("boardSanction.source.invalid"));
            return null;
        }
        if (request.commentId() != null) {
            PostSource comment = store.comment(request.postId(), request.commentId()).orElse(null);
            if (comment == null || comment.boardId() != request.boardId() || !Long.valueOf(request.targetId()).equals(comment.authorId())) {
                invalid.put("commentId", new MessageRef("boardSanction.source.invalid"));
                return null;
            }
        }
        return post.title();
    }

    // ── 해제 ──────────────────────────────────────────────────────────────

    /** 매니저 해제 — 자기 게시판 + {@code board.staff-action.restore}(아니면 403), 진행 중 쓰기 제한만(409). 처리 기록 LIFT */
    @Transactional
    public BoardSanctionRow liftByManager(long actorId, long sanctionId, String reasonText, String ip) {
        long[] owner = store.ownerOf(sanctionId).orElseThrow(BoardSanctionException::notFound);
        Optional<ActiveOperatorAccount> operator = operatorAccounts.activeOperator(actorId);
        if (operator.isPresent()) {
            return liftByOperatorAccount(owner, sanctionId, operator.get(), actorId, reasonText, ip);
        }
        StaffGate gate = store.gate(owner[1], actorId);
        if (!(gate.manager() && gate.accountOk() && gate.canRestore()) || store.find(sanctionId).map(BoardSanctionRow::byOperator).orElse(false)) {
            throw BoardSanctionException.forbidden(); // 매니저는 운영자 회원 계정이 준 쓰기 제한을 풀지 못한다(operator-member-account-contract 정책 7)
        }
        String reason = requireReason(reasonText);
        Instant now = now();
        BoardSanctionRow row = liftLocked(owner, sanctionId, actorId, null, reason, now);
        store.insertAction(ids.nextId(), owner[1], actorId, gate.role(), null, ACTION_LIFT, owner[0], sanctionId, row.postId(), row.commentId(), reason, now);
        log.info("게시판 제재 해제(매니저) boardSanctionId={} by userId={}", sanctionId, actorId);
        return store.find(sanctionId).orElseThrow();
    }

    /** 운영자 회원 계정의 해제 — 관리자 권한 {@code sanction.manage}(운영자 해제 입구와 같다), 해제한 사람 = 운영자, 처리 기록 LIFT(OPERATOR), 감사 {@code SITE_BOARD_SANCTION_LIFT} */
    private BoardSanctionRow liftByOperatorAccount(long[] owner, long sanctionId, ActiveOperatorAccount operator, long userId, String reasonText, String ip) {
        String kind = store.boardKind(owner[1]).orElse(null);
        if (!operator.can(Permission.SANCTION_MANAGE) || "GROUP".equals(kind)) {
            throw BoardSanctionException.forbidden();
        }
        String reason = requireReason(reasonText);
        Instant now = now();
        BoardSanctionRow row = liftLocked(owner, sanctionId, null, operator.operatorId(), reason, now);
        store.insertAction(ids.nextId(), owner[1], userId, BoardSanctionStore.ROLE_OPERATOR, operator.operatorId(), ACTION_LIFT, owner[0], sanctionId, row.postId(),
                row.commentId(), reason, now);
        audit.record(AuditEvent.byOperator(operator.operatorId(), ip, AuditActions.SITE_BOARD_SANCTION_LIFT, AuditResult.SUCCESS).target(TARGET, Long.toString(owner[0]))
                .with("viaUserId", Long.toString(userId)).with("boardId", Long.toString(owner[1])).with("boardSanctionId", Long.toString(sanctionId))
                .with("boardSanctionNo", row.no()).with("kind", row.kind().name()).with("reason", reason));
        log.info("게시판 제재 해제(운영자 회원 계정) boardSanctionId={} by userId={} operatorId={}", sanctionId, userId, operator.operatorId());
        return store.find(sanctionId).orElseThrow();
    }

    /** 운영자 해제 — 권한({@code sanction.manage})은 부르는 쪽 입구가 본다. 감사 {@code BOARD_SANCTION_LIFT} */
    @Transactional
    public BoardSanctionRow liftByOperator(long sanctionId, String reasonText, long operatorId, String ip) {
        long[] owner = store.ownerOf(sanctionId).orElseThrow(BoardSanctionException::notFound);
        String reason = requireReason(reasonText);
        Instant now = now();
        BoardSanctionRow row = liftLocked(owner, sanctionId, null, operatorId, reason, now);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.BOARD_SANCTION_LIFT, AuditResult.SUCCESS).target(TARGET, Long.toString(owner[0]))
                .with("boardSanctionId", Long.toString(sanctionId)).with("boardSanctionNo", row.no()).with("boardId", Long.toString(owner[1]))
                .with("kind", row.kind().name()).with("reason", reason));
        log.info("게시판 제재 해제(운영자) boardSanctionId={} by operatorId={}", sanctionId, operatorId);
        return store.find(sanctionId).orElseThrow();
    }

    private BoardSanctionRow liftLocked(long[] owner, long sanctionId, Long byUser, Long byOperator, String reason, Instant now) {
        store.lockTarget(owner[0]);
        store.endDue(owner[0], owner[1], now);
        BoardSanctionRow row = store.find(sanctionId).orElseThrow(BoardSanctionException::notFound);
        if (!row.runningAt(now) || !store.lift(sanctionId, byUser, byOperator, reason, now)) {
            throw BoardSanctionException.notLiftable();
        }
        return row;
    }

    // ── 무효 ──────────────────────────────────────────────────────────────

    /** 운영자 무효 — 권한({@code sanction.manage})은 입구가 본다. 대기 이의제기는 같은 트랜잭션에서 인용(결정 10). 감사 {@code BOARD_SANCTION_VOID} */
    @Transactional
    public BoardSanctionRow voidByOperator(long sanctionId, String reasonText, long operatorId, String ip) {
        long[] owner = store.ownerOf(sanctionId).orElseThrow(BoardSanctionException::notFound);
        String reason = requireReason(reasonText);
        Long appealId = store.lockPendingAppeal(sanctionId).orElse(null); // 차례: 이의제기 행 → 회원 행
        store.lockTarget(owner[0]);
        Instant now = now();
        BoardSanctionRow row = store.find(sanctionId).orElseThrow(BoardSanctionException::notFound);
        boolean wasRunning = row.runningAt(now);
        if (!row.valid() || !store.voidSanction(sanctionId, VOID_OPERATOR, operatorId, reason, now)) {
            throw BoardSanctionException.alreadyVoided();
        }
        AuditEvent event = AuditEvent.byOperator(operatorId, ip, AuditActions.BOARD_SANCTION_VOID, AuditResult.SUCCESS).target(TARGET, Long.toString(owner[0]))
                .with("boardSanctionId", Long.toString(sanctionId)).with("boardSanctionNo", row.no()).with("boardId", Long.toString(owner[1]))
                .with("kind", row.kind().name()).with("reason", reason).with("released", wasRunning);
        if (appealId != null) {
            acceptForVoid(appealId, row, operatorId, now);
            event = event.with("appealAccepted", Long.toString(appealId));
        }
        audit.record(event);
        log.info("게시판 제재 무효(운영자) boardSanctionId={} appealId={} by operatorId={}", sanctionId, appealId, operatorId);
        return store.find(sanctionId).orElseThrow();
    }

    /** 무효로 한 제재의 대기 이의제기를 인용으로 닫는다(답변 「운영자가 제재를 무효로 했어요」) + 답변 알림 */
    private void acceptForVoid(long appealId, BoardSanctionRow row, long operatorId, Instant now) {
        String answer = messages.get(new MessageRef("boardSanction.void.appealAnswer"), Locale.KOREAN);
        if (memberStore.answerAppeal(appealId, AppealStatus.ACCEPTED, answer, operatorId, now)) {
            memberNotices.boardAppealAnswered(row.userId(), row.id(), appealId, row.kind().name(), AppealStatus.ACCEPTED.name(), answer);
        }
    }

    // ── 회원 쪽 ───────────────────────────────────────────────────────────

    /** 경고 확인(멱등 — 쓰기 제한이면 할 일 없음). 내 것이 아니면 404 */
    @Transactional
    public void acknowledge(long userId, long sanctionId) {
        if (!store.acknowledge(sanctionId, userId, now())) {
            throw BoardSanctionException.notFound();
        }
    }

    /**
     * 게시판 제재 이의제기 접수(결정 4 — 사이트 제재와 같은 규칙) → 운영자 알림 {@code SANCTION_APPEAL_FILED}.
     *
     * @throws BoardSanctionException         내 게시판 제재가 아니거나 없음(404 {@code BOARD_SANCTION_NOT_FOUND})
     * @throws SanctionNotAppealableException 무효가 된 제재(409)
     * @throws AppealAlreadyException         이미 접수(409 — 기각 뒤에도)
     * @throws AppealExpiredException         시작 후 30일이 지남(409)
     * @throws ValidationException            사유 10~500자({@code fields.reason})
     */
    @Transactional
    public AppealRow fileAppeal(long userId, long sanctionId, String reasonText) {
        BoardSanctionRow row = store.find(sanctionId).filter(r -> r.userId() == userId).orElseThrow(BoardSanctionException::notFound);
        if (row.status() == SanctionStatus.VOIDED) {
            throw new SanctionNotAppealableException();
        }
        if (row.appealId() != null) {
            throw new AppealAlreadyException();
        }
        Instant now = now();
        if (!now.isBefore(appealDeadline(row))) {
            throw new AppealExpiredException();
        }
        String reason = MemberSanctionService.clean(reasonText);
        int length = reason == null ? 0 : MemberSanctionService.length(reason);
        if (length < MemberSanctionService.APPEAL_MIN || length > MemberSanctionService.TEXT_MAX) {
            throw ValidationException.of("reason", new MessageRef("appeal.reason.length", MemberSanctionService.APPEAL_MIN, MemberSanctionService.TEXT_MAX));
        }
        long id = ids.nextId();
        long no = store.insertAppeal(id, sanctionId, userId, reason, now).orElseThrow(AppealAlreadyException::new);
        operatorNotices.publish(AdminNotificationEvent.SANCTION_APPEAL_FILED, NotificationContext.empty().link("appealId", Long.toString(id)));
        log.info("게시판 제재 이의제기 접수 appealId={} appealNo={} boardSanctionId={} userId={}", id, no, sanctionId, userId);
        return memberStore.findAppeal(id).orElseThrow();
    }

    /**
     * 게시판 제재 이의제기 답변 — {@link MemberSanctionService#answer} 가 이의제기 행을 잠그고 상태·답변 글을 본 뒤 부른다. 인용이면 VOIDED(진행 중 쓰기 제한은 즉시 풀림 ·
     * 반복 검토 수에서 빠짐). 감사 {@code APPEAL_ACCEPT}·{@code APPEAL_REJECT}, 회원 알림 {@code APPEAL_ANSWERED}(메일 없음 — 결정 6).
     */
    @Transactional
    public AppealRow answerAppeal(AppealRow appeal, boolean accept, String answer, long operatorId, String ip) {
        store.lockTarget(appeal.userId());
        Instant now = now();
        BoardSanctionRow row = store.find(appeal.boardSanctionId()).orElseThrow(com.community.sanction.exception.AppealNotFoundException::new);
        boolean released = false;
        if (accept && row.valid()) {
            released = row.runningAt(now);
            store.voidSanction(row.id(), VOID_APPEAL, operatorId, null, now);
        }
        if (!memberStore.answerAppeal(appeal.id(), accept ? AppealStatus.ACCEPTED : AppealStatus.REJECTED, answer, operatorId, now)) {
            throw new AppealClosedException();
        }
        AuditEvent event = AuditEvent.byOperator(operatorId, ip, accept ? AuditActions.APPEAL_ACCEPT : AuditActions.APPEAL_REJECT, AuditResult.SUCCESS)
                .target(TARGET, Long.toString(appeal.userId())).with("appealId", Long.toString(appeal.id())).with("appealNo", appeal.no())
                .with("boardSanctionId", Long.toString(row.id())).with("boardId", Long.toString(row.boardId())).with("kind", row.kind().name()).with("answer", answer);
        audit.record(released ? event.with("released", true) : event);
        String result = accept ? AppealStatus.ACCEPTED.name() : AppealStatus.REJECTED.name();
        memberNotices.boardAppealAnswered(appeal.userId(), row.id(), appeal.id(), row.kind().name(), result, answer);
        log.info("게시판 제재 이의제기 답변 appealId={} result={} boardSanctionId={} by operatorId={}", appeal.id(), result, row.id(), operatorId);
        return memberStore.findAppeal(appeal.id()).orElseThrow();
    }

    // ── 반복 검토 ─────────────────────────────────────────────────────────

    public Optional<ReviewRow> findReview(long id) {
        return store.findReview(id);
    }

    /** 이 검토가 센 제재(받은 차례, 무효 포함) — 열 때 7일 앞(또는 마지막으로 닫힌 검토) 뒤 ~ 닫은 때(열려 있으면 지금) */
    public List<BoardSanctionRow> reviewSanctions(ReviewRow review) {
        Instant until = review.closedAt() == null ? now() : review.closedAt();
        return store.ofUserBoardBetween(review.userId(), review.boardId(), review.windowStart(), until);
    }

    /** 열린 검토의 지금 7일 건수(VOIDED 제외 · 마지막으로 닫힌 검토 이후) — 닫힌 검토는 닫은 때의 수 */
    public int recentCount(ReviewRow review, Instant now) {
        if (!review.open()) {
            return review.closedCount() == null ? review.openedCount() : review.closedCount();
        }
        Instant from = now.minus(BoardSanctionStore.REVIEW_WINDOW);
        if (review.countedAfter() != null && review.countedAfter().isAfter(from)) {
            from = review.countedAfter();
        }
        return store.countValidAfter(review.userId(), review.boardId(), from);
    }

    /** 같은 기준으로 이 회원이 게시판 제재를 받은 게시판 수 — 닫힌 검토는 닫은 때의 수 */
    public int recentBoardCount(ReviewRow review, Instant now) {
        if (!review.open()) {
            return review.closedBoardCount() == null ? 1 : review.closedBoardCount();
        }
        return store.boardsValidAfter(review.userId(), now.minus(BoardSanctionStore.REVIEW_WINDOW));
    }

    /**
     * 반복 검토 닫기 — 권한({@code sanction.manage})은 입구가 본다. 감사 {@code BOARD_SANCTION_REVIEW_CLOSE}.
     *
     * @throws BoardSanctionException 없는 검토 404 · 이미 닫힘 409
     * @throws ValidationException    {@code fields.result|memo|siteSanctionId|voidSanctionIds}
     */
    @Transactional
    public ReviewRow closeReview(long reviewId, ReviewClose request, long operatorId, String ip) {
        long[] locked = store.lockReview(reviewId).orElseThrow(BoardSanctionException::reviewNotFound);
        if (locked[2] != 1) {
            throw BoardSanctionException.reviewClosed();
        }
        long userId = locked[0];
        long boardId = locked[1];
        ReviewRow review = store.findReview(reviewId).orElseThrow(BoardSanctionException::reviewNotFound);
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        String result = request == null || request.result() == null ? null : request.result().strip().toUpperCase(Locale.ROOT);
        if (!RESULT_KEPT.equals(result) && !RESULT_SITE.equals(result) && !RESULT_VOIDED.equals(result)) {
            invalid.put("result", new MessageRef("boardSanction.review.result.invalid"));
        }
        String memo = request == null ? null : MemberSanctionService.clean(request.memo());
        if (memo != null && MemberSanctionService.length(memo) > TEXT_MAX) {
            invalid.put("memo", new MessageRef("boardSanction.review.memo.length", TEXT_MAX));
        }
        Long siteSanctionId = null;
        if (RESULT_SITE.equals(result)) {
            siteSanctionId = parseId(request.siteSanctionId());
            Long site = siteSanctionId;
            if (site == null || memberStore.find(site).filter(s -> s.userId() == userId).isEmpty()) {
                invalid.put("siteSanctionId", new MessageRef("boardSanction.review.siteSanction.invalid"));
            }
        }
        List<BoardSanctionRow> voidRows = new ArrayList<>();
        if (RESULT_VOIDED.equals(result)) {
            Map<Long, BoardSanctionRow> counted = new LinkedHashMap<>();
            for (BoardSanctionRow row : reviewSanctions(review)) {
                if (row.valid()) {
                    counted.put(row.id(), row);
                }
            }
            Set<Long> wanted = new LinkedHashSet<>();
            boolean bad = request.voidSanctionIds() == null || request.voidSanctionIds().isEmpty();
            for (String raw : request.voidSanctionIds() == null ? List.<String>of() : request.voidSanctionIds()) {
                Long id = parseId(raw);
                if (id == null || !counted.containsKey(id)) {
                    bad = true;
                } else {
                    wanted.add(id);
                }
            }
            if (bad) {
                invalid.put("voidSanctionIds", new MessageRef("boardSanction.review.voidSanctionIds.invalid"));
            } else {
                wanted.stream().sorted().forEach(id -> voidRows.add(counted.get(id)));
            }
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }

        Instant now = now();
        List<Long> voided = new ArrayList<>();
        if (!voidRows.isEmpty()) {
            Map<Long, Long> appeals = new LinkedHashMap<>();
            for (BoardSanctionRow row : voidRows) { // 차례: 검토 행 → 이의제기 행(제재 ID 차례) → 회원 행
                store.lockPendingAppeal(row.id()).ifPresent(appealId -> appeals.put(row.id(), appealId));
            }
            store.lockTarget(userId);
            for (BoardSanctionRow row : voidRows) {
                if (store.voidSanction(row.id(), VOID_REVIEW, operatorId, memo, now)) {
                    voided.add(row.id());
                    Long appealId = appeals.get(row.id());
                    if (appealId != null) {
                        acceptForVoid(appealId, row, operatorId, now);
                    }
                }
            }
        }
        Instant from = now.minus(BoardSanctionStore.REVIEW_WINDOW);
        Instant countFrom = review.countedAfter() != null && review.countedAfter().isAfter(from) ? review.countedAfter() : from;
        int closedCount = store.countValidAfter(userId, boardId, countFrom);
        int closedBoards = store.boardsValidAfter(userId, from);
        if (!store.closeReview(reviewId, result, memo, siteSanctionId, voided, closedCount, closedBoards, operatorId, now)) {
            throw BoardSanctionException.reviewClosed();
        }
        AuditEvent event = AuditEvent.byOperator(operatorId, ip, AuditActions.BOARD_SANCTION_REVIEW_CLOSE, AuditResult.SUCCESS).target(TARGET, Long.toString(userId))
                .with("reviewId", Long.toString(reviewId)).with("reviewNo", review.no()).with("boardId", Long.toString(boardId)).with("result", result);
        if (memo != null) {
            event = event.with("memo", memo);
        }
        if (siteSanctionId != null) {
            event = event.with("siteSanctionId", Long.toString(siteSanctionId));
        }
        if (!voided.isEmpty()) {
            event = event.with("voidedBoardSanctionIds", voided.stream().map(String::valueOf).toList());
        }
        audit.record(event);
        log.info("반복 검토 닫기 reviewId={} result={} voided={} by operatorId={}", reviewId, result, voided.size(), operatorId);
        return store.findReview(reviewId).orElseThrow();
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    /**
     * 그 게시판에서 제재를 줄 수 있는 매니저·스텝(계정 정상 · 이메일 인증 · 역할 권한) — 아니면 403. 게시판이 없으면 403, 소모임 게시판(소모임 글)이면 409 대상 보호
     * (user-web 「소모임 글은 제재할 수 없는 회원이에요」).
     */
    private Issuer requireIssuer(long boardId, long actorId) {
        // 운영자 회원 계정(힘이 살아 있음 — 회원 상태 정상은 판정에 들어 있다) — 모든 공개 게시판, 관리자 권한 sanction.manage(operator-member-account-contract 정책 7)
        Optional<ActiveOperatorAccount> operator = operatorAccounts.activeOperator(actorId);
        if (operator.isPresent()) {
            String kind = store.boardKind(boardId).orElseThrow(BoardSanctionException::forbidden);
            if ("GROUP".equals(kind)) {
                throw BoardSanctionException.targetProtected();
            }
            if (!operator.get().can(Permission.SANCTION_MANAGE)) {
                throw BoardSanctionException.forbidden();
            }
            return new Issuer(null, operator.get());
        }
        StaffGate gate = store.gate(boardId, actorId);
        if (!gate.accountOk()) {
            throw BoardSanctionException.forbidden();
        }
        String kind = store.boardKind(boardId).orElseThrow(BoardSanctionException::forbidden);
        if ("GROUP".equals(kind)) {
            throw BoardSanctionException.targetProtected();
        }
        if (!gate.issuer()) {
            throw BoardSanctionException.forbidden();
        }
        return new Issuer(gate, null);
    }

    /**
     * 받을 수 없는 대상 — 409 하나: 본인 · 탈퇴 · 운영자 회원 계정(힘이 살아 있음 — 매니저·스텝에게도 다른 운영자 회원 계정에게도) · 같은 게시판 매니저·스텝(매니저·스텝이 줄 때만 —
     * 운영자 회원 계정은 매니저·스텝을 제재할 수 있다).
     */
    private void requireTargetable(TargetRow target, long boardId, long actorId, Issuer issuer) {
        if (target.gone() || target.id() == actorId || operatorAccounts.isActiveOperatorAccount(target.id())
                || (!issuer.isOperator() && store.staffOf(boardId, target.id()))) {
            throw BoardSanctionException.targetProtected();
        }
    }

    private Quota quota(long boardId, long userId, Instant now) {
        return new Quota(store.dailyLimit(boardId), store.used(boardId, userId, ServiceTime.dateOf(now)), nextResetAfter(now));
    }

    /** 해제·무효 사유 1~200자(결정 11) — {@code fields.reason} */
    private static String requireReason(String raw) {
        String reason = MemberSanctionService.clean(raw);
        if (reason == null || MemberSanctionService.length(reason) > TEXT_MAX) {
            throw ValidationException.of("reason", new MessageRef("sanction.reason.length", 1, TEXT_MAX));
        }
        return reason;
    }

    static Long parseId(String raw) {
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
