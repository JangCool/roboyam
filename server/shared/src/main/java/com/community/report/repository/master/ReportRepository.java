package com.community.report.repository.master;

import com.community.report.entity.ReportEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

public interface ReportRepository extends RoboyamRepository<ReportEntity, Long> {

    boolean existsByTargetTypeAndTargetIdAndReporterId(String targetType, Long targetId, Long reporterId);
}
