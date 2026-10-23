package com.community.notificationtemplate.engine;

import com.community.notificationtemplate.builtin.BuiltinMessages;
import com.community.notificationtemplate.builtin.BuiltinTemplates;
import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageChannelDef;
import com.community.notificationtemplate.catalog.MessageLocales;
import com.community.notificationtemplate.catalog.MessageSeverity;
import com.community.notificationtemplate.catalog.MessageTarget;
import com.community.notificationtemplate.catalog.MessageTargetCatalog;
import com.community.notificationtemplate.catalog.MessageTargetType;
import com.community.notificationtemplate.catalog.MessageVariable;
import com.community.notificationtemplate.engine.MessageTemplateStore.Selection;
import com.community.notificationtemplate.engine.MessageTemplateStore.Snapshot;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * <b>알림 문구 렌더링 엔진</b> — 발송 쪽(메일 · 관리자 알림 · 회원 알림 · Slack/Mattermost 게시)이 부르는 공개 서비스. 대상 · 채널 · 받는 사람 언어 · 변수 값 · 조건 값을 받아
 * 문구를 고르고 변수를 채우고 템플릿을 씌운 결과를 돌려준다. 관리 API 의 미리보기·시험도 같은 그리기 코드({@link #previewEmail} · {@link #previewChat})를 쓴다.
 *
 * <h3>문구 고르기</h3>
 * <ol>
 *   <li>대상 · 채널의 특수 상황 규칙을 위에서부터 — 요청의 조건 값에 그 규칙의 「조건 이름 = 값」이 있으면 그 문구(언어와 상관없이).</li>
 *   <li>없으면 받는 사람 언어의 「쓸 문구」(언어가 ko·en 밖이거나 없으면 사이트 표시 기본 언어, 그것도 없으면 ko).</li>
 *   <li>고른 문구가 없어졌으면(동시에 지워짐) 그 언어의 기본 문구, 그다음 ko 기본 문구.</li>
 * </ol>
 *
 * <h3>변수</h3>
 * 공통 변수 {@code siteName}·{@code siteUrl}(사이트 공개 주소)·{@code adminUrl}({@code ADMIN_URL})은 엔진이 채운다. 이메일·Slack·Mattermost 의 {@code title}·{@code body} 는
 * 부르는 쪽이 주지 않으면 같은 요청으로 그 대상의 <b>앱 알림 문구를 먼저 그려</b> 채운다(계약 목업 가정 — 이메일만 따로 고치고 싶으면 이벤트 변수를 직접 쓴다).
 * 값은 채널에 맞게 가린다 — 이메일 HTML 이스케이프(링크 변수는 버튼 {@code href} 로만, http·https 만), Slack·Mattermost 멘션·표기 무력화, 앱 알림 평문.
 * 실제 발송에서 값이 없는 변수는 빈 글자로 지운다.
 *
 * <h3>캐시</h3>
 * 사용자 문구·템플릿·선택은 {@link MessageTemplateStore} 의 15초 사본을 읽는다 — 관리 API 가 저장하면 그 서버는 바로 비우고, 다른 서버는 15초 안에 따라온다.
 * 이 서비스는 던지지 않는 것이 아니다: 카탈로그에 없는 대상·채널, 준비 중 채널(카카오톡)은 {@link IllegalArgumentException} — 부르는 쪽의 코드 결함이다.
 * DB 를 읽는 것은 JDBC 사본뿐이라 업무 트랜잭션 안(세이브포인트)에서 불러도 트랜잭션을 깨뜨리지 않는다. 메일·게시 자체는 커밋 뒤 발송기가 한다.
 */
@Service
public class NotificationMessageRenderer {

    private static final DateTimeFormatter OCCURRED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String DEFAULT_SITE_NAME = "ROBOYAM";

    private final MessageTemplateStore store;
    private final BuiltinMessages builtins;
    private final BuiltinTemplates builtinTemplates;
    private final Messages messages;
    private final String adminUrl;

    public NotificationMessageRenderer(MessageTemplateStore store, BuiltinMessages builtins, BuiltinTemplates builtinTemplates, Messages messages,
                                       @Value("${roboyam.admin-url:}") String adminUrl) {
        this.store = store;
        this.builtins = builtins;
        this.builtinTemplates = builtinTemplates;
        this.messages = messages;
        this.adminUrl = MessageTemplateStore.normalizeUrl(adminUrl);
    }

    // ── 발송 쪽 API ─────────────────────────────────────────────────────────

    /** 앱 알림 한 줄(제목·본문 평문) — 채널은 {@link MessageChannel#IN_APP} 이어야 한다 */
    public RenderedInApp renderInApp(MessageRenderRequest request) {
        require(request, MessageChannel.IN_APP);
        Snapshot snapshot = store.current();
        Resolved resolved = resolve(request, snapshot);
        Map<String, String> values = values(request, resolved, snapshot, false);
        String title = TemplateText.oneLine(TemplateText.fill(resolved.message.subject(), values::get, false));
        String body = TemplateText.fill(resolved.message.body(), values::get, false).strip();
        return new RenderedInApp(resolved.message.id(), title, body);
    }

    /** 이메일 한 통(제목 · 문서 HTML) — 채널은 {@link MessageChannel#EMAIL} 이어야 한다. 보내기는 {@code MailDispatcher#sendTagged} */
    public RenderedEmail renderEmail(MessageRenderRequest request) {
        require(request, MessageChannel.EMAIL);
        Snapshot snapshot = store.current();
        Resolved resolved = resolve(request, snapshot);
        Map<String, String> values = values(request, resolved, snapshot, true);
        MailTemplateValues template = mailTemplate(resolved.message.templateId(), snapshot).orElseGet(() ->
                builtinTemplates.mailTemplate(BuiltinTemplates.mailId(resolved.target.audience()), MessageLocales.locale(resolved.locale)).orElseThrow());
        return email(resolved.message.id(), resolved.locale, resolved.message.subject(), resolved.message.body(), template, values, false, snapshot.site());
    }

    /** Slack · Mattermost 메시지 카드 한 장 — 채널은 {@link MessageChannel#SLACK} · {@link MessageChannel#MATTERMOST}. 올리기는 {@code NotificationChannelPoster#post(channel, RenderedChat)} */
    public RenderedChat renderChat(MessageRenderRequest request) {
        if (request.channel() == null || !request.channel().chat()) {
            throw new IllegalArgumentException("Slack · Mattermost 채널이 아니다: " + request.channel());
        }
        Snapshot snapshot = store.current();
        Resolved resolved = resolve(request, snapshot);
        Map<String, String> values = values(request, resolved, snapshot, true);
        ChatTemplateValues template = chatTemplate(resolved.message.templateId(), request.channel(), snapshot)
                .orElseGet(() -> builtinTemplates.chat(request.channel(), MessageLocales.locale(resolved.locale)));
        MessageSeverity severity = request.severity() != null ? request.severity() : resolved.target.severity();
        return ChatRenderer.render(request.channel(), template, resolved.message.subject(), resolved.message.body(), values::get, linkNames(resolved.def), false,
                resolved.locale, severity, siteName(snapshot.site()), values.get(MessageVariable.LINK), Instant.now(), snapshot.site().zone());
    }

    /** 이 요청이 쓸 문구(고르기만 — 로그·검사용) */
    public MessageValues pick(MessageRenderRequest request) {
        return resolve(request, store.current()).message;
    }

    // ── 미리보기·시험(관리 API) ──────────────────────────────────────────────

    /**
     * 저장 전 값으로 이메일을 그린다 — 미리보기·시험 발송. 본문은 부르는 쪽이 이미 정화했어야 한다({@link MailHtml}).
     *
     * @param values      변수 이름 → 가리기 전 값(견본 값)
     * @param keepUnknown 모르는 변수를 그대로 보일까(미리보기 true)
     */
    public RenderedEmail previewEmail(String locale, String subject, String bodyHtml, MailTemplateValues template, Map<String, String> values, boolean keepUnknown) {
        Snapshot snapshot = store.current();
        Map<String, String> filled = new HashMap<>(values);
        filled.putIfAbsent(MessageVariable.SITE_NAME, siteName(snapshot.site()));
        return email(null, MessageLocales.valid(locale) ? locale : MessageLocales.KO, subject, bodyHtml, template, filled, keepUnknown, snapshot.site());
    }

    /** 저장 전 값으로 Slack · Mattermost 카드를 그린다 — 시험 게시 */
    public RenderedChat previewChat(MessageChannel tool, ChatTemplateValues template, String subject, String body, Map<String, String> values, Set<String> linkNames,
                                    String locale, MessageSeverity severity, boolean keepUnknown) {
        Snapshot snapshot = store.current();
        Map<String, String> filled = new HashMap<>(values);
        filled.putIfAbsent(MessageVariable.SITE_NAME, siteName(snapshot.site()));
        return ChatRenderer.render(tool, template, subject, body, filled::get, linkNames, keepUnknown, MessageLocales.valid(locale) ? locale : MessageLocales.KO, severity,
                siteName(snapshot.site()), filled.get(MessageVariable.LINK), Instant.now(), snapshot.site().zone());
    }

    /** 사이트 정보(사이트 이름 · 공개 주소 · 로고 · 시간대) — 관리 API 의 견본 값 */
    public MessageTemplateStore.Site site() {
        return store.current().site();
    }

    /** 관리자 앱 주소({@code ADMIN_URL} — 없으면 null) */
    public String adminUrl() {
        return adminUrl;
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private record Resolved(MessageTarget target, MessageChannelDef def, String locale, MessageValues message) {
    }

    private static void require(MessageRenderRequest request, MessageChannel channel) {
        if (request.channel() != channel) {
            throw new IllegalArgumentException(channel + " 채널 요청이 아니다: " + request.channel());
        }
    }

    private Resolved resolve(MessageRenderRequest request, Snapshot snapshot) {
        MessageTarget target = MessageTargetCatalog.find(request.targetType(), request.targetCode())
                .orElseThrow(() -> new IllegalArgumentException("문구 대상이 카탈로그에 없다: " + request.targetType() + ":" + request.targetCode()));
        MessageChannelDef def = target.channel(request.channel())
                .orElseThrow(() -> new IllegalArgumentException("대상이 이 채널로 나가지 않는다: " + target.code() + " " + request.channel()));
        if (request.channel().preparing()) {
            throw new IllegalArgumentException("준비 중인 채널이다: " + request.channel());
        }
        String locale = locale(request.locale(), snapshot.site());
        Selection selection = snapshot.selection(target.type(), target.code(), request.channel());

        for (MessageRule rule : selection.rules()) {
            Set<String> values = request.conditions().get(rule.key());
            if (values != null && values.contains(rule.value())) {
                Optional<MessageValues> found = own(rule.messageId(), target, request.channel(), snapshot);
                if (found.isPresent()) {
                    return new Resolved(target, def, locale, found.get());
                }
            }
        }
        String selected = selection.selected(locale);
        Optional<MessageValues> chosen = selected.isEmpty() ? Optional.empty() : own(selected, target, request.channel(), snapshot);
        MessageValues message = chosen
                .or(() -> builtins.of(target, request.channel(), locale))
                .or(() -> builtins.of(target, request.channel(), MessageLocales.KO))
                .orElseThrow(() -> new IllegalStateException("쓸 문구가 없다: " + target.code() + " " + request.channel()));
        return new Resolved(target, def, locale, message);
    }

    /** 이 대상 · 채널의 문구(기본 것 또는 사용자 것) */
    private Optional<MessageValues> own(String id, MessageTarget target, MessageChannel channel, Snapshot snapshot) {
        MessageValues found = BuiltinMessages.isBuiltinId(id) ? builtins.find(id).orElse(null) : snapshot.messages().get(id);
        if (found == null || found.channel() != channel || found.targetType() != target.type() || !found.targetCode().equals(target.code())) {
            return Optional.empty();
        }
        return Optional.of(found);
    }

    /** 받는 사람 언어 → ko·en 밖이거나 없으면 사이트 표시 기본 언어 → ko */
    private static String locale(String requested, MessageTemplateStore.Site site) {
        if (MessageLocales.valid(requested)) {
            return requested;
        }
        String siteLanguage = MessageLocales.of(site.language());
        return siteLanguage != null ? siteLanguage : MessageLocales.KO;
    }

    /** 이 요청의 변수 값(가리기 전) — 부르는 쪽 값 + 공통 변수 + 시각 + 앱 알림 제목·본문 */
    private Map<String, String> values(MessageRenderRequest request, Resolved resolved, Snapshot snapshot, boolean withInApp) {
        Map<String, String> values = new HashMap<>(request.variables());
        MessageTemplateStore.Site site = snapshot.site();
        values.putIfAbsent(MessageVariable.SITE_NAME, siteName(site));
        if (site.publicUrl() != null) {
            values.putIfAbsent(MessageVariable.SITE_URL, site.publicUrl());
        }
        if (adminUrl != null) {
            values.putIfAbsent(MessageVariable.ADMIN_URL, adminUrl);
        }
        if (!values.containsKey(MessageVariable.OCCURRED_AT) && request.occurredAt() != null) {
            ZoneId zone = request.zone() != null ? request.zone() : site.zone();
            values.put(MessageVariable.OCCURRED_AT, OCCURRED.format(request.occurredAt().atZone(zone)));
        }
        if (withInApp && resolved.target.type() == MessageTargetType.EVENT && resolved.target.has(MessageChannel.IN_APP)
                && (!values.containsKey(MessageVariable.TITLE) || !values.containsKey(MessageVariable.BODY))) {
            RenderedInApp inApp = renderInApp(request.withChannel(MessageChannel.IN_APP));
            values.putIfAbsent(MessageVariable.TITLE, inApp.title());
            values.putIfAbsent(MessageVariable.BODY, inApp.body());
        }
        return values;
    }

    private RenderedEmail email(String messageId, String locale, String subject, String bodyHtml, MailTemplateValues template, Map<String, String> values,
                                boolean keepUnknown, MessageTemplateStore.Site site) {
        String name = siteName(site);
        MailRenderer.Result result = MailRenderer.render(locale, subject, bodyHtml, template, values::get, keepUnknown, name, site.logoUrl());
        String tagged = messages.get(new MessageRef("mail.common.siteTaggedSubject", name, result.subject()), MessageLocales.locale(locale));
        return new RenderedEmail(messageId, tagged, result.subject(), result.html());
    }

    private Optional<MailTemplateValues> mailTemplate(String id, Snapshot snapshot) {
        if (id == null) {
            return Optional.empty();
        }
        if (BuiltinTemplates.isMailBuiltin(id)) {
            return builtinTemplates.mailTemplate(id, MessageLocales.locale(MessageLocales.KO));
        }
        return Optional.ofNullable(snapshot.mailTemplates().get(id));
    }

    private Optional<ChatTemplateValues> chatTemplate(String id, MessageChannel tool, Snapshot snapshot) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<ChatTemplateValues> found = BuiltinTemplates.isChatBuiltin(id) ? builtinTemplates.chatTemplate(id, MessageLocales.locale(MessageLocales.KO))
                : Optional.ofNullable(snapshot.chatTemplates().get(id));
        return found.filter(template -> template.tool() == tool);
    }

    private static Set<String> linkNames(MessageChannelDef def) {
        return def.variables().stream().filter(MessageVariable::link).map(MessageVariable::name).collect(Collectors.toUnmodifiableSet());
    }

    private static String siteName(MessageTemplateStore.Site site) {
        return site.siteName() == null || site.siteName().isBlank() ? DEFAULT_SITE_NAME : site.siteName();
    }
}
