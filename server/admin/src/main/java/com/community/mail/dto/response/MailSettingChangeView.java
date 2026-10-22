package com.community.mail.dto.response;

/**
 * 이력 한 줄의 바뀐 칸 하나.
 *
 * @param field  칸 이름(화면 문구 키로 쓴다): {@code host} · {@code port} · {@code security} · {@code username} · {@code password} · {@code fromAddress}
 * @param label  칸 이름을 요청 언어로 푼 문구(서버 번들 {@code mail.field.<field>})
 * @param before 이전 값(없었으면 null). 비밀번호는 늘 null
 * @param after  새 값(지웠으면 null). 비밀번호는 늘 null
 * @param change 비밀번호만 {@code SET} · {@code CHANGED} · {@code CLEARED}, 다른 칸은 null
 */
public record MailSettingChangeView(String field, String label, String before, String after, String change) {
}
