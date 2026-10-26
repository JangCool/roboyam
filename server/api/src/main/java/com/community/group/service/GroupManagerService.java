package com.community.group.service;

import com.community.group.dto.GroupRuleValues;
import com.community.group.dto.request.GroupManagerLimitRequest;
import com.community.group.dto.request.GroupManagerRequest;
import com.community.group.dto.response.GroupManagerListView;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupManagerPermission;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupMemberStatus;
import com.community.group.entity.GroupRole;
import com.community.group.exception.GroupLeaderCannotLeaveException;
import com.community.group.exception.GroupManagerLimitReachedException;
import com.community.group.exception.GroupMemberNotFoundException;
import com.community.group.exception.GroupNotMemberException;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소모임 매니저(3단계 — community api-groups.md 5-1) — 임명 · 맡은 일 바꾸기 · 해임 · 자진 사퇴 · 인원 설정 · 매니저 목록. <b>모든 변경은 소모임 행을 먼저 잠근다</b>
 * (동시에 둘이 임명해도 인원 상한을 넘지 않는다). 판정은 {@link GroupAccess} 가 요청마다 DB 의 현재 값으로 한다.
 *
 * <p>판정 차례(1단계와 같다): 로그인 401 → 소모임 404 · 403 {@code GROUP_INACTIVE} → 역할(모임장 고정 403 {@code GROUP_LEADER_ONLY}) → (임명·맡은 일 바꾸기만) 매니저 기능
 * 403 {@code GROUP_MANAGERS_DISABLED} → 쓰기 자격(403 {@code EMAIL_NOT_VERIFIED} → {@code ACCOUNT_RESTRICTED} → {@code BUSINESS_ACCOUNT_RESTRICTED}) → 입력 400 → 대상·상태.
 * 해임·사퇴·인원 설정은 매니저 기능이 꺼져 있어도 동작한다(이미 있는 매니저를 정리할 수 있어야 한다). 유효 상한 = min(소모임 상한, 매니저 규칙의 최대 인원).
 */
@Service
public class GroupManagerService {

    private static final Logger log = LoggerFactory.getLogger(GroupManagerService.class);

    private final GroupAccess access;
    private final GroupMemberRepository members;
    private final GroupSettingsService settings;
    private final GroupPeople people;
    private final GroupNotices notices;

    public GroupManagerService(GroupAccess access, GroupMemberRepository members, GroupSettingsService settings, GroupPeople people, GroupNotices notices) {
        this.access = access;
        this.members = members;
        this.settings = settings;
        this.people = people;
        this.notices = notices;
    }

    // ── 임명 · 맡은 일 바꾸기 ────────────────────────────────────────────────

    /**
     * 지금 멤버를 매니저로 임명하거나(아직 매니저가 아니면) 맡은 일을 교체한다(이미 매니저면). 같은 집합이면 기록·알림 없이 끝난다. 새 임명만 인원 상한을 본다.
     * 대상: 지금 멤버(정지·이메일 미인증이어도 임명할 수 있다 — 도구만 멈춘다). 멤버가 아니면 404 {@code GROUP_MEMBER_NOT_FOUND}, 모임장이면 409 {@code GROUP_LEADER_CANNOT_LEAVE}.
     */
    @Transactional
    public void appoint(String rawId, String rawTarget, Long userId, GroupManagerRequest request) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        access.requireLeader(group, me);
        access.requireManagersEnabled();
        access.requireWriter(me);
        List<String> keys = parsePermissions(request);
        int mask = GroupManagerPermission.maskOf(keys.stream().map(k -> GroupManagerPermission.of(k).orElseThrow()).toList());

