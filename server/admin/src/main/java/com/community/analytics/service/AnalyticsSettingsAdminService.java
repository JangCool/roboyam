package com.community.analytics.service;

import com.community.cache.service.CacheRefresher;
import com.community.cache.service.CacheSaveSource;
import com.community.analytics.dto.AnalyticsSettingsAdmin;
import com.community.analytics.settings.StatsCollection;
import com.community.analytics.settings.StatsCollectionStore;
import com.community.analytics.store.AnalyticsDatabase;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * 「설정 › 통계 수집」(admin-web {@code /settings/analytics}, docs/features/site-analytics-contract.md 「설정」·「API」) — 조회 {@code site.read} · 저장 {@code site.manage}(권한은 컨트롤러).
 *
 * <ul>
 *   <li>저장: 한 행(shared V133 {@code stats_collection_settings} + V135, 마이그레이션이 기본값으로 만들어 둔다)을 잠가 덮는다. 1단계 여덟 칸 모두 필요 — 비면 400 {@code fields.<칸>},
 *       보관 개월 수가 2·3·6·12(이번 달 포함) 밖이면 400 {@code fields.rawRetentionMonths}. 2단계 세 칸(환경·검색어·봇)은 비면 저장된 값 그대로(그 칸을 모르는 화면이 저장해도 꺼지지 않게).
 *       방문 수집 전체를 꺼도 하위 칸 값은 그대로 저장한다(화면과 같다).</li>
 *   <li>같은 값 저장은 아무것도 남기지 않는다. 바뀐 칸만 같은 트랜잭션에서 이력 한 줄({@code UPDATE} · {@code ADMIN}) + 감사 {@code STATS_COLLECTION_UPDATE}({@code before}/{@code after}).</li>
 *   <li>저장 뒤 이 서버의 설정 사본을 버린다 — 다른 서버(공개 사이트 포함)는 최대 30초 늦게 따른다({@link StatsCollectionSettings}).</li>
 *   <li>원본 기록이 동작하게 되면 커밋 뒤 이번 달·다음 달 원본 조각을 만든다(크론이 꺼진 동안 조각을 만들지 않았을 수 있어서 — 실패해도 저장은 그대로, 쓰는 쪽이 한 번 더 만든다).</li>
 *   <li>이력 조회는 메일 서버·광고 자리 설정 이력과 같은 쪽 규칙(기본 20 · 최대 100 · 틀리면 400 {@code fields.page}·{@code fields.size}).</li>
 * </ul>
 */
