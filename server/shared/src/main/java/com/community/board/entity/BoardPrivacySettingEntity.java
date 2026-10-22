package com.community.board.entity;

import com.community.board.dto.BoardPrivacyValues;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 게시판 기본 설정 「개인정보 보유기간」(행 하나) — V42__boards.sql 의 {@code board_privacy_settings}. DB 배치 {@code board-privacy-retention}
 * (R__batch_18)이 이 행을 읽어 적용하고 {@code last_run_at} 을 채운다.
 */
@Entity
@Table(name = "board_privacy_settings")
public class BoardPrivacySettingEntity {

    public static final int ROW_ID = 1;

    @Id
    private Integer id;

    private boolean retentionEnabled;
    private int ipRetentionDays;
    private int guestRetentionDays;
    private String guestMode;
    private int deletedContentRetentionDays;
    private int reportRecordRetentionDays;
    private int staffActionRetentionDays;
    private Instant lastRunAt;
    private Instant updatedAt;
    private Long updatedBy;

    protected BoardPrivacySettingEntity() {
    }

    public BoardPrivacyValues values() {
        return new BoardPrivacyValues(retentionEnabled, ipRetentionDays, guestRetentionDays, guestMode, deletedContentRetentionDays, reportRecordRetentionDays,
                staffActionRetentionDays);
    }

    public void change(BoardPrivacyValues v, long operatorId, Instant now) {
        this.retentionEnabled = v.retentionEnabled();
        this.ipRetentionDays = v.ipRetentionDays();
        this.guestRetentionDays = v.guestRetentionDays();
        this.guestMode = v.guestMode();
        this.deletedContentRetentionDays = v.deletedContentRetentionDays();
        this.reportRecordRetentionDays = v.reportRecordRetentionDays();
        this.staffActionRetentionDays = v.staffActionRetentionDays();
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    /** 「지금 실행」을 마쳤다 */
    public void markRun(Instant now) {
        this.lastRunAt = now;
    }

    public boolean isRetentionEnabled() {
        return retentionEnabled;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
