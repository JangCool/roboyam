package com.community.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 이메일 로그인 요청. 비밀번호는 비어 있지 않기만 하면 된다 — 가입 규칙(길이·문자 구성)을 로그인에서 다시 검사하지 않는다.
 * 이메일 형식은 정규화(소문자·공백 제거) 뒤에 서비스가 본다. 검증 문구는 {키} 로 두어 메시지 번들에서 요청 언어로 풀린다. {@link #toString()} 은 값을 싣지 않는다(비밀번호가 로그에 새지 않게).
 */
public record LoginRequest(
        @NotBlank(message = "{user.email.required}") @Size(max = 254, message = "{user.email.max}") String email,
        @NotBlank(message = "{user.password.required}") String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=(감춤), password=(감춤)]";
    }
}
