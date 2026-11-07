package com.community.mfa;

import com.community.mfa.dto.MfaSettings;
import com.community.mfa.dto.OperatorMfaRow;
import com.community.mfa.entity.AdminSecuritySettingEntity;
import com.community.mfa.entity.OperatorMfaEntity;
import com.community.mfa.entity.OperatorTrustedDeviceEntity;
import com.community.mfa.entity.QAdminSecuritySettingEntity;
import com.community.mfa.entity.QOperatorMfaEntity;
import com.community.mfa.entity.QOperatorMfaRecoveryCodeEntity;
import com.community.mfa.entity.QOperatorTrustedDeviceEntity;
import com.community.mfa.repository.master.MfaStore;
import com.community.zconfiguration.jpa.JpaMasterConfig;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 2차 인증 표(admin 스트림 V3)의 시험 준비·확인 — admin 모듈의 엔티티·QueryDSL 로 한다(호출마다 새 트랜잭션을 열고 커밋한다 — 시험은 서비스·MockMvc 요청이 다른 연결로 읽는다).
 * 운영 코드와 같은 읽기는 {@link MfaStore} 를 그대로 부른다.
 */
public final class MfaFixture {

    private static final QAdminSecuritySettingEntity SETTING = QAdminSecuritySettingEntity.adminSecuritySettingEntity;
    private static final QOperatorMfaEntity MFA = QOperatorMfaEntity.operatorMfaEntity;
    private static final QOperatorMfaRecoveryCodeEntity CODE = QOperatorMfaRecoveryCodeEntity.operatorMfaRecoveryCodeEntity;
    private static final QOperatorTrustedDeviceEntity DEVICE = QOperatorTrustedDeviceEntity.operatorTrustedDeviceEntity;

    /** 신뢰 기기 한 대(쿠키 해시는 앞뒤 공백을 지운 값) */
    public record Device(long id, String tokenHash, String agent, String ip, Instant trustedAt, Instant expiresAt) {
    }

    private final TransactionTemplate tx;
    private final EntityManager entityManager;
    private final JPAQueryFactory query;
    private final MfaStore store;

