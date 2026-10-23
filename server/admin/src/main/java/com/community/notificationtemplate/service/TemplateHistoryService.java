package com.community.notificationtemplate.service;

import com.community.notificationtemplate.dto.TemplateChange;
import com.community.notificationtemplate.dto.response.TemplateHistoryChangeView;
import com.community.notificationtemplate.dto.response.TemplateHistoryItemView;
import com.community.notificationtemplate.dto.response.TemplateHistoryPage;
import com.community.notificationtemplate.engine.ChatColor;
import com.community.notificationtemplate.engine.MailColor;
import com.community.notificationtemplate.entity.NotificationChatTemplateHistoryEntity;
import com.community.notificationtemplate.entity.NotificationMailTemplateHistoryEntity;
import com.community.notificationtemplate.entity.NotificationMessageHistoryEntity;
import com.community.notificationtemplate.entity.TemplateHistoryBase;
import com.community.notificationtemplate.repository.master.NotificationChatTemplateHistoryRepository;
import com.community.notificationtemplate.repository.master.NotificationMailTemplateHistoryRepository;
import com.community.notificationtemplate.repository.master.NotificationMessageHistoryRepository;
import com.community.notificationtemplate.repository.master.TemplateHistoryQueries;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 문구 · 메일 템플릿 · Slack/Mattermost 템플릿 변경 이력 쓰기·읽기(V57 의 이력 표 셋 — 설정 이력과 같은 모양). 쓰기는 저장하는 트랜잭션에 참여하고 바뀐 칸이 없으면 쓰지 않는다.
 * 기본 문구·템플릿은 이력이 없다(빈 쪽).
 */
