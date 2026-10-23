package com.community.notificationtemplate.dto.request;

/**
 * {@code POST /api/admin/notification-messages/preview} — 이메일만(Slack·Mattermost·카카오톡·앱 알림 미리보기는 화면이 견본 값으로 그린다).
 *
 * @param target     대상(변수 견본 값) — 메일 템플릿 편집 화면은 비운다(템플릿 쓰는 대상의 공통 변수만)
 * @param templateId 씌울 메일 템플릿(저장된 것) — {@code template} 이 없을 때. 둘 다 없으면 받는 사람의 기본 메일 템플릿
 * @param template   메일 템플릿을 저장 전 값으로 미리 볼 때(메일 템플릿 편집 화면)
 */
public record MailPreviewRequest(MessageTargetRefInput target, String locale, String subject, String bodyHtml, String templateId, MailTemplateSaveRequest template) {
}
