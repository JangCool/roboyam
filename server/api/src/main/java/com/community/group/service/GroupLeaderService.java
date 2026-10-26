package com.community.group.service;

import com.community.group.dto.GroupRuleValues;
import com.community.group.dto.request.GroupChatLinksRequest;
import com.community.group.dto.request.GroupDissolveRequest;
import com.community.group.dto.request.GroupLeaderRequest;
import com.community.group.dto.request.GroupReasonRequest;
import com.community.group.dto.request.GroupRecruitingRequest;
import com.community.group.dto.request.GroupUpdateRequest;
import com.community.group.dto.response.GroupApplicationsView;
import com.community.group.dto.response.GroupDetailView;
import com.community.group.dto.response.GroupMemberCountView;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupJoinType;
import com.community.group.entity.GroupManagerPermission;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupMemberStatus;
import com.community.group.entity.GroupRole;
import com.community.group.exception.GroupApplicationNotFoundException;
import com.community.group.exception.GroupFullException;
import com.community.group.exception.GroupLeaderCannotLeaveException;
import com.community.group.exception.GroupManagerProtectedException;
import com.community.group.exception.GroupMemberNotFoundException;
import com.community.group.exception.GroupNameTakenException;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupPostQueries;
import com.community.group.repository.master.GroupRepository;
import com.community.group.repository.master.GroupRepositoryQDSL;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임장·매니저 관리(community api-groups.md 5절 — 매니저 임명 계열 5-1 은 {@link GroupManagerService}). 판정은 {@link GroupAccess}: 401 → 404·403 {@code GROUP_INACTIVE} → 역할 → 쓰기 자격 → 입력 → 대상.
 *
 * <ul>
 *   <li><b>모임장 고정</b>: 정보 수정 · 채팅 링크 · 모임장 넘기기 · 해산.</li>
 *   <li><b>체크형</b>(모임장 + 그 일을 맡은 매니저): 모집 마감·재개(RECRUITING) · 가입 신청 열람·승인·거절(APPLICATIONS) · 강퇴(KICK — 매니저는 일반 멤버만).</li>
 *   <li>정지 중이거나 이메일 인증 전인 모임장·매니저는 쓰기가 모두 멈춘다(열람은 열려 있다).</li>
 *   <li>해산: 소모임째 DELETED, 멤버 전원에게 알림. 소모임 글은 공개 여부와 상관없이 모두 지운다. 포인트는 회수하지 않고, 1년 미만 유지된 소모임이면 모임장에게
 *       해산 패널티(설정 {@code dissolvePenaltyDays} 동안 개설 신청 불가 — {@link GroupDissolvePenalty}, V96)를 남긴다.</li>
 * </ul>
 */
@Service
public class GroupLeaderService {

    private static final Logger log = LoggerFactory.getLogger(GroupLeaderService.class);

    private final GroupAccess access;
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupSettingsService settings;
    private final GroupViews views;
    private final GroupPeople people;
    private final GroupNotices notices;
    private final GroupPostQueries groupPosts;
    private final BoardActorResolver actors;
    private final BoardCatalog catalog;

    public GroupLeaderService(GroupAccess access, GroupRepository groups, GroupMemberRepository members, GroupSettingsService settings, GroupViews views,
                              GroupPeople people, GroupNotices notices, BoardActorResolver actors, BoardCatalog catalog, GroupPostQueries groupPosts) {
        this.groupPosts = groupPosts;
        this.access = access;
        this.groups = groups;
        this.members = members;
        this.settings = settings;
        this.views = views;
        this.people = people;
        this.notices = notices;
        this.actors = actors;
        this.catalog = catalog;
    }

    // ── 모임장 고정 ──────────────────────────────────────────────────────────

