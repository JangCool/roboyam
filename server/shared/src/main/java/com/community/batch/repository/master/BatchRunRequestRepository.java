package com.community.batch.repository.master;

import com.community.batch.entity.BatchRunRequestEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 요청 줄 넣기(persist + flush) — 집기·닫기는 {@code com.community.batch.core.BatchRequestQueue} */
public interface BatchRunRequestRepository extends RoboyamRepository<BatchRunRequestEntity, Long> {
}
