package com.community.notificationtemplate.service;

import com.community.batch.catalog.BatchCatalog;
import com.community.notificationtemplate.builtin.BuiltinMessages;
import com.community.notificationtemplate.builtin.BuiltinTemplates;
import com.community.notificationtemplate.catalog.MailTemplateAudience;
import com.community.notificationtemplate.catalog.MessageAudience;
import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageChannelDef;
import com.community.notificationtemplate.catalog.MessageCondition;
import com.community.notificationtemplate.catalog.MessageTarget;
import com.community.notificationtemplate.catalog.MessageTargetCatalog;
import com.community.notificationtemplate.catalog.MessageVariable;
import com.community.notificationtemplate.dto.response.MessageConditionOptionView;
import com.community.notificationtemplate.dto.response.MessageConditionView;
import com.community.notificationtemplate.dto.response.MessageTargetRefView;
import com.community.notificationtemplate.dto.response.MessageVariableView;
import com.community.notificationtemplate.dto.response.MessageView;
import com.community.notificationtemplate.engine.ChatColor;
import com.community.notificationtemplate.engine.ChatTemplateValues;
import com.community.notificationtemplate.engine.MailColor;
import com.community.notificationtemplate.engine.MailTemplateValues;
import com.community.notificationtemplate.engine.MessageTemplateStore;
import com.community.notificationtemplate.engine.MessageValues;
import com.community.notificationtemplate.engine.NotificationMessageRenderer;
import com.community.notificationtemplate.engine.TemplateText;
import com.community.notificationtemplate.entity.NotificationChatTemplateEntity;
import com.community.notificationtemplate.entity.NotificationMailTemplateEntity;
import com.community.notificationtemplate.entity.NotificationMessageEntity;
import com.community.notificationtemplate.exception.MessageTargetNotFoundException;
import com.community.notificationtemplate.repository.master.NotificationChatTemplateRepository;
import com.community.notificationtemplate.repository.master.NotificationMailTemplateRepository;
import com.community.notificationtemplate.repository.master.NotificationMessageRepository;
import com.community.notificationtemplate.catalog.MessageLocales;
import com.community.notificationtemplate.catalog.MessageSeverity;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.role.entity.RoleAxis;
import com.community.role.entity.RoleEntity;
import com.community.role.repository.master.RoleRepository;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 알림 템플릿·문구 관리 API 가 같이 쓰는 도우미 — 대상 찾기, 문구·템플릿 찾기(기본 것 + 사용자 것), 변수·조건 보기(요청 언어), 견본 값, 운영자 이름.
 * 읽기는 부르는 서비스의 트랜잭션 안에서 한다(엔진의 15초 사본이 아니라 DB 의 지금 값).
 */
@Component
public class TemplateSupport {

    static final String SAMPLE_ADMIN_URL = "https://admin.roboyam.example.com";
    static final String SAMPLE_SITE_URL = "https://roboyam.example.com";

    private final Messages messages;
    private final BuiltinMessages builtins;
    private final BuiltinTemplates builtinTemplates;
    private final NotificationMessageRepository messageRepo;
    private final NotificationMailTemplateRepository mailRepo;
    private final NotificationChatTemplateRepository chatRepo;
    private final OperatorRepository operators;
    private final RoleRepository roles;
    private final NotificationMessageRenderer renderer;
    private final MessageTemplateStore store;

    public TemplateSupport(Messages messages, BuiltinMessages builtins, BuiltinTemplates builtinTemplates, NotificationMessageRepository messageRepo,
                           NotificationMailTemplateRepository mailRepo, NotificationChatTemplateRepository chatRepo, OperatorRepository operators, RoleRepository roles,
                           NotificationMessageRenderer renderer, MessageTemplateStore store) {
        this.messages = messages;
        this.builtins = builtins;
        this.builtinTemplates = builtinTemplates;
        this.messageRepo = messageRepo;
        this.mailRepo = mailRepo;
        this.chatRepo = chatRepo;
        this.operators = operators;
        this.roles = roles;
        this.renderer = renderer;
        this.store = store;
    }

    // ── 대상 ────────────────────────────────────────────────────────────────

    public MessageTarget target(String type, String code) {
        return MessageTargetCatalog.find(type, code).orElseThrow(MessageTargetNotFoundException::new);
    }

    public String targetName(MessageTarget target) {
        return messages.get(new MessageRef(target.nameKey()));
    }

