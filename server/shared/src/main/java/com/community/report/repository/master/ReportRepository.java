package com.community.report.repository.master;

import com.community.report.entity.ReportEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

public interface ReportRepository extends IrobitRepository<ReportEntity, Long> {

    boolean existsByTargetTypeAndTargetIdAndReporterId(String targetType, Long targetId, Long reporterId);
}
