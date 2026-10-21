package com.community.auth.service;

import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

/**
 * 인증 메일의 제목과 본문. 본문은 인라인 스타일만 쓴 단순한 HTML 이다(메일 앱은 CSS 를 거의 지원하지 않는다).
 * 사이트 이름·닉네임·링크는 HTML 로 가려서 넣는다. 문구는 메시지 번들({@code mail.*})에서 <b>메일을 받는 사람이 요청한 언어</b>로 만든다 —
 * 발송은 요청 스레드 안에서 일어나므로 그 요청의 {@code Accept-Language} 가 언어다.
 */
final class AuthMailTemplate {

    /** 사이트 설정이 없을 때 쓰는 이름 */
    private static final String DEFAULT_SITE_NAME = "ROBOYAM";

    record Mail(String subject, String html) {
    }

    private AuthMailTemplate() {
    }

    /** 이메일 인증 링크 메일 */
    static Mail verification(Messages messages, Locale locale, String siteName, String nickname, String link, int validHours) {
        String site = HtmlUtils.htmlEscape(orDefault(siteName));
        String name = HtmlUtils.htmlEscape(nickname == null ? messages.get(key("mail.verify.defaultName"), locale) : nickname);
        return new Mail(messages.get(key("mail.verify.subject", orDefault(siteName)), locale),
                layout(messages, locale, site, messages.get(key("mail.verify.title"), locale),
                        p(messages.get(key("mail.verify.greeting", name, site), locale)),
                        p(messages.get(key("mail.verify.guide"), locale)),
                        button(messages.get(key("mail.verify.button"), locale), link),
                        note(messages.get(key("mail.verify.validity", String.valueOf(validHours)), locale)),
                        note(HtmlUtils.htmlEscape(link)),
                        note(messages.get(key("mail.verify.ignore"), locale))));
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
    private static String layout(Messages messages, Locale locale, String brandHtml, String title, String... body) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"").append(locale.getLanguage()).append("\"><body style=\"margin:0;padding:24px;background:#f3f4f6;")
                .append("font-family:-apple-system,'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif\">")
                .append("<div style=\"max-width:520px;margin:0 auto;background:#ffffff;border:1px solid #e2e4e8;")
                .append("border-radius:12px;padding:28px\">")
                .append("<p style=\"margin:0 0 18px;font-size:18px;font-weight:700;letter-spacing:.5px;color:#16181d\">").append(brandHtml).append("</p>")
                .append("<h1 style=\"margin:0 0 16px;font-size:18px;color:#16181d\">").append(title).append("</h1>");
        for (String part : body) {
            html.append(part);
        }
        html.append("<hr style=\"border:0;border-top:1px solid #e2e4e8;margin:22px 0\">")
                .append("<p style=\"margin:0;font-size:11px;color:#8b92a0\">").append(messages.get(key("mail.common.noReply"), locale)).append("</p>")
                .append("</div></body></html>");
        return html.toString();
    }
}
