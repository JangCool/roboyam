package com.community.profile.service;

import com.community.adminauth.service.AdminPasswordMatcher;
import com.community.adminauth.service.AdminSessionTerminator;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.mfa.service.TrustedDeviceService;
import com.community.notification.service.AdminNotices;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.profile.dto.request.PasswordChangeRequest;
import com.community.profile.exception.InvalidCurrentPasswordException;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 비밀번호 변경 — 로그인한 운영자 <b>본인</b>의 것만(대상은 늘 요청한 사람 {@link AdminActor}).
 *
 * <ul>
 *   <li><b>검사 순서</b>: 입력 형식(현재 비밀번호 비어 있음 {@code fields.currentPassword} · 새 비밀번호 규칙 {@code fields.newPassword} → 400 {@code VALIDATION}) →
 *       현재 비밀번호 대조(틀리면 <b>400</b> {@code INVALID_CURRENT_PASSWORD} — 401 이면 공통 클라이언트가 세션 만료로 보고 로그인 화면으로 보낸다) →
 *       새 비밀번호가 현재와 같으면 400 {@code fields.newPassword}.</li>
 *   <li>성공하면 <b>지금 세션은 유지하고 다른 세션은 모두 끝낸다</b>({@link AdminSessionTerminator}) — 다른 기기에 남은 로그인을 끊는다. 관리자 2차 인증의 신뢰 기기도 모두 끊는다. 감사 {@code PASSWORD_CHANGE}(비밀번호는 남기지 않는다).</li>
 *   <li><b>가정</b>: 현재 비밀번호를 틀린 횟수는 로그인 실패 잠금에 합산하지 않는다(이미 로그인한 세션이 요구되고 요청마다 bcrypt 비용이 든다). 틀린 시도는 감사 {@code PASSWORD_CHANGE}
 *       실패로 남긴다(별도 트랜잭션 — 오류 응답에서도 남는다).</li>
 * </ul>
 * 비밀번호를 로그·오류·감사 기록에 남기지 않는다.
 */
@Service
public class PasswordChangeService {

    private static final Logger log = LoggerFactory.getLogger(PasswordChangeService.class);

    private final OperatorRepository operators;
    private final AdminPasswordMatcher matcher;
    private final PasswordEncoder encoder;
    private final AdminSessionTerminator sessions;
    private final AuditLogService audit;
    private final AdminNotices notices;
    private final TrustedDeviceService trustedDevices;

    public PasswordChangeService(OperatorRepository operators, AdminPasswordMatcher matcher, PasswordEncoder encoder, AdminSessionTerminator sessions,
                                 AuditLogService audit, AdminNotices notices, TrustedDeviceService trustedDevices) {
        this.operators = operators;
        this.matcher = matcher;
        this.encoder = encoder;
        this.sessions = sessions;
        this.audit = audit;
        this.notices = notices;
        this.trustedDevices = trustedDevices;
    }

    /**
     * @param currentSessionId 지금 요청의 세션 ID — 이 세션만 남기고 다른 세션을 끝낸다
     */
    @Transactional
    public void change(AdminActor actor, PasswordChangeRequest request, String currentSessionId) {
        String current = request == null ? null : request.currentPassword();
        String next = request == null ? null : request.newPassword();
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        if (current == null || current.isEmpty()) {
            invalid.put("currentPassword", new MessageRef("profile.password.current.required"));
        }
        if (next == null || next.isEmpty()) {
            invalid.put("newPassword", new MessageRef("user.password.required"));
        } else {
            UserPolicy.passwordViolation(next).ifPresent(message -> invalid.put("newPassword", message));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }

        OperatorEntity operator = operators.findWithLockById(actor.operatorId()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        if (!matcher.matches(current, operator.getPasswordHash())) {
            // 업무 트랜잭션은 이 예외로 롤백되므로 실패 기록은 따로 남긴다(감사 표는 다른 표와 잠금을 다투지 않는다)
            audit.recordIsolated(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.PASSWORD_CHANGE, AuditResult.FAILURE)
                    .target("OPERATOR", Long.toString(actor.operatorId())).with("reason", "CURRENT_PASSWORD_MISMATCH"));
            throw new InvalidCurrentPasswordException();
        }
        if (current.equals(next)) {
            throw ValidationException.of("newPassword", new MessageRef("profile.password.new.same"));
        }

        operator.changePassword(encoder.encode(next), Instant.now());
        int ended = sessions.endOthers(actor.operatorId(), currentSessionId);
        trustedDevices.revokeAll(actor.operatorId(), "PASSWORD_CHANGE", actor.operatorId(), actor.ip()); // 관리자 2차 인증의 신뢰 기기를 모두 끊는다
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.PASSWORD_CHANGE, AuditResult.SUCCESS)
                .target("OPERATOR", Long.toString(actor.operatorId())).with("sessionsEnded", ended));
        notices.passwordChanged(actor.operatorId());
        log.info("비밀번호를 바꿨다 operatorId={} 끝낸 다른 세션={}개", actor.operatorId(), ended);
    }
}
