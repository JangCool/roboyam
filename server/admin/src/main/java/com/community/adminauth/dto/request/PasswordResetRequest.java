package com.community.adminauth.dto.request;

/** 승인번호 + 새 비밀번호를 한 번에 보내 비밀번호를 바꾼다. 칸별 문구가 필요해 서비스가 검사한다({@code fields.email|certCode|newPassword}). {@link #toString()} 은 값을 싣지 않는다 */
public record PasswordResetRequest(String email, String certCode, String newPassword) {

    @Override
    public String toString() {
        return "PasswordResetRequest[email=(감춤), certCode=(감춤), newPassword=(감춤)]";
    }
}
