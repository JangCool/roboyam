package com.community.group.service;

import com.community.group.dto.GroupRuleValues;
import com.community.group.dto.request.GroupJoinRequest;
import com.community.group.dto.response.GroupJoinResultView;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupManagerPermission;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupMemberStatus;
import com.community.group.entity.GroupRole;
import com.community.group.exception.GroupAlreadyAppliedException;
import com.community.group.exception.GroupAlreadyMemberException;
import com.community.group.exception.GroupClosedException;
import com.community.group.exception.GroupFullException;
import com.community.group.exception.GroupLeaderCannotLeaveException;
import com.community.group.exception.GroupRateLimitedException;
import com.community.group.exception.GroupReapplyLaterException;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupPostQueries;
import com.community.group.repository.master.GroupRepositoryQDSL;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입·신청·신청 취소·탈퇴(community api-groups.md 3절) — 로그인 + 이메일 인증 + 정지 아님. 소모임 행을 잠그고 멤버 수·정원을 같은 트랜잭션에서 본다(동시에 몰려도 넘지 않는다).
 *
 * <p><b>가입 거절 차례</b>: 401 → 404 · 403 {@code GROUP_INACTIVE} → 쓰기 자격 → 입력({@code fields.message}·{@code fields.offlineAck}) → 이미 멤버 → 이미 신청 →
 * 모집 마감 → 정원 → 재신청 대기(429) → 하루 한도(429 {@code RATE_LIMITED}). 상세의 {@code join} 판정과 같은 차례다.
 */
@Service
public class GroupMembershipService {

    private static final Logger log = LoggerFactory.getLogger(GroupMembershipService.class);
    static final String RESULT_JOINED = "JOINED";
    static final String RESULT_APPLIED = "APPLIED";

    private final GroupAccess access;
    private final GroupMemberRepository members;
    private final GroupRepositoryQDSL queries;
    private final GroupSettingsService settings;
    private final GroupNotices notices;
    private final GroupPostQueries groupPosts;

    public GroupMembershipService(GroupAccess access, GroupMemberRepository members, GroupRepositoryQDSL queries, GroupSettingsService settings,
                                  GroupNotices notices, GroupPostQueries groupPosts) {
        this.groupPosts = groupPosts;
        this.access = access;
        this.members = members;
        this.queries = queries;
        this.settings = settings;
        this.notices = notices;
    }

    @Transactional
    public GroupJoinResultView join(String rawId, Long userId, GroupJoinRequest request) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        access.requireWriter(me);

        String message = request == null ? null : GroupRules.normalize(request.message());
        if (message != null && message.isEmpty()) {
            message = null;
        }
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        if (message != null && message.codePointCount(0, message.length()) > GroupRules.JOIN_MESSAGE_MAX) {
            invalid.put("message", new MessageRef("group.join.message.max", String.valueOf(GroupRules.JOIN_MESSAGE_MAX)));
        }
        if (group.isOffline() && (request == null || !Boolean.TRUE.equals(request.offlineAck()))) {
            invalid.put("offlineAck", new MessageRef("group.join.offlineAck.required"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }

        GroupMemberEntity row = members.findByGroupIdAndUserId(group.getId(), me).orElse(null);
        if (group.getLeaderId().equals(me) || (row != null && row.active())) {
            throw new GroupAlreadyMemberException();
        }
        if (row != null && row.getStatus() == GroupMemberStatus.APPLIED) {
            throw new GroupAlreadyAppliedException();
        }
        if (!group.isRecruiting()) {
            throw new GroupClosedException();
        }
        if (group.full()) {
            throw new GroupFullException();
        }
        Instant now = Instant.now();
        GroupRuleValues.Policy policy = settings.policy();
        long wait = GroupViews.reapplyWaitSeconds(row, now, policy);
        if (wait > 0) {
            throw new GroupReapplyLaterException(wait);
        }
        checkDailyLimit(me, now);

        boolean apply = GroupViews.METHOD_APPLY.equals(GroupViews.method(group, row));
        if (apply) {
            if (row == null) {
                members.persist(GroupMemberEntity.applied(group.getId(), me, message, now));
            } else {
                row.apply(message, now);
            }
            members.flush();
            // 처리할 사람: 모임장 + 가입 신청을 맡은 매니저(3단계)
            notices.joinRequested(group, group.getLeaderId(), me);
            for (GroupMemberEntity manager : members.findByGroupIdAndRoleAndStatusOrderByRoleChangedAtAscUserIdAsc(group.getId(), GroupRole.MANAGER,
                    GroupMemberStatus.ACTIVE)) {
                if ((manager.getManagerPerms() & GroupManagerPermission.APPLICATIONS.bit()) != 0) {
                    notices.joinRequested(group, manager.getUserId(), me);
                }
            }
            log.info("소모임 가입을 신청했다 groupId={} userId={}", group.getId(), me);
            return new GroupJoinResultView(RESULT_APPLIED, null);
        }
        if (row == null) {
            members.persist(GroupMemberEntity.joined(group.getId(), me, message, now));
        } else {
            row.join(message, now);
            groupPosts.restoreForMember(group.getId(), me, now); // 다시 가입 — 나가서 가린 글만 되살린다(운영자가 숨긴 글은 그대로)
        }
        group.addMembers(1, now);
        members.flush();
        log.info("소모임에 가입했다 groupId={} userId={} memberCount={}", group.getId(), me, group.getMemberCount());
        return new GroupJoinResultView(RESULT_JOINED, group.getMemberCount());
    }

    /** 신청 취소 — 내 승인 대기 신청을 거둔다(없어도 204, 멱등) */
    @Transactional
    public void cancel(String rawId, Long userId) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        access.requireLeaver(me);
        members.findByGroupIdAndUserId(group.getId(), me).filter(row -> row.getStatus() == GroupMemberStatus.APPLIED).ifPresent(row -> {
            row.withdrawApplication(Instant.now());
            log.info("소모임 가입 신청을 거뒀다 groupId={} userId={}", group.getId(), me);
        });
    }

    /** 탈퇴 — 모임장은 409(넘기거나 해산), 비멤버는 204(멱등). 매니저 자격은 같이 사라진다 */
    @Transactional
    public void leave(String rawId, Long userId) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        access.requireLeaver(me);
        if (group.getLeaderId().equals(me)) {
            throw new GroupLeaderCannotLeaveException();
        }
        GroupMemberEntity row = access.membership(group.getId(), me).orElse(null);
        if (row == null) {
            return;
        }
        Instant now = Instant.now();
        row.leave(now);
        int hidden = groupPosts.hideForLeaver(group.getId(), me, now); // 스스로 나가면 그 소모임 안의 내 글은 모두 가려진다(다시 가입하면 되살린다 — 8절)
        group.addMembers(-1, now);
        log.info("소모임에서 나갔다 groupId={} userId={} memberCount={} 가린 글={}", group.getId(), me, group.getMemberCount(), hidden);
    }

    /** 하루(24시간) 가입·신청 한도 — 넘으면 429 RATE_LIMITED(가장 오래된 시도가 24시간을 넘길 때까지) */
    private void checkDailyLimit(long userId, Instant now) {
        int limit = settings.properties().joinDailyLimit();
        if (limit <= 0) {
            return;
        }
        Instant since = now.minus(Duration.ofDays(1));
        List<Instant> attempts = queries.joinAttempts(userId, since);
        if (attempts.size() >= limit) {
            Instant oldest = attempts.stream().min(Instant::compareTo).orElse(now);
            throw new GroupRateLimitedException(Duration.between(now, oldest.plus(Duration.ofDays(1))).toSeconds());
        }
    }
}
