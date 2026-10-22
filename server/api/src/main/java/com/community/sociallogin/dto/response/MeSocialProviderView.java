package com.community.sociallogin.dto.response;

/**
 * 내 소셜 연결 한 줄.
 *
 * @param id        공급자 id({@code kakao} · {@code naver} · {@code google})
 * @param name      요청 언어의 공급자 이름
 * @param linked    이 계정에 연결돼 있는가
 * @param linkable  지금 연결을 시작할 수 있는가(운영자가 켜 두고 키가 있음 + 아직 연결 안 됨)
 * @param canUnlink 연결돼 있고 해제해도 다른 로그인 수단(비밀번호 또는 다른 소셜)이 남는가
 */
public record MeSocialProviderView(String id, String name, boolean linked, boolean linkable, boolean canUnlink) {
}
