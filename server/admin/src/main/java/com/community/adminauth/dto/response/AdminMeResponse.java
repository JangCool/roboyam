package com.community.adminauth.dto.response;

/**
 * 로그인 상태({@code GET /api/admin/auth/me}) · 로그인 응답 공통. 비로그인도 오류가 아니라 {@code authenticated: false} 다.
 *
 * @param siteName    사이트 이름 — 비로그인에도 준다(로그인 화면의 로고·탭 제목). 설치 전 등으로 못 주면 null
 * @param siteLogoUrl 일반 설정에서 올린 로고의 {@code <img src>} — 그림 파일이면 {@code /media/…} 주소, SVG 면 서버가 만든 data URI. 로고가 없거나 설치 전이면 null
 */
public record AdminMeResponse(boolean authenticated, AdminUserView user, String siteName, String siteLogoUrl) {
}
