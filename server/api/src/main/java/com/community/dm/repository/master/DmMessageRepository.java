package com.community.dm.repository.master;

import com.community.dm.entity.DmMessageEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 쪽지 넣기(persist) — 읽기·지우기는 {@link DmQueries} */
public interface DmMessageRepository extends RoboyamRepository<DmMessageEntity, Long> {
}
