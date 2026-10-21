package com.community.auth.dto;

/**
 * 새로 만든 이메일 인증 링크 — 메일로 보낼 정보다. {@code token} 은 링크에 넣을 <b>원문</b>이라 DB 에는 없고(해시만 저장) 로그에 남기지 않는다.
 * {@link #toString()} 은 이메일과 토큰을 감춘다.
 */
public record IssuedVerification(String email, String nickname, String token) {

    @Override
    public String toString() {
        return "IssuedVerification[email=(감춤), nickname=" + nickname + ", token=(감춤)]";
    }
}