@Service
public class TemplateHistoryService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;
    private static final String LABEL_KEY_PREFIX = "settingHistory.field.";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final NotificationMessageHistoryRepository messageHistory;
    private final NotificationMailTemplateHistoryRepository mailHistory;
    private final NotificationChatTemplateHistoryRepository chatHistory;
    private final TemplateHistoryQueries queries;
    private final SnowflakeIdGenerator ids;
    private final TemplateSupport support;
    private final Messages messages;

    public TemplateHistoryService(NotificationMessageHistoryRepository messageHistory, NotificationMailTemplateHistoryRepository mailHistory,
                                  NotificationChatTemplateHistoryRepository chatHistory, TemplateHistoryQueries queries, SnowflakeIdGenerator ids, TemplateSupport support,
                                  Messages messages) {
        this.messageHistory = messageHistory;
        this.mailHistory = mailHistory;
        this.chatHistory = chatHistory;
        this.queries = queries;
        this.ids = ids;
        this.support = support;
        this.messages = messages;
    }

    /** 칸이 바뀌었으면 하나 더한다 */
    public static void diff(List<TemplateChange> changes, String field, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.add(new TemplateChange(field, before, after));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordMessage(long messageId, String action, long operatorId, Instant now, List<TemplateChange> changes) {
        if (!changes.isEmpty()) {
            messageHistory.persist(NotificationMessageHistoryEntity.create(ids.nextId(), messageId, action, operatorId, now, MAPPER.writeValueAsString(changes)));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordMail(long templateId, String action, long operatorId, Instant now, List<TemplateChange> changes) {
        if (!changes.isEmpty()) {
            mailHistory.persist(NotificationMailTemplateHistoryEntity.create(ids.nextId(), templateId, action, operatorId, now, MAPPER.writeValueAsString(changes)));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordChat(long templateId, String action, long operatorId, Instant now, List<TemplateChange> changes) {
        if (!changes.isEmpty()) {
            chatHistory.persist(NotificationChatTemplateHistoryEntity.create(ids.nextId(), templateId, action, operatorId, now, MAPPER.writeValueAsString(changes)));
        }
    }

    /** 사용자 문구의 이력 한 쪽(기본 문구는 {@code messageId} 가 null — 빈 쪽) */
    @Transactional(readOnly = true)
    public TemplateHistoryPage messagePage(Long messageId, String page, String size) {
        int[] ps = pageAndSize(page, size);
        return messageId == null ? empty(ps) : page(ps, () -> queries.messageCount(messageId), () -> queries.messagePage(messageId, ps[0], ps[1]));
    }

    @Transactional(readOnly = true)
    public TemplateHistoryPage mailPage(Long templateId, String page, String size) {
        int[] ps = pageAndSize(page, size);
        return templateId == null ? empty(ps) : page(ps, () -> queries.mailCount(templateId), () -> queries.mailPage(templateId, ps[0], ps[1]));
    }

    @Transactional(readOnly = true)
    public TemplateHistoryPage chatPage(Long templateId, String page, String size) {
        int[] ps = pageAndSize(page, size);
        return templateId == null ? empty(ps) : page(ps, () -> queries.chatCount(templateId), () -> queries.chatPage(templateId, ps[0], ps[1]));
    }

    private TemplateHistoryPage page(int[] ps, Supplier<Long> count, Supplier<List<? extends TemplateHistoryBase>> rows) {
        long total = count.get();
        List<TemplateHistoryItemView> items = new ArrayList<>();
        if (total > 0 && (long) ps[0] * ps[1] < total) {
            List<? extends TemplateHistoryBase> found = rows.get();
            Map<Long, String> names = support.operatorNames(found.stream().map(TemplateHistoryBase::getOperatorId).toList());
            String deleted = messages.get(new MessageRef("mail.history.operatorDeleted"));
            for (TemplateHistoryBase row : found) {
                String name = row.getOperatorId() == null ? null : names.get(row.getOperatorId());
                boolean gone = name == null || name.equals(deleted);
                items.add(new TemplateHistoryItemView(String.valueOf(row.getId()), row.getAction(), row.getSource(),
                        row.getOperatorId() == null ? null : String.valueOf(row.getOperatorId()), gone ? deleted : name, gone, row.getChangedAt(), changes(row.getChanges())));
            }
        }
        return new TemplateHistoryPage(items, total, ps[0], ps[1]);
    }

    private static TemplateHistoryPage empty(int[] ps) {
        return new TemplateHistoryPage(List.of(), 0, ps[0], ps[1]);
    }

    private List<TemplateHistoryChangeView> changes(String json) {
        List<TemplateChange> parsed;
        try {
            parsed = json == null ? List.of() : List.of(MAPPER.readValue(json, TemplateChange[].class));
        } catch (RuntimeException e) {
            parsed = List.of();
        }
        return parsed.stream().map(c -> new TemplateHistoryChangeView(c.field(), LABEL_KEY_PREFIX + c.field(), display(c.field(), c.before()), display(c.field(), c.after())))
                .toList();
    }

    /** 코드로 담은 값(색 · 쓰는 대상)을 요청 언어의 이름으로 */
    private String display(String field, String value) {
        if (value == null) {
            return null;
        }
        return switch (field) {
            case "headerColor" -> MailColor.of(value).map(c -> messages.get(new MessageRef(c.nameKey())))
                    .orElseGet(() -> messages.get(new MessageRef("notificationTemplate.color.none")));
            case "colorInfo", "colorWarning", "colorCritical" -> ChatColor.of(value).map(c -> messages.get(new MessageRef(c.nameKey()))).orElse(value);
            case "audience" -> support.audienceName(value);
            default -> value;
        };
    }

    /** 쪽 번호·크기 — 비었으면 기본값, 정수가 아니거나 범위 밖이면 400(설정 이력과 같은 규칙) */
    private static int[] pageAndSize(String page, String size) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        MessageRef range = new MessageRef("mail.history.page.range", String.valueOf(MAX_SIZE));
        Integer p = integer(page, 0, 0, Integer.MAX_VALUE / MAX_SIZE);
        if (p == null) {
            fields.put("page", range);
        }
        Integer s = integer(size, DEFAULT_SIZE, 1, MAX_SIZE);
        if (s == null) {
            fields.put("size", range);
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return new int[] {p, s};
    }

    private static Integer integer(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.strip());
            return value < min || value > max ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
