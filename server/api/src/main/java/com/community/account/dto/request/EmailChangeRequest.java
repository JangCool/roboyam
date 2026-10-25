package com.community.account.dto.request;

/** {@code POST /api/me/email-change} — 새 이메일과 본인 확인용 현재 비밀번호. 서비스가 검사한다. {@link #toString()} 은 값을 싣지 않는다 */
public record EmailChangeRequest(String newEmail, String password) {

    @Override
    public String toString() {
        return "EmailChangeRequest[newEmail=(감춤), password=(감춤)]";
    }
}
