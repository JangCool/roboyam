package com.community.board.entity;

import com.community.board.dto.BoardDefaultsValues;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 게시판 기본 설정(행 하나, {@code id = 1}) — V42__boards.sql 의 {@code board_defaults}. 행은 마이그레이션이 만든다 */
@Entity
@Table(name = "board_defaults")
public class BoardDefaultsEntity {

    public static final int ROW_ID = 1;

    @Id
    private Integer id;

    private String dateDisplay;
    private String writeMin;
    private String commentMin;
    private String postEditor;
    private boolean imageUploadAllowed;
    private String commentEditor;
    private int postGapSeconds;
    private int postDailyLimit;
    private int commentGapSeconds;
    private int commentDailyLimit;
    private int listSize;
    private int pinnedMax;
    private boolean indexable;
    private boolean searchable;
    private boolean inHome;
    private boolean attachmentPurgeEnabled;
    private int attachmentRetentionDays;
    private String pointMode;
    private int commentProtectMin;
    private boolean commentProtectEdit;
    private String commentProtectDelete;
    private int commentProtectDelayMinutes;
    private int commentGuestDelayMinutes;
    private boolean staffHiddenNotice;
    private int bestCommentMinLikes;
    private int managerMax;
    private int staffMax;
    private int staffPostDailyLimit;
    private int staffCommentDailyLimit;
    private int staffSanctionDailyLimit;
    private Instant updatedAt;
    private Long updatedBy;

    protected BoardDefaultsEntity() {
    }

    public BoardDefaultsValues values() {
        return new BoardDefaultsValues(dateDisplay, writeMin, commentMin, postEditor, imageUploadAllowed, commentEditor, postGapSeconds, postDailyLimit, commentGapSeconds,
                commentDailyLimit, listSize, pinnedMax, indexable, searchable, inHome, attachmentPurgeEnabled, attachmentRetentionDays, pointMode,
                commentProtectMin, commentProtectEdit, commentProtectDelete, commentProtectDelayMinutes, commentGuestDelayMinutes, staffHiddenNotice, bestCommentMinLikes,
                managerMax, staffMax, staffPostDailyLimit, staffCommentDailyLimit, staffSanctionDailyLimit);
    }

    /** 칸 전부를 바꾼다 — 검사를 마친 값(빈 칸 없음)만 받는다 */
    public void change(BoardDefaultsValues v, long operatorId, Instant now) {
        this.dateDisplay = v.dateDisplay();
        this.writeMin = v.writeMin();
        this.commentMin = v.commentMin();
        this.postEditor = v.postEditor();
        this.imageUploadAllowed = v.imageUploadAllowed();
        this.commentEditor = v.commentEditor();
        this.postGapSeconds = v.postGapSeconds();
        this.postDailyLimit = v.postDailyLimit();
        this.commentGapSeconds = v.commentGapSeconds();
        this.commentDailyLimit = v.commentDailyLimit();
        this.listSize = v.listSize();
        this.pinnedMax = v.pinnedMax();
        this.indexable = v.indexable();
        this.searchable = v.searchable();
        this.inHome = v.inHome();
        this.attachmentPurgeEnabled = v.attachmentPurgeEnabled();
        this.attachmentRetentionDays = v.attachmentRetentionDays();
        this.pointMode = v.pointMode();
        this.commentProtectMin = v.commentProtectMin();
        this.commentProtectEdit = v.commentProtectEdit();
        this.commentProtectDelete = v.commentProtectDelete();
        this.commentProtectDelayMinutes = v.commentProtectDelayMinutes();
        this.commentGuestDelayMinutes = v.commentGuestDelayMinutes();
        this.staffHiddenNotice = v.staffHiddenNotice();
        this.bestCommentMinLikes = v.bestCommentMinLikes();
        this.managerMax = v.managerMax();
        this.staffMax = v.staffMax();
        this.staffPostDailyLimit = v.staffPostDailyLimit();
        this.staffCommentDailyLimit = v.staffCommentDailyLimit();
        this.staffSanctionDailyLimit = v.staffSanctionDailyLimit();
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
