package com.community.notificationtemplate.dto.request;

/**
 * {@code POST·PUT /api/admin/notification-messages/messages[/{id}]} — 고치기({@code PUT})는 대상·채널을 바꾸지 못한다(서버가 원래 값으로 본다).
 *
 * @param templateId 그 채널의 템플릿 id — 앱 알림은 null(보내도 무시)
 * @param body       이메일은 간단 서식 HTML(서버가 정화), Slack·Mattermost 는 그 앱 표기, 앱 알림은 평문
 */
public record MessageSaveRequest(String targetType, String targetCode, String channel, String name, String locale, String templateId, String subject, String body) {
}
