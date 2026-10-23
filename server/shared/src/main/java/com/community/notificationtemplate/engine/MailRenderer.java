package com.community.notificationtemplate.engine;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 이메일 한 통 그리기 — 문구(제목·간단 서식 본문·버튼 블록)에 변수 값을 채우고 메일 템플릿(머리말·틀·꼬리말)을 씌운 <b>문서 HTML</b>.
 * 메일 앱은 CSS 를 거의 받지 않으므로 지금 서버 메일({@code NotificationMailTemplate} · {@code AuthMailTemplate})과 같은 인라인 스타일을 쓴다.
 * admin-web {@code UtilMailTemplate.render}(목업 미리보기)와 같은 모양이다.
 *
 * <ul>
 *   <li>변수 값은 HTML 로 가린다. 링크 변수 값은 버튼의 {@code href} 로만 주소가 된다 — http·https 가 아니면 {@code #}. 본문 글자 속 링크 값은 가린 글자다.</li>
 *   <li>꼬리말은 평문 — 줄마다 값을 채운 뒤 통째로 가린다(줄바꿈은 문단).</li>
 *   <li>띠 색이 있으면 머리말(로고·사이트 이름)을 띠 안에 흰 글씨로, 머리말이 비었으면 얇은 띠만.</li>
 * </ul>
 * 본문·틀은 저장 때 정화된 HTML 이어야 한다({@link MailHtml}). 제목은 값을 채운 평문 한 줄이다(머리말 규칙은 보낼 때 {@code MailDispatcher#sendTagged}).
 */
public final class MailRenderer {

    private static final Pattern BUTTON_BLOCK = Pattern.compile(
            "<p data-mail-button(?:=\"[^\"]*\")?>\\s*<a href=\"\\{\\{\\s*([A-Za-z][A-Za-z0-9_]*)\\s*\\}\\}\"[^>]*>([\\s\\S]*?)</a>\\s*</p>");
    private static final Pattern CONTENT_PARAGRAPH = Pattern.compile("<p>\\s*\\{\\{\\s*content\\s*\\}\\}\\s*</p>");
    private static final Pattern CONTENT = Pattern.compile("\\{\\{\\s*content\\s*\\}\\}");
    private static final Pattern LINE = Pattern.compile("\\r?\\n");

    private static final String BODY = "margin:0;padding:24px;background:#f3f4f6;font-family:-apple-system,'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif";
    private static final String CARD = "max-width:520px;margin:0 auto;background:#ffffff;border:1px solid #e2e4e8;border-radius:12px;padding:28px";
    private static final String BRAND = "margin:0 0 18px;font-size:18px;font-weight:700;letter-spacing:.5px;color:#16181d";
    private static final String BAND = "margin:-28px -28px 22px;padding:18px 28px;border-radius:11px 11px 0 0";
    private static final String BAND_STRIP = "margin:-28px -28px 22px;height:6px;border-radius:11px 11px 0 0";
    private static final String BAND_BRAND = "margin:0;font-size:18px;font-weight:700;letter-spacing:.5px;color:#ffffff";
    private static final String LOGO = "display:block;max-height:40px;max-width:200px;margin:0 0 12px";
    private static final String P = "margin:0 0 14px;font-size:15px;line-height:1.6;color:#16181d;word-break:break-word";
    private static final String FOOT = "margin:0 0 6px;font-size:11px;line-height:1.5;color:#8b92a0";
    private static final String HR = "border:0;border-top:1px solid #e2e4e8;margin:22px 0";
    private static final String QUOTE = "margin:0 0 14px;padding:4px 0 4px 12px;border-left:3px solid #e2e4e8;color:#5b6270";
    private static final String LIST = "margin:0 0 14px;padding-left:22px;font-size:15px;line-height:1.6;color:#16181d";
    private static final String LINK = "color:#1c5fcf";
    private static final String BUTTON = "display:inline-block;padding:12px 22px;background:#1c5fcf;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:600;font-size:15px";

    /** 그린 결과 — 제목(값을 채운 한 줄, 머리말 없음)과 문서 HTML */
    public record Result(String subject, String html) {
    }

    private MailRenderer() {
    }

    /**
     * @param locale      {@code ko} · {@code en} — 틀·꼬리말 언어와 문서 {@code lang}
     * @param subject     문구 제목
     * @param bodyHtml    문구 본문(정화된 간단 서식 HTML)
     * @param template    씌울 메일 템플릿
     * @param value       변수 이름 → 가리기 전 값(모르는 변수면 null)
     * @param keepUnknown 모르는 변수를 그대로 둘까(미리보기) · 지울까(실제 발송)
     * @param siteName    사이트 이름(머리말)
     * @param logoUrl     로고 주소(없으면 null)
     */
    public static Result render(String locale, String subject, String bodyHtml, MailTemplateValues template, Function<String, String> value, boolean keepUnknown,
                                String siteName, String logoUrl) {
        Function<String, String> escaped = name -> {
            String raw = value.apply(name);
            return raw == null ? null : TemplateText.escape(raw);
        };

        // 버튼 블록 → 서버 메일과 같은 단추(주소는 링크 변수 값 — http·https 만)
        Matcher button = BUTTON_BLOCK.matcher(bodyHtml == null ? "" : bodyHtml);
        StringBuilder withButton = new StringBuilder();
        while (button.find()) {
            String href = value.apply(button.group(1));
            String safeHref = TemplateText.isWebUrl(href) ? TemplateText.escape(href.strip()) : "#";
            button.appendReplacement(withButton, Matcher.quoteReplacement(
                    "<p style=\"margin:20px 0\"><a href=\"" + safeHref + "\" style=\"" + BUTTON + "\">" + button.group(2) + "</a></p>"));
        }
        button.appendTail(withButton);
        String body = styled(TemplateText.fill(withButton.toString(), escaped, keepUnknown));

        String[] frame = splitFrame(template.frame(locale));
        String before = styled(TemplateText.fill(frame[0], escaped, keepUnknown));
        String after = styled(TemplateText.fill(frame[1], escaped, keepUnknown));

        StringBuilder footer = new StringBuilder();
        String footerText = template.footer(locale) == null ? "" : template.footer(locale).strip();
        if (!footerText.isEmpty()) {
            for (String line : LINE.split(footerText, -1)) {
                String filled = TemplateText.escape(TemplateText.fill(line, value, keepUnknown));
                footer.append("<p style=\"").append(FOOT).append("\">").append(filled.isEmpty() ? "&nbsp;" : filled).append("</p>");
            }
        }

        String band = template.headerColor() == null ? null : template.headerColor().hex();
        String site = siteName == null ? "" : siteName;
        String logo = template.showLogo() && logoUrl != null && !logoUrl.isBlank()
                ? "<img src=\"" + TemplateText.escape(logoUrl) + "\" alt=\"" + TemplateText.escape(site) + "\" style=\"" + LOGO + "\">" : "";
        String brand = template.showSiteName() && !site.isBlank() ? "<p style=\"" + (band != null ? BAND_BRAND : BRAND) + "\">" + TemplateText.escape(site) + "</p>" : "";
        String head = logo + brand;
        if (band != null) {
            head = head.isEmpty() ? "<div style=\"" + BAND_STRIP + ";background:" + band + "\"></div>"
                    : "<div style=\"" + BAND + ";background:" + band + "\">" + head + "</div>";
        }

        String html = "<!DOCTYPE html><html lang=\"" + (locale == null ? "ko" : locale) + "\"><head><meta charset=\"utf-8\"><base target=\"_blank\"></head>"
                + "<body style=\"" + BODY + "\"><div style=\"" + CARD + "\">"
                + head + before + body + after
                + (footer.isEmpty() ? "" : "<hr style=\"" + HR + "\">" + footer)
                + "</div></body></html>";
        String filledSubject = TemplateText.oneLine(TemplateText.fill(subject, value, keepUnknown));
        return new Result(filledSubject, html);
    }

    /** 틀을 {@code {{content}}} 앞·뒤로 나눈다(그 한 줄만 있는 문단은 문단째) */
    private static String[] splitFrame(String frame) {
        String source = frame == null ? "" : frame;
        Matcher paragraph = CONTENT_PARAGRAPH.matcher(source);
        if (paragraph.find()) {
            return new String[] {source.substring(0, paragraph.start()), source.substring(paragraph.end())};
        }
        Matcher content = CONTENT.matcher(source);
        if (content.find()) {
            return new String[] {source.substring(0, content.start()), source.substring(content.end())};
        }
        return new String[] {source, ""};
    }

    /** 간단 서식 태그에 메일용 인라인 스타일을 붙인다 */
    private static String styled(String html) {
        return html.replace("<p>", "<p style=\"" + P + "\">")
                .replace("<blockquote>", "<blockquote style=\"" + QUOTE + "\">")
                .replace("<ul>", "<ul style=\"" + LIST + "\">")
                .replace("<ol>", "<ol style=\"" + LIST + "\">")
                .replaceAll("<a (?![^>]*style=)", "<a style=\"" + LINK + "\" ");
    }
}
