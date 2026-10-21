package com.community.auth.dto.response;

/**
 * 로그인 상태({@code GET /api/auth/me}) · 로그인 응답 공통. 비로그인도 오류가 아니라 {@code authenticated: false} 다.
 *
 * @param restriction 정지·영구 정지 중일 때만 값이 있다. 그 밖에는 null
 * @param adminUrl    항상 null — 운영자는 공개 사이트에 로그인하지 않는다(화면 타입의 칸은 남겨 두었다)
 */
public record MeResponse(boolean authenticated, UserView user, RestrictionView restriction, String adminUrl) {

    public static MeResponse anonymous() {
        return new MeResponse(false, null, null, null);
    }
}
