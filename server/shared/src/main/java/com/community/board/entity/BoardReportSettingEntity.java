package com.community.board.entity;

import com.community.board.dto.BoardReportValues;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 게시판 기본 설정 「신고 정책」(행 하나) — V42__boards.sql 의 {@code board_report_settings}. 이번에는 저장만 하고 적용(신고 처리)은 후속이다 */
@Entity
@Table(name = "board_report_settings")
public class BoardReportSettingEntity {

    public static final int ROW_ID = 1;

    @Id
    private Integer id;

    private boolean reportEnabled;
    private int autoHideThreshold;
    private String autoHideTarget;
    private String autoHideMinGrade;
    private boolean autoHideExemptStaffPosts;
    private int rightsTempMeasureDays;
    private int dailyReportLimit;
    private int reportCooldownSeconds;
    private int rejectionLimitCount;
    private int rejectionLimitDays;
    private int rejectionBlockDays;
    private String notifyOperatorScope;
    private boolean notifyAuthorOnAction;
    private boolean notifyReporterOnResult;
    private Instant updatedAt;
    private Long updatedBy;

    protected BoardReportSettingEntity() {
    }

    public BoardReportValues values() {
        return new BoardReportValues(reportEnabled, autoHideThreshold, autoHideTarget, autoHideMinGrade, autoHideExemptStaffPosts, rightsTempMeasureDays,
                dailyReportLimit, reportCooldownSeconds, rejectionLimitCount, rejectionLimitDays, rejectionBlockDays, notifyOperatorScope, notifyAuthorOnAction,
                notifyReporterOnResult);
    }

    public void change(BoardReportValues v, long operatorId, Instant now) {
        this.reportEnabled = v.reportEnabled();
        this.autoHideThreshold = v.autoHideThreshold();
        this.autoHideTarget = v.autoHideTarget();
        this.autoHideMinGrade = v.autoHideMinGrade();
        this.autoHideExemptStaffPosts = v.autoHideExemptStaffPosts();
        this.rightsTempMeasureDays = v.rightsTempMeasureDays();
        this.dailyReportLimit = v.dailyReportLimit();
        this.reportCooldownSeconds = v.reportCooldownSeconds();
        this.rejectionLimitCount = v.rejectionLimitCount();
        this.rejectionLimitDays = v.rejectionLimitDays();
        this.rejectionBlockDays = v.rejectionBlockDays();
        this.notifyOperatorScope = v.notifyOperatorScope();
        this.notifyAuthorOnAction = v.notifyAuthorOnAction();
        this.notifyReporterOnResult = v.notifyReporterOnResult();
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
