package com.community.notification.service;

import com.community.notification.dto.response.NotificationItemView;
import com.community.notification.dto.response.NotificationLink;
import com.community.notification.dto.response.NotificationPageView;
import com.community.notification.dto.response.NotificationSummaryView;
import com.community.notification.entity.AdminNotificationEntity;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.exception.NotificationNotFoundException;
import com.community.notification.repository.master.AdminNotificationRepositoryQDSL;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종 알림 서랍 — 운영자 <b>본인</b>의 알림만(모든 질의에 내 id 가 들어가고 경로에 운영자 id 가 없다). 목록·안 읽은 수·요약은 {@link NotificationVisibility} 의 <b>같은 거르기</b>를 쓴다
 * (권한이 없는 이벤트·내가 끈 이벤트는 모두에서 빠진다). 제목·본문은 저장된 문구 키 + 인자를 <b>읽는 사람의 요청 언어</b>로 푼다(발행은 다른 사람의 요청에서 일어났을 수 있다).
 * 목록은 최근순 커서 쪽(커서 = 마지막으로 받은 알림의 id)이다.
 */
@Service
public class NotificationQueryService {

    static final int DEFAULT_SIZE = 30;
    static final int MAX_SIZE = 100;

    private static final Pattern DIGITS = Pattern.compile("^[0-9]{1,18}$");

    private final AdminNotificationRepositoryQDSL notifications;
    private final NotificationVisibility visibility;
    private final Messages messages;

    public NotificationQueryService(AdminNotificationRepositoryQDSL notifications, NotificationVisibility visibility, Messages messages) {
        this.notifications = notifications;
        this.visibility = visibility;
        this.messages = messages;
    }

    /** 최근순 한 쪽. 커서·크기가 올바르지 않으면 400 {@code fields.cursor|size} */
    @Transactional(readOnly = true)
    public NotificationPageView list(AdminActor actor, String cursor, Integer size) {
        Long beforeId = cursorOf(cursor);
        int limit = size == null ? DEFAULT_SIZE : size;
        if (limit < 1 || limit > MAX_SIZE) {
            throw ValidationException.of("size", new MessageRef("notification.size.range", String.valueOf(MAX_SIZE)));
        }

        Set<String> codes = visibility.codesOf(actor);
        List<AdminNotificationEntity> rows = notifications.page(actor.operatorId(), codes, beforeId, limit + 1);
        boolean more = rows.size() > limit;
        List<AdminNotificationEntity> pageRows = more ? rows.subList(0, limit) : rows;

        Locale locale = messages.locale();
        List<NotificationItemView> items = new ArrayList<>();
        for (AdminNotificationEntity row : pageRows) {
            items.add(item(row, locale));
        }
        String nextCursor = more ? items.get(items.size() - 1).id() : null;
        return new NotificationPageView(items, nextCursor, notifications.unread(actor.operatorId(), codes));
    }

    /** 폴링용 요약 — 안 읽은 수와 가장 최근 알림의 id */
    @Transactional(readOnly = true)
    public NotificationSummaryView summary(AdminActor actor) {
        Set<String> codes = visibility.codesOf(actor);
        Long latest = notifications.latestId(actor.operatorId(), codes);
        return new NotificationSummaryView(notifications.unread(actor.operatorId(), codes), latest == null ? null : Long.toString(latest));
    }

    /** 읽음 처리 — 내 알림만(아니면 404 {@code NOTIFICATION_NOT_FOUND}). 이미 읽은 알림은 그대로 성공한다 */
    @Transactional
    public void markRead(AdminActor actor, String id) {
        if (id == null || !DIGITS.matcher(id).matches()) {
            throw new NotificationNotFoundException();
        }
        long notificationId = Long.parseLong(id);
        if (!notifications.exists(actor.operatorId(), notificationId)) {
            throw new NotificationNotFoundException();
        }
        notifications.markRead(actor.operatorId(), notificationId, Instant.now());
    }

    /** 내가 볼 수 있는 안 읽은 알림을 모두 읽음 처리한다 */
    @Transactional
    public void markAllRead(AdminActor actor) {
        notifications.markAllRead(actor.operatorId(), visibility.codesOf(actor), Instant.now());
    }

    private NotificationItemView item(AdminNotificationEntity row, Locale locale) {
        AdminNotificationEvent event = AdminNotificationEvent.ofCode(row.getEventCode()).orElseThrow(); // 거르기가 코드에 있는 이벤트만 통과시킨다
        String title = NotificationArgs.text(row.getTitleKey(), row.getArgs(), messages, locale);
        String body = NotificationArgs.text(row.getBodyKey(), row.getArgs(), messages, locale);
        return new NotificationItemView(Long.toString(row.getId()), row.getEventCode(), event.area().id(), row.getSeverity().kind(),
                title == null ? messages.get(new MessageRef(event.nameKey()), locale) : title, body,
                row.getLinkPath() == null ? null : new NotificationLink(row.getLinkPath()), row.getCreatedAt(), row.isRead());
    }

    /** 커서 — 없으면 처음부터. 숫자(알림 id)가 아니면 400 {@code fields.cursor} */
    private static Long cursorOf(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        if (!DIGITS.matcher(cursor.strip()).matches()) {
            throw ValidationException.of("cursor", new MessageRef("notification.cursor.invalid"));
        }
        return Long.parseLong(cursor.strip());
    }
}
