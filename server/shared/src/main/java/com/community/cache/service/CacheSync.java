package com.community.cache.service;

import com.community.cache.definition.CacheCatalog;
import com.community.cache.definition.CacheDefinition;
import com.community.cache.definition.CacheServerRole;
import com.community.cache.repository.master.CacheStateStore;
import com.community.cache.repository.master.CacheStateStore.GlobalRow;
import com.community.cache.repository.master.CacheStateStore.NodeStateRow;
import com.community.cache.repository.master.CacheStateStore.SettingRow;
import com.community.zconfiguration.cache.CacheControl;
import com.community.zconfiguration.cache.CacheProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 캐시 동기화 — api·admin 서버마다 돈다(docs/features/cache-admin-contract.md 「정책」).
 *
 * <ul>
 *   <li>{@code roboyam.cache.poll-seconds}(기본 3초)마다 설정을 읽는다 — 질의 두 개({@code cache_settings} 전부 · 전체 스위치 1행). 서버 2~3대면 초당 질의 2개 남짓이다.
 *       바뀐 세대 번호면 그 캐시를 비우고, TTL 이 바뀌었으면 적용하고(비우지 않는다 — 세션 정리 결정 3), 실제 켜짐(전체 AND 캐시)이 바뀌었으면 따라간다(끄면 비운다).
 *       행이 없는 캐시는 정의의 기본값(TTL 기본값 · 켜짐 · 세대 0)이다.</li>
 *   <li>상태(반영 세대·반영 켜짐·마지막 비움·마지막 확인·항목 수·적중·누락)는 {@code roboyam.cache.report-seconds}(기본 10초)마다, 그리고 바뀐 것을 반영한 직후 DB 에 한 묶음으로
 *       덮어쓴다(관리자 서버는 다른 서버의 메모리를 볼 수 없다). 한 시간 넘게 보고가 없는 줄은 가끔 지운다(화면은 1분 넘게 보고가 없는 서버를 뺀다).</li>
 *   <li>처음 읽을 때는 비우지 않고 지금 세대를 반영한 것으로 둔다(띄운 직후는 비어 있다 — 「마지막 비움」 없음).</li>
 *   <li>DB 를 읽지 못하면 지금 상태 그대로 두고 다음 주기에 다시 읽는다.</li>
 * </ul>
 */
@Component
public class CacheSync {

    private static final Logger log = LoggerFactory.getLogger(CacheSync.class);
    private static final Duration STALE_ROWS = Duration.ofHours(1);
    private static final int CLEANUP_EVERY_REPORTS = 60;

    private final CacheControl control;
    private final CacheStateStore store;
    private final CacheProperties properties;
    private final TransactionTemplate transactions;
    private final CacheServerRole role;
    private final String nodeId;
    private final Map<String, Applied> applied = new ConcurrentHashMap<>();
    private volatile Instant lastCheckedAt;
    private Instant lastReportedAt = Instant.EPOCH;
    private int reports;

    /** 이 서버가 따라간 상태 */
    private record Applied(long generation, boolean enabled, int ttlSeconds, Instant lastClearedAt) {
    }

    public CacheSync(CacheControl control, CacheStateStore store, CacheProperties properties, PlatformTransactionManager transactionManager,
                     @Value("${roboyam.schema.stream:api}") String stream, @Value("${roboyam.node-id:0}") int nodeNumber) {
        this.control = control;
        this.store = store;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
        this.role = CacheServerRole.ofStream(stream);
        String configured = properties.nodeName();
        this.nodeId = configured == null || configured.isBlank() ? role.lower() + "-" + nodeNumber : configured.strip();
    }

    /** 이 서버의 이름(노드 ID) */
    public String nodeId() {
        return nodeId;
    }

    public CacheServerRole role() {
        return role;
    }

    /** 띄운 직후 곧바로 한 번 맞춘다(첫 주기를 기다리지 않는다) */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        poll();
    }

    @Scheduled(fixedDelayString = "${roboyam.cache.poll-seconds:3}", initialDelayString = "${roboyam.cache.poll-seconds:3}", timeUnit = TimeUnit.SECONDS)
    public void scheduledPoll() {
        poll();
    }

    /** 설정을 읽어 따라가고, 바뀌었거나 보고 주기가 되었으면 보고한다 */
    synchronized void poll() {
        List<SettingRow> rows;
        boolean globalEnabled;
        try {
            rows = store.settings();
            globalEnabled = store.global().map(GlobalRow::enabled).orElse(true);
        } catch (RuntimeException e) {
            log.warn("캐시 설정을 읽지 못해 지금 상태로 둔다: {}", e.getClass().getSimpleName());
            return;
        }
        Map<String, SettingRow> byKey = new HashMap<>();
        rows.forEach(r -> byKey.put(r.cacheKey(), r));
        Instant now = Instant.now();
        lastCheckedAt = now;
        boolean changed = false;
        for (String name : control.localNames()) {
            CacheDefinition def = CacheCatalog.find(name).orElse(null);
            if (def == null) {
                continue;
            }
            SettingRow row = byKey.get(name);
            long generation = row == null ? 0 : row.generation();
            int ttl = row == null || row.ttlSeconds() == null ? def.defaultTtlSeconds() : row.ttlSeconds();
            boolean effective = globalEnabled && (row == null || row.enabled());
            Applied before = applied.get(name);
            if (before == null) {
                control.applyTtl(name, Duration.ofSeconds(ttl));
                control.applyEnabled(name, effective);
                applied.put(name, new Applied(generation, effective, ttl, null));
                changed = true;
                continue;
            }
            Instant cleared = before.lastClearedAt();
            if (before.ttlSeconds() != ttl) {
                control.applyTtl(name, Duration.ofSeconds(ttl));
                changed = true;
            }
            if (before.enabled() != effective) {
                control.applyEnabled(name, effective);
                cleared = now; // 끄면 비운다 · 켜면 빈 채로 다시 찬다
                changed = true;
            }
            if (before.generation() != generation) {
                control.clear(name);
                cleared = now;
                changed = true;
            }
            applied.put(name, new Applied(generation, effective, ttl, cleared));
        }
        if (changed || Duration.between(lastReportedAt, now).compareTo(properties.report()) >= 0) {
            report(now);
        }
    }

    private void report(Instant now) {
        List<NodeStateRow> out = new ArrayList<>();
        for (String name : control.localNames()) {
            Applied a = applied.get(name);
            if (a == null) {
                continue;
            }
            CacheControl.Stats stats = control.stats(name);
            out.add(new NodeStateRow(nodeId, name, role.name(), a.generation(), a.enabled(), a.lastClearedAt(), lastCheckedAt, stats.entryCount(), stats.hitCount(),
                    stats.missCount(), now));
        }
        try {
            store.report(out);
            lastReportedAt = now;
            if (++reports % CLEANUP_EVERY_REPORTS == 0) {
                transactions.executeWithoutResult(status -> store.deleteStale(now.minus(STALE_ROWS)));
            }
        } catch (RuntimeException e) {
            log.warn("캐시 상태를 보고하지 못했다: {}", e.getClass().getSimpleName());
        }
    }
}
