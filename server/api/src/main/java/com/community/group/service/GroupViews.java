package com.community.group.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.group.dto.GroupRuleValues;
import com.community.group.dto.response.GroupBoardRefView;
import com.community.group.dto.response.GroupCategoryRefView;
import com.community.group.dto.response.GroupDetailView;
import com.community.group.dto.response.GroupPersonView;
import com.community.group.dto.response.GroupRowView;
import com.community.group.entity.GroupCategoryEntity;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupJoinType;
import com.community.group.entity.GroupManagerPermission;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupMemberStatus;
import com.community.group.entity.GroupRole;
import com.community.group.entity.GroupStatus;
import com.community.group.entity.GroupVisibility;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupPostQueries;
import com.community.group.repository.master.GroupRepositoryQDSL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 공개 사이트 소모임 응답 조립 — 소모임 행 + 분류(게시판이 할당된 분류) + 게시판 사본 + 설정(공개 범위 켜짐·운영 한도·매니저 규칙) + 보는 사람의 멤버 행.
 * 여러 출처를 합치므로 매퍼가 아니라 여기서 만든다. 부르는 서비스의 트랜잭션 안에서 돈다.
 */
@Component
public class GroupViews {

    public static final String STATE_NONE = "NONE";
    public static final String STATE_MEMBER = "MEMBER";
    public static final String STATE_LEADER = "LEADER";
    public static final String STATE_APPLIED = "APPLIED";
    public static final String STATE_REJECTED = "REJECTED";
    public static final String STATE_KICKED = "KICKED";

    static final String METHOD_JOIN = "JOIN";
    static final String METHOD_APPLY = "APPLY";
    static final int NEWCOMER_ITEMS = 5;

    private final BoardCatalog catalog;
    private final GroupCategoryLookup categories;
    private final GroupSettingsService settings;
    private final GroupPeople people;
    private final GroupRepositoryQDSL queries;
    private final GroupMemberRepository members;
    private final GroupPostQueries groupPosts;

    public GroupViews(BoardCatalog catalog, GroupCategoryLookup categories, GroupSettingsService settings, GroupPeople people, GroupRepositoryQDSL queries,
                      GroupMemberRepository members, GroupPostQueries groupPosts) {
        this.groupPosts = groupPosts;
        this.catalog = catalog;
        this.categories = categories;
        this.settings = settings;
        this.people = people;
        this.queries = queries;
        this.members = members;
    }

    /** 한 응답이 같이 쓰는 사본 — 게시판·분류·공개 범위 켜짐 */
    public record Context(BoardCatalogSnapshot boards, GroupCategoryLookup.Snapshot categories, boolean visibilityEnabled) {
    }

    public Context context() {
        return new Context(catalog.snapshot(), categories.snapshot(), settings.visibility().visibilityEnabled());
    }

    // ── 한 줄 ────────────────────────────────────────────────────────────────

    public GroupRowView row(GroupEntity g, String myState, Context ctx) {
        EffectiveBoard board = ctx.boards().board(g.getBoardId()).orElse(null);
        return new GroupRowView(Long.toString(g.getId()), g.getName(), GroupRules.summary(g.getDescription()), category(g, ctx), boardRef(board),
                g.getJoinType().name(), g.getCapacity(), g.getMemberCount(), g.isRecruiting(), GroupRules.recruitState(g), g.isOffline(),
                board != null && board.investmentNotice(), visibility(g, ctx), myState);
    }

    public static GroupCategoryRefView category(GroupEntity g, Context ctx) {
        GroupCategoryEntity cat = ctx.categories().ofBoard(g.getBoardId());
        return cat == null ? null : new GroupCategoryRefView(Long.toString(cat.getId()), cat.getName());
    }

    public static GroupBoardRefView boardRef(EffectiveBoard board) {
        return board == null ? null : new GroupBoardRefView(board.slug(), board.name(), board.nameEn());
    }

    /** 공개 소모임은 늘 PUBLIC, 비공개 소모임은 공개 범위 기능이 켜져 있을 때만 PRIVATE(꺼져 있으면 null — 화면은 칩을 그리지 않는다) */
    public static String visibility(GroupEntity g, Context ctx) {
        if (g.getVisibility() == GroupVisibility.PUBLIC) {
            return GroupVisibility.PUBLIC.name();
        }
        return ctx.visibilityEnabled() ? GroupVisibility.PRIVATE.name() : null;
    }

