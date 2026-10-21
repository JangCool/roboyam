package com.community.adminauth.dto.request;

/**
 * 관리자 로그인 요청. 입력 검사(이메일 형식·비밀번호 비어 있음)는 서비스가 한다 — 두 칸의 문구를 한꺼번에 모아 {@code fields} 로 주려는 것이다.
 * {@link #toString()} 은 값을 싣지 않는다.
 */
public record AdminLoginRequest(String email, String password) {

    @Override
    public String toString() {
        return "AdminLoginRequest[email=(감춤), password=(감춤)]";
    }
}
