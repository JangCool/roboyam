package com.community.site.repository.master;

import com.community.site.entity.SiteOperatorHistoryEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.util.List;

public interface SiteOperatorHistoryRepository extends IrobitRepository<SiteOperatorHistoryEntity, Long> {

    /** 전환 이력 — 최신 먼저 */
    List<SiteOperatorHistoryEntity> findAllByOrderByChangedAtDescIdDesc();
}
