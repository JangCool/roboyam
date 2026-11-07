package com.community.pointshop.repository.master;

import com.community.pointshop.entity.PointShopPurchaseEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 구매 넣기(persist + flush) — 읽기·환불 표시는 {@link PointShopPurchaseStore} */
public interface PointShopPurchaseRepository extends RoboyamRepository<PointShopPurchaseEntity, Long> {
}
