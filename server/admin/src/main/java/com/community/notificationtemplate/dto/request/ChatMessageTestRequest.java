package com.community.notificationtemplate.dto.request;

/**
 * {@code POST /api/admin/notification-messages/chat-test} — Slack·Mattermost 문구를 고른 템플릿을 씌워 그 종류의 알림 도구 하나로 시험 게시(변수는 견본 값).
 *
 * @param channel   {@code SLACK} · {@code MATTERMOST}
 * @param channelId 알림 도구 id({@code /api/admin/notification-channels} 의 그 종류 도구)
 */
public record ChatMessageTestRequest(MessageTargetRefInput target, String channel, String locale, String templateId, String subject, String body, String channelId) {
}
