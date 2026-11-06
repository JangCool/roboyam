package com.community.withdrawal.service;

import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupMemberStatus;
import com.community.group.entity.GroupRole;
import com.community.group.entity.GroupStatus;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupPostQueries;
import com.community.group.repository.master.GroupRepository;
import com.community.group.service.GroupImages;
import com.community.group.service.GroupNotices;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴하는 모임장의 소모임 처리(commerce.md 17-1 D6) — 회원이 고른 「넘기기」(대상 고름)·「해산」, 고르지 않으면 자동 위임(매니저 → 가장 오래된 멤버 순), 멤버가 없으면 해산.
 * <b>{@code requireWriter}(이메일 미인증·정지)를 거치지 않는 내부 경로</b>라 미인증·정지 모임장도 탈퇴할 수 있다. 탈퇴로 인한 해산에는 해산 패널티를 매기지 않는다.
 * 신청 때는 선택만 저장하고 <b>완료 때</b>(즉시 완료·배치 완료 공통 — {@link WithdrawalCompletionService}) 처리한다 — 취소하면 소모임은 그대로 남는다. 부르는 쪽 트랜잭션 안에서.
 */
@Component
public class WithdrawalGroupHandler {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalGroupHandler.class);

    /** 넘겨받을 수 있는 멤버 — 매니저(임명이 이른 순) 먼저, 그다음 일반 멤버(가입이 이른 순) */
    public record Candidate(long userId, GroupRole role) {
    }

    private static final List<GroupStatus> LIVE = List.of(GroupStatus.PENDING, GroupStatus.ACTIVE, GroupStatus.INACTIVE);

    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupPostQueries groupPosts;
    private final GroupNotices notices;
    private final GroupImages images;

    public WithdrawalGroupHandler(GroupRepository groups, GroupMemberRepository members, GroupPostQueries groupPosts, GroupNotices notices, GroupImages images) {
        this.images = images;
        this.groups = groups;
        this.members = members;
        this.groupPosts = groupPosts;
        this.notices = notices;
    }

    /** 내가 모임장인 살아 있는 소모임(개설 심사 중 포함) */
    @Transactional(readOnly = true)
    public List<GroupEntity> led(long userId) {
        return groups.findByLeaderIdAndStatusIn(userId, LIVE).stream().sorted(Comparator.comparing(GroupEntity::getId)).toList();
    }

    /** 넘겨받을 후보(나 제외) — 자동 위임 순서와 같다 */
    @Transactional(readOnly = true)
    public List<Candidate> candidates(GroupEntity group, long me) {
        List<GroupMemberEntity> active = members.findByGroupIdAndStatus(group.getId(), GroupMemberStatus.ACTIVE);
        Comparator<GroupMemberEntity> order = Comparator.comparing((GroupMemberEntity m) -> m.getRole() == GroupRole.MANAGER ? 0 : 1)
                .thenComparing(m -> m.getRole() == GroupRole.MANAGER ? nvl(m.getRoleChangedAt()) : nvl(m.getJoinedAt())).thenComparing(GroupMemberEntity::getUserId);
        return active.stream().filter(m -> !m.getUserId().equals(me) && m.getRole() != GroupRole.LEADER).sorted(order)
                .map(m -> new Candidate(m.getUserId(), m.getRole() == GroupRole.MANAGER ? GroupRole.MANAGER : GroupRole.MEMBER)).toList();
    }

    /** 자동 위임이 고를 사람(없으면 비어 있다 → 해산) */
    @Transactional(readOnly = true)
    public Optional<Candidate> autoDelegate(GroupEntity group, long me) {
        return candidates(group, me).stream().findFirst();
    }

    /**
     * 넘기기 — 대상은 그 소모임의 지금 멤버(나 제외). 새 모임장이 매니저였으면 자리·맡은 일이 사라지고 이전 모임장은 일반 멤버가 된다(탈퇴 완료 때 나간다).
     *
     * @throws ValidationException 대상이 지금 멤버가 아닐 때({@code fields.groupChoices})
     */
    @Transactional
    public void transfer(long groupId, long me, long target, Instant now) {
        GroupEntity group = lockLed(groupId, me).orElse(null);
        if (group == null) {
            return;
        }
        GroupMemberEntity next = members.findByGroupIdAndUserId(group.getId(), target).filter(GroupMemberEntity::active).filter(m -> target != me)
                .orElseThrow(() -> ValidationException.of("groupChoices", new MessageRef("withdrawal.groupChoices.candidate")));
        members.findByGroupIdAndUserId(group.getId(), me).ifPresent(previous -> previous.demote(me));
        next.promoteToLeader(me, now);
        group.changeLeader(target);
        notices.leaderTransferred(group, target, me);
        log.info("탈퇴로 모임장을 넘겼다 groupId={} from={} to={}", group.getId(), me, target);
    }

    /** 해산 — 소모임째 DELETED, 글 모두 삭제, 멤버 전원(나 제외)에게 알림. 패널티 없음. 대표 이미지는 떼어 정리 배치에 맡긴다(V154) */
    @Transactional
    public void dissolve(long groupId, long me, Instant now) {
        GroupEntity group = lockLed(groupId, me).orElse(null);
        if (group == null) {
            return;
        }
        List<GroupMemberEntity> active = members.findByGroupIdAndStatus(group.getId(), GroupMemberStatus.ACTIVE);
        images.detach(group);
        group.dissolve(now, null);
        groups.flush();
        int deleted = groupPosts.deleteOnDissolve(group.getId(), now);
        for (GroupMemberEntity m : active) {
            if (!m.getUserId().equals(me)) {
                notices.dissolved(group, m.getUserId(), me);
            }
        }
        log.info("탈퇴로 소모임을 해산했다 groupId={} 멤버={}명 글={}건 by userId={}", group.getId(), active.size(), deleted, me);
    }

    /**
     * 완료 때의 처리 — 신청 때 저장한 선택대로. {@code TRANSFER} 대상이 더 이상 지금 멤버가 아니면(또는 대상이 없으면) {@link #auto} 로 떨어진다(완료를 막지 않는다).
     * 신청 뒤 새로 모임장이 된 소모임은 선택이 없으므로 {@code AUTO}.
     *
     * @param action {@code AUTO} · {@code TRANSFER} · {@code DISSOLVE}(그 밖·null 은 AUTO)
     */
    @Transactional
    public void complete(long groupId, long me, String action, Long target, Instant now) {
        GroupEntity group = lockLed(groupId, me).orElse(null);
        if (group == null) {
            return;
        }
        if ("DISSOLVE".equals(action)) {
            dissolve(groupId, me, now);
            return;
        }
        if ("TRANSFER".equals(action) && target != null && candidates(group, me).stream().anyMatch(c -> c.userId() == target)) {
            transfer(groupId, me, target, now);
            return;
        }
        auto(groupId, me, now);
    }

    /** 자동 — 매니저 → 가장 오래된 멤버에게 넘기고, 멤버가 없으면 해산 */
    @Transactional
    public void auto(long groupId, long me, Instant now) {
        GroupEntity group = lockLed(groupId, me).orElse(null);
        if (group == null) {
            return;
        }
        Optional<Candidate> next = autoDelegate(group, me);
        if (next.isPresent()) {
            transfer(groupId, me, next.get().userId(), now);
        } else {
            dissolve(groupId, me, now);
        }
    }

    /** 아직 내가 모임장인 살아 있는 소모임이면 잠가 읽는다(그 사이 넘겼거나 해산됐으면 비어 있다) */
    private Optional<GroupEntity> lockLed(long groupId, long me) {
        return groups.findWithLockById(groupId).filter(g -> LIVE.contains(g.getStatus()) && g.getLeaderId() != null && g.getLeaderId() == me);
    }

    private static Instant nvl(Instant at) {
        return at == null ? Instant.EPOCH : at;
    }
}