    public String targetDescription(MessageTarget target) {
        return messages.get(new MessageRef(target.descriptionKey()));
    }

    public static MessageTargetRefView ref(MessageTarget target) {
        return new MessageTargetRefView(target.type().name(), target.code(), target.audience().name());
    }

    public String channelName(MessageChannel channel) {
        return messages.get(new MessageRef("notificationTemplate.channel." + channel.name()));
    }

    public String audienceName(String audience) {
        return messages.get(new MessageRef("notificationTemplate.audience." + audience));
    }

    // ── 문구 ────────────────────────────────────────────────────────────────

    /** 기본 문구 또는 사용자 문구 */
    public Optional<MessageValues> message(String id) {
        if (BuiltinMessages.isBuiltinId(id)) {
            return builtins.find(id);
        }
        Long key = parseId(id);
        return key == null ? Optional.empty() : messageRepo.findById(key).map(TemplateSupport::values);
    }

    public static MessageValues values(NotificationMessageEntity row) {
        return new MessageValues(String.valueOf(row.getId()), row.getTargetType(), row.getTargetCode(), row.getChannel(), row.getLocale(), row.getName(),
                row.getTemplateId(), row.getSubject(), row.getBody(), false, row.getUpdatedAt(), row.getUpdatedBy());
    }

    /** 기본 문구 전부(대상 · 채널 · 언어 차례) */
    public List<MessageValues> builtinMessages() {
        return builtins.all();
    }

    /** 대상 · 채널의 문구(기본 먼저, 그다음 사용자 문구 — 만든 차례) */
    public List<MessageValues> messagesOf(MessageTarget target, MessageChannel channel) {
        List<MessageValues> out = new ArrayList<>();
        for (String locale : MessageLocales.ALL) {
            builtins.of(target, channel, locale).ifPresent(out::add);
        }
        messageRepo.findAllByTargetTypeAndTargetCodeAndChannelOrderByIdAsc(target.type(), target.code(), channel).forEach(row -> out.add(values(row)));
        return out;
    }

    public String messageName(MessageValues message) {
        return message.builtin() ? messages.get(new MessageRef("notificationTemplate.builtin.messageName")) : message.name();
    }

    public MessageView messageView(MessageValues message, MessageTarget target, Map<Long, String> names) {
        return new MessageView(message.id(), ref(target), message.channel().name(), messageName(message), message.locale(), message.templateId(), message.subject(),
                message.body(), message.builtin(), message.updatedAt(), message.updatedBy() == null ? null : names.get(message.updatedBy()));
    }

    // ── 템플릿 ──────────────────────────────────────────────────────────────

    public Optional<MailTemplateValues> mailTemplate(String id) {
        if (id == null) {
            return Optional.empty();
        }
        if (BuiltinTemplates.isMailBuiltin(id)) {
            return builtinTemplates.mailTemplate(id, messages.locale());
        }
        Long key = parseId(id);
        return key == null ? Optional.empty() : mailRepo.findById(key).map(TemplateSupport::values);
    }

    public static MailTemplateValues values(NotificationMailTemplateEntity row) {
        return new MailTemplateValues(String.valueOf(row.getId()), row.getName(), false, row.getAudience(), row.isShowLogo(), row.isShowSiteName(),
                MailColor.of(row.getHeaderColor()).orElse(null), Map.of(MessageLocales.KO, nz(row.getFooterKo()), MessageLocales.EN, nz(row.getFooterEn())),
                Map.of(MessageLocales.KO, nz(row.getFrameKo()), MessageLocales.EN, nz(row.getFrameEn())), row.getUpdatedAt(), row.getUpdatedBy());
    }

