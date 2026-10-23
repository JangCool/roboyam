package com.community.mfa.service;

import com.community.adminauth.service.AdminPasswordMatcher;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.mfa.dto.MfaLoginMethod;
import com.community.mfa.dto.MfaSettings;
import com.community.mfa.dto.OperatorMfaRow;
import com.community.mfa.dto.PendingMfa;
import com.community.mfa.dto.request.MfaPasswordRequest;
import com.community.mfa.dto.response.MfaEnrollmentView;
import com.community.mfa.exception.MfaAlreadyEnrolledException;
import com.community.mfa.exception.MfaEnrollmentNotStartedException;
import com.community.mfa.repository.master.MfaStore;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.profile.exception.InvalidCurrentPasswordException;
import com.community.site.dto.SiteBranding;
import com.community.site.service.SiteAssets;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OTP 등록 — 강제 등록(ENROLL 대기 세션)·자발 등록(로그인 세션)·다시 등록.
 *
 * <ul>
 *   <li><b>시작</b>: 비밀키 20바이트를 새로 만들어 <b>암호화해</b> 등록 중 칸에 두고(키가 없으면 먼저 503 {@code CRYPTO_KEY_MISSING}) 평문 비밀키·otpauth 주소를 돌려준다
 *       (issuer = 사이트 이름, 계정 = 운영자 이메일). 다시 시작하면 등록 중 비밀키가 바뀐다. 자발 등록은 이미 등록했으면 409 {@code MFA_ALREADY_ENROLLED}.</li>
 *   <li><b>확정</b>: 등록 중 비밀키가 없거나 10분이 지났으면 400 {@code MFA_ENROLLMENT_NOT_STARTED}. 코드 확인은 로그인과 같은 실패 키·순서({@link MfaCodeVerifier}).
 *       맞으면 비밀키를 확정하고(확인에 쓴 칸을 마지막 칸으로) 복구 코드 10개를 새로 만들어 돌려준다(해시만 저장) — 감사 {@code MFA_ENROLL}.</li>
 *   <li><b>다시 등록</b>: 현재 비밀번호를 확인하고(틀리면 400 {@code INVALID_CURRENT_PASSWORD}, 감사 실패) 시작과 같다. 확정할 때까지는 옛 비밀키가 그대로 쓰이고,
 *       확정하면 옛 비밀키·복구 코드·<b>신뢰 기기 모두</b> 무효 — 감사 {@code MFA_REENROLL}.</li>
 *   <li>강제 등록 확정은 같은 트랜잭션에서 로그인을 완성한다(감사 {@code LOGIN_SUCCESS} mfa = ENROLLED).</li>
 * </ul>
 * 비밀키·코드·복구 코드를 로그·감사·오류에 싣지 않는다.
 */
