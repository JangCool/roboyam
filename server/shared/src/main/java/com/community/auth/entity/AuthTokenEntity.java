package com.community.auth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 이메일 인증·비밀번호 재설정·이메일 변경 링크의 토큰 — V2__auth.sql 의 {@code auth_tokens}(이메일 변경 용도·{@code new_email} 은 V64). 토큰 원문은 저장하지 않고
 * SHA-256 해시(16진 64자)만 둔다. 새 링크를 만들면 이전 링크는 사용 처리({@code consumed_at})로 무효가 된다. {@link #toString()} 은 값을 싣지 않는다(기본 {@code Object.toString}).
 */
@Entity
@Table(name = "auth_tokens")
public class AuthTokenEntity {

    @Id
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private AuthTokenPurpose purpose;

    private String tokenHash;
    private Instant expiresAt;
    private Instant consumedAt;
    private Instant createdAt;
    /** 이메일 변경 링크가 바꿀 새 주소(소문자로 정규화한 값). 다른 용도는 null */
    private String newEmail;

    protected AuthTokenEntity() {
    }

    /** @param tokenHash 토큰 원문의 SHA-256 16진 64자. 원문을 넘기지 않는다 */
    public static AuthTokenEntity create(long id, long userId, AuthTokenPurpose purpose, String tokenHash, Instant expiresAt, Instant now) {
        AuthTokenEntity token = new AuthTokenEntity();
        token.id = id;
        token.userId = userId;
        token.purpose = purpose;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        token.createdAt = now;
        return token;
    }

    /**
     * 이메일 변경 링크.
     *
     * @param newEmail  바꿀 새 주소(소문자로 정규화한 값)
     * @param tokenHash 토큰 원문의 SHA-256 16진 64자
     */
    public static AuthTokenEntity createEmailChange(long id, long userId, String newEmail, String tokenHash, Instant expiresAt, Instant now) {
        AuthTokenEntity token = create(id, userId, AuthTokenPurpose.CHANGE_EMAIL, tokenHash, expiresAt, now);
        token.newEmail = newEmail;
        return token;
    }

    /** 링크를 썼다(또는 새 링크로 무효가 됐다) */
    public void consume(Instant now) {
        if (consumedAt == null) {
            consumedAt = now;
        }
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public Long getUserId() {
        return userId;
    }

    public AuthTokenPurpose getPurpose() {
        return purpose;
    }

    /** 이메일 변경 링크의 새 주소 — 로그·응답에 원문을 싣지 않는다 */
    public String getNewEmail() {
        return newEmail;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
