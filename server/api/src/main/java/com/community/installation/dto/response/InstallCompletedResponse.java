package com.community.installation.dto.response;

/**
 * 설치 완료 응답.
 *
 * @param installed 늘 true
 * @param demo      데모 데이터 — {@code NONE}(구조만으로 설치) · {@code RUNNING}(요청과 따로 넣는 중 — 결과는 {@code GET …/demo})
 */
public record InstallCompletedResponse(boolean installed, String demo) {

    public static final String DEMO_NONE = "NONE";
    public static final String DEMO_RUNNING = "RUNNING";
}
