package com.community.operator.service;

import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

/**
 * 운영자 메일(계정 안내·초대)의 제목과 본문. 본문은 인라인 스타일만 쓴 단순한 HTML 이다(메일 앱은 CSS 를 거의 지원하지 않는다).
 * 사이트 이름·운영자 이름·링크는 HTML 로 가려서 넣는다. 문구는 메시지 번들({@code mail.operator*})에서 요청한 운영자의 언어로 만든다 — 발송은 요청 스레드 안에서 일어난다.
 */
public final class OperatorMailTemplate {

    /** 사이트 설정이 없을 때 쓰는 이름 */
    private static final String DEFAULT_SITE_NAME = "ROBOYAM";

    public record Mail(String subject, String html) {
    }

    private OperatorMailTemplate() {
    }

    /** 운영자를 바로 만들었을 때 — 임시 비밀번호 안내. {@code adminUrl} 은 없을 수 있다 */
    static Mail credentials(Messages messages, Locale locale, String siteName, String operatorName, String temporaryPassword, String adminUrl) {
        String site = HtmlUtils.htmlEscape(orDefault(siteName));
        String name = HtmlUtils.htmlEscape(operatorName);
        StringBuilder body = new StringBuilder();
        body.append(p(messages.get(key("mail.operatorCredentials.greeting", name, site), locale)));
        body.append(p(messages.get(key("mail.operatorCredentials.guide"), locale)));
        body.append("<p style=\"margin:16px 0;padding:14px 16px;background:#f3f4f6;border-radius:8px;font-size:13px;color:#5b6270\">")
                .append(messages.get(key("mail.operatorCredentials.passwordLabel"), locale))
                .append("<br><span style=\"font-size:18px;font-weight:700;letter-spacing:1px;color:#16181d;font-family:Consolas,monospace\">")
                .append(HtmlUtils.htmlEscape(temporaryPassword)).append("</span></p>");
        if (adminUrl != null && !adminUrl.isBlank()) {
            body.append(note(messages.get(key("mail.operatorCredentials.login", HtmlUtils.htmlEscape(adminUrl)), locale)));
        }
        body.append(note(messages.get(key("mail.operatorCredentials.keep"), locale)));
        return new Mail(messages.get(key("mail.operatorCredentials.subject", orDefault(siteName)), locale),
                layout(messages, locale, site, messages.get(key("mail.operatorCredentials.title"), locale), body.toString()));
    }

    /** 초대 링크 메일 */
    static Mail invitation(Messages messages, Locale locale, String siteName, String link, int validHours) {
        String site = HtmlUtils.htmlEscape(orDefault(siteName));
        String body = p(messages.get(key("mail.operatorInvite.greeting", site), locale))
                + p(messages.get(key("mail.operatorInvite.guide"), locale))
                + button(messages.get(key("mail.operatorInvite.button"), locale), link)
                + note(messages.get(key("mail.operatorInvite.validity", String.valueOf(validHours)), locale))
                + note(HtmlUtils.htmlEscape(link))
                + note(messages.get(key("mail.operatorInvite.ignore"), locale));
        return new Mail(messages.get(key("mail.operatorInvite.subject", orDefault(siteName)), locale),
                layout(messages, locale, site, messages.get(key("mail.operatorInvite.title"), locale), body));
    }

    /** 비밀번호 재설정 승인번호 메일 — 번호·유효 분·「요청하지 않았다면 무시」 안내. 번호는 숫자뿐이지만 HTML 로 가려서 넣는다 */
    public static Mail passwordResetCode(Messages messages, Locale locale, String siteName, String code, int validMinutes) {
        String site = HtmlUtils.htmlEscape(orDefault(siteName));
        String body = p(messages.get(key("mail.operatorReset.greeting", site), locale))
                + p(messages.get(key("mail.operatorReset.guide"), locale))
                + "<p style=\"margin:16px 0;padding:14px 16px;background:#f3f4f6;border-radius:8px;font-size:13px;color:#5b6270\">"
                + messages.get(key("mail.operatorReset.codeLabel"), locale)
                + "<br><span style=\"font-size:26px;font-weight:700;letter-spacing:6px;color:#16181d;font-family:Consolas,monospace\">"
                + HtmlUtils.htmlEscape(code) + "</span></p>"
                + note(messages.get(key("mail.operatorReset.validity", String.valueOf(validMinutes)), locale))
                + note(messages.get(key("mail.operatorReset.keep"), locale))
                + note(messages.get(key("mail.operatorReset.ignore"), locale));
        return new Mail(messages.get(key("mail.operatorReset.subject", orDefault(siteName)), locale),
                layout(messages, locale, site, messages.get(key("mail.operatorReset.title"), locale), body));
    }

    private static MessageRef key(String key, Object... args) {
        return new MessageRef(key, args);
    }

    private static String orDefault(String siteName) {
        return siteName == null || siteName.isBlank() ? DEFAULT_SITE_NAME : siteName;
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
                + "text-decoration:none;border-radius:8px;font-weight:600;font-size:15px\">" + label + "</a></p>";
    }

    /** @param brandHtml 이미 HTML 로 가려진 사이트 이름 */
    private static String layout(Messages messages, Locale locale, String brandHtml, String title, String body) {
        return "<!DOCTYPE html><html lang=\"" + locale.getLanguage() + "\"><body style=\"margin:0;padding:24px;background:#f3f4f6;"
                + "font-family:-apple-system,'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif\">"
                + "<div style=\"max-width:520px;margin:0 auto;background:#ffffff;border:1px solid #e2e4e8;border-radius:12px;padding:28px\">"
                + "<p style=\"margin:0 0 18px;font-size:18px;font-weight:700;letter-spacing:.5px;color:#16181d\">" + brandHtml + "</p>"
                + "<h1 style=\"margin:0 0 16px;font-size:18px;color:#16181d\">" + title + "</h1>"
                + body
                + "<hr style=\"border:0;border-top:1px solid #e2e4e8;margin:22px 0\">"
                + "<p style=\"margin:0;font-size:11px;color:#8b92a0\">" + messages.get(key("mail.common.noReply"), locale) + "</p>"
                + "</div></body></html>";
    }
}
