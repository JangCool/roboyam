package com.community.group.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardFields;
import com.community.group.dto.GroupRuleValues;
import com.community.group.dto.request.GroupCreateRequest;
import com.community.group.dto.response.GroupCreateResultView;
import com.community.group.entity.GroupCreationMode;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupJoinType;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupStatus;
import com.community.group.entity.GroupVisibility;
import com.community.group.exception.GroupCreateNotPermittedException;
import com.community.group.exception.GroupDissolvePenaltyException;
import com.community.group.exception.GroupGradeTooLowException;
import com.community.group.exception.GroupLimitReachedException;
import com.community.group.exception.GroupNameTakenException;
import com.community.group.exception.GroupNotFoundException;
import com.community.group.exception.GroupRateLimitedException;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupRepository;
import com.community.group.repository.master.GroupRepositoryQDSL;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개설 신청 · 내 개설 신청 취소(community api-groups.md 4절).
 *
 * <p><b>차례</b>: 401 → 쓰기 자격(403 {@code EMAIL_NOT_VERIFIED} · {@code ACCOUNT_RESTRICTED}) → 회원 권한 {@code group.create}(403 {@code GROUP_CREATE_NOT_PERMITTED} — 로보얌) →
 * 입력 400 {@code fields.*} → 최소 등급 403 {@code GROUP_GRADE_TOO_LOW} → 1인 상한 409 {@code GROUP_LIMIT_REACHED}(+{@code max}) →
 * 해산 패널티 403 {@code GROUP_DISSOLVE_PENALTY}(+{@code retryAfterSeconds}·{@code until} — V96) → 하루 한도 429 {@code RATE_LIMITED} →
 * 같은 이름 409 {@code GROUP_NAME_TAKEN}. 회원 행을 잠가 같은 회원의 동시 신청을 줄 세운다(상한·하루 한도가 넘치지 않게).
 * 승인제면 PENDING(202) + 운영자 알림 {@code CLUB_OPEN_REQUEST_PENDING}, 자유 개설이면 바로 ACTIVE(201 — 신청자가 모임장·첫 멤버). 로보얌에는 공개 사이트에 로그인하는
 * 최고 관리자가 없어(운영자는 관리자 앱) 「최고 관리자가 만들면 바로 열림·등급 면제」는 없다.
 */
@Service
public class GroupCreateService {

    private static final Logger log = LoggerFactory.getLogger(GroupCreateService.class);

    private final GroupAccess access;
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupSettingsService settings;
    private final GroupViews views;
    private final UserRepository users;
    private final SnowflakeIdGenerator ids;
    private final NotificationPublisher operatorNotices;

    public GroupCreateService(GroupAccess access, GroupRepository groups, GroupMemberRepository members, GroupSettingsService settings, GroupViews views,
                              UserRepository users, SnowflakeIdGenerator ids, NotificationPublisher operatorNotices) {
        this.access = access;
        this.groups = groups;
        this.members = members;
        this.settings = settings;
        this.views = views;
        this.users = users;
        this.ids = ids;
        this.operatorNotices = operatorNotices;
    }

