package com.community.bizapplication.repository.master;

import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.QBizApplicationEntity;
import com.community.bizapplication.entity.QBizApplicationItemEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 자리(한도) 판정의 DB 쪽 — 같은 상품의 「자리를 차지하는 상태」 신청 건 줄 중 기간이 겹치는 것을 읽고(QueryDSL), 승인 때는 상품마다 어드바이저리 잠금을 건다.
 * Native SQL 로 남긴 것과 이유: {@link #lock} — {@code pg_advisory_xact_lock} 은 DB 전용 기능이다(트랜잭션이 끝나면 풀린다. 같은 상품을 승인하는 요청이 겹쳐도 판정·저장이
 * 한 번에 하나씩 된다).
 */
@Repository
public class BizSlotStore extends IrobitRepositoryQDSL {

    private static final QBizApplicationItemEntity I = QBizApplicationItemEntity.bizApplicationItemEntity;
    private static final QBizApplicationEntity A = QBizApplicationEntity.bizApplicationEntity;

    /** 겹치는 줄(기간만) */
    public record Occupied(long applicationId, Instant startAt, Instant endAt) {
    }

    private final JdbcTemplate jdbc;

    public BizSlotStore(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 그 상품의 승인 잠금(트랜잭션 끝까지) */
    public void lock(BizProduct product) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))::text", String.class, "biz-slot:" + product.name());
    }

    /** [start, end) 와 겹치는 차지 줄 — {@code exceptApplicationId} 는 빼고(자기 자신) */
    public List<Occupied> overlapping(BizProduct product, Instant start, Instant end, Long exceptApplicationId) {
        return query.select(Projections.constructor(Occupied.class, I.applicationId, I.startAt, I.endAt)).from(I).join(A).on(A.id.eq(I.applicationId))
                .where(I.product.eq(product), I.startAt.lt(end), I.endAt.gt(start), A.status.in(BizApplicationStatus.OCCUPYING),
                        A.id.ne(exceptApplicationId == null ? -1L : exceptApplicationId))
                .fetch();
    }
}
