package com.community.withdrawal.repository.master;

import com.community.group.entity.QGroupEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.user.entity.QProfileEntity;
import com.community.withdrawal.entity.QMemberWithdrawalEntity;
import com.community.withdrawal.entity.WithdrawalStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 회원 탈퇴 관리 읽기(admin-web {@code /members/withdrawals}, member-withdrawal-contract 「관리자」) — 목록(거르기·정렬·쪽)·요약 4칸·사유 분포와 이름 찾기(닉네임·운영자·소모임).
 * 쓰기는 shared {@code WithdrawalRequestWriter}·{@code MemberWithdrawalStore}. 줄 모양은 shared {@link MemberWithdrawalStore.Row} 를 그대로 쓴다(칸은 {@link MemberWithdrawalStore#columns()}).
 * 모두 QueryDSL 이다(탈퇴 신청 표 = shared {@code MemberWithdrawalEntity}).
 *
 * <p>인덱스(V132·V134): 상태 탭 + 신청일 정렬 {@code ix_member_withdrawals_status_requested}, 전체 신청일 {@code ix_member_withdrawals_requested}, 완료일(요약 오늘·이번 달, 완료 탭)
 * {@code ix_member_withdrawals_completed}, 취소일 {@code ix_member_withdrawals_canceled}, 진행 중·10일 넘긴 진행 중 {@code ix_member_withdrawals_scheduled}, 회원별 {@code ix_member_withdrawals_user}.
 */
@Repository
public class MemberWithdrawalAdminStore extends RoboyamRepositoryQDSL {

    private static final QMemberWithdrawalEntity W = QMemberWithdrawalEntity.memberWithdrawalEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;
    private static final QGroupEntity GROUP = QGroupEntity.groupEntity;

    /** 목록 조건 — 비어 있는(null) 조건은 거르지 않는다. 기간은 [from, to) */
    public record Criteria(WithdrawalStatus status, Long userId, Instant requestedFrom, Instant requestedTo, Instant completedFrom, Instant completedTo, String sortColumn,
                           boolean ascending, int page, int size) {
    }

    /** 목록 한 줄 — 신청 줄 + 닉네임(프로필, 익명화됐으면 null) */
    public record Listed(MemberWithdrawalStore.Row row, String nickname) {
    }

    /** 정렬할 수 있는 칸 — 화면 이름 → 칸 */
    public static final Map<String, String> SORTS = Map.of("requestedAt", "requested_at", "expectedCompleteAt", "expected_complete_at", "completedAt", "completed_at",
            "canceledAt", "canceled_at", "pendingCount", "pending_count");

    /** {@link #SORTS} 의 칸 → 경로 */
    private static final Map<String, ComparableExpressionBase<?>> SORT_PATHS = Map.of("requested_at", W.requestedAt, "expected_complete_at", W.expectedCompleteAt,
            "completed_at", W.completedAt, "canceled_at", W.canceledAt, "pending_count", W.pendingCount);

    /** 신청 줄 칸 + 닉네임 */
    private static final Expression<?>[] LISTED = listedColumns();

    public MemberWithdrawalAdminStore(JPAQueryFactory query) {
        super(query);
    }

    public List<Listed> search(Criteria c) {
        ComparableExpressionBase<?> sortPath = SORT_PATHS.get(c.sortColumn());
        OrderSpecifier<?> order = c.ascending() ? sortPath.asc().nullsLast() : sortPath.desc().nullsLast();
        return listed().where(where(c)).orderBy(order, W.id.desc()).offset((long) c.page() * c.size()).limit(c.size()).fetch().stream()
                .map(MemberWithdrawalAdminStore::listed).toList();
    }

    public long count(Criteria c) {
        Long total = query.select(W.count()).from(W).where(where(c)).fetchOne();
        return total == null ? 0 : total;
    }

    /** 신청 줄 + 닉네임 한 건(없으면 null) */
    public Listed find(long id) {
        Tuple t = listed().where(W.id.eq(id)).fetchFirst();
        return t == null ? null : listed(t);
    }

    /** 그 회원의 가장 최근 신청(없으면 null) */
    public Listed latestOf(long userId) {
        Tuple t = listed().where(W.userId.eq(userId)).orderBy(W.requestedAt.desc(), W.id.desc()).fetchFirst();
        return t == null ? null : listed(t);
    }

    /**
     * 요약 4칸 — 모두 인덱스로만 센다(칸마다 한 문장).
     *
     * @param todayStart    서비스 하루의 0시(완료 ≥ 이 시각 = 오늘 완료)
     * @param monthStart    서비스 달의 1일 0시(완료 ≥ 이 시각 = 이번 달 완료)
     * @param overdueBefore 이 시각 이전에 신청한 진행 중 건 = 10일 넘긴 진행 중
     * @return {진행 중, 오늘 완료, 이번 달 완료, 10일 넘긴 진행 중}
     */
    public long[] summary(Instant todayStart, Instant monthStart, Instant overdueBefore) {
        long scheduled = count(W.status.eq(WithdrawalStatus.SCHEDULED));
        long today = count(W.status.eq(WithdrawalStatus.COMPLETED).and(W.completedAt.goe(todayStart)));
        long month = count(W.status.eq(WithdrawalStatus.COMPLETED).and(W.completedAt.goe(monthStart)));
        long overdue = count(W.status.eq(WithdrawalStatus.SCHEDULED).and(W.requestedAt.loe(overdueBefore)));
        return new long[] {scheduled, today, month, overdue};
    }

    /** 사유별 건수(신청일 [from, to), 상태 무관) — 고르지 않음은 키 null */
    public Map<String, Long> reasonCounts(Instant from, Instant to) {
        BooleanBuilder where = new BooleanBuilder();
        if (from != null) {
            where.and(W.requestedAt.goe(from));
        }
        if (to != null) {
            where.and(W.requestedAt.lt(to));
        }
        Map<String, Long> out = new HashMap<>();
        NumberExpression<Long> n = W.count();
        for (Tuple t : query.select(W.reason, n).from(W).where(where).groupBy(W.reason).fetch()) {
            out.put(t.get(W.reason), t.get(n));
        }
        return out;
    }

    /** 운영자 이름(지워진 운영자는 빠진다) */
    public Map<Long, String> operatorNames(Collection<Long> ids) {
        Map<Long, String> out = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(OPERATOR.id, OPERATOR.operatorName).from(OPERATOR).where(OPERATOR.id.in(Set.copyOf(ids))).fetch()) {
            out.put(t.get(OPERATOR.id), t.get(OPERATOR.operatorName));
        }
        return out;
    }

    /** 회원 닉네임(익명화됐으면 null 값 — 키는 있다) */
    public Map<Long, String> nicknames(Collection<Long> userIds) {
        Map<Long, String> out = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(PROFILE.userId, PROFILE.nickname).from(PROFILE).where(PROFILE.userId.in(Set.copyOf(userIds))).fetch()) {
            out.put(t.get(PROFILE.userId), t.get(PROFILE.nickname));
        }
        return out;
    }

    /** 소모임 이름(지워진 소모임도 줄은 남아 이름이 있다) */
    public Map<Long, String> groupNames(Collection<Long> groupIds) {
        Map<Long, String> out = new LinkedHashMap<>();
        if (groupIds == null || groupIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(GROUP.id, GROUP.name).from(GROUP).where(GROUP.id.in(Set.copyOf(groupIds))).fetch()) {
            out.put(t.get(GROUP.id), t.get(GROUP.name));
        }
        return out;
    }

    private long count(Predicate predicate) {
        Long n = query.select(W.count()).from(W).where(predicate).fetchOne();
        return n == null ? 0 : n;
    }

    /** 신청 줄 + 닉네임(프로필 왼쪽 조인) */
    private JPAQuery<Tuple> listed() {
        return query.select(LISTED).from(W).leftJoin(PROFILE).on(PROFILE.userId.eq(W.userId));
    }

    private static Listed listed(Tuple t) {
        return new Listed(MemberWithdrawalStore.row(t), t.get(PROFILE.nickname));
    }

    private static Expression<?>[] listedColumns() {
        Expression<?>[] columns = MemberWithdrawalStore.columns();
        Expression<?>[] out = Arrays.copyOf(columns, columns.length + 1);
        out[columns.length] = PROFILE.nickname;
        return out;
    }

    private static BooleanBuilder where(Criteria c) {
        BooleanBuilder where = new BooleanBuilder();
        if (c.status() != null) {
            where.and(W.status.eq(c.status()));
        }
        if (c.userId() != null) {
            where.and(W.userId.eq(c.userId()));
        }
        range(where, W.requestedAt, c.requestedFrom(), c.requestedTo());
        range(where, W.completedAt, c.completedFrom(), c.completedTo());
        return where;
    }

    private static void range(BooleanBuilder where, DateTimePath<Instant> path, Instant from, Instant to) {
        if (from != null) {
            where.and(path.goe(from));
        }
        if (to != null) {
            where.and(path.lt(to));
        }
    }
}
