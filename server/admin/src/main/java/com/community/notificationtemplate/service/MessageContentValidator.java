package com.community.notificationtemplate.service;

import com.community.notificationtemplate.catalog.MailTemplateAudience;
import com.community.notificationtemplate.catalog.MessageAudience;
import com.community.notificationtemplate.catalog.MessageLocales;
import com.community.notificationtemplate.catalog.MessageTargetCatalog;
import com.community.notificationtemplate.catalog.MessageVariable;
import com.community.notificationtemplate.engine.TemplateText;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 문구 · 템플릿 내용 검사 — admin-web {@code UtilMailTemplate.validateMessage|validateTemplate} · {@code UtilNotificationMessage.validateChat|validateInApp} ·
 * {@code UtilChatTemplate.validate} 와 <b>같은 규칙</b>(화면 검사는 미리 알려 주는 용도이고 판정은 여기다). 길이 상한은 계약 「미확정」 3 의 가정 값이다.
 * 위반은 칸 경로 → 문구({@link MessageRef})로 모은다(칸마다 첫 위반만).
 */
@Component
public class MessageContentValidator {

    public static final int NAME_MAX = 50;
    public static final int MAIL_SUBJECT_MAX = 200;
    public static final int MAIL_BODY_MAX = 20000;
    public static final int BUTTON_LABEL_MAX = 40;
    public static final int IN_APP_TITLE_MAX = 100;
    public static final int IN_APP_BODY_MAX = 300;
    public static final int CHAT_TITLE_MAX = 200;
    public static final int CHAT_BODY_MAX = 1500;
    public static final int FOOTER_MAX = 500;
    public static final int MAIL_FRAME_MAX = 20000;
    public static final int CHAT_FRAME_MAX = 500;
    public static final int OPEN_LABEL_MAX = 20;

    private static final String BUTTON_TOKEN = "button";
    private static final Pattern BUTTON_BLOCK = Pattern.compile(
            "<p data-mail-button(?:=\"[^\"]*\")?>\\s*<a href=\"\\{\\{\\s*([A-Za-z][A-Za-z0-9_]*)\\s*\\}\\}\"[^>]*>([\\s\\S]*?)</a>\\s*</p>");
    private static final Pattern BUTTON_TOKEN_RE = Pattern.compile("\\{\\{\\s*button\\s*\\}\\}");

    private final Messages messages;

    public MessageContentValidator(Messages messages) {
        this.messages = messages;
    }

