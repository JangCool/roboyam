package com.community.batch.repository.master;

import com.community.batch.entity.BatchRunFailureEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 실패 줄 넣기(persist + flush) — 읽기·해결 표시는 {@code com.community.batch.core.BatchFailureRecorder} */
public interface BatchRunFailureRepository extends IrobitRepository<BatchRunFailureEntity, Long> {
}
