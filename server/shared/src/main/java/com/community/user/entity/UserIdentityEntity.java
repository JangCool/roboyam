package com.community.user.entity;

import com.community.social.entity.SocialProvider;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회원에 연결된 소셜 계정 — V2__auth.sql 의 {@code user_identities}. 로그인 응답의 {@code authMethods} 와 소셜 로그인(공개 사이트 서버의 {@code sociallogin})이 쓴다.
 * 같은 소셜 계정(공급자 + 공급자 회원 번호)은 한 회원에게만 연결된다(유일 제약 {@code ux_user_identities_provider}).
 * {@link #toString()} 은 값을 싣지 않는다(기본 {@code Object.toString} 을 그대로 쓴다) — 공급자 회원 번호·이메일이 로그에 새지 않게.
 */
@Entity
@Table(name = "user_identities")
public class UserIdentityEntity {

    @Id
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private SocialProvider provider;

    /** 공급자가 주는 회원 번호(구글 {@code sub}, 카카오·네이버 {@code id}) — 128자 이하 */
    private String providerUserId;

    /** 연결할 때 공급자가 준 이메일(소문자). 안 줬으면 null */
    private String email;

    private Instant createdAt;

    protected UserIdentityEntity() {
    }

    /** 소셜 계정을 회원에 연결한다 */
    public static UserIdentityEntity create(long id, long userId, SocialProvider provider, String providerUserId, String email, Instant now) {
        UserIdentityEntity identity = new UserIdentityEntity();
        identity.id = id;
        identity.userId = userId;
        identity.provider = provider;
        identity.providerUserId = providerUserId;
        identity.email = email;
        identity.createdAt = now;
        return identity;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public SocialProvider getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }
}
