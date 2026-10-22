package com.community.notification.service;

import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

/**
 * 알림 이메일의 제목과 본문 — 한 통 형식(제목, 본문, 링크 단추). 기존 운영자 메일과 같은 방식의 인라인 스타일 HTML 이다(메일 앱은 CSS 를 거의 지원하지 않는다).
 * 제목·본문은 이미 받는 사람의 언어로 푼 글자이고 여기서 HTML 로 가려서 넣는다(운영자 이름 같은 입력 값이 섞여 있다). 링크는 관리자 앱 주소({@code ADMIN_URL})가 있으면
 * 절대 주소 단추이고, 없으면 경로만 글자로 보인다.
 */
final class NotificationMailTemplate {

    private static final String DEFAULT_SITE_NAME = "ROBOYAM";

    /**
     * @param subject         머리말이 없을 때의 제목 `[사이트 이름] 제목`
     * @param untaggedSubject 메일 설정에 제목 머리말이 있을 때 머리말 뒤에 붙일 제목 — 자체 `[사이트 이름]` 을 뺀 알림 제목(머리말과 겹치지 않게, {@code MailDispatcher#sendTagged})
     */
    record Mail(String subject, String untaggedSubject, String html) {
    }

    private NotificationMailTemplate() {
    }

    /**
     * @param body    알림 본문(없을 수 있다)
     * @param adminUrl 끝의 {@code /} 를 뗀 관리자 앱 주소(없을 수 있다)
     * @param linkPath 이동 경로(없을 수 있다)
     */
    static Mail build(Messages messages, Locale locale, String siteName, String title, String body, String adminUrl, String linkPath) {
        String site = siteName == null || siteName.isBlank() ? DEFAULT_SITE_NAME : siteName;
        StringBuilder content = new StringBuilder();
        if (body != null) {
            content.append(p(HtmlUtils.htmlEscape(body)));
        }
        if (linkPath != null) {
            if (adminUrl != null) {
                String link = adminUrl + linkPath;
                content.append(button(messages.get(new MessageRef("notification.mail.open"), locale), link));
                content.append(note(HtmlUtils.htmlEscape(link)));
            } else {
                content.append(note(HtmlUtils.htmlEscape(linkPath)));
            }
        }
        content.append(note(HtmlUtils.htmlEscape(messages.get(new MessageRef("notification.mail.reason"), locale))));
        String subject = messages.get(new MessageRef("notification.mail.subject", site, title), locale);
        return new Mail(subject, title, layout(messages, locale, HtmlUtils.htmlEscape(site), HtmlUtils.htmlEscape(title), content.toString()));
    }

    private static String p(String text) {
        return "<p style=\"margin:0 0 14px;font-size:15px;line-height:1.6;color:#16181d\">" + text + "</p>";
    }

    private static String note(String text) {
        return "<p style=\"margin:0 0 10px;font-size:12px;line-height:1.5;color:#5b6270;word-break:break-all\">" + text + "</p>";
    }

    private static String button(String label, String link) {
        return "<p style=\"margin:20px 0\"><a href=\"" + HtmlUtils.htmlEscape(link)
                + "\" style=\"display:inline-block;padding:12px 22px;background:#1c5fcf;color:#ffffff;"
                + "text-decoration:none;border-radius:8px;font-weight:600;font-size:15px\">" + HtmlUtils.htmlEscape(label) + "</a></p>";
    }

    /** @param brandHtml 이미 HTML 로 가려진 사이트 이름 · @param titleHtml 이미 HTML 로 가려진 제목 */
    private static String layout(Messages messages, Locale locale, String brandHtml, String titleHtml, String body) {
        return "<!DOCTYPE html><html lang=\"" + locale.getLanguage() + "\"><body style=\"margin:0;padding:24px;background:#f3f4f6;"
                + "font-family:-apple-system,'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif\">"
                + "<div style=\"max-width:520px;margin:0 auto;background:#ffffff;border:1px solid #e2e4e8;border-radius:12px;padding:28px\">"
                + "<p style=\"margin:0 0 18px;font-size:18px;font-weight:700;letter-spacing:.5px;color:#16181d\">" + brandHtml + "</p>"
                + "<h1 style=\"margin:0 0 16px;font-size:18px;color:#16181d\">" + titleHtml + "</h1>"
                + body
                + "<hr style=\"border:0;border-top:1px solid #e2e4e8;margin:22px 0\">"
                + "<p style=\"margin:0;font-size:11px;color:#8b92a0\">" + messages.get(new MessageRef("mail.common.noReply"), locale) + "</p>"
                + "</div></body></html>";
    }
}
