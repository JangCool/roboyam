package com.community.restriction.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.restriction.dto.request.RestrictionGrant;
import com.community.restriction.dto.response.MemberRestrictionView;
import com.community.restriction.dto.response.RestrictionActorView;
import com.community.restriction.entity.RestrictedFeature;
import com.community.restriction.entity.RestrictionSource;
import com.community.restriction.entity.RestrictionStatus;
import com.community.restriction.entity.RestrictionType;
import com.community.restriction.exception.MemberRestrictedException;
import com.community.restriction.exception.MemberWithdrawnException;
import com.community.restriction.exception.RestrictionMemberNotFoundException;
import com.community.restriction.exception.RestrictionNotActiveException;
import com.community.restriction.exception.RestrictionNotFoundException;
import com.community.restriction.repository.master.MemberRestrictionStore;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기능 제한(commerce.md 12-3, docs/features/commerce-foundation-contract.md 「0-4」) — 계정 전체가 아니라 특정 기능만 막는 제재. 계정 정지와 따로 걸린다.
 *
 * <ul>
 *   <li><b>진행 중</b> = 해제 안 됨 ∧ 시작 ≤ 지금 ∧ (끝 없음 ∨ 끝 > 지금). 같은 종류가 겹쳐도 막지 않는다 — 늦게 끝나는 쪽까지 막힌다(화면 확정 결정 1).</li>
 *   <li><b>막는 것</b>({@link #assertAllowed}): {@link RestrictionType#blocks} — 걸리면 403 {@code MEMBER_RESTRICTED} + {@code restriction{type, until, reason}}(운영 메모 없음).
 *       {@code until} 은 같은 종류 중 가장 늦은 끝(무기한이 있으면 null). 두 종류가 같은 기능을 막으면(충전 = 동결·충전 제한) 더 늦게 끝나는 종류를 알린다.</li>
 *   <li><b>부여</b>: 운영자({@link #grant} — 탈퇴 회원 409, 끝이 지금 이전이면 400) · 자동({@link #grantAuto} — 같은 종류가 그보다 늦게까지 진행 중이면 만들지 않는다).
 *       둘 다 감사({@code MEMBER_RESTRICTION_GRANT}, 자동은 SYSTEM)와 <b>회원 알림</b>(종류·끝·공개 사유 — 운영 메모 없음).</li>
 *   <li><b>해제</b>({@link #lift}): 조건부 UPDATE(진행 중일 때만) — 없거나 남의 것 404, 이미 해제·만료 409. 사유 필수(5~200자, 화면 확정 결정 2). 감사 {@code MEMBER_RESTRICTION_LIFT}.</li>
 *   <li>{@link #hasActive} — 탈퇴 전 검증(17-1)·지금 있는 포인트 적립(동결이면 조용히 건너뛴다)이 쓴다.</li>
 * </ul>
 * 관리자 화면 API(다음 단계)가 {@link #grant}·{@link #lift}·{@link #list} 를 부른다 — 권한({@code member.read}·{@code sanction.manage}) 검사는 컨트롤러 선언이 한다.
 */
@Service
@Transactional(readOnly = true)
public class MemberRestrictionService {

    /** 공개 사유·해제 사유 길이 */
    public static final int REASON_MIN = 5;
    public static final int REASON_MAX = 200;
    /** 운영 메모 최대 길이 */
    public static final int MEMO_MAX = 1000;

    private final MemberRestrictionStore store;
    private final AuditLogService audit;
    private final MemberNotificationPublisher notices;
    private final SnowflakeIdGenerator ids;
    private final Messages messages;
    private final Clock clock;

    public MemberRestrictionService(MemberRestrictionStore store, AuditLogService audit, MemberNotificationPublisher notices, SnowflakeIdGenerator ids,
                                    Messages messages, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.audit = audit;
        this.notices = notices;
        this.ids = ids;
        this.messages = messages;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 판정 ──────────────────────────────────────────────────────────────────

    /**
     * 그 기능을 쓸 수 있는가 — 막혀 있으면 던진다.
     *
     * @throws MemberRestrictedException 진행 중인 제한이 그 기능을 막을 때(403)
     */
    public void assertAllowed(long userId, RestrictedFeature feature) {
        blocking(userId, feature).ifPresent(e -> {
            throw e;
        });
    }

    /** 그 기능을 막는 제한(없으면 빈 값) — 막힌 기능 안내를 미리 보일 때 */
    public Optional<MemberRestrictedException> blocking(long userId, RestrictedFeature feature) {
        Instant now = clock.instant();
        List<MemberRestrictionStore.Row> blocking = store.active(userId, now).stream().filter(row -> row.type().blocks(feature)).toList();
        if (blocking.isEmpty()) {
            return Optional.empty();
        }
        // 종류마다 가장 늦은 끝(무기한이 있으면 null) — 그중 가장 늦게 끝나는 종류를 알린다. 저장소가 늦게 끝나는 것부터(무기한 먼저) 준다
        Map<RestrictionType, MemberRestrictionStore.Row> latest = new LinkedHashMap<>();
        blocking.forEach(row -> latest.putIfAbsent(row.type(), row));
        MemberRestrictionStore.Row chosen = latest.values().stream().min(Comparator.comparing(MemberRestrictionStore.Row::until,
                Comparator.nullsFirst(Comparator.<Instant>reverseOrder()))).orElseThrow();
        return Optional.of(new MemberRestrictedException(chosen.type(), chosen.until(), publicReason(chosen)));
    }

    /** 종류 무관 진행 중인 제한이 있는가(탈퇴 전 검증 — 17-1) */
    public boolean hasActive(long userId) {
        return store.hasAnyActive(userId, clock.instant());
    }

    /** 그 종류가 진행 중인가 */
    public boolean hasActive(long userId, RestrictionType type) {
        return store.hasActive(userId, type, clock.instant());
    }

    /** 그 회원의 기능 제한 — 진행 중 먼저, 그다음 최근 것부터. 상태는 지금 시각으로 판정한다 */
    public List<MemberRestrictionView> list(long userId) {
        Instant now = clock.instant();
        return store.list(userId, now).stream().map(row -> view(row, now)).toList();
    }

    // ── 부여·해제 ─────────────────────────────────────────────────────────────

    /**
     * 운영자가 기능 제한을 건다(시작 = 지금).
     *
     * @throws ValidationException                종류 없음·끝이 지금 이전·사유 길이·메모 길이({@code fields.type|until|reason|adminMemo})
     * @throws RestrictionMemberNotFoundException 없는 회원(404 {@code MEMBER_NOT_FOUND})
     * @throws MemberWithdrawnException           탈퇴한 회원(409)
     */
    @Transactional
    public MemberRestrictionView grant(long userId, RestrictionGrant request, long operatorId, String ip) {
        Instant now = clock.instant();
        Map<String, MessageRef> violations = new LinkedHashMap<>();
        if (request.type() == null) {
            violations.put("type", new MessageRef("restriction.type.required"));
        }
        if (request.until() != null && !request.until().isAfter(now)) {
            violations.put("until", new MessageRef("restriction.until.past"));
        }
        String reason = request.reason() == null ? "" : request.reason().strip();
        if (reason.length() < REASON_MIN || reason.length() > REASON_MAX) {
            violations.put("reason", new MessageRef("restriction.reason.length", REASON_MIN, REASON_MAX));
        }
        String memo = request.adminMemo() == null || request.adminMemo().isBlank() ? null : request.adminMemo().strip();
        if (memo != null && memo.length() > MEMO_MAX) {
            violations.put("adminMemo", new MessageRef("restriction.memo.max", MEMO_MAX));
        }
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
        boolean gone = store.memberGone(userId).orElseThrow(RestrictionMemberNotFoundException::new);
        if (gone) {
            throw new MemberWithdrawnException();
        }
        long id = ids.nextId();
        store.insert(id, userId, request.type(), reason, memo, now, request.until(), RestrictionSource.OPERATOR, null, operatorId, now);
        AuditEvent event = AuditEvent.byOperator(operatorId, ip, AuditActions.MEMBER_RESTRICTION_GRANT, AuditResult.SUCCESS).target("MEMBER", Long.toString(userId))
                .with("restrictionId", Long.toString(id)).with("source", RestrictionSource.OPERATOR.name()).with("type", request.type().name());
        audit.record(request.until() == null ? event.with("indefinite", true) : event.with("until", request.until().toString())); // detail 은 null 을 담지 않는다
        notices.featureRestricted(userId, request.type().name(), request.until(), reason, null);
        return view(store.find(id).orElseThrow(), now);
    }

    /**
     * 자동 규칙으로 기능 제한을 건다(예: 24시간 안 선점 3번 만료 → {@code grantAuto(userId, GROUP_BUY_BAN, 24시간, GROUP_BUY_HOLD_EXPIRED)}). 부르는 업무의 트랜잭션 안에서.
     * 같은 종류가 그보다 늦게까지(또는 무기한) 진행 중이거나 회원이 없거나 탈퇴했으면 만들지 않는다. 같은 회원의 자동 부여는 줄을 선다(중복 없이).
     *
     * @return 만든 제한(만들지 않았으면 빈 값)
     */
    @Transactional
    public Optional<MemberRestrictionView> grantAuto(long userId, RestrictionType type, Duration duration, String autoRule) {
        if (duration == null || duration.isNegative() || duration.isZero() || autoRule == null || autoRule.isBlank()) {
            throw new IllegalArgumentException("자동 제한의 기간·규칙이 없다");
        }
        store.lockMember(userId);
        Instant now = clock.instant();
        Instant until = now.plus(duration);
        if (store.memberGone(userId).orElse(true)) {
            return Optional.empty();
        }
        boolean covered = store.active(userId, now).stream().anyMatch(row -> row.type() == type && (row.until() == null || !row.until().isBefore(until)));
        if (covered) {
            return Optional.empty();
        }
        long id = ids.nextId();
        store.insert(id, userId, type, null, null, now, until, RestrictionSource.AUTO, autoRule, null, now);
        audit.record(new AuditEvent(AuditActorType.SYSTEM, null, AuditActions.MEMBER_RESTRICTION_GRANT, AuditResult.SUCCESS, "MEMBER", Long.toString(userId), null,
                Map.of("restrictionId", Long.toString(id), "source", RestrictionSource.AUTO.name(), "type", type.name(), "until", until.toString(), "autoRule", autoRule)));
        notices.featureRestricted(userId, type.name(), until, null, autoRule);
        return Optional.of(view(store.find(id).orElseThrow(), now));
    }

    /**
     * 진행 중인 기능 제한을 해제한다.
     *
     * @throws ValidationException           해제 사유 길이({@code fields.reason})
     * @throws RestrictionNotFoundException  없거나 그 회원의 것이 아닐 때(404)
     * @throws RestrictionNotActiveException 이미 해제됐거나 기간이 끝났을 때(409)
     */
    @Transactional
    public MemberRestrictionView lift(long userId, long restrictionId, String reason, long operatorId, String ip) {
        String why = reason == null ? "" : reason.strip();
        if (why.length() < REASON_MIN || why.length() > REASON_MAX) {
            throw ValidationException.of("reason", new MessageRef("restriction.reason.length", REASON_MIN, REASON_MAX));
        }
        Instant now = clock.instant();
        if (!store.lift(restrictionId, userId, operatorId, why, now)) {
            MemberRestrictionStore.Row row = store.find(restrictionId).filter(r -> r.userId() == userId).orElseThrow(RestrictionNotFoundException::new);
            throw row.activeAt(now) ? new IllegalStateException("해제하지 못했다") : new RestrictionNotActiveException();
        }
        MemberRestrictionStore.Row lifted = store.find(restrictionId).orElseThrow();
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.MEMBER_RESTRICTION_LIFT, AuditResult.SUCCESS).target("MEMBER", Long.toString(userId))
                .with("restrictionId", Long.toString(restrictionId)).with("type", lifted.type().name()).with("reason", why));
        return view(lifted, now);
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private MemberRestrictionView view(MemberRestrictionStore.Row row, Instant now) {
        RestrictionStatus status = row.liftedAt() != null ? RestrictionStatus.LIFTED
                : row.until() != null && !row.until().isAfter(now) ? RestrictionStatus.EXPIRED : RestrictionStatus.ACTIVE;
        return new MemberRestrictionView(Long.toString(row.id()), row.type(), status, row.source(), row.autoRule(), publicReason(row), row.adminMemo(), row.startsAt(),
                row.until(), actor(row.createdBy(), row.createdByName()), row.createdAt(), actor(row.liftedBy(), row.liftedByName()), row.liftedAt(), row.liftReason());
    }

    /** 회원에게 공개하는 사유 — 자동 부여는 규칙의 번들 문구(요청 언어) */
    private String publicReason(MemberRestrictionStore.Row row) {
        if (row.reason() != null) {
            return row.reason();
        }
        return row.autoRule() == null ? "" : messages.get(new MessageRef("restriction.autoRule." + row.autoRule() + ".reason"));
    }

    private static RestrictionActorView actor(Long id, String name) {
        return id == null ? null : new RestrictionActorView(Long.toString(id), name);
    }
}
