package com.community.notificationtemplate.builtin;

import com.community.notificationtemplate.catalog.MailTemplateAudience;
import com.community.notificationtemplate.catalog.MessageAudience;
import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageLocales;
import com.community.notificationtemplate.catalog.MessageSeverity;
import com.community.notificationtemplate.engine.ChatColor;
import com.community.notificationtemplate.engine.ChatTemplateValues;
import com.community.notificationtemplate.engine.MailTemplateValues;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 기본(builtin) 템플릿 — <b>코드가 원천</b>이고 DB 에 행이 없다(읽기 전용, 복사해서 만든다). 꼬리말·버튼 글자는 메시지 번들 문구다.
 * <ul>
 *   <li>메일: 받는 사람별 둘 — 「회원 기본」({@value #MAIL_MEMBER}) · 「운영자 기본」({@value #MAIL_OPERATOR}). 로고·사이트 이름 보이기, 띠 없음, 틀 {@code <p>{{content}}</p>}.</li>
 *   <li>Slack · Mattermost: 종류마다 하나({@value #SLACK_DEFAULT} · {@value #MATTERMOST_DEFAULT}) — 지금 {@code ChannelMessageFormatter} 의 모양(심각도 그림 · 굵은 제목 ·
 *       안내 파랑/주의 호박/긴급 빨강 · 사이트 이름 · 보낸 시각 · 「열기」 버튼 · 틀 {@code {{content}}}).</li>
 * </ul>
 */
@Component
public class BuiltinTemplates {

    public static final String MAIL_MEMBER = "mail-member";
    public static final String MAIL_OPERATOR = "mail-operator";
    public static final String SLACK_DEFAULT = "slack-default";
    public static final String MATTERMOST_DEFAULT = "mattermost-default";

    private static final String PLAIN_FRAME = "<p>{{content}}</p>";

    private final Messages messages;

    public BuiltinTemplates(Messages messages) {
        this.messages = messages;
    }

    /** 받는 사람의 기본 메일 템플릿 id */
    public static String mailId(MessageAudience audience) {
        return audience == MessageAudience.MEMBER ? MAIL_MEMBER : MAIL_OPERATOR;
    }

    /** 종류의 기본 Slack · Mattermost 템플릿 id */
    public static String chatId(MessageChannel tool) {
        return tool == MessageChannel.SLACK ? SLACK_DEFAULT : MATTERMOST_DEFAULT;
    }

    /** 채널의 기본 문구가 쓰는 템플릿 — 이메일은 받는 사람의 기본 메일 템플릿, Slack·Mattermost 는 그 종류의 기본 템플릿, 앱 알림·카카오톡은 null */
    public static String defaultTemplateId(MessageChannel channel, MessageAudience audience) {
        return switch (channel) {
            case EMAIL -> mailId(audience);
            case SLACK, MATTERMOST -> chatId(channel);
            case IN_APP, KAKAO -> null;
        };
    }

    public static boolean isMailBuiltin(String id) {
        return MAIL_MEMBER.equals(id) || MAIL_OPERATOR.equals(id);
    }

    public static boolean isChatBuiltin(String id) {
        return SLACK_DEFAULT.equals(id) || MATTERMOST_DEFAULT.equals(id);
    }

    /** 기본 메일 템플릿 둘(회원 먼저) — 이름은 {@code nameLocale} 로 푼다 */
    public List<MailTemplateValues> mailTemplates(Locale nameLocale) {
        return List.of(mail(MessageAudience.MEMBER, nameLocale), mail(MessageAudience.OPERATOR, nameLocale));
    }

    public Optional<MailTemplateValues> mailTemplate(String id, Locale nameLocale) {
        if (MAIL_MEMBER.equals(id)) {
            return Optional.of(mail(MessageAudience.MEMBER, nameLocale));
        }
        if (MAIL_OPERATOR.equals(id)) {
            return Optional.of(mail(MessageAudience.OPERATOR, nameLocale));
        }
        return Optional.empty();
    }

    public Optional<ChatTemplateValues> chatTemplate(String id, Locale nameLocale) {
        if (SLACK_DEFAULT.equals(id)) {
            return Optional.of(chat(MessageChannel.SLACK, nameLocale));
        }
        if (MATTERMOST_DEFAULT.equals(id)) {
            return Optional.of(chat(MessageChannel.MATTERMOST, nameLocale));
        }
        return Optional.empty();
    }

    public ChatTemplateValues chat(MessageChannel tool, Locale nameLocale) {
        String id = chatId(tool);
        Map<String, String> open = Map.of(MessageLocales.KO, text("notification.channel.post.open", MessageLocales.KO),
                MessageLocales.EN, text("notification.channel.post.open", MessageLocales.EN));
        Map<MessageSeverity, ChatColor> colors = Map.of(MessageSeverity.INFO, ChatColor.BLUE, MessageSeverity.WARNING, ChatColor.AMBER,
                MessageSeverity.CRITICAL, ChatColor.RED);
        return new ChatTemplateValues(id, tool, messages.get(new MessageRef("notificationTemplate.builtin.chat.name"), nameLocale), true, true, true, colors, true,
                true, true, open, Map.of(MessageLocales.KO, "{{content}}", MessageLocales.EN, "{{content}}"), null, null);
    }

    private MailTemplateValues mail(MessageAudience audience, Locale nameLocale) {
        String key = audience == MessageAudience.MEMBER ? "mailMember" : "mailOperator";
        Map<String, String> footer = Map.of(MessageLocales.KO, text("notificationTemplate.builtin." + key + ".footer", MessageLocales.KO),
                MessageLocales.EN, text("notificationTemplate.builtin." + key + ".footer", MessageLocales.EN));
        return new MailTemplateValues(mailId(audience), messages.get(new MessageRef("notificationTemplate.builtin." + key + ".name"), nameLocale), true,
                MailTemplateAudience.of(audience), true, true, null, footer, Map.of(MessageLocales.KO, PLAIN_FRAME, MessageLocales.EN, PLAIN_FRAME), null, null);
    }

    private String text(String key, String locale) {
        return messages.get(new MessageRef(key), MessageLocales.locale(locale));
    }
}
