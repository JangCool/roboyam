package com.community.site.dto.response;

/**
 * {@code GET·PUT /api/admin/site/search-verification} — 저장된 검색 엔진 소유 확인 코드 셋. 없으면 null(공개 사이트 머리에 그 태그를 넣지 않는다).
 *
 * @param naver  {@code <meta name="naver-site-verification">} 의 {@code content}
 * @param google {@code <meta name="google-site-verification">} 의 {@code content}
 * @param bing   {@code <meta name="msvalidate.01">} 의 {@code content}
 */
public record SiteSearchVerificationResponse(String naver, String google, String bing) {
}
