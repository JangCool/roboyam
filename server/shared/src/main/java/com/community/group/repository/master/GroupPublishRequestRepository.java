package com.community.group.repository.master;

import com.community.group.entity.GroupPublishRequestEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 공개 요청 한 행(글 ID 가 키, V94) — 목록·대기 수·일괄 닫기는 {@link GroupPublishRequestQueries}. 쓰기는 소모임 행을 잠근 뒤 한다 */
public interface GroupPublishRequestRepository extends RoboyamRepository<GroupPublishRequestEntity, Long> {
}
