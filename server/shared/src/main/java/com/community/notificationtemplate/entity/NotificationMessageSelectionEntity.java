package com.community.notificationtemplate.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 대상 × 채널의 「언어별 쓸 문구」와 특수 상황 규칙 — V57 의 {@code notification_message_selections}. 행이 없으면 언어별 기본 문구를 쓰고 규칙이 없다.
 * 규칙은 JSON 배열 글자 {@code [{id, key, value, messageId}]}(차례 그대로 — 위에서부터 맞는 첫 규칙)이다.
 */
@Entity
@Table(name = "notification_message_selections")
@IdClass(NotificationMessageSelectionId.class)
public class NotificationMessageSelectionEntity {

    @Id
    private String targetType;

    @Id
    private String targetCode;

    @Id
    private String channel;

    /** 문구 id — 빈 글자면 그 언어의 기본 문구 */
    private String selectedKo;
    private String selectedEn;

    @JdbcTypeCode(SqlTypes.JSON)
    private String rules;

    private Long updatedBy;
    private Instant updatedAt;

    protected NotificationMessageSelectionEntity() {
    }

    public static NotificationMessageSelectionEntity create(String targetType, String targetCode, String channel, String selectedKo, String selectedEn, String rulesJson,
                                                            long operatorId, Instant now) {
        NotificationMessageSelectionEntity row = new NotificationMessageSelectionEntity();
        row.targetType = targetType;
        row.targetCode = targetCode;
        row.channel = channel;
        row.change(selectedKo, selectedEn, rulesJson, operatorId, now);
        return row;
    }

    public void change(String selectedKo, String selectedEn, String rulesJson, long operatorId, Instant now) {
        this.selectedKo = selectedKo == null ? "" : selectedKo;
        this.selectedEn = selectedEn == null ? "" : selectedEn;
        this.rules = rulesJson == null ? "[]" : rulesJson;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetCode() {
        return targetCode;
    }

    public String getChannel() {
        return channel;
    }

    public String getSelectedKo() {
        return selectedKo;
    }

    public String getSelectedEn() {
        return selectedEn;
    }

    public String getRules() {
        return rules;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