    /** 내 상태 — 비로그인 null */
    public static String myState(GroupEntity g, GroupMemberEntity row, Long userId) {
        if (userId == null) {
            return null;
        }
        if (g.getLeaderId().equals(userId)) {
            return STATE_LEADER;
        }
        if (row == null) {
            return STATE_NONE;
        }
        return switch (row.getStatus()) {
            case ACTIVE -> STATE_MEMBER;
            case APPLIED -> STATE_APPLIED;
            case REJECTED -> STATE_REJECTED;
            case KICKED -> STATE_KICKED;
            case LEFT -> STATE_NONE;
        };
    }

    // ── 상세 ─────────────────────────────────────────────────────────────────

    /**
     * @param actor 보는 사람(비회원이면 null)
     */
    public GroupDetailView detail(GroupEntity g, Long userId, BoardActor actor, Instant now) {
        if (g.getStatus() == GroupStatus.INACTIVE) {
            return GroupDetailView.inactive(Long.toString(g.getId()), g.getName());
        }
        Context ctx = context();
        GroupMemberEntity row = userId == null ? null : members.findByGroupIdAndUserId(g.getId(), userId).orElse(null);
        boolean leader = userId != null && g.getLeaderId().equals(userId);
        boolean member = leader || (row != null && row.active());
        String myRole = !member ? null : leader ? GroupRole.LEADER.name() : row.getRole() == GroupRole.MANAGER ? GroupRole.MANAGER.name() : GroupRole.MEMBER.name();
        boolean manager = GroupRole.MANAGER.name().equals(myRole);
        GroupRuleValues.Policy policy = settings.policy();
        GroupRuleValues.Manager managerRules = settings.manager();

        EffectiveBoard board = ctx.boards().board(g.getBoardId()).orElse(null);
        GroupDetailView.Board boardView = board == null ? null : new GroupDetailView.Board(board.slug(), board.name(), board.nameEn(), board.color(),
                board.investmentNotice(), board.activePrefixes().stream().map(GroupViews::prefix).toList());

        GroupPeople.Members persons = people.members(List.of(g.getLeaderId()), true);
        String blocked = (leader || manager) ? GroupAccess.writeBlocked(actor) : null;
        GroupDetailView.Permissions permissions = new GroupDetailView.Permissions(leader || manager, member && (actor == null || !actor.restricted()), leader,
                leader && managerRules.managersEnabled(), GroupAccess.can(g, row, userId, GroupManagerPermission.APPLICATIONS),
                GroupAccess.can(g, row, userId, GroupManagerPermission.KICK), GroupAccess.can(g, row, userId, GroupManagerPermission.RECRUITING),
                GroupAccess.can(g, row, userId, GroupManagerPermission.EVENTS), GroupAccess.can(g, row, userId, GroupManagerPermission.UNPUBLISH), blocked);

        GroupDetailView.Chat chat = null;
        if (member) {
            List<GroupDetailView.ChatLink> links = new ArrayList<>();
            if (g.getKakaoUrl() != null) {
                links.add(new GroupDetailView.ChatLink("KAKAO", g.getKakaoUrl(), g.getKakaoLabel()));
            }
            if (g.getDiscordUrl() != null) {
                links.add(new GroupDetailView.ChatLink("DISCORD", g.getDiscordUrl(), g.getDiscordLabel()));
            }
            chat = new GroupDetailView.Chat(List.copyOf(links));
        }

        GroupDetailView.ManagerInfo managerInfo = null;
        GroupDetailView.Newcomers newcomers = null;
        if (member) {
            int managerCount = members.findByGroupIdAndRoleAndStatusOrderByRoleChangedAtAscUserIdAsc(g.getId(), GroupRole.MANAGER, GroupMemberStatus.ACTIVE).size();
            managerInfo = new GroupDetailView.ManagerInfo(managerCount, Math.min(g.getManagerLimit(), managerRules.limitMax()), managerRules.limitMax());
            int days = Math.max(1, settings.properties().newcomerDays());
            Instant since = now.minus(Duration.ofDays(days));
            long count = queries.newcomerCount(g.getId(), since);
            List<com.community.dm.dto.response.DmPartnerView> items = List.of();
            if (count > 0) {
                List<GroupMemberEntity> recent = queries.newcomers(g.getId(), since, NEWCOMER_ITEMS);
                GroupPeople.Members newcomerPeople = people.members(recent.stream().map(GroupMemberEntity::getUserId).toList(), true);
                items = recent.stream().map(m -> GroupUsers.of(newcomerPeople.of(m.getUserId()))).toList();
            }
            newcomers = new GroupDetailView.Newcomers(days, count, items);
        }
        Long pending = GroupAccess.can(g, row, userId, GroupManagerPermission.APPLICATIONS)
                ? members.countByGroupIdAndStatus(g.getId(), GroupMemberStatus.APPLIED) : null;
        // 글 수(2단계) — 멤버는 모든 보이는 글 · 그 밖은 공개로 전환된 글, 새 글 수는 멤버에게만(남의 글, 마지막으로 본 뒤)
        long postCount = groupPosts.count(g.getId(), !member, now);
        Long newPostCount = member ? groupPosts.newPostCounts(userId, List.of(g.getId()), now).getOrDefault(g.getId(), 0L) : null;

        return new GroupDetailView(Long.toString(g.getId()), g.getName(), g.getDescription(), category(g, ctx), g.getJoinType().name(), g.getCapacity(),
                g.getMemberCount(), g.isRecruiting(), GroupRules.recruitState(g), g.isOffline(), visibility(g, ctx), boardView, g.getStatus().name(), g.getOpenedAt(),
                GroupUsers.of(persons.of(g.getLeaderId())), board != null && board.investmentNotice(), myState(g, row, userId), myRole, join(g, row, userId, actor, now, policy),
                chat, !member, permissions, managerInfo, newcomers, pending,
                new GroupDetailView.Limits(policy.capacityMin(), policy.capacityMax(), policy.eventMaxDaysAhead(), policy.eventMaxLengthDays()), postCount, newPostCount);
    }

