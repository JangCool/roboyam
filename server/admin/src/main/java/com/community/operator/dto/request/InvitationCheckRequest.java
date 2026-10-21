package com.community.operator.dto.request;

/** 초대 링크 확인 — 토큰은 URL 이 아니라 본문으로 받는다(주소 로그에 남지 않게). {@link #toString()} 은 값을 싣지 않는다 */
public record InvitationCheckRequest(String token) {

    @Override
    public String toString() {
        return "InvitationCheckRequest[token=(감춤)]";
    }
}
