package com.community.highlight.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 게시판 추천 글 설정 — V273__highlight_board_settings.sql 의 {@code highlight_board_settings}(게시판마다 행 하나, docs/features/board-recommend-contract.md 「규칙」).
 * 행이 없으면 「자동 채우기」 켬({@link #DEFAULT_AUTO_FILL}) — 관리자 하이라이트 게시판 곳 화면에서 스위치를 바꾼 게시판만 행이 생긴다. 공개 서버는 추천 글을 채울 때 읽는다.
 */
@Entity
@Table(name = "highlight_board_settings")
public class HighlightBoardSettingEntity {

    /** 행이 없을 때의 값 */
    public static final boolean DEFAULT_AUTO_FILL = true;

    @Id
    private Long boardId;

    private boolean autoFill;
    private Long updatedBy;
    private Instant updatedAt;

    protected HighlightBoardSettingEntity() {
    }

    public static HighlightBoardSettingEntity create(long boardId, boolean autoFill, long operatorId, Instant now) {
        HighlightBoardSettingEntity row = new HighlightBoardSettingEntity();
        row.boardId = boardId;
        row.autoFill = autoFill;
        row.updatedBy = operatorId;
        row.updatedAt = now;
        return row;
    }

    public void changeAutoFill(boolean autoFill, long operatorId, Instant now) {
        this.autoFill = autoFill;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public Long getBoardId() {
        return boardId;
    }

    /** 고른 글이 모자라면 그 게시판의 추천 많은 글로 채우는가 */
    public boolean isAutoFill() {
        return autoFill;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
