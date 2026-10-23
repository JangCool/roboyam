package com.community.mfa.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.mfa.exception.MfaCodeInvalidException;
import com.community.mfa.exception.MfaCodeReusedException;
import com.community.mfa.exception.RecoveryCodeInvalidException;
import com.community.mfa.totp.Totp;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 2차 인증 코드 확인의 공통 순서 — 잠금 확인 → 형식(400 {@code VALIDATION}, 횟수에 세지 않음) → TOTP 대조 → 실패면 횟수·감사·오류.
 * 로그인 2단계·등록 확정·복구 코드 새로 만들기가 같이 쓴다(같은 실패 키 — {@link MfaAttemptService}).
 *
 * <ul>
 *   <li>틀림: 실패 횟수 +1(독립 트랜잭션) → 감사 {@code MFA_FAILURE}(reason CODE_INVALID) → 400 {@code MFA_CODE_INVALID}(+remainingAttempts), 한도에 닿으면 429 {@code MFA_LOCKED}.</li>
 *   <li>재사용(받아들인 칸 이하): 횟수에 세지 않고 감사(CODE_REUSED) → 400 {@code MFA_CODE_REUSED}.</li>
 *   <li>맞음: 받아들인 칸을 돌려준다 — 저장·횟수 지우기는 부르는 쪽(같은 트랜잭션).</li>
 * </ul>
 * 실패 감사는 업무 트랜잭션이 오류로 롤백돼도 남도록 {@code recordIsolated}. 코드·비밀키는 감사·로그·오류에 싣지 않는다.
 */
@Component
public class MfaCodeVerifier {

    private static final Pattern OTP = Pattern.compile("^[0-9]{" + Totp.DIGITS + "}$");

    private final MfaAttemptService attempts;
    private final AuditLogService audit;
    private final Clock clock;

    public MfaCodeVerifier(MfaAttemptService attempts, AuditLogService audit, Clock clock) {
        this.attempts = attempts;
        this.audit = audit;
        this.clock = clock;
    }

    /** OTP 형식(숫자 6자리)이 아니면 400 {@code VALIDATION}({@code fields.code}) */
    public static void requireOtpFormat(String code) {
        if (code == null || !OTP.matcher(code).matches()) {
            throw ValidationException.of("code", new MessageRef("mfa.code.format"));
        }
    }

    /**
     * TOTP 를 확인한다.
     *
     * @param secret   비밀키 바이트
     * @param lastStep 마지막으로 받아들인 칸(등록 확정이면 null)
     * @param stage    감사 {@code detail.stage}(LOGIN · ENROLL · RECOVERY_CODES)
     * @return 받아들인 칸
     */
    public long verifyTotp(long operatorId, byte[] secret, Long lastStep, String code, int maxFailures, String stage, String ip) {
        attempts.assertNotLocked(operatorId);
        requireOtpFormat(code);
        Totp.Match match = Totp.verify(secret, code, clock.instant(), lastStep);
        switch (match.result()) {
            case ACCEPTED -> {
                return match.step();
            }
            case REUSED -> {
                recordFailureAudit(operatorId, ip, "CODE_REUSED", stage);
                throw new MfaCodeReusedException();
            }
            default -> {
                recordFailureAudit(operatorId, ip, "CODE_INVALID", stage);
                int remaining = attempts.recordFailure(operatorId, maxFailures);
                throw new MfaCodeInvalidException(remaining);
            }
        }
    }

    /** 복구 코드가 맞지 않거나 이미 썼다 — 횟수 +1, 감사, 400 {@code RECOVERY_CODE_INVALID}(한도면 429) */
    public RecoveryCodeInvalidException recoveryCodeFailed(long operatorId, int maxFailures, String ip) {
        recordFailureAudit(operatorId, ip, "RECOVERY_CODE_INVALID", "LOGIN");
        attempts.recordFailure(operatorId, maxFailures);
        return new RecoveryCodeInvalidException();
    }

    private void recordFailureAudit(long operatorId, String ip, String reason, String stage) {
        audit.recordIsolated(AuditEvent.byOperator(operatorId, ip, AuditActions.MFA_FAILURE, AuditResult.FAILURE).target("OPERATOR", Long.toString(operatorId))
                .with("reason", reason).with("stage", stage));
    }
}
