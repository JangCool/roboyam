package com.community.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 신고 사건 — V82 {@code report_cases}. 같은 대상(글·댓글·소모임 — V95)의 신고를 한 줄로 묶는다. 대상마다 열린 사건({@link #PENDING}·{@link #IN_REVIEW})은 하나다(부분 유일 색인).
 * 닫힌 사건 뒤의 새 신고는 새 사건으로 0부터 센다. docs/features/report-admin-contract.md.
 *
 * <p>상태 전이는 이 엔티티의 메서드만 한다: 대기 → 검토 중({@link #startReview}) → 조치({@link #resolve}) 또는 무시({@link #dismiss}).
 * 무시는 {@link #reopen} 으로 대기로 되돌린다. 조치는 되돌리지 못한다.
 */
@Entity
@Table(name = "report_cases")
@DynamicUpdate
public class ReportCaseEntity {

    public static final String PENDING = "PENDING";
    public static final String IN_REVIEW = "IN_REVIEW";
    public static final String RESOLVED = "RESOLVED";
    public static final String DISMISSED = "DISMISSED";
    /** 열린 사건의 상태 */
    public static final List<String> OPEN = List.of(PENDING, IN_REVIEW);

    /** 조치: 운영자 숨김(공개 대상만) */
    public static final String ACTION_HIDE = "HIDE";
    /** 조치: 삭제 */
    public static final String ACTION_DELETE = "DELETE";
    /** 조치: 자동 숨김 확정(권리침해 임시조치면 기한을 지워 계속 숨김) */
    public static final String ACTION_KEEP_HIDDEN = "KEEP_HIDDEN";
    /** 조치: 소모임 비활성화(소모임 사건만 — V95) */
    public static final String ACTION_DEACTIVATE = "DEACTIVATE";
    /** 무시 */
    public static final String ACTION_DISMISS = "DISMISS";

    /** 조치 글 최대 글자(V82 note) */
    public static final int NOTE_MAX = 500;

    @Id
    private Long id;

    /** 운영자가 부르는 짧은 번호 — DB 시퀀스가 매긴다(읽기 전용). 방금 넣은 엔티티에는 비어 있다 */
    @Column(insertable = false, updatable = false)
    private Long caseNo;

    private String targetType;
    private Long targetId;
    /** 대상 글(댓글 사건이면 그 댓글의 글) — 소모임 사건이면 null(V95) */
    private Long postId;
    private Long boardId;
    private Long targetAuthorId;
    private String status;
    private int reportCount;
    private Instant firstReportedAt;
    private Instant lastReportedAt;
    private Long reviewerId;
    private Instant reviewStartedAt;
    private Instant closedAt;
    private Long closedBy;
    private String action;
    private String reasonCode;
    private String note;
    private boolean falseReport;
    private Instant createdAt;
    private Instant updatedAt;

    protected ReportCaseEntity() {
    }

    /** 새 사건(신고 0건 — 이어서 {@link #reportAdded}) */
    public static ReportCaseEntity open(long id, String targetType, long targetId, Long postId, long boardId, Long targetAuthorId, Instant now) {
        ReportCaseEntity c = new ReportCaseEntity();
        c.id = id;
        c.targetType = targetType;
        c.targetId = targetId;
        c.postId = postId;
        c.boardId = boardId;
        c.targetAuthorId = targetAuthorId;
        c.status = PENDING;
        c.firstReportedAt = now;
        c.lastReportedAt = now;
        c.createdAt = now;
        c.updatedAt = now;
        return c;
    }

    /** 신고가 하나 더 붙었다 — 글의 게시판이 옮겨졌을 수 있어 글·게시판도 지금 값으로(소모임 사건은 글 null) */
    public void reportAdded(Long postId, long boardId, Instant now) {
        this.reportCount++;
        this.lastReportedAt = now;
        this.postId = postId;
        this.boardId = boardId;
        this.updatedAt = now;
    }

    /** 처리 권한이 있는 운영자가 상세를 열었다 — 대기였으면 검토 중으로(이미 검토 중이면 처음 연 사람 그대로). 바뀌었으면 true */
    public boolean startReview(long operatorId, Instant now) {
        if (!PENDING.equals(status)) {
            return false;
        }
        this.status = IN_REVIEW;
        this.reviewerId = operatorId;
        this.reviewStartedAt = now;
        this.updatedAt = now;
        return true;
    }

    /** 조치로 닫는다 — {@code action} 은 HIDE · DELETE · KEEP_HIDDEN · DEACTIVATE(소모임) */
    public void resolve(String action, String reasonCode, String note, long operatorId, Instant now) {
        close(RESOLVED, action, reasonCode, note, false, operatorId, now);
    }

    /** 무시로 닫는다 */
    public void dismiss(String reasonCode, String note, boolean falseReport, long operatorId, Instant now) {
        close(DISMISSED, ACTION_DISMISS, reasonCode, note, falseReport, operatorId, now);
    }

    private void close(String to, String action, String reasonCode, String note, boolean falseReport, long operatorId, Instant now) {
        this.status = to;
        this.action = action;
        this.reasonCode = reasonCode;
        this.note = note;
        this.falseReport = falseReport;
        this.closedAt = now;
        this.closedBy = operatorId;
        this.updatedAt = now;
    }

    /** 무시를 되돌린다 — 대기로. 검토자·처리 결과·허위 표시는 비운다 */
    public void reopen(Instant now) {
        this.status = PENDING;
        this.action = null;
        this.reasonCode = null;
        this.note = null;
        this.falseReport = false;
        this.closedAt = null;
        this.closedBy = null;
        this.reviewerId = null;
        this.reviewStartedAt = null;
        this.updatedAt = now;
    }

    /**
     * 신고 대상 단위 잠금 키({@code PostCounterQueries#lockWriter}) — 공개 서버 신고 접수(열린 사건 찾기·열기)와 관리자 「다시 열기」가 같은 키로 줄을 선다.
     * 잠그는 차례는 늘 이 잠금 → 사건 행 → 대상 행이다.
     */
    public static String lockKey(String targetType, long targetId) {
        return "report-target:" + targetType + ":" + targetId;
    }

    public boolean isOpen() {
        return OPEN.contains(status);
    }

    public boolean isComment() {
        return ReportEntity.COMMENT.equals(targetType);
    }

    /** 소모임 사건(V95) — 글이 없다 */
    public boolean isGroup() {
        return ReportEntity.GROUP.equals(targetType);
    }

    public Long getId() {
        return id;
    }

    public Long getCaseNo() {
        return caseNo;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getBoardId() {
        return boardId;
    }

    public Long getTargetAuthorId() {
        return targetAuthorId;
    }

    public String getStatus() {
        return status;
    }

    public int getReportCount() {
        return reportCount;
    }

    public Instant getFirstReportedAt() {
        return firstReportedAt;
    }

    public Instant getLastReportedAt() {
        return lastReportedAt;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public Instant getReviewStartedAt() {
        return reviewStartedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Long getClosedBy() {
        return closedBy;
    }

    public String getAction() {
        return action;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getNote() {
        return note;
    }

    public boolean isFalseReport() {
        return falseReport;
    }
}
