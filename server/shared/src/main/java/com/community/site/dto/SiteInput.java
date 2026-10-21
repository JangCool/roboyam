package com.community.site.dto;

/**
 * 사이트 이름·공개 주소·슬로건 입력(설치 마법사의 {@code site}). 검사는 서비스가 모든 위반을 한 번에 모아 돌려준다.
 *
 * @param name      1~50자
 * @param publicUrl {@code http(s)://호스트[:포트]}, 끝 {@code /} 없음
 * @param tagline   슬로건(한 줄 소개) — 선택. {@code null}·빈 글자는 "문구 없음", 있으면 앞뒤 공백을 뗀 1~100자
 */
public record SiteInput(String name, String publicUrl, String tagline) {
}
