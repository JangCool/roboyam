package com.community.dm.repository.master;

import com.community.dm.entity.DmBoxEntity;
import com.community.dm.entity.DmBoxId;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 쪽지함 줄 넣기(persist) — 읽기·고치기는 {@link DmQueries} */
public interface DmBoxRepository extends IrobitRepository<DmBoxEntity, DmBoxId> {
}
