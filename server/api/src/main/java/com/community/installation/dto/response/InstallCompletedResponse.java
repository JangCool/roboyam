package com.community.installation.dto.response;

/**
 * 설치 완료 응답.
 *
 * @param installed 늘 true
 * @param demo      데모 데이터 — {@code NONE}(구조만으로 설치) · {@code APPLIED}(넣음) · {@code FAILED}(넣지 못해 건너뜀 — 설치는 끝났고 구조만 있다)
 */
public record InstallCompletedResponse(boolean installed, String demo) {

    public static final String DEMO_NONE = "NONE";
}
