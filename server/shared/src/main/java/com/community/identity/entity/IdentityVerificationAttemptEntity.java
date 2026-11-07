package com.community.identity.entity;

import com.community.identity.dto.IdentityProviderKind;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 본인인증 시도 한 줄(서버가 먼저 발급한 인증 번호) — shared V84 의 {@code identity_verification_attempts}. PENDING(발급) → VERIFIED | FAILED(거절한 오류 코드).
 * 끝내기는 {@code IdentityStore#finishAttempt} 의 조건부 UPDATE(발급 상태일 때만 — 한 번만)다.
 */
@Entity
@Table(name = "identity_verification_attempts")
public class IdentityVerificationAttemptEntity {

    public static final String PENDING = "PENDING";
    public static final String VERIFIED = "VERIFIED";
    public static final String FAILED = "FAILED";

    @Id
    private String id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private IdentityProviderKind provider;

    private String status;
    private String failureCode;
    private Instant createdAt;
    private Instant completedAt;

    protected IdentityVerificationAttemptEntity() {
    }

    /** 발급 — 상태 PENDING */
    public static IdentityVerificationAttemptEntity start(String id, long userId, IdentityProviderKind provider, Instant createdAt) {
        IdentityVerificationAttemptEntity a = new IdentityVerificationAttemptEntity();
        a.id = id;
        a.userId = userId;
        a.provider = provider;
        a.status = PENDING;
        a.createdAt = createdAt;
        return a;
    }
}
