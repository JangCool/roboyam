package com.community.profile.dto.request;

/** 내 비밀번호 변경 — 현재 비밀번호 확인 + 새 비밀번호. 칸별 문구가 필요해 서비스가 검사한다({@code fields.currentPassword|newPassword}). {@link #toString()} 은 값을 싣지 않는다 */
public record PasswordChangeRequest(String currentPassword, String newPassword) {

    @Override
    public String toString() {
        return "PasswordChangeRequest[currentPassword=(감춤), newPassword=(감춤)]";
    }
}
