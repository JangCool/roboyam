package com.community.group.repository.master;

import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupMemberStatus;
import com.community.group.entity.GroupRole;
import com.community.group.entity.GroupSettingHistoryEntity;
import com.community.group.entity.GroupSettingSection;
import com.community.group.entity.GroupStatus;
import com.community.group.entity.GroupVisibility;
import com.community.group.entity.QGroupEntity;
import com.community.group.entity.QGroupMemberEntity;
import com.community.group.entity.QGroupSettingHistoryEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.user.entity.QProfileEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 소모임 목록·검색·집계 — 정렬·페이징은 DB 에서 한다. 검색은 {@code lower(칸) LIKE '%단어%'}(V90 의 pg_trgm 색인과 같은 식).
 */
@Repository
public class GroupRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QGroupEntity GROUP = QGroupEntity.groupEntity;
    private static final QGroupMemberEntity MEMBER = QGroupMemberEntity.groupMemberEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;
    private static final QGroupSettingHistoryEntity HISTORY = QGroupSettingHistoryEntity.groupSettingHistoryEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;

    /** 관리 대상(관리자 목록) — 열림 · 비활성 */
    public static final List<GroupStatus> MANAGED = List.of(GroupStatus.ACTIVE, GroupStatus.INACTIVE);
    /** 살아 있는 소모임(분류의 소모임 수 · 1인 상한) — 신청 중 · 열림 · 비활성 */
    public static final List<GroupStatus> LIVE = List.of(GroupStatus.PENDING, GroupStatus.ACTIVE, GroupStatus.INACTIVE);

    public GroupRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    // ── 공개 목록(열림만) ─────────────────────────────────────────────────────

    /**
     * 공개 목록의 조건.
     *
     * @param boardIds    분류에 할당된 게시판(분류 칩) — null 이면 조건 없음, 비어 있으면 빈 목록
     * @param q           이름·소개 부분 일치(대소문자 무시) — null 이면 조건 없음
     * @param recruiting  모집 중(모집 켬 + 정원 남음)만
     * @param memberOf    이 회원이 멤버인 소모임만(null 이면 조건 없음)
     * @param exclude     뺄 소모임 ID
     */
    public record PublicFilter(Collection<Long> boardIds, String q, boolean recruiting, Long memberOf, Collection<Long> exclude) {
    }

    /** 정렬 — popular(멤버 수, 같으면 최근 활동) · new(개설) · recruiting(최근 활동) · mine(최근 활동) */
    public List<GroupEntity> publicPage(PublicFilter filter, String sort, long offset, int limit) {
        JPAQuery<GroupEntity> q = query.selectFrom(GROUP).where(publicWhere(filter));
        List<OrderSpecifier<?>> order = new ArrayList<>();
        switch (sort) {
            case "new" -> order.add(GROUP.openedAt.desc().nullsLast());
            case "popular" -> {
                order.add(GROUP.memberCount.desc());
                order.add(GROUP.lastActivityAt.desc().nullsLast());
            }
            default -> order.add(GROUP.lastActivityAt.desc().nullsLast());
        }
        order.add(GROUP.id.desc());
        return q.orderBy(order.toArray(OrderSpecifier[]::new)).offset(offset).limit(limit).fetch();
    }

    public long publicCount(PublicFilter filter) {
        Long n = query.select(GROUP.count()).from(GROUP).where(publicWhere(filter)).fetchOne();
        return n == null ? 0 : n;
    }

    private BooleanExpression publicWhere(PublicFilter f) {
        BooleanExpression where = GROUP.status.eq(GroupStatus.ACTIVE);
        if (f.boardIds() != null) {
            where = f.boardIds().isEmpty() ? where.and(GROUP.id.isNull()) : where.and(GROUP.boardId.in(f.boardIds()));
        }
        if (f.q() != null && !f.q().isBlank()) {
            String pattern = "%" + escapeLike(f.q().strip().toLowerCase(Locale.ROOT)) + "%";
            where = where.and(GROUP.name.lower().like(pattern, '!').or(GROUP.description.lower().like(pattern, '!')));
        }
        if (f.recruiting()) {
            where = where.and(GROUP.recruiting.isTrue()).and(GROUP.memberCount.lt(GROUP.capacity));
        }
        if (f.memberOf() != null) {
            where = where.and(JPAExpressions.selectOne().from(MEMBER)
                    .where(MEMBER.groupId.eq(GROUP.id), MEMBER.userId.eq(f.memberOf()), MEMBER.status.eq(GroupMemberStatus.ACTIVE)).exists());
        }
        if (f.exclude() != null && !f.exclude().isEmpty()) {
            where = where.and(GROUP.id.notIn(f.exclude()));
        }
        return where;
    }

    /** 게시판 ID → 그 상태들의 소모임 수 */
    public Map<Long, Long> countByBoard(Collection<GroupStatus> statuses) {
        Map<Long, Long> out = new HashMap<>();
        for (Tuple row : query.select(GROUP.boardId, GROUP.count()).from(GROUP).where(GROUP.status.in(statuses)).groupBy(GROUP.boardId).fetch()) {
            out.put(row.get(GROUP.boardId), row.get(GROUP.count()));
        }
        return out;
    }

    // ── 관리자 목록(열림·비활성) ──────────────────────────────────────────────

    /**
     * @param q          이름 또는 모임장 닉네임 부분 일치(대소문자 무시)
     * @param status     ACTIVE · INACTIVE · null(둘 다)
     * @param closed     모집을 마감한 소모임만
     * @param offline    오프라인 모임만
     * @param visibility 공개 범위(null 이면 조건 없음)
     */
    public record AdminFilter(String q, GroupStatus status, boolean closed, boolean offline, GroupVisibility visibility) {
    }

    /** 최신순(개설일, 같으면 ID) 고정 */
    public List<GroupEntity> adminPage(AdminFilter filter, long offset, int limit) {
        return query.selectFrom(GROUP).where(adminWhere(filter)).orderBy(GROUP.openedAt.desc().nullsLast(), GROUP.id.desc()).offset(offset).limit(limit).fetch();
    }

    public long adminCount(AdminFilter filter) {
        Long n = query.select(GROUP.count()).from(GROUP).where(adminWhere(filter)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 칩 숫자 — 검색어만 적용(상태·성질·공개 범위 필터는 적용하지 않는다): all · active · inactive · closed · offline */
    public Map<String, Long> adminCounts(String q) {
        NumberExpression<Long> active = new CaseBuilder().when(GROUP.status.eq(GroupStatus.ACTIVE)).then(1L).otherwise(0L).sum();
        NumberExpression<Long> inactive = new CaseBuilder().when(GROUP.status.eq(GroupStatus.INACTIVE)).then(1L).otherwise(0L).sum();
        NumberExpression<Long> closed = new CaseBuilder().when(GROUP.recruiting.isFalse()).then(1L).otherwise(0L).sum();
        NumberExpression<Long> offline = new CaseBuilder().when(GROUP.offline.isTrue()).then(1L).otherwise(0L).sum();
        Tuple row = query.select(GROUP.count(), active, inactive, closed, offline).from(GROUP)
                .where(adminWhere(new AdminFilter(q, null, false, false, null))).fetchOne();
        Map<String, Long> out = new LinkedHashMap<>();
        out.put("all", row == null ? 0 : nz(row.get(GROUP.count())));
        out.put("active", row == null ? 0 : nz(row.get(active)));
        out.put("inactive", row == null ? 0 : nz(row.get(inactive)));
        out.put("closed", row == null ? 0 : nz(row.get(closed)));
        out.put("offline", row == null ? 0 : nz(row.get(offline)));
        return out;
    }

    private BooleanExpression adminWhere(AdminFilter f) {
        BooleanExpression where = f.status() == null ? GROUP.status.in(MANAGED) : GROUP.status.eq(f.status());
        if (f.q() != null && !f.q().isBlank()) {
            String pattern = "%" + escapeLike(f.q().strip().toLowerCase(Locale.ROOT)) + "%";
            where = where.and(GROUP.name.lower().like(pattern, '!')
                    .or(GROUP.leaderId.in(JPAExpressions.select(PROFILE.userId).from(PROFILE).where(PROFILE.nickname.lower().like(pattern, '!')))));
        }
        if (f.closed()) {
            where = where.and(GROUP.recruiting.isFalse());
        }
        if (f.offline()) {
            where = where.and(GROUP.offline.isTrue());
        }
        if (f.visibility() != null) {
            where = where.and(GROUP.visibility.eq(f.visibility()));
        }
        return where;
    }

    // ── 멤버 ─────────────────────────────────────────────────────────────────

    /** 멤버 탭 — 모임장 → 매니저 → 멤버, 같은 역할 안에서는 가입이 이른 순 */
    public List<GroupMemberEntity> memberPage(long groupId, long offset, int limit) {
        NumberExpression<Integer> roleOrder = new CaseBuilder().when(MEMBER.role.eq(GroupRole.LEADER)).then(0)
                .when(MEMBER.role.eq(GroupRole.MANAGER)).then(1).otherwise(2);
        return query.selectFrom(MEMBER).where(MEMBER.groupId.eq(groupId), MEMBER.status.eq(GroupMemberStatus.ACTIVE))
                .orderBy(roleOrder.asc(), MEMBER.joinedAt.asc().nullsLast(), MEMBER.userId.asc()).offset(offset).limit(limit).fetch();
    }

    /** 최근 가입한 지금 멤버(모임장 제외) — 수와 최근 순 몇 명 */
    public long newcomerCount(long groupId, Instant since) {
        Long n = query.select(MEMBER.count()).from(MEMBER).where(newcomers(groupId, since)).fetchOne();
        return n == null ? 0 : n;
    }

    public List<GroupMemberEntity> newcomers(long groupId, Instant since, int limit) {
        return query.selectFrom(MEMBER).where(newcomers(groupId, since)).orderBy(MEMBER.joinedAt.desc(), MEMBER.userId.desc()).limit(limit).fetch();
    }

    private BooleanBuilder newcomers(long groupId, Instant since) {
        return new BooleanBuilder(MEMBER.groupId.eq(groupId)).and(MEMBER.status.eq(GroupMemberStatus.ACTIVE)).and(MEMBER.role.ne(GroupRole.LEADER))
                .and(MEMBER.joinedAt.after(since));
    }

    /**
     * 하루 가입·신청 한도 — 그 시각 뒤에 이 회원이 바로 가입하거나 신청한 시각들(한 행에서 둘 다 그 뒤면 이른 쪽 하나). 모임장 행(개설)은 세지 않는다.
     * 행은 다시 가입·신청할 때 같은 행을 고치므로 같은 소모임을 여러 번 오간 것은 마지막 한 번만 센다(1차 방어 — 소모임마다의 규칙이 진짜 한도).
     */
    public List<Instant> joinAttempts(long userId, Instant since) {
        List<Instant> out = new ArrayList<>();
        for (Tuple row : query.select(MEMBER.appliedAt, MEMBER.joinedAt).from(MEMBER)
                .where(MEMBER.userId.eq(userId), MEMBER.role.ne(GroupRole.LEADER), MEMBER.appliedAt.after(since).or(MEMBER.joinedAt.after(since))).fetch()) {
            Instant applied = row.get(MEMBER.appliedAt);
            Instant joined = row.get(MEMBER.joinedAt);
            Instant first = null;
            if (applied != null && applied.isAfter(since)) {
                first = applied;
            }
            if (joined != null && joined.isAfter(since) && (first == null || joined.isBefore(first))) {
                first = joined;
            }
            if (first != null) {
                out.add(first);
            }
        }
        return out;
    }

    // ── 설정 화면의 영향 숫자 ─────────────────────────────────────────────────

    /** 열린 소모임의 임명된 매니저 수 · 매니저가 있는 소모임 수 */
    public long[] managerUsage() {
        Tuple row = query.select(MEMBER.count(), MEMBER.groupId.countDistinct()).from(MEMBER)
                .join(GROUP).on(GROUP.id.eq(MEMBER.groupId))
                .where(GROUP.status.eq(GroupStatus.ACTIVE), MEMBER.role.eq(GroupRole.MANAGER), MEMBER.status.eq(GroupMemberStatus.ACTIVE)).fetchOne();
        return new long[] {row == null ? 0 : nz(row.get(MEMBER.count())), row == null ? 0 : nz(row.get(MEMBER.groupId.countDistinct()))};
    }

    /** 열린 소모임에서 「글 공개 처리·되돌리기」(UNPUBLISH = 16, 가장 큰 비트라 값이 16 이상이면 있다)를 맡은 매니저 수 */
    public long unpublishManagerCount() {
        Long n = query.select(MEMBER.count()).from(MEMBER).join(GROUP).on(GROUP.id.eq(MEMBER.groupId))
                .where(GROUP.status.eq(GroupStatus.ACTIVE), MEMBER.role.eq(GroupRole.MANAGER), MEMBER.status.eq(GroupMemberStatus.ACTIVE),
                        MEMBER.managerPerms.goe(16)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 열린 소모임의 매니저 상한별 소모임 수(상한 오름차순) */
    public Map<Integer, Long> managerLimitCounts() {
        Map<Integer, Long> out = new LinkedHashMap<>();
        for (Tuple row : query.select(GROUP.managerLimit, GROUP.count()).from(GROUP).where(GROUP.status.eq(GroupStatus.ACTIVE))
                .groupBy(GROUP.managerLimit).orderBy(GROUP.managerLimit.asc()).fetch()) {
            out.put(row.get(GROUP.managerLimit), row.get(GROUP.count()));
        }
        return out;
    }

    /** 열린 소모임의 공개 범위별 수 */
    public Map<GroupVisibility, Long> visibilityCounts() {
        Map<GroupVisibility, Long> out = new HashMap<>();
        for (Tuple row : query.select(GROUP.visibility, GROUP.count()).from(GROUP).where(GROUP.status.eq(GroupStatus.ACTIVE)).groupBy(GROUP.visibility).fetch()) {
            out.put(row.get(GROUP.visibility), row.get(GROUP.count()));
        }
        return out;
    }

    // ── 설정 변경 이력 ───────────────────────────────────────────────────────

    /** 이력 한 줄 + 저장한 운영자의 지금 이름(지워졌으면 null) */
    public record HistoryRow(GroupSettingHistoryEntity history, String operatorName) {
    }

    /** 최신순(시각, 같으면 ID 큰 쪽 먼저). section 이 null 이면 모든 구획 */
    public List<HistoryRow> settingHistory(GroupSettingSection section, int page, int size) {
        return query.select(HISTORY, OPERATOR.operatorName).from(HISTORY).leftJoin(OPERATOR).on(OPERATOR.id.eq(HISTORY.operatorId))
                .where(section == null ? null : HISTORY.section.eq(section))
                .orderBy(HISTORY.changedAt.desc(), HISTORY.id.desc()).offset((long) page * size).limit(size).fetch()
                .stream().map(row -> new HistoryRow(row.get(HISTORY), row.get(OPERATOR.operatorName))).toList();
    }

    public long settingHistoryCount(GroupSettingSection section) {
        Long n = query.select(HISTORY.count()).from(HISTORY).where(section == null ? null : HISTORY.section.eq(section)).fetchOne();
        return n == null ? 0 : n;
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }

    private static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
