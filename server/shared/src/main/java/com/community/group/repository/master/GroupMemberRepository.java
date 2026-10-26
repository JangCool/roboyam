package com.community.group.repository.master;

import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupMemberId;
import com.community.group.entity.GroupMemberStatus;
import com.community.group.entity.GroupRole;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 소모임 멤버·가입 신청 — 멤버 행은 소모임 행을 잠근 뒤 고친다(소모임 잠금이 줄을 세운다) */
public interface GroupMemberRepository extends RoboyamRepository<GroupMemberEntity, GroupMemberId> {

    Optional<GroupMemberEntity> findByGroupIdAndUserId(Long groupId, Long userId);

    /** 그 회원의 소모임 행들(상태) */
    List<GroupMemberEntity> findByUserIdAndStatusIn(Long userId, Collection<GroupMemberStatus> statuses);

    /** 그 회원이 여러 소모임에서 가진 행(목록의 내 상태) */
    List<GroupMemberEntity> findByUserIdAndGroupIdIn(Long userId, Collection<Long> groupIds);

    /** 한 소모임에서 여러 회원의 행(소모임 글 댓글의 「나간 멤버」 가림) */
    List<GroupMemberEntity> findByGroupIdAndUserIdIn(Long groupId, Collection<Long> userIds);

    /** 가입 신청 목록 — 오래된 신청이 위 */
    List<GroupMemberEntity> findByGroupIdAndStatusOrderByAppliedAtAscUserIdAsc(Long groupId, GroupMemberStatus status);

    /** 지금 멤버 전원(해산 알림) */
    List<GroupMemberEntity> findByGroupIdAndStatus(Long groupId, GroupMemberStatus status);

    long countByGroupIdAndStatus(Long groupId, GroupMemberStatus status);

    /** 매니저(3단계) — 임명이 이른 순 */
    List<GroupMemberEntity> findByGroupIdAndRoleAndStatusOrderByRoleChangedAtAscUserIdAsc(Long groupId, GroupRole role, GroupMemberStatus status);

    /** 매니저 수(3단계 — 인원 상한 판정·상세 managerInfo) */
    long countByGroupIdAndRoleAndStatus(Long groupId, GroupRole role, GroupMemberStatus status);

    /** 하루 가입·신청 한도 — 그 시각 뒤에 바로 가입한 수 · 신청한 수 */
    long countByUserIdAndJoinedAtAfterAndDecidedByIsNull(Long userId, Instant after);

    long countByUserIdAndAppliedAtAfter(Long userId, Instant after);
}
