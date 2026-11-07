package com.community.mfa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 복구 코드 하나 — admin 스트림 V3 의 {@code operator_mfa_recovery_codes}. 코드 원문은 두지 않고 SHA-256(운영자 ID ‖ 코드)만 둔다((운영자, 해시) 유일).
 * 쓰면 {@code usedAt} 이 찬다 — 한 번만(조건부 UPDATE {@code used_at IS NULL}, {@code MfaStore#useRecoveryCode}).
 * {@code toString} 을 덮어쓰지 않는다(기본 {@code Object#toString} 은 해시를 싣지 않는다).
 */
@Entity
@Table(name = "operator_mfa_recovery_codes")
public class OperatorMfaRecoveryCodeEntity {

    @Id
    private Long id;

    private long operatorId;
    private String codeHash;
    private Instant createdAt;
    private Instant usedAt;

    protected OperatorMfaRecoveryCodeEntity() {
    }

    /** 쓰지 않은 새 코드 */
    public static OperatorMfaRecoveryCodeEntity create(long id, long operatorId, String codeHash, Instant now) {
        OperatorMfaRecoveryCodeEntity row = new OperatorMfaRecoveryCodeEntity();
        row.id = id;
        row.operatorId = operatorId;
        row.codeHash = codeHash;
        row.createdAt = now;
        return row;
    }

    public Long getId() {
        return id;
    }
}
