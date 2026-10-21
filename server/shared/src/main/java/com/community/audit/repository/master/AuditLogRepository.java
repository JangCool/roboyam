package com.community.audit.repository.master;

import com.community.audit.entity.AuditLogEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

public interface AuditLogRepository extends RoboyamRepository<AuditLogEntity, Long> {
}
