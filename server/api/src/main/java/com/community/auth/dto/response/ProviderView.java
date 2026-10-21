package com.community.auth.dto.response;

/** 쓸 수 있는 소셜 로그인 공급자 하나. {@code id} 는 소문자({@code kakao} · {@code naver} · {@code google})이고 {@code name} 은 요청 언어(Accept-Language)로 푼 표시 이름이다. */
public record ProviderView(String id, String name) {
}
