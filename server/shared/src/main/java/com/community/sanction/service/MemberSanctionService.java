package com.community.sanction.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.permission.entity.Permission;
import com.community.sanction.dto.SanctionIssue;
import com.community.sanction.dto.SanctionOperator;
import com.community.sanction.entity.AppealStatus;
import com.community.sanction.entity.SanctionKind;
import com.community.sanction.entity.SanctionStatus;
import com.community.sanction.exception.AppealAlreadyException;
import com.community.sanction.exception.AppealClosedException;
import com.community.sanction.exception.AppealExpiredException;
import com.community.sanction.exception.AppealNotFoundException;
import com.community.sanction.exception.SanctionAlreadyBannedException;
import com.community.sanction.exception.SanctionAlreadySuspendedException;
import com.community.sanction.exception.SanctionNotAppealableException;
import com.community.sanction.exception.SanctionNotFoundException;
import com.community.sanction.exception.SanctionNotLiftableException;
import com.community.sanction.exception.SanctionStepTooLowException;
import com.community.sanction.exception.SanctionTargetNotFoundException;
import com.community.sanction.repository.master.MemberSanctionStore;
import com.community.sanction.repository.master.MemberSanctionStore.AppealRow;
import com.community.sanction.repository.master.MemberSanctionStore.MemberRow;
import com.community.sanction.repository.master.MemberSanctionStore.SanctionRow;
import com.community.sanction.repository.master.MemberSanctionStore.StaffRow;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이트 제재·이의제기(docs/features/member-sanction-contract.md 결정 1~4 · 정책 1~8) — api(내 제재·이의제기 접수)와 admin(부여·해제·인용·기각·신고 처리)이 같이 쓴다.
 *
 * <ul>
 *   <li><b>동시성</b>: 서버 여러 대 전제 — 메모리 상태 없이 <b>회원 행 잠금</b>({@code users FOR UPDATE}) 아래에서 단계를 다시 계산한다. 동시 부여는 줄을 서서 두 번째가 새 단계를 본다.
 *       진행 중 정지는 회원당 하나(부분 유일 색인), 이의제기는 제재당 하나(유일 색인 · {@code ON CONFLICT}), 답변은 이의제기 행 잠금 + 조건부 UPDATE 로 한 번만.
 *       잠금 차례: 이의제기 행 → 회원 행 → 제재 행(신고 처리는 사건 행 → 대상 행 → 회원 행). 만료 크론(R__batch_39)도 회원 행을 먼저 잠근다.</li>
 *   <li><b>지연 만료</b>({@link #settle}): 쓰기 전·회원 쪽 읽기 전에 끝이 지난 정지를 먼저 끝내고 상태를 바로잡는다(크론이 늦어도 정확). 관리자 읽기는 쓰지 않고
 *       {@link SanctionRow#effectiveStatus} 로 같은 결과를 보인다.</li>
 *   <li><b>부여</b>({@link #issue}): 검사 → 회원 잠금(없음·탈퇴·탈퇴 진행 중 404) → 지연 만료 → 단계 계산({@link SanctionLadder}) → 영구 정지 409 · 정지 중 경고·기간 정지 409 ·
 *       낮은 단계 409 · 건너뛰기 표시 없음 400 · {@code sanction.ban}/{@code sanction.manage} 없음 403 · 영구 정지 닉네임 확인 400 → (영구 정지면 진행 중 정지 ENDED) → 줄 →
 *       회원 상태 SUSPENDED·BANNED + 게시판 매니저·스텝 지정 끝 → 감사 {@code SANCTION_ISSUE} → 회원 알림 {@code SANCTION_ISSUED}(메일은 커밋 뒤, 인증된 이메일).</li>
 *   <li><b>해제</b>({@link #lift}): 진행 중 정지(기간 정지 {@code sanction.manage} · 영구 정지 {@code sanction.ban})만 — LIFTED + 다른 진행 중 정지가 없으면 회원 ACTIVE ·
 *       감사 {@code SANCTION_LIFT} · 알림 {@code SANCTION_LIFTED}. 경고 횟수는 그대로다.</li>
 *   <li><b>이의제기</b>({@link #fileAppeal}): 내 제재 · 무효 아님 · 처음 · 시작 후 30일 안 · 사유 10~500자 → 운영자 알림 {@code SANCTION_APPEAL_FILED}.
 *       <b>답변</b>({@link #answer}): 대기만(409 APPEAL_CLOSED), 답변 1~500자, 인용이면 제재 VOIDED(경고면 남은 경고 번호를 다시 매김) + 진행 중이던 정지는 즉시 풀림,
 *       감사 {@code APPEAL_ACCEPT}·{@code APPEAL_REJECT}, 알림 {@code APPEAL_ANSWERED}(메일).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class MemberSanctionService {

    private static final Logger log = LoggerFactory.getLogger(MemberSanctionService.class);

    /** 사유·답변·해제 사유 최대 글자 */
    public static final int TEXT_MAX = 500;
    /** 이의 사유 글자 */
    public static final int APPEAL_MIN = 10;
    /** 이의제기 기한 — 제재 시작 후 30일(결정 3) */
    public static final Duration APPEAL_WINDOW = Duration.ofDays(30);
    /** 현황·이의제기 상세의 이력 최대 */
    public static final int HISTORY_MAX = 30;
    /** 감사 대상 종류 */
    public static final String TARGET = "MEMBER";

    private final MemberSanctionStore store;
    private final AuditLogService audit;
    private final MemberNotificationPublisher memberNotices;
    private final NotificationPublisher operatorNotices;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;
    private final BoardSanctionService boardSanctions;

    public MemberSanctionService(MemberSanctionStore store, AuditLogService audit, MemberNotificationPublisher memberNotices, NotificationPublisher operatorNotices,
                                 SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks, BoardSanctionService boardSanctions) {
        this.store = store;
        this.boardSanctions = boardSanctions;
        this.audit = audit;
        this.memberNotices = memberNotices;
        this.operatorNotices = operatorNotices;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 지금(DB 시각 정밀도 — 마이크로초) */
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 이의제기 마감(시작 + 30일) — 무효면 null */
    public static Instant appealDeadline(SanctionRow row) {
        return row.status() == SanctionStatus.VOIDED ? null : row.startsAt().plus(APPEAL_WINDOW);
    }

    // ── 읽기 ──────────────────────────────────────────────────────────────

    /** 제재 대상 회원(없으면 빈 값) */
    public Optional<MemberRow> member(long userId) {
        return store.member(userId);
    }

    /** 그 회원의 제재 전부(최근 것이 위) */
    public List<SanctionRow> allOf(long userId) {
        return store.allOfUser(userId);
    }

    public Optional<SanctionRow> find(long id) {
        return store.find(id);
    }

    public Optional<AppealRow> findAppeal(long id) {
        return store.findAppeal(id);
    }

    public List<AppealRow> appealsOf(long userId) {
        return store.appealsOfUser(userId);
    }

    /** 확인하지 않은 유효한 경고 수 */
    public int pendingWarnings(long userId) {
        return store.pendingWarnings(userId);
    }

    /** 진행 중인 기간 정지의 끝(없으면 빈 값) */
    public Optional<Instant> runningUntil(long userId) {
        return store.runningUntil(userId, now());
    }

    // ── 지연 만료 ─────────────────────────────────────────────────────────

    /**
     * 끝이 지난 정지를 끝내고 회원 상태를 바로잡는다 — 할 일이 있을 때만 회원 행을 잠근다(없으면 색인 읽기 한 번). 회원 쪽 로그인 정보·내 제재가 읽기 전에 부른다.
     * ⚠ R__batch_39_sanction_expire.sql 과 같은 판정(이중 구현).
     *
     * @return 바꾼 것이 있으면 true
     */
    @Transactional
    public boolean settle(long userId) {
        Instant now = now();
        if (!store.needsSettle(userId, now)) {
            return false;
        }
        if (store.lockMember(userId).isEmpty()) {
            return false;
        }
        return settleLocked(userId, now);
    }

    /** 회원 행을 잠근 뒤의 지연 만료 */
    private boolean settleLocked(long userId, Instant now) {
        int ended = store.endDue(userId, now);
        boolean released = store.releaseMember(userId, now);
        if (ended > 0 || released) {
            log.info("제재 지연 만료 userId={} ended={} released={}", userId, ended, released);
        }
        return ended > 0 || released;
    }

    // ── 부여 ──────────────────────────────────────────────────────────────

    /**
     * 제재를 준다. 칸 오류 경로는 {@code request.fieldPrefix()} 를 앞에 붙인다(신고 처리 {@code sanction.}).
     *
     * @return 새 제재 줄
     */
    @Transactional
    public SanctionRow issue(SanctionIssue request, SanctionOperator operator) {
        String prefix = request.fieldPrefix() == null ? "" : request.fieldPrefix();
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        SanctionKind kind = SanctionKind.of(request.kind()).orElse(null);
        if (kind == null) {
            invalid.put(prefix + "kind", new MessageRef("sanction.kind.invalid"));
        }
        String reason = clean(request.reason());
        if (reason == null || length(reason) > TEXT_MAX) {
            invalid.put(prefix + "reason", new MessageRef("sanction.reason.length", 1, TEXT_MAX));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        MemberRow member = store.lockMember(request.userId()).filter(m -> !m.gone()).orElseThrow(SanctionTargetNotFoundException::new);
        Instant now = now();
        settleLocked(member.id(), now);

        SanctionLadder.Standing standing = SanctionLadder.standing(store.allOfUser(member.id()), now);
        if (SanctionLadder.BLOCKED_ALREADY_BANNED.equals(standing.blockedReason())) {
            throw new SanctionAlreadyBannedException();
        }
        if (SanctionLadder.BLOCKED_SUSPENDED.equals(standing.blockedReason()) && kind != SanctionKind.BAN) {
            throw new SanctionAlreadySuspendedException();
        }
        SanctionLadder.Option option = standing.option(kind);
        if (SanctionLadder.LOWER.equals(option.state()) || SanctionLadder.BLOCKED.equals(option.state())) {
            throw new SanctionStepTooLowException();
        }
        boolean skip = SanctionLadder.SKIP.equals(option.state());
        if (skip && !request.skipStep()) {
            throw ValidationException.of(prefix + "skipStep", new MessageRef("sanction.skipStep.required"));
        }
        if (option.requiresBan() ? !operator.canBan() : !operator.canManage()) {
            deny(operator, option.requiresBan() ? Permission.SANCTION_BAN : Permission.SANCTION_MANAGE);
        }
        if (kind == SanctionKind.BAN && !request.sealed()) { // 봉인 제재는 닉네임을 모르므로 확인하지 않는다(대상은 서버가 글·댓글에서 찾았다)
            String typed = request.confirmNickname() == null ? "" : request.confirmNickname().strip();
            if (member.nickname() == null || !member.nickname().equals(typed)) {
                throw ValidationException.of(prefix + "confirmNickname", new MessageRef("sanction.confirmNickname.mismatch"));
            }
        }

        Long replaced = null;
        if (kind == SanctionKind.BAN && standing.running() != null) {
            replaced = store.endRunning(member.id(), now).orElse(null); // 정지 중 영구 정지 — 진행 중 정지는 끝난 것으로(정책 2)
        }
        Integer warningNo = kind.isWarning() ? Math.min(SanctionLadder.WARNING_LIMIT, standing.warningCount() + 1) : null;
        Instant endsAt = kind.timed() ? now.plus(kind.length()) : null;
        long id = ids.nextId();
        long no = store.insert(id, member.id(), kind, warningNo, skip, reason, now, endsAt, operator.operatorId(), request.reportCaseId(), now, request.sealed());
        List<StaffRow> staffEnded = List.of();
        if (kind.restricts()) {
            store.restrictMember(member.id(), kind == SanctionKind.BAN ? "BANNED" : "SUSPENDED", now);
            staffEnded = store.endBoardStaff(member.id()); // 정지 시작 — 게시판 매니저·스텝 지정 끝(되살아나지 않는다, operator-permissions.md 「자동 해제」)
        }

        AuditEvent event = sealedTarget(AuditEvent.byOperator(operator.operatorId(), operator.ip(), AuditActions.SANCTION_ISSUE, AuditResult.SUCCESS), request,
                member.id()).with("sanctionId", Long.toString(id)).with("sanctionNo", no).with("kind", kind.name())
                .with("skipped", skip).with("reason", reason);
        if (warningNo != null) {
            event = event.with("warningNo", warningNo);
        }
        if (endsAt != null) {
            event = event.with("endsAt", endsAt.toString());
        }
        if (request.reportCaseId() != null) {
            event = event.with("reportCaseId", Long.toString(request.reportCaseId()));
        }
        if (replaced != null) {
            event = event.with("replacedSanctionId", Long.toString(replaced));
        }
        if (!staffEnded.isEmpty()) {
            event = event.with("boardStaffEnded", staffEnded.stream().map(s -> s.role() + " " + s.boardId()).toList());
        }
        audit.record(event);
        memberNotices.sanctionIssued(member.id(), id, kind.name(), warningNo, endsAt, reason);
        log.info("제재 부여 sanctionId={} userId={} kind={} skipped={} by operatorId={}", id, member.id(), kind, skip, operator.operatorId());
        return store.find(id).orElseThrow();
    }

    /**
     * 감사 대상 — 봉인 제재(V184)는 회원 대신 그 글·댓글(+ {@code sealed: true}) — 감사 목록·검색에서 회원과 이어지지 않는다(anonymous-board-contract 정책 8·9).
     */
    private static AuditEvent sealedTarget(AuditEvent event, SanctionIssue request, long userId) {
        SanctionIssue.SealedSource source = request.sealedSource();
        if (source == null) {
            return event.target(TARGET, Long.toString(userId));
        }
        return source.commentId() != null ? event.target("COMMENT", Long.toString(source.commentId())).with("postId", Long.toString(source.postId())).with("sealed", true)
                : event.target("POST", Long.toString(source.postId())).with("sealed", true);
    }

    // ── 해제 ──────────────────────────────────────────────────────────────

    /**
     * 진행 중인 정지·영구 정지를 푼다.
     *
     * @throws SanctionNotFoundException   없는 제재(404)
     * @throws SanctionNotLiftableException 경고 · 이미 끝남·풀림·무효(409)
     */
    @Transactional
    public SanctionRow lift(long sanctionId, String reasonText, SanctionOperator operator) {
        long userId = store.userOf(sanctionId).orElseThrow(SanctionNotFoundException::new);
        store.lockMember(userId).orElseThrow(SanctionNotFoundException::new);
        Instant now = now();
        settleLocked(userId, now);
        SanctionRow row = store.find(sanctionId).orElseThrow(SanctionNotFoundException::new);
        if (!row.runningAt(now)) {
            throw new SanctionNotLiftableException();
        }
        String reason = clean(reasonText);
        if (reason == null || length(reason) > TEXT_MAX) {
            throw ValidationException.of("reason", new MessageRef("sanction.reason.length", 1, TEXT_MAX));
        }
        boolean ban = row.kind() == SanctionKind.BAN;
        if (ban ? !operator.canBan() : !operator.canManage()) {
            deny(operator, ban ? Permission.SANCTION_BAN : Permission.SANCTION_MANAGE);
        }
        if (!store.lift(sanctionId, operator.operatorId(), reason, now)) {
            throw new SanctionNotLiftableException();
        }
        store.releaseMember(userId, now);
        AuditEvent lifted = AuditEvent.byOperator(operator.operatorId(), operator.ip(), AuditActions.SANCTION_LIFT, AuditResult.SUCCESS);
        // 봉인 제재(V184)의 해제는 회원이 아니라 그 제재를 대상으로 남긴다(감사 목록에서 신원이 이어지지 않게)
        lifted = row.sealed() ? lifted.with("sealed", true) : lifted.target(TARGET, Long.toString(userId));
        audit.record(lifted.with("sanctionId", Long.toString(sanctionId)).with("sanctionNo", row.no()).with("kind", row.kind().name()).with("reason", reason));
        memberNotices.sanctionLifted(userId, sanctionId, row.kind().name());
        log.info("제재 해제 sanctionId={} userId={} kind={} by operatorId={}", sanctionId, userId, row.kind(), operator.operatorId());
        return store.find(sanctionId).orElseThrow();
    }

    // ── 회원 쪽 ───────────────────────────────────────────────────────────

    /** 경고 확인(멱등 — 경고가 아니면 할 일 없음) */
    @Transactional
    public void acknowledge(long userId, long sanctionId) {
        if (!store.acknowledge(sanctionId, userId, now())) {
            throw new SanctionNotFoundException();
        }
    }

    /**
     * 이의제기 접수 — 정지·영구 정지 중에도. 운영자 알림 {@code SANCTION_APPEAL_FILED}.
     *
     * @throws SanctionNotFoundException       내 제재가 아니거나 없음(404)
     * @throws SanctionNotAppealableException  무효가 된 제재(409)
     * @throws AppealAlreadyException          이미 접수(409 — 기각 뒤에도)
     * @throws AppealExpiredException          시작 후 30일이 지남(409)
     * @throws ValidationException             사유 10~500자({@code fields.reason})
     */
    @Transactional
    public AppealRow fileAppeal(long userId, long sanctionId, String reasonText) {
        SanctionRow row = store.find(sanctionId).filter(r -> r.userId() == userId).orElseThrow(SanctionNotFoundException::new);
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
        String reason = clean(reasonText);
        int length = reason == null ? 0 : length(reason);
        if (length < APPEAL_MIN || length > TEXT_MAX) {
            throw ValidationException.of("reason", new MessageRef("appeal.reason.length", APPEAL_MIN, TEXT_MAX));
        }
        long id = ids.nextId();
        long no = store.insertAppeal(id, sanctionId, userId, reason, now).orElseThrow(AppealAlreadyException::new); // 동시 접수 — 유일 색인이 한 번만
        operatorNotices.publish(AdminNotificationEvent.SANCTION_APPEAL_FILED, NotificationContext.empty().link("appealId", Long.toString(id)));
        log.info("이의제기 접수 appealId={} appealNo={} sanctionId={} userId={}", id, no, sanctionId, userId);
        return store.findAppeal(id).orElseThrow();
    }

    // ── 이의제기 답변 ─────────────────────────────────────────────────────

    /**
     * 인용({@code accept}) · 기각 — 권한({@code appeal.manage})은 부르는 쪽 입구가 본다.
     *
     * @throws AppealNotFoundException 없음(404)
     * @throws AppealClosedException   이미 답함(409)
     * @throws ValidationException     답변 1~500자({@code fields.answer})
     */
    @Transactional
    public AppealRow answer(long appealId, boolean accept, String answerText, long operatorId, String ip) {
        AppealRow appeal = store.lockAppeal(appealId).orElseThrow(AppealNotFoundException::new);
        if (appeal.status() != AppealStatus.PENDING) {
            throw new AppealClosedException();
        }
        String answer = clean(answerText);
        if (answer == null || length(answer) > TEXT_MAX) {
            throw ValidationException.of("answer", new MessageRef("appeal.answer.length", 1, TEXT_MAX));
        }
        if (appeal.board()) {
            return boardSanctions.answerAppeal(appeal, accept, answer, operatorId, ip); // 게시판 제재 이의제기(V137 — board-sanction-contract 결정 4)
        }
        store.lockMember(appeal.userId());
        Instant now = now();
        settleLocked(appeal.userId(), now);
        SanctionRow sanction = store.find(appeal.sanctionId()).orElseThrow(AppealNotFoundException::new);
        boolean released = false;
        if (accept) {
            boolean wasRunning = sanction.runningAt(now);
            store.voidSanction(sanction.id(), now);
            if (sanction.kind().isWarning()) {
                store.renumberWarnings(appeal.userId(), now); // 무효가 된 경고를 빼고 남은 경고 번호를 다시 센다
            }
            if (wasRunning) {
                released = store.releaseMember(appeal.userId(), now);
            }
        }
        if (!store.answerAppeal(appealId, accept ? AppealStatus.ACCEPTED : AppealStatus.REJECTED, answer, operatorId, now)) {
            throw new AppealClosedException();
        }
        AuditEvent event = AuditEvent.byOperator(operatorId, ip, accept ? AuditActions.APPEAL_ACCEPT : AuditActions.APPEAL_REJECT, AuditResult.SUCCESS)
                .target(TARGET, Long.toString(appeal.userId())).with("appealId", Long.toString(appealId)).with("appealNo", appeal.no())
                .with("sanctionId", Long.toString(sanction.id())).with("kind", sanction.kind().name()).with("answer", answer);
        audit.record(released ? event.with("released", true) : event);
        String result = accept ? AppealStatus.ACCEPTED.name() : AppealStatus.REJECTED.name();
        memberNotices.appealAnswered(appeal.userId(), sanction.id(), appealId, sanction.kind().name(), sanction.warningNo(), result, answer);
        log.info("이의제기 답변 appealId={} result={} sanctionId={} by operatorId={}", appealId, result, sanction.id(), operatorId);
        return store.findAppeal(appealId).orElseThrow();
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    private static void deny(SanctionOperator operator, Permission required) {
        operator.deny().accept(required.code());
        throw new IllegalStateException("권한 거부가 던지지 않았다: " + required.code());
    }

    /** 앞뒤 공백·NUL 을 뗀 글 — 비었으면 null */
    static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.replace("\u0000", "").strip();
        return s.isEmpty() ? null : s;
    }

    static int length(String text) {
        return text.codePointCount(0, text.length());
    }
}
