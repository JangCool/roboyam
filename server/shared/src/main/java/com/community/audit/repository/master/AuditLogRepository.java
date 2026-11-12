package com.community.audit.repository.master;

import com.community.audit.entity.AuditLogEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

public interface AuditLogRepository extends IrobitRepository<AuditLogEntity, Long> {
}
