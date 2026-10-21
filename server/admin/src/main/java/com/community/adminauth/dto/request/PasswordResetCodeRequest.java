package com.community.adminauth.dto.request;

/** 비밀번호 재설정 승인번호 요청. 이메일 형식 검사는 서비스가 한다({@code fields.email}). {@link #toString()} 은 값을 싣지 않는다 */
public record PasswordResetCodeRequest(String email) {

    @Override
    public String toString() {
        return "PasswordResetCodeRequest[email=(감춤)]";
    }
}
