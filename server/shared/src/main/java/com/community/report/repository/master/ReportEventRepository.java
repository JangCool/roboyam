package com.community.report.repository.master;

import com.community.report.entity.ReportEventEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;

public interface ReportEventRepository extends RoboyamRepository<ReportEventEntity, Long> {

    /** 한 사건의 기록 — 오래된 것부터(색인 ix_report_events_case) */
    List<ReportEventEntity> findByCaseIdOrderByIdAsc(Long caseId);
}
