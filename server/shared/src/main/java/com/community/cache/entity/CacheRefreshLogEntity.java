package com.community.cache.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 갱신 기록 한 줄 — shared V175 {@code cache_refresh_logs}. 계기 MANUAL(지금 갱신·전체 갱신) · AUTO_SAVE(관리자 화면에서 관련 데이터 저장).
 * {@code sourceCode} 는 자동일 때 무엇을 저장해서인지(메시지 {@code cache.source.<코드>}), {@code sourceDetail} 은 그 대상 이름(없으면 null).
 */
@Entity
@Table(name = "cache_refresh_logs")
public class CacheRefreshLogEntity {

    public static final String MANUAL = "MANUAL";
    public static final String AUTO_SAVE = "AUTO_SAVE";

    @Id
    private Long id;

    private String cacheKey;
    private String triggerKind;
    private boolean allCaches;
    private String sourceCode;
    private String sourceDetail;
    private Long operatorId;
    private long generation;
    private Instant requestedAt;

    protected CacheRefreshLogEntity() {
    }

    public static CacheRefreshLogEntity create(long id, String cacheKey, String triggerKind, boolean allCaches, String sourceCode, String sourceDetail, Long operatorId,
                                               long generation, Instant now) {
        CacheRefreshLogEntity row = new CacheRefreshLogEntity();
        row.id = id;
        row.cacheKey = cacheKey;
        row.triggerKind = triggerKind;
        row.allCaches = allCaches;
        row.sourceCode = sourceCode;
        row.sourceDetail = sourceDetail == null || sourceDetail.isBlank() ? null : sourceDetail.length() > 200 ? sourceDetail.substring(0, 200) : sourceDetail;
        row.operatorId = operatorId;
        row.generation = generation;
        row.requestedAt = now;
        return row;
    }
}