@Service
public class MfaEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(MfaEnrollmentService.class);

    /** 등록 중 비밀키의 유효 시간 — 지나면 확정할 수 없고 정리 배치가 버린다 */
    public static final Duration PENDING_VALIDITY = Duration.ofMinutes(10);

    /** 사이트 이름이 없을 때(설치 전 등)의 issuer */
    static final String DEFAULT_ISSUER = "roboyam";

    private final MfaStore store;
    private final MfaSecrets secrets;
    private final MfaCodeVerifier verifier;
    private final MfaAttemptService attempts;
    private final TrustedDeviceService devices;
    private final MfaLoginService logins;
    private final OperatorRepository operators;
    private final AdminPasswordMatcher passwords;
    private final SiteAssets siteAssets;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;
    private final Clock clock;

    public MfaEnrollmentService(MfaStore store, MfaSecrets secrets, MfaCodeVerifier verifier, MfaAttemptService attempts, TrustedDeviceService devices,
                                MfaLoginService logins, OperatorRepository operators, AdminPasswordMatcher passwords, SiteAssets siteAssets, SnowflakeIdGenerator ids,
                                AuditLogService audit, Clock clock) {
        this.store = store;
        this.secrets = secrets;
        this.verifier = verifier;
        this.attempts = attempts;
        this.devices = devices;
        this.logins = logins;
        this.operators = operators;
        this.passwords = passwords;
        this.siteAssets = siteAssets;
        this.ids = ids;
        this.audit = audit;
        this.clock = clock;
    }

    /** 강제 등록 시작(ENROLL 대기 세션) */
    @Transactional
    public MfaEnrollmentView startForced(PendingMfa pending) {
        return start(pending.operatorId(), false);
    }

    /** 자발 등록 시작(로그인 세션) — 이미 등록했으면 409 */
    @Transactional
    public MfaEnrollmentView startVoluntary(AdminActor actor) {
        if (store.find(actor.operatorId()).map(OperatorMfaRow::enrolled).orElse(false)) {
            throw new MfaAlreadyEnrolledException();
        }
        return start(actor.operatorId(), false);
    }

    /** 다시 등록 시작 — 현재 비밀번호 확인 뒤 시작과 같다(등록하지 않았으면 그냥 등록 시작) */
    @Transactional
    public MfaEnrollmentView reenroll(AdminActor actor, MfaPasswordRequest request) {
        String password = request == null ? null : request.password();
        if (password == null || password.isEmpty()) {
            throw ValidationException.of("password", new MessageRef("profile.password.current.required"));
        }
        OperatorEntity operator = operators.findById(actor.operatorId()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        if (!passwords.matches(password, operator.getPasswordHash())) {
            audit.recordIsolated(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.MFA_REENROLL, AuditResult.FAILURE)
                    .target("OPERATOR", Long.toString(actor.operatorId())).with("reason", "CURRENT_PASSWORD_MISMATCH"));
            throw new InvalidCurrentPasswordException();
        }
        boolean enrolled = store.find(actor.operatorId()).map(OperatorMfaRow::enrolled).orElse(false);
        return start(actor.operatorId(), enrolled);
    }

    /** 강제 등록 확정 — 복구 코드를 돌려주고 같은 트랜잭션에서 로그인을 완성한다 */
    @Transactional
    public List<String> confirmForced(PendingMfa pending, String code, String ip, String userAgent) {
        List<String> codes = confirm(pending.operatorId(), code, ip);
        logins.complete(pending.operatorId(), MfaLoginMethod.ENROLLED, ip, userAgent, null);
        return codes;
    }

    /** 자발 등록·다시 등록 확정 */
    @Transactional
    public List<String> confirmVoluntary(AdminActor actor, String code) {
        return confirm(actor.operatorId(), code, actor.ip());
    }

    private MfaEnrollmentView start(long operatorId, boolean reenroll) {
        secrets.requireKey();
        OperatorEntity operator = operators.findById(operatorId).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        String secret = MfaSecrets.newSecret();
        store.startPending(operatorId, secrets.encrypt(secret), reenroll, clock.instant());
        String issuer = siteAssets.branding().map(SiteBranding::siteName).filter(name -> !name.isBlank()).orElse(DEFAULT_ISSUER);
        String account = operator.getEmail();
        log.info("OTP 등록을 시작했다 operatorId={} 다시 등록={}", operatorId, reenroll);
        return new MfaEnrollmentView(secret, MfaSecrets.otpauthUri(issuer, account, secret), issuer, account);
    }

    private List<String> confirm(long operatorId, String code, String ip) {
        MfaSettings settings = store.settings();
        Instant now = clock.instant();
        OperatorMfaRow row = store.lock(operatorId)
                .filter(found -> found.pendingSecretEnc() != null && found.pendingStartedAt() != null && now.isBefore(found.pendingStartedAt().plus(PENDING_VALIDITY)))
                .orElse(null);
        if (row == null) {
            attempts.assertNotLocked(operatorId);
            throw new MfaEnrollmentNotStartedException();
        }
        attempts.assertNotLocked(operatorId);
        MfaCodeVerifier.requireOtpFormat(code);
        long step = verifier.verifyTotp(operatorId, secrets.decrypt(row.pendingSecretEnc()), null, code, settings.maxFailures(), "ENROLL", ip);
        boolean reenroll = row.pendingReenroll() && row.enrolled();

        store.confirmPending(operatorId, step, now);
        List<String> codes = RecoveryCodes.generate();
        List<Long> rowIds = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (String recoveryCode : codes) {
            rowIds.add(ids.nextId());
            hashes.add(RecoveryCodes.hash(operatorId, RecoveryCodes.normalize(recoveryCode).orElseThrow()));
        }
        store.replaceRecoveryCodes(operatorId, rowIds, hashes, now);
        attempts.reset(operatorId);

        AuditEvent event = AuditEvent.byOperator(operatorId, ip, reenroll ? AuditActions.MFA_REENROLL : AuditActions.MFA_ENROLL, AuditResult.SUCCESS)
                .target("OPERATOR", Long.toString(operatorId)).with("recoveryCodes", codes.size());
        if (reenroll) {
            event = event.with("trustedDevicesRevoked", devices.revokeAll(operatorId, "REENROLL", operatorId, ip));
        }
        audit.record(event);
        log.info("OTP 등록을 확정했다 operatorId={} 다시 등록={}", operatorId, reenroll);
        return codes;
    }
}
