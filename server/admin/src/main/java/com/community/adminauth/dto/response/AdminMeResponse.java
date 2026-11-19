package com.community.adminauth.dto.response;

import com.community.mfa.dto.response.AdminLoginMfa;
import com.community.site.dto.SiteLogoUrls;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 로그인 상태({@code GET /api/admin/auth/me}) · 로그인 응답 공통. 비로그인도 오류가 아니라 {@code authenticated: false} 다.
 *
 * @param siteName          사이트 이름 — 비로그인에도 준다(로그인 화면의 로고·탭 제목). 설치 전 등으로 못 주면 null
 * @param siteLogoUrl       평소 로고 묶음의 대표 한 장의 {@code <img src>}(① 한글·밝은 칸, 비었으면 채워진 첫 칸) — 그림 파일이면 {@code /media/…} 주소, SVG 면 서버가 만든 data URI.
 *                          로고가 없거나 설치 전이면 null. 좁은 사이드바의 네모 아바타가 쓴다
 * @param mfa               관리자 2차 인증 단계 — {@code VERIFY}·{@code ENROLL} 이면 2차 대기 세션({@code authenticated:false}), {@code GRACE} 면 로그인됐고 등록 안내만.
 *                          2차 인증 없이 들어갔거나 비로그인이면 null
 * @param recoveryCodesLeft 로그인 2단계를 복구 코드로 통과했을 때만 남은 개수(그 밖은 칸이 나가지 않는다)
 * @param siteLogos         평소 로고 묶음 4칸(한글·영문 × 밝은·어두운 바탕용, SVG 는 data URI, 빈 칸 null) — 상단 로고 자리가 놓인 면에 맞는 칸을 고른다(2026-11-19). 비로그인에도 준다
 * @param siteNameInLogo    그림에 사이트 이름이 들어 있다 — 그림이 보이면 이름 글자를 붙이지 않는다
 */
public record AdminMeResponse(boolean authenticated, AdminUserView user, String siteName, String siteLogoUrl, AdminLoginMfa mfa,
                              @JsonInclude(JsonInclude.Include.NON_NULL) Integer recoveryCodesLeft, SiteLogoUrls siteLogos, boolean siteNameInLogo) {
}