    /** 이름 — 필수 · 50자 */
    public MessageRef nameProblem(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty()) {
            return new MessageRef("notificationTemplate.problem.name.required");
        }
        return name.length() > NAME_MAX ? tooLong(NAME_MAX) : null;
    }

    /**
     * 이메일 문구 — 이름 · 제목(필수 · 한 줄 · 200자 · 모르는 변수) · 본문(정화된 HTML — 버튼 하나까지(링크 변수만) · 20000자 · 모르는 변수 · 보안 메일 필수 변수).
     * 버튼 문제도 {@code body} 칸이다.
     */
    public void mail(Map<String, MessageRef> out, String name, String subject, String bodyHtml, MessageAudience audience, boolean security,
                     List<MessageVariable> variables) {
        put(out, "name", nameProblem(name));
        Set<String> known = names(variables);
        String s = subject == null ? "" : subject;
        List<String> subjectUnknown = unknown(TemplateText.variables(s), known);
        if (s.isBlank()) {
            out.putIfAbsent("subject", new MessageRef("notificationTemplate.problem.subject.required"));
        } else if (s.contains("\n") || s.contains("\r")) {
            out.putIfAbsent("subject", new MessageRef("notificationTemplate.problem.multiLine"));
        } else if (s.length() > MAIL_SUBJECT_MAX) {
            out.putIfAbsent("subject", tooLong(MAIL_SUBJECT_MAX));
        } else if (!subjectUnknown.isEmpty()) {
            out.putIfAbsent("subject", unknownProblem(subjectUnknown, MailTemplateAudience.of(audience)));
        }

        String body = bodyHtml == null ? "" : bodyHtml;
        Matcher blocks = BUTTON_BLOCK.matcher(body);
        int blockCount = 0;
        String buttonLink = null;
        String buttonLabel = null;
        while (blocks.find()) {
            blockCount++;
            if (buttonLink == null) {
                buttonLink = blocks.group(1);
                buttonLabel = TemplateText.plainText(blocks.group(2));
            }
        }
        int tokens = count(BUTTON_TOKEN_RE, body);
        String withoutButtons = BUTTON_BLOCK.matcher(body).replaceAll("");
        List<String> bodyVars = TemplateText.variables(withoutButtons).stream().filter(v -> !BUTTON_TOKEN.equals(v)).toList();
        List<String> bodyUnknown = unknown(bodyVars, known);
        String plain = TemplateText.plainText(body);

        MessageRef bodyProblem = null;
        if (blockCount + tokens > 1) {
            bodyProblem = new MessageRef("notificationTemplate.problem.buttonMany");
        } else if (blockCount == 1) {
            String link = buttonLink;
            boolean linkVariable = variables.stream().anyMatch(v -> v.name().equals(link) && v.link());
            if (buttonLabel == null || buttonLabel.isBlank()) {
                bodyProblem = new MessageRef("notificationTemplate.problem.buttonLabel");
            } else if (buttonLabel.strip().length() > BUTTON_LABEL_MAX) {
                bodyProblem = tooLong(BUTTON_LABEL_MAX);
            } else if (!linkVariable) {
                bodyProblem = new MessageRef("notificationTemplate.problem.buttonLink");
            }
        } else if (tokens == 1) {
            bodyProblem = new MessageRef("notificationTemplate.problem.buttonLink");
        }
        if (bodyProblem == null) {
            if (plain.isBlank() && blockCount == 0) {
                bodyProblem = new MessageRef("notificationTemplate.problem.body.required");
            } else if (body.length() > MAIL_BODY_MAX) {
                bodyProblem = tooLong(MAIL_BODY_MAX);
            } else if (!bodyUnknown.isEmpty()) {
                bodyProblem = unknownProblem(bodyUnknown, MailTemplateAudience.of(audience));
            } else if (security) {
                Set<String> used = new LinkedHashSet<>(TemplateText.variables(s));
                used.addAll(bodyVars);
                if (buttonLink != null) {
                    used.add(buttonLink);
                }
                List<String> missing = variables.stream().filter(MessageVariable::required).map(MessageVariable::name).filter(n -> !used.contains(n)).toList();
                if (!missing.isEmpty()) {
                    bodyProblem = new MessageRef("notificationTemplate.problem.missingRequired", tokens(missing));
                }
            }
        }
        put(out, "body", bodyProblem);
    }

    /** 앱 알림(제목 100 · 본문 300) · Slack·Mattermost(제목 200 · 본문 1500) — 이름 · 제목(필수 · 한 줄) · 본문(비어도 됨) · 모르는 변수 */
    public void titled(Map<String, MessageRef> out, String name, String subject, String body, MessageAudience audience, List<MessageVariable> variables, int titleMax,
                       int bodyMax) {
        put(out, "name", nameProblem(name));
        Set<String> known = names(variables);
        String s = subject == null ? "" : subject;
        if (s.contains("\n") || s.contains("\r")) {
            put(out, "subject", new MessageRef("notificationTemplate.problem.multiLine"));
        } else {
            put(out, "subject", text(s, titleMax, known, audience, true));
        }
        put(out, "body", text(body == null ? "" : body, bodyMax, known, audience, false));
    }

    /** 메일 템플릿 틀 — {@code {{content}}} 정확히 한 번 · 20000자 · 쓰는 대상의 공통 변수만 */
    public MessageRef mailFrame(String frame, MailTemplateAudience audience) {
        String f = frame == null ? "" : frame;
        int count = TemplateText.contentCount(f);
        if (count == 0) {
            return new MessageRef("notificationTemplate.problem.contentMissing");
        }
        if (count > 1) {
            return new MessageRef("notificationTemplate.problem.contentMany");
        }
        if (f.length() > MAIL_FRAME_MAX) {
            return tooLong(MAIL_FRAME_MAX);
        }
        List<String> unknown = unknown(TemplateText.variables(f).stream().filter(v -> !MessageVariable.CONTENT.equals(v)).toList(), commonNames(audience));
        return unknown.isEmpty() ? null : unknownProblem(unknown, audience);
    }

    /** 메일 템플릿 꼬리말 — 평문 · 500자 · 쓰는 대상의 공통 변수만({@code {{content}}} 도 안 된다). 비어도 된다 */
    public MessageRef mailFooter(String footer, MailTemplateAudience audience) {
        String f = footer == null ? "" : footer;
        if (f.length() > FOOTER_MAX) {
            return tooLong(FOOTER_MAX);
        }
        List<String> unknown = unknown(TemplateText.variables(f), commonNames(audience));
        return unknown.isEmpty() ? null : unknownProblem(unknown, audience);
    }

    /** Slack·Mattermost 템플릿 틀 — {@code {{content}}} 정확히 한 번 · 500자 · {@code content} + 운영자 공통 변수만 */
    public MessageRef chatFrame(String frame) {
        String f = frame == null ? "" : frame;
        int count = TemplateText.contentCount(f);
        if (count == 0) {
            return new MessageRef("notificationTemplate.problem.contentMissing");
        }
        if (count > 1) {
            return new MessageRef("notificationTemplate.problem.contentMany");
        }
        if (f.length() > CHAT_FRAME_MAX) {
            return tooLong(CHAT_FRAME_MAX);
        }
        List<String> unknown = unknown(TemplateText.variables(f).stream().filter(v -> !MessageVariable.CONTENT.equals(v)).toList(),
                commonNames(MailTemplateAudience.OPERATOR));
        return unknown.isEmpty() ? null : unknownProblem(unknown, MailTemplateAudience.OPERATOR);
    }

    /** 「열기」 버튼 글자 — 필수 · 20자 */
    public MessageRef openLabel(String label) {
        String l = label == null ? "" : label.strip();
        if (l.isEmpty()) {
            return new MessageRef("notificationTemplate.problem.buttonLabel");
        }
        return l.length() > OPEN_LABEL_MAX ? tooLong(OPEN_LABEL_MAX) : null;
    }

    public static boolean validLocale(String locale) {
        return MessageLocales.valid(locale);
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private MessageRef text(String text, int max, Set<String> known, MessageAudience audience, boolean required) {
        if (required && text.isBlank()) {
            return new MessageRef("notificationTemplate.problem.subject.required");
        }
        if (text.length() > max) {
            return tooLong(max);
        }
        List<String> unknown = unknown(TemplateText.variables(text), known);
        return unknown.isEmpty() ? null : unknownProblem(unknown, MailTemplateAudience.of(audience));
    }

    /** 모르는 변수의 문제 — 다른 받는 사람의 공통 변수(운영자 쪽의 {@code siteUrl} …)가 섞였으면 그것을 따로 알린다 */
    MessageRef unknownProblem(List<String> unknown, MailTemplateAudience audience) {
        for (MessageAudience owner : MessageAudience.values()) {
            if (audience.single().filter(owner::equals).isPresent()) {
                continue;
            }
            String only = MessageTargetCatalog.audienceOnlyVariable(owner);
            List<String> names = unknown.stream().filter(only::equals).toList();
            if (!names.isEmpty()) {
                return new MessageRef("notificationTemplate.problem.audienceVariable", tokens(names),
                        messages.get(new MessageRef("notificationTemplate.audience." + owner.name())));
            }
        }
        return new MessageRef("notificationTemplate.problem.unknownVariable", tokens(unknown));
    }

    private static Set<String> commonNames(MailTemplateAudience audience) {
        return names(MessageTargetCatalog.commonVariables(audience));
    }

    private static Set<String> names(Collection<MessageVariable> variables) {
        return variables.stream().map(MessageVariable::name).collect(Collectors.toSet());
    }

    private static List<String> unknown(List<String> used, Set<String> known) {
        List<String> out = new ArrayList<>();
        for (String name : used) {
            if (!known.contains(name)) {
                out.add(name);
            }
        }
        return out;
    }

    private static String tokens(List<String> names) {
        return names.stream().map(n -> "{{" + n + "}}").collect(Collectors.joining(", "));
    }

    private static int count(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    static MessageRef tooLong(int max) {
        return new MessageRef("notificationTemplate.problem.tooLong", String.valueOf(max));
    }

    private static void put(Map<String, MessageRef> out, String field, MessageRef problem) {
        if (problem != null) {
            out.putIfAbsent(field, problem);
        }
    }
}
