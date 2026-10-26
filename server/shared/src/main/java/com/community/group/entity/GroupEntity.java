package com.community.group.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 소모임 한 행 — V89__groups.sql 의 {@code groups}. 개설 신청도 이 행이다(PENDING → ACTIVE · REJECTED · DELETED).
 * 정원·멤버 수·상태를 바꾸는 일은 모두 행을 먼저 잠그고(저장소 {@code findWithLockById}) 같은 트랜잭션에서 한다. ID 는 Snowflake 라 새 행은 {@code persist()}.
 */
@Entity
@Table(name = "groups")
@DynamicUpdate
public class GroupEntity {

    @Id
    private Long id;

    private String name;
    private String nameKey;
    private String description;
    private Long boardId;
    @Enumerated(EnumType.STRING)
    private GroupJoinType joinType;
    private int capacity;
    private boolean offline;
    private boolean recruiting;
    @Enumerated(EnumType.STRING)
    private GroupVisibility visibility;
    private Instant visibilityChangedAt;
    private Long visibilityChangedBy;
    @Enumerated(EnumType.STRING)
    private GroupStatus status;
    private Long leaderId;
    private Long creatorId;
    private int memberCount;
    private int managerLimit;
    private boolean stockAgreed;
    private String rejectReason;
    private Long reviewedBy;
    private Instant reviewedAt;
    private String kakaoUrl;
    private String kakaoLabel;
    private String discordUrl;
    private String discordLabel;
    private Instant requestedAt;
    private Instant openedAt;
    private Instant lastActivityAt;
    private Instant deletedAt;
    private Instant deactivatedAt;
    private Long deactivatedBy;
    private String deactivateReason;
    private Long deactivateCaseId;
    private Instant reactivatedAt;
    private Long reactivatedBy;
    private String reactivateMemo;

    protected GroupEntity() {
    }

    /**
     * 개설 신청(또는 자유 개설) — {@code open} 이면 바로 열린다(신청자가 모임장·첫 멤버, 멤버 수 1). 아니면 PENDING(멤버 수 0, 승인 때 1).
     */
    public static GroupEntity request(long id, String name, String nameKey, String description, long boardId, GroupJoinType joinType, int capacity,
                                      boolean offline, GroupVisibility visibility, long creatorId, int managerLimit, boolean stockAgreed, boolean open, Instant now) {
        GroupEntity row = new GroupEntity();
        row.id = id;
        row.name = name;
        row.nameKey = nameKey;
        row.description = description;
        row.boardId = boardId;
        row.joinType = joinType;
        row.capacity = capacity;
        row.offline = offline;
        row.recruiting = true;
        row.visibility = visibility;
        row.leaderId = creatorId;
        row.creatorId = creatorId;
        row.managerLimit = managerLimit;
        row.stockAgreed = stockAgreed;
        row.requestedAt = now;
        if (open) {
            row.status = GroupStatus.ACTIVE;
            row.memberCount = 1;
            row.openedAt = now;
            row.lastActivityAt = now;
        } else {
            row.status = GroupStatus.PENDING;
            row.memberCount = 0;
        }
        return row;
    }

    /** 운영자 승인 — 열린다(신청자가 모임장·첫 멤버) */
    public void approve(long operatorId, Instant now) {
        this.status = GroupStatus.ACTIVE;
        this.memberCount = 1;
        this.reviewedBy = operatorId;
        this.reviewedAt = now;
        this.openedAt = now;
        this.lastActivityAt = now;
    }

    /** 운영자 반려(사유는 신청자에게 그대로 간다) */
    public void reject(long operatorId, String reason, Instant now) {
        this.status = GroupStatus.REJECTED;
        this.rejectReason = reason;
        this.reviewedBy = operatorId;
        this.reviewedAt = now;
    }

    /** 신청 취소 · 해산 — 목록·상세에서 사라진다(404) */
    public void delete(Instant now) {
        this.status = GroupStatus.DELETED;
        this.deletedAt = now;
    }

