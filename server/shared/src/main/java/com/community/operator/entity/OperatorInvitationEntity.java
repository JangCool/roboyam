package com.community.operator.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 운영자 초대 한 건 — V7__operators_roles.sql 의 {@code operator_invitations}. 1회용 초대 링크의 토큰은 <b>해시(SHA-256 16진 64자)만</b> 저장한다 — 원문은 메일에만 있다.
 * 받은 사람이 수락하면 {@code consumed_at} 이 찍히고 운영자가 생긴다(공개 API {@code /api/admin/auth/invitation/accept}). 같은 이메일에 수락하지 않은 초대는 하나뿐이다.
 * 초대할 때 정한 역할은 {@code operator_invitation_roles} 에 있다. {@link #toString()} 은 값을 싣지 않는다.
 */
@Entity
@Table(name = "operator_invitations")
public class OperatorInvitationEntity {

    @Id
    private Long id;

    private String email;
    private String tokenHash;
    private Long invitedBy;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant consumedAt;

    protected OperatorInvitationEntity() {
    }

    /** @param tokenHash 토큰 원문의 SHA-256 16진 64자. 원문을 넘기지 않는다 */
    public static OperatorInvitationEntity create(long id, String email, String tokenHash, Long invitedBy, Instant now, Instant expiresAt) {
        OperatorInvitationEntity invitation = new OperatorInvitationEntity();
        invitation.id = id;
        invitation.email = email;
        invitation.tokenHash = tokenHash;
        invitation.invitedBy = invitedBy;
        invitation.createdAt = now;
        invitation.expiresAt = expiresAt;
        return invitation;
    }

    /** 유효기간이 지났나 */
    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    /** 수락했다 — 링크를 다시 쓸 수 없게 수락 시각을 남긴다 */
    public void consume(Instant now) {
        this.consumedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    /** 초대한 운영자 — 그 운영자가 지워졌으면 null */
    public Long getInvitedBy() {
        return invitedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** 수락한 시각 — 수락하지 않았으면 null */
    public Instant getConsumedAt() {
        return consumedAt;
    }
}
