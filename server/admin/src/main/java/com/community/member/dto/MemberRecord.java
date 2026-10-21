package com.community.member.dto;

import com.community.user.entity.UserStatus;
import java.time.Instant;

/**
 * 회원 목록 한 줄의 원본 — 조회가 읽어 온 계정·프로필 값이다(비밀번호 해시는 없고 비밀번호가 있는지만 안다). 이메일은 아직 가리지 않은 값이라 응답에 그대로 싣지 않는다.
 *
 * @param id          회원 ID(회원번호)
 * @param hasPassword 비밀번호가 있는 계정인가(소셜로만 가입했으면 false)
 * @param deletedAt   탈퇴한 시각. 탈퇴하지 않았으면 null
 */
public record MemberRecord(Long id, String email, String nickname, String gradeKey, UserStatus status, Boolean hasPassword, Instant emailVerifiedAt,
                           Instant createdAt, Instant lastLoginAt, Instant deletedAt) {

    /** 이메일을 로그에 남기지 않는다 */
    @Override
    public String toString() {
        return "MemberRecord[id=" + id + ", status=" + status + "]";
    }
}