    /** 정보 수정 — 모든 칸. 정원은 운영 한도 범위 안이고 지금 멤버 수보다 작게 줄일 수 없다. 응답은 상세 */
    @Transactional
    public GroupDetailView update(String rawId, Long userId, GroupUpdateRequest request) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        access.requireLeader(group, me);
        access.requireWriter(me);
        GroupUpdateRequest r = request == null ? new GroupUpdateRequest(null, null, null, null, null) : request;
        GroupRuleValues.Policy policy = settings.policy();
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        String nameProblem = GroupRules.nameProblem(r.name());
        if (nameProblem != null) {
            invalid.put("name", new MessageRef(nameProblem, String.valueOf(GroupRules.NAME_MIN), String.valueOf(GroupRules.NAME_MAX)));
        }
        String description = r.description() == null ? null : r.description().strip();
        if (description == null || description.isEmpty() || description.codePointCount(0, description.length()) > GroupRules.DESCRIPTION_MAX) {
            invalid.put("description", new MessageRef("group.description.length", String.valueOf(GroupRules.DESCRIPTION_MAX)));
        }
        GroupJoinType joinType = GroupCreateService.enumOf(GroupJoinType.class, r.joinType());
        if (joinType == null) {
            invalid.put("joinType", new MessageRef("group.joinType.invalid"));
        }
        if (r.capacity() == null || r.capacity() < policy.capacityMin() || r.capacity() > policy.capacityMax()) {
            invalid.put("capacity", new MessageRef("group.capacity.range", String.valueOf(policy.capacityMin()), String.valueOf(policy.capacityMax())));
        } else if (r.capacity() < group.getMemberCount()) {
            invalid.put("capacity", new MessageRef("group.capacity.belowMembers", String.valueOf(group.getMemberCount())));
        }
        if (r.offline() == null) {
            invalid.put("offline", new MessageRef("group.offline.required"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        String name = GroupRules.normalize(r.name());
        String nameKey = GroupRules.nameKey(name);
        if (!nameKey.equals(group.getNameKey()) && groups.existsByNameKeyAndStatusIn(nameKey, GroupRepositoryQDSL.LIVE)) {
            throw new GroupNameTakenException();
        }
        group.edit(name, nameKey, description, joinType, r.capacity(), r.offline());
        try {
            groups.flush();
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("ux_groups_name_key_live")) {
                throw new GroupNameTakenException();
            }
            throw e;
        }
        log.info("소모임 정보를 고쳤다 groupId={} by userId={}", group.getId(), me);
        return views.detail(group, me, actors.resolve(me, catalog.snapshot()), Instant.now());
    }

    /** 채팅 링크 — 카카오 오픈채팅·디스코드 초대 https 주소만, 이름 선택 30자. null 이면 지운다 */
    @Transactional
    public void chatLinks(String rawId, Long userId, GroupChatLinksRequest request) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        access.requireLeader(group, me);
        access.requireWriter(me);
        GroupChatLinksRequest.Link kakao = request == null ? null : request.kakao();
        GroupChatLinksRequest.Link discord = request == null ? null : request.discord();
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        if (kakao != null && (!GroupRules.kakaoUrl(kakao.url()) || tooLong(kakao.label()))) {
            invalid.put("kakao", new MessageRef("group.chat.kakao.invalid", String.valueOf(GroupRules.CHAT_LABEL_MAX)));
        }
        if (discord != null && (!GroupRules.discordUrl(discord.url()) || tooLong(discord.label()))) {
            invalid.put("discord", new MessageRef("group.chat.discord.invalid", String.valueOf(GroupRules.CHAT_LABEL_MAX)));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        group.changeChatLinks(kakao == null ? null : kakao.url().strip(), kakao == null ? null : label(kakao.label()),
                discord == null ? null : discord.url().strip(), discord == null ? null : label(discord.label()));
        log.info("소모임 채팅 링크를 바꿨다 groupId={} 카카오={} 디스코드={} by userId={}", group.getId(), kakao != null, discord != null, me);
    }

    /** 모임장 넘기기 — 내가 아닌 지금 멤버에게. 새 모임장이 매니저였으면 자리·맡은 일이 사라지고 이전 모임장은 일반 멤버가 된다 */
    @Transactional
    public void transferLeader(String rawId, Long userId, GroupLeaderRequest request) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        access.requireLeader(group, me);
        access.requireWriter(me);
        Long target = request == null ? null : GroupQueryService.parseLong(request.userId());
        if (target == null) {
            throw ValidationException.of("userId", new MessageRef("group.leader.userId.required"));
        }
        if (target == me) {
            throw new GroupMemberNotFoundException();
        }
        GroupMemberEntity next = access.membership(group.getId(), target).orElseThrow(GroupMemberNotFoundException::new);
        Instant now = Instant.now();
        members.findByGroupIdAndUserId(group.getId(), me).ifPresent(previous -> previous.demote(me));
        next.promoteToLeader(me, now);
        group.changeLeader(target);
        notices.leaderTransferred(group, target, me);
        log.info("모임장을 넘겼다 groupId={} from={} to={}", group.getId(), me, target);
    }

