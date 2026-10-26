package com.community.point.repository.master;

import com.community.point.entity.PointLotEntity;
import com.community.point.entity.PointLotKind;
import com.community.point.entity.PointRevokeShortfallEntity;
import com.community.point.entity.QPointLotEntity;
import com.community.point.entity.QPointRevokeShortfallEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 관리자 회원 상세의 포인트 묶음·미회수 기록 읽기(commerce-stage1-contract 「1-1」 API). 트랜잭션은 부르는 서비스가 연다.
 *
 * <ul>
 *   <li><b>남은 것만</b>: 남은 양이 있고 소멸일이 지나지 않은 묶음 — <b>차감 순서</b>({@code PointDeductionPolicy}): 무상(소멸일 → 만든 시각 → ID) 다음 유상(ID). 색인
 *       {@code ix_point_lots_deduction}.</li>
 *   <li><b>전체</b>: 그 회원의 모든 묶음 — 최근 받은 순(만든 시각 → ID 내림). 색인 {@code ix_point_lots_user}.</li>
 *   <li><b>미회수 기록</b>: 최근 것이 먼저. 색인 {@code ix_point_revoke_shortfalls_user}.</li>
 * </ul>
 */
@Repository
public class PointMemberLotQueries extends RoboyamRepositoryQDSL {

    private static final QPointLotEntity LOT = QPointLotEntity.pointLotEntity;
    private static final QPointRevokeShortfallEntity SHORTFALL = QPointRevokeShortfallEntity.pointRevokeShortfallEntity;

    public PointMemberLotQueries(JPAQueryFactory query) {
        super(query);
    }

    /** 묶음 한 쪽 */
    public List<PointLotEntity> page(long userId, boolean active, Instant now, int page, int size) {
        return query.selectFrom(LOT).where(where(userId, active, now)).orderBy(order(active)).offset((long) page * size).limit(size).fetch();
    }

    /** 같은 조건의 묶음 수 */
    public long count(long userId, boolean active, Instant now) {
        Long count = query.select(LOT.count()).from(LOT).where(where(userId, active, now)).fetchOne();
        return count == null ? 0 : count;
    }

    /** 최근 미회수 기록 {@code limit} 건 */
    public List<PointRevokeShortfallEntity> recentShortfalls(long userId, int limit) {
        return query.selectFrom(SHORTFALL).where(SHORTFALL.userId.eq(userId)).orderBy(SHORTFALL.createdAt.desc(), SHORTFALL.id.desc()).limit(limit).fetch();
    }

    private static BooleanExpression where(long userId, boolean active, Instant now) {
        BooleanExpression mine = LOT.userId.eq(userId);
        return active ? mine.and(LOT.remainingAmount.gt(0L)).and(LOT.expiresAt.isNull().or(LOT.expiresAt.gt(now))) : mine;
    }

    private static OrderSpecifier<?>[] order(boolean active) {
        if (!active) {
            return new OrderSpecifier<?>[] {LOT.createdAt.desc(), LOT.id.desc()};
        }
        // 무상 먼저(0) → 유상(1). 무상은 소멸일 → 만든 시각 → ID, 유상은 ID(소멸일·만든 시각 칸을 비워 ID 만 남긴다)
        return new OrderSpecifier<?>[] {
                new CaseBuilder().when(LOT.kind.eq(PointLotKind.FREE)).then(0).otherwise(1).asc(),
                LOT.expiresAt.asc().nullsLast(),
                new CaseBuilder().when(LOT.kind.eq(PointLotKind.FREE)).then(LOT.createdAt).otherwise(Expressions.nullExpression(Instant.class)).asc().nullsLast(),
                LOT.id.asc()};
    }
}
