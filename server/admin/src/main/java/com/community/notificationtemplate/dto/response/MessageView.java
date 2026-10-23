package com.community.notificationtemplate.dto.response;

import java.time.Instant;

/**
 * 문구 하나 — 기본 문구({@code builtin=true}, 코드가 준다)는 읽기 전용이다. 칸의 뜻은 채널마다 다르다(계약 「개정 4」 · {@code V57} 주석).
 *
 * @param id            기본 문구는 {@code builtin-<대상 코드>-<채널>-<언어>}, 사용자 문구는 Snowflake(문자열)
 * @param name          기본 문구는 요청 언어의 「기본 문구」
 * @param templateId    채널의 템플릿 id — 앱 알림은 null
 * @param updatedAt     기본 문구는 null
 * @param updatedByName 기본 문구는 null, 지워진 운영자는 대체 문구
 */
public record MessageView(String id, MessageTargetRefView target, String channel, String name, String locale, String templateId, String subject, String body,
                          boolean builtin, Instant updatedAt, String updatedByName) {
}
