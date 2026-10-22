package com.community.sociallogin.dto;

/**
 * 시작한 흐름 — 브라우저를 보낼 공급자 인가 주소와, 서버 세션에 둘 흐름.
 *
 * @param redirectUrl 공급자 인가 주소. state 원문이 들어 있어 로그에 남기지 않는다
 */
public record AuthorizationStart(String redirectUrl, AuthorizationFlow flow) {

    @Override
    public String toString() {
        return "AuthorizationStart[" + flow + "]";
    }
}