    @Transactional
    public GroupCreateResultView create(Long userId, GroupCreateRequest request) {
        long me = GroupAccess.requireUser(userId);
        users.findWithLockById(me).filter(u -> !u.isGone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        BoardActor actor = access.actor(me);
        String blocked = GroupAccess.writeBlocked(actor);
        if (blocked != null) {
            throw new com.community.group.exception.GroupWriteBlockedException(blocked);
        }
        if (!actor.can(com.community.permission.entity.MemberPermission.GROUP_CREATE.code())) {
            throw new GroupCreateNotPermittedException();
        }

        GroupViews.Context ctx = views.context();
        GroupRuleValues.Policy policy = settings.policy();
        Input input = validate(request, ctx, policy);

        GroupRuleValues.Creation rules = settings.creation();
        Integer required = ctx.boards().gradeLevel(rules.minGrade());
        if (required != null && actor.gradeLevel() < required) {
            throw new GroupGradeTooLowException(rules.minGrade(), ctx.boards().gradeLabel(rules.minGrade()));
        }
        long led = groups.countByLeaderIdAndStatusIn(me, GroupRepositoryQDSL.LIVE);
        if (led >= rules.maxPerOwner()) {
            throw new GroupLimitReachedException(rules.maxPerOwner());
        }
        Instant now = Instant.now();
        Instant penaltyUntil = penaltyUntil(groups, me, now);
        if (penaltyUntil != null) {
            throw new GroupDissolvePenaltyException(penaltyUntil, now);
        }
        int daily = settings.properties().createDailyLimit();
        if (daily > 0) {
            Instant since = now.minus(Duration.ofDays(1));
            if (groups.countByCreatorIdAndRequestedAtAfter(me, since) >= daily) {
                Instant oldest = groups.findFirstByCreatorIdAndRequestedAtAfterOrderByRequestedAtAsc(me, since).map(GroupEntity::getRequestedAt).orElse(now);
                throw new GroupRateLimitedException(Duration.between(now, oldest.plus(Duration.ofDays(1))).toSeconds());
            }
        }
        String nameKey = GroupRules.nameKey(input.name);
        if (groups.existsByNameKeyAndStatusIn(nameKey, GroupRepositoryQDSL.LIVE)) {
            throw new GroupNameTakenException();
        }

        boolean open = rules.creationMode() == GroupCreationMode.OPEN;
        GroupVisibility visibility = ctx.visibilityEnabled() && input.visibility != null ? input.visibility : GroupVisibility.PRIVATE;
        GroupEntity group = GroupEntity.request(ids.nextId(), input.name, nameKey, input.description, input.board.id(), input.joinType, input.capacity,
                input.offline, visibility, me, settings.manager().limitDefault(), input.board.investmentNotice() && input.agreeStock, open, now);
        groups.persist(group);
        if (open) {
            members.persist(GroupMemberEntity.leader(group.getId(), me, now));
        }
        try {
            groups.flush();
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("ux_groups_name_key_live")) {
                throw new GroupNameTakenException();
            }
            throw e;
        }
        if (!open) {
            operatorNotices.publish(AdminNotificationEvent.CLUB_OPEN_REQUEST_PENDING, NotificationContext.empty());
        }
        log.info("소모임 개설을 신청했다 groupId={} userId={} status={}", group.getId(), me, group.getStatus());
        return new GroupCreateResultView(Long.toString(group.getId()), group.getStatus().name());
    }

    /**
     * 해산 패널티 끝 시각 — 그 회원이 모임장으로 해산한 소모임 중 끝 시각이 지금 뒤인 것의 가장 늦은 시각, 없으면 null(V96). 끝 시각은 해산 때 박혀 설정을 바꿔도 그대로다
     */
    static Instant penaltyUntil(GroupRepository groups, long userId, Instant now) {
        return groups.findFirstByLeaderIdAndDissolvePenaltyUntilAfterOrderByDissolvePenaltyUntilDesc(userId, now).map(GroupEntity::getDissolvePenaltyUntil).orElse(null);
    }

