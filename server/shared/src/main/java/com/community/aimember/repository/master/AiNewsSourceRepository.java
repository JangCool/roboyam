package com.community.aimember.repository.master;

import com.community.aimember.entity.AiNewsSourceEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.util.List;

/** 뉴스 출처({@code ai_news_sources}, V228) */
public interface AiNewsSourceRepository extends IrobitRepository<AiNewsSourceEntity, Long> {

    /** 화면·수집 차례 */
    List<AiNewsSourceEntity> findAllByOrderBySortOrderAscIdAsc();

    List<AiNewsSourceEntity> findByEnabledTrueOrderBySortOrderAscIdAsc();
}
