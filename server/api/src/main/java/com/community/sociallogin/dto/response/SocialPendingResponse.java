package com.community.sociallogin.dto.response;

/**
 * {@code GET /api/auth/social/pending} — 소셜 가입을 이어가는 중인 사람. 공급자 회원 번호·이메일 원문은 내지 않는다.
 *
 * @param provider          공급자 id({@code kakao} · {@code naver} · {@code google})
 * @param providerName      요청 언어의 공급자 이름
 * @param suggestedNickname 공급자가 「닉네임」으로 준 값 중 닉네임 규칙에 맞는 것(실명은 쓰지 않아 구글은 늘 null). 없으면 null
 * @param emailMasked       {@code a***@example.com} 모양. 공급자가 이메일을 안 줬으면 null
 * @param emailVerified     공급자가 이메일을 확인했다고 했는가 — false 면 가입 뒤 이메일 인증이 필요하다(이메일이 없으면 false)
 * @param next              가입 뒤 돌아갈 같은 사이트 상대 경로. 없으면 null(화면은 {@code /} 로)
 */
public record SocialPendingResponse(String provider, String providerName, String suggestedNickname, String emailMasked, boolean emailVerified,
                                    String next) {
}
