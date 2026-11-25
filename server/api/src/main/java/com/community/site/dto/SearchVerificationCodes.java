package com.community.site.dto;

/**
 * 검색 엔진 소유 확인 코드(V271 — 관리자 설정 > 일반 「검색 엔진 등록」)의 사본 칸. 값이 있는 것만 {@code index.html} 머리에 메타 태그로 넣는다({@code IndexHtmlRenderer}).
 * 값은 관리자 서버가 검사한 영문·숫자·{@code -}·{@code _} 1~100자다.
 *
 * @param naver  {@code <meta name="naver-site-verification">} — 없으면 null
 * @param google {@code <meta name="google-site-verification">} — 없으면 null
 * @param bing   {@code <meta name="msvalidate.01">} — 없으면 null
 */
public record SearchVerificationCodes(String naver, String google, String bing) {

    /** 하나도 없음(설정 행이 없을 때 포함) */
    public static final SearchVerificationCodes NONE = new SearchVerificationCodes(null, null, null);
}
