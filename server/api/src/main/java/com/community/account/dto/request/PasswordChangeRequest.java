package com.community.account.dto.request;

/** {@code PUT /api/me/password} — 현재 비밀번호와 새 비밀번호. 서비스가 검사한다. {@link #toString()} 은 값을 싣지 않는다 */
public record PasswordChangeRequest(String currentPassword, String newPassword) {

    @Override
    public String toString() {
        return "PasswordChangeRequest[currentPassword=(감춤), newPassword=(감춤)]";
    }
}