    /** 비활성 — 마지막 비활성 기록을 새로 쓰고 다시 열기 기록을 비운다 */
    public void deactivate(long operatorId, String reason, Long caseId, Instant now) {
        this.status = GroupStatus.INACTIVE;
        this.deactivatedAt = now;
        this.deactivatedBy = operatorId;
        this.deactivateReason = reason;
        this.deactivateCaseId = caseId;
        this.reactivatedAt = null;
        this.reactivatedBy = null;
        this.reactivateMemo = null;
    }

    /** 다시 열기 — 비활성 기록은 남는다. 최근 활동 시각은 바꾸지 않는다 */
    public void reactivate(long operatorId, String memo, Instant now) {
        this.status = GroupStatus.ACTIVE;
        this.reactivatedAt = now;
        this.reactivatedBy = operatorId;
        this.reactivateMemo = memo;
    }

    /** 모임장의 정보 수정(분류·게시판은 못 바꾼다) */
    public void edit(String name, String nameKey, String description, GroupJoinType joinType, int capacity, boolean offline) {
        this.name = name;
        this.nameKey = nameKey;
        this.description = description;
        this.joinType = joinType;
        this.capacity = capacity;
        this.offline = offline;
    }

    public void setRecruiting(boolean recruiting) {
        this.recruiting = recruiting;
    }

    public void changeChatLinks(String kakaoUrl, String kakaoLabel, String discordUrl, String discordLabel) {
        this.kakaoUrl = kakaoUrl;
        this.kakaoLabel = kakaoLabel;
        this.discordUrl = discordUrl;
        this.discordLabel = discordLabel;
    }

    /** 모임장이 정한 매니저 인원 상한(3단계 — 1 이상 매니저 규칙의 최대 인원 이하는 부르는 쪽이 본다) */
    public void changeManagerLimit(int limit) {
        this.managerLimit = limit;
    }

    public void changeLeader(long userId) {
        this.leaderId = userId;
    }

    /** 멤버가 늘거나 줄었다(행을 잠근 뒤) */
    public void addMembers(int delta, Instant now) {
        this.memberCount = Math.max(0, this.memberCount + delta);
        if (delta > 0) {
            this.lastActivityAt = now;
        }
    }

    /** 최근 활동 — 소모임 글을 썼다(행을 잠근 뒤, 지금 뜨는 소모임) */
    public void touchActivity(Instant now) {
        this.lastActivityAt = now;
    }

    // ── 읽기 ──────────────────────────────────────────────────────────────

    /** 정원이 찼나 */
    public boolean full() {
        return memberCount >= capacity;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getDescription() {
        return description;
    }

    public Long getBoardId() {
        return boardId;
    }

    public GroupJoinType getJoinType() {
        return joinType;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isOffline() {
        return offline;
    }

    public boolean isRecruiting() {
        return recruiting;
    }

    public GroupVisibility getVisibility() {
        return visibility;
    }

    public Instant getVisibilityChangedAt() {
        return visibilityChangedAt;
    }

    public Long getVisibilityChangedBy() {
        return visibilityChangedBy;
    }

    public GroupStatus getStatus() {
        return status;
    }

    public Long getLeaderId() {
        return leaderId;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public int getManagerLimit() {
        return managerLimit;
    }

    public boolean isStockAgreed() {
        return stockAgreed;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getKakaoUrl() {
        return kakaoUrl;
    }

    public String getKakaoLabel() {
        return kakaoLabel;
    }

    public String getDiscordUrl() {
        return discordUrl;
    }

    public String getDiscordLabel() {
        return discordLabel;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getDeactivatedAt() {
        return deactivatedAt;
    }

    public Long getDeactivatedBy() {
        return deactivatedBy;
    }

    public String getDeactivateReason() {
        return deactivateReason;
    }

    public Long getDeactivateCaseId() {
        return deactivateCaseId;
    }

    public Instant getReactivatedAt() {
        return reactivatedAt;
    }

    public Long getReactivatedBy() {
        return reactivatedBy;
    }

    public String getReactivateMemo() {
        return reactivateMemo;
    }
}
