package com.community.point.repository.master;

import com.community.point.dto.PointExpiring;
import com.community.point.entity.PointLotKind;
import com.community.point.entity.QPointLotEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.community.zconfiguration.time.ServiceTime;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 포인트 묶음 <b>읽기</b> — 회원 요약과 관리자 회원 묶음 화면이 같이 쓰는 「소멸 예정」(commerce-stage1-contract 「1-1 화면 확정과 서버 세부」 API). 쓰기는 {@code PointService} 만 한다.
 * 트랜잭션은 부르는 서비스가 연다. 색인 {@code ix_point_lots_deduction (user_id, kind, expires_at, id) WHERE remaining_amount > 0} 를 탄다.
 */
@Repository
public class PointLotQueries extends RoboyamRepositoryQDSL {

    private static final QPointLotEntity LOT = QPointLotEntity.pointLotEntity;

    public PointLotQueries(JPAQueryFactory query) {
        super(query);
    }

    /**
     * 가장 가까운 소멸 예정 — 소멸일이 아직 지나지 않은({@code expires_at > now}) 남은 무상 묶음 중 가장 이른 소멸 시각의 <b>한국 시간 날짜</b>와, 그 날짜(지금 이후)에 소멸할 남은 양의 합.
     * 같은 날짜의 묶음이 여럿이면 더한다. 없으면 비어 있다. 소멸일이 지났는데 아직 소멸 처리되지 않은 묶음(배치 전)은 「예정」이 아니라 빠진다.
     */
    public Optional<PointExpiring> expiring(long userId, Instant now) {
        BooleanExpression upcoming = LOT.userId.eq(userId).and(LOT.kind.eq(PointLotKind.FREE)).and(LOT.remainingAmount.gt(0L)).and(LOT.expiresAt.gt(now));
        Instant first = query.select(LOT.expiresAt.min()).from(LOT).where(upcoming).fetchOne();
        if (first == null) {
            return Optional.empty();
        }
        LocalDate date = ServiceTime.dateOf(first);
        Instant dayEnd = date.plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant();
        Long amount = query.select(LOT.remainingAmount.sum()).from(LOT).where(upcoming, LOT.expiresAt.lt(dayEnd)).fetchOne();
        return Optional.of(new PointExpiring(date.toString(), amount == null ? 0 : amount));
    }
}
