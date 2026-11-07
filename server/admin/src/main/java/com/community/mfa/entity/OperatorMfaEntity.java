package com.community.mfa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 운영자 OTP 한 행(운영자당 하나) — admin 스트림 V3 의 {@code operator_mfa}. {@code secretEnc} 가 있으면 등록됨, {@code lastStep} 은 마지막으로 받아들인 TOTP 칸(그 이하는 거절).
 * {@code pending*} 은 등록 중(확정 전) 비밀키다.
 *
 * <p>운영 흐름의 행은 {@code MfaStore#startPending}(ON CONFLICT upsert)이 만들고, 확정·칸 저장·사용 시각은 같은 저장소의 조건부 UPDATE 가 바꾼다
 * (「동시에 낸 두 요청 중 하나만 이긴다」를 문장 하나로 지킨다 — 엔티티를 읽고 고치는 방식으로 바꾸지 않는다).
 *
 * <p>비밀키는 암호문(「v1:」)으로만 들고, {@code toString} 을 덮어쓰지 않는다(기본 {@code Object#toString} 은 칸 값을 싣지 않는다).
 */
@Entity
@Table(name = "operator_mfa")
public class OperatorMfaEntity {

    @Id
    private Long operatorId;

    private String secretEnc;
    private Instant enrolledAt;
    private Long lastStep;
    private Instant lastUsedAt;
    private String pendingSecretEnc;
    private Instant pendingStartedAt;
    private boolean pendingReenroll;
    private Instant updatedAt;

    protected OperatorMfaEntity() {
    }

    /**
     * 등록을 마친 행 — 받아들인 칸은 아직 없다. 운영 흐름(등록 중 → 확정)을 거치지 않고 바로 등록할 때(시험 준비 등) 쓴다.
     *
     * @param secretEnc 비밀키 암호문
     */
    public static OperatorMfaEntity enrolled(long operatorId, String secretEnc, Instant now) {
        OperatorMfaEntity row = new OperatorMfaEntity();
        row.operatorId = operatorId;
        row.secretEnc = secretEnc;
        row.enrolledAt = now;
        row.updatedAt = now;
        return row;
    }

    public long getOperatorId() {
        return operatorId;
    }

    public String getSecretEnc() {
        return secretEnc;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public Long getLastStep() {
        return lastStep;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public String getPendingSecretEnc() {
        return pendingSecretEnc;
    }

    public Instant getPendingStartedAt() {
        return pendingStartedAt;
    }

    public boolean isPendingReenroll() {
        return pendingReenroll;
    }
}
