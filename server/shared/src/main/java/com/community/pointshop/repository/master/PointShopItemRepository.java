package com.community.pointshop.repository.master;

import com.community.pointshop.entity.PointShopItemEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 품목 넣기(persist + flush) — 읽기·고치기는 {@link PointShopItemStore} */
public interface PointShopItemRepository extends IrobitRepository<PointShopItemEntity, Long> {
}
