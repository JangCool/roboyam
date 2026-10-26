package com.community.group.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 소모임 × 회원 한 행 — V90 {@code group_members}. 멤버·가입 신청·거절·탈퇴·강퇴를 한 행의 상태로 둔다(다시 오면 같은 행을 고친다).
 * <b>자격 소멸은 이 엔티티 한 곳</b>: 나가기·강퇴·다시 가입·신청이 역할을 MEMBER, 맡은 일을 0 으로 되돌린다(매니저 자격은 다시 가입해도 돌아오지 않는다).
 */
@Entity
@Table(name = "group_members")
@IdClass(GroupMemberId.class)
@DynamicUpdate
public class GroupMemberEntity {

    @Id
    private Long groupId;
    @Id
    private Long userId;

    @Enumerated(EnumType.STRING)
    private GroupRole role;
    private int managerPerms;
    private Instant roleChangedAt;
    private Long roleChangedBy;
    @Enumerated(EnumType.STRING)
    private GroupMemberStatus status;
    private String message;
    private Instant appliedAt;
    private Instant joinedAt;
    private Instant decidedAt;
    private Long decidedBy;
    private String rejectReason;
    private Instant leftAt;
    private Instant kickedAt;
    private boolean kickedBefore;
    private Instant lastSeenAt;

    protected GroupMemberEntity() {
    }

    /** 모임장 행(개설 승인·자유 개설) */
    public static GroupMemberEntity leader(long groupId, long userId, Instant now) {
        GroupMemberEntity row = blank(groupId, userId);
        row.role = GroupRole.LEADER;
        row.status = GroupMemberStatus.ACTIVE;
        row.joinedAt = now;
        return row;
    }

    /** 새 행 — 바로 가입 */
    public static GroupMemberEntity joined(long groupId, long userId, String message, Instant now) {
        GroupMemberEntity row = blank(groupId, userId);
        row.join(message, now);
        return row;
    }

    /** 새 행 — 가입 신청 */
    public static GroupMemberEntity applied(long groupId, long userId, String message, Instant now) {
        GroupMemberEntity row = blank(groupId, userId);
        row.apply(message, now);
        return row;
    }

    private static GroupMemberEntity blank(long groupId, long userId) {
        GroupMemberEntity row = new GroupMemberEntity();
        row.groupId = groupId;
        row.userId = userId;
        row.role = GroupRole.MEMBER;
        return row;
    }

    /** 바로 가입(있던 행 — 탈퇴했다 다시 오는 경우) */
    public void join(String message, Instant now) {
        demote(null);
        this.status = GroupMemberStatus.ACTIVE;
        this.message = message;
        this.joinedAt = now;
        this.leftAt = null;
        this.rejectReason = null;
    }

    /** 가입 신청(있던 행 — 거절·탈퇴·강퇴 뒤 다시) */
    public void apply(String message, Instant now) {
        demote(null);
        this.status = GroupMemberStatus.APPLIED;
        this.message = message;
        this.appliedAt = now;
        this.decidedAt = null;
        this.decidedBy = null;
        this.rejectReason = null;
    }

    /** 신청 승인 — 승인 시각이 가입 시각이다 */
    public void approve(long deciderId, Instant now) {
        this.status = GroupMemberStatus.ACTIVE;
        this.decidedAt = now;
        this.decidedBy = deciderId;
        this.joinedAt = now;
        this.leftAt = null;
    }

    /** 신청 거절 — 거절 시각부터 재신청 대기 */
    public void reject(long deciderId, String reason, Instant now) {
        this.status = GroupMemberStatus.REJECTED;
        this.decidedAt = now;
        this.decidedBy = deciderId;
        this.rejectReason = reason;
    }

    /**
     * 신청 취소 — 행을 지우는 대신 탈퇴로 둔다(거절 기록·강퇴 기록이 남게). 나간 시각({@code leftAt})은 건드리지 않는다 — 그 칸은 「멤버였다가 스스로 나간 때」라
     * 나간 멤버의 댓글 가림(2단계)이 읽는다(신청만 했다 취소한 사람은 멤버였던 적이 없어 가려지지 않는다).
     */
    public void withdrawApplication(Instant now) {
        this.status = GroupMemberStatus.LEFT;
    }

    /** 글 탭을 봤다(새 글 수의 기준 시각) */
    public void seen(Instant now) {
        this.lastSeenAt = now;
    }

    /** 탈퇴 */
    public void leave(Instant now) {
        demote(null);
        this.status = GroupMemberStatus.LEFT;
        this.leftAt = now;
    }

    /** 강퇴 — 그 뒤로는 신청으로만 돌아온다 */
    public void kick(Instant now) {
        demote(null);
        this.status = GroupMemberStatus.KICKED;
        this.kickedAt = now;
        this.kickedBefore = true;
    }

    /** 모임장이 됐다(모임장 넘기기) — 매니저였으면 자리·맡은 일이 사라진다 */
    public void promoteToLeader(Long by, Instant now) {
        this.role = GroupRole.LEADER;
        this.managerPerms = 0;
        this.roleChangedAt = now;
        this.roleChangedBy = by;
    }

    /** 매니저로 임명(3단계) — 지금 멤버인 일반 멤버만(부르는 쪽이 본다). 맡은 일은 1개 이상 */
    public void appoint(int perms, long by, Instant now) {
        this.role = GroupRole.MANAGER;
        this.managerPerms = perms;
        this.roleChangedAt = now;
        this.roleChangedBy = by;
    }

    /** 매니저의 맡은 일을 바꾼다(3단계) — 임명 시각({@code roleChangedAt})은 그대로 둔다(매니저 목록의 「임명이 이른 순」이 맡은 일 바꾸기로 흔들리지 않게) */
    public void changePermissions(int perms) {
        this.managerPerms = perms;
    }

    /** 일반 멤버로 — 이전 모임장 · 해임 · 사퇴 · 자격 소멸 */
    public void demote(Long by) {
        if (this.role != GroupRole.MEMBER || this.managerPerms != 0) {
            this.role = GroupRole.MEMBER;
            this.managerPerms = 0;
            this.roleChangedAt = Instant.now();
            this.roleChangedBy = by;
        }
    }

    public boolean active() {
        return status == GroupMemberStatus.ACTIVE;
    }
    public Long getGroupId() {
        return groupId;
    }

    public Long getUserId() {
        return userId;
    }

    public GroupRole getRole() {
        return role;
    }

    public int getManagerPerms() {
        return managerPerms;
    }

    public Instant getRoleChangedAt() {
        return roleChangedAt;
    }

    public GroupMemberStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Instant getLeftAt() {
        return leftAt;
    }

    public Instant getKickedAt() {
        return kickedAt;
    }

    public boolean isKickedBefore() {
        return kickedBefore;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
