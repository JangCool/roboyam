package com.community.pointshop.repository.master;

import com.community.pointshop.entity.PointShopRefundRequestEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 환불 신청 넣기(persist + flush) — 읽기·결정은 {@link PointShopPurchaseStore} */
public interface PointShopRefundRequestRepository extends RoboyamRepository<PointShopRefundRequestEntity, Long> {
}
