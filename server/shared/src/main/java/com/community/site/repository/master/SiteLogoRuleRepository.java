package com.community.site.repository.master;

import com.community.site.entity.SiteLogoRuleEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.util.List;

/** 로고 보이기 규칙의 기념일 줄({@code site_logo_rules}, V249) */
public interface SiteLogoRuleRepository extends IrobitRepository<SiteLogoRuleEntity, Long> {

    /** 우선순위 차례(위 줄이 먼저) */
    List<SiteLogoRuleEntity> findAllByOrderBySortOrderAsc();
}
