package com.community.cache.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 설정 변경 이력 한 줄 — shared V175 {@code cache_setting_changes}. 종류 TTL · ENABLED · GLOBAL_ENABLED(전체 — {@code cacheKey} null) */
@Entity
@Table(name = "cache_setting_changes")
public class CacheSettingChangeEntity {

    public static final String TTL = "TTL";
    public static final String ENABLED = "ENABLED";
    public static final String GLOBAL_ENABLED = "GLOBAL_ENABLED";

    @Id
    private Long id;

    private String cacheKey;
    private String kind;
    private Integer fromSeconds;
    private Integer toSeconds;
    private Boolean enabled;
    private Long operatorId;
    private Instant changedAt;

    protected CacheSettingChangeEntity() {
    }

    public static CacheSettingChangeEntity ttl(long id, String cacheKey, int from, int to, Long operatorId, Instant now) {
        CacheSettingChangeEntity row = base(id, cacheKey, TTL, operatorId, now);
        row.fromSeconds = from;
        row.toSeconds = to;
        return row;
    }

    public static CacheSettingChangeEntity enabled(long id, String cacheKey, boolean value, Long operatorId, Instant now) {
        CacheSettingChangeEntity row = base(id, cacheKey, cacheKey == null ? GLOBAL_ENABLED : ENABLED, operatorId, now);
        row.enabled = value;
        return row;
    }

    private static CacheSettingChangeEntity base(long id, String cacheKey, String kind, Long operatorId, Instant now) {
        CacheSettingChangeEntity row = new CacheSettingChangeEntity();
        row.id = id;
        row.cacheKey = cacheKey;
        row.kind = kind;
        row.operatorId = operatorId;
        row.changedAt = now;
        return row;
    }
}
