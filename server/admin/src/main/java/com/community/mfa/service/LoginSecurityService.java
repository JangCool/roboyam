package com.community.mfa.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.mfa.dto.MfaOperatorStatus;
import com.community.mfa.dto.MfaSettings;
import com.community.mfa.dto.OperatorMfaRow;
import com.community.mfa.dto.request.LoginSecuritySaveRequest;
import com.community.mfa.dto.response.LoginSecurityPendingOperator;
import com.community.mfa.dto.response.LoginSecuritySummary;
import com.community.mfa.dto.response.LoginSecurityView;
import com.community.mfa.exception.MfaRootNotEnrolledException;
import com.community.mfa.repository.master.MfaStore;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.permission.exception.PermissionDeniedException;
import com.community.permission.service.AccessDeniedRecorder;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 보안 설정(「설정 › 시스템 › 로그인 보안」) — <b>최고 관리자(루트)만</b> 읽고 바꾼다(권한 코드로 위임하지 않는다 — commerce.md 13-3).
 *
 * <ul>
 *   <li>루트가 아니면 403 {@code PERMISSION_DENIED} + 거부 감사(사유 SUPER_ONLY). 입구는 {@code @AdminAuthenticated} 이고 판정은 여기서 한다.</li>
 *   <li>검사 순서: 칸 범위(사용 여부 필수 · 유예 1~90 · 기기 신뢰 1~12 · 실패 한도 3~10 → 400 {@code VALIDATION}) → 켜는 순간 루트 본인 미등록이면 409 {@code MFA_ROOT_NOT_ENROLLED}.</li>
 *   <li>꺼짐 → 켜짐이면 {@code mfa_enabled_at} = 지금(유예 시작)·{@code mfa_enabled_by} = 루트. 바꾼 칸만 감사 {@code MFA_SETTINGS_UPDATE}({@code before}/{@code after}) —
 *       바뀐 것이 없으면 쓰지도 감사하지도 않는다.</li>
 *   <li>끄면 운영자 전원에게 심각도 높음 알림({@code MFA_DISABLED}), 켜면 운영자 전원에게 등록 안내({@code MFA_ENABLED}, 켠 사람 제외).</li>
 *   <li>등록 현황은 활성 운영자(루트 포함) 기준 — 켜져 있을 때만 유예 안/지남을 센다. 미등록 목록은 유예 마감이 이른 차례(같으면 ID) 최대 10명.</li>
 * </ul>
 */
@Service
public class LoginSecurityService {

    private static final Logger log = LoggerFactory.getLogger(LoginSecurityService.class);

    /** 미등록 목록의 최대 인원 */
    public static final int PENDING_LIMIT = 10;
    /** 감사 기록의 대상 — 설정 행 하나 */
    public static final String TARGET_TYPE = "LOGIN_SECURITY";

    private final MfaStore store;
    private final OperatorRepository operators;
    private final AccessDeniedRecorder denied;
    private final AuditLogService audit;
    private final NotificationPublisher publisher;
    private final Clock clock;

