package com.community.batch.repository.master;

import com.community.batch.entity.BatchRunStepEntity;
import com.community.batch.entity.BatchRunStepId;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 단계 줄 넣기(persist + flush) — 고치기는 {@code com.community.batch.core.BatchRunRecorder} 의 QueryDSL UPDATE */
public interface BatchRunStepRepository extends RoboyamRepository<BatchRunStepEntity, BatchRunStepId> {
}
