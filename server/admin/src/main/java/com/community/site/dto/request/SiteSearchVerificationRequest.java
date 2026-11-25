package com.community.site.dto.request;

/**
 * 검색 엔진 소유 확인 코드 저장 요청({@code PUT /api/admin/site/search-verification}) — 셋을 한꺼번에 저장한다. 칸마다 메타 태그 {@code content} 값 하나.
 * 앞뒤 공백을 뗀 뒤 비면 null(그 태그를 넣지 않는다), 아니면 영문·숫자·{@code -}·{@code _} 1~100자. 검사 문구가 칸별이라 서비스가 한꺼번에 검사한다.
 *
 * @param naver  {@code naver-site-verification}
 * @param google {@code google-site-verification}
 * @param bing   {@code msvalidate.01}
 */
public record SiteSearchVerificationRequest(String naver, String google, String bing) {
}
