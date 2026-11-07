package com.community.analytics.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 통계 수집 설정 변경 이력 한 줄 — shared V133 {@code stats_collection_setting_history}. 값이 실제로 바뀐 저장마다 하나이고 고치거나 지우지 않는다.
 * 바뀐 칸은 JSON 배열 글자({@code changes}). action 은 지금 {@code UPDATE}, source 는 {@code ADMIN} 만.
 */
@Entity
@Table(name = "stats_collection_setting_history")
public class StatsCollectionSettingHistoryEntity {

    public static final String ACTION_UPDATE = "UPDATE";
    public static final String SOURCE_ADMIN = "ADMIN";

    @Id
    private Long id;

    private String action;
    private String source;
    /** 바꾼 운영자(FK 없음 — 지워져도 남는다) */
    private Long operatorId;
    private Instant changedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String changes;

    protected StatsCollectionSettingHistoryEntity() {
    }

    public static StatsCollectionSettingHistoryEntity update(long id, long operatorId, Instant changedAt, String changesJson) {
        StatsCollectionSettingHistoryEntity row = new StatsCollectionSettingHistoryEntity();
        row.id = id;
        row.action = ACTION_UPDATE;
        row.source = SOURCE_ADMIN;
        row.operatorId = operatorId;
        row.changedAt = changedAt;
        row.changes = changesJson;
        return row;
    }
}
