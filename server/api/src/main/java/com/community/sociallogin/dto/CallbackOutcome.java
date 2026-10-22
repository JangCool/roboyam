package com.community.sociallogin.dto;

import com.community.auth.dto.AuthUser;
import com.community.social.entity.SocialProvider;

/**
 * 콜백 처리 결과 — 웹 계층이 이것으로 세션을 바꾸고 사용자 앱의 정해진 경로로 돌려보낸다.
 *
 * @param kind      결과 종류
 * @param provider  공급자(state 를 찾지 못했으면 null — 그때는 주소의 공급자를 믿지 않는다)
 * @param mode      흐름의 목적(state 를 찾지 못했으면 LOGIN 으로 본다)
 * @param principal {@link Kind#LOGGED_IN} 일 때 로그인할 회원
 * @param pending   {@link Kind#SIGNUP_REQUIRED} 일 때 세션에 둘 가입 대기 정보
 * @param failure   {@link Kind#FAILED} 일 때 사유
 * @param next      로그인 뒤 돌아갈 상대 경로(검사를 마친 값). 없으면 null
 */
public record CallbackOutcome(Kind kind, SocialProvider provider, SocialMode mode, AuthUser principal, PendingSignup pending, SocialFailure failure,
                              String next) {

    public enum Kind {
        /** 연결된 소셜 계정 — 그 회원으로 로그인한다 */
        LOGGED_IN,
        /** 처음 보는 소셜 계정 — 가입 완료 단계로 */
        SIGNUP_REQUIRED,
        /** 처음 보는 소셜 계정인데 이메일이 기존 회원과 같다 — 자동 연결하지 않는다 */
        EMAIL_EXISTS,
        /** 로그인한 회원에 연결했다(이미 그 회원에 연결돼 있던 경우 포함) */
        LINKED,
        /** 그 소셜 계정이 다른 회원에 연결돼 있다 */
        LINK_TAKEN,
        /** 실패 — {@link #failure} 가 사유 */
        FAILED
    }

    public static CallbackOutcome loggedIn(SocialProvider provider, AuthUser principal, String next) {
        return new CallbackOutcome(Kind.LOGGED_IN, provider, SocialMode.LOGIN, principal, null, null, next);
    }

    public static CallbackOutcome signupRequired(SocialProvider provider, PendingSignup pending) {
        return new CallbackOutcome(Kind.SIGNUP_REQUIRED, provider, SocialMode.LOGIN, null, pending, null, pending.next());
    }

    public static CallbackOutcome emailExists(SocialProvider provider) {
        return new CallbackOutcome(Kind.EMAIL_EXISTS, provider, SocialMode.LOGIN, null, null, null, null);
    }

    public static CallbackOutcome linked(SocialProvider provider) {
        return new CallbackOutcome(Kind.LINKED, provider, SocialMode.LINK, null, null, null, null);
    }

    public static CallbackOutcome linkTaken(SocialProvider provider) {
        return new CallbackOutcome(Kind.LINK_TAKEN, provider, SocialMode.LINK, null, null, null, null);
    }

    public static CallbackOutcome failed(SocialProvider provider, SocialMode mode, SocialFailure failure) {
        return new CallbackOutcome(Kind.FAILED, provider, mode == null ? SocialMode.LOGIN : mode, null, null, failure, null);
    }
}
