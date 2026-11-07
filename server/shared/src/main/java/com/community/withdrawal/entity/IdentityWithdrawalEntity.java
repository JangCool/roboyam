package com.community.withdrawal.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 재가입·재인증 제한 해시 한 줄 — shared V84 의 {@code identity_withdrawals}. 해시만 둔다(누구의 것인지 잇는 칸이 없다). 넣기만 하고 고치지 않는다 — 기간이 지나면 정리 배치
 * ({@code member-privacy-retention})가 지운다.
 */
@Entity
@Table(name = "identity_withdrawals")
public class IdentityWithdrawalEntity {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private WithdrawalKind kind;

    /** BlindIndex 해시(16진 64자) */
    private String hash;
    private Instant withdrawnAt;
    private Instant expiresAt;

    protected IdentityWithdrawalEntity() {
    }

    public static IdentityWithdrawalEntity of(long id, WithdrawalKind kind, String hash, Instant withdrawnAt, Instant expiresAt) {
        IdentityWithdrawalEntity w = new IdentityWithdrawalEntity();
        w.id = id;
        w.kind = kind;
        w.hash = hash;
        w.withdrawnAt = withdrawnAt;
        w.expiresAt = expiresAt;
        return w;
    }
}
