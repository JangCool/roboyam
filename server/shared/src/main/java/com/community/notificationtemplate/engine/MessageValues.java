package com.community.notificationtemplate.engine;

import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageTargetType;
import java.time.Instant;

/**
 * 문구 하나의 값 — 기본 문구(코드가 번들에서 만든다)와 사용자 문구(DB)가 같은 모양이다. 칸의 뜻은 채널마다 다르다({@code V57} 주석 · 계약 「개정 4」).
 *
 * @param id         기본 문구는 {@code builtin-<대상 코드>-<채널>-<언어>}, 사용자 문구는 Snowflake 글자
 * @param name       이름 — 기본 문구는 null(관리 API 가 요청 언어로 「기본 문구」를 붙인다)
 * @param templateId 채널의 템플릿 id — 앱 알림은 null
 * @param updatedAt  기본 문구는 null
 */
public record MessageValues(String id, MessageTargetType targetType, String targetCode, MessageChannel channel, String locale, String name, String templateId,
                            String subject, String body, boolean builtin, Instant updatedAt, Long updatedBy) {
}
