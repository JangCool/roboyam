package com.community.mfa.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.mfa.dto.MfaSettings;
import com.community.mfa.dto.OperatorMfaRow;
import com.community.mfa.dto.response.OperatorMfaView;
import com.community.mfa.exception.MfaResetSelfException;
import com.community.mfa.repository.master.MfaStore;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.exception.OperatorNotFoundException;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.permission.exception.PermissionDeniedException;
import com.community.permission.service.AccessDeniedRecorder;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 상세의 2차 인증 — 조회(운영자 조회 권한 {@code operator.read})와 OTP 초기화(<b>루트만</b>, 다른 운영자에게만).
 *
 * <ul>
 *   <li>초기화: 루트가 아니면 403 + 거부 감사(SUPER_ONLY) · 없는 운영자 404 · 본인이면 409 {@code MFA_RESET_SELF}(본인은 서버 접근자의 초기화 명령으로만).
 *       그 운영자의 OTP(등록 중 비밀키 포함)·복구 코드·신뢰 기기·2차 인증 실패 기록을 지우고 감사 {@code MFA_RESET}({@code via} = ADMIN, 지운 수).
 *       등록하지 않았던 운영자여도 남은 기록을 지우고 감사한다(DB 함수 {@code admin_mfa_reset} 과 같은 동작).</li>
 * </ul>
 */
@Service
public class OperatorMfaService {

    private static final Logger log = LoggerFactory.getLogger(OperatorMfaService.class);

    private final MfaStore store;
    private final OperatorRepository operators;
    private final MfaAttemptService attempts;
    private final AccessDeniedRecorder denied;
    private final AuditLogService audit;
    private final Clock clock;

    public OperatorMfaService(MfaStore store, OperatorRepository operators, MfaAttemptService attempts, AccessDeniedRecorder denied, AuditLogService audit,
                              Clock clock) {
        this.store = store;
        this.operators = operators;
        this.attempts = attempts;
        this.denied = denied;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OperatorMfaView view(String operatorId) {
        OperatorEntity operator = find(operatorId);
        MfaSettings settings = store.settings();
        Instant now = clock.instant();
        Optional<OperatorMfaRow> row = store.find(operator.getId());
        boolean enrolled = row.map(OperatorMfaRow::enrolled).orElse(false);
        Instant graceEndsAt = enrolled ? null : MfaPolicy.graceEndsAt(settings, operator.getCreatedAt());
        return new OperatorMfaView(settings.enabled(), enrolled, enrolled ? row.get().enrolledAt() : null, graceEndsAt,
                !enrolled && MfaPolicy.graceExpired(settings, operator.getCreatedAt(), now), store.countDevices(operator.getId(), now));
    }

    @Transactional
    public void reset(AdminActor actor, String operatorId) {
        if (!actor.root()) {
            denied.record(actor, "SUPER_ONLY", "OPERATOR", operatorId, List.of());
            throw new PermissionDeniedException();
        }
        OperatorEntity operator = find(operatorId);
        long targetId = operator.getId();
        if (targetId == actor.operatorId()) {
            throw new MfaResetSelfException();
        }
        boolean wasEnrolled = store.lock(targetId).map(OperatorMfaRow::enrolled).orElse(false);
        store.deleteMfa(targetId);
        int codes = store.deleteRecoveryCodes(targetId);
        int devices = store.deleteDevices(targetId);
        attempts.reset(targetId);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.MFA_RESET, AuditResult.SUCCESS).target("OPERATOR", Long.toString(targetId))
                .with("via", "ADMIN").with("wasEnrolled", wasEnrolled).with("recoveryCodes", codes).with("trustedDevices", devices));
        log.info("운영자 OTP 를 초기화했다 operatorId={} by operatorId={}", targetId, actor.operatorId());
    }

    private OperatorEntity find(String operatorId) {
        long id;
        try {
            id = Long.parseLong(operatorId);
        } catch (NumberFormatException e) {
            throw new OperatorNotFoundException();
        }
        return operators.findById(id).orElseThrow(OperatorNotFoundException::new);
    }
}
