package com.community.notificationtemplate.dto;

/**
 * 문구 · 템플릿 이력의 바뀐 칸 하나 — 이력 표 {@code changes} 배열의 원소 모양({@code {field, before, after}}, 설정 이력과 같다).
 * 색·쓰는 대상은 코드로 담고 조회 때 요청 언어의 이름으로 푼다. 본문·틀은 글자 요약(80자), 템플릿은 그때의 이름, 켜고 끄기는 {@code "true"}/{@code "false"}.
 */
public record TemplateChange(String field, String before, String after) {
}
