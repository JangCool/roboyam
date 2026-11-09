package com.community.cache.service;

import com.community.cache.definition.CacheCatalog;
import com.community.cache.entity.CacheRefreshLogEntity;
import com.community.cache.entity.CacheSettingEntity;
import com.community.cache.repository.master.CacheRefreshLogRepository;
import com.community.cache.repository.master.CacheSettingRepository;
import com.community.zconfiguration.cache.CacheControl;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
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
     * 관리자 화면에서 관련 데이터를 저장했다 — 그 계기의 캐시들을 비운다(기록 「자동(저장)」).
     *
     * @param detail     대상 이름(게시판 이름 등 — 없으면 null)
     * @param operatorId 저장한 운영자(없으면 null)
     */
    @Transactional
    public void onSave(CacheSaveSource source, String detail, Long operatorId) {
        bump(source.keys(), CacheRefreshLogEntity.AUTO_SAVE, false, source.name(), detail, operatorId, Instant.now());
    }

    /**
     * 세대 번호를 올린다 — 캐시 키 → 올린 뒤 번호(키 차례). 부르는 쪽의 트랜잭션에서 돈다.
     *
     * @param trigger {@link CacheRefreshLogEntity#MANUAL} · {@link CacheRefreshLogEntity#AUTO_SAVE}
     * @param all     「전체 갱신」으로 함께 비웠는가
     */
    @Transactional
    public Map<String, Long> bump(Collection<String> keys, String trigger, boolean all, String sourceCode, String detail, Long operatorId, Instant now) {
        Map<String, Long> out = new LinkedHashMap<>();
        List<String> sorted = new ArrayList<>(new TreeSet<>(keys));
        for (String key : sorted) {
            if (CacheCatalog.find(key).isEmpty()) {
                continue;
            }
            CacheSettingEntity row = settings.findWithLockByCacheKey(key).orElseGet(() -> {
                CacheSettingEntity created = CacheSettingEntity.defaults(key, now);
                settings.persist(created);
                return created;
            });
            long generation = row.bump(operatorId, now);
            logs.persist(CacheRefreshLogEntity.create(ids.nextId(), key, trigger, all, sourceCode, detail, operatorId, generation, now));
            out.put(key, generation);
        }
        clearHereAfterCommit(out.keySet());
        return out;
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
}
