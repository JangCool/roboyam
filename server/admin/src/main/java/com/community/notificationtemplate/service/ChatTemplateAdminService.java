package com.community.notificationtemplate.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.notificationtemplate.builtin.BuiltinTemplates;
import com.community.notificationtemplate.catalog.MailTemplateAudience;
import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageLocales;
import com.community.notificationtemplate.catalog.MessageSeverity;
import com.community.notificationtemplate.dto.TemplateChange;
import com.community.notificationtemplate.dto.request.ChatTemplateSaveRequest;
import com.community.notificationtemplate.dto.response.ChatTemplateListItemView;
import com.community.notificationtemplate.dto.response.ChatTemplateView;
import com.community.notificationtemplate.dto.response.TemplateHistoryPage;
import com.community.notificationtemplate.engine.ChatColor;
import com.community.notificationtemplate.engine.ChatTemplateValues;
import com.community.notificationtemplate.engine.NotificationMessageRenderer;
import com.community.notificationtemplate.engine.TemplateText;
import com.community.notificationtemplate.entity.NotificationChatTemplateEntity;
import com.community.notificationtemplate.entity.TemplateHistoryBase;
import com.community.notificationtemplate.exception.TemplateBuiltinException;
import com.community.notificationtemplate.exception.TemplateInUseException;
import com.community.notificationtemplate.exception.TemplateLimitException;
import com.community.notificationtemplate.exception.TemplateNotFoundException;
import com.community.notificationtemplate.repository.master.NotificationChatTemplateRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Slack · Mattermost 템플릿(메시지 카드의 겉모양) 관리 — 종류는 주소 조각({@code slack} · {@code mattermost})이다. 종류마다 기본 템플릿 하나(코드가 원천, 409 {@code TEMPLATE_BUILTIN}),
 * 사용자 템플릿 {@value #LIMIT}개(409 {@code TEMPLATE_LIMIT}), 쓰는 문구가 있으면 지울 수 없다(409 {@code TEMPLATE_IN_USE}).
 * 검사: 이름 · 팔레트 밖 색({@code fields.colors.INFO …}) · 「열기」 버튼 글자(보일 때 필수·20자) · 틀의 {@code {{content}}} 한 번 · 500자 · 모르는 변수.
 */
@Service
@Transactional(readOnly = true)
public class ChatTemplateAdminService {

    private static final Logger log = LoggerFactory.getLogger(ChatTemplateAdminService.class);

    static final int LIMIT = 20;

    private final NotificationChatTemplateRepository repo;
    private final BuiltinTemplates builtins;
    private final TemplateSupport support;
    private final MessageContentValidator validator;
    private final TemplateHistoryService history;
    private final NotificationMessageRenderer renderer;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;

    public ChatTemplateAdminService(NotificationChatTemplateRepository repo, BuiltinTemplates builtins, TemplateSupport support, MessageContentValidator validator,
                                    TemplateHistoryService history, NotificationMessageRenderer renderer, AuditLogService audit, SnowflakeIdGenerator ids) {
        this.repo = repo;
        this.builtins = builtins;
        this.support = support;
        this.validator = validator;
        this.history = history;
        this.renderer = renderer;
        this.audit = audit;
        this.ids = ids;
    }

    /** 주소 조각 → 종류. 모르는 값이면 404 */
    public static MessageChannel tool(String path) {
        String value = path == null ? "" : path.strip().toUpperCase(Locale.ROOT);
        return MessageChannel.of(value).filter(MessageChannel::chat).orElseThrow(TemplateNotFoundException::new);
    }

    public List<ChatTemplateListItemView> list(MessageChannel tool) {
        List<ChatTemplateValues> all = new ArrayList<>();
        all.add(builtins.chat(tool, support.messages().locale()));
        repo.findAllByToolOrderByIdAsc(tool).forEach(row -> all.add(TemplateSupport.values(row)));
        Map<Long, String> names = support.operatorNames(all.stream().map(ChatTemplateValues::updatedBy).toList());
        return all.stream().map(t -> new ChatTemplateListItemView(t.id(), tool.name(), t.name(), t.builtin(), colors(t), support.usingMessages(tool, t.id()).size(),
                t.updatedAt(), t.updatedBy() == null ? null : names.get(t.updatedBy()))).toList();
    }

    public ChatTemplateView get(MessageChannel tool, String id) {
        return view(support.chatTemplate(id, tool).orElseThrow(TemplateNotFoundException::new));
    }

    @Transactional
    public ChatTemplateView create(AdminActor actor, MessageChannel tool, ChatTemplateSaveRequest body) {
        if (repo.countByTool(tool) >= LIMIT) {
            throw new TemplateLimitException(LIMIT);
        }
        NotificationChatTemplateEntity.Fields fields = validate(body);
        Instant now = Instant.now();
        NotificationChatTemplateEntity row = NotificationChatTemplateEntity.create(ids.nextId(), tool, fields, actor.operatorId(), now);
        repo.persist(row);
        history.recordChat(row.getId(), TemplateHistoryBase.ACTION_CREATE, actor.operatorId(), now, List.of(new TemplateChange("name", null, fields.name())));
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NOTIFICATION_TEMPLATE_CREATE, AuditResult.SUCCESS)
                .target("NOTIFICATION_TEMPLATE", String.valueOf(row.getId())).with("type", tool.name()));
        support.invalidate();
        log.info("{} 템플릿을 만들었다 id={} by operatorId={}", tool, row.getId(), actor.operatorId());
        return view(TemplateSupport.values(row));
    }

    @Transactional
    public ChatTemplateView update(AdminActor actor, MessageChannel tool, String id, ChatTemplateSaveRequest body) {
        NotificationChatTemplateEntity row = lockUser(tool, id);
        NotificationChatTemplateEntity.Fields next = validate(body);
        NotificationChatTemplateEntity.Fields before = row.fields();
        List<TemplateChange> changes = new ArrayList<>();
        TemplateHistoryService.diff(changes, "name", before.name(), next.name());
        TemplateHistoryService.diff(changes, "showSeverityIcon", String.valueOf(before.showSeverityIcon()), String.valueOf(next.showSeverityIcon()));
        TemplateHistoryService.diff(changes, "boldTitle", String.valueOf(before.boldTitle()), String.valueOf(next.boldTitle()));
        TemplateHistoryService.diff(changes, "colorInfo", before.colorInfo(), next.colorInfo());
        TemplateHistoryService.diff(changes, "colorWarning", before.colorWarning(), next.colorWarning());
        TemplateHistoryService.diff(changes, "colorCritical", before.colorCritical(), next.colorCritical());
        TemplateHistoryService.diff(changes, "showSiteName", String.valueOf(before.showSiteName()), String.valueOf(next.showSiteName()));
        TemplateHistoryService.diff(changes, "showSentAt", String.valueOf(before.showSentAt()), String.valueOf(next.showSentAt()));
        TemplateHistoryService.diff(changes, "showOpenButton", String.valueOf(before.showOpenButton()), String.valueOf(next.showOpenButton()));
        TemplateHistoryService.diff(changes, "openLabelKo", before.openLabelKo(), next.openLabelKo());
        TemplateHistoryService.diff(changes, "openLabelEn", before.openLabelEn(), next.openLabelEn());
        if (!Objects.equals(before.frameKo(), next.frameKo())) {
            changes.add(new TemplateChange("frameKo", TemplateText.excerpt(before.frameKo()), TemplateText.excerpt(next.frameKo())));
        }
        if (!Objects.equals(before.frameEn(), next.frameEn())) {
            changes.add(new TemplateChange("frameEn", TemplateText.excerpt(before.frameEn()), TemplateText.excerpt(next.frameEn())));
        }
        if (changes.isEmpty()) {
            return view(TemplateSupport.values(row));
        }
        Instant now = Instant.now();
        row.change(next, actor.operatorId(), now);
        history.recordChat(row.getId(), TemplateHistoryBase.ACTION_UPDATE, actor.operatorId(), now, changes);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NOTIFICATION_TEMPLATE_UPDATE, AuditResult.SUCCESS)
                .target("NOTIFICATION_TEMPLATE", String.valueOf(row.getId())).with("type", tool.name()).with("fields", changes.stream().map(TemplateChange::field).toList()));
        support.invalidate();
        return view(TemplateSupport.values(row));
    }

    @Transactional
    public void delete(AdminActor actor, MessageChannel tool, String id) {
        NotificationChatTemplateEntity row = lockUser(tool, id);
        int count = support.usingMessages(tool, String.valueOf(row.getId())).size();
        if (count > 0) {
            throw new TemplateInUseException(count);
        }
        repo.delete(row);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NOTIFICATION_TEMPLATE_DELETE, AuditResult.SUCCESS)
                .target("NOTIFICATION_TEMPLATE", String.valueOf(row.getId())).with("type", tool.name()));
        support.invalidate();
        log.info("{} 템플릿을 지웠다 id={} by operatorId={}", tool, row.getId(), actor.operatorId());
    }

    public TemplateHistoryPage history(MessageChannel tool, String id, String page, String size) {
        ChatTemplateValues template = support.chatTemplate(id, tool).orElseThrow(TemplateNotFoundException::new);
        return history.chatPage(template.builtin() ? null : Long.parseLong(template.id()), page, size);
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private NotificationChatTemplateEntity lockUser(MessageChannel tool, String id) {
        if (BuiltinTemplates.isChatBuiltin(id)) {
            if (!BuiltinTemplates.chatId(tool).equals(id)) {
                throw new TemplateNotFoundException();
            }
            throw new TemplateBuiltinException();
        }
        Long key = TemplateSupport.parseId(id);
        Optional<NotificationChatTemplateEntity> found = key == null ? Optional.empty() : repo.findWithLockById(key);
        return found.filter(row -> row.getTool() == tool).orElseThrow(TemplateNotFoundException::new);
    }

    /** 저장 전 검사 — 위반은 400 {@code fields.name|colors.INFO|colors.WARNING|colors.CRITICAL|openLabel.ko|openLabel.en|frames.ko|frames.en} */
    NotificationChatTemplateEntity.Fields validate(ChatTemplateSaveRequest body) {
        ChatTemplateSaveRequest request = body == null ? new ChatTemplateSaveRequest(null, null, null, null, null, null, null, null, null) : body;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        String name = request.name() == null ? "" : request.name().strip();
        MessageRef nameProblem = validator.nameProblem(name);
        if (nameProblem != null) {
            fields.put("name", nameProblem);
        }
        boolean showOpen = Boolean.TRUE.equals(request.showOpenButton());
        Map<String, String> labels = request.openLabel() == null ? Map.of() : request.openLabel();
        Map<String, String> frames = request.frames() == null ? Map.of() : request.frames();
        for (String locale : MessageLocales.ALL) {
            if (showOpen) {
                MessageRef label = validator.openLabel(labels.get(locale));
                if (label != null) {
                    fields.put("openLabel." + locale, label);
                }
            }
            MessageRef frame = validator.chatFrame(frames.get(locale));
            if (frame != null) {
                fields.put("frames." + locale, frame);
            }
        }
        Map<String, String> colors = request.colors() == null ? Map.of() : request.colors();
        for (MessageSeverity severity : MessageSeverity.values()) {
            if (ChatColor.of(colors.get(severity.name())).isEmpty()) {
                fields.put("colors." + severity.name(), new MessageRef("notificationTemplate.problem.color"));
            }
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return new NotificationChatTemplateEntity.Fields(name, Boolean.TRUE.equals(request.showSeverityIcon()), Boolean.TRUE.equals(request.boldTitle()),
                colors.get(MessageSeverity.INFO.name()), colors.get(MessageSeverity.WARNING.name()), colors.get(MessageSeverity.CRITICAL.name()),
                Boolean.TRUE.equals(request.showSiteName()), Boolean.TRUE.equals(request.showSentAt()), showOpen, strip(labels.get(MessageLocales.KO)),
                strip(labels.get(MessageLocales.EN)), nz(frames.get(MessageLocales.KO)), nz(frames.get(MessageLocales.EN)));
    }

    private ChatTemplateView view(ChatTemplateValues t) {
        String by = t.updatedBy() == null ? null : support.operatorNames(List.of(t.updatedBy())).get(t.updatedBy());
        String siteName = renderer.site().siteName();
        Map<String, String> open = new LinkedHashMap<>();
        Map<String, String> frames = new LinkedHashMap<>();
        for (String locale : MessageLocales.ALL) {
            open.put(locale, t.openLabel(locale));
            frames.put(locale, t.frame(locale));
        }
        return new ChatTemplateView(t.id(), t.tool().name(), t.name(), t.builtin(), colors(t), support.usingMessages(t.tool(), t.id()).size(), t.updatedAt(), by,
                t.showSeverityIcon(), t.boldTitle(), t.showSiteName(), t.showSentAt(), t.showOpenButton(), open, frames, siteName == null ? "" : siteName,
                support.commonViews(MailTemplateAudience.OPERATOR, true));
    }

    private static Map<String, String> colors(ChatTemplateValues t) {
        Map<String, String> out = new LinkedHashMap<>();
        for (MessageSeverity severity : MessageSeverity.values()) {
            out.put(severity.name(), t.color(severity).name());
        }
        return out;
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
