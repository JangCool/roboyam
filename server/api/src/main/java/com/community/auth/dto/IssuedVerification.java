package com.community.auth.dto;

/**
 * 새로 만든 이메일 인증 링크 — 메일로 보낼 정보다. {@code token} 은 링크에 넣을 <b>원문</b>이라 DB 에는 없고(해시만 저장) 로그에 남기지 않는다.
 * {@link #toString()} 은 이메일과 토큰을 감춘다.
 *
 * @param social 비밀번호 없이 소셜 계정으로만 가입한 회원인가 — 인증 메일 문구의 조건 {@code signupMethod=SOCIAL}(공급자가 확인해 주지 않은 이메일)
 */
public record IssuedVerification(String email, String nickname, String token, boolean social) {

    @Override
    public String toString() {
        return "IssuedVerification[email=(감춤), nickname=" + nickname + ", token=(감춤), social=" + social + "]";
    }
}
