package com.community.report.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 신고 한 건 — V46 의 {@code reports}. 같은 회원은 같은 대상을 한 번만(유일 색인). 처리(수용·반려)는 관리자 신고 처리 기능(후속)이 한다.
 *
 * @see com.community.report.entity.ReportReasons
 */
@Entity
@Table(name = "reports")
public class ReportEntity {

    public static final String POST = "POST";
    public static final String COMMENT = "COMMENT";
    public static final String PENDING = "PENDING";
    public static final String REJECTED = "REJECTED";

    @Id
    private Long id;

    private String targetType;
    private Long targetId;
    private Long postId;
    private Long boardId;
    private Long reporterId;
    private Long targetAuthorId;
    private String reason;
    private String detail;
    private String snapshotTitle;
    private String snapshotContent;
    private String snapshotAuthorName;
    private boolean counted;
    private String status;
    private Instant resolvedAt;
    private Long resolvedBy;
    private Instant createdAt;

    protected ReportEntity() {
    }

    public static ReportEntity create(long id, String targetType, long targetId, long postId, long boardId, long reporterId, Long targetAuthorId,
                                      String reason, String detail, String snapshotTitle, String snapshotContent, String snapshotAuthorName, boolean counted,
                                      Instant now) {
        ReportEntity report = new ReportEntity();
        report.id = id;
        report.targetType = targetType;
        report.targetId = targetId;
        report.postId = postId;
        report.boardId = boardId;
        report.reporterId = reporterId;
        report.targetAuthorId = targetAuthorId;
        report.reason = reason;
        report.detail = detail;
        report.snapshotTitle = snapshotTitle;
        report.snapshotContent = snapshotContent;
        report.snapshotAuthorName = snapshotAuthorName;
        report.counted = counted;
        report.status = PENDING;
        report.createdAt = now;
        return report;
    }

    public Long getId() {
        return id;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getReason() {
        return reason;
    }

    public boolean isCounted() {
        return counted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
