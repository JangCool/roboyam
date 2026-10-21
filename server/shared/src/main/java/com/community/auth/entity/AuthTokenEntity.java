package com.community.auth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 이메일 인증·비밀번호 재설정 링크의 토큰 — V2__auth.sql 의 {@code auth_tokens}. 토큰 원문은 저장하지 않고 SHA-256 해시(16진 64자)만 둔다.
 * 새 링크를 만들면 이전 링크는 사용 처리({@code consumed_at})로 무효가 된다. {@link #toString()} 은 값을 싣지 않는다.
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
}
