package com.community.pointshop.repository.master;

import com.community.pointshop.entity.UserItemEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 보유 줄 넣기(persist + flush) — 읽기·사용·회수·예약은 {@link UserItemStore} */
public interface UserItemRepository extends RoboyamRepository<UserItemEntity, Long> {
}
