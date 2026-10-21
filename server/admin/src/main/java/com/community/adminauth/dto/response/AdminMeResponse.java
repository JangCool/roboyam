package com.community.adminauth.dto.response;

/**
 * 로그인 상태({@code GET /api/admin/auth/me}) · 로그인 응답 공통. 비로그인도 오류가 아니라 {@code authenticated: false} 다.
 *
 * @param siteName    사이트 이름 — 비로그인에도 준다(로그인 화면의 로고·탭 제목). 설치 전 등으로 못 주면 null
 * @param siteLogoUrl 일반 설정에서 올린 로고 주소 — 로고 기능이 아직 없어 늘 null(사이트 설정 3단계)
 */
public record AdminMeResponse(boolean authenticated, AdminUserView user, String siteName, String siteLogoUrl) {
}
