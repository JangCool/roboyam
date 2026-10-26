package com.community.group.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.group.dto.GroupRuleValues;
import com.community.group.dto.response.GroupCategoriesView;
import com.community.group.dto.response.GroupCreationRulesView;
import com.community.group.dto.response.GroupDetailView;
import com.community.group.dto.response.GroupListPageView;
import com.community.group.dto.response.GroupMemberPageView;
import com.community.group.dto.response.GroupRowView;
import com.community.group.dto.response.MyGroupsView;
import com.community.group.entity.GroupCategoryEntity;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupManagerPermission;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupMemberStatus;
import com.community.group.entity.GroupRole;
import com.community.group.entity.GroupStatus;
import com.community.group.exception.GroupNotFoundException;
import com.community.group.exception.GroupNotMemberException;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupPostQueries;
import com.community.group.repository.master.GroupRepository;
import com.community.group.repository.master.GroupRepositoryQDSL;
import com.community.permission.entity.MemberPermission;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소모임 읽기(공개 사이트) — community api-groups.md 2절(분류·목록·상세·멤버 탭) · 4절(개설 규칙 안내 · 내 소모임). 「지금 뜨는 소모임」은 6단계.
 * 목록·검색에는 열린 소모임만 나온다. 상세의 비활성은 안내 칸만, 신청 중·반려·해산·없는 ID 는 404.
 */
@Service
@Transactional(readOnly = true)
public class GroupQueryService {

    static final int LIST_DEFAULT_SIZE = 20;
    static final int LIST_MAX_SIZE = 50;
    static final int MEMBER_DEFAULT_SIZE = 30;
    static final int MEMBER_MAX_SIZE = 50;
    static final int Q_MAX = 30;
    static final int EXCLUDE_MAX = 5;
    static final Set<String> SORTS = Set.of("popular", "new", "recruiting", "mine");

    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupRepositoryQDSL queries;
    private final GroupPostQueries groupPosts;
    private final GroupViews views;
    private final GroupPeople people;
    private final GroupSettingsService settings;
    private final GroupAccess access;
    private final BoardActorResolver actors;
    private final BoardCatalog catalog;

    public GroupQueryService(GroupRepository groups, GroupMemberRepository members, GroupRepositoryQDSL queries, GroupViews views, GroupPeople people,
                             GroupSettingsService settings, GroupAccess access, BoardActorResolver actors,
                             BoardCatalog catalog, GroupPostQueries groupPosts) {
        this.groupPosts = groupPosts;
        this.groups = groups;
        this.members = members;
        this.queries = queries;
        this.views = views;
        this.people = people;
        this.settings = settings;
        this.access = access;
        this.actors = actors;
        this.catalog = catalog;
    }

    // ── 분류 ─────────────────────────────────────────────────────────────────

    /** 켜진 소모임 게시판이 하나 이상 할당된 분류만 — 소모임 수는 꺼진 게시판까지 센 열린 소모임 */
    public GroupCategoriesView categories() {
        GroupViews.Context ctx = views.context();
        Map<Long, Long> counts = queries.countByBoard(List.of(GroupStatus.ACTIVE));
        List<GroupCategoriesView.Item> items = new ArrayList<>();
        for (GroupCategoryEntity cat : ctx.categories().ordered()) {
            List<GroupCategoriesView.Board> boards = new ArrayList<>();
            long groupCount = 0;
            for (Long boardId : ctx.categories().boards(cat.getId())) {
                groupCount += counts.getOrDefault(boardId, 0L);
                EffectiveBoard board = ctx.boards().board(boardId).orElse(null);
                if (board != null && board.active() && BoardFields.KIND_GROUP.equals(board.kind())) {
                    boards.add(new GroupCategoriesView.Board(board.slug(), board.name(), board.nameEn(), board.investmentNotice()));
                }
            }
            if (!boards.isEmpty()) {
                items.add(new GroupCategoriesView.Item(Long.toString(cat.getId()), cat.getName(), groupCount,
                        boards.stream().anyMatch(GroupCategoriesView.Board::investmentNotice), List.copyOf(boards)));
            }
        }
        return new GroupCategoriesView(groups.countByStatus(GroupStatus.ACTIVE), List.copyOf(items));
    }

    // ── 목록·검색 ────────────────────────────────────────────────────────────

