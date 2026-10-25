package com.community.account.dto.response;

import com.community.sociallogin.dto.response.MeSocialProviderView;
import java.util.List;

/**
 * {@code GET /api/me/account} — 내 계정.
 *
 * @param email         지금 로그인 이메일. 소셜 가입자가 이메일을 안 줬으면 null
 * @param emailVerified 그 이메일을 인증했는가
 * @param hasPassword   비밀번호가 있는 계정인가(없으면 비밀번호·이메일 변경을 쓸 수 없다) — {@code GET /api/me/social} 과 같은 값
 * @param pendingEmail  이메일 변경을 요청했고 아직 쓸 수 있는 변경 링크가 있으면 그 새 주소(앞 한 글자만 남기고 가림, {@code m***@example.com}), 아니면 null
 * @param providers     소셜 공급자 3종 — {@code GET /api/me/social} 의 줄과 같다
 */
public record AccountResponse(String email, boolean emailVerified, boolean hasPassword, String pendingEmail, List<MeSocialProviderView> providers) {
}
