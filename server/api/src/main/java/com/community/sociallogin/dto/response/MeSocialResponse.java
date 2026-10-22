package com.community.sociallogin.dto.response;

import java.util.List;

/**
 * {@code GET /api/me/social} — 내 소셜 연결 관리 화면.
 *
 * @param hasPassword 비밀번호가 있는 계정인가(없으면 소셜 전용 — 마지막 연결은 해제할 수 없다)
 * @param providers   공급자 3종(카카오·네이버·구글 순) 전부
 */
public record MeSocialResponse(boolean hasPassword, List<MeSocialProviderView> providers) {
}
