package com.community.user.entity;

import com.community.social.entity.SocialProvider;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회원에 연결된 소셜 계정 — V2__auth.sql 의 {@code user_identities}. 지금은 로그인 응답의 {@code authMethods} 를 만들려고 읽기만 한다
 * (연결을 만드는 소셜 가입·연동은 그 기능을 만들 때 더한다). {@link #toString()} 은 값을 싣지 않는다.
 */
@Entity
@Table(name = "user_identities")
public class UserIdentityEntity {

    @Id
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private SocialProvider provider;

    private Instant createdAt;

    protected UserIdentityEntity() {
    }

    public SocialProvider getProvider() {
        return provider;
    }
}
