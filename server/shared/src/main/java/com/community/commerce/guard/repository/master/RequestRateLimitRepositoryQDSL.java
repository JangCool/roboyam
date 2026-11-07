package com.community.commerce.guard.repository.master;

import com.community.commerce.guard.dto.RequestRateLimitRow;
import com.community.commerce.guard.entity.QRequestRateLimitEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 요청 종류별 1분 한도({@code request_rate_limits}) 읽기 — 돈 요청의 15초 사본({@code RequestRateLimits})이 다시 읽을 때. 엔티티를 올리지 않고 두 칸만 읽는다(트랜잭션 밖에서도 부른다 —
 * 그때는 질의 하나가 자기 연결로 끝난다).
 */
@Repository
public class RequestRateLimitRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QRequestRateLimitEntity LIMIT = QRequestRateLimitEntity.requestRateLimitEntity;

    public RequestRateLimitRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 표의 모든 줄(종류 코드 · 한도) */
    public List<RequestRateLimitRow> all() {
        return query.select(Projections.constructor(RequestRateLimitRow.class, LIMIT.action, LIMIT.limitPerMinute)).from(LIMIT).fetch();
    }
}
