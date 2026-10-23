package com.community.mfa.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.mfa.dto.MfaSettings;
import com.community.mfa.dto.OperatorMfaRow;
import com.community.mfa.dto.response.MfaMeView;
import com.community.mfa.dto.response.MfaTrustedDeviceView;
import com.community.mfa.exception.MfaCodeReusedException;
import com.community.mfa.exception.MfaDeviceNotFoundException;
import com.community.mfa.exception.MfaNotEnrolledException;
import com.community.mfa.repository.master.MfaStore;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 2차 인증(로그인 세션, 본인 것만 — 대상은 늘 요청한 사람) — 상태 · 복구 코드 새로 만들기 · 신뢰 기기 목록·끊기.
 *
 * <ul>
 *   <li>복구 코드 새로 만들기: 현재 OTP 로 확인(로그인과 같은 실패 키, 받아들인 칸 저장) → 옛 코드 모두 지우고 새 10개 → 감사 {@code MFA_RECOVERY_CODES_REGENERATE}.
 *       등록하지 않았으면 409 {@code MFA_NOT_ENROLLED}.</li>
 *   <li>신뢰 기기: 기간이 남은 것만 보이고 지금 브라우저의 쿠키와 같은 기기가 {@code current}. 끊기는 내 기기만(아니면 404 {@code MFA_DEVICE_NOT_FOUND}) — 감사 {@code MFA_TRUSTED_DEVICE_REVOKE}.</li>
 * </ul>
 */
@Service
public class MfaAccountService {

    private final MfaStore store;
    private final MfaSecrets secrets;
    private final MfaCodeVerifier verifier;
    private final MfaAttemptService attempts;
    private final TrustedDeviceService devices;
    private final OperatorRepository operators;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;
    private final Clock clock;

    public MfaAccountService(MfaStore store, MfaSecrets secrets, MfaCodeVerifier verifier, MfaAttemptService attempts, TrustedDeviceService devices,
                             OperatorRepository operators, SnowflakeIdGenerator ids, AuditLogService audit, Clock clock) {
        this.store = store;
        this.secrets = secrets;
        this.verifier = verifier;
        this.attempts = attempts;
        this.devices = devices;
        this.operators = operators;
        this.ids = ids;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MfaMeView me(AdminActor actor) {
        MfaSettings settings = store.settings();
        Optional<OperatorMfaRow> row = store.find(actor.operatorId());
        boolean enrolled = row.map(OperatorMfaRow::enrolled).orElse(false);
        Instant graceEndsAt = enrolled ? null : MfaPolicy.graceEndsAt(settings, operators.findById(actor.operatorId()).map(OperatorEntity::getCreatedAt).orElse(null));
        return new MfaMeView(settings.enabled(), enrolled, enrolled ? row.get().enrolledAt() : null, enrolled ? row.get().lastUsedAt() : null,
                enrolled ? store.unusedRecoveryCodes(actor.operatorId()) : 0, graceEndsAt, settings.trustDays());
    }

    /** 복구 코드 새로 만들기 — 현재 OTP 확인 */
    @Transactional
    public List<String> regenerateRecoveryCodes(AdminActor actor, String code) {
        long operatorId = actor.operatorId();
        MfaSettings settings = store.settings();
        OperatorMfaRow row = store.lock(operatorId).filter(OperatorMfaRow::enrolled).orElseThrow(MfaNotEnrolledException::new);
        attempts.assertNotLocked(operatorId);
        MfaCodeVerifier.requireOtpFormat(code);
        long step = verifier.verifyTotp(operatorId, secrets.decrypt(row.secretEnc()), row.lastStep(), code, settings.maxFailures(), "RECOVERY_CODES", actor.ip());
        Instant now = clock.instant();
        if (!store.acceptStep(operatorId, step, now)) {
            throw new MfaCodeReusedException();
        }
        List<String> codes = RecoveryCodes.generate();
        List<Long> rowIds = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (String recoveryCode : codes) {
            rowIds.add(ids.nextId());
            hashes.add(RecoveryCodes.hash(operatorId, RecoveryCodes.normalize(recoveryCode).orElseThrow()));
        }
        store.replaceRecoveryCodes(operatorId, rowIds, hashes, now);
        attempts.reset(operatorId);
        audit.record(AuditEvent.byOperator(operatorId, actor.ip(), AuditActions.MFA_RECOVERY_CODES_REGENERATE, AuditResult.SUCCESS)
                .target("OPERATOR", Long.toString(operatorId)).with("recoveryCodes", codes.size()));
        return codes;
    }

    /** 내 신뢰 기기(기간이 남은 것, 최근 차례) */
    @Transactional(readOnly = true)
    public List<MfaTrustedDeviceView> devices(AdminActor actor, String currentToken) {
        return store.devices(actor.operatorId(), TrustedDeviceService.hash(currentToken), clock.instant()).stream()
                .map(row -> new MfaTrustedDeviceView(Long.toString(row.id()), row.agent(), row.ip(), row.trustedAt(), row.expiresAt(), row.lastUsedAt(), row.current()))
                .toList();
    }

    /**
     * 내 신뢰 기기 하나를 끊는다.
     *
     * @return 끊은 기기가 지금 브라우저였으면 true(부르는 쪽이 쿠키도 지운다)
     */
    @Transactional
    public boolean revokeDevice(AdminActor actor, String deviceId, String currentToken) {
        long id;
        try {
            id = Long.parseLong(deviceId);
        } catch (NumberFormatException e) {
            throw new MfaDeviceNotFoundException();
        }
        String hash = store.deviceHash(actor.operatorId(), id).orElseThrow(MfaDeviceNotFoundException::new);
        store.deleteDevice(actor.operatorId(), id);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.MFA_TRUSTED_DEVICE_REVOKE, AuditResult.SUCCESS)
                .target("OPERATOR", Long.toString(actor.operatorId())).with("count", 1).with("all", false).with("cause", "SELF"));
        return hash.equals(TrustedDeviceService.hash(currentToken));
    }

    /** 내 신뢰 기기를 모두 끊는다. 끊은 수 */
    @Transactional
    public int revokeAllDevices(AdminActor actor) {
        return devices.revokeAll(actor.operatorId(), "SELF", actor.operatorId(), actor.ip());
    }
}
