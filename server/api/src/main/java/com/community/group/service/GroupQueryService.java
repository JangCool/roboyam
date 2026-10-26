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
import com.community.group.dto.response.GroupTrendingView;
import com.community.group.dto.response.MyGroupsView;
import com.community.group.dto.response.ProfileGroupsView;
import com.community.group.entity.GroupCategoryEntity;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupEventEntity;
import com.community.group.entity.GroupManagerPermission;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupMemberStatus;
import com.community.group.entity.GroupRole;
import com.community.group.entity.GroupStatus;
import com.community.group.exception.GroupNotFoundException;
import com.community.group.exception.GroupNotMemberException;
import com.community.group.repository.master.GroupEventQueries;
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
 * 소모임 읽기(공개 사이트) — community api-groups.md 2절(분류·목록·상세·멤버 탭) · 4절(개설 규칙 안내 · 내 소모임). 「지금 뜨는 소모임」(2절 trending — 6단계) · 회원 프로필의 소모임 탭(10절).
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
    /** 목록 {@code exclude} 상한 — 「지금 뜨는 소모임」 개수 설정의 최대(trendingSize 1~20)와 같다(원본은 5 — 설정 최대가 20이라 로보얌은 20, 6단계) */
    static final int EXCLUDE_MAX = 20;
    /** 프로필 소모임 탭에 보이는 수 상한(원본 PROFILE_GROUPS_SHOWN) */
    static final int PROFILE_GROUPS_SHOWN = 50;
    /** 지금 뜨는 소모임 후보를 최근 활동 순으로 이만큼만 읽어 무작위로 고른다(원본 설정 trending-candidates 기본값 — 화면 설정 없음) */
    static final int TRENDING_CANDIDATES = 200;
    static final Set<String> SORTS = Set.of("popular", "new", "recruiting", "mine");

    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupRepositoryQDSL queries;
    private final GroupPostQueries groupPosts;
    private final GroupEventQueries groupEvents;
    private final GroupViews views;
    private final GroupPeople people;
    private final GroupSettingsService settings;
    private final GroupAccess access;
    private final BoardActorResolver actors;
    private final BoardCatalog catalog;
    private final GroupTrendingPicker trendingPicker;

    public GroupQueryService(GroupRepository groups, GroupMemberRepository members, GroupRepositoryQDSL queries, GroupViews views, GroupPeople people,
                             GroupSettingsService settings, GroupAccess access, BoardActorResolver actors,
                             BoardCatalog catalog, GroupPostQueries groupPosts, GroupEventQueries groupEvents, GroupTrendingPicker trendingPicker) {
        this.trendingPicker = trendingPicker;
        this.groupPosts = groupPosts;
        this.groupEvents = groupEvents;
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

    // ── 지금 뜨는 소모임 ─────────────────────────────────────────────────────

    /**
     * 지금 뜨는 소모임(community api-groups.md 2절) — 열려 있고 모집 중이며 정원이 남았고 최근 {@code trendingActiveDays}일 안에 활동이 있는 소모임을 최근 활동 순으로
     * {@value #TRENDING_CANDIDATES}개까지 읽어 활동·규모로 가중한 무작위로 {@code trendingSize}개(관리자 설정 — 운영 한도, 요청마다 읽는다). 후보가 모자라면 있는 만큼, 없으면 빈 목록.
     */
    public GroupTrendingView trending(Long userId) {
        GroupRuleValues.Policy policy = settings.policy();
        Instant now = Instant.now();
        List<GroupEntity> candidates = queries.trendingCandidates(now.minus(java.time.Duration.ofDays(policy.trendingActiveDays())), TRENDING_CANDIDATES);
        List<GroupEntity> picked = trendingPicker.pick(candidates, policy.trendingSize(), policy.trendingActiveDays(), now);
        return new GroupTrendingView(rows(picked, userId, views.context()));
    }

    // ── 회원 프로필의 소모임 탭 ──────────────────────────────────────────────

    /**
     * 한 회원이 지금 멤버인 열린(ACTIVE) 소모임 — 가입이 최근인 순으로 {@value #PROFILE_GROUPS_SHOWN}개까지(community api-profile.md 「소모임 (6-4)」). 신청 중·나감·강퇴·해산·비활성은
     * 나오지 않는다. 소모임은 공개 범위와 상관없이 목록에 공개되는 모임이라 남이 보는 프로필과 내 프로필이 같은 목록이다(원본 규칙). 회원 확인은 부르는 쪽({@code UserProfileService}).
     *
     * @param viewerId 보는 회원(비회원 null) — 줄의 {@code myState}
     */
    public ProfileGroupsView profileGroups(long ownerId, Long viewerId) {
        List<GroupMemberEntity> rows = new ArrayList<>(members.findByUserIdAndStatusIn(ownerId, List.of(GroupMemberStatus.ACTIVE)));
        rows.sort((a, b) -> {
            int byJoined = compareDesc(a.getJoinedAt(), b.getJoinedAt());
            return byJoined != 0 ? byJoined : Long.compare(b.getGroupId(), a.getGroupId());
        });
        Map<Long, GroupEntity> byId = new HashMap<>();
        groups.findAllById(rows.stream().map(GroupMemberEntity::getGroupId).toList()).forEach(g -> byId.put(g.getId(), g));
        List<GroupMemberEntity> shown = new ArrayList<>();
        List<GroupEntity> shownGroups = new ArrayList<>();
        for (GroupMemberEntity m : rows) {
            GroupEntity g = byId.get(m.getGroupId());
            if (g == null || g.getStatus() != GroupStatus.ACTIVE) {
                continue;
            }
            shown.add(m);
            shownGroups.add(g);
            if (shown.size() == PROFILE_GROUPS_SHOWN) {
                break;
            }
        }
        List<GroupRowView> groupRows = rows(shownGroups, viewerId, views.context());
        List<ProfileGroupsView.Row> items = new ArrayList<>(shown.size());
        for (int i = 0; i < shown.size(); i++) {
            GroupMemberEntity m = shown.get(i);
            GroupEntity g = shownGroups.get(i);
            String role = g.getLeaderId().equals(ownerId) ? GroupRole.LEADER.name() : m.getRole() == GroupRole.MANAGER ? GroupRole.MANAGER.name() : GroupRole.MEMBER.name();
            items.add(new ProfileGroupsView.Row(groupRows.get(i), role, m.getJoinedAt()));
        }
        return new ProfileGroupsView(List.copyOf(items));
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
        List<Long> joinedIds = rows.stream().filter(GroupMemberEntity::active).map(GroupMemberEntity::getGroupId).toList();
        Instant now = Instant.now();
        Map<Long, Long> newPosts = groupPosts.newPostCounts(me, joinedIds, now);
        // 다음 일정(4단계) — 가입한 소모임 모두의 가장 가까운 시작 전 일정을 질의 한 번으로
        Map<Long, GroupEventEntity> nextEvents = groupEvents.nextEvents(joinedIds, now);
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
                GroupEventEntity next = nextEvents.get(g.getId());
                joined.add(new MyGroupsView.Joined(row, m.getRole().name(), m.getJoinedAt(), newPosts.getOrDefault(g.getId(), 0L).intValue(),
                        next == null ? null : new MyGroupsView.NextEvent(Long.toString(next.getId()), next.getTitle(), next.getStartsAt())));
            } else {
                applications.add(new MyGroupsView.Application(row, m.getAppliedAt()));
            }
        });
        List<MyGroupsView.Request> requests = groups.findByCreatorIdAndStatusInOrderByRequestedAtDesc(me, List.of(GroupStatus.PENDING, GroupStatus.REJECTED))
                .stream().map(g -> new MyGroupsView.Request(Long.toString(g.getId()), g.getName(), GroupViews.category(g, ctx),
                        GroupViews.boardRef(ctx.boards().board(g.getBoardId()).orElse(null)), g.getJoinType().name(), g.getCapacity(), g.getDescription(),
                        g.isOffline(), GroupViews.visibility(g, ctx), g.getStatus().name(), g.getRejectReason(), g.getRequestedAt(), g.getReviewedAt())).toList();
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
