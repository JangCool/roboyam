package com.community.cache.service;

import com.community.cache.definition.CacheCatalog;
import com.community.cache.entity.CacheRefreshLogEntity;
import com.community.cache.entity.CacheSettingEntity;
import com.community.cache.repository.master.CacheRefreshLogRepository;
import com.community.cache.repository.master.CacheSettingRepository;
import com.community.zconfiguration.cache.CacheControl;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 캐시 갱신(비우기) — DB 의 캐시별 세대 번호를 올리고 갱신 기록을 남긴다. 각 서버가 몇 초 안에 따라가 자기 캐시를 비운다({@link CacheSync}).
 *
 * <ul>
 *   <li>부르는 쪽의 트랜잭션에 참여한다 — 관리자 저장이 롤백되면 세대 번호·기록도 없다. 커밋된 뒤에는 이 서버의 캐시를 곧바로 비운다(예전 사본의 {@code invalidate()} 와 같은 효과).</li>
 *   <li>세대 번호는 행 쓰기 잠금으로 올린다(동시에 저장해도 번호가 겹치지 않는다 — 겹치면 그 사이에 확인한 서버가 두 번째 저장을 놓친다). 여러 캐시는 키 차례로 잠근다.</li>
 *   <li>자동(저장)({@link #onSave})은 트랜잭션 안이면 <b>커밋 직전</b>에 한꺼번에 올린다 — 캐시 행 잠금이 늘 업무 잠금 뒤·키 차례라 저장끼리 엇갈려 막히지 않고,
 *       한 저장에서 같은 캐시는 한 번만 오른다(여러 줄을 한꺼번에 고쳐도 기록이 줄 수만큼 쌓이지 않는다).</li>
 *   <li>꺼진 캐시(전체 또는 캐시)도 자동(저장)은 세대 번호를 올린다 — 비울 것이 없지만 켰을 때 혼동이 없게 기록을 남긴다. 「지금 갱신」의 꺼짐 거절은 관리 API 가 한다.</li>
 * </ul>
 */
@Service
public class CacheRefresher {

    private final CacheSettingRepository settings;
    private final CacheRefreshLogRepository logs;
    private final SnowflakeIdGenerator ids;
    private final CacheControl control;

    public CacheRefresher(CacheSettingRepository settings, CacheRefreshLogRepository logs, SnowflakeIdGenerator ids, CacheControl control) {
        this.settings = settings;
        this.logs = logs;
        this.ids = ids;
        this.control = control;
    }

    /**
     * 관리자 화면에서 관련 데이터를 저장했다 — 그 계기의 캐시들을 비운다(기록 「자동(저장)」). 트랜잭션 안이면 커밋 직전에 올린다.
     *
     * @param detail     대상 이름(게시판 이름 등 — 없으면 null)
     * @param operatorId 저장한 운영자(없으면 null)
     */
    @Transactional
    public void onSave(CacheSaveSource source, String detail, Long operatorId) {
        Origin origin = new Origin(source.name(), detail, operatorId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            Map<String, Origin> keys = new TreeMap<>();
            source.keys().forEach(key -> keys.put(key, origin));
            clearHereAfterCommit(apply(keys, CacheRefreshLogEntity.AUTO_SAVE, false, Instant.now()).keySet());
            return;
        }
        pending().add(source, origin);
    }

    /**
     * 세대 번호를 지금 올린다(지금 갱신·전체 갱신) — 캐시 키 → 올린 뒤 번호(키 차례). 부르는 쪽의 트랜잭션에서 돈다.
     *
     * @param trigger {@link CacheRefreshLogEntity#MANUAL} · {@link CacheRefreshLogEntity#AUTO_SAVE}
     * @param all     「전체 갱신」으로 함께 비웠는가
     */
    @Transactional
    public Map<String, Long> bump(Collection<String> keys, String trigger, boolean all, String sourceCode, String detail, Long operatorId, Instant now) {
        Origin origin = new Origin(sourceCode, detail, operatorId);
        Map<String, Origin> sorted = new TreeMap<>();
        keys.forEach(key -> sorted.put(key, origin));
        Map<String, Long> out = apply(sorted, trigger, all, now);
        clearHereAfterCommit(out.keySet());
        return out;
    }

    /** 키 차례로 잠그고 올린다(카탈로그에 없는 키는 건너뛴다) */
    private Map<String, Long> apply(Map<String, Origin> sortedKeys, String trigger, boolean all, Instant now) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, Origin> e : sortedKeys.entrySet()) {
            String key = e.getKey();
            if (CacheCatalog.find(key).isEmpty()) {
                continue;
            }
            Origin o = e.getValue();
            CacheSettingEntity row = settings.findWithLockByCacheKey(key).orElseGet(() -> {
                CacheSettingEntity created = CacheSettingEntity.defaults(key, now);
                settings.persist(created);
                return created;
            });
            long generation = row.bump(o.operatorId(), now);
            logs.persist(CacheRefreshLogEntity.create(ids.nextId(), key, trigger, all, o.sourceCode(), o.detail(), o.operatorId(), generation, now));
            out.put(key, generation);
        }
        return out;
    }

    /** 지금 트랜잭션의 모아 둔 자동(저장) — 없으면 만들어 건다 */
    private Pending pending() {
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            if (s instanceof Pending p) {
                return p;
            }
        }
        Pending p = new Pending();
        TransactionSynchronizationManager.registerSynchronization(p);
        return p;
    }

    /** 커밋된 뒤 이 서버의 캐시를 곧바로 비운다(다른 서버는 세대 번호로 몇 초 안에 따라온다). 트랜잭션 밖이면 바로 */
    private void clearHereAfterCommit(Collection<String> keys) {
        List<String> names = List.copyOf(keys);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    names.forEach(control::clear);
                }
            });
        } else {
            names.forEach(control::clear);
        }
    }

    /** 기록에 남길 계기 */
    private record Origin(String sourceCode, String detail, Long operatorId) {
    }

    /** 한 트랜잭션에서 모은 자동(저장) — 캐시 키마다 처음 계기가 기록된다. 커밋 직전에 한꺼번에 올리고, 커밋 뒤 이 서버를 비운다 */
    private final class Pending implements TransactionSynchronization {

        private final Map<String, Origin> keys = new TreeMap<>();
        private List<String> bumped = List.of();

        void add(CacheSaveSource source, Origin origin) {
            source.keys().forEach(key -> keys.putIfAbsent(key, origin));
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            bumped = List.copyOf(apply(keys, CacheRefreshLogEntity.AUTO_SAVE, false, Instant.now()).keySet());
        }

        @Override
        public void afterCommit() {
            bumped.forEach(control::clear);
        }
    }
}