    /** 내 신청 중인 개설 신청을 거둔다 — 내 것이 아니거나 이미 처리됐으면 404 */
    @Transactional
    public void cancelRequest(String rawId, Long userId) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = groups.findWithLockById(GroupAccess.parseId(rawId))
                .filter(g -> g.getStatus() == GroupStatus.PENDING && me == (g.getCreatorId() == null ? -1L : g.getCreatorId()))
                .orElseThrow(GroupNotFoundException::new);
        group.delete(Instant.now());
        log.info("소모임 개설 신청을 거뒀다 groupId={} userId={}", group.getId(), me);
    }

    // ── 입력 ─────────────────────────────────────────────────────────────────

    private static final class Input {
        String name;
        String description;
        EffectiveBoard board;
        GroupJoinType joinType;
        int capacity;
        boolean offline;
        GroupVisibility visibility;
        boolean agreeStock;
    }

    /** 칸별 위반을 모은다 — name · description · categoryId · boardSlug · joinType · capacity · visibility · agreeRules · agreeStock · agreeOffline */
    private static Input validate(GroupCreateRequest r, GroupViews.Context ctx, GroupRuleValues.Policy policy) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        Input in = new Input();
        GroupCreateRequest req = r == null ? new GroupCreateRequest(null, null, null, null, null, null, null, null, null, null, null) : r;

        String nameProblem = GroupRules.nameProblem(req.name());
        if (nameProblem != null) {
            invalid.put("name", new MessageRef(nameProblem, String.valueOf(GroupRules.NAME_MIN), String.valueOf(GroupRules.NAME_MAX)));
        } else {
            in.name = GroupRules.normalize(req.name());
        }
        String description = req.description() == null ? null : req.description().strip();
        if (description == null || description.isEmpty() || description.codePointCount(0, description.length()) > GroupRules.DESCRIPTION_MAX) {
            invalid.put("description", new MessageRef("group.description.length", String.valueOf(GroupRules.DESCRIPTION_MAX)));
        } else {
            in.description = description;
        }

        Long categoryId = GroupQueryService.parseLong(req.categoryId());
        List<EffectiveBoard> candidates = new ArrayList<>();
        if (categoryId == null || !ctx.categories().byId().containsKey(categoryId)) {
            invalid.put("categoryId", new MessageRef("group.categoryId.required"));
        } else {
            for (Long boardId : ctx.categories().boards(categoryId)) {
                ctx.boards().board(boardId).filter(b -> b.active() && BoardFields.KIND_GROUP.equals(b.kind())).ifPresent(candidates::add);
            }
            if (candidates.isEmpty()) {
                invalid.put("categoryId", new MessageRef("group.categoryId.required"));
            } else if (req.boardSlug() == null || req.boardSlug().isBlank()) {
                if (candidates.size() == 1) {
                    in.board = candidates.get(0);
                } else {
                    invalid.put("boardSlug", new MessageRef("group.boardSlug.required"));
                }
            } else {
                String slug = req.boardSlug().strip();
                in.board = candidates.stream().filter(b -> b.slug().equals(slug)).findFirst().orElse(null);
                if (in.board == null) {
                    invalid.put("boardSlug", new MessageRef("group.boardSlug.invalid"));
                }
            }
        }

        in.joinType = enumOf(GroupJoinType.class, req.joinType());
        if (in.joinType == null) {
            invalid.put("joinType", new MessageRef("group.joinType.invalid"));
        }
        if (req.capacity() == null || req.capacity() < policy.capacityMin() || req.capacity() > policy.capacityMax()) {
            invalid.put("capacity", new MessageRef("group.capacity.range", String.valueOf(policy.capacityMin()), String.valueOf(policy.capacityMax())));
        } else {
            in.capacity = req.capacity();
        }
        in.offline = Boolean.TRUE.equals(req.offline());
        if (ctx.visibilityEnabled() && req.visibility() != null && !req.visibility().isBlank()) {
            in.visibility = enumOf(GroupVisibility.class, req.visibility());
            if (in.visibility == null) {
                invalid.put("visibility", new MessageRef("group.visibility.invalid"));
            }
        }
        if (!Boolean.TRUE.equals(req.agreeRules())) {
            invalid.put("agreeRules", new MessageRef("group.agreeRules.required"));
        }
        in.agreeStock = Boolean.TRUE.equals(req.agreeStock());
        if (in.board != null && in.board.investmentNotice() && !in.agreeStock) {
            invalid.put("agreeStock", new MessageRef("group.agreeStock.required"));
        }
        if (in.offline && !Boolean.TRUE.equals(req.agreeOffline())) {
            invalid.put("agreeOffline", new MessageRef("group.agreeOffline.required"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        return in;
    }

    static <E extends Enum<E>> E enumOf(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
