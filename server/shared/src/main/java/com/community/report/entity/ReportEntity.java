package com.community.report.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 신고 한 건 — V46 의 {@code reports}. 같은 회원은 같은 대상을 한 번만(유일 색인). 대상의 열린 사건({@link ReportCaseEntity}, V82 {@code case_id})에 묶인다.
 * 상태는 사건을 닫을 때 함께 바뀐다(관리자 신고 관리): 조치 {@link #ACCEPTED} · 허위 표시 무시 {@link #REJECTED}(신고 남용 차단이 센다) · 그냥 무시 {@link #DISMISSED} · 다시 열기 {@link #PENDING}.
 *
 * @see com.community.report.entity.ReportReasons
 */
@Entity
@Table(name = "reports")
public class ReportEntity {

    public static final String POST = "POST";
    public static final String COMMENT = "COMMENT";
    /** 소모임(V95) — {@code postId} 는 비고 {@code boardId} 는 그 소모임의 게시판, {@code targetAuthorId} 는 신고 당시 모임장 */
    public static final String GROUP = "GROUP";
    public static final String PENDING = "PENDING";
    /** 허위 신고로 표시한 무시 — 신고 남용 차단(반려 제한)이 센다 */
    public static final String REJECTED = "REJECTED";
    /** 수용(조치됨) */
    public static final String ACCEPTED = "ACCEPTED";
    /** 그냥 무시(위반 아님) — 반려 제한이 세지 않는다(V82) */
    public static final String DISMISSED = "DISMISSED";

    @Id
    private Long id;

    /** 묶인 사건(V82) — 옛 처리된 신고는 null */
    private Long caseId;
    private String targetType;
    private Long targetId;
    /** 대상 글(댓글 신고면 그 댓글의 글) — 소모임 신고면 null(V95) */
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

    public static ReportEntity create(long id, long caseId, String targetType, long targetId, Long postId, long boardId, long reporterId, Long targetAuthorId,
                                      String reason, String detail, String snapshotTitle, String snapshotContent, String snapshotAuthorName, boolean counted,
                                      Instant now) {
        ReportEntity report = new ReportEntity();
        report.id = id;
        report.caseId = caseId;
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

    public Long getCaseId() {
        return caseId;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public Long getTargetAuthorId() {
        return targetAuthorId;
    }

    public String getReason() {
        return reason;
    }

    /** 신고자가 적은 설명(없으면 null) */
    public String getDetail() {
        return detail;
    }

    public String getSnapshotTitle() {
        return snapshotTitle;
    }

    public String getSnapshotContent() {
        return snapshotContent;
    }

    public String getSnapshotAuthorName() {
        return snapshotAuthorName;
    }

    public String getStatus() {
        return status;
    }

    public boolean isCounted() {
        return counted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
