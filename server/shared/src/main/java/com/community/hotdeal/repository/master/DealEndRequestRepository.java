package com.community.hotdeal.repository.master;

import com.community.hotdeal.entity.DealEndRequestEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 핫딜 종료 요청({@code deal_end_requests}) — 넣기는 {@code persist}. 질의는 {@link DealEndRequestStore}·{@link DealQueries} */
public interface DealEndRequestRepository extends RoboyamRepository<DealEndRequestEntity, Long> {
}
