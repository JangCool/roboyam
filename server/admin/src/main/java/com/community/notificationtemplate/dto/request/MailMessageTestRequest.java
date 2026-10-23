package com.community.notificationtemplate.dto.request;

/**
 * {@code POST /api/admin/notification-messages/test} — 고치는 중인 값으로 이메일 시험 발송. 받는 주소는 이 호출에만 쓰고 저장·로그·감사에 남기지 않는다.
 *
 * @param templateId 씌울 메일 템플릿 — 없으면 받는 사람의 기본 메일 템플릿
 */
public record MailMessageTestRequest(MessageTargetRefInput target, String locale, String templateId, String subject, String bodyHtml, String to) {

    @Override
    public String toString() {
        return "MailMessageTestRequest[target=" + target + ", locale=" + locale + ", templateId=" + templateId + ", to=(감춤)]";
    }
}
