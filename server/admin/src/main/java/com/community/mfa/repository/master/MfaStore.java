package com.community.mfa.repository.master;

import com.community.mfa.dto.MfaOperatorStatus;
import com.community.mfa.dto.MfaSettings;
import com.community.mfa.dto.OperatorMfaRow;
import com.community.mfa.dto.TrustedDeviceRow;
import com.community.mfa.entity.AdminSecuritySettingEntity;
import com.community.mfa.entity.OperatorMfaRecoveryCodeEntity;
import com.community.mfa.entity.OperatorTrustedDeviceEntity;
import com.community.mfa.entity.QAdminSecuritySettingEntity;
import com.community.mfa.entity.QOperatorMfaEntity;
import com.community.mfa.entity.QOperatorMfaRecoveryCodeEntity;
import com.community.mfa.entity.QOperatorTrustedDeviceEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 관리자 2차 인증 표(admin 스트림 V3 — {@code admin_security_settings} · {@code operator_mfa} · {@code operator_mfa_recovery_codes} · {@code operator_trusted_devices}) 읽기·쓰기.
 * 엔티티는 admin 모듈에 둔다({@code com.community.mfa.entity} — shared 에 두면 이 표가 없는 공개 서버(api)의 엔티티 목록에 들어간다).
 *
 * <p><b>트랜잭션은 부르는 서비스가 연다</b>(행 잠금·대량 UPDATE 는 트랜잭션 안에서만 된다). 동시 요청의 재사용은 조건부 UPDATE({@code last_step < ?}, {@code used_at IS NULL})
 * 한 문장의 처리 건수로 막는다 — 엔티티를 읽고 고치는 방식으로 바꾸지 않는다. 비밀키는 암호문으로만 오가고, 복구 코드·쿠키는 해시만 다룬다.
 *
 * <p>읽기는 엔티티가 아니라 칸을 골라 읽는다 — 같은 트랜잭션의 대량 UPDATE 뒤에도 1차 캐시의 옛 값을 보지 않는다. 잠금 읽기({@link #lock}·{@link #lockSettings})만 엔티티를 읽는다.
 * 시각은 DB 가 마이크로초라 넘기기 전에 자른다(반올림되지 않게 — 예전 JDBC 구현과 같다).
 *
 * <p>네이티브 SQL 로 남긴 것: 설정 기본 행 넣기·등록 시작({@code ON CONFLICT} — 사유는 메서드 주석).
 */
@Repository
public class MfaStore extends RoboyamRepositoryQDSL {

    private static final QAdminSecuritySettingEntity SETTING = QAdminSecuritySettingEntity.adminSecuritySettingEntity;
    private static final QOperatorMfaEntity MFA = QOperatorMfaEntity.operatorMfaEntity;
    private static final QOperatorMfaRecoveryCodeEntity CODE = QOperatorMfaRecoveryCodeEntity.operatorMfaRecoveryCodeEntity;
    private static final QOperatorTrustedDeviceEntity DEVICE = QOperatorTrustedDeviceEntity.operatorTrustedDeviceEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;

    private final EntityManager entityManager;

    public MfaStore(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    // ── 설정 ─────────────────────────────────────────────────────────────────

    /** 설정 행(마이그레이션이 넣는다 — 없으면 기본값 행을 만든다) */
    public MfaSettings settings() {
        Tuple row = query.select(SETTING.mfaEnabled, SETTING.graceDays, SETTING.trustMonths, SETTING.maxFailures, SETTING.mfaEnabledAt, SETTING.mfaEnabledBy)
                .from(SETTING).where(SETTING.id.eq(AdminSecuritySettingEntity.ROW_ID)).fetchOne();
        if (row == null) {
            insertDefaultSettings();
            return settings();
        }
        return new MfaSettings(Boolean.TRUE.equals(row.get(SETTING.mfaEnabled)), nz(row.get(SETTING.graceDays)), nz(row.get(SETTING.trustMonths)),
                nz(row.get(SETTING.maxFailures)), row.get(SETTING.mfaEnabledAt), row.get(SETTING.mfaEnabledBy));
    }

    /**
     * 설정 행을 쓰기 잠금으로 읽는다({@code PESSIMISTIC_WRITE} = {@code FOR NO KEY UPDATE} — 이 표를 FK 로 가리키는 표가 없다) — 동시 저장을 줄 세운다. 이어서
     * {@link #saveSettings} 가 이 엔티티를 고친다. 저장의 첫 읽기라 1차 캐시에 낡은 값이 없다.
     */
    public MfaSettings lockSettings() {
        AdminSecuritySettingEntity row = query.selectFrom(SETTING).where(SETTING.id.eq(AdminSecuritySettingEntity.ROW_ID)).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        if (row == null) {
            insertDefaultSettings();
            return lockSettings();
        }
        return new MfaSettings(row.isMfaEnabled(), row.getGraceDays(), row.getTrustMonths(), row.getMaxFailures(), row.getMfaEnabledAt(), row.getMfaEnabledBy());
    }

    /** 설정을 바꾼다(변경 감지 — 커밋 때 UPDATE). 행이 없으면 아무 일도 하지 않는다(예전 UPDATE 0건과 같다) */
    public void saveSettings(MfaSettings next, long updatedBy, Instant now) {
        AdminSecuritySettingEntity row = entityManager.find(AdminSecuritySettingEntity.class, AdminSecuritySettingEntity.ROW_ID);
        if (row != null) {
            row.change(next.enabled(), next.graceDays(), next.trustMonths(), next.maxFailures(), micros(next.enabledAt()), next.enabledBy(), updatedBy, micros(now));
        }
    }

    /**
     * 기본값 행(id = 1)을 넣는다 — 이미 있으면 아무것도 하지 않는다.
     *
     * <p>Native SQL 인 이유: {@code INSERT ... ON CONFLICT DO NOTHING} 은 JPQL·QueryDSL 에 대응 문법이 없다. 칸 기본값(꺼짐·14일·1개월·5회)은 표 정의가 정한다.
     */
    private void insertDefaultSettings() {
        entityManager.createNativeQuery("INSERT INTO admin_security_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING").executeUpdate();
    }

    // ── 운영자 OTP ─────────────────────────────────────────────────────────────

    public Optional<OperatorMfaRow> find(long operatorId) {
        Tuple row = query.select(MFA.operatorId, MFA.secretEnc, MFA.enrolledAt, MFA.lastStep, MFA.lastUsedAt, MFA.pendingSecretEnc, MFA.pendingStartedAt,
                MFA.pendingReenroll).from(MFA).where(MFA.operatorId.eq(operatorId)).fetchOne();
        return row == null ? Optional.empty() : Optional.of(new OperatorMfaRow(row.get(MFA.operatorId), row.get(MFA.secretEnc), row.get(MFA.enrolledAt),
                row.get(MFA.lastStep), row.get(MFA.lastUsedAt), row.get(MFA.pendingSecretEnc), row.get(MFA.pendingStartedAt),
                Boolean.TRUE.equals(row.get(MFA.pendingReenroll))));
    }

    /**
     * 그 운영자의 OTP 행을 쓰기 잠금으로 읽는다(등록 확정·코드 확인을 줄 세운다). 잠금은 OTP 표만 고르는 한 문장({@code PESSIMISTIC_WRITE} = {@code FOR NO KEY UPDATE} —
     * 이 표를 FK 로 가리키는 표가 없다), 칸은 그 뒤 프로젝션으로 읽는다(잠근 뒤의 값 — 엔티티를 1차 캐시에 올리지 않는다).
     */
    public Optional<OperatorMfaRow> lock(long operatorId) {
        if (query.select(MFA.operatorId).from(MFA).where(MFA.operatorId.eq(operatorId)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne() == null) {
            return Optional.empty();
        }
        return find(operatorId);
    }

    /**
     * 등록 중 비밀키를 둔다(이미 있으면 바꾼다). 확정된 비밀키는 그대로다.
     *
     * <p>Native SQL 인 이유: {@code INSERT ... ON CONFLICT DO UPDATE} 는 JPQL·QueryDSL 에 대응 문법이 없다. 「찾아보고 없으면 넣는다」로 바꾸면
     * 같은 운영자의 동시 시작이 {@code duplicate key} 로 깨진다.
     */
    public void startPending(long operatorId, String pendingSecretEnc, boolean reenroll, Instant now) {
        entityManager.createNativeQuery("INSERT INTO operator_mfa (operator_id, pending_secret_enc, pending_started_at, pending_reenroll, updated_at) VALUES (?1, ?2, ?3, ?4, ?3) "
                        + "ON CONFLICT (operator_id) DO UPDATE SET pending_secret_enc = EXCLUDED.pending_secret_enc, pending_started_at = EXCLUDED.pending_started_at, "
                        + "pending_reenroll = EXCLUDED.pending_reenroll, updated_at = EXCLUDED.updated_at")
                .setParameter(1, operatorId)
                .setParameter(2, pendingSecretEnc)
                .setParameter(3, micros(now))
                .setParameter(4, reenroll)
                .executeUpdate();
    }

    /** 등록 중 비밀키를 확정 비밀키로 옮긴다 — 확인에 쓴 칸을 마지막 칸으로 둔다(같은 코드로 곧바로 로그인하지 못하게) */
    public void confirmPending(long operatorId, long acceptedStep, Instant now) {
        Instant at = micros(now);
        query.update(MFA)
                .set(MFA.secretEnc, MFA.pendingSecretEnc)
                .set(MFA.enrolledAt, at)
                .set(MFA.lastStep, acceptedStep)
                .set(MFA.lastUsedAt, at)
                .setNull(MFA.pendingSecretEnc)
                .setNull(MFA.pendingStartedAt)
                .set(MFA.pendingReenroll, false)
                .set(MFA.updatedAt, at)
                .where(MFA.operatorId.eq(operatorId), MFA.pendingSecretEnc.isNotNull())
                .execute();
    }

    /**
     * 받아들인 칸을 저장한다 — 저장된 칸보다 클 때만(동시에 같은 코드를 낸 두 요청 중 하나만 이긴다).
     *
     * @return 저장했으면 true
     */
    public boolean acceptStep(long operatorId, long step, Instant now) {
        Instant at = micros(now);
        return query.update(MFA)
                .set(MFA.lastStep, step)
                .set(MFA.lastUsedAt, at)
                .set(MFA.updatedAt, at)
                .where(MFA.operatorId.eq(operatorId), MFA.secretEnc.isNotNull(), MFA.lastStep.isNull().or(MFA.lastStep.lt(step)))
                .execute() == 1;
    }

    /** 복구 코드로 들어왔을 때 마지막 사용 시각만 */
    public void touchUsed(long operatorId, Instant now) {
        Instant at = micros(now);
        query.update(MFA).set(MFA.lastUsedAt, at).set(MFA.updatedAt, at).where(MFA.operatorId.eq(operatorId)).execute();
    }

    /** 그 운영자의 OTP 행(등록·등록 중 모두)을 지운다. 지웠으면 true */
    public boolean deleteMfa(long operatorId) {
        return query.delete(MFA).where(MFA.operatorId.eq(operatorId)).execute() > 0;
    }

    // ── 복구 코드 ─────────────────────────────────────────────────────────────

    /** 그 운영자의 복구 코드를 모두 지우고 새 해시들로 바꾼다(새 행은 다음 질의·커밋 때 함께 나간다 — JDBC 배치) */
    public void replaceRecoveryCodes(long operatorId, List<Long> ids, List<String> hashes, Instant now) {
        query.delete(CODE).where(CODE.operatorId.eq(operatorId)).execute();
        Instant at = micros(now);
        for (int i = 0; i < hashes.size(); i++) {
            entityManager.persist(OperatorMfaRecoveryCodeEntity.create(ids.get(i), operatorId, hashes.get(i), at));
        }
    }

    /**
     * 쓰지 않은 그 복구 코드를 쓴 것으로 표시한다(조건부 — 같은 코드를 두 번 쓰지 못한다).
     *
     * @return 표시했으면 true
     */
    public boolean useRecoveryCode(long operatorId, String codeHash, Instant now) {
        return query.update(CODE).set(CODE.usedAt, micros(now))
                .where(CODE.operatorId.eq(operatorId), CODE.codeHash.eq(codeHash), CODE.usedAt.isNull())
                .execute() == 1;
    }

    public int unusedRecoveryCodes(long operatorId) {
        Long count = query.select(CODE.count()).from(CODE).where(CODE.operatorId.eq(operatorId), CODE.usedAt.isNull()).fetchOne();
        return count == null ? 0 : count.intValue();
    }

    public int deleteRecoveryCodes(long operatorId) {
        return (int) query.delete(CODE).where(CODE.operatorId.eq(operatorId)).execute();
    }

    // ── 신뢰 기기 ─────────────────────────────────────────────────────────────

    public void insertDevice(long id, long operatorId, String tokenHash, String agent, String ip, Instant trustedAt, Instant expiresAt) {
        entityManager.persist(OperatorTrustedDeviceEntity.create(id, operatorId, tokenHash, agent, ip, micros(trustedAt), micros(expiresAt)));
    }

    /** 그 운영자의, 기간이 남은 기기 중 쿠키 해시가 같은 것 — 다른 운영자의 기기 기록으로는 찾지 않는다 */
    public Optional<Long> findValidDevice(long operatorId, String tokenHash, Instant now) {
        return Optional.ofNullable(query.select(DEVICE.id).from(DEVICE)
                .where(DEVICE.operatorId.eq(operatorId), DEVICE.tokenHash.eq(tokenHash), DEVICE.expiresAt.gt(micros(now))).fetchFirst());
    }

    public void touchDevice(long id, Instant now) {
        query.update(DEVICE).set(DEVICE.lastUsedAt, micros(now)).where(DEVICE.id.eq(id)).execute();
    }

    /** 기간이 남은 기기들(최근 신뢰 차례). {@code currentHash} 와 같은 기기가 {@code current} */
    public List<TrustedDeviceRow> devices(long operatorId, String currentHash, Instant now) {
        return query.select(DEVICE.id, DEVICE.agent, DEVICE.ip, DEVICE.trustedAt, DEVICE.expiresAt, DEVICE.lastUsedAt, DEVICE.tokenHash).from(DEVICE)
                .where(DEVICE.operatorId.eq(operatorId), DEVICE.expiresAt.gt(micros(now)))
                .orderBy(DEVICE.trustedAt.desc(), DEVICE.id.desc())
                .fetch().stream()
                .map(t -> new TrustedDeviceRow(t.get(DEVICE.id), t.get(DEVICE.agent), t.get(DEVICE.ip), t.get(DEVICE.trustedAt), t.get(DEVICE.expiresAt),
                        t.get(DEVICE.lastUsedAt), currentHash != null && currentHash.equals(t.get(DEVICE.tokenHash).trim())))
                .toList();
    }

    public int countDevices(long operatorId, Instant now) {
        Long count = query.select(DEVICE.count()).from(DEVICE).where(DEVICE.operatorId.eq(operatorId), DEVICE.expiresAt.gt(micros(now))).fetchOne();
        return count == null ? 0 : count.intValue();
    }

    /** 그 운영자의 기기 하나의 쿠키 해시(내 것이 아니면 비어 있다) */
    public Optional<String> deviceHash(long operatorId, long id) {
        return Optional.ofNullable(query.select(DEVICE.tokenHash).from(DEVICE).where(DEVICE.operatorId.eq(operatorId), DEVICE.id.eq(id)).fetchFirst())
                .map(String::trim);
    }

    /** 그 운영자의 기기 하나를 지운다. 지웠으면 true */
    public boolean deleteDevice(long operatorId, long id) {
        return query.delete(DEVICE).where(DEVICE.operatorId.eq(operatorId), DEVICE.id.eq(id)).execute() == 1;
    }

    /** 그 운영자의 기기를 모두 지운다(기간이 끝난 것 포함). 지운 수 */
    public int deleteDevices(long operatorId) {
        return (int) query.delete(DEVICE).where(DEVICE.operatorId.eq(operatorId)).execute();
    }

    // ── 등록 현황 ─────────────────────────────────────────────────────────────

    /** 활성 운영자(루트 포함)와 등록 여부 — ID 차례 */
    public List<MfaOperatorStatus> activeOperators(Instant now) {
        // 등록 여부(secret_enc IS NOT NULL) — 1·0 으로 고른다
        NumberExpression<Integer> enrolledFlag = new CaseBuilder().when(MFA.secretEnc.isNotNull()).then(1).otherwise(0);
        return query.select(OPERATOR.id, OPERATOR.operatorName, OPERATOR.createdAt, enrolledFlag).from(OPERATOR)
                .leftJoin(MFA).on(MFA.operatorId.eq(OPERATOR.id))
                .where(activeOperator(micros(now)))
                .orderBy(OPERATOR.id.asc())
                .fetch().stream()
                .map(t -> new MfaOperatorStatus(t.get(OPERATOR.id), t.get(OPERATOR.operatorName), t.get(OPERATOR.createdAt), isOne(t.get(enrolledFlag))))
                .toList();
    }

    /** 지금 로그인할 수 있는 운영자 — {@code OperatorEntity#canLogin} · {@code NotificationRecipientRepositoryQDSL#isActive} 와 같은 조건 */
    private static BooleanExpression activeOperator(Instant now) {
        return OPERATOR.approved.isTrue().and(OPERATOR.locked.isFalse()).and(OPERATOR.expired.isFalse()).and(OPERATOR.enabled.isTrue())
                .and(OPERATOR.accountStartAt.isNull().or(OPERATOR.accountStartAt.loe(now)))
                .and(OPERATOR.accountEndAt.isNull().or(OPERATOR.accountEndAt.goe(now)));
    }

    /** CASE 가 고른 1·0(DB 가 고른 수 타입) */
    private static boolean isOne(Object value) {
        return value instanceof Number n && n.intValue() == 1;
    }

    /** DB 는 마이크로초 — 반올림되지 않게 먼저 자른다 */
    private static Instant micros(Instant at) {
        return at == null ? null : at.truncatedTo(ChronoUnit.MICROS);
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
