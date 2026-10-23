package com.community.notificationtemplate.dto.response;

/**
 * 이력 한 줄의 바뀐 칸 하나(설정 이력과 같은 모양).
 *
 * @param labelKey 칸 이름의 화면 i18n 키({@code settingHistory.field.<field>})
 * @param before   이전 값(만들기면 null) — 본문·틀은 글자 요약, 템플릿은 그때의 이름, 색·쓰는 대상은 요청 언어의 이름, 켜고 끄기는 {@code "true"}/{@code "false"}
 */
public record TemplateHistoryChangeView(String field, String labelKey, String before, String after) {
}
