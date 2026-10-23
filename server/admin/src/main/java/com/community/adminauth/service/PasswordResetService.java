package com.community.adminauth.service;

import com.community.adminauth.config.AdminPasswordResetProperties;
import com.community.adminauth.dto.OperatorPrincipal;
import com.community.adminauth.exception.CertCodeException;
import com.community.adminauth.exception.CertCodeExhaustedException;
import com.community.adminauth.exception.CertCodeExpiredException;
import com.community.adminauth.exception.CertCodeInvalidException;
import com.community.adminauth.exception.CertCodeNotFoundException;
import com.community.adminauth.exception.CertCodeUsedException;
import com.community.adminauth.exception.PasswordResetNotAllowedException;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.mfa.service.TrustedDeviceService;
import com.community.notification.service.AdminNotices;
import com.community.operator.entity.OperatorCertCodeEntity;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorCertCodeRepository;
import com.community.operator.repository.master.OperatorRepository;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정 — 이메일 + 승인번호 + 새 비밀번호를 한 번에 받아 비밀번호를 바꾼다(로그인 전 공개 API, 하모니카3 방식). 성공하면 호출하는 쪽(컨트롤러)이 곧바로 로그인시킨다.
 *
 * <ul>
 *   <li><b>검사 순서</b>: 입력 형식(이메일·승인번호 6자리 숫자·새 비밀번호 규칙 → 400 {@code VALIDATION}, 승인번호 시도 횟수를 쓰지 않는다) →
 *       승인번호(받은 적 없음 {@code CERT_CODE_NOT_FOUND} → 이미 씀 → 만료 → 소진 → 틀림 {@code CERT_CODE_INVALID}, 모두 400) → 계정 상태(403).</li>
 *   <li><b>무차별 대입 방어</b>: 번호 하나로 5번 틀리면 소진돼 새로 받아야 한다(새로 받기는 이메일 쿨다운·시간당 한도에 걸린다 — 시간당 최대 25번 추측). 틀린 횟수는 오류 응답에서도
 *       남는다({@code noRollbackFor}). 맞는 번호도 한 번 쓰면 못 쓴다. 행 잠금으로 같은 번호의 동시 시도를 줄 세운다.</li>
 *   <li><b>계정 열거 방어</b>: 없는 이메일·대상이 아닌 계정은 승인번호 요청 때 같은 모양의 <b>가짜 행</b>이 만들어져({@link PasswordResetCodeService}) 확인 응답(없음/틀림/소진/만료)이
 *       실제 계정과 같은 순서로 같은 문구·같은 작업량이다. 계정을 조회하는 일도 모든 경로에서 똑같이 한다. 새 비밀번호 해시(느린 연산)는 승인번호가 맞은 뒤에만 한다.</li>
 *   <li><b>성공하면</b>: 비밀번호 해시 교체 → 승인번호 소진 → <b>그 운영자의 모든 세션 종료</b> → <b>관리자 2차 인증 신뢰 기기 모두 끊기</b> → 로그인 실패 횟수·잠금 초기화 → 감사 {@code PASSWORD_RESET}(번호·비밀번호는 남기지 않는다). 로그인 완성({@code LOGIN_SUCCESS} via PASSWORD_RESET)은 2차 인증 판정 뒤({@code AdminLoginFinisher}).
 *       계정이 잠금·미승인·만료·사용 중지·기간 밖이면 거부한다(403, 번호는 쓰지 않은 채 둔다).</li>
 * </ul>
 * 이메일·승인번호·비밀번호를 로그에 남기지 않는다.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private static final Pattern CODE_FORMAT = Pattern.compile("^[0-9]{" + AdminPasswordResetProperties.CODE_LENGTH + "}$");

    private final OperatorRepository operators;
    private final OperatorCertCodeRepository certs;
    private final PasswordEncoder encoder;
    private final AdminLoginAttemptService attempts;
    private final AdminSessionTerminator sessions;
    private final AdminPasswordResetProperties props;
    private final AuditLogService audit;
    private final AdminNotices notices;
    private final TrustedDeviceService trustedDevices;

    public PasswordResetService(OperatorRepository operators, OperatorCertCodeRepository certs, PasswordEncoder encoder, AdminLoginAttemptService attempts,
                                AdminSessionTerminator sessions, AdminPasswordResetProperties props, AuditLogService audit, AdminNotices notices,
                                TrustedDeviceService trustedDevices) {
        this.operators = operators;
        this.certs = certs;
        this.encoder = encoder;
        this.attempts = attempts;
        this.sessions = sessions;
        this.props = props;
        this.audit = audit;
        this.notices = notices;
        this.trustedDevices = trustedDevices;
    }

    /**
     * 승인번호를 확인하고 비밀번호를 바꾼다.
     *
     * @param ip 요청자 IP(감사 기록용)
     * @param userAgent 요청의 User-Agent 원문 — 로그인 감사는 2차 인증 판정 뒤에 남겨 여기서는 쓰지 않는다(호출 모양을 유지한다). 없으면 null
     * @return 로그인시킬 주체
     * @throws ValidationException               입력 형식이 틀렸을 때(400 {@code fields.email|certCode|newPassword})
     * @throws CertCodeException                 승인번호를 쓸 수 없을 때(400 {@code CERT_CODE_*})
     * @throws PasswordResetNotAllowedException  계정이 로그인할 수 없는 상태일 때(403)
     */
    @Transactional(noRollbackFor = CertCodeException.class)
    public OperatorPrincipal reset(String rawEmail, String certCode, String newPassword, String ip, String userAgent) {
        String email = UserPolicy.normalizeEmail(rawEmail);
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        UserPolicy.emailViolation(email).ifPresent(message -> invalid.put("email", message));
        if (certCode == null || !CODE_FORMAT.matcher(certCode).matches()) {
            invalid.put("certCode", new MessageRef("operator.certCode.format", AdminPasswordResetProperties.CODE_LENGTH));
        }
        if (newPassword == null || newPassword.isEmpty()) {
            invalid.put("newPassword", new MessageRef("user.password.required"));
        } else {
            UserPolicy.passwordViolation(newPassword).ifPresent(message -> invalid.put("newPassword", message));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }

        Instant now = Instant.now();
        OperatorCertCodeEntity cert = certs.findWithLockByEmailKey(PasswordResetCodeService.certKey(email)).orElseThrow(CertCodeNotFoundException::new);
        Optional<OperatorEntity> found = operators.findByEmail(email); // 가짜 행·실제 행 어느 쪽이든 같은 일을 하게 한다(계정 열거·시간 차이 방어)
        if (cert.isUsed()) {
            throw new CertCodeUsedException();
        }
        if (cert.isExpired(now)) {
            throw new CertCodeExpiredException();
        }
        if (cert.isExhausted(props.maxFailures())) {
            throw new CertCodeExhaustedException();
        }

        Long operatorId = cert.getOperatorId();
        boolean matched = cert.matches(certCode) && operatorId != null && found.map(OperatorEntity::getId).filter(operatorId::equals).isPresent();
        if (!matched) {
            cert.fail();
            int left = props.maxFailures() - cert.getFailCount();
            if (left <= 0) {
                if (operatorId != null) {
                    audit.record(AuditEvent.anonymous(ip, AuditActions.PASSWORD_RESET, AuditResult.FAILURE).actor(operatorId).target("OPERATOR", Long.toString(operatorId))
                            .with("reason", "CODE_EXHAUSTED"));
                    log.info("비밀번호 재설정 승인번호를 너무 많이 틀려 소진됐다 operatorId={}", operatorId);
                }
                throw new CertCodeExhaustedException();
            }
            throw new CertCodeInvalidException(left);
        }

        OperatorEntity operator = operators.findWithLockById(operatorId).orElseThrow(CertCodeNotFoundException::new);
        if (!operator.canLogin(now)) {
            // 403 으로 끝나 업무 트랜잭션이 롤백되므로 거부 기록은 따로 남긴다
            audit.recordIsolated(AuditEvent.anonymous(ip, AuditActions.PASSWORD_RESET, AuditResult.DENIED).actor(operatorId).target("OPERATOR", Long.toString(operatorId))
                    .with("reason", "NOT_ALLOWED"));
            throw new PasswordResetNotAllowedException();
        }

        operator.changePassword(encoder.encode(newPassword), now);
        cert.markUsed(now);
        int ended = sessions.endAll(operatorId);
        int devices = trustedDevices.revokeAll(operatorId, "PASSWORD_RESET", operatorId, ip); // 관리자 2차 인증의 신뢰 기기를 모두 끊는다
        attempts.resetFailures(email); // 로그인 실패 횟수·잠금을 지운다. 로그인 완성(감사 LOGIN_SUCCESS via PASSWORD_RESET·마지막 로그인 시각)은 2차 인증 판정 뒤 컨트롤러 쪽이 한다

        String target = Long.toString(operatorId);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.PASSWORD_RESET, AuditResult.SUCCESS).target("OPERATOR", target).with("sessionsEnded", ended)
                .with("trustedDevicesRevoked", devices));
        notices.passwordReset(operatorId);
        log.info("비밀번호를 재설정했다 operatorId={} 끝낸 세션={}개", operatorId, ended);
        return new OperatorPrincipal(operatorId);
    }
}
