package com.community.cache.repository.master;

import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.QGroupBuyEntity;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.QPointShopItemEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 캐시 미리보기의 예시 번호 — 판매 중인 포인트 상점 상품 · 모집 중인 공동구매의 최신 하나(ID = 만든 시각 차례). 핫딜 글은 관리자 핫딜 목록 질의({@code AdminDealQueries})를 쓴다.
 * 판매 중 = 켜짐 · 판매 시작 지남 · 판매 끝 전 · 재고 남음(없음 = 무제한) — 관리자 화면 {@code UtilPointShop.saleState} 의 ON_SALE 과 같다. 프로필 아바타 품목은 상품 화면이 없어 뺀다.
 */
@Repository
public class CacheSampleStore extends RoboyamRepositoryQDSL {

    private static final QPointShopItemEntity ITEM = QPointShopItemEntity.pointShopItemEntity;
    private static final QGroupBuyEntity GROUP_BUY = QGroupBuyEntity.groupBuyEntity;

    public CacheSampleStore(JPAQueryFactory query) {
        super(query);
    }

    public Optional<Long> latestOnSaleItem(Instant now) {
        return Optional.ofNullable(query.select(ITEM.id).from(ITEM)
                .where(ITEM.active.isTrue(), ITEM.kind.ne(PointShopItemKind.PROFILE_AVATAR),
                        ITEM.saleStartAt.isNull().or(ITEM.saleStartAt.loe(now)), ITEM.saleEndAt.isNull().or(ITEM.saleEndAt.gt(now)),
                        ITEM.stock.isNull().or(ITEM.stock.gt(0)))
                .orderBy(ITEM.id.desc()).fetchFirst());
    }

    public Optional<Long> latestRecruitingGroupBuy() {
        return Optional.ofNullable(query.select(GROUP_BUY.id).from(GROUP_BUY).where(GROUP_BUY.status.eq(GroupBuyStatus.RECRUITING))
                .orderBy(GROUP_BUY.id.desc()).fetchFirst());
    }
}
