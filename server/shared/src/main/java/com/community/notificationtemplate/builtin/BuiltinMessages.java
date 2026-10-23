package com.community.notificationtemplate.builtin;

import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageChannelDef;
import com.community.notificationtemplate.catalog.MessageLocales;
import com.community.notificationtemplate.catalog.MessageTarget;
import com.community.notificationtemplate.catalog.MessageTargetCatalog;
import com.community.notificationtemplate.catalog.MessageTargetType;
import com.community.notificationtemplate.catalog.RequiredMail;
import com.community.notificationtemplate.engine.MessageValues;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * 기본(builtin) 문구 — <b>코드가 원천</b>이고 DB 에 행이 없다(읽기 전용, 복사해서 만든다). 내용은 <b>지금 메시지 번들 문구 그대로</b>다 — 번들의 {@code {0}} 자리를
 * 이름 붙은 변수({@code {{jobName}}} …)로 바꿔 만든다(목업 {@code notification-template-mock.ts} 의 기본 문구와 같은 글).
 *
 * <ul>
 *   <li>앱 알림: 운영자 이벤트 {@code notification.event.<코드>.title|body}, 회원 이벤트 {@code member.notification.<코드>.title} + 본문 변수 한 줄.</li>
 *   <li>이메일: 운영자 이벤트 = 관리자 알림 메일 한 양식({@code notification.mail.*} — 제목 {@code {{title}}}, 굵은 제목 · 본문 · 「관리자 앱에서 보기」 버튼 · 주소 · 수신 안내),
 *       회원 이벤트 = 같은 꼴의 새 문구(「사이트에서 보기」), 필수 안내 메일 = 지금 계정·보안 메일({@code mail.verify.*} · {@code mail.operator*} · {@code mail.test.*}).</li>
 *   <li>Slack · Mattermost: 제목 {@code {{title}}} + 본문 {@code {{body}}} — 그림·굵게·「열기」·꼬리말은 기본 템플릿이 붙인다.</li>
 *   <li>카카오톡: 기본 문구가 없다(알림톡 템플릿을 카카오에 먼저 등록해야 한다).</li>
 * </ul>
 * 번들은 실행 중에 바뀌지 않으므로 처음 쓸 때 한 번 만들어 둔다.
 */
@Component
public class BuiltinMessages {

    private static final String PREFIX = "builtin-";

    private final Messages messages;
    private volatile Map<String, MessageValues> cache;

    public BuiltinMessages(Messages messages) {
        this.messages = messages;
    }

    /** 기본 문구 id */
    public static String id(MessageTarget target, MessageChannel channel, String locale) {
        return PREFIX + target.code() + "-" + channel.name() + "-" + locale;
    }

    public static boolean isBuiltinId(String id) {
        return id != null && id.startsWith(PREFIX);
    }

    /** 모든 기본 문구(대상 · 채널 · 언어 차례) */
    public List<MessageValues> all() {
        return List.copyOf(map().values());
    }