@Service
public class AnalyticsSettingsAdminService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsSettingsAdminService.class);

    static final String AUDIT_TARGET = "STATS_COLLECTION";
    static final int HISTORY_MAX_SIZE = 100;
    static final int HISTORY_DEFAULT_SIZE = 20;
    /** 칸 차례 — 이력·감사가 이 차례로 남는다 */
    static final List<String> FIELDS = List.of("enabled", "visits", "rawEvents", "rawRetentionMonths", "referrers", "flows", "environment", "searchTerms", "bots",
            "notificationSends", "boardActivity");
    private static final String LABEL_KEY_PREFIX = "settingHistory.field.";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final StatsCollectionStore store;
    private final CacheRefresher refresher;
    private final AnalyticsDatabase analytics;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;
    private final Messages messages;

    public AnalyticsSettingsAdminService(StatsCollectionStore store, CacheRefresher refresher, AnalyticsDatabase analytics, AuditLogService audit,
                                         SnowflakeIdGenerator ids, Messages messages) {
        this.store = store;
        this.refresher = refresher;
        this.analytics = analytics;
        this.audit = audit;
        this.ids = ids;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public AnalyticsSettingsAdmin.View get() {
        return view(store.find().orElse(null));
    }

    @Transactional
    public AnalyticsSettingsAdmin.View save(AdminActor actor, AnalyticsSettingsAdmin.Save body) {
        StatsCollection checked = validate(body);
        StatsCollectionStore.Stored was = store.lock().orElse(null);
        StatsCollection before = was == null ? StatsCollection.OFF : was.value();
        StatsCollection next = withMore(checked, body, before);
        Map<String, Object> a = flat(before);
        Map<String, Object> b = flat(next);
        Map<String, Object> from = new LinkedHashMap<>();
        Map<String, Object> to = new LinkedHashMap<>();
        List<AnalyticsSettingsAdmin.Change> changes = new ArrayList<>();
        for (String key : FIELDS) {
            if (!Objects.equals(a.get(key), b.get(key))) {
                from.put(key, a.get(key));
                to.put(key, b.get(key));
                changes.add(new AnalyticsSettingsAdmin.Change(key, String.valueOf(a.get(key)), String.valueOf(b.get(key))));
            }
        }
        if (changes.isEmpty()) {
            return get();
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS); // DB(timestamptz) 정밀도 — 이력 시각과 저장 시각이 같게
        store.save(next, actor.operatorId(), now);
        store.addHistory(ids.nextId(), actor.operatorId(), now, MAPPER.writeValueAsString(changes));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", from);
        detail.put("after", to);
        audit.record(new AuditEvent(AuditActorType.OPERATOR, actor.operatorId(), AuditActions.STATS_COLLECTION_UPDATE, AuditResult.SUCCESS, AUDIT_TARGET, "1",
                actor.ip(), detail));
        // 모든 서버의 통계 수집 설정 사본을 비운다(세대 번호 — 같은 트랜잭션, 이 서버는 커밋 뒤 바로)
        refresher.onSave(CacheSaveSource.STATS_SAVE, null, actor.operatorId());
        boolean rawTurnedOn = next.rawEventsOn() && !before.rawEventsOn();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (rawTurnedOn) {
                    ensurePartitions();
                }
            }
        });
        return get();
    }

    @Transactional(readOnly = true)
    public AnalyticsSettingsAdmin.HistoryPage history(String page, String size) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        MessageRef range = new MessageRef("analytics.settings.history.page.range", String.valueOf(HISTORY_MAX_SIZE));
        Integer pageNumber = integer(page, 0, 0, Integer.MAX_VALUE / HISTORY_MAX_SIZE);
        if (pageNumber == null) {
            fields.put("page", range);
        }
        Integer pageSize = integer(size, HISTORY_DEFAULT_SIZE, 1, HISTORY_MAX_SIZE);
        if (pageSize == null) {
            fields.put("size", range);
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        long total = store.historyCount();
        List<AnalyticsSettingsAdmin.HistoryItem> items = new ArrayList<>();
        if (total > 0 && (long) pageNumber * pageSize < total) {
            String deleted = messages.get(new MessageRef("analytics.settings.history.operatorDeleted"));
            for (StatsCollectionStore.HistoryRow row : store.history(pageNumber, pageSize)) {
                boolean gone = row.operatorName() == null;
                List<AnalyticsSettingsAdmin.ChangeView> changes = parse(row.changesJson()).stream()
                        .map(c -> new AnalyticsSettingsAdmin.ChangeView(c.field(), labelKey(c.field()), c.before(), c.after())).toList();
                items.add(new AnalyticsSettingsAdmin.HistoryItem(Long.toString(row.id()), row.action(), row.source(),
                        row.operatorId() == null ? null : row.operatorId().toString(), gone ? deleted : row.operatorName(), gone, row.changedAt(), changes));
            }
        }
        return new AnalyticsSettingsAdmin.HistoryPage(items, total, pageNumber, pageSize);
    }

    /** 칸 이름 i18n 키 — 방문 수집 전체({@code enabled})는 메일 서버의 {@code enabled} 와 겹치지 않게 {@code analyticsEnabled}(화면 합의) */
    static String labelKey(String field) {
        return LABEL_KEY_PREFIX + ("enabled".equals(field) ? "analyticsEnabled" : field);
    }

    /** 원본 조각 만들기 — 분석 연결로, 트랜잭션 밖에서. 함수가 아직 없거나(공개 사이트 서버가 analytics 스트림을 이관하기 전) 실패해도 경고만 */
    private void ensurePartitions() {
        try {
            analytics.jdbc().queryForObject("SELECT analytics_ensure_page_view_partitions(1)", Integer.class);
        } catch (RuntimeException e) {
            log.warn("방문 원본 조각을 미리 만들지 못했다(쓰는 쪽이 처음 쓸 때 만든다): {}", e.getClass().getSimpleName());
        }
    }

    private static AnalyticsSettingsAdmin.View view(StatsCollectionStore.Stored stored) {
        StatsCollection s = stored == null ? StatsCollection.OFF : stored.value();
        return new AnalyticsSettingsAdmin.View(s.enabled(), s.visits(), s.rawEvents(), s.rawRetentionMonths(), s.referrers(), s.flows(), s.environment(),
                s.searchTerms(), s.bots(), s.notificationSends(), s.boardActivity(), stored == null || stored.updatedAt() == null ? null : stored.updatedAt().toString(), stored == null ? null : stored.updatedByName());
    }

    private static StatsCollection validate(AnalyticsSettingsAdmin.Save body) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        MessageRef required = new MessageRef("analytics.settings.required");
        Boolean[] switches = body == null ? new Boolean[7]
                : new Boolean[] {body.enabled(), body.visits(), body.rawEvents(), body.referrers(), body.flows(), body.notificationSends(), body.boardActivity()};
        String[] names = {"enabled", "visits", "rawEvents", "referrers", "flows", "notificationSends", "boardActivity"};
        for (int i = 0; i < names.length; i++) {
            if (switches[i] == null) {
                fields.put(names[i], required);
            }
        }
        Integer months = body == null ? null : body.rawRetentionMonths();
        if (months == null || !StatsCollection.RETENTION_MONTHS.contains(months)) {
            fields.put("rawRetentionMonths", new MessageRef("analytics.settings.retention"));
        }
        if (!fields.isEmpty()) {
            // 칸 차례(FIELDS)로 다시 담는다 — 대표 문구가 화면 차례의 첫 칸이 되게
            Map<String, MessageRef> ordered = new LinkedHashMap<>();
            FIELDS.stream().filter(fields::containsKey).forEach(k -> ordered.put(k, fields.get(k)));
            throw new ValidationException(ordered);
        }
        return new StatsCollection(body.enabled(), body.visits(), body.rawEvents(), months, body.referrers(), body.flows(), body.notificationSends(),
                body.boardActivity());
    }

    /** 2단계 세 칸 — 본문에 없으면 저장된 값 그대로 */
    private static StatsCollection withMore(StatsCollection checked, AnalyticsSettingsAdmin.Save body, StatsCollection before) {
        boolean environment = body.environment() == null ? before.environment() : body.environment();
        boolean searchTerms = body.searchTerms() == null ? before.searchTerms() : body.searchTerms();
        boolean bots = body.bots() == null ? before.bots() : body.bots();
        return new StatsCollection(checked.enabled(), checked.visits(), checked.rawEvents(), checked.rawRetentionMonths(), checked.referrers(), checked.flows(),
                environment, searchTerms, bots, checked.notificationSends(), checked.boardActivity());
    }

    private static Map<String, Object> flat(StatsCollection s) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", s.enabled());
        out.put("visits", s.visits());
        out.put("rawEvents", s.rawEvents());
        out.put("rawRetentionMonths", s.rawRetentionMonths());
        out.put("referrers", s.referrers());
        out.put("flows", s.flows());
        out.put("environment", s.environment());
        out.put("searchTerms", s.searchTerms());
        out.put("bots", s.bots());
        out.put("notificationSends", s.notificationSends());
        out.put("boardActivity", s.boardActivity());
        return out;
    }

    private static List<AnalyticsSettingsAdmin.Change> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(MAPPER.readValue(json, AnalyticsSettingsAdmin.Change[].class));
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** 비었으면 기본값, 정수가 아니거나 범위 밖이면 null(메일 서버 설정 이력과 같은 규칙) */
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
