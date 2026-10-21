package com.community.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 닉네임·등급 등 공개되는 회원 정보 — V2__auth.sql 의 {@code profiles}. users 와 같은 ID 를 쓴다(1:1).
 * 지금은 루트 계정을 만드는 데 필요한 칸만 매핑했다(소개·이미지 등은 그 기능을 만들 때 더한다).
 */
@Entity
@Table(name = "profiles")
public class ProfileEntity {

    /** 처음 등급 — 1 새싹 (profiles.grade 의 CHECK 는 1~4) */
    private static final short GRADE_SEEDLING = 1;

    @Id
    private Long userId;

    private String nickname;

    private short grade;

    private Instant createdAt;
    private Instant updatedAt;

    protected ProfileEntity() {
    }

    public static ProfileEntity create(long userId, String nickname, Instant now) {
        ProfileEntity profile = new ProfileEntity();
        profile.userId = userId;
        profile.nickname = nickname;
        profile.grade = GRADE_SEEDLING;
        profile.createdAt = now;
        profile.updatedAt = now;
        return profile;
    }

    public Long getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    /** 등급 번호(1 새싹 · 2 일반 · 3 단골 · 4 베테랑) */
    public short getGradeLevel() {
        return grade;
    }
}
