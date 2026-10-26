package com.community.group.service;

import com.community.board.dto.BoardActor;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupManagerPermission;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupRole;
import com.community.group.entity.GroupStatus;
import com.community.group.exception.GroupInactiveException;
import com.community.group.exception.GroupLeaderOnlyException;
import com.community.group.exception.GroupManagerPermissionDeniedException;
import com.community.group.exception.GroupNotFoundException;
import com.community.group.exception.GroupWriteBlockedException;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 소모임 판정 한 곳 — 로그인 · 소모임 상태 · 역할(모임장 고정 / 맡은 일) · 쓰기 자격을 요청마다 DB 의 현재 값으로 본다(캐시 없음).
 *
 * <p><b>쓰기 판정 순서</b>(community api-groups.md 5-1): 로그인 401 → 소모임 상태(404 {@code GROUP_NOT_FOUND} · 403 {@code GROUP_INACTIVE}) → 역할
 * (403 {@code GROUP_LEADER_ONLY} · {@code GROUP_MANAGER_PERMISSION_DENIED}) → 쓰기 자격(403 {@code EMAIL_NOT_VERIFIED} → {@code ACCOUNT_RESTRICTED} → {@code BUSINESS_ACCOUNT_RESTRICTED}) → 입력 → 대상·상태.
 * 부르는 서비스의 트랜잭션 안에서 돈다(잠금은 트랜잭션 안에서만 뜻이 있다).
 */
@Component
public class GroupAccess {

    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final BoardActorResolver actors;
    private final BoardCatalog catalog;

    public GroupAccess(GroupRepository groups, GroupMemberRepository members, BoardActorResolver actors, BoardCatalog catalog) {
        this.groups = groups;
        this.members = members;
        this.actors = actors;
        this.catalog = catalog;
    }

    /** 로그인한 회원 ID — 없으면 401 */
    public static long requireUser(Long userId) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }

    /** 경로의 소모임 ID — 숫자가 아니면 404 */
    public static long parseId(String raw) {
        try {
            return Long.parseLong(raw == null ? "" : raw.strip());
        } catch (NumberFormatException e) {
            throw new GroupNotFoundException();
        }
    }

    /** 지금 쓰려는 사람(등급·정지·이메일 인증·회원 역할 권한) — 비회원·탈퇴는 401 */
    public BoardActor actor(long userId) {
        BoardActor actor = actors.resolve(userId, catalog.snapshot());
        if (actor.guest()) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return actor;
    }

    /** 쓰기를 막는 까닭(이메일 인증 → 정지 → 사업자 회원) — 없으면 null */
    public static String writeBlocked(BoardActor actor) {
        if (actor == null || actor.guest()) {
            return null;
        }
        if (!actor.emailVerified()) {
            return GroupWriteBlockedException.EMAIL_NOT_VERIFIED;
        }
        if (actor.restricted()) {
            return GroupWriteBlockedException.ACCOUNT_RESTRICTED;
        }
        return actor.business() ? GroupWriteBlockedException.BUSINESS_ACCOUNT_RESTRICTED : null;
    }

    /** 쓰기 자격 — 이메일 인증 전 · 정지면 403 */
    public void requireWriter(long userId) {
        String blocked = writeBlocked(actor(userId));
        if (blocked != null) {
            throw new GroupWriteBlockedException(blocked);
        }
    }

    /** 나가기·신청 취소의 자격 — 이메일 인증 전·정지만 막는다(사업자 회원이 된 멤버도 나갈 수 있게) */
    public void requireLeaver(long userId) {
        String blocked = writeBlocked(actor(userId));
        if (blocked != null && !GroupWriteBlockedException.BUSINESS_ACCOUNT_RESTRICTED.equals(blocked)) {
            throw new GroupWriteBlockedException(blocked);
        }
    }

    /** 소모임 행을 잠그고 읽는다 — 열림만(없음·신청 중·반려·해산 404, 비활성 403) */
    public GroupEntity lockActive(long groupId) {
        GroupEntity group = groups.findWithLockById(groupId).orElseThrow(GroupNotFoundException::new);
        return requireActive(group);
    }

    /** 잠그지 않고 읽는다 — 열림만 */
    public GroupEntity active(long groupId) {
        return requireActive(groups.findById(groupId).orElseThrow(GroupNotFoundException::new));
    }

    private static GroupEntity requireActive(GroupEntity group) {
        if (group.getStatus() == GroupStatus.INACTIVE) {
            throw new GroupInactiveException();
        }
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new GroupNotFoundException();
        }
        return group;
    }

    /** 그 회원의 지금 멤버 행(멤버가 아니면 비어 있다) */
    public Optional<GroupMemberEntity> membership(long groupId, long userId) {
        return members.findByGroupIdAndUserId(groupId, userId).filter(GroupMemberEntity::active);
    }

    /** 모임장 고정 일 — 모임장이 아니면 403 {@code GROUP_LEADER_ONLY} */
    public void requireLeader(GroupEntity group, long userId) {
        if (!group.getLeaderId().equals(userId)) {
            throw new GroupLeaderOnlyException();
        }
    }

    /**
     * 체크형 일 — 모임장은 늘, 매니저는 그 체크가 있어야 한다. 멤버·비멤버 403 {@code GROUP_LEADER_ONLY}, 체크가 없는 매니저 403 {@code GROUP_MANAGER_PERMISSION_DENIED}.
     *
     * @return 처리자의 역할(LEADER · MANAGER)
     */
    public GroupRole requireCapability(GroupEntity group, long userId, GroupManagerPermission permission) {
        if (group.getLeaderId().equals(userId)) {
            return GroupRole.LEADER;
        }
        GroupMemberEntity row = membership(group.getId(), userId).orElseThrow(GroupLeaderOnlyException::new);
        if (row.getRole() != GroupRole.MANAGER) {
            throw new GroupLeaderOnlyException();
        }
        if ((row.getManagerPerms() & permission.bit()) == 0) {
            throw new GroupManagerPermissionDeniedException();
        }
        return GroupRole.MANAGER;
    }

    /** 그 사람이 이 일을 할 수 있나(역할·체크만 — 정지·미인증은 따로) */
    public static boolean can(GroupEntity group, GroupMemberEntity row, Long userId, GroupManagerPermission permission) {
        if (userId == null) {
            return false;
        }
        if (group.getLeaderId().equals(userId)) {
            return true;
        }
        return row != null && row.active() && row.getRole() == GroupRole.MANAGER && (row.getManagerPerms() & permission.bit()) != 0;
    }
}