    public Optional<ChatTemplateValues> chatTemplate(String id, MessageChannel tool) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<ChatTemplateValues> found;
        if (BuiltinTemplates.isChatBuiltin(id)) {
            found = builtinTemplates.chatTemplate(id, messages.locale());
        } else {
            Long key = parseId(id);
            found = key == null ? Optional.empty() : chatRepo.findById(key).map(TemplateSupport::values);
        }
        return found.filter(template -> template.tool() == tool);
    }

    public static ChatTemplateValues values(NotificationChatTemplateEntity row) {
        NotificationChatTemplateEntity.Fields f = row.fields();
        return new ChatTemplateValues(String.valueOf(row.getId()), row.getTool(), f.name(), false, f.showSeverityIcon(), f.boldTitle(),
                Map.of(MessageSeverity.INFO, ChatColor.of(f.colorInfo()).orElse(ChatColor.BLUE), MessageSeverity.WARNING, ChatColor.of(f.colorWarning()).orElse(ChatColor.AMBER),
                        MessageSeverity.CRITICAL, ChatColor.of(f.colorCritical()).orElse(ChatColor.RED)),
                f.showSiteName(), f.showSentAt(), f.showOpenButton(), Map.of(MessageLocales.KO, nz(f.openLabelKo()), MessageLocales.EN, nz(f.openLabelEn())),
                Map.of(MessageLocales.KO, nz(f.frameKo()), MessageLocales.EN, nz(f.frameEn())), row.getUpdatedAt(), row.getUpdatedBy());
    }

    /** 채널의 템플릿 이름(목록 · 이력) — 없으면 null */
    public String templateName(MessageChannel channel, String templateId) {
        if (templateId == null) {
            return null;
        }
        return switch (channel) {
            case EMAIL -> mailTemplate(templateId).map(MailTemplateValues::name).orElse(null);
            case SLACK, MATTERMOST -> chatTemplate(templateId, channel).map(ChatTemplateValues::name).orElse(null);
            case KAKAO -> null;
            case IN_APP -> null;
        };
    }

    /** 템플릿을 쓰는 문구(기본 문구 포함) */
    public List<MessageValues> usingMessages(MessageChannel channel, String templateId) {
        List<MessageValues> out = new ArrayList<>();
        builtins.all().stream().filter(m -> m.channel() == channel && templateId.equals(m.templateId())).forEach(out::add);
        messageRepo.findAllByChannelAndTemplateId(channel, templateId).forEach(row -> out.add(values(row)));
        return out;
    }

    /** 받는 사람별 쓰는 문구 수 */
    public static Map<String, Integer> countsByAudience(List<MessageValues> using) {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put(MessageAudience.MEMBER.name(), 0);
        out.put(MessageAudience.OPERATOR.name(), 0);
        for (MessageValues message : using) {
            MessageTargetCatalog.find(message.targetType(), message.targetCode()).ifPresent(target -> out.merge(target.audience().name(), 1, Integer::sum));
        }
        return out;
    }

    // ── 변수 · 조건 · 견본 값 ────────────────────────────────────────────────

    public List<MessageVariableView> variableViews(MessageTarget target, MessageChannelDef def) {
        Map<String, String> samples = samples(target, def);
        return def.variables().stream().map(v -> view(v, samples.get(v.name()))).toList();
    }

    /** 공통 변수(+ 틀의 본문 자리) 보기 — 메일 템플릿 · Slack/Mattermost 템플릿 화면 */
    public List<MessageVariableView> commonViews(MailTemplateAudience audience, boolean withContent) {
        List<MessageVariableView> out = new ArrayList<>();
        if (withContent) {
            out.add(view(MessageTargetCatalog.contentVariable(), ""));
        }
        for (MessageVariable variable : MessageTargetCatalog.commonVariables(audience)) {
            out.add(view(variable, commonSample(variable)));
        }
        return out;
    }

    /** 공통 변수의 견본 값 */
    public Map<String, String> commonSamples(MailTemplateAudience audience) {
        Map<String, String> out = new HashMap<>();
        for (MessageVariable variable : MessageTargetCatalog.commonVariables(audience)) {
            out.put(variable.name(), commonSample(variable));
        }
        return out;
    }

    /** 대상 · 채널의 견본 값(미리보기 · 시험) */
    public Map<String, String> samples(MessageTarget target, MessageChannelDef def) {
        Map<String, String> out = new HashMap<>();
        for (MessageVariable variable : def.variables()) {
            switch (variable.sample()) {
                case IN_APP_TITLE, IN_APP_BODY -> {
                }
                case LINK -> out.put(variable.name(), linkSample(target));
                default -> out.put(variable.name(), commonSample(variable));
            }
        }
        if (def.variables().stream().anyMatch(v -> v.sample() == MessageVariable.Sample.IN_APP_TITLE || v.sample() == MessageVariable.Sample.IN_APP_BODY)) {
            String locale = MessageLocales.of(messages.locale()) == null ? MessageLocales.KO : MessageLocales.of(messages.locale());
            Map<String, String> inAppSamples = target.channel(MessageChannel.IN_APP).map(inApp -> {
                Map<String, String> s = new HashMap<>();
                inApp.variables().forEach(v -> s.put(v.name(), v.sample() == MessageVariable.Sample.LINK ? linkSample(target) : commonSample(v)));
                return s;
            }).orElse(Map.of());
            builtins.of(target, MessageChannel.IN_APP, locale).ifPresent(inApp -> {
                out.put(MessageVariable.TITLE, TemplateText.fill(inApp.subject(), inAppSamples::get, true));
                out.put(MessageVariable.BODY, TemplateText.fill(inApp.body(), inAppSamples::get, true));
            });
            out.putIfAbsent(MessageVariable.TITLE, messages.get(new MessageRef("notificationTemplate.var.title.sample")));
            out.putIfAbsent(MessageVariable.BODY, messages.get(new MessageRef("notificationTemplate.var.body.sample")));
        }
        return out;
    }

    public List<MessageConditionView> conditionViews(MessageChannelDef def) {
        return def.conditions().stream().map(c -> new MessageConditionView(c.key(), messages.get(new MessageRef(c.labelKey())), options(c))).toList();
    }

    /** 그 조건의 선택지에 값이 있는가 */
    public boolean conditionAllows(MessageCondition condition, String value) {
        return value != null && options(condition).stream().anyMatch(option -> option.value().equals(value));
    }

    private List<MessageConditionOptionView> options(MessageCondition condition) {
        return switch (condition.source()) {
            case STATIC -> condition.values().stream().map(v -> new MessageConditionOptionView(v, messages.get(new MessageRef(condition.optionLabelKey(v))))).toList();
            case BATCH_JOBS -> java.util.Arrays.stream(BatchCatalog.values())
                    .map(job -> new MessageConditionOptionView(job.key(), messages.get(new MessageRef(job.nameKey())))).toList();
            case OPERATOR_ROLES -> roles.findAll().stream().filter(role -> role.getAxis() == RoleAxis.OPERATOR)
                    .sorted(Comparator.comparing(RoleEntity::getRoleId))
                    .map(role -> new MessageConditionOptionView(role.getRoleId(), role.getRoleName())).toList();
        };
    }

    private MessageVariableView view(MessageVariable variable, String sample) {
        return new MessageVariableView(variable.name(), messages.get(new MessageRef(variable.descriptionKey())), sample == null ? "" : sample, variable.required(),
                variable.link());
    }

    private String commonSample(MessageVariable variable) {
        MessageTemplateStore.Site site = renderer.site();
        return switch (variable.sample()) {
            case SITE_NAME -> site.siteName() != null && !site.siteName().isBlank() ? site.siteName() : bundleSample(variable);
            case SITE_URL -> site.publicUrl() != null ? site.publicUrl() : bundleSample(variable);
            case ADMIN_URL -> renderer.adminUrl() != null ? renderer.adminUrl() : bundleSample(variable);
            default -> bundleSample(variable);
        };
    }

    private String bundleSample(MessageVariable variable) {
        return messages.get(new MessageRef(variable.sampleKey()));
    }

    private String linkSample(MessageTarget target) {
        String base;
        if (target.audience() == MessageAudience.OPERATOR) {
            base = renderer.adminUrl() != null ? renderer.adminUrl() : SAMPLE_ADMIN_URL;
        } else {
            String publicUrl = renderer.site().publicUrl();
            base = publicUrl != null ? publicUrl : SAMPLE_SITE_URL;
        }
        return base + (target.samplePath() == null ? "/" : target.samplePath());
    }

    // ── 운영자 이름 · 캐시 ──────────────────────────────────────────────────

    /** 운영자 id → 이름(지워진 운영자는 「삭제된 운영자」) */
    public Map<Long, String> operatorNames(Collection<Long> ids) {
        Set<Long> wanted = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> out = new HashMap<>();
        if (wanted.isEmpty()) {
            return out;
        }
        for (OperatorEntity operator : operators.findAllById(wanted)) {
            out.put(operator.getId(), operator.getOperatorName());
        }
        String deleted = messages.get(new MessageRef("mail.history.operatorDeleted"));
        wanted.forEach(id -> out.putIfAbsent(id, deleted));
        return out;
    }

    /** 렌더링 엔진의 사본을 비운다 — 트랜잭션 안이면 커밋된 뒤에(커밋 전에 비우면 다른 요청이 옛 값을 다시 15초 붙잡을 수 있다) */
    public void invalidate() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    store.invalidate();
                }
            });
        } else {
            store.invalidate();
        }
    }

    public Messages messages() {
        return messages;
    }

    public static Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
