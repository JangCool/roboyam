package com.community.withdrawal.dto;

import java.time.Instant;

/**
 * 열람한 탈퇴 회원 연락처 — 감사를 남긴 뒤에만 만들어진다. 응답은 `Cache-Control: no-store`. `toString` 은 이메일을 감춘다.
 *
 * @param email  복호화한 이메일
 * @param maskAt 화면이 다시 가리는 순간(열람 + 10분 — 화면 확정 결정 4)
 */
public record RevealedContact(String email, Instant maskAt) {

    @Override
    public String toString() {
        return "RevealedContact[email=(감춤), maskAt=" + maskAt + "]";
    }
}
