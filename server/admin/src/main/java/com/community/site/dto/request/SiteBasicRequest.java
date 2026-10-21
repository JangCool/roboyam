package com.community.site.dto.request;

/**
 * 사이트 기본 정보 저장 요청 — 이름·한 줄 소개. 공개 주소·로고는 이 요청이 건드리지 않는다. 검사 문구가 칸(siteName·tagline)별로 나가므로 서비스가 한꺼번에 검사한다.
 *
 * @param siteName 1~50자(앞뒤 공백을 자른 뒤), 필수
 * @param tagline  0~100자. 비우면(null·빈 글자) 문구 없음
 */
public record SiteBasicRequest(String siteName, String tagline) {
}
