package com.community.mfa.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.mfa.dto.MfaLoginMethod;
import com.community.mfa.dto.MfaSettings;
import com.community.mfa.dto.MfaVerifyResult;
import com.community.mfa.dto.OperatorMfaRow;
import com.community.mfa.dto.PendingMfa;
import com.community.mfa.dto.request.MfaVerifyRequest;
import com.community.mfa.exception.MfaCodeReusedException;
import com.community.mfa.exception.MfaPendingExpiredException;
import com.community.mfa.repository.master.MfaStore;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 2단계(VERIFY 대기) — OTP 또는 복구 코드 하나로 확인하고 로그인을 완성한다.
 *
 * <ul>
 *   <li>둘 다 보내거나 둘 다 비우면 400 {@code VALIDATION}({@code fields.code}). OTP 형식(숫자 6자리)·복구 코드 형식({@code XXXXX-XXXXX}, 대소문자·하이픈 무시)이 아니면
 *       400 {@code VALIDATION} — 실패 횟수에 세지 않는다.</li>
 *   <li>OTP: {@link MfaCodeVerifier} 의 순서 + 받아들인 칸 저장(저장된 칸보다 클 때만 — 동시에 같은 코드를 낸 두 요청 중 하나만 이긴다).</li>
 *   <li>복구 코드: 쓰지 않은 그 코드를 쓴 것으로 표시(한 번만) → 감사 {@code MFA_RECOVERY_CODE_USED}(남은 개수) → 응답에 {@code recoveryCodesLeft}.
 *       틀리거나 이미 쓴 코드는 실패 횟수 +1, 400 {@code RECOVERY_CODE_INVALID}.</li>
 *   <li>통과: 실패 횟수를 지우고, 「이 기기에서 다시 묻지 않기」면 신뢰 기기를 만들고, 로그인을 완성한다(감사 {@code LOGIN_SUCCESS} mfa = TOTP·RECOVERY_CODE).</li>
 *   <li>대기 중에 OTP 가 초기화돼 등록이 없으면 401 {@code MFA_PENDING_EXPIRED}(처음부터 다시 로그인 — 판정이 다시 된다).</li>
 * </ul>
 */
@Service
public class MfaVerifyService {

    private final MfaStore store;
    private final MfaSecrets secrets;
    private final MfaCodeVerifier verifier;
    private final MfaAttemptService attempts;
    private final TrustedDeviceService devices;
    private final MfaLoginService logins;
    private final AuditLogService audit;
    private final Clock clock;

    public MfaVerifyService(MfaStore store, MfaSecrets secrets, MfaCodeVerifier verifier, MfaAttemptService attempts, TrustedDeviceService devices,
                            MfaLoginService logins, AuditLogService audit, Clock clock) {
        this.store = store;
        this.secrets = secrets;
        this.verifier = verifier;
        this.attempts = attempts;
        this.devices = devices;
        this.logins = logins;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * @param userAgent 요청의 User-Agent 원문(요약만 남긴다)
     */
    @Transactional
    public MfaVerifyResult verify(PendingMfa pending, MfaVerifyRequest request, String ip, String userAgent) {
        long operatorId = pending.operatorId();
        String code = request == null ? null : blankToNull(request.code());
        String recoveryCode = request == null ? null : blankToNull(request.recoveryCode());
        if ((code == null) == (recoveryCode == null)) {
            throw ValidationException.of("code", new MessageRef("mfa.verify.oneOf"));
        }

        MfaSettings settings = store.settings();
        OperatorMfaRow row = store.lock(operatorId).filter(OperatorMfaRow::enrolled).orElseThrow(MfaPendingExpiredException::new);
        Instant now = clock.instant();
        MfaLoginMethod method;
        Integer left = null;
        if (code != null) {
            attempts.assertNotLocked(operatorId);
            MfaCodeVerifier.requireOtpFormat(code);
            long step = verifier.verifyTotp(operatorId, secrets.decrypt(row.secretEnc()), row.lastStep(), code, settings.maxFailures(), "LOGIN", ip);
            if (!store.acceptStep(operatorId, step, now)) {
                throw new MfaCodeReusedException();
            }
            method = MfaLoginMethod.TOTP;
        } else {
            attempts.assertNotLocked(operatorId);
            String normalized = RecoveryCodes.normalize(recoveryCode)
                    .orElseThrow(() -> ValidationException.of("recoveryCode", new MessageRef("mfa.recoveryCode.format")));
            if (!store.useRecoveryCode(operatorId, RecoveryCodes.hash(operatorId, normalized), now)) {
                throw verifier.recoveryCodeFailed(operatorId, settings.maxFailures(), ip);
            }
            store.touchUsed(operatorId, now);
            left = store.unusedRecoveryCodes(operatorId);
            audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.MFA_RECOVERY_CODE_USED, AuditResult.SUCCESS).target("OPERATOR", Long.toString(operatorId))
                    .with("recoveryCodesLeft", left));
            method = MfaLoginMethod.RECOVERY_CODE;
        }

        attempts.reset(operatorId);
        String token = request.rememberDevice() != null && request.rememberDevice() ? devices.trust(operatorId, settings.trustDays(), ip, userAgent) : null;
        logins.complete(operatorId, method, ip, userAgent, null);
        return new MfaVerifyResult(method, left, token, settings.trustDays());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
