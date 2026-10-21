package com.community.audit.dto;

import java.time.Instant;

/**
 * 감사 기록 한 줄(읽기용) — 표에 있는 값 그대로. {@code actorName} 은 지금의 운영자 이름이다(운영자 행이 지워졌거나 행위자가 없으면 null).
 *
 * @param actorType {@code OPERATOR} · {@code SYSTEM} · {@code ANONYMOUS}
 * @param result    {@code SUCCESS} · {@code FAILURE} · {@code DENIED}
 * @param detail    짧은 JSON 문자열. 없으면 null
 */
public record AuditRow(long id, Instant occurredAt, String actorType, Long actorId, String actorName, String action, String result, String targetType, String targetId,
                       String ip, String detail) {
}
