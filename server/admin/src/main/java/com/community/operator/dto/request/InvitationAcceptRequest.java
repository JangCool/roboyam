package com.community.operator.dto.request;

/**
 * 초대 수락 — 이름(닉네임)과 비밀번호를 정한다. 칸별 문구가 필요해 서비스가 검사한다({@code fields.nickname|password}). 앞뒤를 자르지 않은 그대로 받는다.
 * {@link #toString()} 은 값을 싣지 않는다.
 */
public record InvitationAcceptRequest(String token, String nickname, String password) {

    @Override
    public String toString() {
        return "InvitationAcceptRequest[token=(감춤), nickname=(감춤), password=(감춤)]";
    }
}
