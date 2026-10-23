package com.community.adminauth.dto.response;

import com.community.mfa.dto.response.AdminLoginMfa;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 로그인 상태({@code GET /api/admin/auth/me}) · 로그인 응답 공통. 비로그인도 오류가 아니라 {@code authenticated: false} 다.
 *
 * @param siteName          사이트 이름 — 비로그인에도 준다(로그인 화면의 로고·탭 제목). 설치 전 등으로 못 주면 null
 * @param siteLogoUrl       일반 설정에서 올린 로고의 {@code <img src>} — 그림 파일이면 {@code /media/…} 주소, SVG 면 서버가 만든 data URI. 로고가 없거나 설치 전이면 null
 * @param mfa               관리자 2차 인증 단계 — {@code VERIFY}·{@code ENROLL} 이면 2차 대기 세션({@code authenticated:false}), {@code GRACE} 면 로그인됐고 등록 안내만.
 *                          2차 인증 없이 들어갔거나 비로그인이면 null
 * @param recoveryCodesLeft 로그인 2단계를 복구 코드로 통과했을 때만 남은 개수(그 밖은 칸이 나가지 않는다)
 */
public record AdminMeResponse(boolean authenticated, AdminUserView user, String siteName, String siteLogoUrl, AdminLoginMfa mfa,
                              @JsonInclude(JsonInclude.Include.NON_NULL) Integer recoveryCodesLeft) {
}
