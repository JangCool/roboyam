package com.community.board.entity;

import com.community.board.dto.BoardPolicyValues;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 게시판 — V42__boards.sql 의 {@code boards}. ID 는 Snowflake(앱 발급)라 새 행은 {@code persist()} 로 넣는다.
 * 정책 칸은 null = 기본 설정({@link BoardDefaultsEntity})을 따른다. slug·종류는 만든 뒤 바꾸지 않는다.
 */
@Entity
@Table(name = "boards")
@DynamicUpdate
public class BoardEntity {

    @Id
    private Long id;

    private String slug;
    private String name;
    private String nameEn;
    private String description;
    private String kind;
    private String layout;
    private boolean viewToggle;
    private String color;
    private boolean allowAdult;
    private boolean investmentNotice;
    private boolean active;
    private int sortOrder;
    private String writeLock;
    private String introText;
    private String writeGuideText;

    private String writeMin;
    private String commentMin;
    private String postEditor;
    private Boolean imageUploadAllowed;
    private String commentEditor;
    private Integer listSize;
    private Integer pinnedMax;
    private Boolean indexable;
    private Boolean searchable;
    private Boolean inHome;
    private String pointMode;
    private Integer commentProtectMin;
    private Boolean commentProtectEdit;
    private String commentProtectDelete;
    private Integer commentProtectDelayMinutes;
    private Integer commentGuestDelayMinutes;
    private Integer managerMax;
    private Integer staffPostDailyLimit;
    private Integer staffCommentDailyLimit;
    private Integer staffSanctionDailyLimit;

    private Instant createdAt;
    private Long createdBy;
    private Instant updatedAt;
    private Long updatedBy;

    protected BoardEntity() {
    }

    /** 새 게시판 — 켜진 채, 정책은 모두 기본값을 따르고, 안내문·잠금은 없다 */
    public static BoardEntity create(long id, String slug, String name, String nameEn, String description, String kind, String layout, boolean viewToggle,
                                     String color, boolean investmentNotice, int sortOrder, Long operatorId, Instant now) {
        BoardEntity board = new BoardEntity();
        board.id = id;
        board.slug = slug;
        board.name = name;
        board.nameEn = nameEn;
        board.description = description == null ? "" : description;
        board.kind = kind;
        board.layout = layout;
        board.viewToggle = !"CARD".equals(layout) && viewToggle;
        board.color = color;
        board.investmentNotice = investmentNotice;
        board.active = true;
        board.sortOrder = sortOrder;
        board.createdAt = now;
        board.createdBy = operatorId;
        board.updatedAt = now;
        board.updatedBy = operatorId;
        return board;
    }

    /** 기본 정보를 바꾼다 */
    public void changeBasic(String name, String nameEn, String description, String layout, boolean viewToggle, String color, boolean allowAdult,
                            boolean investmentNotice) {
        this.name = name;
        this.nameEn = nameEn;
        this.description = description;
        this.layout = layout;
        this.viewToggle = !"CARD".equals(layout) && viewToggle;
        this.color = color;
        this.allowAdult = allowAdult;
        this.investmentNotice = investmentNotice;
    }

    /** 잠금·안내문을 바꾼다 */
    public void changeGuides(String writeLock, String introText, String writeGuideText) {
        this.writeLock = writeLock;
        this.introText = introText;
        this.writeGuideText = writeGuideText;
    }

    /** 정책 칸 전체를 바꾼다(null = 기본값 따름) */
    public void changePolicy(BoardPolicyValues policy) {
        this.writeMin = policy.writeMin();
        this.commentMin = policy.commentMin();
        this.postEditor = policy.postEditor();
        this.imageUploadAllowed = policy.imageUploadAllowed();
        this.commentEditor = policy.commentEditor();
        this.listSize = policy.listSize();
        this.pinnedMax = policy.pinnedMax();
        this.indexable = policy.indexable();
        this.searchable = policy.searchable();
        this.inHome = policy.inHome();
        this.pointMode = policy.pointMode();
        this.commentProtectMin = policy.commentProtectMin();
        this.commentProtectEdit = policy.commentProtectEdit();
        this.commentProtectDelete = policy.commentProtectDelete();
        this.commentProtectDelayMinutes = policy.commentProtectDelayMinutes();
        this.commentGuestDelayMinutes = policy.commentGuestDelayMinutes();
        this.managerMax = policy.managerMax();
        this.staffPostDailyLimit = policy.staffPostDailyLimit();
        this.staffCommentDailyLimit = policy.staffCommentDailyLimit();
        this.staffSanctionDailyLimit = policy.staffSanctionDailyLimit();
    }

    public void changeActive(boolean active) {
        this.active = active;
    }

    public void reorder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** 마지막으로 고친 운영자·시각 */
    public void touch(Long operatorId, Instant now) {
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public BoardPolicyValues policy() {
        return new BoardPolicyValues(writeMin, commentMin, postEditor, imageUploadAllowed, commentEditor, listSize, pinnedMax, indexable, searchable, inHome, pointMode, commentProtectMin,
                commentProtectEdit, commentProtectDelete, commentProtectDelayMinutes, commentGuestDelayMinutes, managerMax, staffPostDailyLimit,
                staffCommentDailyLimit, staffSanctionDailyLimit);
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getDescription() {
        return description;
    }

    public String getKind() {
        return kind;
    }

    public String getLayout() {
        return layout;
    }

    public boolean isViewToggle() {
        return viewToggle;
    }

    public String getColor() {
        return color;
    }

    public boolean isAllowAdult() {
        return allowAdult;
    }

    public boolean isInvestmentNotice() {
        return investmentNotice;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getWriteLock() {
        return writeLock;
    }

    public String getIntroText() {
        return introText;
    }

    public String getWriteGuideText() {
        return writeGuideText;
    }

    public Integer getManagerMax() {
        return managerMax;
    }

    public String getWriteMin() {
        return writeMin;
    }

    public String getCommentMin() {
        return commentMin;
    }
}
