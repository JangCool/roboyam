package com.community.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 회원 계정 — V2__auth.sql 의 {@code users}. 닉네임·등급은 {@link ProfileEntity} 에 있다.
 *
 * <p>ID 는 애플리케이션이 발급(Snowflake)하므로 새 회원은 {@code persist()} 로 넣는다 — {@code save()} 는 INSERT 앞에 불필요한 SELECT 가 붙는다.
 * 지금은 로그인에 필요한 칸만 매핑했다(탈퇴·정지 처리·회원가입 등은 그 기능을 만들 때 더한다). 운영자는 회원이 아니라 {@code OperatorEntity} 다.
 *
 * <p>{@link DynamicUpdate}: 고친 열만 UPDATE 한다 — 로그인 시각을 갱신하면서, 다른 흐름이 따로 바꾼 열(상태·역할 등)을 읽어 둔 낡은 값으로 되써서 덮어쓰지 않게 한다.
 *
 * <p>{@link #toString()} 은 값을 싣지 않는다(기본 {@code Object.toString} 을 그대로 쓴다) — 이메일·비밀번호 해시가 로그에 새지 않게.
 */
@Entity
@Table(name = "users")
@DynamicUpdate
public class UserEntity {

    @Id
    private Long id;

    /** 소문자로 정규화해 저장 */
    private String email;

    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    private Instant emailVerifiedAt;
    private String termsVersion;
    private Instant termsAgreedAt;
    private Instant ageConfirmedAt;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;
    /** 탈퇴한 시각. 있으면 로그인할 수 없다 */
    private Instant deletedAt;

    protected UserEntity() {
    }

    /** 로그인했다 — 마지막 로그인 시각만 고친다 */
    public void markLoggedIn(Instant now) {
        this.lastLoginAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    /** 비밀번호 해시. 응답·로그에 싣지 않는다(소셜로만 가입한 계정은 null) */
    public String getPasswordHash() {
        return passwordHash;
    }

    /** 비밀번호가 있는 계정인가(소셜로만 가입했으면 false) */
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isEmpty();
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /** 탈퇴했거나 지워진 계정인가 — 로그인할 수 없고 로그인 상태로 보지 않는다 */
    public boolean isGone() {
        return status == UserStatus.WITHDRAWN || deletedAt != null;
    }
}
