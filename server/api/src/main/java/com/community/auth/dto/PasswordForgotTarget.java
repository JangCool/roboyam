package com.community.auth.dto;

import com.community.social.entity.SocialProvider;

/**
 * 비밀번호 찾기 작업이 보낼 메일 — 작업 스레드 안에서만 오간다. {@link #toString()} 은 이메일·토큰을 싣지 않는다.
 *
 * @param token    재설정 링크에 넣을 토큰 <b>원문</b>({@link Kind#RESET} 일 때만, DB 에는 해시만 있다)
 * @param provider 가입한 소셜 공급자({@link Kind#SOCIAL} 일 때만 — 가장 먼저 연결한 것)
 */
public record PasswordForgotTarget(Kind kind, long userId, String email, String nickname, String token, SocialProvider provider) {

    public enum Kind {
        /** 비밀번호가 있는 회원 — 재설정 링크 */
        RESET,
        /** 소셜로만 가입한 회원 — 가입한 소셜 로그인 안내 */
        SOCIAL,
        /** 보내지 않는다(없는 이메일·탈퇴·60초 안 반복·로그인 수단이 없음) */
        NONE
    }

    public static PasswordForgotTarget none() {
        return new PasswordForgotTarget(Kind.NONE, 0, null, null, null, null);
    }

    @Override
    public String toString() {
        return "PasswordForgotTarget[kind=" + kind + ", userId=" + userId + ", email=(감춤), token=(감춤), provider=" + provider + "]";
    }
}
