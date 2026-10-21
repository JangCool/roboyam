package com.community.member.dto.response;

/** 소셜 연동 공급자 한 개의 연결 여부 — {@code provider} 는 {@code KAKAO} · {@code NAVER} · {@code GOOGLE} */
public record MemberSocialLink(String provider, boolean linked) {
}
