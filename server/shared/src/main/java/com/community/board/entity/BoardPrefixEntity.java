package com.community.board.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 게시판 말머리 — V42__boards.sql 의 {@code board_prefixes}. 공통 말머리(질문·후기·잡담·공지)는 {@code commonKey} 가 있고 이름·운영진 전용이 고정이라 켜고 끄기만 한다.
 * 고유 말머리는 {@code commonKey} 가 null 이다.
 */
@Entity
@Table(name = "board_prefixes")
public class BoardPrefixEntity {

    @Id
    private Long id;

    private Long boardId;
    private String commonKey;
    private String name;
    private boolean staffOnly;
    private String color;
    private boolean active;
    private int sortOrder;
    private Instant createdAt;
    private Instant updatedAt;

    protected BoardPrefixEntity() {
    }

    public static BoardPrefixEntity common(long id, long boardId, String commonKey, String name, boolean active, int sortOrder, Instant now) {
        BoardPrefixEntity prefix = new BoardPrefixEntity();
        prefix.id = id;
        prefix.boardId = boardId;
        prefix.commonKey = commonKey;
        prefix.name = name;
        prefix.staffOnly = "NOTICE".equals(commonKey);
        prefix.active = active;
        prefix.sortOrder = sortOrder;
        prefix.createdAt = now;
        prefix.updatedAt = now;
        return prefix;
    }

    public static BoardPrefixEntity own(long id, long boardId, String name, boolean staffOnly, String color, boolean active, int sortOrder, Instant now) {
        BoardPrefixEntity prefix = new BoardPrefixEntity();
        prefix.id = id;
        prefix.boardId = boardId;
        prefix.name = name;
        prefix.staffOnly = staffOnly;
        prefix.color = color;
        prefix.active = active;
        prefix.sortOrder = sortOrder;
        prefix.createdAt = now;
        prefix.updatedAt = now;
        return prefix;
    }

    /** 고유 말머리를 고친다 */
    public void change(String name, boolean staffOnly, String color, boolean active, int sortOrder, Instant now) {
        this.name = name;
        this.staffOnly = staffOnly;
        this.color = color;
        this.active = active;
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    /** 켜고 끈다(공통 말머리) */
    public void changeActive(boolean active, Instant now) {
        this.active = active;
        this.updatedAt = now;
    }

    public boolean isCommon() {
        return commonKey != null;
    }

    public Long getId() {
        return id;
    }

    public Long getBoardId() {
        return boardId;
    }

    public String getCommonKey() {
        return commonKey;
    }

    public String getName() {
        return name;
    }

    public boolean isStaffOnly() {
        return staffOnly;
    }

    public String getColor() {
        return color;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
