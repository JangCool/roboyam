package com.community.auth.dto.response;

/**
 * 로그인 상태({@code GET /api/auth/me}) · 로그인 응답 공통. 비로그인도 오류가 아니라 {@code authenticated: false} 다.
 *
 * @param restriction 정지·영구 정지 중일 때만 값이 있다. 그 밖에는 null
 * @param adminUrl    관리자 앱 주소 — 정상 상태의 운영자에게만 값이 있다. 그 밖에는 null
 */
public record MeResponse(boolean authenticated, UserView user, RestrictionView restriction, String adminUrl) {

    public static MeResponse anonymous() {
        return new MeResponse(false, null, null, null);
    }
}
