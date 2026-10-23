package com.community.notificationtemplate.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.notificationtemplate.builtin.BuiltinMessages;
import com.community.notificationtemplate.builtin.BuiltinTemplates;
import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageChannelDef;
import com.community.notificationtemplate.catalog.MessageCondition;
import com.community.notificationtemplate.catalog.MessageLocales;
import com.community.notificationtemplate.catalog.MessageTarget;
import com.community.notificationtemplate.catalog.MessageTargetCatalog;
import com.community.notificationtemplate.catalog.MessageTargetType;
import com.community.notificationtemplate.dto.TemplateChange;
import com.community.notificationtemplate.dto.request.MessageChannelSaveRequest;
import com.community.notificationtemplate.dto.request.MessageRuleInput;
import com.community.notificationtemplate.dto.request.MessageSaveRequest;
import com.community.notificationtemplate.dto.request.MessageTargetSaveRequest;
import com.community.notificationtemplate.dto.response.MessageChannelDetailView;
import com.community.notificationtemplate.dto.response.MessageChannelSummaryView;
import com.community.notificationtemplate.dto.response.MessageListItemView;
import com.community.notificationtemplate.dto.response.MessageRuleConditionView;
import com.community.notificationtemplate.dto.response.MessageRuleView;
import com.community.notificationtemplate.dto.response.MessageSelectedView;
import com.community.notificationtemplate.dto.response.MessageTargetDetailView;
import com.community.notificationtemplate.dto.response.MessageTargetView;
import com.community.notificationtemplate.dto.response.MessageView;
import com.community.notificationtemplate.dto.response.TemplateHistoryPage;
import com.community.notificationtemplate.engine.MailHtml;
import com.community.notificationtemplate.engine.MessageRule;
import com.community.notificationtemplate.engine.MessageValues;
import com.community.notificationtemplate.engine.TemplateText;
import com.community.notificationtemplate.entity.NotificationMessageEntity;
import com.community.notificationtemplate.entity.NotificationMessageSelectionEntity;
import com.community.notificationtemplate.entity.NotificationMessageSelectionId;
import com.community.notificationtemplate.entity.TemplateHistoryBase;
import com.community.notificationtemplate.exception.ChannelPreparingException;
import com.community.notificationtemplate.exception.MessageBuiltinException;
import com.community.notificationtemplate.exception.MessageInUseException;
import com.community.notificationtemplate.exception.MessageLimitException;
import com.community.notificationtemplate.exception.MessageNotFoundException;
import com.community.notificationtemplate.repository.master.NotificationMessageRepository;
import com.community.notificationtemplate.repository.master.NotificationMessageSelectionRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 문구 관리(docs/features/message-template-contract.md 「개정 4」 · 「서버 구현 결과」) — 대상(이벤트 · 필수 안내 메일) 목록·상세, 대상의 채널별 「쓸 문구」·특수 상황 규칙 저장,
 * 사용자 문구 만들기·고치기·지우기·이력. 입구 권한은 컨트롤러가 선언한다(읽기 {@code template.read}, 쓰기 {@code template.manage}).
 *
 * <ul>
 *   <li>기본 문구는 코드가 원천이라 고치거나 지울 수 없다(409 {@code MESSAGE_BUILTIN}). 대상 · 채널은 만든 뒤 바꾸지 못한다.</li>
 *   <li>준비 중인 채널(카카오톡)은 문구를 만들거나 고치거나 지우지 못하고(409 {@code CHANNEL_PREPARING}), 대상 저장에도 넣을 수 없다(400 {@code fields.channels.KAKAO}).</li>
 *   <li>쓰는 중(언어별 쓸 문구 · 규칙)인 문구는 지우지 못한다(409 {@code MESSAGE_IN_USE}). 대상 · 채널당 사용자 문구 {@value #MESSAGE_LIMIT}개(409 {@code MESSAGE_LIMIT}).</li>
 *   <li>이메일 본문은 저장 전에 정화한다({@link MailHtml} — 간단 서식 + 버튼 블록, 링크는 http·https 또는 변수 하나).</li>
 *   <li>바뀐 칸만 이력({@code notification_message_history}) + 감사. 저장하면 렌더링 엔진 사본을 비운다(커밋 뒤).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class NotificationMessageAdminService {

    private static final Logger log = LoggerFactory.getLogger(NotificationMessageAdminService.class);

    static final int MESSAGE_LIMIT = 20;
    static final int RULE_MAX = 10;
    private static final Pattern RULE_ID = Pattern.compile("[1-9][0-9]{0,18}");

    private final NotificationMessageRepository messageRepo;
    private final NotificationMessageSelectionRepository selectionRepo;
    private final TemplateSupport support;
    private final MessageContentValidator validator;
    private final TemplateHistoryService history;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;

    public NotificationMessageAdminService(NotificationMessageRepository messageRepo, NotificationMessageSelectionRepository selectionRepo, TemplateSupport support,
                                           MessageContentValidator validator, TemplateHistoryService history, AuditLogService audit, SnowflakeIdGenerator ids) {
        this.messageRepo = messageRepo;
        this.selectionRepo = selectionRepo;
        this.support = support;
        this.validator = validator;
        this.history = history;
        this.audit = audit;
        this.ids = ids;
    }

    // ── 대상 ────────────────────────────────────────────────────────────────

    /** 대상 목록 — 종류 · 받는 사람으로 거를 수 있다(선택, 모르는 값이면 거르지 않는다) */
    public List<MessageTargetView> targets(String type, String audience) {
        State state = state();
        List<MessageTargetView> out = new ArrayList<>();
        for (MessageTarget target : MessageTargetCatalog.all()) {
            if (type != null && !type.isBlank() && !target.type().name().equals(type)) {
                continue;
            }
            if (audience != null && !audience.isBlank() && !target.audience().name().equals(audience)) {
                continue;
            }
            out.add(targetView(target, state));
        }
        return out;
    }

    public MessageTargetDetailView target(String type, String code) {
        MessageTarget target = support.target(type, code);
        return detail(target, state());
    }

    /** 채널마다 쓸 문구(언어별)와 규칙(목록 전체, 차례 그대로)을 한 번에 저장한다 — 보낸 채널만 바뀐다 */
    @Transactional
    public MessageTargetDetailView saveTarget(AdminActor actor, String type, String code, MessageTargetSaveRequest body) {
        MessageTarget target = support.target(type, code);
        Map<String, MessageChannelSaveRequest> channels = body == null || body.channels() == null ? Map.of() : body.channels();
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Map<MessageChannel, MessageChannelSaveRequest> accepted = new LinkedHashMap<>();
        for (Map.Entry<String, MessageChannelSaveRequest> entry : channels.entrySet()) {
            String prefix = "channels." + entry.getKey();
            Optional<MessageChannel> channel = MessageChannel.of(entry.getKey());
            if (channel.isEmpty()) {
                fields.put(prefix, new MessageRef("notificationTemplate.problem.channel.unknown"));
                continue;
            }
            Optional<MessageChannelDef> def = target.channel(channel.get());
            if (def.isEmpty()) {
                fields.put(prefix, new MessageRef("notificationTemplate.problem.channel.notInTarget", support.targetName(target), support.channelName(channel.get())));
                continue;
            }
            if (channel.get().preparing()) {
                fields.put(prefix, new MessageRef("notificationTemplate.problem.channel.preparing", support.channelName(channel.get())));
                continue;
            }
            MessageChannelSaveRequest value = entry.getValue() == null ? new MessageChannelSaveRequest(Map.of(), List.of()) : entry.getValue();
            Map<String, String> selected = value.selected() == null ? Map.of() : value.selected();
            for (String locale : MessageLocales.ALL) {
                Optional<MessageValues> message = own(selected.get(locale), target, channel.get());
                if (message.isEmpty()) {
                    fields.put(prefix + ".selected." + locale, new MessageRef("notificationTemplate.problem.selected"));
                } else if (!locale.equals(message.get().locale())) {
                    fields.put(prefix + ".selected." + locale, new MessageRef("notificationTemplate.problem.selectedLocale"));
                }
            }
            List<MessageRuleInput> rules = value.rules() == null ? List.of() : value.rules();
            for (int at = 0; at < rules.size(); at++) {
                MessageRuleInput rule = rules.get(at);
                String key = rule == null || rule.condition() == null ? null : rule.condition().key();
                String conditionValue = rule == null || rule.condition() == null ? null : rule.condition().value();
                Optional<MessageCondition> condition = def.get().condition(key);
                if (condition.isEmpty() || !support.conditionAllows(condition.get(), conditionValue)) {
                    fields.put(prefix + ".rules[" + at + "].condition", new MessageRef("notificationTemplate.problem.rule.condition"));
                }
                if (own(rule == null ? null : rule.messageId(), target, channel.get()).isEmpty()) {
                    fields.put(prefix + ".rules[" + at + "].messageId", new MessageRef("notificationTemplate.problem.rule.message"));
                }
            }
            if (rules.size() > RULE_MAX) {
                fields.put(prefix + ".rules", new MessageRef("notificationTemplate.problem.rules.max", String.valueOf(RULE_MAX)));
            }
            accepted.put(channel.get(), value);
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }

        Instant now = Instant.now();
        List<String> changed = new ArrayList<>();
        for (Map.Entry<MessageChannel, MessageChannelSaveRequest> entry : accepted.entrySet()) {
            MessageChannel channel = entry.getKey();
            MessageChannelSaveRequest value = entry.getValue();
            List<MessageRule> rules = new ArrayList<>();
            for (MessageRuleInput rule : value.rules() == null ? List.<MessageRuleInput>of() : value.rules()) {
                String id = rule.id() != null && RULE_ID.matcher(rule.id().strip()).matches() ? rule.id().strip() : String.valueOf(ids.nextId());
                rules.add(new MessageRule(id, rule.condition().key(), rule.condition().value(), rule.messageId()));
            }
            String ko = value.selected().get(MessageLocales.KO);
            String en = value.selected().get(MessageLocales.EN);
            String rulesJson = MessageRule.write(rules);
            NotificationMessageSelectionId key = new NotificationMessageSelectionId(target.type().name(), target.code(), channel.name());
            Optional<NotificationMessageSelectionEntity> existing = selectionRepo.findById(key);
            if (existing.isPresent()) {
                NotificationMessageSelectionEntity row = existing.get();
                boolean same = Objects.equals(effective(row.getSelectedKo(), target, channel, MessageLocales.KO), ko)
                        && Objects.equals(effective(row.getSelectedEn(), target, channel, MessageLocales.EN), en)
                        && MessageRule.parse(row.getRules()).equals(rules);
                if (!same) {
                    row.change(ko, en, rulesJson, actor.operatorId(), now);
                    changed.add(channel.name());
                }
            } else {
                boolean defaults = rules.isEmpty() && Objects.equals(effective("", target, channel, MessageLocales.KO), ko)
                        && Objects.equals(effective("", target, channel, MessageLocales.EN), en);
                if (!defaults) {
                    selectionRepo.persist(NotificationMessageSelectionEntity.create(target.type().name(), target.code(), channel.name(), ko, en, rulesJson,
                            actor.operatorId(), now));
                    changed.add(channel.name());
                }
            }
        }
        if (!changed.isEmpty()) {
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NOTIFICATION_MESSAGE_TARGET_UPDATE, AuditResult.SUCCESS)
                    .target("NOTIFICATION_MESSAGE_TARGET", target.type().name() + ":" + target.code()).with("channels", changed));
            support.invalidate();
            log.info("알림 문구 대상의 선택·규칙을 저장했다 target={} channels={} by operatorId={}", target.code(), changed, actor.operatorId());
        }
        return detail(target, state());
    }

    // ── 문구 ────────────────────────────────────────────────────────────────

    /** 모든 문구 목록(본문 없이) — 기본 문구 먼저 */
    public List<MessageListItemView> messages() {
        State state = state();
        Map<Long, String> names = support.operatorNames(state.userMessages.stream().map(MessageValues::updatedBy).toList());
        Map<String, String> targetNames = new HashMap<>();
        Map<String, String> templateNames = new HashMap<>();
        List<MessageListItemView> out = new ArrayList<>();
        for (MessageValues message : state.all()) {
            Optional<MessageTarget> found = MessageTargetCatalog.find(message.targetType(), message.targetCode());
            if (found.isEmpty()) {
                continue; // 카탈로그에서 빠진 대상의 옛 문구 — 보이지 않는다
            }
            MessageTarget target = found.get();
            String targetName = targetNames.computeIfAbsent(target.type() + ":" + target.code(), k -> support.targetName(target));
            String templateName = message.templateId() == null ? null
                    : templateNames.computeIfAbsent(message.channel() + ":" + message.templateId(), k -> support.templateName(message.channel(), message.templateId()));
            Usage usage = state.usage(message, target);
            out.add(new MessageListItemView(message.id(), TemplateSupport.ref(target), targetName, target.area(), target.security(), message.channel().name(),
                    message.channel().preparing(), support.messageName(message), message.locale(), message.templateId(), templateName, message.builtin(),
                    message.updatedAt(), message.updatedBy() == null ? null : names.get(message.updatedBy()), usage.defaultFor, usage.ruleCount));
        }
        return out;
    }

    public MessageView message(String id) {
        MessageValues message = support.message(id).orElseThrow(MessageNotFoundException::new);
        MessageTarget target = MessageTargetCatalog.find(message.targetType(), message.targetCode()).orElseThrow(MessageNotFoundException::new);
        return support.messageView(message, target, support.operatorNames(message.updatedBy() == null ? List.of() : List.of(message.updatedBy())));
    }

    @Transactional
    public MessageView create(AdminActor actor, MessageSaveRequest body) {
        MessageSaveRequest request = body == null ? new MessageSaveRequest(null, null, null, null, null, null, null, null) : body;
        MessageTarget target = support.target(request.targetType(), request.targetCode());
        MessageChannel channel = channelOf(target, request.channel());
        if (messageRepo.countByTargetTypeAndTargetCodeAndChannel(target.type(), target.code(), channel) >= MESSAGE_LIMIT) {
            throw new MessageLimitException(MESSAGE_LIMIT);
        }
        Clean clean = validate(target, channel, request);
        Instant now = Instant.now();
        NotificationMessageEntity row = NotificationMessageEntity.create(ids.nextId(), target.type(), target.code(), channel, clean.locale, clean.name, clean.templateId,
                clean.subject, clean.body, actor.operatorId(), now);
        messageRepo.persist(row);

        List<TemplateChange> changes = new ArrayList<>();
        changes.add(new TemplateChange("name", null, clean.name));
        changes.add(new TemplateChange("locale", null, clean.locale));
        if (clean.templateId != null) {
            changes.add(new TemplateChange("templateId", null, support.templateName(channel, clean.templateId)));
        }
        history.recordMessage(row.getId(), TemplateHistoryBase.ACTION_CREATE, actor.operatorId(), now, changes);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NOTIFICATION_MESSAGE_CREATE, AuditResult.SUCCESS)
                .target("NOTIFICATION_MESSAGE", String.valueOf(row.getId())).with("target", target.code()).with("channel", channel.name()).with("locale", clean.locale));
        support.invalidate();
        log.info("알림 문구를 만들었다 id={} target={} channel={} by operatorId={}", row.getId(), target.code(), channel, actor.operatorId());
        return support.messageView(TemplateSupport.values(row), target, support.operatorNames(List.of(actor.operatorId())));
    }

    @Transactional
    public MessageView update(AdminActor actor, String id, MessageSaveRequest body) {
        if (BuiltinMessages.isBuiltinId(id)) {
            if (support.message(id).isEmpty()) {
                throw new MessageNotFoundException();
            }
            throw new MessageBuiltinException();
        }
        Long key = TemplateSupport.parseId(id);
        NotificationMessageEntity row = key == null ? null : messageRepo.findWithLockById(key).orElse(null);
        if (row == null) {
            throw new MessageNotFoundException();
        }
        MessageTarget target = MessageTargetCatalog.find(row.getTargetType(), row.getTargetCode()).orElseThrow(MessageNotFoundException::new);
        if (row.getChannel().preparing()) {
            throw new ChannelPreparingException(support.channelName(row.getChannel()));
        }
        MessageSaveRequest request = body == null ? new MessageSaveRequest(null, null, null, null, null, null, null, null) : body;
        // 대상 · 채널은 바꾸지 못한다 — 원래 값으로 본다
        Clean clean = validate(target, row.getChannel(), new MessageSaveRequest(row.getTargetType().name(), row.getTargetCode(), row.getChannel().name(), request.name(),
                request.locale(), request.templateId(), request.subject(), request.body()));
        if (!clean.locale.equals(row.getLocale())) {
            NotificationMessageSelectionEntity selection = selectionRepo.findById(
                    new NotificationMessageSelectionId(target.type().name(), target.code(), row.getChannel().name())).orElse(null);
            String current = selection == null ? "" : MessageLocales.EN.equals(row.getLocale()) ? selection.getSelectedEn() : selection.getSelectedKo();
            if (id.strip().equals(current)) {
                throw new ValidationException(Map.of("locale", new MessageRef("notificationTemplate.problem.localeInUse")));
            }
        }
        List<TemplateChange> changes = new ArrayList<>();
        TemplateHistoryService.diff(changes, "name", row.getName(), clean.name);
        TemplateHistoryService.diff(changes, "locale", row.getLocale(), clean.locale);
        if (!Objects.equals(row.getTemplateId(), clean.templateId)) {
            changes.add(new TemplateChange("templateId", support.templateName(row.getChannel(), row.getTemplateId()), support.templateName(row.getChannel(), clean.templateId)));
        }
        TemplateHistoryService.diff(changes, "subject", row.getSubject(), clean.subject);
        if (!Objects.equals(row.getBody(), clean.body)) {
            changes.add(new TemplateChange("body", TemplateText.excerpt(row.getBody()), TemplateText.excerpt(clean.body)));
        }
        if (changes.isEmpty()) {
            return support.messageView(TemplateSupport.values(row), target, support.operatorNames(nullable(row.getUpdatedBy())));
        }
        Instant now = Instant.now();
        row.change(clean.locale, clean.name, clean.templateId, clean.subject, clean.body, actor.operatorId(), now);
        history.recordMessage(row.getId(), TemplateHistoryBase.ACTION_UPDATE, actor.operatorId(), now, changes);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NOTIFICATION_MESSAGE_UPDATE, AuditResult.SUCCESS)
                .target("NOTIFICATION_MESSAGE", String.valueOf(row.getId())).with("fields", changes.stream().map(TemplateChange::field).toList()));
        support.invalidate();
        log.info("알림 문구를 고쳤다 id={} 칸={} by operatorId={}", row.getId(), changes.stream().map(TemplateChange::field).toList(), actor.operatorId());
        return support.messageView(TemplateSupport.values(row), target, support.operatorNames(List.of(actor.operatorId())));
    }

    @Transactional
    public void delete(AdminActor actor, String id) {
        if (BuiltinMessages.isBuiltinId(id)) {
            if (support.message(id).isEmpty()) {
                throw new MessageNotFoundException();
            }
            throw new MessageBuiltinException();
        }
        Long key = TemplateSupport.parseId(id);
        NotificationMessageEntity row = key == null ? null : messageRepo.findWithLockById(key).orElse(null);
        if (row == null) {
            throw new MessageNotFoundException();
        }
        if (row.getChannel().preparing()) {
            throw new ChannelPreparingException(support.channelName(row.getChannel()));
        }
        String messageId = String.valueOf(row.getId());
        NotificationMessageSelectionEntity selection = selectionRepo.findById(
                new NotificationMessageSelectionId(row.getTargetType().name(), row.getTargetCode(), row.getChannel().name())).orElse(null);
        if (selection != null && (messageId.equals(selection.getSelectedKo()) || messageId.equals(selection.getSelectedEn())
                || MessageRule.parse(selection.getRules()).stream().anyMatch(rule -> messageId.equals(rule.messageId())))) {
            throw new MessageInUseException();
        }
        messageRepo.delete(row);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NOTIFICATION_MESSAGE_DELETE, AuditResult.SUCCESS)
                .target("NOTIFICATION_MESSAGE", messageId).with("target", row.getTargetCode()).with("channel", row.getChannel().name()));
        support.invalidate();
        log.info("알림 문구를 지웠다 id={} by operatorId={}", messageId, actor.operatorId());
    }

    public TemplateHistoryPage history(String id, String page, String size) {
        MessageValues message = support.message(id).orElseThrow(MessageNotFoundException::new);
        return history.messagePage(message.builtin() ? null : Long.parseLong(message.id()), page, size);
    }

    // ── 검사 ────────────────────────────────────────────────────────────────

    /** 저장할 칸 — 채널마다 쓰지 않는 칸은 비운다(이메일 본문은 정화된 HTML) */
    record Clean(String name, String locale, String templateId, String subject, String body) {
    }

    private MessageChannel channelOf(MessageTarget target, String raw) {
        MessageChannel channel = MessageChannel.of(raw).orElseThrow(() ->
                new ValidationException(Map.of("channel", new MessageRef("notificationTemplate.problem.channel.unknown"))));
        if (!target.has(channel)) {
            throw new ValidationException(Map.of("channel", new MessageRef("notificationTemplate.problem.channel.notInTarget", support.targetName(target),
                    support.channelName(channel))));
        }
        if (channel.preparing()) {
            throw new ChannelPreparingException(support.channelName(channel));
        }
        return channel;
    }

    /** 문구 내용 검사(채널마다 다른 규칙) — 위반은 400 {@code fields.name|subject|body|templateId|locale} */
    Clean validate(MessageTarget target, MessageChannel channel, MessageSaveRequest request) {
        MessageChannelDef def = target.channel(channel).orElseThrow();
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        String name = request.name() == null ? "" : request.name().strip();
        String subject = request.subject() == null ? "" : request.subject();
        String templateId = request.templateId() == null || request.templateId().isBlank() ? null : request.templateId().strip();
        String body;
        switch (channel) {
            case EMAIL -> {
                body = MailHtml.sanitize(request.body());
                validator.mail(fields, name, subject, body, target.audience(), target.security(), def.variables());
                Optional<com.community.notificationtemplate.engine.MailTemplateValues> template = support.mailTemplate(templateId);
                if (template.isEmpty()) {
                    fields.putIfAbsent("templateId", new MessageRef("notificationTemplate.problem.templateId.mail"));
                } else if (!template.get().audience().fits(target.audience())) {
                    fields.putIfAbsent("templateId", new MessageRef("notificationTemplate.problem.templateId.mailAudience", support.audienceName(target.audience().name())));
                }
            }
            case SLACK, MATTERMOST -> {
                body = request.body() == null ? "" : request.body();
                validator.titled(fields, name, subject, body, target.audience(), def.variables(), MessageContentValidator.CHAT_TITLE_MAX,
                        MessageContentValidator.CHAT_BODY_MAX);
                if (support.chatTemplate(templateId, channel).isEmpty()) {
                    fields.putIfAbsent("templateId", new MessageRef("notificationTemplate.problem.templateId.chat", support.channelName(channel)));
                }
            }
            case IN_APP -> {
                body = request.body() == null ? "" : request.body();
                templateId = null;
                validator.titled(fields, name, subject, body, target.audience(), def.variables(), MessageContentValidator.IN_APP_TITLE_MAX,
                        MessageContentValidator.IN_APP_BODY_MAX);
            }
            default -> throw new ChannelPreparingException(support.channelName(channel));
        }
        if (!MessageContentValidator.validLocale(request.locale())) {
            fields.putIfAbsent("locale", new MessageRef("notificationTemplate.problem.locale"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return new Clean(name, request.locale(), templateId, subject, body);
    }

    // ── 보기 ────────────────────────────────────────────────────────────────

    /** 한 번에 읽은 사용자 문구 · 선택 */
    private final class State {

        final List<MessageValues> userMessages;
        final Map<String, NotificationMessageSelectionEntity> selections;

        State(List<MessageValues> userMessages, Map<String, NotificationMessageSelectionEntity> selections) {
            this.userMessages = userMessages;
            this.selections = selections;
        }

        List<MessageValues> all() {
            List<MessageValues> out = new ArrayList<>(support.builtinMessages());
            out.addAll(userMessages);
            return out;
        }

        NotificationMessageSelectionEntity selection(MessageTarget target, MessageChannel channel) {
            return selections.get(target.type().name() + ":" + target.code() + ":" + channel.name());
        }

        String selected(MessageTarget target, MessageChannel channel, String locale) {
            NotificationMessageSelectionEntity row = selection(target, channel);
            String stored = row == null ? "" : MessageLocales.EN.equals(locale) ? row.getSelectedEn() : row.getSelectedKo();
            return effective(stored, target, channel, locale);
        }

        List<MessageRule> rules(MessageTarget target, MessageChannel channel) {
            NotificationMessageSelectionEntity row = selection(target, channel);
            return row == null ? List.of() : MessageRule.parse(row.getRules());
        }

        Usage usage(MessageValues message, MessageTarget target) {
            List<String> defaultFor = MessageLocales.ALL.stream().filter(locale -> message.id().equals(selected(target, message.channel(), locale))).toList();
            int ruleCount = (int) rules(target, message.channel()).stream().filter(rule -> message.id().equals(rule.messageId())).count();
            return new Usage(defaultFor, ruleCount);
        }

        Optional<MessageValues> find(String id) {
            if (BuiltinMessages.isBuiltinId(id)) {
                return support.message(id);
            }
            return userMessages.stream().filter(m -> m.id().equals(id)).findFirst();
        }
    }

    private record Usage(List<String> defaultFor, int ruleCount) {
    }

    private State state() {
        List<MessageValues> user = messageRepo.findAllByOrderByIdAsc().stream().map(TemplateSupport::values).toList();
        Map<String, NotificationMessageSelectionEntity> selections = new HashMap<>();
        selectionRepo.findAll().forEach(row -> selections.put(row.getTargetType() + ":" + row.getTargetCode() + ":" + row.getChannel(), row));
        return new State(user, selections);
    }

    /** 저장된 「쓸 문구」 — 비었으면 그 언어의 기본 문구(기본 문구가 없는 채널은 빈 글자) */
    private static String effective(String stored, MessageTarget target, MessageChannel channel, String locale) {
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        return target.channel(channel).filter(MessageChannelDef::hasBuiltin).map(def -> BuiltinMessages.id(target, channel, locale)).orElse("");
    }

    private MessageTargetView targetView(MessageTarget target, State state) {
        List<MessageChannelSummaryView> channels = new ArrayList<>();
        for (MessageChannelDef def : target.channels()) {
            Map<String, MessageSelectedView> selected = new LinkedHashMap<>();
            for (String locale : MessageLocales.ALL) {
                String id = state.selected(target, def.channel(), locale);
                selected.put(locale, id.isEmpty() ? null : state.find(id).map(m -> new MessageSelectedView(m.id(), support.messageName(m), m.builtin())).orElse(null));
            }
            int messageCount = (int) state.userMessages.stream()
                    .filter(m -> m.targetType() == target.type() && m.targetCode().equals(target.code()) && m.channel() == def.channel()).count();
            channels.add(new MessageChannelSummaryView(def.channel().name(), def.channel().preparing(), support.variableViews(target, def), support.conditionViews(def),
                    selected, state.rules(target, def.channel()).size(), messageCount));
        }
        return new MessageTargetView(target.type().name(), target.code(), target.audience().name(), target.area(),
                target.severity() == null ? null : target.severity().name(), support.targetName(target), support.targetDescription(target), target.security(), channels);
    }

    private MessageTargetDetailView detail(MessageTarget target, State state) {
        List<MessageChannelDetailView> channels = new ArrayList<>();
        List<MessageValues> mine = state.userMessages.stream().filter(m -> m.targetType() == target.type() && m.targetCode().equals(target.code())).toList();
        Map<Long, String> names = support.operatorNames(mine.stream().map(MessageValues::updatedBy).toList());
        for (MessageChannelDef def : target.channels()) {
            Map<String, String> defaults = new LinkedHashMap<>();
            Map<String, String> selected = new LinkedHashMap<>();
            for (String locale : MessageLocales.ALL) {
                defaults.put(locale, def.hasBuiltin() ? BuiltinMessages.id(target, def.channel(), locale) : null);
                selected.put(locale, state.selected(target, def.channel(), locale));
            }
            List<MessageRuleView> rules = state.rules(target, def.channel()).stream()
                    .map(rule -> new MessageRuleView(rule.id(), new MessageRuleConditionView(rule.key(), rule.value()), rule.messageId())).toList();
            List<MessageView> messages = new ArrayList<>();
            for (MessageValues message : support.messagesOf(target, def.channel())) {
                if (message.builtin()) {
                    messages.add(support.messageView(message, target, names));
                }
            }
            mine.stream().filter(m -> m.channel() == def.channel()).forEach(m -> messages.add(support.messageView(m, target, names)));
            channels.add(new MessageChannelDetailView(def.channel().name(), defaults, selected, rules, messages));
        }
        return new MessageTargetDetailView(targetView(target, state), channels);
    }

    /** 이 대상 · 채널의 문구인가(기본 것 또는 사용자 것) */
    private Optional<MessageValues> own(String id, MessageTarget target, MessageChannel channel) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return support.message(id.strip()).filter(m -> m.channel() == channel && m.targetType() == target.type() && m.targetCode().equals(target.code()));
    }

    private static List<Long> nullable(Long id) {
        return id == null ? List.of() : List.of(id);
    }
}