    private static GroupDetailView.Prefix prefix(BoardPrefixInfo p) {
        return new GroupDetailView.Prefix(Long.toString(p.id()), p.name(), p.staffOnly(), p.common(), p.color());
    }

    /** 가입 단추 판정 — 가입 API 의 거절 차례와 같다(로그인 → 이메일 인증 → 정지 → 이미 멤버 → 이미 신청 → 마감 → 정원 → 재신청 대기) */
    public static GroupDetailView.Join join(GroupEntity g, GroupMemberEntity row, Long userId, BoardActor actor, Instant now, GroupRuleValues.Policy policy) {
        String method = method(g, row);
        if (userId == null || actor == null || actor.guest()) {
            return new GroupDetailView.Join(false, method, "LOGIN_REQUIRED", null);
        }
        String blocked = GroupAccess.writeBlocked(actor);
        if (blocked != null) {
            return new GroupDetailView.Join(false, method, blocked, null);
        }
        if (g.getLeaderId().equals(userId) || (row != null && row.active())) {
            return new GroupDetailView.Join(false, method, "ALREADY_MEMBER", null);
        }
        if (row != null && row.getStatus() == GroupMemberStatus.APPLIED) {
            return new GroupDetailView.Join(false, method, "ALREADY_APPLIED", null);
        }
        if (!g.isRecruiting()) {
            return new GroupDetailView.Join(false, method, "CLOSED", null);
        }
        if (g.full()) {
            return new GroupDetailView.Join(false, method, "FULL", null);
        }
        long wait = reapplyWaitSeconds(row, now, policy);
        if (wait > 0) {
            return new GroupDetailView.Join(false, method, "REAPPLY_LATER", wait);
        }
        return new GroupDetailView.Join(true, method, null, null);
    }

    /** 신청제이거나 강퇴됐던 회원은 신청, 아니면 바로 가입 */
    public static String method(GroupEntity g, GroupMemberEntity row) {
        return g.getJoinType() == GroupJoinType.APPROVAL || (row != null && row.isKickedBefore()) ? METHOD_APPLY : METHOD_JOIN;
    }

    /** 가입 신청이 거절된 지 재신청 대기 일수가 지나지 않았으면 남은 초, 아니면 0 */
    public static long reapplyWaitSeconds(GroupMemberEntity row, Instant now, GroupRuleValues.Policy policy) {
        if (row == null || row.getStatus() != GroupMemberStatus.REJECTED || row.getDecidedAt() == null || policy.reapplyDays() <= 0) {
            return 0;
        }
        Instant until = row.getDecidedAt().plus(Duration.ofDays(policy.reapplyDays()));
        return until.isAfter(now) ? Math.max(1, Duration.between(now, until).toSeconds()) : 0;
    }
}