    /**
     * 해산 — 이름을 그대로 입력해야 한다. 소모임째 DELETED, 글 모두 삭제, 멤버 전원(모임장 제외)에게 알림. 개설부터 1년(KST 달력)이 안 됐고 패널티 일수가 0 이 아니면
     * 그 모임장의 패널티 끝 시각(지금 + 일수)을 소모임 행에 남긴다 — 개설 신청 403 {@code GROUP_DISSOLVE_PENALTY}
     */
    @Transactional
    public void dissolve(String rawId, Long userId, GroupDissolveRequest request) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        access.requireLeader(group, me);
        access.requireWriter(me);
        String typed = request == null || request.name() == null ? null : request.name().strip();
        if (typed == null || !typed.equals(group.getName())) {
            throw ValidationException.of("name", new MessageRef("group.dissolve.name.mismatch"));
        }
        List<GroupMemberEntity> active = members.findByGroupIdAndStatus(group.getId(), GroupMemberStatus.ACTIVE);
        Instant now = Instant.now();
        Instant penaltyUntil = GroupDissolvePenalty.penaltyUntil(group, settings.creation().dissolvePenaltyDays(), now);
        group.dissolve(now, penaltyUntil);
        groups.flush();
        // 글은 공개 여부와 상관없이 모두 지운다(2026-10-26 사용자 결정 — community 의 「공개 글은 연결 유지」를 바꿨다)
        int deleted = groupPosts.deleteOnDissolve(group.getId(), now);
        log.info("소모임 해산 — 글 {}건을 지웠다 groupId={} 패널티끝={}", deleted, group.getId(), penaltyUntil);
        for (GroupMemberEntity m : active) {
            if (!m.getUserId().equals(me)) {
                notices.dissolved(group, m.getUserId(), me);
            }
        }
        log.info("소모임을 해산했다 groupId={} 멤버={}명 by userId={}", group.getId(), active.size(), me);
    }

    // ── 체크형(모임장 + 맡은 매니저) ──────────────────────────────────────────

    /** 모집 켜기·끄기 */
    @Transactional
    public void recruiting(String rawId, Long userId, GroupRecruitingRequest request) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        access.requireCapability(group, me, GroupManagerPermission.RECRUITING);
        access.requireWriter(me);
        if (request == null || request.recruiting() == null) {
            throw ValidationException.of("recruiting", new MessageRef("group.recruiting.required"));
        }
        group.setRecruiting(request.recruiting());
        log.info("소모임 모집을 바꿨다 groupId={} recruiting={} by userId={}", group.getId(), request.recruiting(), me);
    }

    /** 가입 신청 목록 — 열람은 정지·미인증이어도 열려 있다 */
    @Transactional(readOnly = true)
    public GroupApplicationsView applications(String rawId, Long userId) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.active(GroupAccess.parseId(rawId));
        access.requireCapability(group, me, GroupManagerPermission.APPLICATIONS);
        List<GroupMemberEntity> rows = members.findByGroupIdAndStatusOrderByAppliedAtAscUserIdAsc(group.getId(), GroupMemberStatus.APPLIED);
        GroupPeople.Members persons = people.members(rows.stream().map(GroupMemberEntity::getUserId).toList(), true);
        return new GroupApplicationsView(rows.stream().map(m -> new GroupApplicationsView.Item(GroupUsers.of(persons.of(m.getUserId())), m.getMessage(), m.getAppliedAt(),
                m.isKickedBefore())).toList(), group.getCapacity(), group.getMemberCount());
    }

    /** 신청 승인 — 정원을 잠금 아래서 본다(409 {@code GROUP_FULL}). 두 사람이 동시에 처리하면 늦은 쪽은 404 {@code GROUP_APPLICATION_NOT_FOUND} */
    @Transactional
    public GroupMemberCountView approve(String rawId, String rawUserId, Long userId) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        access.requireCapability(group, me, GroupManagerPermission.APPLICATIONS);
        access.requireWriter(me);
        GroupMemberEntity row = applicationOf(group, rawUserId);
        if (group.full()) {
            throw new GroupFullException();
        }
        Instant now = Instant.now();
        row.approve(me, now);
        group.addMembers(1, now);
        groupPosts.restoreForMember(group.getId(), row.getUserId(), now); // 나갔다 다시 들어온 사람 — 나가서 가린 글만 되살린다(승인 대기 동안은 가려진 채)
        notices.joinApproved(group, row.getUserId(), me);
        log.info("소모임 가입 신청을 승인했다 groupId={} userId={} by userId={} memberCount={}", group.getId(), row.getUserId(), me, group.getMemberCount());
        return new GroupMemberCountView(group.getMemberCount());
    }

    /** 신청 거절 — 사유 선택 100자(적었을 때만 알림에 담는다). 거절한 사람은 운영 한도(재신청 대기 일수)가 지나야 다시 신청할 수 있다 */
    @Transactional
    public void reject(String rawId, String rawUserId, Long userId, GroupReasonRequest request) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        access.requireCapability(group, me, GroupManagerPermission.APPLICATIONS);
        access.requireWriter(me);
        String reason = request == null || request.reason() == null || request.reason().isBlank() ? null : request.reason().strip();
        if (reason != null && reason.codePointCount(0, reason.length()) > GroupRules.APPLICATION_REJECT_MAX) {
            throw ValidationException.of("reason", new MessageRef("group.application.reason.max", String.valueOf(GroupRules.APPLICATION_REJECT_MAX)));
        }
        GroupMemberEntity row = applicationOf(group, rawUserId);
        row.reject(me, reason, Instant.now());
        notices.joinRejected(group, row.getUserId(), me, reason);
        log.info("소모임 가입 신청을 거절했다 groupId={} userId={} by userId={}", group.getId(), row.getUserId(), me);
    }

    /** 강퇴 — 모임장은 일반 멤버·매니저 모두, 강퇴를 맡은 매니저는 일반 멤버만(모임장·매니저·자기 자신 403). 모임장 자신은 409 */
    @Transactional
    public void kick(String rawId, String rawUserId, Long userId) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        GroupRole role = access.requireCapability(group, me, GroupManagerPermission.KICK);
        access.requireWriter(me);
        Long target = GroupQueryService.parseLong(rawUserId);
        if (target == null) {
            throw new GroupMemberNotFoundException();
        }
        if (group.getLeaderId().equals(target)) {
            if (role == GroupRole.MANAGER) {
                throw new GroupManagerProtectedException();
            }
            throw new GroupLeaderCannotLeaveException();
        }
        GroupMemberEntity row = access.membership(group.getId(), target).orElseThrow(GroupMemberNotFoundException::new);
        if (role == GroupRole.MANAGER && (row.getRole() != GroupRole.MEMBER || target == me)) {
            throw new GroupManagerProtectedException();
        }
        Instant now = Instant.now();
        row.kick(now);
        group.addMembers(-1, now);
        notices.kicked(group, target, me);
        if (role == GroupRole.MANAGER) { // 매니저가 내보냈으면 모임장도 안다(3단계 GROUP_KICKED_BY_MANAGER — 일으킨 사람 = 그 매니저, 내보낸 멤버)
            notices.kickedByManager(group, me, target, people.members(List.of(target), false).nickname(target));
        }
        log.info("소모임에서 강퇴했다 groupId={} userId={} by userId={}({})", group.getId(), target, me, role);
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private GroupMemberEntity applicationOf(GroupEntity group, String rawUserId) {
        Long target = GroupQueryService.parseLong(rawUserId);
        if (target == null) {
            throw new GroupApplicationNotFoundException();
        }
        return members.findByGroupIdAndUserId(group.getId(), target).filter(m -> m.getStatus() == GroupMemberStatus.APPLIED)
                .orElseThrow(GroupApplicationNotFoundException::new);
    }

    private static boolean tooLong(String label) {
        return label != null && label.strip().codePointCount(0, label.strip().length()) > GroupRules.CHAT_LABEL_MAX;
    }

    private static String label(String raw) {
        return raw == null || raw.isBlank() ? null : raw.strip();
    }
}
