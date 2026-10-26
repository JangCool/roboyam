package com.community.group.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.service.BoardCatalog;
import com.community.group.dto.request.GroupAdminMemoRequest;
import com.community.group.dto.request.GroupAdminReasonRequest;
import com.community.group.dto.response.GroupAdminDetailView;
import com.community.group.dto.response.GroupAdminListView;
import com.community.group.dto.response.GroupBoardRefView;
import com.community.group.dto.response.GroupCategoryRefView;
import com.community.group.dto.response.GroupOpenRequestListView;
import com.community.group.dto.response.GroupPersonView;
import com.community.group.entity.GroupCategoryEntity;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupManagerPermission;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupMemberStatus;
import com.community.group.entity.GroupRole;
import com.community.group.entity.GroupStatus;
import com.community.group.entity.GroupVisibility;
import com.community.group.exception.GroupAlreadyInactiveException;
import com.community.group.exception.GroupNotFoundException;
import com.community.group.exception.GroupNotInactiveException;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupPostQueries;
import com.community.group.repository.master.GroupRepository;
import com.community.group.repository.master.GroupRepositoryQDSL;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소모임 개설 승인·소모임 관리(관리자 앱) — admin-web {@code useGroupAdminApi.ts} 머리말 그대로(community api-groups.md 6-1·6-2·11-9).
 *
 * <ul>
 *   <li>권한은 컨트롤러가 선언한다(조회 {@code group.read}, 승인·반려·비활성화·다시 열기 {@code group.manage}).</li>
 *   <li>상태를 바꾸는 일은 소모임 행을 잠그고 상태를 다시 읽는다 — 두 운영자가 동시에 눌러도 한 번만 바뀐다(늦은 쪽은 404 · 409).</li>
 *   <li>감사 {@code GROUP_APPROVE|REJECT|DEACTIVATE|REACTIVATE}(대상 {@code GROUP} = 소모임 ID), 회원 알림 {@code GROUP_APPROVED|REJECTED|DEACTIVATED|REACTIVATED}.</li>
 *   <li>신고 처리의 「소모임 비활성화」는 로보얌 신고 대상에 소모임이 없어 아직 없다 — 생기면 {@link #deactivate} 와 같은 길(사건 ID)을 쓴다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class GroupAdminService {

    private static final Logger log = LoggerFactory.getLogger(GroupAdminService.class);
    static final String TARGET = "GROUP";
    static final int REASON_MAX = 200;
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupRepositoryQDSL queries;
    private final GroupCategoryLookup categories;
    private final GroupPeople people;
    private final GroupSettingsService settings;
    private final GroupNotices notices;
    private final BoardCatalog catalog;
    private final AuditLogService audit;
    private final GroupPostQueries groupPosts;

    public GroupAdminService(GroupRepository groups, GroupMemberRepository members, GroupRepositoryQDSL queries, GroupCategoryLookup categories,
                             GroupPeople people, GroupSettingsService settings, GroupNotices notices, BoardCatalog catalog, AuditLogService audit,
                             GroupPostQueries groupPosts) {
        this.groupPosts = groupPosts;
        this.groups = groups;
        this.members = members;
        this.queries = queries;
        this.categories = categories;
        this.people = people;
        this.settings = settings;
        this.notices = notices;
        this.catalog = catalog;
        this.audit = audit;
    }

    // ── 개설 승인 ────────────────────────────────────────────────────────────

    public GroupOpenRequestListView requests() {
        List<GroupEntity> rows = groups.findByStatusOrderByRequestedAtAscIdAsc(GroupStatus.PENDING);
        GroupCategoryLookup.Snapshot cats = categories.snapshot();
        BoardCatalogSnapshot boards = catalog.snapshot();
        GroupPeople.Members persons = people.members(rows.stream().map(GroupEntity::getCreatorId).toList(), false);
        return new GroupOpenRequestListView(rows.stream().map(g -> new GroupOpenRequestListView.Item(Long.toString(g.getId()), g.getName(), g.getDescription(),
                category(g, cats), board(g, boards), g.getJoinType().name(), g.getCapacity(), g.isOffline(), persons.of(g.getCreatorId()), g.getRequestedAt(),
                g.isStockAgreed(), g.getVisibility().name())).toList());
    }

    /** 승인 → 열린다(신청자가 모임장·첫 멤버). 신청 중이 아니면 404 */
    @Transactional
    public void approve(AdminActor actor, String rawId) {
        GroupEntity group = lock(rawId);
        if (group.getStatus() != GroupStatus.PENDING) {
            throw new GroupNotFoundException();
        }
        Instant now = Instant.now();
        group.approve(actor.operatorId(), now);
        GroupMemberEntity leader = members.findByGroupIdAndUserId(group.getId(), group.getLeaderId()).orElse(null);
        if (leader == null) {
            members.persist(GroupMemberEntity.leader(group.getId(), group.getLeaderId(), now));
        }
        members.flush();
        audit.record(event(actor, AuditActions.GROUP_APPROVE, group).with("name", group.getName())
                .with("requesterId", group.getCreatorId() == null ? "" : Long.toString(group.getCreatorId())));
        notices.approved(group);
        log.info("소모임 개설을 승인했다 groupId={} by operatorId={}", group.getId(), actor.operatorId());
    }

    /** 반려 — 사유 1~200자(신청자에게 그대로). 신청 중이 아니면 404 */
    @Transactional
    public void reject(AdminActor actor, String rawId, GroupAdminReasonRequest request) {
        String reason = requiredReason(request == null ? null : request.reason());
        GroupEntity group = lock(rawId);
        if (group.getStatus() != GroupStatus.PENDING) {
            throw new GroupNotFoundException();
        }
        group.reject(actor.operatorId(), reason, Instant.now());
        audit.record(event(actor, AuditActions.GROUP_REJECT, group).with("name", group.getName()).with("reason", reason));
        notices.rejected(group, reason);
        log.info("소모임 개설을 반려했다 groupId={} by operatorId={}", group.getId(), actor.operatorId());
    }

    // ── 소모임 관리 ──────────────────────────────────────────────────────────

    public GroupAdminListView list(String q, String status, String closed, String offline, String visibility, String page, String size) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        GroupStatus statusFilter = null;
        String statusKey = status == null || status.isBlank() ? "ALL" : status.strip().toUpperCase(Locale.ROOT);
        switch (statusKey) {
            case "ALL" -> {
            }
            case "ACTIVE" -> statusFilter = GroupStatus.ACTIVE;
            case "INACTIVE" -> statusFilter = GroupStatus.INACTIVE;
            default -> invalid.put("status", new MessageRef("group.admin.status.invalid"));
        }
        GroupVisibility visibilityFilter = null;
        String visibilityKey = visibility == null || visibility.isBlank() ? "ALL" : visibility.strip().toUpperCase(Locale.ROOT);
        switch (visibilityKey) {
            case "ALL" -> {
            }
            case "PUBLIC" -> visibilityFilter = GroupVisibility.PUBLIC;
            case "PRIVATE" -> visibilityFilter = GroupVisibility.PRIVATE;
            default -> invalid.put("visibility", new MessageRef("group.admin.visibility.invalid"));
        }
        Integer pageNo = intIn(page, 0, 0, 1_000_000);
        if (pageNo == null) {
            invalid.put("page", new MessageRef("group.list.page.invalid"));
        }
        Integer pageSize = intIn(size, DEFAULT_SIZE, 1, MAX_SIZE);
        if (pageSize == null) {
            invalid.put("size", new MessageRef("group.list.size.invalid", String.valueOf(MAX_SIZE)));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        String keyword = q == null || q.isBlank() ? null : q.strip();
        GroupRepositoryQDSL.AdminFilter filter = new GroupRepositoryQDSL.AdminFilter(keyword, statusFilter, flag(closed), flag(offline), visibilityFilter);
        List<GroupEntity> rows = queries.adminPage(filter, (long) pageNo * pageSize, pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        List<GroupEntity> pageRows = hasNext ? rows.subList(0, pageSize) : rows;
        GroupCategoryLookup.Snapshot cats = categories.snapshot();
        BoardCatalogSnapshot boards = catalog.snapshot();
        GroupPeople.Members persons = people.members(pageRows.stream().map(GroupEntity::getLeaderId).toList(), false);
        List<GroupAdminListView.Row> items = pageRows.stream().map(g -> new GroupAdminListView.Row(Long.toString(g.getId()), g.getName(), category(g, cats),
                board(g, boards), persons.of(g.getLeaderId()), g.getMemberCount(), g.getCapacity(), g.getStatus().name(), GroupRules.recruitState(g),
                g.getJoinType().name(), g.isOffline(), g.isStockAgreed(), g.getVisibility().name(), g.getOpenedAt(), g.getLastActivityAt(),
                g.getStatus() == GroupStatus.INACTIVE ? g.getDeactivatedAt() : null)).toList();
        Map<String, Long> c = queries.adminCounts(keyword);
        return new GroupAdminListView(items, pageNo, pageSize, hasNext, queries.adminCount(filter),
                new GroupAdminListView.Counts(c.get("all"), c.get("active"), c.get("inactive"), c.get("closed"), c.get("offline")),
                groups.countByStatus(GroupStatus.PENDING));
    }

    /** 상세 — 열림·비활성만(그 밖 404) */
    public GroupAdminDetailView detail(String rawId) {
        GroupEntity g = groups.findById(parseId(rawId)).filter(x -> x.getStatus() == GroupStatus.ACTIVE || x.getStatus() == GroupStatus.INACTIVE)
                .orElseThrow(GroupNotFoundException::new);
        List<GroupMemberEntity> managerRows = members.findByGroupIdAndRoleAndStatusOrderByRoleChangedAtAscUserIdAsc(g.getId(), GroupRole.MANAGER,
                GroupMemberStatus.ACTIVE);
        List<Long> userIds = new ArrayList<>(List.of(g.getLeaderId()));
        if (g.getCreatorId() != null) {
            userIds.add(g.getCreatorId());
        }
        if (g.getVisibilityChangedBy() != null) {
            userIds.add(g.getVisibilityChangedBy());
        }
        managerRows.forEach(m -> userIds.add(m.getUserId()));
        GroupPeople.Members persons = people.members(userIds, false);
        Map<Long, GroupPersonView> operators = people.operators(java.util.Arrays.asList(g.getReviewedBy(), g.getDeactivatedBy(), g.getReactivatedBy()));

        long active = 0;
        long inactive = 0;
        long total = 0;
        for (GroupEntity led : groups.findByLeaderIdAndStatusIn(g.getLeaderId(), GroupRepositoryQDSL.LIVE)) {
            total++;
            if (led.getStatus() == GroupStatus.ACTIVE) {
                active++;
            } else if (led.getStatus() == GroupStatus.INACTIVE) {
                inactive++;
            }
        }
        long[] postCounts = groupPosts.adminCounts(g.getId(), java.time.Instant.now()); // 지우지 않은 글 수 · 그중 비멤버에게 보이는 글 수(2단계)
        GroupAdminDetailView.Deactivation deactivation = g.getDeactivatedAt() == null ? null
                : new GroupAdminDetailView.Deactivation(g.getDeactivatedAt(), operators.get(g.getDeactivatedBy()), g.getDeactivateReason(), null,
                g.getReactivatedAt(), operators.get(g.getReactivatedBy()), g.getReactivateMemo());
        List<GroupAdminDetailView.Manager> managers = managerRows.stream()
                .map(m -> new GroupAdminDetailView.Manager(persons.of(m.getUserId()), m.getRoleChangedAt(), GroupManagerPermission.keys(m.getManagerPerms())))
                .toList();
        return new GroupAdminDetailView(Long.toString(g.getId()), g.getName(), g.getDescription(), category(g, categories.snapshot()), board(g, catalog.snapshot()),
                g.getStatus().name(), g.getJoinType().name(), g.isRecruiting(), GroupRules.recruitState(g), g.getCapacity(), g.getMemberCount(), g.isOffline(),
                g.isStockAgreed(), new GroupAdminDetailView.Chat(g.getKakaoUrl() != null, g.getDiscordUrl() != null), persons.of(g.getLeaderId()),
                persons.of(g.getCreatorId()), operators.get(g.getReviewedBy()), g.getRequestedAt(), g.getOpenedAt(), g.getLastActivityAt(),
                new GroupAdminDetailView.LeaderLoad(total, active, inactive, settings.creation().maxPerOwner()), postCounts[0], postCounts[1], 0, deactivation, managers,
                g.getManagerLimit(), settings.manager().limitMax(), g.getVisibility().name(), g.getVisibilityChangedAt(), persons.of(g.getVisibilityChangedBy()));
    }

    /** 비활성화 — 사유 1~200자(모임장에게 그대로). 이미 비활성 409, 열림·비활성이 아니면 404 */
    @Transactional
    public void deactivate(AdminActor actor, String rawId, GroupAdminReasonRequest request) {
        String reason = requiredReason(request == null ? null : request.reason());
        GroupEntity group = lock(rawId);
        if (group.getStatus() == GroupStatus.INACTIVE) {
            throw new GroupAlreadyInactiveException();
        }
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new GroupNotFoundException();
        }
        group.deactivate(actor.operatorId(), reason, null, Instant.now());
        audit.record(event(actor, AuditActions.GROUP_DEACTIVATE, group).with("name", group.getName()).with("reason", reason));
        notices.deactivated(group, reason);
        log.info("소모임을 비활성화했다 groupId={} by operatorId={}", group.getId(), actor.operatorId());
    }

    /** 다시 열기 — 메모 0~200자(운영자만). 이미 열림 409, 열림·비활성이 아니면 404(해산한 소모임은 되살리지 않는다) */
    @Transactional
    public void reactivate(AdminActor actor, String rawId, GroupAdminMemoRequest request) {
        String memo = request == null || request.memo() == null || request.memo().isBlank() ? null : request.memo().strip();
        if (memo != null && memo.codePointCount(0, memo.length()) > REASON_MAX) {
            throw ValidationException.of("memo", new MessageRef("group.admin.memo.max", String.valueOf(REASON_MAX)));
        }
        GroupEntity group = lock(rawId);
        if (group.getStatus() == GroupStatus.ACTIVE) {
            throw new GroupNotInactiveException();
        }
        if (group.getStatus() != GroupStatus.INACTIVE) {
            throw new GroupNotFoundException();
        }
        group.reactivate(actor.operatorId(), memo, Instant.now());
        audit.record(event(actor, AuditActions.GROUP_REACTIVATE, group).with("name", group.getName()).with("memo", memo == null ? "" : memo));
        notices.reactivated(group);
        log.info("소모임을 다시 열었다 groupId={} by operatorId={}", group.getId(), actor.operatorId());
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private GroupEntity lock(String rawId) {
        return groups.findWithLockById(parseId(rawId)).orElseThrow(GroupNotFoundException::new);
    }

    private static long parseId(String raw) {
        try {
            return Long.parseLong(raw == null ? "" : raw.strip());
        } catch (NumberFormatException e) {
            throw new GroupNotFoundException();
        }
    }

    private static String requiredReason(String raw) {
        String reason = raw == null ? null : raw.strip();
        if (reason == null || reason.isEmpty() || reason.codePointCount(0, reason.length()) > REASON_MAX) {
            throw ValidationException.of("reason", new MessageRef("group.admin.reason.length", String.valueOf(REASON_MAX)));
        }
        return reason;
    }

    private static GroupCategoryRefView category(GroupEntity g, GroupCategoryLookup.Snapshot cats) {
        GroupCategoryEntity cat = cats.ofBoard(g.getBoardId());
        return cat == null ? null : new GroupCategoryRefView(Long.toString(cat.getId()), cat.getName());
    }

    private static GroupBoardRefView board(GroupEntity g, BoardCatalogSnapshot boards) {
        return boards.board(g.getBoardId()).map(b -> new GroupBoardRefView(b.slug(), b.name(), b.nameEn()))
                .orElse(new GroupBoardRefView(null, "", null));
    }

    private static boolean flag(String raw) {
        return raw != null && ("true".equalsIgnoreCase(raw.strip()) || "1".equals(raw.strip()));
    }

    private static Integer intIn(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.strip());
            return value < min || value > max ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static AuditEvent event(AdminActor actor, String action, GroupEntity group) {
        return AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target(TARGET, Long.toString(group.getId()));
    }
}