        Long target = GroupQueryService.parseLong(rawTarget);
        GroupMemberEntity row = target == null ? null : access.membership(group.getId(), target).orElse(null);
        if (row == null) {
            throw new GroupMemberNotFoundException();
        }
        if (group.getLeaderId().equals(target)) {
            throw new GroupLeaderCannotLeaveException(); // 모임장은 역할 대상이 아니다(코드 재사용 — 원본과 같다)
        }
        List<String> ordered = GroupManagerPermission.keys(mask);
        if (row.getRole() == GroupRole.MANAGER) {
            if (row.getManagerPerms() == mask) {
                return;
            }
            row.changePermissions(mask);
            notices.managerPermissionsChanged(group, target, me, ordered);
            log.info("소모임 매니저의 맡은 일을 바꿨다 groupId={} userId={} perms={} by userId={}", group.getId(), target, ordered, me);
            return;
        }
        GroupRuleValues.Manager rules = settings.manager();
        int limit = rules.effectiveLimit(group.getManagerLimit());
        long count = members.countByGroupIdAndRoleAndStatus(group.getId(), GroupRole.MANAGER, GroupMemberStatus.ACTIVE);
        if (count >= limit) {
            throw new GroupManagerLimitReachedException(limit);
        }
        row.appoint(mask, me, Instant.now());
        notices.managerAppointed(group, target, me, ordered);
        log.info("소모임 매니저를 임명했다 groupId={} userId={} perms={} by userId={}", group.getId(), target, ordered, me);
    }

    /** 요청의 맡은 일 — 1~5개, 위 키만, 중복 불가. 아니면 400 {@code fields.permissions} */
    private static List<String> parsePermissions(GroupManagerRequest request) {
        Object raw = request == null ? null : request.permissions();
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw ValidationException.of("permissions", new MessageRef("group.manager.permissions.required"));
        }
        Set<String> chosen = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof String key) || GroupManagerPermission.of(key.strip()).isEmpty()) {
                throw ValidationException.of("permissions", new MessageRef("group.manager.permissions.invalid"));
            }
            if (!chosen.add(key.strip())) {
                throw ValidationException.of("permissions", new MessageRef("group.manager.permissions.duplicate"));
            }
        }
        return List.copyOf(chosen);
    }

    // ── 해임 · 사퇴 ──────────────────────────────────────────────────────────

    /** 해임 — 이미 매니저가 아니면(일반 멤버·비멤버·모임장 포함) 아무 일도 없다(멱등 204). 매니저 기능이 꺼져 있어도 동작한다 */
    @Transactional
    public void remove(String rawId, String rawTarget, Long userId) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        access.requireLeader(group, me);
        access.requireWriter(me);
        Long target = GroupQueryService.parseLong(rawTarget);
        GroupMemberEntity row = target == null ? null : access.membership(group.getId(), target).orElse(null);
        if (row == null || row.getRole() != GroupRole.MANAGER) {
            return;
        }
        row.demote(me);
        notices.managerRemoved(group, target, me);
        log.info("소모임 매니저를 해임했다 groupId={} userId={} by userId={}", group.getId(), target, me);
    }

    /**
     * 자진 사퇴 — 매니저가 아니면(일반 멤버·모임장·비멤버) 아무 일도 없다(멱등 204, 쓰기 자격도 보지 않는다 — 원본과 같다). 정지·이메일 미인증인 매니저는 멈춘다
     * (탈퇴와 같은 규칙 — 사업자 회원은 막지 않는다). 사퇴하면 일반 멤버가 되고 모임장에게 알림. 매니저 규칙과 무관하다.
     */
    @Transactional
    public void resign(String rawId, Long userId) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        GroupMemberEntity row = access.membership(group.getId(), me).orElse(null);
        if (row == null || row.getRole() != GroupRole.MANAGER) {
            return;
        }
        access.requireLeaver(me);
        row.demote(me);
        notices.managerResigned(group, me);
        log.info("소모임 매니저가 물러났다 groupId={} userId={}", group.getId(), me);
    }

    // ── 인원 설정 ────────────────────────────────────────────────────────────

    /**
     * 이 소모임의 매니저 인원 상한 — 1 이상 매니저 규칙의 최대 인원 이하의 정수이고 지금 매니저 수보다 작게 줄일 수 없다(400 {@code fields.limit}).
     * 지금 매니저 수가 최대 인원보다 많으면(최고 관리자가 최대 인원을 줄인 뒤) 해임으로 먼저 줄여야 한다. 매니저 기능과 무관하게 동작한다.
     */
    @Transactional
    public void setLimit(String rawId, Long userId, GroupManagerLimitRequest request) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        access.requireLeader(group, me);
        access.requireWriter(me);
        Integer limit = wholeNumber(request == null ? null : request.limit());
        int max = settings.manager().limitMax();
        if (limit == null || limit < 1 || limit > max) {
            throw ValidationException.of("limit", new MessageRef("group.manager.limit.range", String.valueOf(max)));
        }
        long count = members.countByGroupIdAndRoleAndStatus(group.getId(), GroupRole.MANAGER, GroupMemberStatus.ACTIVE);
        if (count > max) {
            throw ValidationException.of("limit", new MessageRef("group.manager.limit.overMax", String.valueOf(count), String.valueOf(max)));
        }
        if (limit < count) {
            throw ValidationException.of("limit", new MessageRef("group.manager.limit.belowCount", String.valueOf(count)));
        }
        if (group.getManagerLimit() == limit) {
            return;
        }
        group.changeManagerLimit(limit);
        log.info("소모임 매니저 인원 상한을 바꿨다 groupId={} limit={} by userId={}", group.getId(), limit, me);
    }

    /** JSON 정수만(문자열·소수·불리언은 null) */
    private static Integer wholeNumber(Object raw) {
        if (raw instanceof Integer i) {
            return i;
        }
        if (raw instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
            return l.intValue();
        }
        if (raw instanceof Short s) {
            return (int) s;
        }
        return null;
    }

    // ── 목록 ────────────────────────────────────────────────────────────────

    /** 매니저 목록 — 멤버만(비회원 401, 비멤버 403 {@code GROUP_NOT_MEMBER}). 맡은 일은 모임장과 그 매니저 본인에게만 싣는다 */
    @Transactional(readOnly = true)
    public GroupManagerListView list(String rawId, Long userId) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.active(GroupAccess.parseId(rawId));
        boolean leader = group.getLeaderId().equals(me);
        if (!leader && access.membership(group.getId(), me).isEmpty()) {
            throw new GroupNotMemberException();
        }
        List<GroupMemberEntity> rows = members.findByGroupIdAndRoleAndStatusOrderByRoleChangedAtAscUserIdAsc(group.getId(), GroupRole.MANAGER,
                GroupMemberStatus.ACTIVE);
        GroupPeople.Members persons = people.members(rows.stream().map(GroupMemberEntity::getUserId).toList(), true);
        List<GroupManagerListView.Item> items = rows.stream().map(m -> new GroupManagerListView.Item(GroupUsers.of(persons.of(m.getUserId())), m.getRoleChangedAt(),
                leader || m.getUserId().equals(me) ? GroupManagerPermission.keys(m.getManagerPerms()) : null)).toList();
        GroupRuleValues.Manager rules = settings.manager();
        return new GroupManagerListView(items, items.size(), rules.effectiveLimit(group.getManagerLimit()), rules.limitMax());
    }
}
