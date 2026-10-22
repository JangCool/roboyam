package com.community.mail.dto.response;

/**
 * 이력 한 줄의 바뀐 칸 하나(소셜 로그인 설정 이력 {@code SocialSettingHistoryChangeView} 와 같은 모양).
 *
 * @param field    {@code host} · {@code port} · {@code security} · {@code username} · {@code password} · {@code fromAddress}
 * @param labelKey 칸 이름의 화면 i18n 키({@code settingHistory.field.<field>}) — 문구는 admin-web 이 정한다
 * @param before   이전 값(만들기면 null). 포트도 글자. 비밀번호는 표시 코드만({@code SET} 또는 null)
 * @param after    새 값(지웠으면 null). 비밀번호는 {@code SET}(처음 넣음)·{@code CHANGED}(바꿈)·{@code CLEARED}(지움) — 비밀번호 값은 어디에도 없다
 */
public record MailSettingChangeView(String field, String labelKey, String before, String after) {
}
