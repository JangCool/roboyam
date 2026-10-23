package com.community.notificationtemplate.engine;

import com.community.notification.channel.ChannelMessageFormatter;
import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageSeverity;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Slack · Mattermost 메시지 카드 그리기 — Slack·Mattermost 템플릿(제목 줄 모양 · 색 막대 · 꼬리말 · 「열기」 버튼 · 본문 틀)에 문구(제목 한 줄 · 그 앱 표기 본문)를 씌운다.
 *
 * <ul>
 *   <li>변수 값은 지금처럼({@link ChannelMessageFormatter}) 멘션·표기 기호를 무력화해 넣는다. 링크 변수 값은 링크 구문을 깨는 글자만 인코딩한다(무력화하면 주소가 깨진다).
 *       운영자가 쓴 문구·틀의 표기(굵게·링크·목록)는 그대로다.</li>
 *   <li>Slack: 첨부 하나 = {@code color} + 블록(제목 줄과 본문 {@code section} · 「열기」 {@code actions} 버튼 · 꼬리말 {@code context} — 보낸 시각은
 *       {@code <!date^…>} 라 받는 앱이 보는 사람의 시간대로 그린다).</li>
 *   <li>Mattermost: 첨부 하나 = {@code color} · {@code title}(굵은 제목 — 굵게를 끄면 본문 첫 줄) · {@code text} · {@code footer}.
 *       Mattermost 첨부 버튼은 연동 주소가 있어야 해서 「열기」는 본문 끝의 링크로 그린다(가정). 보낸 시각은 사이트 표시 시간대로 적는다.</li>
 * </ul>
 */
public final class ChatRenderer {

    static final int TITLE_MAX = 200;
    static final int TEXT_MAX = 2900;
    static final int VALUE_MAX = 1000;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ChatRenderer() {
    }

    /**
     * @param tool        Slack · Mattermost
     * @param template    씌울 템플릿
     * @param subject     문구 제목(한 줄)
     * @param body        문구 본문(그 앱 표기, 비어도 된다)
     * @param value       변수 이름 → 가리기 전 값(모르는 변수면 null)
     * @param linkNames   링크 변수 이름들
     * @param keepUnknown 모르는 변수를 그대로 둘까(미리보기) · 지울까(실제 발송)
     * @param locale      {@code ko} · {@code en} — 틀·버튼 글자 언어
     * @param severity    색 막대·그림(없으면 안내)
     * @param siteName    꼬리말의 사이트 이름
     * @param linkUrl     「열기」 버튼 주소(이벤트의 {@code link} 값 — 없으면 null)
     * @param sentAt      보낸 시각
     * @param zone        Mattermost 꼬리말의 시각 시간대
     */
    public static RenderedChat render(MessageChannel tool, ChatTemplateValues template, String subject, String body, Function<String, String> value,
                                      Set<String> linkNames, boolean keepUnknown, String locale, MessageSeverity severity, String siteName, String linkUrl,
                                      Instant sentAt, ZoneId zone) {
        boolean slack = tool == MessageChannel.SLACK;
        Function<String, String> safe = name -> {
            String raw = value.apply(name);
            if (raw == null) {
                return null;
            }
            if (linkNames.contains(name) && TemplateText.isWebUrl(raw)) {
                return ChannelMessageFormatter.linkValue(slack, raw.strip());
            }
            return ChannelMessageFormatter.neutralizeValue(slack, raw, VALUE_MAX);
        };

        String title = cut(TemplateText.oneLine(TemplateText.fill(subject, safe, keepUnknown)), TITLE_MAX);
        String merged = TemplateText.replaceContent(template.frame(locale), body == null ? "" : body);
        String text = cut(TemplateText.fill(merged, safe, keepUnknown).strip(), TEXT_MAX);
        String icon = template.showSeverityIcon() ? (severity == null ? MessageSeverity.INFO : severity).emoji() : "";
        String color = template.color(severity).hex();

        List<String> footerParts = new ArrayList<>();
        if (template.showSiteName() && siteName != null && !siteName.isBlank()) {
            footerParts.add(ChannelMessageFormatter.neutralizeValue(slack, TemplateText.oneLine(siteName), 100));
        }
        if (template.showSentAt() && sentAt != null) {
            String stamp = STAMP.format(sentAt.atZone(zone == null ? ZoneId.of("UTC") : zone));
            footerParts.add(slack ? "<!date^" + sentAt.getEpochSecond() + "^{date_short_pretty} {time}|" + stamp + ">" : stamp);
        }
        String footer = String.join(" · ", footerParts);

        String label = null;
        String url = null;
        if (template.showOpenButton() && TemplateText.isWebUrl(linkUrl)) {
            label = cut(TemplateText.oneLine(template.openLabel(locale)), 40);
            url = linkUrl.strip();
            if (label.isEmpty()) {
                label = null;
                url = null;
            }
        }

        String headline = (icon.isEmpty() ? "" : icon + " ") + (template.boldTitle() ? (slack ? "*" + title + "*" : "**" + title + "**") : title);
        Map<String, Object> attachment = slack ? slackAttachment(headline, title, text, color, footer, label, url)
                : mattermostAttachment(icon, title, template.boldTitle(), text, color, footer, label, url);
        return new RenderedChat(tool, (icon.isEmpty() ? "" : icon + " ") + title, title, text, color, footer, label, url, attachment);
    }

    private static Map<String, Object> slackAttachment(String headline, String title, String text, String color, String footer, String label, String url) {
        List<Object> blocks = new ArrayList<>();
        blocks.add(Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", text.isEmpty() ? headline : headline + "\n" + text)));
        if (url != null) {
            blocks.add(Map.of("type", "actions", "elements", List.of(Map.of("type", "button", "text", Map.of("type", "plain_text", "text", label, "emoji", true),
                    "url", url))));
        }
        if (!footer.isEmpty()) {
            blocks.add(Map.of("type", "context", "elements", List.of(Map.of("type", "mrkdwn", "text", footer))));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("color", color);
        out.put("fallback", title);
        out.put("blocks", blocks);
        return out;
    }

    private static Map<String, Object> mattermostAttachment(String icon, String title, boolean bold, String text, String color, String footer, String label, String url) {
        String head = (icon.isEmpty() ? "" : icon + " ") + title;
        StringBuilder body = new StringBuilder();
        if (!bold) {
            body.append(head);
        }
        if (!text.isEmpty()) {
            if (!body.isEmpty()) {
                body.append('\n');
            }
            body.append(text);
        }
        if (url != null) {
            if (!body.isEmpty()) {
                body.append("\n\n");
            }
            body.append('[').append(ChannelMessageFormatter.neutralizeValue(false, label, 40)).append("](").append(ChannelMessageFormatter.linkValue(false, url)).append(')');
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fallback", title);
        out.put("color", color);
        if (bold) {
            out.put("title", head);
        }
        out.put("text", body.toString());
        if (!footer.isEmpty()) {
            out.put("footer", footer);
        }
        return out;
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