    public GroupListPageView list(String category, String sort, String q, String page, String size, String exclude, Long userId) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        GroupViews.Context ctx = views.context();
        List<Long> boardIds = null;
        if (category != null && !category.isBlank()) {
            Long id = parseLong(category);
            if (id == null || !ctx.categories().byId().containsKey(id)) {
                invalid.put("category", new MessageRef("group.list.category.invalid"));
            } else {
                boardIds = ctx.categories().boards(id);
            }
        }
        String sortKey = sort == null || sort.isBlank() ? "popular" : sort.strip();
        if (!SORTS.contains(sortKey)) {
            invalid.put("sort", new MessageRef("group.list.sort.invalid"));
        }
        String keyword = q == null || q.isBlank() ? null : q.strip();
        if (keyword != null && keyword.codePointCount(0, keyword.length()) > Q_MAX) {
            invalid.put("q", new MessageRef("group.list.q.max", String.valueOf(Q_MAX)));
        }
        Integer pageNo = intIn(page, 1, 1, 100_000);
        if (pageNo == null) {
            invalid.put("page", new MessageRef("group.list.page.invalid"));
        }
        Integer pageSize = intIn(size, LIST_DEFAULT_SIZE, 1, LIST_MAX_SIZE);
        if (pageSize == null) {
            invalid.put("size", new MessageRef("group.list.size.invalid", String.valueOf(LIST_MAX_SIZE)));
        }
        List<Long> excluded = new ArrayList<>();
        if (exclude != null && !exclude.isBlank()) {
            boolean bad = false;
            for (String part : exclude.split(",")) {
                Long id = parseLong(part);
                if (id == null) {
                    bad = true;
                } else {
                    excluded.add(id);
                }
            }
            if (bad || excluded.size() > EXCLUDE_MAX) {
                invalid.put("exclude", new MessageRef("group.list.exclude.invalid", String.valueOf(EXCLUDE_MAX)));
            }
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        if ("mine".equals(sortKey) && userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        GroupRepositoryQDSL.PublicFilter filter = new GroupRepositoryQDSL.PublicFilter(boardIds, keyword, "recruiting".equals(sortKey),
                "mine".equals(sortKey) ? userId : null, excluded);
        long offset = (long) (pageNo - 1) * pageSize;
        List<GroupEntity> rows = queries.publicPage(filter, sortKey, offset, pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        List<GroupEntity> pageRows = hasNext ? rows.subList(0, pageSize) : rows;
        long total = queries.publicCount(filter);
        return new GroupListPageView(rows(pageRows, userId, ctx), pageNo, hasNext, total);
    }

    /** 줄들 — 내 상태는 한 번에 읽는다 */
    List<GroupRowView> rows(List<GroupEntity> rows, Long userId, GroupViews.Context ctx) {
        Map<Long, GroupMemberEntity> mine = new HashMap<>();
        if (userId != null && !rows.isEmpty()) {
            members.findByUserIdAndGroupIdIn(userId, rows.stream().map(GroupEntity::getId).toList()).forEach(m -> mine.put(m.getGroupId(), m));
        }
        return rows.stream().map(g -> views.row(g, GroupViews.myState(g, mine.get(g.getId()), userId), ctx)).toList();
    }

    // ── 상세 · 멤버 ──────────────────────────────────────────────────────────

    public GroupDetailView detail(String rawId, Long userId) {
        GroupEntity group = groups.findById(GroupAccess.parseId(rawId))
                .filter(g -> g.getStatus() == GroupStatus.ACTIVE || g.getStatus() == GroupStatus.INACTIVE).orElseThrow(GroupNotFoundException::new);
        BoardActor actor = userId == null ? null : actors.resolve(userId, catalog.snapshot());
        Long viewer = actor == null || actor.guest() ? null : userId;
        return views.detail(group, viewer, viewer == null ? null : actor, Instant.now());
    }

    /** 멤버 탭 — 멤버만(비회원 401, 비멤버 403 {@code GROUP_NOT_MEMBER}) */
    public GroupMemberPageView members(String rawId, Long userId, String page, String size) {
        long id = GroupAccess.parseId(rawId);
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.active(id);
        boolean leader = group.getLeaderId().equals(me);
        GroupMemberEntity mine = access.membership(id, me).orElse(null);
        if (!leader && mine == null) {
            throw new GroupNotMemberException();
        }
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        Integer pageNo = intIn(page, 1, 1, 100_000);
        if (pageNo == null) {
            invalid.put("page", new MessageRef("group.list.page.invalid"));
        }
        Integer pageSize = intIn(size, MEMBER_DEFAULT_SIZE, 1, MEMBER_MAX_SIZE);
        if (pageSize == null) {
            invalid.put("size", new MessageRef("group.list.size.invalid", String.valueOf(MEMBER_MAX_SIZE)));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        List<GroupMemberEntity> rows = queries.memberPage(id, (long) (pageNo - 1) * pageSize, pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        List<GroupMemberEntity> pageRows = hasNext ? rows.subList(0, pageSize) : rows;
        GroupPeople.Members persons = people.members(pageRows.stream().map(GroupMemberEntity::getUserId).toList(), true);
        List<GroupMemberPageView.Item> items = pageRows.stream().map(m -> {
            boolean showPerms = m.getRole() == GroupRole.MANAGER && (leader || m.getUserId().equals(me));
            return new GroupMemberPageView.Item(GroupUsers.of(persons.of(m.getUserId())), m.getRole().name(), m.getJoinedAt(),
                    showPerms ? GroupManagerPermission.keys(m.getManagerPerms()) : null);
        }).toList();
        return new GroupMemberPageView(items, pageNo, hasNext, members.countByGroupIdAndStatus(id, GroupMemberStatus.ACTIVE));
    }

    // ── 개설 규칙 안내 ───────────────────────────────────────────────────────

    public GroupCreationRulesView creationRules(Long userId) {
        GroupViews.Context ctx = views.context();
        GroupRuleValues.Creation rules = settings.creation();
        GroupRuleValues.Policy policy = settings.policy();
        String minLabel = ctx.boards().gradeLabel(rules.minGrade());
        if (minLabel == null) {
            minLabel = rules.minGrade();
        }
        GroupCreationRulesView.Me me = null;
        if (userId != null) {
            BoardActor actor = actors.resolve(userId, ctx.boards());
            if (!actor.guest()) {
                long led = groups.countByLeaderIdAndStatusIn(userId, GroupRepositoryQDSL.LIVE);
                String reason = creationBlocked(actor, rules, led, ctx);
                me = new GroupCreationRulesView.Me(reason == null, reason, actor.gradeKey(), led);
            }
        }
        return new GroupCreationRulesView(rules.creationMode().name(), rules.minGrade(), minLabel, rules.maxPerOwner(), me, policy.capacityMin(),
                policy.capacityMax(), settings.visibility().visibilityEnabled());
    }

    /** 개설 규칙으로 못 내는 까닭(낼 수 있으면 null) — 회원 권한 → 최소 등급 → 1인 상한 */
    static String creationBlocked(BoardActor actor, GroupRuleValues.Creation rules, long ledCount, GroupViews.Context ctx) {
        if (!actor.can(MemberPermission.GROUP_CREATE.code())) {
            return "NOT_PERMITTED";
        }
        Integer required = ctx.boards().gradeLevel(rules.minGrade());
        if (required != null && actor.gradeLevel() < required) {
            return "GRADE_TOO_LOW";
        }
        return ledCount >= rules.maxPerOwner() ? "LIMIT_REACHED" : null;
    }

    // ── 내 소모임 ────────────────────────────────────────────────────────────

    public MyGroupsView mine(Long userId) {
        long me = GroupAccess.requireUser(userId);
        GroupViews.Context ctx = views.context();
        List<GroupMemberEntity> rows = members.findByUserIdAndStatusIn(me, List.of(GroupMemberStatus.ACTIVE, GroupMemberStatus.APPLIED));
        Map<Long, GroupEntity> byId = new HashMap<>();
        groups.findAllById(rows.stream().map(GroupMemberEntity::getGroupId).toList()).forEach(g -> byId.put(g.getId(), g));
        // 새 글 수(2단계) — 가입한 소모임 모두를 질의 한 번으로(마지막으로 본 뒤 올라온 남의 글)
        Map<Long, Long> newPosts = groupPosts.newPostCounts(me, rows.stream().filter(GroupMemberEntity::active).map(GroupMemberEntity::getGroupId).toList(),
                Instant.now());
        List<MyGroupsView.Joined> joined = new ArrayList<>();
        List<MyGroupsView.Application> applications = new ArrayList<>();
        rows.stream().sorted((a, b) -> compareDesc(a.getStatus() == GroupMemberStatus.ACTIVE ? a.getJoinedAt() : a.getAppliedAt(),
                b.getStatus() == GroupMemberStatus.ACTIVE ? b.getJoinedAt() : b.getAppliedAt())).forEach(m -> {
            GroupEntity g = byId.get(m.getGroupId());
            if (g == null || g.getStatus() != GroupStatus.ACTIVE) {
                return;
            }
            GroupRowView row = views.row(g, GroupViews.myState(g, m, me), ctx);
            if (m.getStatus() == GroupMemberStatus.ACTIVE) {
                joined.add(new MyGroupsView.Joined(row, m.getRole().name(), m.getJoinedAt(), newPosts.getOrDefault(g.getId(), 0L).intValue(), null));
            } else {
                applications.add(new MyGroupsView.Application(row, m.getAppliedAt()));
            }
        });
        List<MyGroupsView.Request> requests = groups.findByCreatorIdAndStatusInOrderByRequestedAtDesc(me, List.of(GroupStatus.PENDING, GroupStatus.REJECTED))
                .stream().map(g -> new MyGroupsView.Request(Long.toString(g.getId()), g.getName(), GroupViews.category(g, ctx),
                        GroupViews.boardRef(ctx.boards().board(g.getBoardId()).orElse(null)), g.getJoinType().name(), g.getCapacity(), g.getDescription(),
                        g.isOffline(), g.getVisibility().name(), g.getStatus().name(), g.getRejectReason(), g.getRequestedAt(), g.getReviewedAt())).toList();
        return new MyGroupsView(List.copyOf(joined), List.copyOf(applications), requests);
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private static int compareDesc(Instant a, Instant b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return b.compareTo(a);
    }

    static Long parseLong(String raw) {
        try {
            return raw == null ? null : Long.parseLong(raw.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 정수 쿼리 — 비면 기본값, 숫자가 아니거나 범위 밖이면 null */
    static Integer intIn(String raw, int fallback, int min, int max) {
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
}
