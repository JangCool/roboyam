package com.community.report.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 신고 사건 기록 한 줄 — V82 {@code report_events}. 쌓기만 한다(관리자 신고 관리 서랍의 「처리 기록」).
 * 신고({@link #REPORTED})는 {@link #BY_MEMBER} 이지만 신고자 ID 는 남기지 않는다 — 신고자는 보유기간 배치가 지우는 {@code reports} 에만 두고 덧붙임에는 {@code reportId} 만.
 */
@Entity
@Table(name = "report_events")
public class ReportEventEntity {

    public static final String REPORTED = "REPORTED";
    public static final String AUTO_HIDDEN = "AUTO_HIDDEN";
    public static final String REVIEW_STARTED = "REVIEW_STARTED";
    public static final String RESOLVED = "RESOLVED";
    public static final String DISMISSED = "DISMISSED";
    public static final String REOPENED = "REOPENED";

    public static final String BY_OPERATOR = "OPERATOR";
    public static final String BY_SYSTEM = "SYSTEM";
    public static final String BY_MEMBER = "MEMBER";

    @Id
    private Long id;

    private Long caseId;
    private String type;
    private String actorKind;
    private Long actorId;
    @JdbcTypeCode(SqlTypes.JSON)
    private String detail;
    private Instant createdAt;

    protected ReportEventEntity() {
    }

    /**
     * @param actorId    운영자 ID — {@link #BY_OPERATOR} 만, 그 밖은 null 로 남긴다
     * @param detailJson 덧붙임 JSON(없으면 null)
     */
    public static ReportEventEntity create(long id, long caseId, String type, String actorKind, Long actorId, String detailJson, Instant now) {
        ReportEventEntity row = new ReportEventEntity();
        row.id = id;
        row.caseId = caseId;
        row.type = type;
        row.actorKind = actorKind;
        row.actorId = BY_OPERATOR.equals(actorKind) ? actorId : null;
        row.detail = detailJson;
        row.createdAt = now;
        return row;
    }

    public Long getId() {
        return id;
    }

    public Long getCaseId() {
        return caseId;
    }

    public String getType() {
        return type;
    }

    public String getActorKind() {
        return actorKind;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
