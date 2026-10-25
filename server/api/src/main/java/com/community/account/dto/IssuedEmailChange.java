package com.community.account.dto;

/**
 * 새로 만든 이메일 변경 링크 — 메일 두 통(새 주소에 링크, 지금 주소에 알림)을 보낼 정보다. {@code token} 은 링크에 넣을 <b>원문</b>이라 DB 에는 없다(해시만).
 * {@link #toString()} 은 이메일·토큰을 싣지 않는다.
 */
public record IssuedEmailChange(long userId, String currentEmail, String newEmail, String nickname, String token) {

    @Override
    public String toString() {
        return "IssuedEmailChange[userId=" + userId + ", currentEmail=(감춤), newEmail=(감춤), token=(감춤)]";
    }
}
