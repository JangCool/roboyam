package com.community.cache.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 캐시별 설정 — shared V175 {@code cache_settings}. 행이 없으면 정의의 기본값(TTL 기본값 · 켜짐 · 세대 0)이다.
 * {@code ttlSeconds} null = 정의의 기본 TTL. {@code generation} 은 갱신마다 1 오르고, 각 서버가 몇 초마다 읽어 바뀌었으면 자기 캐시를 비운다.
 */
@Entity
@Table(name = "cache_settings")
public class CacheSettingEntity {

    @Id
    private String cacheKey;

    private Integer ttlSeconds;
    private boolean enabled;
    private long generation;
    /** 마지막 갱신 요청·켜기/끄기 시각 — 「갱신 대기」가 시작된 시각 */
    private Instant changedAt;
    private Instant updatedAt;
    private Long updatedBy;

    protected CacheSettingEntity() {
    }

    /** 정의의 기본값으로 새 행 */
    public static CacheSettingEntity defaults(String cacheKey, Instant now) {
        CacheSettingEntity row = new CacheSettingEntity();
        row.cacheKey = cacheKey;
        row.enabled = true;
        row.generation = 0;
        row.updatedAt = now;
        return row;
    }

    /** 세대 번호를 올린다 — 올린 뒤 번호 */
    public long bump(Long operatorId, Instant now) {
        generation += 1;
        changedAt = now;
        updatedAt = now;
        updatedBy = operatorId;
        return generation;
    }

    public void changeTtl(int seconds, Long operatorId, Instant now) {
        ttlSeconds = seconds;
        updatedAt = now;
        updatedBy = operatorId;
    }

    public void changeEnabled(boolean value, Long operatorId, Instant now) {
        enabled = value;
        changedAt = now;
        updatedAt = now;
        updatedBy = operatorId;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public Integer getTtlSeconds() {
        return ttlSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getGeneration() {
        return generation;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
