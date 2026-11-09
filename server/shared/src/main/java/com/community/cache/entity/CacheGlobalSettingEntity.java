package com.community.cache.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 전체 캐시 스위치 — shared V175 {@code cache_global_settings}(1행, id = 1, 마이그레이션이 넣는다). 실제 적용 = 전체 켜짐 AND 캐시 켜짐 */
@Entity
@Table(name = "cache_global_settings")
public class CacheGlobalSettingEntity {

    public static final short ROW_ID = 1;

    @Id
    private Short id;

    private boolean enabled;
    private Instant changedAt;
    private Instant updatedAt;
    private Long updatedBy;

    protected CacheGlobalSettingEntity() {
    }

    public static CacheGlobalSettingEntity create(Instant now) {
        CacheGlobalSettingEntity row = new CacheGlobalSettingEntity();
        row.id = ROW_ID;
        row.enabled = true;
        row.updatedAt = now;
        return row;
    }

    public void changeEnabled(boolean value, Long operatorId, Instant now) {
        enabled = value;
        changedAt = now;
        updatedAt = now;
        updatedBy = operatorId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
