package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.QGroupBuyWatchEntity;
import com.community.groupbuy.entity.WatchKind;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 회원의 관심 신청({@code group_buy_watches} — 마감 임박·수량 풀림·다음 차수). 서비스({@code GroupBuyWatchService}) 안에 있던 SQL 을 옮겼다.
 *
 * <p>QueryDSL 이 기본이다({@link GroupBuyStoreSupport}). Native SQL 로 남긴 것과 이유: 신청 넣기({@code ON CONFLICT (group_buy_id, user_id, kind, sku_id) DO NOTHING} —
 * 유일 제약 {@code NULLS NOT DISTINCT} 가 같은 신청 두 번을 한 줄로 받는다).
 */
@Repository
public class GroupBuyWatchStore extends GroupBuyStoreSupport {

    private static final QGroupBuyWatchEntity W = QGroupBuyWatchEntity.groupBuyWatchEntity;

    /** 신청 한 줄 — (종류, 단품 ID 또는 null) */
    public record WatchRow(WatchKind kind, Long skuId) {
    }

    public GroupBuyWatchStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    public List<WatchRow> mine(long userId, long groupBuyId) {
        return query.select(Projections.constructor(WatchRow.class, W.kind, W.skuId)).from(W).where(W.groupBuyId.eq(groupBuyId), W.userId.eq(userId)).fetch();
    }

    /** 신청한다(같은 신청이 있으면 그대로) */
    public void insert(long id, long groupBuyId, long userId, WatchKind kind, Long skuId, Instant now) {
        jdbc.update("INSERT INTO group_buy_watches (id, group_buy_id, user_id, kind, sku_id, created_at) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (group_buy_id, user_id, kind, sku_id) DO NOTHING", id, groupBuyId, userId, kind.name(), skuId, Timestamp.from(now));
    }

    /** 신청을 끈다 — 단품 ID 가 null 이면 공구 신청(예전 {@code IS NOT DISTINCT FROM}) */
    public void delete(long userId, long groupBuyId, WatchKind kind, Long skuId) {
        query.delete(W).where(W.groupBuyId.eq(groupBuyId), W.userId.eq(userId), W.kind.eq(kind), skuId == null ? W.skuId.isNull() : W.skuId.eq(skuId)).execute();
    }
}
