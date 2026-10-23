package com.community.notificationtemplate.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.notificationtemplate.builtin.BuiltinTemplates;
import com.community.notificationtemplate.catalog.MailTemplateAudience;
import com.community.notificationtemplate.catalog.MessageAudience;
import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageLocales;
import com.community.notificationtemplate.dto.TemplateChange;
import com.community.notificationtemplate.dto.request.MailTemplateSaveRequest;
import com.community.notificationtemplate.dto.response.MailTemplateListItemView;
import com.community.notificationtemplate.dto.response.MailTemplateView;
import com.community.notificationtemplate.dto.response.MessageVariableView;
import com.community.notificationtemplate.dto.response.TemplateHistoryPage;
import com.community.notificationtemplate.engine.MailColor;
import com.community.notificationtemplate.engine.MailHtml;
import com.community.notificationtemplate.engine.MailTemplateValues;
import com.community.notificationtemplate.engine.MessageTemplateStore;
import com.community.notificationtemplate.engine.MessageValues;
import com.community.notificationtemplate.engine.NotificationMessageRenderer;
import com.community.notificationtemplate.engine.TemplateText;
import com.community.notificationtemplate.entity.NotificationMailTemplateEntity;
import com.community.notificationtemplate.entity.TemplateHistoryBase;
import com.community.notificationtemplate.exception.TemplateBuiltinException;
import com.community.notificationtemplate.exception.TemplateInUseException;
import com.community.notificationtemplate.exception.TemplateLimitException;
import com.community.notificationtemplate.exception.TemplateNotFoundException;
import com.community.notificationtemplate.repository.master.NotificationMailTemplateRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메일 템플릿(메일의 겉모양 틀 — 옛 「메일 레이아웃」) 관리. 기본 템플릿 둘(회원 기본 · 운영자 기본)은 코드가 원천이라 고치거나 지울 수 없다(409 {@code TEMPLATE_BUILTIN}).
 * 사용자 템플릿 {@value #LIMIT}개까지(409 {@code TEMPLATE_LIMIT}), 쓰는 문구가 있으면 지울 수 없다(409 {@code TEMPLATE_IN_USE}), 쓰는 문구의 받는 사람과 맞지 않는 쓰는 대상으로
 * 바꿀 수 없다(400 {@code fields.audience}). 틀은 정화하고({@link MailHtml}) {@code {{content}}} 정확히 한 번, 꼬리말은 평문 500자 — 변수는 쓰는 대상의 공통 변수만.
 */
@Service
@Transactional(readOnly = true)
public class MailTemplateAdminService {

    private static final Logger log = LoggerFactory.getLogger(MailTemplateAdminService.class);

    static final int LIMIT = 20;

    private final NotificationMailTemplateRepository repo;
    private final BuiltinTemplates builtins;
    private final TemplateSupport support;
    private final MessageContentValidator validator;
    private final TemplateHistoryService history;
    private final NotificationMessageRenderer renderer;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;

    public MailTemplateAdminService(NotificationMailTemplateRepository repo, BuiltinTemplates builtins, TemplateSupport support, MessageContentValidator validator,
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

    /** 목록 — 기본 템플릿 먼저, 그다음 사용자 템플릿(만든 차례). 쓰는 대상으로 거를 수 있다(그 값 그대로) */
    public List<MailTemplateListItemView> list(String audience) {
        List<MailTemplateValues> all = new ArrayList<>(builtins.mailTemplates(support.messages().locale()));
        repo.findAllByOrderByIdAsc().forEach(row -> all.add(TemplateSupport.values(row)));
        Map<Long, String> names = support.operatorNames(all.stream().map(MailTemplateValues::updatedBy).toList());
        return all.stream().filter(t -> audience == null || audience.isBlank() || t.audience().name().equals(audience)).map(t -> {
            List<MessageValues> using = support.usingMessages(MessageChannel.EMAIL, t.id());
            return new MailTemplateListItemView(t.id(), t.name(), t.builtin(), t.audience().name(), color(t.headerColor()), using.size(),
                    TemplateSupport.countsByAudience(using), t.updatedAt(), t.updatedBy() == null ? null : names.get(t.updatedBy()));
        }).toList();
    }

    public MailTemplateView get(String id) {
        return view(support.mailTemplate(id).orElseThrow(TemplateNotFoundException::new));
    }

    @Transactional
    public MailTemplateView create(AdminActor actor, MailTemplateSaveRequest body) {
        if (repo.count() >= LIMIT) {
            throw new TemplateLimitException(LIMIT);
        }
        Clean clean = validate(body, null);
        Instant now = Instant.now();
        NotificationMailTemplateEntity row = NotificationMailTemplateEntity.create(ids.nextId(), clean.name, clean.audience, clean.showLogo, clean.showSiteName,
                clean.headerColor, clean.footerKo, clean.footerEn, clean.frameKo, clean.frameEn, actor.operatorId(), now);
        repo.persist(row);
        history.recordMail(row.getId(), TemplateHistoryBase.ACTION_CREATE, actor.operatorId(), now,
                List.of(new TemplateChange("name", null, clean.name), new TemplateChange("audience", null, clean.audience.name())));
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NOTIFICATION_TEMPLATE_CREATE, AuditResult.SUCCESS)
                .target("NOTIFICATION_TEMPLATE", String.valueOf(row.getId())).with("type", "MAIL"));
        support.invalidate();
        log.info("메일 템플릿을 만들었다 id={} by operatorId={}", row.getId(), actor.operatorId());
        return view(TemplateSupport.values(row));
    }

    @Transactional
    public MailTemplateView update(AdminActor actor, String id, MailTemplateSaveRequest body) {
        NotificationMailTemplateEntity row = lockUser(id);
        Clean clean = validate(body, row);
        List<TemplateChange> changes = new ArrayList<>();
        TemplateHistoryService.diff(changes, "name", row.getName(), clean.name);
        TemplateHistoryService.diff(changes, "audience", row.getAudience().name(), clean.audience.name());
        TemplateHistoryService.diff(changes, "showLogo", String.valueOf(row.isShowLogo()), String.valueOf(clean.showLogo));
        TemplateHistoryService.diff(changes, "showSiteName", String.valueOf(row.isShowSiteName()), String.valueOf(clean.showSiteName));
        TemplateHistoryService.diff(changes, "headerColor", colorCode(row.getHeaderColor()), colorCode(clean.headerColor));
        TemplateHistoryService.diff(changes, "footerKo", row.getFooterKo(), clean.footerKo);
        TemplateHistoryService.diff(changes, "footerEn", row.getFooterEn(), clean.footerEn);
        if (!Objects.equals(row.getFrameKo(), clean.frameKo)) {
            changes.add(new TemplateChange("frameKo", TemplateText.excerpt(row.getFrameKo()), TemplateText.excerpt(clean.frameKo)));
        }
        if (!Objects.equals(row.getFrameEn(), clean.frameEn)) {
            changes.add(new TemplateChange("frameEn", TemplateText.excerpt(row.getFrameEn()), TemplateText.excerpt(clean.frameEn)));
        }
        if (changes.isEmpty()) {
            return view(TemplateSupport.values(row));
        }
        Instant now = Instant.now();
        row.change(clean.name, clean.audience, clean.showLogo, clean.showSiteName, clean.headerColor, clean.footerKo, clean.footerEn, clean.frameKo, clean.frameEn,
                actor.operatorId(), now);
        history.recordMail(row.getId(), TemplateHistoryBase.ACTION_UPDATE, actor.operatorId(), now, changes);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NOTIFICATION_TEMPLATE_UPDATE, AuditResult.SUCCESS)
                .target("NOTIFICATION_TEMPLATE", String.valueOf(row.getId())).with("type", "MAIL").with("fields", changes.stream().map(TemplateChange::field).toList()));
        support.invalidate();
        return view(TemplateSupport.values(row));
    }

    @Transactional
    public void delete(AdminActor actor, String id) {
        NotificationMailTemplateEntity row = lockUser(id);
        int count = support.usingMessages(MessageChannel.EMAIL, String.valueOf(row.getId())).size();
        if (count > 0) {
            throw new TemplateInUseException(count);
        }
        repo.delete(row);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NOTIFICATION_TEMPLATE_DELETE, AuditResult.SUCCESS)
                .target("NOTIFICATION_TEMPLATE", String.valueOf(row.getId())).with("type", "MAIL"));
        support.invalidate();
        log.info("메일 템플릿을 지웠다 id={} by operatorId={}", row.getId(), actor.operatorId());
    }

    public TemplateHistoryPage history(String id, String page, String size) {
        MailTemplateValues template = support.mailTemplate(id).orElseThrow(TemplateNotFoundException::new);
        return history.mailPage(template.builtin() ? null : Long.parseLong(template.id()), page, size);
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private record Clean(String name, MailTemplateAudience audience, boolean showLogo, boolean showSiteName, String headerColor, String footerKo, String footerEn,
                         String frameKo, String frameEn) {
    }

    /** 사용자 템플릿을 잠가 읽는다 — 기본 템플릿이면 409, 없으면 404 */
    private NotificationMailTemplateEntity lockUser(String id) {
        if (BuiltinTemplates.isMailBuiltin(id)) {
            throw new TemplateBuiltinException();
        }
        Long key = TemplateSupport.parseId(id);
        return (key == null ? java.util.Optional.<NotificationMailTemplateEntity>empty() : repo.findWithLockById(key)).orElseThrow(TemplateNotFoundException::new);
    }

    /** 저장 전 검사 — 위반은 400 {@code fields.name|audience|headerColor|footer.ko|footer.en|frames.ko|frames.en} */
    Clean validate(MailTemplateSaveRequest body, NotificationMailTemplateEntity current) {
        MailTemplateSaveRequest request = body == null ? new MailTemplateSaveRequest(null, null, null, null, null, null, null) : body;
        MailTemplateAudience audience = MailTemplateAudience.of(request.audience() == null ? "" : request.audience()).orElse(null);
        if (audience == null) {
            throw new ValidationException(Map.of("audience", new MessageRef("notificationTemplate.problem.audience")));
        }
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        String name = request.name() == null ? "" : request.name().strip();
        MessageRef nameProblem = validator.nameProblem(name);
        if (nameProblem != null) {
            fields.put("name", nameProblem);
        }
        Map<String, String> footer = request.footer() == null ? Map.of() : request.footer();
        Map<String, String> frames = request.frames() == null ? Map.of() : request.frames();
        String footerKo = strip(footer.get(MessageLocales.KO));
        String footerEn = strip(footer.get(MessageLocales.EN));
        String frameKo = MailHtml.sanitize(frames.get(MessageLocales.KO));
        String frameEn = MailHtml.sanitize(frames.get(MessageLocales.EN));
        putIf(fields, "footer.ko", validator.mailFooter(footerKo, audience));
        putIf(fields, "frames.ko", validator.mailFrame(frameKo, audience));
        putIf(fields, "footer.en", validator.mailFooter(footerEn, audience));
        putIf(fields, "frames.en", validator.mailFrame(frameEn, audience));
        String color = request.headerColor() == null || request.headerColor().isBlank() ? null : request.headerColor().strip();
        if (color != null && MailColor.of(color).isEmpty()) {
            fields.put("headerColor", new MessageRef("notificationTemplate.problem.color"));
        }
        if (current != null && audience != current.getAudience() && audience != MailTemplateAudience.ANY) {
            Map<String, Integer> counts = TemplateSupport.countsByAudience(support.usingMessages(MessageChannel.EMAIL, String.valueOf(current.getId())));
            MessageAudience other = audience == MailTemplateAudience.MEMBER ? MessageAudience.OPERATOR : MessageAudience.MEMBER;
            int using = counts.getOrDefault(other.name(), 0);
            if (using > 0) {
                fields.put("audience", new MessageRef("notificationTemplate.problem.audienceInUse", support.audienceName(other.name()), String.valueOf(using)));
            }
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return new Clean(name, audience, Boolean.TRUE.equals(request.showLogo()), Boolean.TRUE.equals(request.showSiteName()), color, footerKo, footerEn, frameKo, frameEn);
    }

    private MailTemplateView view(MailTemplateValues t) {
        List<MessageValues> using = support.usingMessages(MessageChannel.EMAIL, t.id());
        Map<String, List<MessageVariableView>> variables = new LinkedHashMap<>();
        for (MailTemplateAudience audience : MailTemplateAudience.values()) {
            variables.put(audience.name(), support.commonViews(audience, true));
        }
        MessageTemplateStore.Site site = renderer.site();
        String by = t.updatedBy() == null ? null : support.operatorNames(List.of(t.updatedBy())).get(t.updatedBy());
        return new MailTemplateView(t.id(), t.name(), t.builtin(), t.audience().name(), t.showLogo(), t.showSiteName(), color(t.headerColor()), localeMap(t.footer()),
                localeMap(t.frames()), using.size(), TemplateSupport.countsByAudience(using), t.updatedAt(), by, site.logoUrl(),
                site.siteName() == null ? "" : site.siteName(), variables);
    }

    private static Map<String, String> localeMap(Map<String, String> values) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String locale : MessageLocales.ALL) {
            out.put(locale, values.getOrDefault(locale, ""));
        }
        return out;
    }

    private static String color(MailColor color) {
        return color == null ? null : color.name();
    }

    /** 이력에 담는 띠 색 — 없으면 {@code NONE}(조회 때 「없음」) */
    private static String colorCode(String color) {
        return color == null ? "NONE" : color;
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    private static void putIf(Map<String, MessageRef> fields, String field, MessageRef problem) {
        if (problem != null) {
            fields.put(field, problem);
        }
    }
}
