package com.community.grade.repository.master;

import com.community.grade.entity.GradeEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 등급 목록·집계·승급 질의는 {@link GradeRepositoryQDSL} 에 있다 */
public interface GradeRepository extends RoboyamRepository<GradeEntity, String> {
}
