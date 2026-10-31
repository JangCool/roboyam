package com.community.zconfiguration.security;

/**
 * 공개 광고 집계 주소 — 보안 설정(로그인 없이 · <b>CSRF 검사 없이</b> 받기)과 컨트롤러가 같은 값을 쓴다. 화면이 페이지를 떠날 때 {@code navigator.sendBeacon} 으로 보내는데
 * sendBeacon 은 머리글({@code X-XSRF-TOKEN})을 붙일 수 없어서다(ad-contract 「공개 노출 목업」 확정 결정 2026-11-01 — 이 두 주소만). 남이 대신 보내도 셈만 늘고(같은 방문자·버전·자리
 * 하루 1번) 상태를 바꾸지 않는다.
 */
public final class PublicAdPaths {

    public static final String IMPRESSIONS = "/api/ads/impressions";
    public static final String CLICKS = "/api/ads/*/clicks";

    private PublicAdPaths() {
    }
}