    public LoginSecurityService(MfaStore store, OperatorRepository operators, AccessDeniedRecorder denied, AuditLogService audit, NotificationPublisher publisher,
                                Clock clock) {
        this.store = store;
        this.operators = operators;
        this.denied = denied;
        this.audit = audit;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    public LoginSecurityView view(AdminActor actor) {
        requireRoot(actor);
        return viewOf(store.settings(), actor.operatorId());
    }

    @Transactional
    public LoginSecurityView save(AdminActor actor, LoginSecuritySaveRequest request) {
        requireRoot(actor);
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        Boolean enabled = request == null ? null : request.enabled();
        Integer graceDays = request == null ? null : request.graceDays();
        Integer trustMonths = request == null ? null : request.trustMonths();
        Integer maxFailures = request == null ? null : request.maxFailures();
        if (enabled == null) {
            invalid.put("enabled", new MessageRef("loginSecurity.enabled.required"));
        }
        range(invalid, "graceDays", graceDays, 1, 90);
        range(invalid, "trustMonths", trustMonths, 1, 12);
        range(invalid, "maxFailures", maxFailures, 3, 10);
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }

        MfaSettings before = store.lockSettings();
        Instant now = clock.instant();
        boolean turningOn = enabled && !before.enabled();
        boolean turningOff = !enabled && before.enabled();
        if (turningOn && !enrolled(actor.operatorId())) {
            throw new MfaRootNotEnrolledException();
        }
        MfaSettings next = new MfaSettings(enabled, graceDays, trustMonths, maxFailures, turningOn ? now : before.enabledAt(),
                turningOn ? Long.valueOf(actor.operatorId()) : before.enabledBy());

        Map<String, Object> changedBefore = new LinkedHashMap<>();
        Map<String, Object> changedAfter = new LinkedHashMap<>();
        changed(changedBefore, changedAfter, "enabled", before.enabled(), next.enabled());
        changed(changedBefore, changedAfter, "graceDays", before.graceDays(), next.graceDays());
        changed(changedBefore, changedAfter, "trustMonths", before.trustMonths(), next.trustMonths());
        changed(changedBefore, changedAfter, "maxFailures", before.maxFailures(), next.maxFailures());
        if (!changedAfter.isEmpty()) {
            store.saveSettings(next, actor.operatorId(), now);
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.MFA_SETTINGS_UPDATE, AuditResult.SUCCESS).target(TARGET_TYPE, "1")
                    .with("before", changedBefore).with("after", changedAfter));
            if (turningOff) {
                publisher.publish(AdminNotificationEvent.MFA_DISABLED, NotificationContext.empty().actor(actor.operatorId()));
            } else if (turningOn) {
                publisher.publish(AdminNotificationEvent.MFA_ENABLED, NotificationContext.empty().actor(actor.operatorId()));
            }
            log.info("로그인 보안 설정을 바꿨다 바뀐 칸={} by operatorId={}", changedAfter.keySet(), actor.operatorId());
        }
        return viewOf(next, actor.operatorId());
    }

    /** 루트가 아니면 거부 감사 + 403 */
    private void requireRoot(AdminActor actor) {
        if (!actor.root()) {
            denied.record(actor, "SUPER_ONLY", TARGET_TYPE, "1", List.of());
            throw new PermissionDeniedException();
        }
    }

    private boolean enrolled(long operatorId) {
        return store.find(operatorId).map(OperatorMfaRow::enrolled).orElse(false);
    }

    private LoginSecurityView viewOf(MfaSettings settings, long rootId) {
        String enabledBy = settings.enabledBy() == null ? null : operators.findById(settings.enabledBy()).map(OperatorEntity::getOperatorName).orElse(null);
        return new LoginSecurityView(settings.enabled(), settings.enabledAt(), enabledBy, settings.graceDays(), settings.trustMonths(), settings.maxFailures(),
                enrolled(rootId), summary(settings));
    }

    private LoginSecuritySummary summary(MfaSettings settings) {
        Instant now = clock.instant();
        List<MfaOperatorStatus> active = store.activeOperators(now);
        int enrolledCount = 0;
        int inGrace = 0;
        int expired = 0;
        record Pending(MfaOperatorStatus operator, Instant graceEndsAt) {
        }
        List<Pending> pending = new java.util.ArrayList<>();
        for (MfaOperatorStatus operator : active) {
            if (operator.enrolled()) {
                enrolledCount++;
                continue;
            }
            Instant end = MfaPolicy.graceEndsAt(settings, operator.createdAt());
            if (end != null) {
                if (now.isBefore(end)) {
                    inGrace++;
                } else {
                    expired++;
                }
            }
            pending.add(new Pending(operator, end));
        }
        List<LoginSecurityPendingOperator> top = pending.stream()
                .sorted(Comparator.comparing(Pending::graceEndsAt, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(p -> p.operator().operatorId()))
                .limit(PENDING_LIMIT)
                .map(p -> new LoginSecurityPendingOperator(Long.toString(p.operator().operatorId()), p.operator().operatorName(), p.graceEndsAt()))
                .toList();
        return new LoginSecuritySummary(active.size(), enrolledCount, inGrace, expired, top);
    }

    private static void range(Map<String, MessageRef> invalid, String field, Integer value, int min, int max) {
        if (value == null || value < min || value > max) {
            invalid.put(field, new MessageRef("loginSecurity.range", min, max));
        }
    }

    private static void changed(Map<String, Object> before, Map<String, Object> after, String field, Object was, Object now) {
        if (!Objects.equals(was, now)) {
            before.put(field, was);
            after.put(field, now);
        }
    }
}
