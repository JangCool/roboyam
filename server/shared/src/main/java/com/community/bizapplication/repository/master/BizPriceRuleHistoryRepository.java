package com.community.bizapplication.repository.master;

import com.community.bizapplication.entity.BizPriceRuleHistoryEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 요금표 변경 이력 넣기(V208 {@code biz_price_rule_history}) — 조회는 관리자 서버의 QueryDSL 저장소 */
public interface BizPriceRuleHistoryRepository extends IrobitRepository<BizPriceRuleHistoryEntity, Long> {
}