    public Optional<MessageValues> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(map().get(id));
    }

    /** 그 대상·채널·언어의 기본 문구 — 기본 문구가 없는 채널(카카오톡)은 비어 있다 */
    public Optional<MessageValues> of(MessageTarget target, MessageChannel channel, String locale) {
        return find(id(target, channel, locale));
    }

    private Map<String, MessageValues> map() {
        Map<String, MessageValues> current = cache;
        if (current == null) {
            synchronized (this) {
                current = cache;
                if (current == null) {
                    current = build();
                    cache = current;
                }
            }
        }
        return current;
    }

    private Map<String, MessageValues> build() {
        Map<String, MessageValues> out = new LinkedHashMap<>();
        for (MessageTarget target : MessageTargetCatalog.all()) {
            for (MessageChannelDef def : target.channels()) {
                if (!def.hasBuiltin()) {
                    continue;
                }
                for (String locale : MessageLocales.ALL) {
                    String[] content = content(target, def.channel(), MessageLocales.locale(locale));
                    String id = id(target, def.channel(), locale);
                    out.put(id, new MessageValues(id, target.type(), target.code(), def.channel(), locale, null,
                            BuiltinTemplates.defaultTemplateId(def.channel(), target.audience()), content[0], content[1], true, null, null));
                }
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /** @return [제목, 본문] */
    private String[] content(MessageTarget target, MessageChannel channel, Locale locale) {
        return switch (channel) {
            case IN_APP -> inApp(target, locale);
            case SLACK, MATTERMOST -> new String[] {"{{title}}", "{{body}}"};
            case EMAIL -> target.type() == MessageTargetType.REQUIRED_MAIL ? requiredMail(RequiredMail.valueOf(target.code()), locale) : eventMail(target, locale);
            case KAKAO -> throw new IllegalStateException("카카오톡은 기본 문구가 없다");
        };
    }

    private String[] inApp(MessageTarget target, Locale locale) {
        MessageTarget.InAppText source = target.inApp();
        Object[] args = args(source.argNames());
        String title = messages.get(new MessageRef(source.titleKey(), args), locale);
        String body = source.bodyKey() != null ? messages.get(new MessageRef(source.bodyKey(), args), locale) : source.bodyLiteral();
        return new String[] {title, body == null ? "" : body};
    }

    /** 번들 인자 자리마다 {@code {{이름}}}(쓰지 않는 자리는 빈 글자) */
    private static Object[] args(List<String> names) {
        List<Object> out = new ArrayList<>();
        for (String name : names) {
            out.add(name == null ? "" : "{{" + name + "}}");
        }
        return out.toArray();
    }

    private String[] eventMail(MessageTarget target, Locale locale) {
        boolean member = target.audience() == com.community.notificationtemplate.catalog.MessageAudience.MEMBER;
        String open = member ? text("notificationTemplate.builtin.memberMail.open", locale) : text("notification.mail.open", locale);
        String reason = member ? text(new MessageRef("notificationTemplate.builtin.memberMail.reason", "{{siteName}}"), locale) : text("notification.mail.reason", locale);
        String body = "<p><strong>{{title}}</strong></p><p>{{body}}</p>" + button("link", open) + "<p>{{link}}</p>" + p(reason);
        return new String[] {"{{title}}", body};
    }

    private String[] requiredMail(RequiredMail mail, Locale locale) {
        return switch (mail) {
            case MEMBER_EMAIL_VERIFY -> new String[] {text("mail.verify.subject", locale),
                    strong(text("mail.verify.title", locale))
                            + p(text(new MessageRef("mail.verify.greeting", "{{nickname}}", "{{siteName}}"), locale))
                            + p(text("mail.verify.guide", locale))
                            + button("verifyLink", text("mail.verify.button", locale))
                            + p(text(new MessageRef("mail.verify.validity", "{{validHours}}"), locale))
                            + "<p>{{verifyLink}}</p>"
                            + p(text("mail.verify.ignore", locale))};
            case OPERATOR_INVITE -> new String[] {text("mail.operatorInvite.subject", locale),
                    strong(text("mail.operatorInvite.title", locale))
                            + p(text(new MessageRef("mail.operatorInvite.greeting", "{{siteName}}"), locale))
                            + p(text("mail.operatorInvite.guide", locale))
                            + button("inviteLink", text("mail.operatorInvite.button", locale))
                            + p(text(new MessageRef("mail.operatorInvite.validity", "{{validHours}}"), locale))
                            + "<p>{{inviteLink}}</p>"
                            + p(text("mail.operatorInvite.ignore", locale))};
            case OPERATOR_CREDENTIALS -> new String[] {text("mail.operatorCredentials.subject", locale),
                    strong(text("mail.operatorCredentials.title", locale))
                            + p(text(new MessageRef("mail.operatorCredentials.greeting", "{{operatorName}}", "{{siteName}}"), locale))
                            + p(text("mail.operatorCredentials.guide", locale))
                            + "<p>" + esc(text("mail.operatorCredentials.passwordLabel", locale)) + ": <strong>{{temporaryPassword}}</strong></p>"
                            + p(text(new MessageRef("mail.operatorCredentials.login", "{{adminUrl}}"), locale))
                            + p(text("mail.operatorCredentials.keep", locale))};
            case OPERATOR_RESET_CODE -> new String[] {text("mail.operatorReset.subject", locale),
                    strong(text("mail.operatorReset.title", locale))
                            + p(text(new MessageRef("mail.operatorReset.greeting", "{{siteName}}"), locale))
                            + p(text("mail.operatorReset.guide", locale))
                            + "<p>" + esc(text("mail.operatorReset.codeLabel", locale)) + ": <strong>{{code}}</strong></p>"
                            + p(text(new MessageRef("mail.operatorReset.validity", "{{validMinutes}}"), locale))
                            + p(text("mail.operatorReset.keep", locale))
                            + p(text("mail.operatorReset.ignore", locale))};
            case MAIL_TEST -> new String[] {text("mail.test.subject", locale), p(text("mail.test.body", locale))};
        };
    }

    private String text(String key, Locale locale) {
        return messages.get(new MessageRef(key), locale);
    }

    private String text(MessageRef ref, Locale locale) {
        return messages.get(ref, locale);
    }

    private static String esc(String text) {
        return HtmlUtils.htmlEscape(text);
    }

    private static String p(String text) {
        return "<p>" + esc(text) + "</p>";
    }

    private static String strong(String text) {
        return "<p><strong>" + esc(text) + "</strong></p>";
    }

    /** 버튼 블록 — 저장 모양 {@code <p data-mail-button=""><a href="{{링크 변수}}">글자</a></p>} */
    static String button(String linkVariable, String label) {
        return "<p data-mail-button=\"\"><a href=\"{{" + linkVariable + "}}\">" + esc(label) + "</a></p>";
    }
}