    public MfaFixture(ApplicationContext context) {
        this.tx = new TransactionTemplate(context.getBean(JpaMasterConfig.TRANSACTION_MANAGER, PlatformTransactionManager.class));
        this.entityManager = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(JpaMasterConfig.ENTITY_MANAGER, EntityManagerFactory.class));
        this.query = context.getBean(JpaMasterConfig.QUERY_FACTORY, JPAQueryFactory.class);
        this.store = context.getBean(MfaStore.class);
    }

    // ── 설정(1행) ───────────────────────────────────────────────────────────

    public MfaSettings settings() {
        return call(store::settings);
    }

    /** 2차 인증 사용만 바꾼다 */
    public void mfaEnabled(boolean enabled) {
        run(() -> query.update(SETTING).set(SETTING.mfaEnabled, enabled).where(SETTING.id.eq(AdminSecuritySettingEntity.ROW_ID)).execute());
    }

    /** 사용 · 유예 · 기기 신뢰 개월 · 실패 한도 · 켠 시각(켠 사람·고친 사람은 그대로) */
    public void settings(boolean enabled, int graceDays, int trustMonths, int maxFailures, Instant enabledAt) {
        run(() -> query.update(SETTING).set(SETTING.mfaEnabled, enabled).set(SETTING.graceDays, graceDays).set(SETTING.trustMonths, trustMonths)
                .set(SETTING.maxFailures, maxFailures).set(SETTING.mfaEnabledAt, enabledAt).where(SETTING.id.eq(AdminSecuritySettingEntity.ROW_ID)).execute());
    }

    /** 읽어 둔 설정으로 되돌린다(고친 사람·시각은 그대로) */
    public void restore(MfaSettings saved) {
        run(() -> query.update(SETTING).set(SETTING.mfaEnabled, saved.enabled()).set(SETTING.graceDays, saved.graceDays()).set(SETTING.trustMonths, saved.trustMonths())
                .set(SETTING.maxFailures, saved.maxFailures()).set(SETTING.mfaEnabledAt, saved.enabledAt()).set(SETTING.mfaEnabledBy, saved.enabledBy())
                .where(SETTING.id.eq(AdminSecuritySettingEntity.ROW_ID)).execute());
    }

    // ── OTP ─────────────────────────────────────────────────────────────

    /**
     * 바로 등록한다(비밀키 암호문) — 행이 있으면 확정 비밀키·등록 시각을 바꾸고 받아들인 칸·등록 중 비밀키를 지운다, 없으면 등록된 행을 넣는다.
     */
    public void enroll(long operatorId, String secretEnc) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        run(() -> {
            long updated = query.update(MFA).set(MFA.secretEnc, secretEnc).set(MFA.enrolledAt, now).setNull(MFA.lastStep).setNull(MFA.pendingSecretEnc)
                    .setNull(MFA.pendingStartedAt).where(MFA.operatorId.eq(operatorId)).execute();
            if (updated == 0) {
                entityManager.persist(OperatorMfaEntity.enrolled(operatorId, secretEnc, now));
            }
        });
    }

    /** OTP 행과 복구 코드를 지운다(신뢰 기기는 그대로) */
    public void unenroll(long operatorId) {
        run(() -> {
            query.delete(MFA).where(MFA.operatorId.eq(operatorId)).execute();
            query.delete(CODE).where(CODE.operatorId.eq(operatorId)).execute();
        });
    }

    /** OTP 행(없으면 비어 있다) */
    public Optional<OperatorMfaRow> mfa(long operatorId) {
        return call(() -> store.find(operatorId));
    }

    // ── 복구 코드 ─────────────────────────────────────────────────────────

    public List<String> recoveryCodeHashes(long operatorId) {
        return call(() -> query.select(CODE.codeHash).from(CODE).where(CODE.operatorId.eq(operatorId)).fetch());
    }

    public int recoveryCodes(long operatorId) {
        return call(() -> query.select(CODE.count()).from(CODE).where(CODE.operatorId.eq(operatorId)).fetchOne()).intValue();
    }

    public int usedRecoveryCodes(long operatorId) {
        return call(() -> query.select(CODE.count()).from(CODE).where(CODE.operatorId.eq(operatorId), CODE.usedAt.isNotNull()).fetchOne()).intValue();
    }

    // ── 신뢰 기기 ─────────────────────────────────────────────────────────

    /** 기기를 바로 믿는다(요약·IP 없음) */
    public void trustDevice(long id, long operatorId, String tokenHash, Instant trustedAt, Instant expiresAt) {
        run(() -> entityManager.persist(OperatorTrustedDeviceEntity.create(id, operatorId, tokenHash, null, null, trustedAt.truncatedTo(ChronoUnit.MICROS),
                expiresAt.truncatedTo(ChronoUnit.MICROS))));
    }

    /** 그 운영자의 기기 수(기간이 끝난 것 포함) */
    public int deviceCount(long operatorId) {
        return call(() -> query.select(DEVICE.count()).from(DEVICE).where(DEVICE.operatorId.eq(operatorId)).fetchOne()).intValue();
    }

    /** 그 운영자의 기기들(기간이 끝난 것 포함, 차례 없음 — 한 대일 때 쓴다) */
    public List<Device> devices(long operatorId) {
        return call(() -> query.select(DEVICE.id, DEVICE.tokenHash, DEVICE.agent, DEVICE.ip, DEVICE.trustedAt, DEVICE.expiresAt).from(DEVICE)
                .where(DEVICE.operatorId.eq(operatorId)).fetch().stream()
                .map(t -> new Device(t.get(DEVICE.id), t.get(DEVICE.tokenHash).trim(), t.get(DEVICE.agent), t.get(DEVICE.ip), t.get(DEVICE.trustedAt),
                        t.get(DEVICE.expiresAt)))
                .toList());
    }

    /** 그 운영자의 기기 하나 — 쿠키 해시로 */
    public Optional<Long> deviceId(long operatorId, String tokenHash) {
        return Optional.ofNullable(call(() -> query.select(DEVICE.id).from(DEVICE).where(DEVICE.operatorId.eq(operatorId), DEVICE.tokenHash.eq(tokenHash)).fetchFirst()));
    }

    private void run(Runnable work) {
        tx.executeWithoutResult(status -> {
            work.run();
            entityManager.flush();
        });
    }

    private <T> T call(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }
}
