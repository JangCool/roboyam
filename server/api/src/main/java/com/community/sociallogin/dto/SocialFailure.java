package com.community.sociallogin.dto;

/**
 * 콜백 실패 사유 — 사용자 앱으로 돌려보낼 때 쿼리 {@code reason} 에 싣는 코드다. 개인정보·토큰·공급자 응답은 싣지 않는다.
 */
public enum SocialFailure {
    /** 사용자가 공급자 화면에서 취소(동의 거부)했다 */
    CANCELLED("cancelled"),
    /** state 가 없거나 맞지 않거나 이미 쓰였다(위조·재사용·다른 브라우저) */
    INVALID_STATE("invalid_state"),
    /** 시작한 지 너무 오래돼 흐름이 끝났다 */
    EXPIRED("expired"),
    /** 공급자 오류 — 코드 교환·회원 정보·ID 토큰 검증 실패, 그 사이 공급자가 꺼짐 */
    PROVIDER_ERROR("provider_error"),
    /** 연결된 회원이 탈퇴했거나 지워져 로그인할 수 없다 */
    ACCOUNT_UNAVAILABLE("account_unavailable"),
    /** 연결을 시작한 회원과 지금 로그인한 회원이 다르다(로그아웃·다른 계정으로 로그인) */
    SESSION_CHANGED("session_changed"),
    /** 이 회원에게 그 공급자의 다른 계정이 이미 연결돼 있다 */
    ALREADY_LINKED("already_linked");

    private final String code;

    SocialFailure(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
