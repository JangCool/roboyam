package com.community.social.dto.response;

/**
 * 이력 한 줄의 바뀐 칸 하나.
 *
 * @param field    {@code clientId} · {@code clientSecret} · {@code enabled}
 * @param labelKey 칸 이름의 화면 i18n 키({@code settingHistory.field.<field>}) — 문구는 admin-web 이 정한다
 * @param before   전 값(만들기면 null). clientId 는 {@code ****abcd}, clientSecret 은 표시 코드만, enabled 는 {@code true}·{@code false}
 * @param after    후 값(지우기면 null). clientSecret 은 {@code SET}(처음 넣음)·{@code CHANGED}(바꿈)·{@code CLEARED}(지움) — 시크릿 값은 어디에도 없다
 */
public record SocialSettingHistoryChangeView(String field, String labelKey, String before, String after) {
}
