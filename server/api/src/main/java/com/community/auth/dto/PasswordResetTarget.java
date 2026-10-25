package com.community.auth.dto;

/**
 * 재설정 링크가 가리키는 회원과 지금 비밀번호 해시 — 「지금 비밀번호와 같은가」를 트랜잭션 밖에서 보려고 꺼낸다. {@link #toString()} 은 해시를 싣지 않는다.
 */
public record PasswordResetTarget(long userId, String passwordHash) {

    @Override
    public String toString() {
        return "PasswordResetTarget[userId=" + userId + ", passwordHash=(감춤)]";
    }
}
