package com.community.notification.service;

import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.entity.NotificationEventEntity;
import com.community.notification.repository.master.NotificationEventRepository;
import com.community.notification.repository.master.OperatorNotificationMuteRepository;
import com.community.permission.service.BootstrapLock;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 이벤트 카탈로그(코드 enum — {@link AdminNotificationEvent})를 {@code notification_events} 표와 <b>맞춘다</b>. 서버가 뜰 때 한 번 돈다({@code NotificationBootstrapRunner}).
 *
 * <ul>
 *   <li>코드에 새로 생긴 이벤트는 코드의 처음 값(사용 켜짐, 이메일은 보낼 수 있는 이벤트만 켜짐)으로 넣는다.</li>
 *   <li><b>운영자가 바꾼 {@code enabled}·{@code email_enabled} 는 덮어쓰지 않는다</b>. 다만 코드의 불변 규칙과 어긋난 값(필수 이벤트가 꺼져 있음, 이메일을 보낼 수 없는 이벤트의 이메일이 켜져 있음)은 규칙에 맞춘다.</li>
 *   <li>코드에서 사라진 이벤트는 표에서 지우고 그 이벤트의 개인 끄기 기록도 지운다(알림 행은 이벤트 코드를 글자로 갖고 문구 키로 읽히므로 남기고, 보관 정책이 지운다 — 서랍에서는 코드에 없는 이벤트라 보이지 않는다).</li>
 *   <li>여러 서버가 동시에 떠도 겹치지 않게 DB 어드바이저리 잠금(트랜잭션 단위)으로 한 서버씩 돈다. 같은 결과로 끝나므로 다시 돌아도 안전하다.</li>
 * </ul>
 */
@Service
public class NotificationCatalogSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(NotificationCatalogSynchronizer.class);

    private final NotificationEventRepository events;
    private final OperatorNotificationMuteRepository mutes;
    private final EntityManager entityManager;

    public NotificationCatalogSynchronizer(NotificationEventRepository events, OperatorNotificationMuteRepository mutes, EntityManager entityManager) {
        this.events = events;
        this.mutes = mutes;
        this.entityManager = entityManager;
    }

    @Transactional
    public void sync() {
        BootstrapLock.acquire(entityManager);

        Instant now = Instant.now();
        Map<String, NotificationEventEntity> existing = new LinkedHashMap<>();
        events.findAll().forEach(entity -> existing.put(entity.getCode(), entity));

        int added = 0;
        int reconciled = 0;
        for (AdminNotificationEvent event : AdminNotificationEvent.values()) {
            NotificationEventEntity entity = existing.get(event.code());
            if (entity == null) {
                events.persist(NotificationEventEntity.of(event, now));
                added++;
            } else if (entity.reconcile(event, now)) {
                reconciled++;
            }
        }

        Set<String> stale = new HashSet<>(existing.keySet());
        stale.removeIf(code -> AdminNotificationEvent.ofCode(code).isPresent());
        if (!stale.isEmpty()) {
            mutes.deleteByEventCodeIn(stale);
            events.deleteAllByIdInBatch(stale);
            log.warn("카탈로그에서 사라진 알림 이벤트 {}개를 지웠다: {}", stale.size(), stale);
        }
        log.info("알림 이벤트 카탈로그를 맞췄다: 이벤트 {}개 (새로 {} · 규칙에 맞춰 고침 {} · 지움 {})", AdminNotificationEvent.values().length, added, reconciled, stale.size());
    }
}
